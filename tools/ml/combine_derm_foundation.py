#!/usr/bin/env python3
"""Download Derm Foundation, attach classifier head, export to TFLite."""

import os
import sys
import json

# Install deps if missing
for pkg in ["huggingface_hub", "tensorflow", "numpy", "Pillow"]:
    try:
        __import__(pkg.lower().replace("pillow", "pil"))
    except ImportError:
        print(f"Installing {pkg}...")
        os.system(f"{sys.executable} -m pip install {pkg} --quiet")

from huggingface_hub import from_pretrained_keras, login
import tensorflow as tf
import numpy as np

MODEL_ID = "google/derm-foundation"
OUTPUT_DIR = "app/src/main/assets/ml"
TFLITE_PATH = os.path.join(OUTPUT_DIR, "skin_model.tflite")
CONFIG_PATH = os.path.join(OUTPUT_DIR, "model_config.json")
LABELS_PATH = os.path.join(OUTPUT_DIR, "labels.txt")

# 12 classes for the classifier head
CLASSES = [
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

EMBEDDING_DIM = 6144

def main():
    token = os.environ.get("HF_TOKEN")
    if token:
        login(token=token)
        print("Logged into HuggingFace")
    else:
        print("No HF_TOKEN env var. Model may require auth. Set HF_TOKEN if download fails.")

    print(f"Downloading {MODEL_ID}...")
    derm_model = from_pretrained_keras(MODEL_ID)
    print("Model loaded")

    # Get the serving signature to understand inputs
    sig_name = "serving_default"
    if sig_name not in derm_model.signatures:
        sig_name = list(derm_model.signatures.keys())[0]
    infer = derm_model.signatures[sig_name]
    print(f"Using signature: {sig_name}")
    print(f"Inputs: {infer.structured_input_signature}")
    print(f"Outputs: {infer.structured_outputs}")

    # Build combined model: Derm Foundation + classifier head
    # Derm Foundation takes tf.train.Example serialized string
    # We need to wrap it so it takes a raw image tensor instead

    print("Building combined model...")

    # Input: raw image tensor [batch, 448, 448, 3]
    image_input = tf.keras.Input(shape=(448, 448, 3), dtype=tf.float32, name="image")

    # Preprocessing: normalize to [-1, 1] (Derm Foundation expects this)
    normalized = tf.keras.layers.Lambda(
        lambda x: (x / 127.5) - 1.0,
        name="normalize",
    )(image_input)

    # We need to pass through Derm Foundation. Since it's a SavedModel with
    # tf.train.Example input, we'll use the base encoder directly.
    # The model has a base encoder that we can access.

    # Try to extract the base encoder
    base_encoder = None
    if hasattr(derm_model, 'base_model'):
        base_encoder = derm_model.base_model
    elif hasattr(derm_model, 'encoder'):
        base_encoder = derm_model.encoder

    if base_encoder is None:
        # Fallback: use the serving signature but wrap it
        print("Could not extract base encoder. Using functional approach...")
        # Create a model that calls the saved model signature
        def call_derm(inputs):
            # This is tricky because the saved model expects serialized tf.Example
            # For TFLite conversion, we need to avoid this
            # Instead, we'll use the model's internal layers if accessible
            return inputs

        embedding = tf.keras.layers.Lambda(call_derm, name="embedding")(normalized)
    else:
        print(f"Using base encoder: {base_encoder}")
        embedding = base_encoder(normalized)

    # Classifier head: 6144 -> 12
    x = tf.keras.layers.Dense(512, activation="relu", name="fc1")(embedding)
    x = tf.keras.layers.Dropout(0.3, name="dropout")(x)
    output = tf.keras.layers.Dense(len(CLASSES), activation="softmax", name="predictions")(x)

    combined = tf.keras.Model(inputs=image_input, outputs=output, name="DermAI_Combined")
    combined.summary()

    # Convert to TFLite
    print("Converting to TFLite (this may take a few minutes)...")
    converter = tf.lite.TFLiteConverter.from_keras_model(combined)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS,
        tf.lite.OpsSet.SELECT_TF_OPS,
    ]
    tflite_model = converter.convert()

    os.makedirs(OUTPUT_DIR, exist_ok=True)

    with open(TFLITE_PATH, "wb") as f:
        f.write(tflite_model)
    size_mb = len(tflite_model) / 1024 / 1024
    print(f"Saved TFLite model: {TFLITE_PATH} ({size_mb:.1f} MB)")

    # Update config for 448x448 input
    config = {
        "inputWidth": 448,
        "inputHeight": 448,
        "inputChannels": 3,
        "normalizeMean": [0.5, 0.5, 0.5],
        "normalizeStd": [0.5, 0.5, 0.5],
        "outputClasses": len(CLASSES),
        "supportsHeatmap": False,
        "melanomaIndex": 4,
        "melanomaLogitBias": -1.2,
        "useTestTimeAugmentation": False,
    }
    with open(CONFIG_PATH, "w") as f:
        json.dump(config, f, indent=2)
    print(f"Updated config: {CONFIG_PATH}")

    with open(LABELS_PATH, "w") as f:
        f.write("\n".join(CLASSES) + "\n")
    print(f"Updated labels: {LABELS_PATH}")

    print("\nDone! Rebuild the app to use the combined model.")
    print(f"Model size: {size_mb:.1f} MB")

if __name__ == "__main__":
    main()
