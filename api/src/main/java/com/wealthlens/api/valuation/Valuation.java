package com.wealthlens.api.valuation;

import java.math.BigDecimal;
import java.util.List;

/** The result of valuing a portfolio. All amounts in ZAR. */
public record Valuation(
        BigDecimal totalValue,
        BigDecimal cash,
        List<HoldingValue> holdings,
        List<AssetClassValue> assetClasses) {
}
