#!/usr/bin/env python3
"""Download a large evaluation set of *clinical* ISIC images.

Deliberately clinical-only. ISIC is mostly dermoscopic -- taken through a
contact lens pressed against the skin -- and those look nothing like a phone
photo, so including them would inflate the sample size while measuring a use
case this app does not have. The clinical pool is ~9.3k images total.

Downloads the 256px renders rather than originals: the model sees 224px anyway,
and it is the difference between ~50 MB and ~15 GB. `verify_thumbnail_parity`
in eval_bulk.py checks that this does not change predictions.

Resumable -- rerun after an interruption and it skips what it already has.

Usage: fetch_isic_bulk.py [--per-class 500] [--out DIR]
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.isic-archive.com/api/v2/images/search/"
UA = {"User-Agent": "DermoAI-eval/1.0 (educational school project)"}

# ISIC diagnosis_3 -> DermoAI class code.
WANTED = {
    "NEV": ["Nevus"],
    "MEL": ["Melanoma Invasive", "Melanoma, NOS", "Melanoma in situ",
            "Melanoma metastasis"],
    "BCC": ["Basal cell carcinoma"],
    "SCC": ["Squamous cell carcinoma, NOS", "Squamous cell carcinoma in situ",
            "Squamous cell carcinoma, Invasive"],
    "ACK": ["Solar or actinic keratosis", "Actinic keratosis"],
    "SEK": ["Seborrheic keratosis"],
}
ALLOWED_LICENSES = {"CC-0", "CC-BY"}


def get(url, tries=4):
    for i in range(tries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA),
                                        timeout=90) as r:
                return json.load(r)
        except (urllib.error.URLError, TimeoutError) as e:
            if i == tries - 1:
                raise
            time.sleep(2 * (i + 1))   # the archive rate-limits under load


def search(diagnoses, limit):
    terms = " OR ".join(f'diagnosis_3:"{d}"' for d in diagnoses)
    q = f'image_type:"clinical: close-up" AND ({terms})'
    url = f"{API}?limit=100&query={urllib.parse.quote(q)}"
    out = []
    while url and len(out) < limit:
        page = get(url)
        for r in page.get("results", []):
            if r.get("copyright_license") not in ALLOWED_LICENSES:
                continue
            files = r.get("files", {})
            src = (files.get("thumbnail_256") or files.get("full") or {}).get("url")
            if src:
                out.append((r["isic_id"], src, r.get("copyright_license")))
            if len(out) >= limit:
                break
        url = page.get("next")
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-class", type=int, default=500)
    ap.add_argument("--out", default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "evalset"))
    args = ap.parse_args()

    total_new = total_have = 0
    credits = []
    for code, diagnoses in WANTED.items():
        dest = os.path.join(args.out, code)
        os.makedirs(dest, exist_ok=True)
        have = {f.split(".")[0] for f in os.listdir(dest) if f.endswith(".jpg")}
        try:
            items = search(diagnoses, args.per_class)
        except Exception as e:
            print(f"{code}: search failed ({e}); keeping {len(have)} already on disk")
            continue

        new = 0
        for isic_id, url, lic in items:
            credits.append(f"{isic_id}\t{lic}")
            if isic_id in have:
                continue
            try:
                req = urllib.request.Request(url, headers=UA)
                with urllib.request.urlopen(req, timeout=60) as r:
                    data = r.read()
                with open(os.path.join(dest, f"{isic_id}.jpg"), "wb") as f:
                    f.write(data)
                new += 1
            except Exception:
                continue
        n = len([f for f in os.listdir(dest) if f.endswith(".jpg")])
        total_new += new
        total_have += n
        print(f"{code:4s} available {len(items):4d}  downloaded {new:4d}  on disk {n:4d}",
              flush=True)

    with open(os.path.join(args.out, "ATTRIBUTION.tsv"), "w", encoding="utf-8") as f:
        f.write("isic_id\tlicense\n" + "\n".join(sorted(set(credits))) + "\n")
    print(f"\ntotal on disk: {total_have} images ({total_new} new)")
    print("ISIC Archive (https://isic-archive.com), CC-0 / CC-BY.")


if __name__ == "__main__":
    main()
