# Upstream community registry images are no longer available. Build official,
# pinned source releases for local development instead.
FROM golang:1.24.8-alpine AS server-build
ENV CGO_ENABLED=0
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    go install github.com/minio/minio@RELEASE.2025-10-15T17-29-55Z

FROM alpine:3.22 AS server
RUN apk add --no-cache ca-certificates
COPY --from=server-build /go/bin/minio /usr/local/bin/minio
EXPOSE 9000 9001
ENTRYPOINT ["minio"]

FROM golang:1.24.8-alpine AS client-build
ENV CGO_ENABLED=0
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    go install github.com/minio/mc@RELEASE.2025-08-13T08-35-41Z

FROM alpine:3.22 AS client
RUN apk add --no-cache ca-certificates
COPY --from=client-build /go/bin/mc /usr/local/bin/mc
ENTRYPOINT ["mc"]
