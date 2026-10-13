package com.wealthlens.api.valuation;

/** Matches the CHECK constraint on instruments.asset_class. CASH is uninvested money. */
public enum AssetClass {
    LOCAL_EQUITY,
    GLOBAL_EQUITY,
    BOND,
    PROPERTY,
    CASH
}
