#!/usr/bin/env python3
"""Train the healthy-vs-lesion gate that the 12-way head fails to provide.

Class 11 scores 99.6% recall on the checkpoint's validation split and 0/9 on
real skin -- the head learned a shortcut. The backbone's 1024-d features do
still carry the distinction (geometry-controlled AUC 0.963), so this fits a
single linear layer over them and exports it for the app.

The threshold is deliberately not 0.5. Calling a lesion "healthy" tells someone
their melanoma is fine, which is far worse than failing to reassure them, so
the operating point is chosen for a target lesion-false-positive rate instead.

Usage: train_healthy_gate.py [--max-fpr 0.02] [--out ../../app/.../healthy_gate.json]
"""
import argparse
import json
import os

import numpy as np
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import GroupKFold
from sklearn.metrics import roc_auc_score, roc_curve

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.dirname(os.path.dirname(HERE))
CACHE = os.path.join(HERE, "testdata", "_featcache")


def load_cache(cache_dir):
    """Load features written by probe_healthy_separability.py.

    Keys look like  lesion_<CLASS>_<ISIC_ID>  /  lesionctr_<CLASS>_<ISIC_ID>
    /  healthy_<CLASS>_<ISIC_ID>_c<N>.  The source image id is the CV group so
    that patches from one photo never straddle a fold.
    """
    X, y, groups, kinds, classes = [], [], [], [], []
    for fn in sorted(os.listdir(cache_dir)):
        if not fn.endswith(".npy"):
            continue
        stem = fn[:-4]
        parts = stem.split("_")
        kind = parts[0]
        if kind not in ("lesion", "lesionctr", "healthy"):
            continue
        # ISIC ids are themselves underscore-joined (ISIC_0123456).
        group = "_".join(parts[2:4]) if len(parts) >= 4 else stem
        X.append(np.load(os.path.join(cache_dir, fn)))
        y.append(1 if kind == "healthy" else 0)
        groups.append(group)
        kinds.append(kind)
        classes.append(parts[1] if len(parts) > 1 else "?")   # MEL / NEV / PHONE / ...
    return (np.stack(X), np.array(y), np.array(groups),
            np.array(kinds), np.array(classes))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=CACHE)
    ap.add_argument("--max-fpr", type=float, default=0.02,
                    help="largest acceptable rate of lesions called healthy")
    ap.add_argument("--threshold", type=float,
                    help="set the operating point directly instead of deriving it "
                         "from --max-fpr (see the per-class sweep before using this)")
    ap.add_argument("--out", default=os.path.join(
        PROJ, "app", "src", "main", "assets", "ml", "healthy_gate.json"))
    args = ap.parse_args()

    X, y, groups, kinds, mel_class = load_cache(args.cache)
    print(f"{len(X)} feature vectors  |  healthy {int(y.sum())}  lesion {int((1-y).sum())}")
    print(f"source images: {len(set(groups))}")

    # Honest estimate first: fit and score only on held-out source images.
    oof = np.zeros(len(y))
    for tr, te in GroupKFold(n_splits=5).split(X, y, groups):
        clf = LogisticRegression(max_iter=3000, C=0.1, class_weight="balanced")
        clf.fit(X[tr], y[tr])
        oof[te] = clf.predict_proba(X[te])[:, 1]

    auc = roc_auc_score(y, oof)
    fpr, tpr, thr = roc_curve(y, oof)
    if args.threshold is not None:
        threshold = float(args.threshold)
        recall = float((oof[y == 1] >= threshold).mean())
        achieved_fpr = float((oof[y == 0] >= threshold).mean())
    else:
        ok = np.where(fpr <= args.max_fpr)[0]
        i = ok[-1] if len(ok) else 0
        threshold, recall, achieved_fpr = float(thr[i]), float(tpr[i]), float(fpr[i])

    # Melanoma is the one class where a false "healthy" could be lethal, so the
    # operating point is checked against it directly rather than trusting the
    # pooled false-positive rate.
    mel = oof[(y == 0) & np.array([k == "MEL" for k in mel_class])]
    if len(mel):
        print(f"melanoma safety margin: {len(mel)} MEL samples, "
              f"max gate score {mel.max():.3f} vs threshold {threshold:.3f}"
              f"{'  *** LEAK ***' if mel.max() >= threshold else '  (clear)'}")

    print(f"\nout-of-fold AUC {auc:.4f}")
    print(f"operating point: threshold {threshold:.4f}")
    print(f"  healthy recall      {recall*100:.1f}%   (healthy skin correctly gated)")
    print(f"  lesion false-pos    {achieved_fpr*100:.2f}%   (lesions wrongly called healthy)")

    # Ship a model fit on everything, evaluated by the numbers above.
    final = LogisticRegression(max_iter=3000, C=0.1, class_weight="balanced")
    final.fit(X, y)

    payload = {
        "_comment": "Healthy-vs-lesion gate over the 1024-d penultimate features. "
                    "Fires only above `threshold`; see tools/ml/train_healthy_gate.py.",
        "weights": final.coef_[0].tolist(),
        "bias": float(final.intercept_[0]),
        "threshold": threshold,
        "metrics": {
            "oof_auc": round(auc, 4),
            "healthy_recall": round(recall, 4),
            "lesion_false_positive_rate": round(achieved_fpr, 4),
            "n_healthy": int(y.sum()),
            "n_lesion": int((1 - y).sum()),
            "n_source_images": len(set(groups)),
        },
        "caveat": "Healthy examples are perilesional crops of ISIC clinical photos, "
                  "not smartphone photos of normal skin. Revalidate on real phone "
                  "photos before trusting the recall figure.",
    }
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w") as f:
        json.dump(payload, f, indent=2)
    print(f"\nwrote {args.out}")


if __name__ == "__main__":
    main()
