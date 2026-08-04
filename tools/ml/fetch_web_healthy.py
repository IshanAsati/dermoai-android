#!/usr/bin/env python3
"""Harvest close-up healthy-skin photos from the web. DOES NOT WORK -- see below.

Kept as a record of a dead end, so nobody spends another afternoon on it.

The idea was a second, independent healthy set to complement the ISIC
perilesional crops. Measured yield: **371 candidates, 0 usable images.**

Three separate reasons, in increasing order of how fundamental they are:

1. Access. Openverse throttles anonymous clients to HTTP 401 after a handful of
   queries and a key needs an account. Wikimedia returns HTTP 429 under its
   robot policy unless you slow right down and send a contact address.

2. Labelling -- the fatal one. Commons search for "skin close-up" returns
   atopic dermatitis, allergy-test sites and freckled skin alongside clear
   skin. Filtering those out automatically needs a classifier that separates
   healthy skin from mild skin disease, which is precisely the thing this data
   was meant to help build. The three images that survived every filter were a
   testicle, a freckled arm, and an allergy test. Mislabelled "healthy"
   examples would corrupt the gate silently.

3. Distribution. Even clean web photos are stock or archive images -- retouched,
   studio-lit, usually a whole limb at distance. Not smartphone snapshots.

What works instead: photograph your own skin. Twenty photos beats this on all
three counts -- no access limits, ground-truth labels, and the right
distribution.

Usage: fetch_web_healthy.py [--out DIR] [--target N]
"""
import argparse
import io
import json
import os
import urllib.parse
import urllib.request

import numpy as np
from PIL import Image

# Openverse throttles anonymous clients to 401 after a handful of queries and a
# key needs an account, so Commons -- genuinely key-free -- is the source here.
API = "https://commons.wikimedia.org/w/api.php"
UA = {"User-Agent": "DermoAI-eval/1.0 (educational school project)"}

QUERIES = [
    "human skin close-up", "skin texture closeup", "hand skin closeup",
    "arm skin", "forearm skin", "human skin macro", "skin texture photograph",
    "bare skin closeup", "leg skin", "knee skin closeup", "elbow skin",
    "back skin human", "shoulder skin", "abdomen skin", "human epidermis photo",
]

# Commons is full of diagrams, microscopy and scanned books under these terms.
TITLE_BLOCKLIST = (
    "diagram", "structure", "anatomy", "200x", "micrograph", "histolog",
    "illustration", "journal", "review", "microform", "book", "plate",
    "drawing", "scheme", "chart", "blausen", "model", "cross section",
)


def get(url):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def search(query, limit=40):
    """Commons file-namespace search, returning (title, image-url) pairs."""
    params = urllib.parse.urlencode({
        "action": "query", "format": "json", "generator": "search",
        "gsrnamespace": 6, "gsrsearch": query, "gsrlimit": limit,
        "prop": "imageinfo", "iiprop": "url|size|extmetadata",
        # Originals run to 40 MP and time out; a 1024px render is plenty for
        # a 224px model input and downloads reliably.
        "iiurlwidth": 1024,
    })
    pages = get(f"{API}?{params}").get("query", {}).get("pages", {})
    out = []
    for p in pages.values():
        title = p.get("title", "")
        if any(b in title.lower() for b in TITLE_BLOCKLIST):
            continue
        ii = (p.get("imageinfo") or [{}])[0]
        url = ii.get("thumburl") or ii.get("url")
        if not url or ii.get("width", 0) < 600:
            continue
        lic = ii.get("extmetadata", {}).get("LicenseShortName", {}).get("value", "?")
        out.append((title, url, lic))
    return out


def skin_score(img):
    """Fraction of pixels plausibly bare skin, plus a flatness check.

    Deliberately loose on hue (pale, pink and deep tones all qualify) and
    strict on the things that mean 'not a skin close-up': dark backgrounds,
    saturated colour, and busy scenes.
    """
    a = np.asarray(img.convert("RGB").resize((128, 128))).astype(np.float32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(2), a.min(2)
    sat = (mx - mn) / (mx + 1e-6)

    skin = ((r >= g) & (g >= b * 0.85) & (mx > 60) & (mx < 250) & (sat < 0.55))
    frac = skin.mean()

    # A close-up of skin has low large-scale structure; a portrait or a scene
    # has strong edges. Cheap proxy: gradient energy on a blurred greyscale.
    grey = a.mean(2)
    gx = np.abs(np.diff(grey, axis=1)).mean()
    gy = np.abs(np.diff(grey, axis=0)).mean()
    return frac, (gx + gy) / 2


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "testdata", "Healthy_web"))
    ap.add_argument("--target", type=int, default=40)
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    seen, kept, credits = set(), 0, []
    stats = {"fetched": 0, "downloaded": 0, "rejected_skin": 0, "rejected_busy": 0}

    for q in QUERIES:
        if kept >= args.target:
            break
        try:
            hits = search(q)
        except Exception as e:
            print(f"  query {q!r} failed: {e}")
            continue
        for title, src, lic in hits:
            if kept >= args.target:
                break
            if title in seen:
                continue
            seen.add(title)
            stats["fetched"] += 1
            try:
                req = urllib.request.Request(src, headers=UA)
                with urllib.request.urlopen(req, timeout=60) as resp:
                    raw = resp.read()
                img = Image.open(io.BytesIO(raw)).convert("RGB")
            except Exception:
                continue
            stats["downloaded"] += 1

            if min(img.size) < 400:
                continue
            # Judge the centre square -- that is what actually gets classified.
            w, h = img.size
            s = min(w, h)
            img = img.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s))

            frac, busy = skin_score(img)
            if frac < 0.80:
                stats["rejected_skin"] += 1
                continue
            if busy > 6.0:
                stats["rejected_busy"] += 1
                continue

            img.save(os.path.join(args.out, f"web_{kept:03d}.jpg"), quality=95)
            credits.append(f"web_{kept:03d}.jpg\t{lic}\t{title}")
            kept += 1

    with open(os.path.join(args.out, "ATTRIBUTION.tsv"), "w", encoding="utf-8") as f:
        f.write("file\tlicense\tsource\n" + "\n".join(credits) + "\n")

    print(json.dumps(stats, indent=2))
    print(f"kept {kept} images -> {args.out}")
    print("Auto-filtered only. Review the contact sheet before trusting these.")


if __name__ == "__main__":
    main()
