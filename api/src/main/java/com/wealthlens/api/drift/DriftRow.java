package com.wealthlens.api.drift;

import com.wealthlens.api.valuation.AssetClass;
import java.math.BigDecimal;

/** How far one asset class is from its target. All percentages are of the whole portfolio. */
public record DriftRow(
        AssetClass assetClass,
        BigDecimal marketValue,
        BigDecimal actualPct,
        BigDecimal targetPct,
        BigDecimal driftPct,        // actualPct - targetPct, in percentage points
        BigDecimal tolerancePct,
        DriftStatus status) {
}
