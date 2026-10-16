package com.wealthlens.api.anomaly;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A transaction that contributed to a flag. Stored as JSON in anomaly_flags.evidence. */
public record Evidence(LocalDate date, String type, BigDecimal amount, String ticker) {

    static Evidence of(ScanTxn t) {
        return new Evidence(t.date(), t.type(), t.amount(), t.ticker());
    }
}
