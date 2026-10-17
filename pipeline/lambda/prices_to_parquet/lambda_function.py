"""Pipeline step 2 (TRANSFORM), running in AWS Lambda: raw CSV -> curated Parquet.

Trigger: S3 "object created" events for raw/prices/*.csv
Output:  s3://<bucket>/curated/prices/date=YYYY-MM-DD/prices.parquet

Why Parquet? It stores data by COLUMN, compressed, with proper types. Athena then reads only
the columns a query needs, so queries scan far fewer bytes, and Athena bills per byte scanned.

Why event-driven? Nobody has to remember to run this: the moment a raw file lands, it's
converted. Each raw file maps to exactly one curated file, so re-running is safe (idempotent).

Runs on Python 3.12 with the AWS-managed layer "AWSSDKPandas-Python312" (pandas + pyarrow +
awswrangler). Paste this whole file into the Lambda console editor.
"""
import urllib.parse
from decimal import Decimal, InvalidOperation

import pandas as pd

RAW_PREFIX = "raw/prices/"
CURATED_PREFIX = "curated/prices/"
EXPECTED_COLUMNS = ["ticker", "price_date", "close_price", "currency"]
ATHENA_TYPES = {"ticker": "string", "price_date": "date", "close_price": "decimal(19,4)", "currency": "string"}


def lambda_handler(event, context):
    results = []
    for record in event.get("Records", []):
        bucket = record["s3"]["bucket"]["name"]
        key = urllib.parse.unquote_plus(record["s3"]["object"]["key"])  # S3 URL-encodes keys in events
        results.append(convert(bucket, key))
    print(results)          # print() output goes to CloudWatch Logs
    return {"converted": results}


def day_from_key(key):
    """raw/prices/date=2026-09-30/prices.csv -> '2026-09-30'.
    Refusing anything outside raw/prices/ also guarantees we never re-trigger on our own output."""
    if not key.startswith(RAW_PREFIX) or not key.endswith(".csv"):
        raise ValueError(f"Not a raw price file: {key}")
    folder = key[len(RAW_PREFIX):].split("/")[0]
    if not folder.startswith("date="):
        raise ValueError(f"Missing date= partition folder: {key}")
    return folder[len("date="):]


def to_decimal(value):
    try:
        return Decimal(str(value).strip())
    except InvalidOperation:
        return None


def clean(df, day):
    """Validate one day's raw rows and give them proper types. Raises on bad data:
    a failed file is better than silently publishing wrong prices."""
    if list(df.columns) != EXPECTED_COLUMNS:
        raise ValueError(f"Unexpected columns {list(df.columns)}, expected {EXPECTED_COLUMNS}")
    df = df.copy()
    df["ticker"] = df["ticker"].str.strip().str.upper()
    df["currency"] = df["currency"].str.strip().str.upper()
    df["close_price"] = df["close_price"].map(to_decimal)

    problems = []
    if (df["price_date"].str.strip() != day).any():
        problems.append("rows dated outside their partition")
    if df["close_price"].map(lambda p: p is None or p <= 0).any():
        problems.append("missing, non-numeric or non-positive prices")
    if df["ticker"].duplicated().any():
        problems.append("duplicate tickers")
    if problems:
        raise ValueError(f"{day}: " + "; ".join(problems))

    df["price_date"] = pd.to_datetime(df["price_date"]).dt.date
    return df


def convert(bucket, key):
    import awswrangler as wr       # imported here so the rest can be tested without AWS libraries

    day = day_from_key(key)
    raw = wr.s3.read_csv(f"s3://{bucket}/{key}", dtype=str)
    curated = clean(raw, day)
    target = f"s3://{bucket}/{CURATED_PREFIX}date={day}/prices.parquet"
    wr.s3.to_parquet(curated, path=target, index=False, dtype=ATHENA_TYPES)
    return {"source": key, "target": target, "rows": len(curated)}
