"""Shared database connection for all WealthLens pipeline scripts.

Every script does `from db import get_connection` instead of repeating
the connection details, so they only live in one place.
"""
import os
from pathlib import Path

import psycopg
from dotenv import load_dotenv

# .env lives in the project root, one folder above pipeline/
load_dotenv(Path(__file__).resolve().parent.parent / ".env")


def get_connection():
    """Open a connection to the local WealthLens Postgres (running in Docker)."""
    return psycopg.connect(
        host=os.environ.get("POSTGRES_HOST", "localhost"),
        port=int(os.environ.get("POSTGRES_PORT", "5432")),
        dbname=os.environ["POSTGRES_DB"],
        user=os.environ["POSTGRES_USER"],
        password=os.environ["POSTGRES_PASSWORD"],
    )
