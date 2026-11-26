#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${1:?JNI output directory required}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
NDK="${ANDROID_NDK_HOME:-$SDK/ndk/27.2.12479018}"
case "$(uname -s)" in Linux) HOST=linux-x86_64;; Darwin) HOST=darwin-x86_64;; *) echo "Use Linux or macOS with Android NDK"; exit 1;; esac
BIN="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
[[ -d "$BIN" ]] || { echo "Missing NDK toolchain: $BIN"; exit 1; }
build() {
  local target="$1" abi="$2" clang="$3" key
  key="$(echo "$target" | tr '[:lower:]-' '[:upper:]_')"
  mkdir -p "$OUT/$abi"
  env "CARGO_TARGET_${key}_LINKER=$BIN/$clang" "CARGO_TARGET_${key}_RUSTFLAGS=-C link-arg=-Wl,-z,max-page-size=16384" \
    cargo build --manifest-path "$ROOT/native/Cargo.toml" --target "$target" --release --target-dir "$ROOT/native/target"
  cp "$ROOT/native/target/$target/release/libramwiper_next.so" "$OUT/$abi/"
}
build aarch64-linux-android arm64-v8a aarch64-linux-android26-clang
build armv7-linux-androideabi armeabi-v7a armv7a-linux-androideabi26-clang
build x86_64-linux-android x86_64 x86_64-linux-android26-clang
