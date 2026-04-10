#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$ROOT_DIR/src"
OUT_DIR="$ROOT_DIR/out"

mkdir -p "$OUT_DIR"
find "$OUT_DIR" -type f -name '*.class' -delete

javac -d "$OUT_DIR" $(find "$SRC_DIR" -name '*.java' | sort)
java -cp "$OUT_DIR" com.fortress.poc.CameraViewerApp "$@"
