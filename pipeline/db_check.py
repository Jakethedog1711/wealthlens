"""Quick check that Python can reach the WealthLens Postgres database."""
import os
from pathlib import Path

import psycopg
from dotenv import load_dotenv

# .env lives in the project root, one folder above pipeline/
load_dotenv(Path(__file__).resolve().parent.parent / ".env")

with psycopg.connect(
    host="localhost",
    port=5432,
    dbname=os.environ["POSTGRES_DB"],
    user=os.environ["POSTGRES_USER"],
    password=os.environ["POSTGRES_PASSWORD"],
) as conn:
    version = conn.execute("SELECT version()").fetchone()[0]
    print("Connected to:", version.split(",")[0])

    tables = conn.execute(
        """
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = 'public'
        ORDER BY table_name
        """
    ).fetchall()

    print(f"Found {len(tables)} tables:")
    for (name,) in tables:
        print("  -", name)