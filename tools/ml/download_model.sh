#!/bin/bash
set -e

# Setup directories
PROJ_DIR="/home/light/projects/DermoAI"
REPO_DIR="$PROJ_DIR/tools/ml/dermoai-final-repo"
DEST_DIR="$PROJ_DIR/tools/ml/source"

rm -rf "$REPO_DIR"
mkdir -p "$REPO_DIR"
mkdir -p "$DEST_DIR"

# Initialize git with sparse checkout
cd "$REPO_DIR"
git init
git remote add origin https://github.com/ishanasati/dermoai-final.git
git config core.sparseCheckout true
echo "model_weights/ce_ls_best.pth" >> .git/info/sparse-checkout

# Pull main branch
echo "Pulling model weights from github..."
git pull origin main

# Copy to destination
cp model_weights/ce_ls_best.pth "$DEST_DIR/ce_ls_best.pth"
echo "Model weights successfully downloaded and placed at $DEST_DIR/ce_ls_best.pth"

# Clean up repo directory
rm -rf "$REPO_DIR"
