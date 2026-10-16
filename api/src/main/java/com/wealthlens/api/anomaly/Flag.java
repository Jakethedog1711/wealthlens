package com.wealthlens.api.anomaly;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** One anomaly found by the detector, before it is saved. */
public record Flag(AnomalyRule rule, long accountId, long clientId, LocalDate startDate, LocalDate endDate,
                   String reason, Map<String, BigDecimal> metrics, List<Evidence> evidence) {
}
