"""Generate synthetic market data: JSE instruments, daily prices and USD/ZAR rates.

Tickers mirror real JSE instruments, but every price here is SYNTHETIC.
Safe to re-run: existing rows are updated, not duplicated.
"""
import math
import random
from datetime import date, timedelta
from decimal import Decimal

from db import get_connection

SEED = 42                       # same seed = same "random" data every run
START_DATE = date(2025, 10, 1)
END_DATE = date(2026, 9, 30)
SOURCE = "SYNTHETIC"
TRADING_DAYS_PER_YEAR = 252

# ticker, name, asset_class, start_price (ZAR), annual_drift, annual_volatility
INSTRUMENTS = [
    ("NPN",    "Naspers",               "LOCAL_EQUITY",  3500.00, 0.10, 0.35),
    ("FSR",    "FirstRand",             "LOCAL_EQUITY",    80.00, 0.09, 0.25),
    ("SBK",    "Standard Bank Group",   "LOCAL_EQUITY",   230.00, 0.09, 0.25),
    ("MTN",    "MTN Group",             "LOCAL_EQUITY",   120.00, 0.06, 0.40),
    ("SHP",    "Shoprite Holdings",     "LOCAL_EQUITY",   300.00, 0.08, 0.25),
    ("CPI",    "Capitec Bank",          "LOCAL_EQUITY",  3300.00, 0.12, 0.30),
    ("STX40",  "Satrix 40 ETF",         "LOCAL_EQUITY",    95.00, 0.09, 0.17),
    ("STXWDM", "Satrix MSCI World ETF", "GLOBAL_EQUITY",  110.00, 0.11, 0.16),
    ("STX500", "Satrix S&P 500 ETF",    "GLOBAL_EQUITY",  140.00, 0.12, 0.17),
    ("STXGOV", "Satrix SA Bond ETF",    "BOND",            55.00, 0.08, 0.07),
    ("STXPRO", "Satrix Property ETF",   "PROPERTY",         9.00, 0.08, 0.18),
]


def trading_days(start, end):
    """Every weekday between start and end (public holidays ignored for simplicity)."""
    d = start
    while d <= end:
        if d.weekday() < 5:          # Monday=0 ... Friday=4
            yield d
        d += timedelta(days=1)


def simulate_path(start_price, drift, vol, n_days, rng):
    """Random daily prices using geometric Brownian motion (the standard model)."""
    dt = 1 / TRADING_DAYS_PER_YEAR
    prices = [start_price]
    for _ in range(n_days - 1):
        shock = rng.gauss(0, 1)
        growth = math.exp((drift - 0.5 * vol**2) * dt + vol * math.sqrt(dt) * shock)
        prices.append(prices[-1] * growth)
    return prices


def to_decimal(value, places):
    """Convert a float to an exact Decimal before it goes into a NUMERIC column."""
    return Decimal(f"{value:.{places}f}")


def main():
    rng = random.Random(SEED)
    days = list(trading_days(START_DATE, END_DATE))

    with get_connection() as conn:
        with conn.cursor() as cur:
            price_rows = []
            for ticker, name, asset_class, start, drift, vol in INSTRUMENTS:
                cur.execute(
                    """
                    INSERT INTO instruments (ticker, name, asset_class)
                    VALUES (%s, %s, %s)
                    ON CONFLICT (ticker) DO UPDATE
                        SET name = EXCLUDED.name, asset_class = EXCLUDED.asset_class
                    RETURNING id
                    """,
                    (ticker, name, asset_class),
                )
                instrument_id = cur.fetchone()[0]

                path = simulate_path(start, drift, vol, len(days), rng)
                for d, p in zip(days, path):
                    price_rows.append((instrument_id, d, to_decimal(p, 4), SOURCE))

            cur.executemany(
                """
                INSERT INTO instrument_prices (instrument_id, price_date, close_price, source)
                VALUES (%s, %s, %s, %s)
                ON CONFLICT (instrument_id, price_date) DO UPDATE
                    SET close_price = EXCLUDED.close_price,
                        source = EXCLUDED.source,
                        loaded_at = now()
                """,
                price_rows,
            )

            fx_path = simulate_path(17.50, 0.0, 0.12, len(days), rng)
            fx_rows = [(d, "USD", "ZAR", to_decimal(r, 6), SOURCE)
                       for d, r in zip(days, fx_path)]
            cur.executemany(
                """
                INSERT INTO fx_rates (rate_date, base_currency, quote_currency, rate, source)
                VALUES (%s, %s, %s, %s, %s)
                ON CONFLICT (rate_date, base_currency, quote_currency) DO UPDATE
                    SET rate = EXCLUDED.rate, source = EXCLUDED.source, loaded_at = now()
                """,
                fx_rows,
            )
    # leaving the "with" block without an error commits everything at once

    print(f"Instruments:  {len(INSTRUMENTS)}")
    print(f"Trading days: {len(days)} ({days[0]} to {days[-1]})")
    print(f"Price rows:   {len(price_rows)}")
    print(f"FX rows:      {len(fx_rows)}")


if __name__ == "__main__":
    main()