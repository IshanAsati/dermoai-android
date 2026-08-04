#!/usr/bin/env python3
"""Browser front-end for the model the APK actually ships.

tools/ml/repo/app.py serves the upstream *PyTorch* checkpoint with a -1.2
melanoma bias and no healthy gate, so what it shows no longer matches the
phone. This serves the real app assets instead:

    app/src/main/assets/ml/skin_model.tflite    (two outputs: logits + features)
    app/src/main/assets/ml/healthy_gate.json    (healthy-vs-lesion gate)
    app/src/main/assets/ml/labels.txt
    app/src/main/assets/ml/model_config.json

and reproduces TfliteInterpreterHolder.runInference step for step, so a photo
gives the same answer here as on device.

It reuses the upstream webapp/index.html unchanged (that file posts to
localhost:5000/predict), so this must run on port 5000.

PRIVACY: entirely local. Uploads are held in memory, classified, and dropped --
never written to disk, never sent anywhere.

Usage: python tools/ml/serve_app_model.py [--port 5000]
"""
import argparse
import io
import json
import os

import numpy as np
from flask import Flask, jsonify, request, send_from_directory
from flask_cors import CORS
from PIL import Image

try:  # iPhone photos
    import pillow_heif
    pillow_heif.register_heif_opener()
except ImportError:
    pass

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")
import tensorflow as tf  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(PROJ, "app", "src", "main", "assets", "ml")
WEBAPP = os.path.join(HERE, "repo", "webapp")

MEAN = np.array([0.485, 0.456, 0.406], np.float32)
STD = np.array([0.229, 0.224, 0.225], np.float32)
FEATURE_DIM = 1024

CODES = ["BCC", "ACK", "NEV", "SEK", "SCC", "MEL",
         "Acne", "Hair Loss", "Nail Fungus", "Fungal", "Vascular", "Healthy"]


def severity_for(i):
    if i == 5:
        return "critical"
    if i in (0, 4):
        return "high"
    if i in (1, 3):
        return "medium"
    return "low"


class AppModel:
    """Mirrors core/ml/.../TfliteInterpreterHolder.kt."""

    def __init__(self):
        self.labels = [l for l in open(os.path.join(ASSETS, "labels.txt"))
                       .read().splitlines() if l.strip()]
        self.config = json.load(open(os.path.join(ASSETS, "model_config.json")))
        self.mel_idx = self.config.get("melanomaIndex", 5)
        self.mel_bias = float(self.config.get("melanomaLogitBias", 0.0))
        self.healthy_idx = self.config.get("healthyIndex", 11)

        self.interp = tf.lite.Interpreter(
            model_path=os.path.join(ASSETS, "skin_model.tflite"), num_threads=4)
        self.interp.allocate_tensors()
        self.inp = self.interp.get_input_details()[0]
        self.logits_out = self.feat_out = None
        for o in self.interp.get_output_details():
            if o["shape"][-1] == len(self.labels):
                self.logits_out = o
            elif o["shape"][-1] == FEATURE_DIM:
                self.feat_out = o

        gate_path = os.path.join(ASSETS, "healthy_gate.json")
        self.gate = None
        if os.path.exists(gate_path) and self.feat_out is not None:
            g = json.load(open(gate_path))
            self.gate = (np.array(g["weights"], np.float32),
                         float(g["bias"]), float(g["threshold"]))
            print(f"  healthy gate: threshold {self.gate[2]:.4f}  "
                  f"{g.get('metrics', {})}", flush=True)
        else:
            print("  WARNING: no healthy gate (model has no feature output?)", flush=True)
        print(f"  melanoma bias {self.mel_bias}  |  "
              f"feature output: {'yes' if self.feat_out is not None else 'no'}", flush=True)

    def _preprocess(self, img):
        """Match ImagePreprocessor: squash to 224, scale to [0,1], ImageNet normalise."""
        w, h = self.config["inputWidth"], self.config["inputHeight"]
        a = np.asarray(img.resize((w, h), Image.BILINEAR), np.float32) / 255.0
        return ((a - MEAN) / STD)[None, ...]

    def predict(self, img):
        self.interp.set_tensor(self.inp["index"], self._preprocess(img))
        self.interp.invoke()
        logits = self.interp.get_tensor(self.logits_out["index"])[0].copy()

        if self.mel_bias:
            logits[self.mel_idx] += self.mel_bias
        e = np.exp(logits - logits.max())
        probs = e / e.sum()

        gate_score, gated = None, False
        if self.gate is not None:
            feats = self.interp.get_tensor(self.feat_out["index"])[0]
            w, b, thr = self.gate
            gate_score = float(1.0 / (1.0 + np.exp(-(float(feats @ w) + b))))
            gated = gate_score >= thr

        order = list(np.argsort(probs)[::-1])
        results = [{
            "class": self.labels[i],
            "class_code": CODES[i],
            "probability": float(probs[i]) * 100.0,
            "confidence": float(probs[i]) * 100.0,
            "severity": severity_for(i),
            "risk": "high" if severity_for(i) in ("high", "critical")
                    else ("medium" if severity_for(i) == "medium" else "low"),
        } for i in order]

        if gated:
            # Same override as on device: the gate, not the head, made this call,
            # so its score becomes the confidence shown. The head's own numbers are
            # rescaled into the probability the gate left over -- otherwise entries
            # below Healthy show a higher confidence than it, which reads as a bug.
            hi = self.healthy_idx
            remainder = 1.0 - gate_score
            rest = [dict(r, probability=r["probability"] * remainder,
                         confidence=r["confidence"] * remainder)
                    for r in results if r["class_code"] != CODES[hi]]
            results = [{
                "class": self.labels[hi],
                "class_code": CODES[hi],
                "probability": gate_score * 100.0,
                "confidence": gate_score * 100.0,
                "severity": "low",
                "risk": "low",
            }] + rest

        top = results[0]
        return {
            "predictions": results,
            "top_class": top["class"],
            "top_class_code": top["class_code"],
            "top_confidence": top["confidence"],
            "top_severity": top["severity"],
            "gate_score": gate_score,
            "gate_fired": gated,
        }


app = Flask(__name__, static_folder=WEBAPP)
CORS(app)
model = None


@app.route("/")
def index():
    return send_from_directory(app.static_folder, "index.html")


@app.route("/<path:path>")
def static_files(path):
    return send_from_directory(app.static_folder, path)


@app.route("/predict", methods=["POST"])
def predict():
    if "image" not in request.files:
        return jsonify({"error": "No image uploaded"}), 400
    try:
        img = Image.open(io.BytesIO(request.files["image"].read())).convert("RGB")
    except Exception as e:
        return jsonify({"error": f"Could not read image: {e}"}), 400

    out = model.predict(img)
    tag = f"GATE {out['gate_score']:.3f}" if out["gate_score"] is not None else "no gate"
    print(f"  -> {out['top_class_code']:8s} {out['top_confidence']:5.1f}%  "
          f"[{tag}{' FIRED' if out['gate_fired'] else ''}]", flush=True)
    return jsonify(out)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=5000)
    args = ap.parse_args()

    print("Loading the assets the APK ships...", flush=True)
    model = AppModel()
    print(f"\nDermoAI (on-device model) at http://localhost:{args.port}", flush=True)
    print("Images are classified in memory and never written to disk.\n", flush=True)
    app.run(host="127.0.0.1", port=args.port)
