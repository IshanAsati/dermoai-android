# DermoAI Model Assets

Source repository: https://github.com/IshanAsati/dermoai-final

## Bundled PyTorch checkpoint

- `model_weights/ce_ls_best.pth` — ConvNeXt-Base, 12 classes, 224×224 input
- ImageNet normalization: mean `[0.485, 0.456, 0.406]`, std `[0.229, 0.224, 0.225]`
- Melanoma index: `5`, recommended logit bias: `-1.2` (from `results_summary.json`)

## Phase 6 conversion

The Android app expects `app/src/main/assets/ml/skin_model.tflite`.

Convert the PyTorch checkpoint to TFLite before enabling on-device inference.