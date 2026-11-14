"""License database and signed, short-lived offline entitlements.

Never store plaintext license keys. No screen content or user credentials are accepted.
"""
from __future__ import annotations

import base64
from contextlib import contextmanager
import hashlib
import hmac
import os
from pathlib import Path
import secrets
import sqlite3
import time
from typing import Any
from uuid import uuid4

import jwt

ISSUER = "overlay-ru-license"
AUDIENCE = "app.pinduoduo.overlayru"
ALLOWED_PLANS = frozenset({"pro_30d", "pro_365d", "pro_lifetime"})
TOKEN_LEASE_SECONDS = 7 * 24 * 60 * 60
MAX_RENEWAL_TOKEN_AGE_SECONDS = 30 * 24 * 60 * 60


class LicenseError(Exception):
    def __init__(self, code: str, status: int = 403) -> None:
        self.code = code
        self.status = status
        super().__init__(code)


def config_from_env() -> "LicenseStore":
    try:
        pepper = os.environ["LICENSE_LOOKUP_SECRET"].encode("utf-8")
        signing_key = Path(os.environ["LICENSE_SIGNING_KEY_PATH"]).read_bytes()
        db_path = Path(os.environ["LICENSE_DATABASE_PATH"])
    except (KeyError, OSError) as exc:
        raise RuntimeError("Required license server environment is not configured") from exc
    if len(pepper) < 32:
        raise RuntimeError("LICENSE_LOOKUP_SECRET must contain at least 32 bytes")
    if not signing_key.startswith(b"-----BEGIN PRIVATE KEY-----"):
        raise RuntimeError("Expected PKCS#8 RSA private key")
    return LicenseStore(db_path, pepper, signing_key)


class LicenseStore:
    def __init__(self, db_path: Path, pepper: bytes, signing_key: bytes) -> None:
        if len(pepper) < 32:
            raise ValueError("Lookup secret is too short")
        self.db_path = Path(db_path)
        self.pepper = pepper
        self.signing_key = signing_key
        # Fail fast for invalid key material.
        from cryptography.hazmat.primitives.serialization import load_pem_private_key
        key = load_pem_private_key(signing_key, password=None)
        if getattr(key, "key_size", 0) < 3072:
            raise ValueError("RSA key must be >= 3072 bits")
        self._init_db()

    def _connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.db_path), timeout=10, isolation_level=None)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA busy_timeout = 10000")
        conn.execute("PRAGMA foreign_keys = ON")
        return conn

    @contextmanager
    def _db(self):
        conn = self._connection()
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def _init_db(self) -> None:
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        with self._db() as conn:
            conn.executescript("""
                CREATE TABLE IF NOT EXISTS licenses (
                    id TEXT PRIMARY KEY,
                    key_hash TEXT NOT NULL UNIQUE,
                    plan TEXT NOT NULL,
                    expires_at INTEGER,
                    max_devices INTEGER NOT NULL DEFAULT 1,
                    revoked_at INTEGER,
                    created_at INTEGER NOT NULL
                );
                CREATE TABLE IF NOT EXISTS activations (
                    license_id TEXT NOT NULL REFERENCES licenses(id) ON DELETE CASCADE,
                    install_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_seen INTEGER NOT NULL,
                    PRIMARY KEY (license_id, install_id)
                );
            """)

    def _hash_key(self, raw_key: str) -> str:
        return hmac.new(self.pepper, raw_key.encode("ascii"), hashlib.sha256).hexdigest()

    @staticmethod
    def _new_key() -> str:
        # 160 bits of randomness: 32 unambiguous base32 characters.
        value = base64.b32encode(secrets.token_bytes(20)).decode("ascii").rstrip("=")
        return "OVR-" + "-".join(value[i:i + 4] for i in range(0, len(value), 4))

    def issue(self, plan: str, days: int | None, max_devices: int = 1) -> tuple[str, str]:
        if plan not in ALLOWED_PLANS:
            raise ValueError("Invalid plan")
        if (days is None) != (plan == "pro_lifetime"):
            raise ValueError("Lifetime plan must have no expiry, fixed plans require --days")
        if days is not None and not 1 <= days <= 3650:
            raise ValueError("days outside allowed range")
        if (plan == "pro_30d" and days != 30) or (plan == "pro_365d" and days != 365):
            raise ValueError("plan duration must match the selected plan")
        if not 1 <= max_devices <= 5:
            raise ValueError("max_devices outside allowed range")
        now = int(time.time())
        license_id = str(uuid4())
        key = self._new_key()
        expires_at = now + days * 86400 if days is not None else None
        with self._db() as conn:
            conn.execute("""
                INSERT INTO licenses (id,key_hash,plan,expires_at,max_devices,created_at)
                VALUES (?,?,?,?,?,?)
            """, (license_id, self._hash_key(key), plan, expires_at, max_devices, now))
        return license_id, key

    def _enforce_license(self, row: sqlite3.Row, now: int) -> None:
        if row["revoked_at"] is not None:
            raise LicenseError("license_revoked")
        if row["expires_at"] is not None and row["expires_at"] <= now:
            raise LicenseError("license_expired")

    def _make_token(self, row: sqlite3.Row, install_id: str, now: int) -> dict[str, Any]:
        expires_at = min(now + TOKEN_LEASE_SECONDS, row["expires_at"] or now + TOKEN_LEASE_SECONDS)
        claims = {
            "iss": ISSUER,
            "aud": AUDIENCE,
            "sub": row["id"],
            "install_id": install_id,
            "plan": row["plan"],
            "iat": now,
            "nbf": now,
            "exp": expires_at,
            "jti": str(uuid4()),
        }
        token = jwt.encode(claims, self.signing_key, algorithm="RS256", headers={"typ": "JWT"})
        return {"token": token, "expires_at": expires_at, "plan": row["plan"]}

    def activate(self, raw_key: str, install_id: str) -> dict[str, Any]:
        if len(raw_key) != 43 or not raw_key.startswith("OVR-"):
            raise LicenseError("invalid_license")
        # Validate strict base32 grouping and reject malformed client input.
        groups = raw_key[4:].split("-")
        if len(groups) != 8 or any(len(g) != 4 or not all(c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" for c in g) for g in groups):
            raise LicenseError("invalid_license")
        now = int(time.time())
        with self._db() as conn:
            conn.execute("BEGIN IMMEDIATE")
            row = conn.execute("SELECT * FROM licenses WHERE key_hash = ?", (self._hash_key(raw_key),)).fetchone()
            if row is None:
                raise LicenseError("invalid_license")
            self._enforce_license(row, now)
            current = conn.execute("SELECT 1 FROM activations WHERE license_id=? AND install_id=?", (row["id"], install_id)).fetchone()
            if current is None:
                count = conn.execute("SELECT COUNT(*) FROM activations WHERE license_id=?", (row["id"],)).fetchone()[0]
                if count >= row["max_devices"]:
                    raise LicenseError("activation_limit_reached")
                conn.execute("INSERT INTO activations (license_id,install_id,created_at,last_seen) VALUES (?,?,?,?)", (row["id"], install_id, now, now))
            else:
                conn.execute("UPDATE activations SET last_seen=? WHERE license_id=? AND install_id=?", (now, row["id"], install_id))
        return self._make_token(row, install_id, now)

    def renew(self, token: str, install_id: str) -> dict[str, Any]:
        try:
            payload = jwt.decode(token, self.public_key(), algorithms=["RS256"], audience=AUDIENCE,
                                 issuer=ISSUER, options={"verify_exp": False})
        except jwt.PyJWTError:
            raise LicenseError("invalid_token") from None
        now = int(time.time())
        if payload.get("install_id") != install_id:
            raise LicenseError("invalid_token")
        # A stolen or very old token cannot be silently reused forever.
        if not isinstance(payload.get("iat"), int) or not isinstance(payload.get("exp"), int):
            raise LicenseError("invalid_token")
        if payload["iat"] > now + 60 or payload["exp"] < now - MAX_RENEWAL_TOKEN_AGE_SECONDS:
            raise LicenseError("expired_token")
        with self._db() as conn:
            conn.execute("BEGIN IMMEDIATE")
            row = conn.execute("SELECT * FROM licenses WHERE id=?", (payload.get("sub"),)).fetchone()
            if row is None:
                raise LicenseError("invalid_token")
            self._enforce_license(row, now)
            activation = conn.execute("SELECT 1 FROM activations WHERE license_id=? AND install_id=?", (row["id"], install_id)).fetchone()
            if activation is None:
                raise LicenseError("activation_not_found")
            conn.execute("UPDATE activations SET last_seen=? WHERE license_id=? AND install_id=?", (now, row["id"], install_id))
        return self._make_token(row, install_id, now)

    def public_key(self) -> bytes:
        from cryptography.hazmat.primitives import serialization
        private = serialization.load_pem_private_key(self.signing_key, password=None)
        return private.public_key().public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )

    def revoke(self, license_id: str) -> bool:
        with self._db() as conn:
            updated = conn.execute("UPDATE licenses SET revoked_at=? WHERE id=? AND revoked_at IS NULL", (int(time.time()), license_id))
            return updated.rowcount == 1

    def extend(self, license_id: str, days: int) -> bool:
        if not 1 <= days <= 3650:
            raise ValueError("Invalid extension")
        with self._db() as conn:
            conn.execute("BEGIN IMMEDIATE")
            row = conn.execute("SELECT expires_at,revoked_at FROM licenses WHERE id=?", (license_id,)).fetchone()
            if row is None or row["revoked_at"] is not None or row["expires_at"] is None:
                return False
            current = max(int(time.time()), row["expires_at"])
            conn.execute("UPDATE licenses SET expires_at=? WHERE id=?", (current + days * 86400, license_id))
            return True

    def reset_device(self, license_id: str, install_id: str) -> bool:
        with self._db() as conn:
            changed = conn.execute("DELETE FROM activations WHERE license_id=? AND install_id=?", (license_id, install_id))
            return changed.rowcount == 1
