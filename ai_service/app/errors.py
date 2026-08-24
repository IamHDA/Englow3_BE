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
