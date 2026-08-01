#!/usr/bin/env python3
"""Convert DermoAI ONNX → TFLite via onnx-tf + TFLiteConverter."""
import os, sys, shutil

# Patch onnx.mapping BEFORE onnx_tf imports it
import onnx
from onnx import TensorProto
import numpy as np

if not hasattr(onnx, 'mapping'):
    mapping = type('MappingShim', (), {})()
    for attr in dir(onnx._mapping):
        if not attr.startswith('_'):
            setattr(mapping, attr, getattr(onnx._mapping, attr))
    mapping.NP_TYPE_TO_TENSOR_TYPE = {
        np.float32: TensorProto.FLOAT, np.uint8: TensorProto.UINT8,
        np.int8: TensorProto.INT8, np.uint16: TensorProto.UINT16,
        np.int16: TensorProto.INT16, np.int32: TensorProto.INT32,
        np.int64: TensorProto.INT64, np.float16: TensorProto.FLOAT16,
        np.double: TensorProto.DOUBLE, np.complex64: TensorProto.COMPLEX64,
        np.complex128: TensorProto.COMPLEX128, np.bool_: TensorProto.BOOL,
    }
    mapping.TENSOR_TYPE_TO_NP_TYPE = {v: k for k, v in mapping.NP_TYPE_TO_TENSOR_TYPE.items()}
    mapping.STORAGE_TENSOR_TYPE_TO_FIELD = {
        TensorProto.FLOAT: 'float_data', TensorProto.DOUBLE: 'double_data',
        TensorProto.INT32: 'int32_data', TensorProto.INT64: 'int64_data',
        TensorProto.UINT8: 'int32_data', TensorProto.INT8: 'int32_data',
        TensorProto.UINT16: 'int32_data', TensorProto.INT16: 'int32_data',
        TensorProto.BOOL: 'int32_data', TensorProto.FLOAT16: 'int32_data',
    }
    onnx.mapping = mapping

# Now import onnx_tf (uses onnx.mapping)
import onnx_tf
import tensorflow as tf

PROJ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ONNX = os.path.join(PROJ, "tools", "ml", "skin_model.onnx")
SAVEDMODEL_DIR = os.path.join(PROJ, "tools", "ml", "savedmodel_tmp")
TFLITE = os.path.join(PROJ, "app", "src", "main", "assets", "ml", "skin_model.tflite")

print("Loading ONNX model...")
onnx_model = onnx.load(ONNX)

print("Converting ONNX → TensorFlow...")
shutil.rmtree(SAVEDMODEL_DIR, ignore_errors=True)
os.makedirs(SAVEDMODEL_DIR, exist_ok=True)

backend = onnx_tf.Backend.prepare(onnx_model, device="CPU")
backend.export(os.path.join(SAVEDMODEL_DIR, "skin_model"))

print("Converting TF SavedModel → TFLite...")
converter = tf.lite.TFLiteConverter.from_saved_model(
    os.path.join(SAVEDMODEL_DIR, "skin_model")
)
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

os.makedirs(os.path.dirname(TFLITE), exist_ok=True)
with open(TFLITE, "wb") as f:
    f.write(tflite_model)
print(f"TFLite saved: {TFLITE} ({len(tflite_model)/1e6:.1f} MB)")

# Smoke test
import onnxruntime as ort
sess = ort.InferenceSession(ONNX, providers=["CPUExecutionProvider"])
dummy = np.random.randn(1, 3, 224, 224).astype(np.float32)
onnx_out = sess.run(None, {"input": dummy})[0]

interp = tf.lite.Interpreter(model_path=TFLITE)
interp.allocate_tensors()
in_det = interp.get_input_details()
out_det = interp.get_output_details()
input_data = dummy
if list(in_det[0]["shape"]) == [1, 224, 224, 3]:
    input_data = np.transpose(input_data, (0, 2, 3, 1))
interp.set_tensor(in_det[0]["index"], input_data.astype(np.float32))
interp.invoke()
tflite_out = interp.get_tensor(out_det[0]["index"])

def softmax(x):
    e = np.exp(x - x.max(axis=-1, keepdims=True))
    return e / e.sum(axis=-1, keepdims=True)

diff = np.max(np.abs(softmax(onnx_out) - softmax(tflite_out)))
print(f"Max softmax diff: {diff:.6f}  {'PASS' if diff < 0.02 else 'WARN'}")
print(f"Top class: ONNX={np.argmax(onnx_out[0])} TFLite={np.argmax(tflite_out[0])}")
print("Done!")

shutil.rmtree(SAVEDMODEL_DIR, ignore_errors=True)
