import math
from dataclasses import dataclass
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime


@dataclass(slots=True)
class ServiceError(Exception):
    code: str
    message: str
    status_code: int
    retryable: bool = False
    retry_after_seconds: int | None = None

    def __str__(self) -> str:
        return self.message


class ProviderError(ServiceError):
    pass


def is_retryable_http_status(status_code: int) -> bool:
    return status_code in {408, 425, 429} or status_code >= 500


def retry_after_seconds(value: str | None) -> int | None:
    if not value:
        return None
    try:
        seconds = float(value)
    except ValueError:
        try:
            seconds = (parsedate_to_datetime(value) - datetime.now(UTC)).total_seconds()
        except (ValueError, TypeError, OverflowError):
            return None
    return min(600, max(1, int(seconds))) if math.isfinite(seconds) else None
