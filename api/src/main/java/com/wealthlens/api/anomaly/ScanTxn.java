package com.wealthlens.api.anomaly;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One transaction as the detector sees it. ticker and assetClass are null for cash movements.
 * type is the transactions.txn_type value: DEPOSIT, WITHDRAWAL or BUY (the only ones the rules need).
 */
public record ScanTxn(long accountId, long clientId, String riskProfile, String type, LocalDate date,
                      BigDecimal amount, String ticker, String assetClass) {
}
