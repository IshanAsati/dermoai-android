"""Wrapper: patch numpy allow_pickle for onnx2tf, then run it."""
import numpy as np
np_load_old = np.load
np.load = lambda *a, **k: np_load_old(*a, allow_pickle=True, **k)

import sys
from onnx2tf import convert

convert(
    input_onnx_file_path="tools/ml/skin_model.onnx",
    output_folder_path="tools/ml/tflite_tmp",
    output_signaturedefs=True,
    non_verbose=True,
)
