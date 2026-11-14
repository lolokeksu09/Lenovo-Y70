"""Local-only license administration. Raw keys are printed exactly once."""
from __future__ import annotations

import argparse
import os
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization

from .core import ALLOWED_PLANS, config_from_env


def write_private(path: Path, content: bytes) -> None:
    # Fail rather than overwrite a signing identity.
    fd = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, "wb") as f:
        f.write(content)


def main() -> None:
    parser = argparse.ArgumentParser(description="Local license administration")
    sub = parser.add_subparsers(dest="cmd", required=True)
    keys = sub.add_parser("init-keypair", help="Generate 3072-bit RSA keypair, private file is mode 0600")
    keys.add_argument("--private", type=Path, required=True)
    keys.add_argument("--public", type=Path, required=True)
    create = sub.add_parser("issue")
    create.add_argument("--plan", choices=sorted(ALLOWED_PLANS), required=True)
    create.add_argument("--days", type=int)
    create.add_argument("--devices", type=int, default=1)
    revoke = sub.add_parser("revoke")
    revoke.add_argument("--license-id", required=True)
    extend = sub.add_parser("extend")
    extend.add_argument("--license-id", required=True)
    extend.add_argument("--days", required=True, type=int)
    reset = sub.add_parser("reset-device")
    reset.add_argument("--license-id", required=True)
    reset.add_argument("--install-id", required=True)
    args = parser.parse_args()

    if args.cmd == "init-keypair":
        if args.public.exists() or args.private.exists():
            parser.error("Refusing to overwrite existing signing keys")
        private_key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        private_bytes = private_key.private_bytes(serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
        public_bytes = private_key.public_key().public_bytes(serialization.Encoding.PEM,
            serialization.PublicFormat.SubjectPublicKeyInfo)
        args.private.parent.mkdir(parents=True, exist_ok=True)
        args.public.parent.mkdir(parents=True, exist_ok=True)
        write_private(args.private, private_bytes)
        args.public.write_bytes(public_bytes)
        print("Private key created with 0600 permissions; public key exported")
        return

    store = config_from_env()
    if args.cmd == "issue":
        license_id, key = store.issue(args.plan, args.days, args.devices)
        print("License ID:", license_id)
        print("Activation key (visible only now):", key)
    elif args.cmd == "revoke":
        print("Revoked" if store.revoke(args.license_id) else "License not found/already revoked")
    elif args.cmd == "extend":
        print("Extended" if store.extend(args.license_id, args.days) else "License not eligible")
    elif args.cmd == "reset-device":
        print("Device released" if store.reset_device(args.license_id, args.install_id) else "Activation not found")


if __name__ == "__main__":
    main()
