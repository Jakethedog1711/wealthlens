package com.wealthlens.api.drift;

import com.wealthlens.api.valuation.AssetClass;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compares a portfolio's actual mix with its target mix. Pure Java, like PortfolioValuator.
 *
 *   actual %  = asset class value / total value x 100
 *   drift     = actual % - target %          (positive = overweight)
 *   breach    = |drift| > tolerance          (exactly on the tolerance is still OK)
 */
public class DriftCalculator {

    private static final int PCT_SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param valuesByClass market value per asset class (classes the client doesn't hold may be missing)
     * @param targets       target and tolerance per asset class for the client's risk profile
     */
    public DriftResult calculate(Map<AssetClass, BigDecimal> valuesByClass, Map<AssetClass, Target> targets) {
        BigDecimal total = valuesByClass.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            return new DriftResult(List.of(), 0, false);    // nothing invested: nothing to compare
        }

        List<DriftRow> rows = new ArrayList<>();
        int breaches = 0;
        // Walk every asset class in enum order, so a class the client holds none of
        // still shows up (as 0% actual, which is often the biggest drift of all)
        for (AssetClass assetClass : AssetClass.values()) {
            Target target = targets.get(assetClass);
            BigDecimal value = valuesByClass.getOrDefault(assetClass, BigDecimal.ZERO);
            if (target == null && value.signum() == 0) {
                continue;                                   // no target and nothing held
            }
            BigDecimal targetPct = target != null ? target.targetPct() : BigDecimal.ZERO;
            BigDecimal tolerance = target != null ? target.tolerancePct() : BigDecimal.ZERO;

            BigDecimal actualPct = value.multiply(HUNDRED).divide(total, PCT_SCALE, RoundingMode.HALF_UP);
            BigDecimal drift = actualPct.subtract(targetPct);
            DriftStatus status = statusFor(drift, tolerance);
            if (status != DriftStatus.WITHIN_TOLERANCE) {
                breaches++;
            }
            rows.add(new DriftRow(assetClass, value, actualPct, targetPct, drift, tolerance, status));
        }
        return new DriftResult(rows, breaches, breaches > 0);
    }

    private static DriftStatus statusFor(BigDecimal drift, BigDecimal tolerance) {
        if (drift.abs().compareTo(tolerance) <= 0) {
            return DriftStatus.WITHIN_TOLERANCE;
        }
        return drift.signum() > 0 ? DriftStatus.OVERWEIGHT : DriftStatus.UNDERWEIGHT;
    }
}
