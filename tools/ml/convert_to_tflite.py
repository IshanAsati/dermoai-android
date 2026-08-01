#!/usr/bin/env python3
"""Convert PT ConvNeXt → TFLite via Keras. Norm-before-pool fixed."""
import os, sys
import numpy as np
import torch

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
import tensorflow as tf
from tensorflow.keras import layers, Model, applications

PROJ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CKPT = os.path.join(PROJ, "tools", "ml", "source", "ce_ls_best.pth")
TFLITE = os.path.join(PROJ, "app", "src", "main", "assets", "ml", "skin_model.tflite")


def load_pt(path):
    ck = torch.load(path, map_location="cpu", weights_only=False)
    return ck.get("model_state_dict", ck.get("model", ck))


def make_tf_model():
    backbone = applications.ConvNeXtBase(
        include_top=False, weights=None, input_shape=(224, 224, 3),
    )

    # Neutralize input normalization (app does it externally)
    norm = backbone.get_layer("convnext_base_prestem_normalization")
    norm.mean = np.array([0.0, 0.0, 0.0], dtype=np.float32)
    norm.variance = np.array([1.0, 1.0, 1.0], dtype=np.float32)

    # Connect to last block output (skip backbone's final layer_normalization)
    # PT: features → avgpool → LayerNorm (classifier.0) → head
    # TF: backbone → avgpool → head_layernorm → head
    last_block = backbone.get_layer("convnext_base_stage_3_block_2_identity")

    x = layers.GlobalAveragePooling2D(name="avgpool")(last_block.output)
    x = layers.LayerNormalization(epsilon=1e-6, name="head_layernorm")(x)
    x = layers.Dropout(0.4, name="head_dropout_1")(x)
    x = layers.Dense(512, name="head_dense_1")(x)
    gelu = lambda x: x * 0.5 * (1.0 + tf.math.erf(x / tf.sqrt(2.0)))
    x = layers.Lambda(gelu, name="head_gelu")(x)
    x = layers.Dropout(0.2, name="head_dropout_2")(x)
    x = layers.Dense(12, name="head_dense_2")(x)
    return Model(inputs=backbone.input, outputs=x, name="DermoAI")


def transfer(pt_sd, tf_model):
    seq = []

    # Stem: conv (kernel+bias) + layernorm (gamma+beta)
    seq += ["backbone.features.0.0.weight", "backbone.features.0.0.bias",
            "backbone.features.0.1.weight", "backbone.features.0.1.bias"]

    # Stages: depths [3, 3, 27, 3]
    pt_stages = [("1", 3, "2"), ("3", 3, "4"), ("5", 27, "6"), ("7", 3, None)]

    for stage_key, n_blocks, ds_key in pt_stages:
        for b in range(n_blocks):
            base = f"backbone.features.{stage_key}.{b}"
            seq += [f"{base}.block.0.weight", f"{base}.block.0.bias",
                    f"{base}.block.2.weight", f"{base}.block.2.bias",
                    f"{base}.block.3.weight", f"{base}.block.3.bias",
                    f"{base}.block.5.weight", f"{base}.block.5.bias",
                    f"{base}.layer_scale"]
        if ds_key:
            ds_base = f"backbone.features.{ds_key}"
            seq += [f"{ds_base}.0.weight", f"{ds_base}.0.bias",
                    f"{ds_base}.1.weight", f"{ds_base}.1.bias"]

    # Head: norm + 2 linear layers
    seq += ["backbone.classifier.0.weight", "backbone.classifier.0.bias",
            "backbone.classifier.2.1.weight", "backbone.classifier.2.1.bias",
            "backbone.classifier.2.4.weight", "backbone.classifier.2.4.bias"]

    missing = [k for k in seq if k not in pt_sd]
    if missing:
        print(f"Missing PT keys: {missing}")
        sys.exit(1)

    tw_list = tf_model.trainable_weights
    print(f"PT seq: {len(seq)}, TF weights: {len(tw_list)}")
    assert len(seq) == len(tw_list), f"Count mismatch {len(seq)} vs {len(tw_list)}"

    errors = 0
    for i, (pt_key, tw) in enumerate(zip(seq, tw_list)):
        v = pt_sd[pt_key].numpy()

        # LayerScale is 3D (C,1,1) → squeeze
        if len(v.shape) == 3 and v.shape[1] == 1 and v.shape[2] == 1:
            v = v.reshape(-1)
        # 4D Conv: PT NCHW (out,in,H,W) → TF NHWC (H,W,in,out)
        elif len(v.shape) == 4:
            v = np.transpose(v, (2, 3, 1, 0))
        # 2D Linear: PT (out,in) → TF (in,out)
        elif len(v.shape) == 2:
            v = v.T

        if tw.shape != v.shape:
            errors += 1
            if errors <= 5:
                print(f"  SHAPE {i}: {pt_key} {pt_sd[pt_key].shape}→{v.shape} vs TF{tw.shape}")
            continue
        tw.assign(v)

    if errors:
        print(f"  {errors} shape errors")
        sys.exit(1)
    print(f"All {len(tw_list)} weights transferred OK")


if __name__ == "__main__":
    print(f"Loading PT: {CKPT}")
    pt_sd = load_pt(CKPT)
    print(f"  {len(pt_sd)} keys")

    print("Building TF model...")
    tf_model = make_tf_model()

    transfer(pt_sd, tf_model)

    print("Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_keras_model(tf_model)
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    os.makedirs(os.path.dirname(TFLITE), exist_ok=True)
    with open(TFLITE, "wb") as f:
        f.write(tflite_model)
    print(f"TFLite: {TFLITE} ({len(tflite_model)/1e6:.1f} MB)")
