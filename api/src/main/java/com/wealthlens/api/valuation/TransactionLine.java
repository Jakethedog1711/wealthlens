package com.wealthlens.api.valuation;

import java.math.BigDecimal;

/**
 * The few transaction fields valuation needs.
 * instrumentId and quantity are null for cash-only movements (deposit, withdrawal, fee).
 */
public record TransactionLine(TxnType type, Long instrumentId, BigDecimal quantity, BigDecimal amount) {
}
