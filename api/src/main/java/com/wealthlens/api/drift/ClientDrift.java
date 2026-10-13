package com.wealthlens.api.drift;

import com.wealthlens.api.client.RiskProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** JSON returned by GET /clients/{id}/drift. */
public record ClientDrift(
        Long clientId,
        RiskProfile riskProfile,
        LocalDate asOf,
        BigDecimal totalValue,
        boolean needsRebalance,
        int breaches,
        List<DriftRow> assetClasses) {
}
