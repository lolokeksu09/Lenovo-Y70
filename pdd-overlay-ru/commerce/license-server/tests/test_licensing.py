import concurrent.futures
import sqlite3
from time import time
from uuid import uuid4

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
from fastapi.testclient import TestClient

from overlay_license.api import create_app
from overlay_license.core import AUDIENCE, ISSUER, LicenseStore


@pytest.fixture(scope="session")
def signing_key():
    private = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    return private.private_bytes(serialization.Encoding.PEM,
                                 serialization.PrivateFormat.PKCS8,
                                 serialization.NoEncryption())


@pytest.fixture
def store(tmp_path, signing_key):
    return LicenseStore(tmp_path / "db.sqlite3", b"X" * 32, signing_key)


@pytest.fixture
def client(store):
    return TestClient(create_app(store))


def create(store, plan="pro_30d", days=30, seats=1):
    return store.issue(plan, days, seats)


def activate(client, key, install_id):
    return client.post("/v1/activate", json={"key": key, "install_id": str(install_id)})


def verify(store, token, install_id):
    return jwt.decode(token, store.public_key(), algorithms=["RS256"],
                      audience=AUDIENCE, issuer=ISSUER, options={"require": ["sub", "iat", "nbf", "exp", "install_id", "plan"]})


def test_successful_issue_activate_and_offline_verification(store, client):
    lid, key = create(store)
    dev = uuid4()
    assert key.startswith("OVR-")
    assert len(key) == 43
    r = activate(client, key, dev)
    assert r.status_code == 200
    claims = verify(store, r.json()["token"], dev)
    assert claims["sub"] == lid
    assert claims["install_id"] == str(dev)
    assert claims["plan"] == "pro_30d"
    assert 0 < claims["exp"] - claims["iat"] <= 7 * 86400
    assert "no-store" in r.headers["cache-control"]
    assert "key" not in r.text
    with store._db() as db:
        recorded = db.execute("SELECT key_hash FROM licenses WHERE id=?", (lid,)).fetchone()[0]
    assert key not in recorded  # No plaintext license is persisted.


def test_wrong_and_malformed_keys_dont_activate(client, store):
    _, key = create(store)
    for value in (key[:-1], "X" * 43, key.lower(), "OVR-" + "Z" * 35):
        r = activate(client, value, uuid4())
        assert r.status_code == 403
        assert r.json() == {"error": "invalid_license"}
        assert value not in r.text
    assert activate(client, key, uuid4()).status_code == 200


def test_device_limit_and_same_device_idempotency(client, store):
    _, key = create(store)
    one, two = uuid4(), uuid4()
    assert activate(client, key, one).status_code == 200
    assert activate(client, key, one).status_code == 200
    denied = activate(client, key, two)
    assert denied.status_code == 403
    assert denied.json()["error"] == "activation_limit_reached"


def test_revocation_blocks_refresh_and_new_activation(client, store):
    lid, key = create(store)
    one = uuid4()
    original = activate(client, key, one).json()["token"]
    assert store.revoke(lid)
    refresh = client.post("/v1/renew", json={"token": original, "install_id": str(one)})
    assert refresh.status_code == 403
    assert refresh.json()["error"] == "license_revoked"
    assert activate(client, key, one).json()["error"] == "license_revoked"


def test_expiry_stops_renewal(client, store):
    lid, key = create(store)
    dev = uuid4()
    token = activate(client, key, dev).json()["token"]
    with store._db() as db:
        db.execute("UPDATE licenses SET expires_at = ? WHERE id = ?", (int(time()) - 1, lid))
    assert client.post("/v1/renew", json={"token": token, "install_id": str(dev)}).json()["error"] == "license_expired"


def test_tampered_and_wrong_install_token(client, store):
    _, key = create(store)
    one = uuid4()
    token = activate(client, key, one).json()["token"]
    altered = token[:100] + ("A" if token[100] != "A" else "B") + token[101:]
    denied = client.post("/v1/renew", json={"token": altered, "install_id": str(one)})
    assert denied.status_code == 403
    assert denied.json()["error"] == "invalid_token"
    denied = client.post("/v1/renew", json={"token": token, "install_id": str(uuid4())})
    assert denied.status_code == 403
    assert denied.json()["error"] == "invalid_token"


def test_refresh_renews_access_without_reusing_original_key(client, store):
    _, key = create(store)
    dev = uuid4()
    token = activate(client, key, dev).json()["token"]
    r = client.post("/v1/renew", json={"token": token, "install_id": str(dev)})
    assert r.status_code == 200
    assert verify(store, r.json()["token"], dev)["install_id"] == str(dev)


def test_admin_extend_and_device_reset(client, store):
    lid, key = create(store)
    one, two = uuid4(), uuid4()
    activate(client, key, one)
    assert store.extend(lid, 30)
    assert store.reset_device(lid, str(one))
    assert activate(client, key, two).status_code == 200
    assert not store.reset_device(lid, str(one))


def test_lifetime_proposal_and_plan_validation(client, store):
    lid, key = store.issue("pro_lifetime", None)
    assert activate(client, key, uuid4()).status_code == 200
    with pytest.raises(ValueError):
        store.issue("pro_30d", None)
    with pytest.raises(ValueError):
        store.issue("pro_30d", 365)
    with pytest.raises(ValueError):
        store.issue("pro_lifetime", 30)


def test_invalid_uuid_and_input_error_do_not_echo_secret(client, store):
    _, key = create(store)
    res = client.post("/v1/activate", json={"key": key, "install_id": "my-secret-device-id"})
    assert res.status_code == 422
    assert res.json() == {"error": "invalid_request"}
    assert key not in res.text
    assert client.get("/health").json() == {"status": "ok"}


def test_concurrent_activation_cannot_exceed_one_device(store, client):
    _, key = create(store)
    devices = [uuid4() for _ in range(7)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=7) as executor:
        codes = list(executor.map(lambda device: activate(client, key, device).status_code, devices))
    assert codes.count(200) == 1
    assert codes.count(403) == 6


def test_private_key_is_not_served(client, store):
    assert client.get("/openapi.json").status_code == 404
    assert client.get("/docs").status_code == 404
    assert client.get("/v1/admin/issue").status_code == 404
    assert "BEGIN PRIVATE" not in client.get("/health").text
