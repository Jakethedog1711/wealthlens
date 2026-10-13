package com.wealthlens.api.valuation;

import java.math.BigDecimal;

/** One instrument the client owns, valued at its latest price. */
public record HoldingValue(
        String ticker,
        AssetClass assetClass,
        BigDecimal units,
        BigDecimal price,
        BigDecimal marketValue,
        BigDecimal weightPct) {
}
