#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/learning-store-continuity-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/dev/learningstore/*.java
java -cp "$BUILD_DIR" dev.learningstore.LearningStoreExample
