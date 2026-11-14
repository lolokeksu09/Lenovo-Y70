"""Public license activation/renewal API. Payment and admin actions are not exposed here."""
from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from uuid import UUID

from .core import LicenseError, LicenseStore, config_from_env


class ActivateRequest(BaseModel):
    key: str = Field(min_length=1, max_length=100)
    install_id: UUID


class RenewRequest(BaseModel):
    token: str = Field(min_length=1, max_length=8192)
    install_id: UUID


class LicenseResponse(BaseModel):
    token: str
    expires_at: int
    plan: str


def create_app(store: LicenseStore | None = None) -> FastAPI:
    store = store or config_from_env()
    app = FastAPI(title="Overlay RU License API", docs_url=None, redoc_url=None, openapi_url=None)

    @app.middleware("http")
    async def no_store(request: Request, call_next):
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.exception_handler(LicenseError)
    async def error_handler(_request: Request, error: LicenseError):
        return JSONResponse({"error": error.code}, status_code=error.status,
                            headers={"Cache-Control": "no-store"})

    @app.exception_handler(RequestValidationError)
    async def invalid_input(_request: Request, _error: RequestValidationError):
        # Never reflect raw keys or bearer tokens in validation errors.
        return JSONResponse({"error": "invalid_request"}, status_code=422,
                            headers={"Cache-Control": "no-store"})

    @app.get("/health")
    def health():
        return {"status": "ok"}

    @app.post("/v1/activate", response_model=LicenseResponse)
    def activate(body: ActivateRequest):
        if body.install_id.version != 4:
            raise LicenseError("invalid_install_id", status=422)
        return store.activate(body.key, str(body.install_id))

    @app.post("/v1/renew", response_model=LicenseResponse)
    def renew(body: RenewRequest):
        if body.install_id.version != 4:
            raise LicenseError("invalid_install_id", status=422)
        return store.renew(body.token, str(body.install_id))

    return app


def get_app() -> FastAPI:
    return create_app()
