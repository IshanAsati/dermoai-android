#!/usr/bin/env python3
"""Are real phone photos of healthy skin separable from lesions in feature space?

The gate fitted on ISIC perilesional crops scored ~0.05 on real phone photos of
healthy skin -- it learned the crop, not the skin. This asks whether the
backbone can still tell the two apart when the healthy examples are actual
phone photos, which decides whether retraining is worth anything.

Grouping is by source photograph, so patches from one photo never straddle a
fold; with only ~11 source photos that is the difference between a real
estimate and a memorised one.

PRIVACY: local only. Features are derived numbers, never the images.

Usage: probe_phone_healthy.py <phone-photo-dir> [--patches 12]
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import GroupKFold
from sklearn.metrics import roc_auc_score

try:
    import pillow_heif
    pillow_heif.register_heif_opener()
except ImportError:
    pass

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from compare_pipelines import TFLite, TTA  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(PROJ, "app", "src", "main", "assets", "ml")
LESION_CACHE = os.path.join(HERE, "testdata", "_featcache_tfl")


def patches(img, n):
    """A grid of square crops, so one photo yields several independent-ish views."""
    w, h = img.size
    side = int(min(w, h) * 0.45)
    out, k = [], int(np.ceil(np.sqrt(n)))
    xs = np.linspace(0, max(0, w - side), k).astype(int)
    ys = np.linspace(0, max(0, h - side), k).astype(int)
    for y in ys:
        for x in xs:
            if len(out) >= n:
                return out
            out.append(img.crop((x, y, x + side, y + side)))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image_dir")
    ap.add_argument("--patches", type=int, default=12)
    ap.add_argument("--tflite", default=os.path.join(ASSETS, "skin_model.tflite"))
    args = ap.parse_args()

    tfl = TFLite(args.tflite)

    # Healthy: real phone photos.
    Xh, gh = [], []
    files = sorted(f for f in os.listdir(args.image_dir)
                   if f.lower().endswith((".jpg", ".jpeg", ".png", ".heic", ".webp")))
    print(f"phone photos: {len(files)}  x {args.patches} patches", flush=True)
    for fn in files:
        img = Image.open(os.path.join(args.image_dir, fn)).convert("RGB")
        for p in patches(img, args.patches):
            Xh.append(tfl.features(TTA[0](p)))
            gh.append(fn)

    # Lesion negatives: reuse the cached ISIC features (same quantized model).
    Xl, gl = [], []
    for fn in sorted(os.listdir(LESION_CACHE)):
        if fn.startswith(("lesion_", "lesionctr_")) and fn.endswith(".npy"):
            Xl.append(np.load(os.path.join(LESION_CACHE, fn)))
            gl.append(fn)
    print(f"lesion features: {len(Xl)} (cached ISIC)")

    X = np.concatenate([np.stack(Xh), np.stack(Xl)])
    y = np.concatenate([np.ones(len(Xh)), np.zeros(len(Xl))])
    groups = np.array(gh + gl)

    oof = np.zeros(len(y))
    for tr, te in GroupKFold(n_splits=5).split(X, y, groups):
        clf = LogisticRegression(max_iter=3000, C=0.1, class_weight="balanced")
        clf.fit(X[tr], y[tr])
        oof[te] = clf.predict_proba(X[te])[:, 1]

    auc = roc_auc_score(y, oof)
    print(f"\ngrouped 5-fold CV (by source photo)  AUC {auc:.4f}")
    hs, ls = oof[y == 1], oof[y == 0]
    print(f"  healthy phone patches: median {np.median(hs):.3f}  "
          f"[{np.percentile(hs,10):.3f} - {np.percentile(hs,90):.3f}]")
    print(f"  ISIC lesions:          median {np.median(ls):.3f}  "
          f"[{np.percentile(ls,10):.3f} - {np.percentile(ls,90):.3f}]")
    print("\n  AUC > 0.95 -> retraining on phone photos should work")
    print("  AUC < 0.85 -> the two are entangled; needs more/better data")


if __name__ == "__main__":
    main()
