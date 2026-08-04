#!/usr/bin/env python3
"""Summarise the per-image CSV written by compare_pipelines.py --csv.

Kept separate so an interrupted run still yields a table from whatever it
managed to record.

Usage: summarize_ladder.py results.csv [--by-class]
"""
import argparse
import csv as csvmod
from collections import defaultdict, Counter

CONFIG_NAMES = {
    "A": "A  PT   3xTTA  bias   full   (web UI)",
    "B": "B  PT   1pass  none   full",
    "C": "C  TFL  1pass  none   full",
    "D": "D  TFL  1pass  none   android  (APK today)",
    "E": "E  TFL  3xTTA  bias   fixed    (proposed)",
}


def matches(expect, label):
    return expect.split("_")[0].lower() in label.lower()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv")
    ap.add_argument("--by-class", action="store_true")
    args = ap.parse_args()

    rows = list(csvmod.DictReader(open(args.csv)))
    if not rows:
        raise SystemExit("empty csv")

    by_cfg = defaultdict(list)
    for r in rows:
        by_cfg[r["config"]].append(r)

    n_imgs = len({r["image"] for r in rows})
    print(f"{len(rows)} predictions over {n_imgs} images\n")

    print("=" * 104)
    print(f"{'config':46s} {'correct':>9s} {'healthy top1':>13s} "
          f"{'mean P(healthy)':>16s}  most common miss")
    print("=" * 104)
    for key in sorted(by_cfg):
        rs = by_cfg[key]
        correct = sum(matches(r["expect"], r["top_label"]) for r in rs)
        healthy = sum(r["top_label"].startswith("Healthy") for r in rs)
        meanh = sum(float(r["p_healthy"]) for r in rs) / len(rs)
        miss = Counter(r["top_label"] for r in rs if not matches(r["expect"], r["top_label"]))
        worst = ", ".join(f"{k.split('/')[0].strip()} x{v}" for k, v in miss.most_common(3)) or "-"
        print(f"{CONFIG_NAMES.get(key, key):46s} {correct:4d}/{len(rs):<4d} "
              f"{healthy:8d}/{len(rs):<4d} {meanh*100:14.1f}%  {worst}")
    print("=" * 104)

    if args.by_class:
        classes = sorted({r["expect"] for r in rows})
        print(f"\nper-class top-1 accuracy\n{'class':16s}" +
              "".join(f"{k:>8s}" for k in sorted(by_cfg)))
        for c in classes:
            line = f"{c:16s}"
            for key in sorted(by_cfg):
                rs = [r for r in by_cfg[key] if r["expect"] == c]
                acc = sum(matches(c, r["top_label"]) for r in rs) / len(rs) * 100 if rs else 0
                line += f"{acc:7.0f}%"
            print(line)


if __name__ == "__main__":
    main()
