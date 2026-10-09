#!/usr/bin/env python3
"""Copy the contents of the app's object-storage buckets from one S3-compatible store to another.

The database holds object keys relative to a bucket, never full addresses, so moving the files is
the whole migration: copy them here, then point the backend at the new store with the S3_* and
STORAGE_* variables. Nothing in the database changes.

Safe to re-run: an object already at the target with the same size is skipped, so an interrupted
copy simply continues. Credentials come from the environment or the repository's .env (which git
ignores) and are never printed.

    SOURCE_S3_ENDPOINT / SOURCE_S3_ACCESS_KEY / SOURCE_S3_SECRET_KEY / [SOURCE_S3_REGION]
    TARGET_S3_ENDPOINT / TARGET_S3_ACCESS_KEY / TARGET_S3_SECRET_KEY / [TARGET_S3_REGION]

    python scripts/migrate_object_storage.py --dry-run
    python scripts/migrate_object_storage.py --buckets exams learning
    python scripts/migrate_object_storage.py --target-prefix englow3-     # englow3-exams, englow3-learning
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

# `speaking` holds learners' private recordings and is left out unless asked for by name: a new
# environment starts without them, and copying test recordings into production helps nobody.
DEFAULT_BUCKETS = ["exams", "learning"]


def load_env_file() -> None:
    """Read the repository's .env without overriding what the shell already set."""
    path = Path(__file__).resolve().parent.parent / ".env"
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, _, value = line.partition("=")
        os.environ.setdefault(name.strip(), value.strip().strip('"'))


def client(side: str):
    wanted = [f"{side}_S3_{part}" for part in ("ENDPOINT", "ACCESS_KEY", "SECRET_KEY")]
    blank = [name for name in wanted if not os.environ.get(name)]
    if blank:
        sys.exit(f"Fill in {', '.join(blank)} in .env (or export them) - see the top of this file.")
    return boto3.client(
        "s3",
        endpoint_url=os.environ[f"{side}_S3_ENDPOINT"],
        region_name=os.environ.get(f"{side}_S3_REGION") or "auto",
        aws_access_key_id=os.environ[f"{side}_S3_ACCESS_KEY"],
        aws_secret_access_key=os.environ[f"{side}_S3_SECRET_KEY"],
        config=Config(retries={"max_attempts": 5, "mode": "standard"}, max_pool_connections=32),
    )


def objects(s3, bucket: str):
    for page in s3.get_paginator("list_objects_v2").paginate(Bucket=bucket):
        yield from page.get("Contents", [])


def ensure_bucket(s3, bucket: str) -> None:
    try:
        s3.head_bucket(Bucket=bucket)
    except ClientError:
        s3.create_bucket(Bucket=bucket)


def copy_one(source, target, src_bucket: str, dst_bucket: str, key: str, size: int, existing: dict[str, int]):
    if existing.get(key) == size:
        return "skipped", size
    body = source.get_object(Bucket=src_bucket, Key=key)
    target.put_object(
        Bucket=dst_bucket,
        Key=key,
        Body=body["Body"].read(),
        ContentType=body.get("ContentType") or "application/octet-stream",
    )
    return "copied", size


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--buckets", nargs="+", default=DEFAULT_BUCKETS)
    parser.add_argument("--target-prefix", default="", help="prepend to each bucket name at the target")
    parser.add_argument("--dry-run", action="store_true", help="count what would be copied; write nothing")
    parser.add_argument("--workers", type=int, default=16)
    args = parser.parse_args()

    load_env_file()
    source, target = client("SOURCE"), client("TARGET")
    failed = 0
    for bucket in args.buckets:
        dst = f"{args.target_prefix}{bucket}"
        listed = list(objects(source, bucket))
        weight = sum(o["Size"] for o in listed) / 1e6
        if args.dry_run:
            print(f"{bucket} -> {dst}: {len(listed)} objects, {weight:.1f} MB (dry run, nothing written)")
            continue
        ensure_bucket(target, dst)
        existing = {o["Key"]: o["Size"] for o in objects(target, dst)}
        counts = {"copied": 0, "skipped": 0}
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            jobs = {
                pool.submit(copy_one, source, target, bucket, dst, o["Key"], o["Size"], existing): o["Key"]
                for o in listed
            }
            for done in as_completed(jobs):
                try:
                    counts[done.result()[0]] += 1
                except Exception as error:  # report the key, keep copying the rest
                    failed += 1
                    print(f"  FAILED {jobs[done]}: {type(error).__name__}", file=sys.stderr)
        print(f"{bucket} -> {dst}: {counts['copied']} copied, {counts['skipped']} already there, of {len(listed)}")

    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
