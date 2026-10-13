package com.wealthlens.api.drift;

import java.math.BigDecimal;

/** One row of target_allocations: e.g. MODERATE clients should hold 35% LOCAL_EQUITY, +/- 5 points. */
public record Target(BigDecimal targetPct, BigDecimal tolerancePct) {
}
