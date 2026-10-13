package com.wealthlens.api.drift;

import static org.assertj.core.api.Assertions.assertThat;

import com.wealthlens.api.valuation.AssetClass;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for drift. Targets mirror the MODERATE profile in target_allocations. */
class DriftCalculatorTest {

    private final DriftCalculator calculator = new DriftCalculator();

    private static final Map<AssetClass, Target> MODERATE = Map.of(
            AssetClass.LOCAL_EQUITY, target("35"),
            AssetClass.GLOBAL_EQUITY, target("20"),
            AssetClass.BOND, target("30"),
            AssetClass.PROPERTY, target("5"),
            AssetClass.CASH, target("10"));

    private static Target target(String pct) {
        return new Target(new BigDecimal(pct), new BigDecimal("5.00"));
    }

    private static BigDecimal r(String value) { return new BigDecimal(value); }

    private static DriftRow row(DriftResult result, AssetClass assetClass) {
        return result.rows().stream().filter(r -> r.assetClass() == assetClass).findFirst().orElseThrow();
    }

    @Test
    void portfolioExactlyOnTargetHasNoDrift() {
        var values = Map.of(
                AssetClass.LOCAL_EQUITY, r("35000"), AssetClass.GLOBAL_EQUITY, r("20000"),
                AssetClass.BOND, r("30000"), AssetClass.PROPERTY, r("5000"), AssetClass.CASH, r("10000"));

        DriftResult result = calculator.calculate(values, MODERATE);

        assertThat(result.needsRebalance()).isFalse();
        assertThat(result.breaches()).isZero();
        assertThat(result.rows()).hasSize(5)
                .allSatisfy(row -> assertThat(row.driftPct()).isEqualByComparingTo("0"));
    }

    @Test
    void tooMuchEquityIsOverweightAndMissingBondsAreUnderweight() {
        // 70% local equity, 30% cash, nothing else
        var values = Map.of(AssetClass.LOCAL_EQUITY, r("70000"), AssetClass.CASH, r("30000"));

        DriftResult result = calculator.calculate(values, MODERATE);

        DriftRow equity = row(result, AssetClass.LOCAL_EQUITY);
        assertThat(equity.actualPct()).isEqualByComparingTo("70");
        assertThat(equity.driftPct()).isEqualByComparingTo("35");          // 70 - 35
        assertThat(equity.status()).isEqualTo(DriftStatus.OVERWEIGHT);

        DriftRow bonds = row(result, AssetClass.BOND);                     // held none at all
        assertThat(bonds.actualPct()).isEqualByComparingTo("0");
        assertThat(bonds.driftPct()).isEqualByComparingTo("-30");
        assertThat(bonds.status()).isEqualTo(DriftStatus.UNDERWEIGHT);

        assertThat(result.needsRebalance()).isTrue();
    }

    @Test
    void driftExactlyOnTheToleranceIsStillWithinTolerance() {
        // Local equity at 40% = target 35% + exactly 5 points
        var values = Map.of(
                AssetClass.LOCAL_EQUITY, r("40000"), AssetClass.GLOBAL_EQUITY, r("15000"),
                AssetClass.BOND, r("30000"), AssetClass.PROPERTY, r("5000"), AssetClass.CASH, r("10000"));

        DriftResult result = calculator.calculate(values, MODERATE);

        assertThat(row(result, AssetClass.LOCAL_EQUITY).status()).isEqualTo(DriftStatus.WITHIN_TOLERANCE);
        assertThat(row(result, AssetClass.GLOBAL_EQUITY).status()).isEqualTo(DriftStatus.WITHIN_TOLERANCE);
        assertThat(result.needsRebalance()).isFalse();
    }

    @Test
    void justOverTheToleranceIsABreach() {
        // Local equity at 40.01%: 0.01 points past the 5-point tolerance
        var values = Map.of(
                AssetClass.LOCAL_EQUITY, r("40010"), AssetClass.GLOBAL_EQUITY, r("19990"),
                AssetClass.BOND, r("30000"), AssetClass.PROPERTY, r("5000"), AssetClass.CASH, r("5000"));

        DriftResult result = calculator.calculate(values, MODERATE);

        assertThat(row(result, AssetClass.LOCAL_EQUITY).status()).isEqualTo(DriftStatus.OVERWEIGHT);
        assertThat(result.breaches()).isEqualTo(1);
    }

    @Test
    void emptyPortfolioHasNothingToRebalance() {
        DriftResult result = calculator.calculate(Map.of(), MODERATE);

        assertThat(result.rows()).isEmpty();
        assertThat(result.needsRebalance()).isFalse();
    }
}
