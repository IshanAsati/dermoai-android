#!/usr/bin/env python3
"""Patched onnx2tf runner."""
import os, sys, shutil, numpy as np

dummy_path = "calibration_image_sample_data_20x128x128x3_float32.npy"
if not os.path.exists(dummy_path):
    np.save(dummy_path, np.random.randn(20, 128, 128, 3).astype(np.float32))

from onnx2tf import convert as onnx2tf_convert

ONNX = "tools/ml/skin_model.onnx"
OUT = "tools/ml/tflite_tmp"
TFLITE = "app/src/main/assets/ml/skin_model.tflite"

shutil.rmtree(OUT, ignore_errors=True)
os.makedirs(OUT, exist_ok=True)

onnx2tf_convert(
    input_onnx_file_path=ONNX,
    output_folder_path=OUT,
    output_signaturedefs=True,
    non_verbose=True,
    batch_size=1,
    disable_group_convolution=True,
)

tflite_files = [os.path.join(r,f) for r,_,fs in os.walk(OUT) for f in fs if f.endswith('.tflite')]
if not tflite_files:
    print("ERROR: No .tflite generated")
    sys.exit(1)

src = max(tflite_files, key=os.path.getsize)
os.makedirs(os.path.dirname(TFLITE), exist_ok=True)
shutil.copy(src, TFLITE)
print(f"TFLite saved: {TFLITE} ({os.path.getsize(TFLITE)/1e6:.1f} MB)")
shutil.rmtree(OUT, ignore_errors=True)
