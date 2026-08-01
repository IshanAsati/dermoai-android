#!/usr/bin/env python3
"""Download Google Derm Foundation from HuggingFace and convert to TFLite."""

import os
import sys
import json

# Install deps if missing
try:
    from huggingface_hub import from_pretrained_keras
except ImportError:
    print("Installing huggingface_hub...")
    os.system(f"{sys.executable} -m pip install huggingface_hub tensorflow --quiet")
    from huggingface_hub import from_pretrained_keras

import tensorflow as tf
import numpy as np

MODEL_ID = "google/derm-foundation"
OUTPUT_DIR = "app/src/main/assets/ml"
TFLITE_PATH = os.path.join(OUTPUT_DIR, "skin_model.tflite")
CONFIG_PATH = os.path.join(OUTPUT_DIR, "model_config.json")
LABELS_PATH = os.path.join(OUTPUT_DIR, "labels.txt")

# Derm Foundation classes (from model card)
DERM_CLASSES = [
    "Actinic Keratosis",
    "Basal Cell Carcinoma",
    "Benign Keratosis",
    "Dermatofibroma",
    "Melanoma",
    "Melanocytic Nevus",
    "Squamous Cell Carcinoma",
    "Vascular Lesion",
    "Acne / Rosacea",
    "Eczema / Dermatitis",
    "Fungal Infection",
    "Healthy Skin",
]

def main():
    print(f"Downloading {MODEL_ID}...")
    model = from_pretrained_keras(MODEL_ID)

    print("Model loaded. Inspecting signatures...")
    print(model.signatures.keys())

    # Get serving signature
    infer = model.signatures.get("serving_default")
    if infer is None:
        infer = model.signatures.get("serving")
    if infer is None:
        infer = list(model.signatures.values())[0]

    print(f"Using signature: {infer}")
    print(f"Inputs: {infer.structured_input_signature}")
    print(f"Outputs: {infer.structured_outputs}")

    # Convert to TFLite
    print("Converting to TFLite...")
    converter = tf.lite.TFLiteConverter.from_saved_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    with open(TFLITE_PATH, "wb") as f:
        f.write(tflite_model)
    print(f"Saved TFLite model: {TFLITE_PATH} ({len(tflite_model) / 1024 / 1024:.1f} MB)")

    # Update config
    config = {
        "inputWidth": 224,
        "inputHeight": 224,
        "inputChannels": 3,
        "normalizeMean": [0.485, 0.456, 0.406],
        "normalizeStd": [0.229, 0.224, 0.225],
        "outputClasses": len(DERM_CLASSES),
        "supportsHeatmap": False,
        "melanomaIndex": 4,
        "melanomaLogitBias": -1.2,
        "useTestTimeAugmentation": False,
    }
    with open(CONFIG_PATH, "w") as f:
        json.dump(config, f, indent=2)
    print(f"Updated config: {CONFIG_PATH}")

    # Update labels
    with open(LABELS_PATH, "w") as f:
        f.write("\n".join(DERM_CLASSES) + "\n")
    print(f"Updated labels: {LABELS_PATH}")

    print("Done! Rebuild the app to use the new model.")

if __name__ == "__main__":
    main()
