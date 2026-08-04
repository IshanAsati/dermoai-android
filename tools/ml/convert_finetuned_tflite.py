#!/usr/bin/env python3
"""Convert the ce_ls_finetuned ConvNeXt-Base checkpoint to TFLite.

Rebuilds the PyTorch graph in Keras, transfers weights layer-by-layer, then
verifies PyTorch -> Keras -> TFLite numerical parity before writing the asset.
The app normalizes input itself, so the backbone's prestem normalization is
neutralized here.
"""
import json
import os
import sys

import numpy as np
import torch
import torch.nn as nn
from torchvision.models import convnext_base
from torchvision.models.convnext import LayerNorm2d

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")
import tensorflow as tf
from tensorflow.keras import Model, applications, layers

PROJ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
# Override with: convert_finetuned_tflite.py <checkpoint.pth> [output.tflite]
CKPT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(PROJ, "ce_ls_finetuned_deploy.pth")
TFLITE = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
    PROJ, "app", "src", "main", "assets", "ml", "skin_model.tflite")

CALIBRATION = os.path.join(PROJ, "calibration_image_sample_data_20x128x128x3_float32.npy")

NUM_CLASSES = 12
FEATURE_DIM = 1024
HIDDEN_DIM = 512

# The Keras port is a pure re-expression of the PyTorch graph, so it must match to
# float noise. TFLite uses dynamic-range int8 weights (keeps the model at ~90MB rather
# than ~177MB for float16), which perturbs logits — so it is judged on what the app
# actually surfaces: the predicted class, and the confidence percentages next to it.
TOL_KERAS = 1e-3
TOL_TFLITE_PROB = 0.10

# Quantization may not change a prediction the model was actually confident about.
# Where the reference itself is nearly undecided (top-1 and top-2 within this margin)
# either answer is arbitrary, so a flip there is noise rather than lost accuracy.
TIE_MARGIN = 0.05

# The healthy gate reads the feature vector directly and decides on one threshold,
# so quantization can move it even when the class logits still agree. A sample
# sitting within this much of the threshold was never really decided.
GATE_MARGIN = 0.10
GATE_MAX_DRIFT = 0.15


# ── PyTorch reference ────────────────────────────────────────────────────────

class DermoNet(nn.Module):
    """Mirrors the training-time architecture the checkpoint was saved from."""

    def __init__(self):
        super().__init__()
        self.backbone = convnext_base(weights=None)
        self.backbone.classifier = nn.Sequential(
            LayerNorm2d(FEATURE_DIM, eps=1e-6),
            nn.Flatten(1),
            nn.Sequential(
                nn.Dropout(0.4),
                nn.Linear(FEATURE_DIM, HIDDEN_DIM),
                nn.GELU(),
                nn.Dropout(0.2),
                nn.Linear(HIDDEN_DIM, NUM_CLASSES),
            ),
        )

    def forward(self, x, return_features=False):
        if not return_features:
            return self.backbone(x)
        # The 1024-d vector the healthy/lesion gate scores: post-pool, post-LayerNorm,
        # pre-classifier. Same tap point as tools/ml/probe_healthy_separability.py.
        f = self.backbone.avgpool(self.backbone.features(x))
        f = self.backbone.classifier[1](self.backbone.classifier[0](f))
        return self.backbone.classifier[2](f), f


def load_state_dict(path):
    ck = torch.load(path, map_location="cpu", weights_only=False)
    if isinstance(ck, dict) and "model_state_dict" in ck:
        return ck["model_state_dict"]
    return ck.get("model", ck) if isinstance(ck, dict) else ck


def build_torch_model(state_dict):
    model = DermoNet()
    missing, unexpected = model.load_state_dict(state_dict, strict=False)
    if missing:
        print(f"ERROR: checkpoint is missing {len(missing)} keys: {missing[:5]}")
        sys.exit(1)
    # Training-only heads (projection/binary) are not part of the deploy graph.
    ignorable = [k for k in unexpected if not k.startswith(("projection_head", "binary_head"))]
    if ignorable:
        print(f"ERROR: unexpected checkpoint keys: {ignorable[:5]}")
        sys.exit(1)
    model.eval()
    return model


# ── Keras port ───────────────────────────────────────────────────────────────

def build_keras_model():
    # include_preprocessing=False drops the prestem Normalization — the app feeds
    # already-normalized pixels.
    backbone = applications.ConvNeXtBase(
        include_top=False, include_preprocessing=False,
        weights=None, input_shape=(224, 224, 3),
    )

    # PyTorch: features -> avgpool -> LayerNorm -> head. Keras applies its trailing
    # LayerNormalization *before* pooling, so branch off just above it.
    #
    # Note: do NOT tap "..._stage_3_block_2_identity" — in keras.applications a block's
    # "_identity" layer is the residual *branch*; the skip connection is added after it,
    # so tapping there silently drops the last block's residual.
    final_norm = backbone.layers[-1]
    if not isinstance(final_norm, layers.LayerNormalization):
        print(f"ERROR: expected a trailing LayerNormalization, got {type(final_norm).__name__}")
        sys.exit(1)
    features = final_norm.input  # post-residual output of the last block

    x = layers.GlobalAveragePooling2D(name="avgpool")(features)
    pooled = layers.LayerNormalization(epsilon=1e-6, name="head_layernorm")(x)
    x = layers.Dropout(0.4, name="head_dropout_1")(pooled)
    x = layers.Dense(HIDDEN_DIM, name="head_dense_1")(x)
    x = layers.Activation("gelu", name="head_gelu")(x)
    x = layers.Dropout(0.2, name="head_dropout_2")(x)
    x = layers.Dense(NUM_CLASSES, name="head_dense_2")(x)

    # Second output: the 1024-d features. The app scores the healthy/lesion gate
    # from these rather than baking the gate into the graph, so the gate can be
    # retrained and reshipped as a small JSON without a 10-minute reconversion.
    return Model(inputs=backbone.input, outputs=[x, pooled], name="DermoAI")


def torch_weight_order():
    """PyTorch parameter names in the order Keras exposes its trainable weights."""
    seq = [
        "backbone.features.0.0.weight", "backbone.features.0.0.bias",
        "backbone.features.0.1.weight", "backbone.features.0.1.bias",
    ]
    # ConvNeXt-Base depths [3, 3, 27, 3]; even indices are stages, odd are downsamples.
    for stage_key, n_blocks, ds_key in [("1", 3, "2"), ("3", 3, "4"), ("5", 27, "6"), ("7", 3, None)]:
        for b in range(n_blocks):
            base = f"backbone.features.{stage_key}.{b}"
            seq += [
                f"{base}.block.0.weight", f"{base}.block.0.bias",
                f"{base}.block.2.weight", f"{base}.block.2.bias",
                f"{base}.block.3.weight", f"{base}.block.3.bias",
                f"{base}.block.5.weight", f"{base}.block.5.bias",
                f"{base}.layer_scale",
            ]
        if ds_key:
            seq += [
                f"backbone.features.{ds_key}.0.weight", f"backbone.features.{ds_key}.0.bias",
                f"backbone.features.{ds_key}.1.weight", f"backbone.features.{ds_key}.1.bias",
            ]
    seq += [
        "backbone.classifier.0.weight", "backbone.classifier.0.bias",
        "backbone.classifier.2.1.weight", "backbone.classifier.2.1.bias",
        "backbone.classifier.2.4.weight", "backbone.classifier.2.4.bias",
    ]
    return seq


def transfer_weights(state_dict, keras_model):
    seq = torch_weight_order()
    missing = [k for k in seq if k not in state_dict]
    if missing:
        print(f"ERROR: missing PyTorch keys: {missing}")
        sys.exit(1)

    targets = keras_model.trainable_weights
    if len(seq) != len(targets):
        print(f"ERROR: weight count mismatch — torch {len(seq)} vs keras {len(targets)}")
        sys.exit(1)

    for i, (key, target) in enumerate(zip(seq, targets)):
        v = state_dict[key].detach().cpu().numpy()
        if v.ndim == 3 and v.shape[1] == 1 and v.shape[2] == 1:
            v = v.reshape(-1)                      # layer_scale (C,1,1) -> (C,)
        elif v.ndim == 4:
            v = np.transpose(v, (2, 3, 1, 0))      # conv NCHW -> HWIO
        elif v.ndim == 2:
            v = v.T                                # linear (out,in) -> (in,out)

        if tuple(target.shape) != v.shape:
            print(f"ERROR: shape mismatch at {i} ({key}): keras {tuple(target.shape)} vs torch {v.shape}")
            sys.exit(1)
        target.assign(v)

    print(f"Transferred {len(targets)} weight tensors")


# ── Verification ─────────────────────────────────────────────────────────────

def sample_inputs(n=4, seed=0):
    """Normalized inputs in the range the app actually produces."""
    rng = np.random.default_rng(seed)
    px = rng.random((n, 224, 224, 3), dtype=np.float32)
    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
    return (px - mean) / std


def verification_inputs():
    """Real (already-normalized) sample images when available, plus random noise."""
    batches = []
    if os.path.exists(CALIBRATION):
        real = np.load(CALIBRATION)  # (20, 128, 128, 3), ImageNet-normalized
        resized = tf.image.resize(real, (224, 224), method="bilinear").numpy()
        batches.append(resized.astype(np.float32))
        print(f"  using {len(resized)} calibration images + noise")
    else:
        print(f"  WARNING: {CALIBRATION} not found; verifying on noise only")
    batches.append(sample_inputs(8, seed=7))
    return np.concatenate(batches, axis=0)


def max_diff(a, b):
    return float(np.max(np.abs(a - b)))


def softmax(logits):
    shifted = logits - logits.max(axis=1, keepdims=True)
    exp = np.exp(shifted)
    return exp / exp.sum(axis=1, keepdims=True)


def convert_to_tflite(keras_model):
    try:
        converter = tf.lite.TFLiteConverter.from_keras_model(keras_model)
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        return converter.convert()
    except Exception as exc:  # Keras 3 models may need the SavedModel path
        print(f"from_keras_model failed ({type(exc).__name__}: {exc}); retrying via SavedModel")
        export_dir = os.path.join(os.path.dirname(TFLITE), "_saved_model")
        keras_model.export(export_dir)
        converter = tf.lite.TFLiteConverter.from_saved_model(export_dir)
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        return converter.convert()


def tflite_predict(model_bytes, x):
    """Run the two-output model, returning (logits, features).

    TFLite does not preserve the Keras output order, so the tensors are matched
    by width rather than index -- 12 classes vs 1024 features.
    """
    interp = tf.lite.Interpreter(model_content=model_bytes)
    inp = interp.get_input_details()[0]
    interp.resize_tensor_input(inp["index"], x.shape)
    interp.allocate_tensors()
    interp.set_tensor(inp["index"], x.astype(np.float32))
    interp.invoke()

    logits = feats = None
    for out in interp.get_output_details():
        t = interp.get_tensor(out["index"])
        print(f"  tflite output {out['shape']} {out['dtype'].__name__}")
        if t.shape[-1] == NUM_CLASSES:
            logits = t
        elif t.shape[-1] == FEATURE_DIM:
            feats = t
    if logits is None or feats is None:
        print(f"ERROR: expected outputs of width {NUM_CLASSES} and {FEATURE_DIM}")
        sys.exit(1)
    print(f"  tflite input {inp['shape']} {inp['dtype'].__name__}")
    return logits, feats


def check_gate_agreement(torch_feats, tfl_feats):
    """Confirm int8 drift in the features does not move the healthy/lesion gate.

    The class logits can survive quantization while the gate flips, because the
    gate reads the 1024-d vector directly and decides on a single threshold.
    Skipped when the gate has not been trained yet.
    """
    gate_path = os.path.join(PROJ, "app", "src", "main", "assets", "ml",
                             "healthy_gate.json")
    if not os.path.exists(gate_path):
        print("  (no healthy_gate.json yet; skipping gate agreement check)")
        return
    with open(gate_path) as f:
        gate = json.load(f)
    w = np.array(gate["weights"], dtype=np.float32)
    b, thr = float(gate["bias"]), float(gate["threshold"])

    def score(feats):
        return 1.0 / (1.0 + np.exp(-(feats @ w + b)))

    s_ref, s_tfl = score(torch_feats), score(tfl_feats)
    flipped = np.where((s_ref >= thr) != (s_tfl >= thr))[0]
    drift = float(np.max(np.abs(s_ref - s_tfl)))
    # Same principle as TIE_MARGIN for class flips: a sample sitting on the
    # threshold was never decided, so quantization moving it is noise. Only
    # count flips the reference was confident about.
    decisive = [int(i) for i in flipped if abs(s_ref[i] - thr) > GATE_MARGIN]
    print(f"Gate  max score drift: {drift:.4f}, decision flips: {len(flipped)}/{len(s_ref)} "
          f"({len(decisive)} decisive)")
    for i in flipped:
        kind = "DECISIVE" if abs(s_ref[i] - thr) > GATE_MARGIN else "near-threshold, allowed"
        print(f"  sample {i}: gate {s_ref[i]:.3f} -> {s_tfl[i]:.3f} (thr {thr:.3f}, {kind})")
    if decisive:
        print("ERROR: quantization changed confident healthy/lesion gate decisions")
        sys.exit(1)
    if drift > GATE_MAX_DRIFT:
        print(f"ERROR: gate score drift {drift:.4f} exceeds {GATE_MAX_DRIFT}; "
              f"retrain the gate on TFLite features (tools/ml/train_healthy_gate.py)")
        sys.exit(1)


def main():
    print(f"Loading checkpoint: {CKPT}")
    state_dict = load_state_dict(CKPT)
    print(f"  {len(state_dict)} tensors")

    torch_model = build_torch_model(state_dict)
    keras_model = build_keras_model()
    transfer_weights(state_dict, keras_model)

    print("Building verification set...")
    x = verification_inputs()
    with torch.no_grad():
        t_logits, t_feats = torch_model(
            torch.from_numpy(x.transpose(0, 3, 1, 2)), return_features=True)
        torch_out, torch_feats = t_logits.numpy(), t_feats.numpy()
    keras_out, keras_feats = keras_model.predict(x, verbose=0)

    d = max_diff(torch_out, keras_out)
    print(f"PyTorch vs Keras   max logit diff: {d:.6f}  ({len(x)} samples)")
    if d > TOL_KERAS:
        print(f"ERROR: Keras port does not match PyTorch (tolerance {TOL_KERAS})")
        sys.exit(1)
    if not np.array_equal(torch_out.argmax(1), keras_out.argmax(1)):
        print("ERROR: Keras port predicts different classes than PyTorch")
        sys.exit(1)

    # The gate reads the feature vector directly, so a drift here would silently
    # move its decisions even while the class logits still look correct.
    df = max_diff(torch_feats, keras_feats)
    print(f"PyTorch vs Keras   max feature diff: {df:.6f}")
    if df > TOL_KERAS:
        print(f"ERROR: feature output does not match PyTorch (tolerance {TOL_KERAS})")
        sys.exit(1)

    print("Converting to TFLite...")
    model_bytes = convert_to_tflite(keras_model)

    tfl_out, tfl_feats = tflite_predict(model_bytes, x)
    torch_p, tfl_p = softmax(torch_out), softmax(tfl_out)
    prob_diff = max_diff(torch_p, tfl_p)
    agreement = float((torch_out.argmax(1) == tfl_out.argmax(1)).mean())
    print(f"PyTorch vs TFLite  max logit diff: {max_diff(torch_out, tfl_out):.6f}")
    print(f"PyTorch vs TFLite  max prob  diff: {prob_diff:.6f}")
    print(f"PyTorch vs TFLite  top-1 agreement: {agreement * 100:.1f}%")

    # A flip only counts against the model if the reference was decisive about it.
    ranked = np.sort(torch_p, axis=1)
    margins = ranked[:, -1] - ranked[:, -2]
    flipped = np.where(torch_out.argmax(1) != tfl_out.argmax(1))[0]
    decisive = [int(i) for i in flipped if margins[i] > TIE_MARGIN]
    for i in flipped:
        kind = "DECISIVE" if margins[i] > TIE_MARGIN else "near-tie, allowed"
        print(f"  sample {i}: {torch_out.argmax(1)[i]} -> {tfl_out.argmax(1)[i]} "
              f"(reference margin {margins[i]:.3f}, {kind})")
    if decisive:
        print(f"ERROR: quantization changed confident predictions at {decisive}")
        sys.exit(1)
    if prob_diff > TOL_TFLITE_PROB:
        print(f"ERROR: confidences drifted beyond tolerance {TOL_TFLITE_PROB}")
        sys.exit(1)

    check_gate_agreement(torch_feats, tfl_feats)

    os.makedirs(os.path.dirname(TFLITE), exist_ok=True)
    with open(TFLITE, "wb") as f:
        f.write(model_bytes)
    print(f"Wrote {TFLITE} ({len(model_bytes) / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
