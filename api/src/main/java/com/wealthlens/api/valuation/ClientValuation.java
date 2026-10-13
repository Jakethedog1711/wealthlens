package com.wealthlens.api.valuation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** JSON returned by GET /clients/{id}/valuation. */
public record ClientValuation(
        Long clientId,
        LocalDate asOf,
        String currency,
        BigDecimal totalValue,
        BigDecimal cash,
        List<AssetClassValue> assetClasses,
        List<HoldingValue> holdings) {

    static ClientValuation of(Long clientId, LocalDate asOf, Valuation v) {
        return new ClientValuation(clientId, asOf, "ZAR", v.totalValue(), v.cash(),
                v.assetClasses(), v.holdings());
    }
}
