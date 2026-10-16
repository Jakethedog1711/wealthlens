"""Run a SQL file (or a quick query) on Athena and print the results.

Athena is asynchronous: you SUBMIT a query, it runs on AWS's side, you POLL until it's done,
then FETCH the results. Results are also saved as a CSV under s3://<bucket>/athena-results/.

Usage:
    python athena_query.py athena/01_create_database.sql
    python athena_query.py athena/03_check_all_prices.sql
    python athena_query.py --sql "SELECT * FROM wealthlens.raw_prices LIMIT 5"
"""
import argparse
import os
import sys
import time
from pathlib import Path

import boto3

from storage import bucket_name

WORKGROUP = "primary"
MAX_ROWS_SHOWN = 50


def run(sql):
    bucket = bucket_name()
    sql = sql.replace("{bucket}", bucket)          # fill in the bucket from .env
    athena = boto3.client("athena", region_name=os.environ.get("AWS_REGION", "eu-north-1"))

    # 1. Submit
    query_id = athena.start_query_execution(
        QueryString=sql,
        WorkGroup=WORKGROUP,
        ResultConfiguration={"OutputLocation": f"s3://{bucket}/athena-results/"},
    )["QueryExecutionId"]

    # 2. Poll until it finishes
    while True:
        execution = athena.get_query_execution(QueryExecutionId=query_id)["QueryExecution"]
        state = execution["Status"]["State"]
        if state in ("SUCCEEDED", "FAILED", "CANCELLED"):
            break
        time.sleep(1)

    stats = execution.get("Statistics", {})
    print(f"Status:       {state}")
    print(f"Data scanned: {stats.get('DataScannedInBytes', 0):,} bytes")
    print(f"Run time:     {stats.get('EngineExecutionTimeInMillis', 0):,} ms")
    if state != "SUCCEEDED":
        print("Reason:      ", execution["Status"].get("StateChangeReason", "unknown"))
        sys.exit(1)

    # 3. Fetch and print results (DDL like CREATE TABLE returns no rows)
    rows = athena.get_query_results(QueryExecutionId=query_id, MaxResults=MAX_ROWS_SHOWN + 1)["ResultSet"]["Rows"]
    if len(rows) <= 1:
        return
    table = [[col.get("VarCharValue", "") for col in row["Data"]] for row in rows]
    widths = [max(len(r[i]) for r in table) for i in range(len(table[0]))]
    print()
    for n, row in enumerate(table):
        print("  ".join(value.ljust(w) for value, w in zip(row, widths)))
        if n == 0:
            print("  ".join("-" * w for w in widths))


def main():
    parser = argparse.ArgumentParser(description="Run SQL on Athena.")
    parser.add_argument("file", nargs="?", help="path to a .sql file")
    parser.add_argument("--sql", help="a query typed directly")
    args = parser.parse_args()
    if not (args.file or args.sql):
        parser.error("give a .sql file or --sql \"...\"")
    run(args.sql or Path(args.file).read_text(encoding="utf-8"))


if __name__ == "__main__":
    main()
