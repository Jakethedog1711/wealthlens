"""Shared S3 access for the pipeline scripts (like db.py, but for S3).

Credentials come from `aws configure` (C:\\Users\\<you>\\.aws\\credentials), never from code.
The bucket name and region come from .env.
"""
import os
from pathlib import Path

import boto3
from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

RAW_PRICES_PREFIX = "raw/prices/"


def bucket_name():
    return os.environ["WEALTHLENS_BUCKET"]


def get_s3():
    """An S3 client for the region in .env (eu-north-1)."""
    return boto3.client("s3", region_name=os.environ.get("AWS_REGION", "eu-north-1"))


def price_key(day):
    """S3 key for one day's prices, using Hive-style partitioning: date=YYYY-MM-DD.
    Athena and Glue understand this "name=value" folder format automatically."""
    return f"{RAW_PRICES_PREFIX}date={day.isoformat()}/prices.csv"
