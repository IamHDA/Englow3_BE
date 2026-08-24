from dataclasses import dataclass


@dataclass(slots=True)
class ServiceError(Exception):
    code: str
    message: str
    status_code: int
    retryable: bool = False

    def __str__(self) -> str:
        return self.message


class ProviderError(ServiceError):
    pass


def is_retryable_http_status(status_code: int) -> bool:
    return status_code in {408, 425, 429} or status_code >= 500
