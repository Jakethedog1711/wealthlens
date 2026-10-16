"""Score the anomaly engine against the answer key the generator planted.

generate_clients.py planted 12 suspicious accounts (3 per pattern) and wrote them to
output/planted_anomalies.csv. The Java engine (POST /anomalies/scan) wrote its flags to the
anomaly_flags table. This script compares the two:

  caught       = flagged AND planted       (true positive)
  false alarm  = flagged but NOT planted   (false positive: wasted compliance time)
  missed       = planted but NOT flagged   (false negative: the dangerous one)

Usage (from pipeline/, with the venv active, after running the scan):
    python score_anomalies.py
"""
import csv
from pathlib import Path

from db import get_connection

ANSWER_KEY = Path(__file__).resolve().parent / "output" / "planted_anomalies.csv"
PATTERNS = ["STRUCTURING", "RAPID_IN_OUT", "DORMANT_THEN_SPIKE", "PROFILE_MISMATCH"]


def main():
    with open(ANSWER_KEY, newline="", encoding="utf-8") as f:
        planted = {(row["pattern"], int(row["account_id"])) for row in csv.DictReader(f)}

    with get_connection() as conn, conn.cursor() as cur:
        cur.execute("SELECT rule, account_id FROM anomaly_flags")
        flagged = {(rule, account_id) for rule, account_id in cur.fetchall()}

    if not flagged:
        raise SystemExit("No flags found. Run POST /anomalies/scan first.")

    print(f"{'Pattern':<20}{'Planted':>8}{'Caught':>8}{'False alarms':>14}{'Missed':>8}")
    totals = [0, 0, 0, 0]
    for pattern in PATTERNS:
        p = {x for x in planted if x[0] == pattern}
        f = {x for x in flagged if x[0] == pattern}
        row = [len(p), len(p & f), len(f - p), len(p - f)]
        totals = [a + b for a, b in zip(totals, row)]
        print(f"{pattern:<20}{row[0]:>8}{row[1]:>8}{row[2]:>14}{row[3]:>8}")
    print(f"{'TOTAL':<20}{totals[0]:>8}{totals[1]:>8}{totals[2]:>14}{totals[3]:>8}")

    caught, false_alarms = totals[1], totals[2]
    precision = caught / (caught + false_alarms) if caught + false_alarms else 0
    recall = caught / totals[0] if totals[0] else 0
    print()
    print(f"Recall    (share of planted cases caught):      {recall:.0%}")
    print(f"Precision (share of flags that were real cases): {precision:.0%}")


if __name__ == "__main__":
    main()
