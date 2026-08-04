#!/usr/bin/env python3
"""Full evaluation of the shipped model + healthy gate on a large image set.

Reports what the app actually shows a user -- the head's ranking with the gate
override applied -- not just raw classifier accuracy. Three things matter here
and none are the headline number:

  * cancer sensitivity: a malignant lesion called benign is the harmful error
  * gate false positives: a lesion shown as "Healthy / Normal Skin"
  * per-class recall: the pooled figure hides that SCC and MEL are the weak ones

Resumable -- results append per image, so an interrupted run continues where it
stopped. Summarise at any point with --report-only.

    python tools/ml/eval_bulk.py --csv results.csv
    python tools/ml/eval_bulk.py --csv results.csv --report-only

PRIVACY: local only. If --healthy-dir points at personal photos, only derived
numbers reach the CSV, never the images.
"""
import argparse
import csv as csvmod
import os
import sys
import time
from collections import Counter, defaultdict

import numpy as np

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import json
from PIL import Image

try:
    import pillow_heif
    pillow_heif.register_heif_opener()
except ImportError:
    pass

from compare_pipelines import TFLite, TTA, LABELS   # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(PROJ, "app", "src", "main", "assets", "ml")

CODES = ["BCC", "ACK", "NEV", "SEK", "SCC", "MEL",
         "Acne", "HairLoss", "NailFungus", "Fungal", "Vascular", "Healthy"]
MALIGNANT = {"BCC", "SCC", "MEL"}          # what must not be missed
HEALTHY_CODE = "Healthy"


def load_assets(tflite_path, gate_path):
    tfl = TFLite(tflite_path)
    gate = None
    if os.path.exists(gate_path):
        g = json.load(open(gate_path))
        gate = (np.array(g["weights"], np.float32), float(g["bias"]),
                float(g["threshold"]), g.get("metrics", {}))
    return tfl, gate


def collect(evalset, healthy_dir):
    """(path, true_code) pairs. Directory name is the label."""
    items = []
    if evalset and os.path.isdir(evalset):
        for code in sorted(os.listdir(evalset)):
            d = os.path.join(evalset, code)
            if not os.path.isdir(d) or code.startswith("_"):
                continue
            for fn in sorted(os.listdir(d)):
                if fn.lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                    items.append((os.path.join(d, fn), code))
    if healthy_dir and os.path.isdir(healthy_dir):
        for fn in sorted(os.listdir(healthy_dir)):
            if fn.lower().endswith((".jpg", ".jpeg", ".png", ".webp", ".heic")):
                items.append((os.path.join(healthy_dir, fn), HEALTHY_CODE))
    return items


def run(items, tfl, gate, csv_path, done):
    new = 0
    t0 = time.time()
    f = open(csv_path, "a", newline="", buffering=1)
    w = csvmod.writer(f)
    if not done:
        w.writerow(["image", "true", "head_pred", "head_conf", "gate_score",
                    "gate_fired", "final_pred"])
    todo = [(p, t) for p, t in items if os.path.basename(p) not in done]
    print(f"{len(todo)} to process ({len(done)} already done)", flush=True)

    for i, (path, true_code) in enumerate(todo, 1):
        try:
            img = Image.open(path).convert("RGB")
        except Exception:
            continue
        t = TTA[0](img)
        logits, feats = tfl.both(t)
        e = np.exp(logits.astype(np.float64) - logits.max())
        probs = e / e.sum()
        head = int(probs.argmax())

        gscore, fired = "", False
        if gate is not None:
            gw, gb, gthr, _ = gate
            gscore = float(1.0 / (1.0 + np.exp(-(float(feats @ gw) + gb))))
            fired = gscore >= gthr
        final = HEALTHY_CODE if fired else CODES[head]

        w.writerow([os.path.basename(path), true_code, CODES[head],
                    f"{probs[head]:.4f}",
                    f"{gscore:.4f}" if gscore != "" else "", int(fired), final])
        new += 1
        if i % 100 == 0:
            rate = i / (time.time() - t0)
            print(f"  {i}/{len(todo)}  {rate:.1f} img/s  "
                  f"eta {(len(todo)-i)/max(rate,1e-9)/60:.1f} min", flush=True)
    f.close()
    return new


# ── reporting ────────────────────────────────────────────────────────────────

def report(csv_path, gate_metrics=None):
    rows = list(csvmod.DictReader(open(csv_path)))
    if not rows:
        sys.exit("no results yet")
    lesion = [r for r in rows if r["true"] != HEALTHY_CODE]
    healthy = [r for r in rows if r["true"] == HEALTHY_CODE]

    print("=" * 78)
    print(f"EVALUATION  —  {len(rows)} images "
          f"({len(lesion)} lesion, {len(healthy)} healthy)")
    print("=" * 78)

    def ci(k, n):
        """95% Wald interval, so a per-class number is read with its uncertainty."""
        if n == 0:
            return 0.0, 0.0
        p = k / n
        return p * 100, 1.96 * (p * (1 - p) / n) ** 0.5 * 100

    # 1. head accuracy per class
    print("\n1. TWELVE-CLASS HEAD  (top-1, gate not applied)")
    print(f"   {'class':6s} {'n':>5s} {'recall':>18s}   most confused with")
    per = defaultdict(list)
    for r in lesion:
        per[r["true"]].append(r)
    tot_ok = 0
    for code in sorted(per):
        rs = per[code]
        ok = sum(r["head_pred"] == code for r in rs)
        tot_ok += ok
        p, e = ci(ok, len(rs))
        wrong = Counter(r["head_pred"] for r in rs if r["head_pred"] != code)
        top = ", ".join(f"{k} {v}" for k, v in wrong.most_common(2)) or "-"
        print(f"   {code:6s} {len(rs):5d} {p:9.1f}% +/-{e:4.1f}   {top}")
    if lesion:
        p, e = ci(tot_ok, len(lesion))
        print(f"   {'ALL':6s} {len(lesion):5d} {p:9.1f}% +/-{e:4.1f}")

    # 2. the error that actually harms someone
    print("\n2. CANCER SAFETY  (malignant = BCC, SCC, MEL)")
    mal = [r for r in lesion if r["true"] in MALIGNANT]
    ben = [r for r in lesion if r["true"] not in MALIGNANT]
    caught = sum(r["final_pred"] in MALIGNANT for r in mal)
    p, e = ci(caught, len(mal))
    print(f"   sensitivity  {p:.1f}% +/-{e:.1f}   "
          f"({caught}/{len(mal)} malignant flagged as malignant)")
    tn = sum(r["final_pred"] not in MALIGNANT for r in ben)
    p, e = ci(tn, len(ben))
    print(f"   specificity  {p:.1f}% +/-{e:.1f}   ({tn}/{len(ben)} benign not alarmed)")
    for code in ("MEL", "SCC", "BCC"):
        rs = [r for r in lesion if r["true"] == code]
        if rs:
            k = sum(r["final_pred"] in MALIGNANT for r in rs)
            p, e = ci(k, len(rs))
            print(f"   {code} caught as some malignancy: {p:5.1f}% +/-{e:4.1f} ({k}/{len(rs)})")

    # 3. the gate
    if any(r["gate_score"] for r in rows):
        print("\n3. HEALTHY GATE")
        if healthy:
            k = sum(int(r["gate_fired"]) for r in healthy)
            p, e = ci(k, len(healthy))
            print(f"   healthy recall      {p:5.1f}% +/-{e:4.1f}  ({k}/{len(healthy)})")
        k = sum(int(r["gate_fired"]) for r in lesion)
        p, e = ci(k, len(lesion))
        print(f"   lesion false-pos    {p:5.1f}% +/-{e:4.1f}  ({k}/{len(lesion)})  "
              f"<- lesions shown as Healthy")
        print("   by class:")
        for code in sorted(per):
            rs = per[code]
            k = sum(int(r["gate_fired"]) for r in rs)
            flag = "  *** MALIGNANT LEAK ***" if k and code in MALIGNANT else ""
            print(f"     {code:6s} {k:4d}/{len(rs):<5d} "
                  f"{k/len(rs)*100:5.1f}%{flag}")
        mel_scores = [float(r["gate_score"]) for r in lesion
                      if r["true"] == "MEL" and r["gate_score"]]
        if mel_scores:
            print(f"   melanoma gate scores: max {max(mel_scores):.3f}, "
                  f"median {np.median(mel_scores):.3f}")

    # 4. what the user is actually shown
    print("\n4. END-TO-END  (what the app displays)")
    ok = sum(r["final_pred"] == r["true"] for r in rows)
    p, e = ci(ok, len(rows))
    print(f"   overall top-1 {p:.1f}% +/-{e:.1f}  ({ok}/{len(rows)})")
    if gate_metrics:
        print(f"\n   gate as shipped: {gate_metrics}")
    print("=" * 78)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--evalset", default=os.path.join(HERE, "evalset"))
    ap.add_argument("--healthy-dir", default=None,
                    help="folder of healthy-skin photos (optional)")
    ap.add_argument("--tflite", default=os.path.join(ASSETS, "skin_model.tflite"))
    ap.add_argument("--gate", default=os.path.join(ASSETS, "healthy_gate.json"))
    ap.add_argument("--csv", default=os.path.join(HERE, "eval_results.csv"))
    ap.add_argument("--report-only", action="store_true")
    ap.add_argument("--limit", type=int)
    args = ap.parse_args()

    tfl, gate = (None, None)
    if not args.report_only:
        tfl, gate = load_assets(args.tflite, args.gate)
        if gate:
            print(f"gate threshold {gate[2]}  {gate[3]}")
        items = collect(args.evalset, args.healthy_dir)
        if args.limit:
            items = items[:args.limit]
        done = set()
        if os.path.exists(args.csv):
            done = {r["image"] for r in csvmod.DictReader(open(args.csv))}
        run(items, tfl, gate, args.csv, done)

    gm = None
    if os.path.exists(args.gate):
        gm = json.load(open(args.gate)).get("metrics")
    report(args.csv, gm)


if __name__ == "__main__":
    main()
