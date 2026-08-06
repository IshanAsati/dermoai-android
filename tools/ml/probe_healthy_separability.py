#!/usr/bin/env python3
"""Can the frozen backbone already tell healthy skin from a lesion?

The shipped classifier scores ~0% on real healthy skin despite 99.6% recall on
its own validation split -- so the *head* has learned something that does not
transfer. This asks a narrower question: do the backbone's 1024-d features
still carry the distinction? If a plain logistic regression on those features
separates healthy from lesion, the fix is a cheap CPU-only head retrain rather
than a GPU fine-tune.

Healthy patches are corner crops of clinical lesion photos (real perilesional
skin). Patches from one source image never straddle a CV fold -- otherwise the
score measures memorised lighting, not generalisation.

Usage: probe_healthy_separability.py [--testdata DIR]
"""
import argparse
import os

import numpy as np
import torch
from PIL import Image
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import GroupKFold
from sklearn.metrics import roc_auc_score

from compare_pipelines import SkinCancerModel, load_torch_model, TTA, TFLite

LESION_DIRS = ["NEV", "MEL", "BCC", "SCC", "ACK", "SEK"]


def clean_skin_patch(img):
    """Reject patches spoiled by the dermatoscope's black rim or non-skin background."""
    a = np.asarray(img.resize((128, 128))).astype(np.float32)
    mx, mn = a.max(2), a.min(2)
    dark = (mx < 60).mean()
    warm = (a[..., 0] >= a[..., 2]).mean()
    sat = ((mx - mn) / (mx + 1e-6)).mean()
    return dark <= 0.10 and warm >= 0.5 and sat <= 0.45


def corner_patches(path):
    img = Image.open(path).convert("RGB")
    w, h = img.size
    side = int(min(w, h) * 0.28)
    out = []
    for x, y in [(0, 0), (w - side, 0), (0, h - side), (w - side, h - side)]:
        patch = img.crop((x, y, x + side, y + side))
        if clean_skin_patch(patch):
            out.append(patch)
    return out


def center_patch(path):
    """Same-size crop from the centre, where the lesion sits.

    Control for the obvious confound: if the lesion class is whole images while
    healthy is small corner crops, a classifier can win by detecting crop size
    and vignetting rather than skin content. Matching the geometry removes that.
    """
    img = Image.open(path).convert("RGB")
    w, h = img.size
    side = int(min(w, h) * 0.28)
    return img.crop(((w - side) // 2, (h - side) // 2,
                     (w - side) // 2 + side, (h - side) // 2 + side))


@torch.no_grad()
def features(model, images, keys, cache_dir, tflite=None):
    """1024-d penultimate activations -- the input to the final 12-way Linear.

    Cached per key: extraction is minutes of CPU and the run may be interrupted.

    With `tflite`, features come from the quantized model instead of PyTorch.
    That is what the app actually sees: int8 weights shift the vector enough to
    move gate decisions near the threshold, so a gate meant for deployment
    should be fitted on these.
    """
    os.makedirs(cache_dir, exist_ok=True)
    feats = []
    for img, key in zip(images, keys):
        cache = os.path.join(cache_dir, f"{key}.npy")
        if os.path.exists(cache):
            feats.append(np.load(cache))
            continue
        x = TTA[0](img)
        if tflite is not None:
            v = tflite.features(x)
        else:
            f = model.backbone.features(x.unsqueeze(0))
            f = model.backbone.avgpool(f)
            f = model.backbone.classifier[1](model.backbone.classifier[0](f))
            v = f.squeeze(0).numpy()
        np.save(cache, v)
        feats.append(v)
    return np.stack(feats)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--testdata", default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "testdata"))
    ap.add_argument("--geometry-control", action="store_true",
                    help="use same-size centre crops as the lesion class")
    ap.add_argument("--tflite", help="extract features from this quantized model "
                                     "instead of PyTorch (what the app sees)")
    args = ap.parse_args()

    model = load_torch_model()

    lesion_imgs, lesion_grp, lesion_key = [], [], []
    healthy_imgs, healthy_grp, healthy_key = [], [], []
    for d in LESION_DIRS:
        folder = os.path.join(args.testdata, d)
        if not os.path.isdir(folder):
            continue
        for fn in sorted(os.listdir(folder)):
            p = os.path.join(folder, fn)
            stem = os.path.splitext(fn)[0]
            if args.geometry_control:
                lesion_imgs.append(center_patch(p))
                lesion_key.append(f"lesionctr_{d}_{stem}")
            else:
                lesion_imgs.append(Image.open(p).convert("RGB"))
                lesion_key.append(f"lesion_{d}_{stem}")
            lesion_grp.append(fn)
            for i, patch in enumerate(corner_patches(p)):
                healthy_imgs.append(patch)
                healthy_grp.append(fn)   # same group as its parent image
                healthy_key.append(f"healthy_{d}_{stem}_c{i}")

    print(f"lesion images: {len(lesion_imgs)}")
    print(f"healthy patches: {len(healthy_imgs)} (from {len(set(healthy_grp))} source images)")
    if len(healthy_imgs) < 20:
        raise SystemExit("too few clean healthy patches to draw a conclusion")

    # Keep quantized features in their own cache; mixing the two would train the
    # gate on a blend of the reference and deployment feature spaces.
    tflite = TFLite(args.tflite) if args.tflite else None
    cache = os.path.join(args.testdata, "_featcache_tfl" if tflite else "_featcache")
    print(f"extracting features (CPU, cached in {cache}) ...", flush=True)
    Xl = features(model, lesion_imgs, lesion_key, cache, tflite)
    Xh = features(model, healthy_imgs, healthy_key, cache, tflite)

    X = np.concatenate([Xl, Xh])
    y = np.concatenate([np.zeros(len(Xl)), np.ones(len(Xh))])
    groups = np.array(lesion_grp + healthy_grp)

    accs, aucs = [], []
    for tr, te in GroupKFold(n_splits=5).split(X, y, groups):
        clf = LogisticRegression(max_iter=3000, C=0.1, class_weight="balanced")
        clf.fit(X[tr], y[tr])
        accs.append(clf.score(X[te], y[te]))
        aucs.append(roc_auc_score(y[te], clf.predict_proba(X[te])[:, 1]))

    print(f"\ngrouped 5-fold CV  accuracy {np.mean(accs)*100:.1f}%  "
          f"(+/- {np.std(accs)*100:.1f})   AUC {np.mean(aucs):.3f}")
    print("\nInterpretation:")
    print("  AUC > 0.95  -> features separate cleanly; a retrained head fixes this on CPU")
    print("  AUC ~ 0.5   -> backbone discards the distinction; needs real fine-tuning")


if __name__ == "__main__":
    main()
