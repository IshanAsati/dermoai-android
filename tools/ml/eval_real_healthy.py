#!/usr/bin/env python3
"""Measure the healthy gate on real phone photos of healthy skin.

Everything in the pipeline so far was validated on ISIC perilesional crops --
real skin, but clinically lit and framed. This is the first genuinely
out-of-distribution test: photos taken on a phone, which is how the app is
actually used.

PRIVACY: runs entirely locally. Images are read, turned into numbers, and
discarded. Nothing is uploaded, cached outside the run, or written back.

Usage: eval_real_healthy.py <image-dir> [--tflite PATH]
"""
import argparse
import os
import sys

import numpy as np

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import json
from PIL import Image

try:  # iPhone photos are HEIC
    import pillow_heif
    pillow_heif.register_heif_opener()
except ImportError:
    pass

from compare_pipelines import TFLite, TTA, LABELS, fixed_framing, android_framing

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(PROJ, "app", "src", "main", "assets", "ml")
HEALTHY_IDX = 11


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image_dir")
    ap.add_argument("--tflite", default=os.path.join(ASSETS, "skin_model.tflite"))
    ap.add_argument("--gate", default=os.path.join(ASSETS, "healthy_gate.json"))
    args = ap.parse_args()

    tfl = TFLite(args.tflite)
    gate = json.load(open(args.gate))
    w = np.array(gate["weights"], np.float32)
    b, thr = float(gate["bias"]), float(gate["threshold"])
    print(f"gate threshold {thr:.4f} | projected recall from ISIC crops: "
          f"{gate['metrics']['healthy_recall']*100:.1f}%\n")

    files = sorted(f for f in os.listdir(args.image_dir)
                   if f.lower().endswith((".jpg", ".jpeg", ".png", ".webp", ".heic", ".bmp")))
    if not files:
        sys.exit(f"no images in {args.image_dir}")

    # Two framings: what the app does today, and the centre-square variant.
    rows = []
    print(f"{'file':16s} {'framing':9s} {'head says':11s} {'conf':>6s} {'gate':>7s}  result")
    print("-" * 74)
    for fn in files:
        img = Image.open(os.path.join(args.image_dir, fn)).convert("RGB")
        for name, tensor in (("centre", fixed_framing(img)), ("app-crop", android_framing(img))):
            logits = tfl([tensor])[0].numpy()
            e = np.exp(logits - logits.max())
            probs = e / e.sum()
            top = int(probs.argmax())
            score = float(1.0 / (1.0 + np.exp(-(tfl.features(tensor) @ w + b))))
            fired = score >= thr
            rows.append((name, top, score, fired))
            print(f"{fn[:15]:16s} {name:9s} {LABELS[top]:11s} {probs[top]*100:5.1f}% "
                  f"{score:7.3f}  {'HEALTHY (gated)' if fired else LABELS[top]}")
        print()

    print("=" * 74)
    for name in ("centre", "app-crop"):
        rs = [r for r in rows if r[0] == name]
        gated = sum(r[3] for r in rs)
        head_healthy = sum(r[1] == HEALTHY_IDX for r in rs)
        scores = [r[2] for r in rs]
        print(f"{name:9s}  head alone: {head_healthy}/{len(rs)} healthy   "
              f"with gate: {gated}/{len(rs)} ({gated/len(rs)*100:.0f}%)   "
              f"gate score median {np.median(scores):.3f}")
    print("=" * 74)


if __name__ == "__main__":
    main()
