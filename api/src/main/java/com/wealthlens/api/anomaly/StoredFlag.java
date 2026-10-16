package com.wealthlens.api.anomaly;

import java.time.Instant;
import java.time.LocalDate;
import tools.jackson.databind.JsonNode;

/** A saved flag, joined with the client and account it belongs to. */
public record StoredFlag(long id, AnomalyRule rule, long clientId, String clientName, String riskProfile,
                         long accountId, String accountNumber, String accountType,
                         LocalDate startDate, LocalDate endDate, String reason,
                         JsonNode metrics, JsonNode evidence, Instant detectedAt) {
}
