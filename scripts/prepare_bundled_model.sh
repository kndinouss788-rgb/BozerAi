#!/usr/bin/env bash
set -euo pipefail

ASSETS_DIR="${1:?Usage: prepare_bundled_model.sh app/src/main/assets}"
MODEL_URL="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
LICENSE_URL="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/LICENSE"
MODEL_FILE="$ASSETS_DIR/bozer-model.gguf"
METADATA_FILE="$ASSETS_DIR/bozer-model.sha256"
LICENSE_FILE="$ASSETS_DIR/BOZER_MODEL_LICENSE.txt"

mkdir -p "$ASSETS_DIR"

echo "Downloading the pinned BOZER model for packaging in the APK..."
curl --fail --location --retry 5 --retry-delay 3 --continue-at - \
  "$MODEL_URL" --output "$MODEL_FILE"

MODEL_SIZE="$(stat -c%s "$MODEL_FILE")"
if [ "$MODEL_SIZE" -lt 900000000 ]; then
  echo "Model file is too small ($MODEL_SIZE bytes); refusing to package it." >&2
  exit 1
fi

MODEL_SHA256="$(sha256sum "$MODEL_FILE" | awk '{print $1}')"
printf '%s %s %s\n' "$MODEL_SHA256" "$MODEL_SIZE" "bozer-model.gguf" > "$METADATA_FILE"

curl --fail --location --retry 3 "$LICENSE_URL" --output "$LICENSE_FILE"
test -s "$LICENSE_FILE"

echo "Bundled model source prepared:"
echo "  file: $MODEL_FILE"
echo "  size: $MODEL_SIZE bytes"
echo "  sha256: $MODEL_SHA256"
echo "  metadata: $METADATA_FILE"
