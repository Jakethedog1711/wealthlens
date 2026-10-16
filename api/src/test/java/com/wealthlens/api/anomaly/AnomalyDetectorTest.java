package com.wealthlens.api.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Each rule gets a "should flag" case and a near-miss "should NOT flag" case. */
class AnomalyDetectorTest {

    private static final LocalDate D = LocalDate.of(2026, 3, 2);

    private static ScanTxn txn(String risk, String type, int day, String amount) {
        return new ScanTxn(1, 1, risk, type, D.plusDays(day), new BigDecimal(amount), null, null);
    }

    private static ScanTxn deposit(int day, String amount) {
        return txn("MODERATE", "DEPOSIT", day, amount);
    }

    private static ScanTxn withdrawal(int day, String amount) {
        return txn("MODERATE", "WITHDRAWAL", day, amount);
    }

    private static ScanTxn buy(String risk, int day, String amount, String ticker, String assetClass) {
        return new ScanTxn(1, 1, risk, "BUY", D.plusDays(day), new BigDecimal(amount), ticker, assetClass);
    }

    private static List<AnomalyRule> rules(List<ScanTxn> txns) {
        return AnomalyDetector.detect(txns).stream().map(Flag::rule).toList();
    }

    @Test
    void ordinaryInvestingRaisesNothing() {
        List<ScanTxn> txns = new ArrayList<>(List.of(
                deposit(0, "250000"),
                buy("CONSERVATIVE", 0, "25000", "NPN", "LOCAL_EQUITY"),
                buy("CONSERVATIVE", 0, "110000", "GOVI", "BOND")));
        for (int month = 1; month <= 11; month++) {
            txns.add(deposit(month * 30, "5000"));
        }
        txns.add(withdrawal(200, "30000"));
        assertThat(rules(txns)).isEmpty();
    }

    @Test
    void threeDepositsJustUnderFiftyThousandIsStructuring() {
        List<Flag> flags = AnomalyDetector.detect(List.of(
                deposit(0, "48500"), deposit(1, "49900"), deposit(3, "46200")));
        assertThat(flags).hasSize(1);
        Flag f = flags.get(0);
        assertThat(f.rule()).isEqualTo(AnomalyRule.STRUCTURING);
        assertThat(f.metrics().get("depositCount")).isEqualByComparingTo("3");
        assertThat(f.metrics().get("totalDeposited")).isEqualByComparingTo("144600");
        assertThat(f.startDate()).isEqualTo(D);
        assertThat(f.endDate()).isEqualTo(D.plusDays(3));
        assertThat(f.evidence()).hasSize(3);
    }

    @Test
    void sameDepositsSpreadOverAMonthAreNotStructuring() {
        assertThat(rules(List.of(deposit(0, "48500"), deposit(15, "49900"), deposit(30, "46200")))).isEmpty();
    }

    @Test
    void depositsOfExactlyFiftyThousandAreNotStructuring() {
        assertThat(rules(List.of(deposit(0, "50000"), deposit(1, "50000"), deposit(2, "50000")))).isEmpty();
    }

    @Test
    void largeDepositWithdrawnWithinDaysIsRapidInOut() {
        List<Flag> flags = AnomalyDetector.detect(List.of(deposit(0, "600000"), withdrawal(2, "590000")));
        assertThat(flags).extracting(Flag::rule).containsExactly(AnomalyRule.RAPID_IN_OUT);
        assertThat(flags.get(0).metrics().get("withdrawnPctOfDeposit")).isEqualByComparingTo("98.33");
        assertThat(flags.get(0).metrics().get("daysBetween")).isEqualByComparingTo("2");
    }

    @Test
    void partialWithdrawalOrSlowWithdrawalIsNotRapidInOut() {
        assertThat(rules(List.of(deposit(0, "600000"), withdrawal(2, "300000")))).isEmpty();
        assertThat(rules(List.of(deposit(0, "600000"), withdrawal(10, "600000")))).isEmpty();
    }

    @Test
    void quietAccountSuddenlyEmptiedIsDormantThenSpike() {
        List<Flag> flags = AnomalyDetector.detect(List.of(deposit(0, "300000"), withdrawal(220, "310000")));
        assertThat(flags).extracting(Flag::rule).containsExactly(AnomalyRule.DORMANT_THEN_SPIKE);
        assertThat(flags.get(0).metrics().get("quietDays")).isEqualByComparingTo("220");
    }

    @Test
    void smallWithdrawalAfterQuietPeriodIsNotDormantThenSpike() {
        assertThat(rules(List.of(deposit(0, "300000"), withdrawal(220, "30000")))).isEmpty();
    }

    @Test
    void conservativeClientPuttingDepositIntoOneShareIsProfileMismatch() {
        List<Flag> flags = AnomalyDetector.detect(List.of(
                txn("CONSERVATIVE", "DEPOSIT", 40, "400000"),
                buy("CONSERVATIVE", 40, "398000", "MTN", "LOCAL_EQUITY")));
        assertThat(flags).extracting(Flag::rule).containsExactly(AnomalyRule.PROFILE_MISMATCH);
        assertThat(flags.get(0).reason()).contains("MTN");
    }

    @Test
    void aggressiveClientDoingTheSameIsNotProfileMismatch() {
        assertThat(rules(List.of(
                txn("AGGRESSIVE", "DEPOSIT", 40, "400000"),
                buy("AGGRESSIVE", 40, "398000", "MTN", "LOCAL_EQUITY")))).isEmpty();
    }

    @Test
    void conservativeClientBuyingBondsIsNotProfileMismatch() {
        assertThat(rules(List.of(
                txn("CONSERVATIVE", "DEPOSIT", 40, "400000"),
                buy("CONSERVATIVE", 40, "398000", "GOVI", "BOND")))).isEmpty();
    }
}
