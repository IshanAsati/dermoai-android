#!/usr/bin/env python3
"""Download a small CC-0 test set of *clinical* skin photos from the ISIC Archive.

Dermoscopic images are taken through a contact lens and look nothing like a
phone photo; the archive's "clinical: close-up" subset is the closest public
stand-in for how DermoAI is actually used. Only CC-0 images are kept.

Also derives a proxy "healthy" set from the corners of lesion photos -- real
skin texture with no lesion in frame. It is a proxy, not a substitute for
actual healthy-skin phone photos: the framing and lighting are still clinical.

Usage: fetch_isic_testdata.py [--out DIR] [--per-class N]
"""
import argparse
import json
import os
import urllib.parse
import urllib.request

from PIL import Image

API = "https://api.isic-archive.com/api/v2/images/search/"
UA = {"User-Agent": "DermoAI-eval/1.0 (school project; educational use)"}

# ISIC diagnosis_3 -> the DermoAI class code we expect the model to output.
# Several ISIC diagnoses collapse onto one DermoAI class (melanoma is split by
# invasiveness upstream; DermoAI has a single MEL class).
WANTED = {
    "NEV": ["Nevus"],
    "MEL": ["Melanoma Invasive", "Melanoma, NOS", "Melanoma in situ"],
    "BCC": ["Basal cell carcinoma"],
    "SCC": ["Squamous cell carcinoma, NOS"],
    "ACK": ["Solar or actinic keratosis"],
    "SEK": ["Seborrheic keratosis"],
}

# Only ~9% of clinical images are CC-0; CC-BY is the bulk and is fine for an
# educational evaluation as long as attribution is recorded alongside.
ALLOWED_LICENSES = {"CC-0", "CC-BY"}


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def fetch_class(diagnoses, limit):
    """Page the search endpoint until `limit` usable clinical images are collected."""
    terms = " OR ".join(f'diagnosis_3:"{d}"' for d in diagnoses)
    q = f'image_type:"clinical: close-up" AND ({terms})'
    url = f"{API}?limit=100&query={urllib.parse.quote(q)}"
    out = []
    while url and len(out) < limit:
        page = get(url)
        for r in page.get("results", []):
            if r.get("copyright_license") not in ALLOWED_LICENSES:
                continue
            out.append((r["isic_id"], r["files"]["full"]["url"],
                        r.get("copyright_license"), r.get("attribution", "")))
            if len(out) >= limit:
                break
        url = page.get("next")
    return out


def download(items, dest):
    os.makedirs(dest, exist_ok=True)
    saved, credits = [], []
    for isic_id, url, lic, attribution in items:
        path = os.path.join(dest, f"{isic_id}.jpg")
        if not os.path.exists(path):
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
                f.write(r.read())
        saved.append(path)
        credits.append(f"{isic_id}\t{lic}\t{attribution}")
    return saved, credits


def make_healthy_proxy(source_paths, dest, per_image=1):
    """Crop lesion-free corner patches -- real skin, no lesion in frame.

    Clinical close-ups centre the lesion, so a corner patch is usually normal
    perilesional skin. Usually, not always: some frames are lesion-filled, so
    treat this set as indicative rather than ground truth.
    """
    os.makedirs(dest, exist_ok=True)
    made = []
    for p in source_paths:
        img = Image.open(p).convert("RGB")
        w, h = img.size
        side = int(min(w, h) * 0.28)
        corners = [(0, 0), (w - side, 0), (0, h - side), (w - side, h - side)]
        for i, (x, y) in enumerate(corners[:per_image]):
            patch = img.crop((x, y, x + side, y + side))
            out = os.path.join(
                dest, f"{os.path.splitext(os.path.basename(p))[0]}_c{i}.jpg")
            patch.save(out, quality=95)
            made.append(out)
    return made


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "testdata"))
    ap.add_argument("--per-class", type=int, default=15)
    args = ap.parse_args()

    nevus_paths, all_credits = [], []
    for code, diagnoses in WANTED.items():
        print(f"fetching {code} ({', '.join(diagnoses)}) ...", flush=True)
        items = fetch_class(diagnoses, args.per_class)
        paths, credits = download(items, os.path.join(args.out, code))
        all_credits += credits
        print(f"  {len(paths)} images -> {os.path.join(args.out, code)}")
        if code == "NEV":
            nevus_paths = paths

    if nevus_paths:
        made = make_healthy_proxy(nevus_paths, os.path.join(args.out, "Healthy_proxy"))
        print(f"  {len(made)} proxy-healthy corner crops -> {args.out}/Healthy_proxy")

    with open(os.path.join(args.out, "ATTRIBUTION.tsv"), "w", encoding="utf-8") as f:
        f.write("isic_id\tlicense\tattribution\n")
        f.write("\n".join(all_credits) + "\n")
    print(f"\nImages from the ISIC Archive (https://isic-archive.com), CC-0 / CC-BY.")
    print(f"Per-image credits written to {args.out}\\ATTRIBUTION.tsv")


if __name__ == "__main__":
    main()
