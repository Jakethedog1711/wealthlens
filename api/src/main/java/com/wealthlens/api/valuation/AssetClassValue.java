package com.wealthlens.api.valuation;

import java.math.BigDecimal;

/** Total value per asset class: the numbers drift will compare against the targets. */
public record AssetClassValue(AssetClass assetClass, BigDecimal marketValue, BigDecimal weightPct) {
}
