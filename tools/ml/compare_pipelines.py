#!/usr/bin/env python3
"""Isolate why the APK and the web UI disagree, one difference at a time.

The two paths differ in five ways at once (checkpoint, TTA, melanoma bias,
input framing, precision), so a single side-by-side says *that* they differ,
not *which* difference matters. This runs the same images through a ladder of
configurations, each one step further from the web UI and closer to the APK:

    A  PyTorch  + 3x TTA + bias      + full frame   <- the web UI (reference)
    B  PyTorch  + 1 pass + no bias   + full frame   <- isolates TTA + bias
    C  TFLite   + 1 pass + no bias   + full frame   <- isolates conversion/int8
    D  TFLite   + 1 pass + no bias   + Android framing  <- today's APK
    E  TFLite   + 3x TTA + bias      + fixed framing    <- the proposed fix

The row where Healthy recall collapses is the culprit; E should land back at A.

Usage:
    compare_pipelines.py <image-dir> [--expect Healthy] [--tflite path]

<image-dir> may contain subdirectories named after the expected class code
(healthy/, nevus/, ...); otherwise --expect applies to every image.
"""
import argparse
import io
import os
import sys
from collections import Counter

import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image
from torchvision.models import convnext_base
import torch.nn as nn
from torchvision.transforms import v2

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")
import tensorflow as tf

PROJ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
REPO = os.path.join(PROJ, "tools", "ml", "repo")
CKPT = os.path.join(REPO, "model_weights", "ce_ls_best.pth")

MEL_IDX = 5
HEALTHY_IDX = 11
MEL_BIAS = -1.2
MEAN = [0.485, 0.456, 0.406]
STD = [0.229, 0.224, 0.225]

LABELS = ["BCC", "ACK", "NEV", "SEK", "SCC", "MEL",
          "Acne", "HairLoss", "NailFungus", "Fungal", "Vascular", "Healthy"]

# Mirrors ScanScreens.kt -- the review screen's starting crop box and decode cap.
ANDROID_CROP = (0.15, 0.2, 0.7, 0.5)   # offsetX, offsetY, width, height (fractions)
MAX_CROP_PX = 2048


# ── model ────────────────────────────────────────────────────────────────────

class SkinCancerModel(nn.Module):
    """Same graph as tools/ml/repo/app.py, minus the unused auxiliary heads."""

    def __init__(self, num_classes=12):
        super().__init__()
        self.backbone = convnext_base(weights=None)
        in_features = self.backbone.classifier[2].in_features
        self.backbone.classifier[2] = nn.Sequential(
            nn.Dropout(p=0.4),
            nn.Linear(in_features, 512),
            nn.GELU(),
            nn.Dropout(p=0.2),
            nn.Linear(512, num_classes),
        )
        self.projection_head = nn.Sequential(
            nn.Linear(in_features, 256), nn.GELU(), nn.Linear(256, 128))
        self.binary_head = nn.Sequential(
            nn.Linear(in_features, 256), nn.GELU(), nn.Linear(256, 2))

    def forward(self, x):
        x = self.backbone.features(x)
        x = self.backbone.avgpool(x)
        f = self.backbone.classifier[1](self.backbone.classifier[0](x))
        return self.backbone.classifier[2](f)


def load_torch_model():
    model = SkinCancerModel()
    ckpt = torch.load(CKPT, map_location="cpu", weights_only=False)
    sd = ckpt.get("model_state_dict", ckpt.get("model", ckpt))
    missing, unexpected = model.load_state_dict(sd, strict=False)
    # strict=False hides a mismatched head behind random weights -- refuse to run.
    if missing:
        sys.exit(f"FATAL: {len(missing)} params missing from checkpoint: {missing[:5]}")
    model.eval()
    return model


# ── transforms ───────────────────────────────────────────────────────────────

# Copied verbatim from app.py:116-135 so the reference path is not re-derived.
TTA = [
    v2.Compose([v2.Resize((224, 224)),
                v2.ToImage(), v2.ToDtype(torch.float32, scale=True),
                v2.Normalize(mean=MEAN, std=STD)]),
    v2.Compose([v2.Resize(256), v2.CenterCrop(224), v2.RandomHorizontalFlip(p=1.0),
                v2.ToImage(), v2.ToDtype(torch.float32, scale=True),
                v2.Normalize(mean=MEAN, std=STD)]),
    v2.Compose([v2.Resize((224, 224)), v2.RandomVerticalFlip(p=1.0),
                v2.ToImage(), v2.ToDtype(torch.float32, scale=True),
                v2.Normalize(mean=MEAN, std=STD)]),
]


def android_framing(img):
    """Reproduce what the bitmap actually looks like by the time the APK sees it.

    decodeUpright (power-of-two subsample to >=2048px) -> crop to the review
    screen's default rect -> JPEG q=95 round-trip -> decode again -> plain
    bilinear squash to 224 with no area averaging.
    """
    # decodeUpright: inSampleSize is limited to powers of two.
    sample = 1
    while max(img.size) // (sample * 2) >= MAX_CROP_PX:
        sample *= 2
    if sample > 1:
        img = img.reduce(sample)  # box average, like BitmapFactory

    w, h = img.size
    ox, oy, cw, ch = ANDROID_CROP
    left, top = int(ox * w), int(oy * h)
    img = img.crop((left, top, left + int(cw * w), top + int(ch * h)))

    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=95)
    img = Image.open(buf).convert("RGB")

    # Bitmap.createScaledBitmap(filter=true) is bilinear WITHOUT antialiasing.
    x = torch.from_numpy(np.asarray(img).copy()).permute(2, 0, 1).float().div_(255)
    x = F.interpolate(x.unsqueeze(0), size=(224, 224), mode="bilinear",
                      align_corners=False, antialias=False).squeeze(0)
    return v2.Normalize(mean=MEAN, std=STD)(x)


def fixed_framing(img):
    """The proposed fix: full frame, centered square, properly antialiased."""
    w, h = img.size
    side = min(w, h)
    img = img.crop(((w - side) // 2, (h - side) // 2,
                    (w - side) // 2 + side, (h - side) // 2 + side))
    x = torch.from_numpy(np.asarray(img).copy()).permute(2, 0, 1).float().div_(255)
    x = F.interpolate(x.unsqueeze(0), size=(224, 224), mode="bilinear",
                      align_corners=False, antialias=True).squeeze(0)
    return v2.Normalize(mean=MEAN, std=STD)(x)


# ── inference backends ───────────────────────────────────────────────────────

def torch_logits(model, batch):
    with torch.no_grad():
        return [model(t.unsqueeze(0))[0] for t in batch]


FEATURE_DIM = 1024


class TFLite:
    """Wraps the exported model, which may have one output (logits) or two
    (logits + the 1024-d features the healthy gate scores). TFLite does not
    preserve Keras output order, so tensors are matched by width."""

    def __init__(self, path):
        self.interp = tf.lite.Interpreter(model_path=path, num_threads=4)
        self.interp.allocate_tensors()
        self.inp = self.interp.get_input_details()[0]
        self.out = None
        self.feat_out = None
        for o in self.interp.get_output_details():
            if o["shape"][-1] == len(LABELS):
                self.out = o
            elif o["shape"][-1] == FEATURE_DIM:
                self.feat_out = o
        if self.out is None:
            raise SystemExit(f"{path}: no output of width {len(LABELS)}")

    def _run(self, t):
        # torch CHW -> TFLite NHWC
        x = t.permute(1, 2, 0).unsqueeze(0).numpy().astype(np.float32)
        self.interp.set_tensor(self.inp["index"], x)
        self.interp.invoke()

    def __call__(self, batch):
        outs = []
        for t in batch:
            self._run(t)
            outs.append(torch.from_numpy(self.interp.get_tensor(self.out["index"])[0]))
        return outs

    def features(self, t):
        """1024-d feature vector for a single preprocessed CHW tensor."""
        if self.feat_out is None:
            raise SystemExit("this .tflite has no feature output; reconvert with "
                             "tools/ml/convert_finetuned_tflite.py")
        self._run(t)
        return self.interp.get_tensor(self.feat_out["index"])[0]

    def both(self, t):
        """(logits, features) from ONE invoke -- calling __call__ then features()
        runs the network twice and doubles the cost of a large evaluation."""
        self._run(t)
        return (self.interp.get_tensor(self.out["index"])[0],
                self.interp.get_tensor(self.feat_out["index"])[0])

    def both_batch(self, batch):
        """Same, for a stack of CHW tensors. Batching amortises the per-invoke
        overhead, which dominates at 224x224 on CPU."""
        x = np.stack([t.permute(1, 2, 0).numpy() for t in batch]).astype(np.float32)
        if tuple(self.interp.get_input_details()[0]["shape"]) != x.shape:
            self.interp.resize_tensor_input(self.inp["index"], x.shape)
            self.interp.allocate_tensors()
            self.inp = self.interp.get_input_details()[0]
            self.out = self.interp.get_output_details()[
                [o["shape"][-1] for o in self.interp.get_output_details()].index(len(LABELS))]
            self.feat_out = self.interp.get_output_details()[
                [o["shape"][-1] for o in self.interp.get_output_details()].index(FEATURE_DIM)]
        self.interp.set_tensor(self.inp["index"], x)
        self.interp.invoke()
        return (self.interp.get_tensor(self.out["index"]),
                self.interp.get_tensor(self.feat_out["index"]))


def combine(logit_list, bias):
    """Softmax each pass then average -- app.py:191-195. Averaging logits differs."""
    probs = []
    for lg in logit_list:
        lg = lg.clone()
        if bias:
            lg[MEL_IDX] += bias
        probs.append(torch.softmax(lg, 0))
    return torch.stack(probs).mean(0)


# ── ladder ───────────────────────────────────────────────────────────────────

def build_configs(model, tflite):
    def pt(batch_fn, bias):
        return lambda img: combine(torch_logits(model, batch_fn(img)), bias)

    def tfl(batch_fn, bias):
        return lambda img: combine(tflite(batch_fn(img)), bias)

    full3 = lambda img: [t(img) for t in TTA]
    full1 = lambda img: [TTA[0](img)]
    andr = lambda img: [android_framing(img)]

    def fixed3(img):
        base = fixed_framing(img)
        return [base, torch.flip(base, dims=[2]), torch.flip(base, dims=[1])]

    cfgs = [
        ("A  PT   3xTTA  bias   full   (web UI)", pt(full3, MEL_BIAS)),
        ("B  PT   1pass  none   full", pt(full1, 0.0)),
    ]
    if tflite:
        cfgs += [
            ("C  TFL  1pass  none   full", tfl(full1, 0.0)),
            ("D  TFL  1pass  none   android  (APK today)", tfl(andr, 0.0)),
            ("E  TFL  3xTTA  bias   fixed    (proposed)", tfl(fixed3, MEL_BIAS)),
        ]
    return cfgs


def collect(image_dir, default_expect):
    items = []
    for root, _, files in os.walk(image_dir):
        for fn in sorted(files):
            if not fn.lower().endswith((".jpg", ".jpeg", ".png", ".webp", ".bmp")):
                continue
            rel = os.path.relpath(root, image_dir)
            expect = default_expect if rel == "." else rel.split(os.sep)[0]
            items.append((os.path.join(root, fn), expect))
    return items


def match(expect, idx):
    # Folder names may carry a qualifier ("Healthy_proxy" -> "Healthy").
    if not expect:
        return False
    return expect.split("_")[0].lower() in LABELS[idx].lower()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image_dir")
    ap.add_argument("--expect", default="Healthy")
    ap.add_argument("--tflite", default=os.path.join(
        PROJ, "app", "src", "main", "assets", "ml", "skin_model.tflite"))
    ap.add_argument("--per-image", action="store_true")
    # ConvNeXt-Base on CPU is slow enough that a long run can be interrupted;
    # results are appended per image so a rerun resumes instead of restarting.
    ap.add_argument("--csv", help="append per-image results here and resume from it")
    args = ap.parse_args()

    torch.set_num_threads(os.cpu_count() or 4)

    items = collect(args.image_dir, args.expect)
    if not items:
        sys.exit(f"No images found under {args.image_dir}")

    done = set()
    if args.csv and os.path.exists(args.csv):
        with open(args.csv) as f:
            for line in f.readlines()[1:]:
                done.add(line.split(",")[0])
        print(f"resuming: {len(done)} images already recorded in {args.csv}")

    print(f"{len(items)} images from {args.image_dir}")

    model = load_torch_model()
    tflite = TFLite(args.tflite) if os.path.exists(args.tflite) else None
    if not tflite:
        print(f"WARNING: {args.tflite} missing -- running configs A/B only")

    configs = build_configs(model, tflite)
    results = {name: [] for name, _ in configs}

    csv = None
    if args.csv:
        new = not os.path.exists(args.csv)
        csv = open(args.csv, "a", buffering=1)
        if new:
            csv.write("image,expect,config,top_label,top_prob,p_healthy\n")

    for path, expect in items:
        key = os.path.basename(path)
        img = Image.open(path).convert("RGB")
        if args.per_image and key not in done:
            print(f"\n{key}  (expect {expect})", flush=True)
        for name, fn in configs:
            if key in done:
                continue
            p = fn(img)
            top = int(p.argmax())
            results[name].append((expect, top, float(p[HEALTHY_IDX]), float(p[top])))
            if csv:
                csv.write(f"{key},{expect},{name.split()[0]},{LABELS[top]},"
                          f"{float(p[top]):.4f},{float(p[HEALTHY_IDX]):.4f}\n")
            if args.per_image:
                print(f"   {name:44s} -> {LABELS[top]:11s} {p[top]*100:5.1f}%"
                      f"   healthy={p[HEALTHY_IDX]*100:5.1f}%", flush=True)
    if csv:
        csv.close()
    if not any(results.values()):
        sys.exit(f"\nAll images already recorded. Summarise with: "
                 f"python tools/ml/summarize_ladder.py {args.csv}")

    print("\n" + "=" * 100)
    print(f"{'config':46s} {'correct':>8s} {'healthy top1':>13s} "
          f"{'mean P(healthy)':>16s}  most common miss")
    print("=" * 100)
    for name, _ in configs:
        rows = results[name]
        correct = sum(match(e, t) for e, t, _, _ in rows)
        healthy_top = sum(t == HEALTHY_IDX for _, t, _, _ in rows)
        mean_h = float(np.mean([h for _, _, h, _ in rows]))
        misses = Counter(LABELS[t] for e, t, _, _ in rows if not match(e, t))
        worst = ", ".join(f"{k} x{v}" for k, v in misses.most_common(3)) or "-"
        print(f"{name:46s} {correct:3d}/{len(rows):<4d} {healthy_top:8d}/{len(rows):<4d} "
              f"{mean_h*100:14.1f}%  {worst}")
    print("=" * 100)


if __name__ == "__main__":
    main()
