"""Generate synthetic clients, accounts and transactions for WealthLens.

Run generate_market_data.py first: trades are priced from instrument_prices.

What it creates
  * clients with a South African mix of names, each with a risk profile
  * 1-3 accounts per client (DISCRETIONARY always, sometimes a TFSA and/or an RA)
  * a year of realistic activity: lump-sum deposits, monthly debit orders,
    purchases split by the client's target allocation, dividends, monthly fees,
    and the odd withdrawal
  * a few deliberately SUSPICIOUS patterns for the anomaly engine to find.
    The "answer key" is written to output/planted_anomalies.csv, so later we can
    test whether the rules engine catches every one of them.

Every name, number and trade is SYNTHETIC. Same seed = same data every run.
Safe to re-run: it wipes clients, accounts and transactions first (TRUNCATE ... CASCADE
also clears allocation_snapshots and ai_output, which are derived from clients).
"""
import argparse
import csv
import math
import random
import re
import unicodedata
from collections import defaultdict
from decimal import ROUND_HALF_UP, Decimal
from pathlib import Path

from faker import Faker

from db import get_connection

SEED = 42
DEFAULT_CLIENTS = 200
ANOMALIES_PER_PATTERN = 3

# Faker has no 'en_ZA' locale, so we blend the closest ones for a South African mix
NAME_LOCALES = {"zu_ZA": 70, "en_GB": 20, "en_IN": 10}
OUTPUT_DIR = Path(__file__).resolve().parent / "output"

CENTS = Decimal("0.01")
MONTHLY_FEE_RATE = Decimal("0.0009")        # ~1.1% a year, charged monthly

# Dividend yield per payment, and the months it is paid in
DIVIDEND_RULES = {
    "LOCAL_EQUITY": (Decimal("0.015"), (3, 9)),
    "GLOBAL_EQUITY": (Decimal("0.007"), (6, 12)),
    "PROPERTY": (Decimal("0.035"), (4, 10)),
    "BOND": (Decimal("0.020"), (1, 4, 7, 10)),
}

# Younger clients lean aggressive, older clients conservative
RISK_WEIGHTS_BY_AGE = [
    (35, {"CONSERVATIVE": 15, "MODERATE": 40, "AGGRESSIVE": 45}),
    (55, {"CONSERVATIVE": 25, "MODERATE": 50, "AGGRESSIVE": 25}),
    (200, {"CONSERVATIVE": 55, "MODERATE": 35, "AGGRESSIVE": 10}),
]


def money(value):
    """Round to cents. Money is always Decimal, never float (floats can't store 0.1 exactly)."""
    return Decimal(value).quantize(CENTS, rounding=ROUND_HALF_UP)


def rand_money(rng, low, high):
    return money(Decimal(str(rng.uniform(low, high))))


def slug(text):
    """'Thabó Nkosi-Dlamini' -> 'thabonkosidlamini' (safe for an email address)."""
    plain = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", plain.lower())


# --------------------------------------------------------------------------- #
# Reading reference data from the database
# --------------------------------------------------------------------------- #

def load_market(cur):
    cur.execute("SELECT id, ticker, asset_class FROM instruments ORDER BY id")
    instruments = {tid: {"id": iid, "ticker": tid, "asset_class": ac}
                   for iid, tid, ac in cur.fetchall()}
    if not instruments:
        raise SystemExit("No instruments found. Run generate_market_data.py first.")

    cur.execute("SELECT instrument_id, price_date, close_price FROM instrument_prices")
    by_id = {i["id"]: i for i in instruments.values()}
    prices = defaultdict(dict)                 # prices[ticker][date] = Decimal
    for iid, d, p in cur.fetchall():
        prices[by_id[iid]["ticker"]][d] = p
    days = sorted({d for series in prices.values() for d in series})

    cur.execute("SELECT risk_profile, asset_class, target_pct FROM target_allocations")
    targets = defaultdict(dict)
    for profile, ac, pct in cur.fetchall():
        targets[profile][ac] = pct / 100
    return instruments, prices, days, targets


# --------------------------------------------------------------------------- #
# Simulating one account
# --------------------------------------------------------------------------- #

class AccountSim:
    """Plays an account forward day by day, keeping cash and units consistent,
    so we never sell units we don't own or withdraw cash we don't have."""

    def __init__(self, key, market, rng):
        self.key = key
        self.instruments, self.prices, _, _ = market
        self.rng = rng
        self.cash = Decimal("0")
        self.units = defaultdict(int)          # ticker -> whole units held
        self.txns = []

    def price(self, ticker, day):
        return self.prices[ticker][day]

    def value(self, day):
        held = sum(self.price(t, day) * u for t, u in self.units.items() if u)
        return money(held + self.cash)

    def _add(self, txn_type, day, amount, ticker=None, qty=None, price=None, desc=None):
        iid = self.instruments[ticker]["id"] if ticker else None
        self.txns.append((self.key, iid, txn_type, day,
                          Decimal(qty) if qty is not None else None,
                          price, amount, desc))

    # --- cash movements ---------------------------------------------------- #
    def deposit(self, day, amount, desc):
        self.cash += amount
        self._add("DEPOSIT", day, amount, desc=desc)

    def withdraw(self, day, amount, desc):
        amount = min(money(amount), money(self.cash))
        if amount > 0:
            self.cash -= amount
            self._add("WITHDRAWAL", day, amount, desc=desc)
        return amount

    def fee(self, day):
        amount = min(money(self.value(day) * MONTHLY_FEE_RATE), money(self.cash))
        if amount > 0:
            self.cash -= amount
            self._add("FEE", day, amount, desc="Monthly advice and admin fee")

    # --- trading ----------------------------------------------------------- #
    def buy(self, day, ticker, budget):
        px = self.price(ticker, day)
        qty = int(min(budget, self.cash) // px)            # JSE trades whole units
        if qty <= 0:
            return
        amount = money(px * qty)
        self.cash -= amount
        self.units[ticker] += qty
        self._add("BUY", day, amount, ticker, qty, px)

    def sell(self, day, ticker, qty):
        qty = min(qty, self.units[ticker])
        if qty <= 0:
            return
        px = self.price(ticker, day)
        amount = money(px * qty)
        self.cash += amount
        self.units[ticker] -= qty
        self._add("SELL", day, amount, ticker, qty, px)

    def raise_cash(self, day, needed):
        """Sell the biggest positions first until there's enough cash."""
        holdings = sorted((t for t, u in self.units.items() if u),
                          key=lambda t: self.price(t, day) * self.units[t], reverse=True)
        for t in holdings:
            short = needed - self.cash
            if short <= 0:
                break
            self.sell(day, t, math.ceil(short / self.price(t, day)))

    def invest(self, day, amount, weights, picks):
        """Split new money across asset classes by target weight, with a little noise."""
        for asset_class, weight in weights.items():
            if asset_class == "CASH" or weight == 0:
                continue
            budget = amount * weight * Decimal(str(self.rng.uniform(0.85, 1.15)))
            tickers = picks[asset_class]
            for t in tickers:
                self.buy(day, t, budget / len(tickers))

    def dividends(self, day):
        for t, u in list(self.units.items()):
            if not u:
                continue
            rule = DIVIDEND_RULES.get(self.instruments[t]["asset_class"])
            if rule and day.month in rule[1]:
                amount = money(self.price(t, day) * u * rule[0])
                if amount > 0:
                    self.cash += amount
                    self._add("DIVIDEND", day, amount, t, desc=f"{t} distribution")


def month_markers(days):
    """First and last trading day of every month."""
    first, last = set(), set()
    for prev, cur in zip(days, days[1:]):
        if cur.month != prev.month:
            last.add(prev)
            first.add(cur)
    first.add(days[0])
    last.add(days[-1])
    return first, last


# --------------------------------------------------------------------------- #
# Building the whole data set (pure Python, no database)
# --------------------------------------------------------------------------- #

def build_dataset(n_clients, market, seed):
    instruments, prices, days, targets = market
    rng = random.Random(seed)
    fake = Faker(list(NAME_LOCALES))
    fake.seed_instance(seed)
    first_days, last_days = month_markers(days)

    by_class = defaultdict(list)
    for t, i in instruments.items():
        by_class[i["asset_class"]].append(t)

    # 1. Clients
    clients = []
    for n in range(n_clients):
        # pick one locale per client so first and last names match
        locale = rng.choices(list(NAME_LOCALES), weights=list(NAME_LOCALES.values()))[0]
        first, last = fake[locale].first_name(), fake[locale].last_name()
        dob = fake[locale].date_of_birth(minimum_age=22, maximum_age=75)
        age = (days[-1] - dob).days // 365
        weights = next(w for limit, w in RISK_WEIGHTS_BY_AGE if age < limit)
        risk = rng.choices(list(weights), weights=list(weights.values()))[0]
        clients.append({
            "key": n, "first_name": first, "last_name": last,
            "email": f"{slug(first)}.{slug(last)}.{n + 1}@example.com",
            "date_of_birth": dob, "risk_profile": risk,
        })

    # 2. Accounts (opened in the first three months so there's history to analyse)
    accounts = []
    numbers = rng.sample(range(10_000_000, 99_999_999), n_clients * 3)
    for c in clients:
        types = ["DISCRETIONARY"]
        if rng.random() < 0.5:
            types.append("TFSA")
        if rng.random() < 0.4 and c["date_of_birth"].year > days[-1].year - 55:
            types.append("RA")             # retirement annuities: under-55s still saving
        for acct_type in types:
            accounts.append({
                "key": len(accounts), "client_key": c["key"],
                "account_number": f"WL{numbers.pop()}",
                "account_type": acct_type,
                "opened_on": rng.choice(days[:65]),
            })

    # 3. Pick accounts for the planted anomalies (one pattern per client)
    discretionary = [a for a in accounts if a["account_type"] == "DISCRETIONARY"]
    rng.shuffle(discretionary)
    used, planted = set(), {}

    def take(pattern, predicate=lambda a: True):
        chosen = [a for a in discretionary
                  if a["client_key"] not in used and predicate(a)][:ANOMALIES_PER_PATTERN]
        for a in chosen:
            used.add(a["client_key"])
            planted[a["key"]] = pattern

    take("PROFILE_MISMATCH",
         lambda a: clients[a["client_key"]]["risk_profile"] == "CONSERVATIVE")
    for pattern in ("STRUCTURING", "RAPID_IN_OUT", "DORMANT_THEN_SPIKE"):
        take(pattern)

    # 4. Simulate each account
    all_txns, answer_key = [], []
    for a in accounts:
        client = clients[a["client_key"]]
        weights = targets[client["risk_profile"]]
        picks = {ac: rng.sample(ts, min(len(ts), 2 if ac == "LOCAL_EQUITY" else 1))
                 for ac, ts in by_class.items()}
        sim = AccountSim(a["key"], market, rng)
        pattern = planted.get(a["key"])

        if a["account_type"] == "TFSA":
            lump, monthly = rand_money(rng, 5_000, 30_000), rand_money(rng, 500, 3_000)
        elif a["account_type"] == "RA":
            lump, monthly = rand_money(rng, 50_000, 1_500_000), rand_money(rng, 1_000, 10_000)
        else:
            lump = money(min(max(rng.lognormvariate(12.4, 0.9), 20_000), 5_000_000))
            monthly = rand_money(rng, 1_000, 20_000) if rng.random() < 0.6 else Decimal(0)
        if pattern == "DORMANT_THEN_SPIKE":
            monthly = Decimal(0)               # no activity after the first deposit

        # Client-driven events, keyed by date
        events = defaultdict(list)
        active_days = [d for d in days if d >= a["opened_on"]]
        later = active_days[20:]               # leave room after the opening deposit

        if a["account_type"] == "DISCRETIONARY" and not pattern and rng.random() < 0.25:
            events[rng.choice(later)].append(("withdraw_pct", rng.uniform(0.05, 0.20)))

        if pattern == "STRUCTURING":
            start = rng.randrange(20, len(active_days) - 15)
            run = active_days[start:start + rng.randint(4, 6)]   # several days in a row
            for d in run:
                events[d].append(("park_deposit", rand_money(rng, 45_000, 49_900)))
        elif pattern == "RAPID_IN_OUT":
            start = rng.randrange(20, len(active_days) - 5)
            size = rand_money(rng, 400_000, 900_000)
            out_day = active_days[start + rng.randint(1, 3)]
            events[active_days[start]].append(("park_deposit", size))
            events[out_day].append(("withdraw_amount", money(size * Decimal(str(rng.uniform(0.97, 1.0))))))
            run = [active_days[start], out_day]
        elif pattern == "DORMANT_THEN_SPIKE":
            spike = next((d for d in active_days if (d - a["opened_on"]).days >= 210),
                         active_days[-1])
            events[spike].append(("liquidate",))
            run = [a["opened_on"], spike]
        elif pattern == "PROFILE_MISMATCH":
            day = rng.choice(later)
            size = money(lump * Decimal(str(rng.uniform(1.5, 3.0))))  # dwarfs the rest
            events[day].append(("concentrated_buy", rng.choice(["MTN", "NPN"]), size))
            run = [day]

        for d in active_days:
            if d == a["opened_on"]:
                sim.deposit(d, lump, "Initial lump sum")
                sim.invest(d, lump, weights, picks)
            elif d in first_days and monthly:
                sim.deposit(d, monthly, "Monthly debit order")
                sim.invest(d, monthly, weights, picks)

            for event in events.get(d, []):
                kind = event[0]
                if kind == "withdraw_pct":
                    want = money(sim.value(d) * Decimal(str(event[1])))
                    sim.raise_cash(d, want)
                    sim.withdraw(d, want, "Client withdrawal")
                elif kind == "withdraw_amount":
                    sim.raise_cash(d, event[1])
                    sim.withdraw(d, event[1], "Client withdrawal")
                elif kind == "park_deposit":
                    sim.deposit(d, event[1], "EFT deposit")       # left in cash on purpose
                elif kind == "liquidate":
                    for t in list(sim.units):
                        sim.sell(d, t, sim.units[t])
                    sim.withdraw(d, sim.cash, "Full withdrawal")
                elif kind == "concentrated_buy":
                    _, ticker, amount = event
                    sim.deposit(d, amount, "EFT deposit")
                    sim.buy(d, ticker, amount)

            if d in first_days:
                sim.dividends(d)
            if d in last_days:
                sim.fee(d)

        all_txns.extend(sim.txns)
        if pattern:
            answer_key.append({"pattern": pattern, "account_key": a["key"],
                               "client_key": a["client_key"],
                               "start_date": min(run), "end_date": max(run)})

    return clients, accounts, all_txns, answer_key


# --------------------------------------------------------------------------- #
# Writing to the database
# --------------------------------------------------------------------------- #

def save(conn, clients, accounts, txns, answer_key):
    with conn.cursor() as cur:
        cur.execute("TRUNCATE transactions, accounts, clients RESTART IDENTITY CASCADE")

        client_ids = {}
        for c in clients:
            cur.execute(
                """INSERT INTO clients (first_name, last_name, email, date_of_birth, risk_profile)
                   VALUES (%s, %s, %s, %s, %s) RETURNING id""",
                (c["first_name"], c["last_name"], c["email"],
                 c["date_of_birth"], c["risk_profile"]))
            client_ids[c["key"]] = cur.fetchone()[0]

        account_ids = {}
        for a in accounts:
            cur.execute(
                """INSERT INTO accounts (client_id, account_number, account_type, opened_on)
                   VALUES (%s, %s, %s, %s) RETURNING id""",
                (client_ids[a["client_key"]], a["account_number"],
                 a["account_type"], a["opened_on"]))
            account_ids[a["key"]] = cur.fetchone()[0]

        # COPY streams thousands of rows in one go: much faster than INSERT per row
        with cur.copy("""COPY transactions (account_id, instrument_id, txn_type, trade_date,
                                            quantity, price, amount, description)
                         FROM STDIN""") as copy:
            for key, *rest in txns:
                copy.write_row((account_ids[key], *rest))

    OUTPUT_DIR.mkdir(exist_ok=True)
    path = OUTPUT_DIR / "planted_anomalies.csv"
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["pattern", "client_id", "account_id", "account_number",
                         "start_date", "end_date"])
        for row in sorted(answer_key, key=lambda r: r["pattern"]):
            a = accounts[row["account_key"]]
            writer.writerow([row["pattern"], client_ids[row["client_key"]],
                             account_ids[row["account_key"]], a["account_number"],
                             row["start_date"], row["end_date"]])
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--clients", type=int, default=DEFAULT_CLIENTS)
    parser.add_argument("--seed", type=int, default=SEED)
    args = parser.parse_args()

    with get_connection() as conn:
        with conn.cursor() as cur:
            market = load_market(cur)
        clients, accounts, txns, answer_key = build_dataset(args.clients, market, args.seed)
        path = save(conn, clients, accounts, txns, answer_key)
    # leaving the "with" block without an error commits everything at once

    counts = defaultdict(int)
    for t in txns:
        counts[t[2]] += 1
    print(f"Clients:      {len(clients)}")
    print(f"Accounts:     {len(accounts)}")
    print(f"Transactions: {len(txns)}")
    for txn_type in sorted(counts):
        print(f"  {txn_type:<11} {counts[txn_type]}")
    print(f"Planted anomalies: {len(answer_key)} -> {path.relative_to(path.parent.parent)}")


if __name__ == "__main__":
    main()
