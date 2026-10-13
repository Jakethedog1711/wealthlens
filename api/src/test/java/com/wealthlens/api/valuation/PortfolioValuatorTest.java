package com.wealthlens.api.valuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the valuation maths. No database and no Spring, so they run in milliseconds.
 * Each test follows Arrange (set up transactions) / Act (value them) / Assert (check the numbers).
 */
class PortfolioValuatorTest {

    private static final long NPN = 1L;
    private static final long STXGOV = 2L;

    private final PortfolioValuator valuator = new PortfolioValuator();

    private final Map<Long, InstrumentInfo> instruments = Map.of(
            NPN, new InstrumentInfo(NPN, "NPN", AssetClass.LOCAL_EQUITY),
            STXGOV, new InstrumentInfo(STXGOV, "STXGOV", AssetClass.BOND));

    // --- small helpers so each test reads like a statement ---------------------------------
    private static BigDecimal r(String value) { return new BigDecimal(value); }

    private static TransactionLine deposit(String amount) {
        return new TransactionLine(TxnType.DEPOSIT, null, null, r(amount));
    }
    private static TransactionLine withdrawal(String amount) {
        return new TransactionLine(TxnType.WITHDRAWAL, null, null, r(amount));
    }
    private static TransactionLine fee(String amount) {
        return new TransactionLine(TxnType.FEE, null, null, r(amount));
    }
    private static TransactionLine buy(long instrument, String units, String amount) {
        return new TransactionLine(TxnType.BUY, instrument, r(units), r(amount));
    }
    private static TransactionLine sell(long instrument, String units, String amount) {
        return new TransactionLine(TxnType.SELL, instrument, r(units), r(amount));
    }
    private static TransactionLine dividend(long instrument, String amount) {
        return new TransactionLine(TxnType.DIVIDEND, instrument, null, r(amount));
    }

    // --- tests ---------------------------------------------------------------------------
    @Test
    void emptyPortfolioIsWorthZero() {
        Valuation v = valuator.value(List.of(), instruments, Map.of());

        assertThat(v.totalValue()).isEqualByComparingTo("0");
        assertThat(v.holdings()).isEmpty();
        assertThat(v.assetClasses()).isEmpty();
    }

    @Test
    void depositOnlyIsAllCash() {
        Valuation v = valuator.value(List.of(deposit("10000")), instruments, Map.of());

        assertThat(v.cash()).isEqualByComparingTo("10000");
        assertThat(v.totalValue()).isEqualByComparingTo("10000");
        assertThat(v.assetClasses()).singleElement()
                .satisfies(c -> {
                    assertThat(c.assetClass()).isEqualTo(AssetClass.CASH);
                    assertThat(c.weightPct()).isEqualByComparingTo("100");
                });
    }

    @Test
    void holdingIsValuedAtLatestPriceNotPurchasePrice() {
        // Bought 2 NPN at R3 000 each; NPN is now R3 500
        var txns = List.of(deposit("10000"), buy(NPN, "2", "6000"));

        Valuation v = valuator.value(txns, instruments, Map.of(NPN, r("3500")));

        assertThat(v.cash()).isEqualByComparingTo("4000");          // 10 000 - 6 000
        assertThat(v.holdings()).singleElement().satisfies(h -> {
            assertThat(h.ticker()).isEqualTo("NPN");
            assertThat(h.units()).isEqualByComparingTo("2");
            assertThat(h.marketValue()).isEqualByComparingTo("7000"); // 2 x 3 500
        });
        assertThat(v.totalValue()).isEqualByComparingTo("11000");    // 7 000 + 4 000
    }

    @Test
    void sellingReducesUnitsAndAddsCash() {
        var txns = List.of(deposit("10000"), buy(NPN, "3", "9000"), sell(NPN, "1", "3200"));

        Valuation v = valuator.value(txns, instruments, Map.of(NPN, r("3200")));

        assertThat(v.cash()).isEqualByComparingTo("4200");          // 10 000 - 9 000 + 3 200
        assertThat(v.holdings()).singleElement()
                .satisfies(h -> assertThat(h.units()).isEqualByComparingTo("2"));
    }

    @Test
    void fullySoldPositionDisappears() {
        var txns = List.of(deposit("10000"), buy(NPN, "2", "6000"), sell(NPN, "2", "6400"));

        Valuation v = valuator.value(txns, instruments, Map.of());   // no price needed

        assertThat(v.holdings()).isEmpty();
        assertThat(v.totalValue()).isEqualByComparingTo("10400");
    }

    @Test
    void dividendsAddCashWhileFeesAndWithdrawalsRemoveIt() {
        var txns = List.of(deposit("10000"), dividend(NPN, "150"), fee("25.50"), withdrawal("1000"));

        Valuation v = valuator.value(txns, instruments, Map.of());

        assertThat(v.cash()).isEqualByComparingTo("9124.50");       // 10 000 + 150 - 25.50 - 1 000
    }

    @Test
    void groupsByAssetClassWithWeightsThatAddUpTo100() {
        // NPN worth 6 000, STXGOV worth 3 000, cash 1 000 -> 60% / 30% / 10%
        var txns = List.of(deposit("10000"), buy(NPN, "2", "6000"), buy(STXGOV, "60", "3000"));

        Valuation v = valuator.value(txns, instruments, Map.of(NPN, r("3000"), STXGOV, r("50")));

        assertThat(v.assetClasses())
                .extracting(AssetClassValue::assetClass, c -> c.weightPct().doubleValue())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(AssetClass.LOCAL_EQUITY, 60.0),
                        org.assertj.core.groups.Tuple.tuple(AssetClass.BOND, 30.0),
                        org.assertj.core.groups.Tuple.tuple(AssetClass.CASH, 10.0));
        assertThat(v.holdings()).extracting(HoldingValue::ticker)
                .containsExactly("NPN", "STXGOV");                    // biggest holding first
    }

    @Test
    void missingPriceForAHeldInstrumentIsAnError() {
        var txns = List.of(deposit("10000"), buy(NPN, "2", "6000"));

        assertThatThrownBy(() -> valuator.value(txns, instruments, Map.of()))
                .isInstanceOf(MissingPriceException.class)
                .hasMessageContaining("NPN");
    }

    @Test
    void sellingMoreThanWasBoughtIsAnError() {
        var txns = List.of(buy(NPN, "1", "3000"), sell(NPN, "2", "6000"));

        assertThatThrownBy(() -> valuator.value(txns, instruments, Map.of(NPN, r("3000"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Negative units");
    }
}
