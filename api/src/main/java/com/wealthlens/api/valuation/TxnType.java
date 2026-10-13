package com.wealthlens.api.valuation;

/** Matches the CHECK constraint on transactions.txn_type. */
public enum TxnType {
    BUY,
    SELL,
    DEPOSIT,
    WITHDRAWAL,
    DIVIDEND,
    FEE
}
