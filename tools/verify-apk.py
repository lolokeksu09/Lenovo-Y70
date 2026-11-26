#!/usr/bin/env python3
"""Verify packaged Rust JNI binaries, supported ELF targets, exports and 16 KiB alignment."""
import pathlib
import struct
import subprocess
import sys
import tempfile
import zipfile

abis = {"arm64-v8a": (2, 183), "armeabi-v7a": (1, 40), "x86_64": (2, 62)}
exports = {"Java_com_ramwiper_next_NativeWiper_" + name for name in ("begin", "append", "finish")}
apk = pathlib.Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as tmp:
    expected = {f"lib/{abi}/libramwiper_next.so" for abi in abis}
    actual = {name for name in archive.namelist() if name.endswith("/libramwiper_next.so")}
    if actual != expected:
        raise SystemExit(f"Unexpected JNI libraries: {actual}; expected {expected}")
    for abi, (elf_class, machine) in abis.items():
        name = f"lib/{abi}/libramwiper_next.so"
        data = archive.read(name)
        if data[:4] != b"\x7fELF" or data[4] != elf_class or data[5] != 1 or struct.unpack_from("<H", data, 18)[0] != machine:
            raise SystemExit(f"Incorrect ELF architecture: {abi}")
        path = pathlib.Path(tmp) / (abi + ".so")
        path.write_bytes(data)
        symbols = subprocess.check_output(["readelf", "--wide", "--dyn-syms", str(path)], text=True)
        for symbol in exports:
            if not any(line.split()[-1:] == [symbol] and " UND " not in line for line in symbols.splitlines()):
                raise SystemExit(f"Missing exported JNI method {symbol} in {abi}")
        headers = subprocess.check_output(["readelf", "--wide", "--program-headers", str(path)], text=True)
        loads = [line.split() for line in headers.splitlines() if line.strip().startswith("LOAD ")]
        if not loads or any(int(line[-1], 16) < 0x4000 for line in loads):
            raise SystemExit(f"Insufficient ELF load alignment in {abi}")
        print(f"{abi}: ELF architecture, JNI exports and load alignment OK")
print("Packaged Rust JNI verification passed.")
