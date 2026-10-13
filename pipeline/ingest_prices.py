"""Pipeline step 1 (EXTRACT): publish daily JSE closing prices to S3 as raw CSV files.

We have no real JSE price feed, so this script plays the role of one: it re-runs the
same seeded simulation as generate_market_data.py (so prices match exactly) and writes
one CSV per trading day to:

    s3://<bucket>/raw/prices/date=2026-09-30/prices.csv

Raw files are never edited afterwards. If a later step has a bug, we fix the step and
re-run it from the raw files: that's the point of keeping a raw layer.

Usage:
    python ingest_prices.py                                   # the whole year
    python ingest_prices.py --start 2026-09-01 --end 2026-09-30
"""
import argparse
import csv
import io
import random
from datetime import date

import generate_market_data as market
from storage import bucket_name, get_s3, price_key


def simulated_prices():
    """{day: [(ticker, close_price), ...]} for every trading day, identical to the database.
    The random generator must be used in exactly the same order as generate_market_data.py,
    otherwise the "same seed" would give different prices."""
    rng = random.Random(market.SEED)
    days = list(market.trading_days(market.START_DATE, market.END_DATE))
    by_day = {d: [] for d in days}
    for ticker, _name, _asset_class, start, drift, vol in market.INSTRUMENTS:
        path = market.simulate_path(start, drift, vol, len(days), rng)
        for d, p in zip(days, path):
            by_day[d].append((ticker, market.to_decimal(p, 4)))
    return by_day


def to_csv(day, rows):
    """One day's prices as CSV text, with a header row."""
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(["ticker", "price_date", "close_price", "currency"])
    for ticker, price in rows:
        writer.writerow([ticker, day.isoformat(), price, "ZAR"])
    return buffer.getvalue()


def main():
    parser = argparse.ArgumentParser(description="Upload daily price files to S3 (raw layer).")
    parser.add_argument("--start", type=date.fromisoformat, default=market.START_DATE)
    parser.add_argument("--end", type=date.fromisoformat, default=market.END_DATE)
    args = parser.parse_args()

    s3, bucket = get_s3(), bucket_name()
    uploaded = rows = 0
    for day, prices in simulated_prices().items():
        if not (args.start <= day <= args.end):
            continue
        s3.put_object(Bucket=bucket, Key=price_key(day),
                      Body=to_csv(day, prices).encode("utf-8"), ContentType="text/csv")
        uploaded += 1
        rows += len(prices)
        if uploaded % 50 == 0:
            print(f"  ...{uploaded} files uploaded")

    print(f"Bucket:   s3://{bucket}/raw/prices/")
    print(f"Files:    {uploaded}")
    print(f"Rows:     {rows}")


if __name__ == "__main__":
    main()
