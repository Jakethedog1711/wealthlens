"""Pipeline step 2 (LOAD): read raw price files from S3, validate them, load into Postgres.

For every file under s3://<bucket>/raw/prices/date=.../ :
  1. download and parse the CSV
  2. VALIDATE each row: known ticker, price > 0, and price_date matches the folder's date
  3. upsert valid rows into instrument_prices (source = 'S3_PIPELINE')
Bad rows are skipped and reported, never loaded. Safe to re-run: rows are updated, not duplicated.

Usage:
    python load_prices.py                                     # every file in S3
    python load_prices.py --start 2026-09-01 --end 2026-09-30
"""
import argparse
import csv
import io
import re
from datetime import date
from decimal import Decimal, InvalidOperation

from db import get_connection
from storage import RAW_PRICES_PREFIX, bucket_name, get_s3

SOURCE = "S3_PIPELINE"
PARTITION = re.compile(r"date=(\d{4}-\d{2}-\d{2})/")


def list_price_files(s3, bucket, start, end):
    """[(day, key), ...] for every daily file in the date range, oldest first.
    S3 returns at most 1000 keys per request, so a paginator fetches the rest."""
    files = []
    for page in s3.get_paginator("list_objects_v2").paginate(Bucket=bucket, Prefix=RAW_PRICES_PREFIX):
        for obj in page.get("Contents", []):
            match = PARTITION.search(obj["Key"])
            if not match:
                continue
            day = date.fromisoformat(match.group(1))
            if (start is None or day >= start) and (end is None or day <= end):
                files.append((day, obj["Key"]))
    return sorted(files)


def validate(row, partition_day, instrument_ids):
    """Return (instrument_id, price_date, price) for a good row, or raise ValueError saying why."""
    ticker = row.get("ticker", "").strip()
    if ticker not in instrument_ids:
        raise ValueError(f"unknown ticker {ticker!r}")
    try:
        price = Decimal(row["close_price"])
    except (InvalidOperation, KeyError):
        raise ValueError(f"price is not a number: {row.get('close_price')!r}")
    if price <= 0:
        raise ValueError(f"price must be positive, got {price}")
    price_date = date.fromisoformat(row["price_date"])
    if price_date != partition_day:
        raise ValueError(f"price_date {price_date} is in the folder for {partition_day}")
    return instrument_ids[ticker], price_date, price


def main():
    parser = argparse.ArgumentParser(description="Load raw price files from S3 into Postgres.")
    parser.add_argument("--start", type=date.fromisoformat)
    parser.add_argument("--end", type=date.fromisoformat)
    args = parser.parse_args()

    s3, bucket = get_s3(), bucket_name()
    files = list_price_files(s3, bucket, args.start, args.end)
    if not files:
        raise SystemExit("No price files found in S3. Run ingest_prices.py first.")

    with get_connection() as conn:
        instrument_ids = dict(conn.execute("SELECT ticker, id FROM instruments").fetchall())

        good, rejected = [], []
        for day, key in files:
            body = s3.get_object(Bucket=bucket, Key=key)["Body"].read().decode("utf-8")
            for line_no, row in enumerate(csv.DictReader(io.StringIO(body)), start=2):
                try:
                    good.append(validate(row, day, instrument_ids))
                except ValueError as problem:
                    rejected.append(f"{key} line {line_no}: {problem}")

        with conn.cursor() as cur:
            cur.executemany(
                """
                INSERT INTO instrument_prices (instrument_id, price_date, close_price, source)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (instrument_id, price_date) DO UPDATE
                    SET close_price = EXCLUDED.close_price,
                        source = EXCLUDED.source,
                        loaded_at = now()
                """,
                [(iid, d, p, SOURCE) for iid, d, p in good],
            )
    # leaving the "with" block without an error commits everything at once

    print(f"Files read:    {len(files)} ({files[0][0]} to {files[-1][0]})")
    print(f"Rows loaded:   {len(good)}")
    print(f"Rows rejected: {len(rejected)}")
    for problem in rejected[:10]:
        print("  -", problem)


if __name__ == "__main__":
    main()
