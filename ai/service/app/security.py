import secrets
from typing import Annotated

from fastapi import Depends, Header

from app.config import Settings, get_settings
from app.errors import ServiceError


async def require_internal_api_key(
    settings: Annotated[Settings, Depends(get_settings)],
    x_internal_api_key: str | None = Header(default=None),
) -> None:
    configured = settings.internal_api_key.get_secret_value()
    if not configured:
        raise ServiceError(
            code="SERVICE_NOT_CONFIGURED",
            message="Internal authentication is not configured",
            status_code=503,
            retryable=True,
        )
    if x_internal_api_key is None or not secrets.compare_digest(
        x_internal_api_key.encode("utf-8"), configured.encode("utf-8")
    ):
        raise ServiceError(
            code="UNAUTHORIZED_SERVICE",
            message="Invalid internal service credential",
            status_code=401,
        )
