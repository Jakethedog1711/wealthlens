package com.wealthlens.api.anomaly;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules engine. Pure Java, no database, no AI: every flag can be explained by a threshold below.
 * Each rule raises at most one flag per account per scan (the first match), which keeps the
 * compliance queue short and readable.
 */
public final class AnomalyDetector {

    // STRUCTURING: 3+ deposits of R45,000 to just under R50,000 within 10 days
    static final BigDecimal STRUCTURING_MIN = new BigDecimal("45000");
    static final BigDecimal STRUCTURING_MAX = new BigDecimal("50000");
    static final int STRUCTURING_COUNT = 3;
    static final int STRUCTURING_WINDOW_DAYS = 10;

    // RAPID_IN_OUT: a deposit of R100,000+ with 90%+ of it withdrawn within 5 days
    static final BigDecimal RAPID_MIN_DEPOSIT = new BigDecimal("100000");
    static final BigDecimal RAPID_MIN_OUT_PCT = new BigDecimal("90");
    static final int RAPID_WINDOW_DAYS = 5;

    // DORMANT_THEN_SPIKE: 180+ days with no deposits or withdrawals, then a withdrawal of
    // at least 50% of everything deposited so far
    static final int DORMANT_DAYS = 180;
    static final BigDecimal DORMANT_MIN_PCT = new BigDecimal("50");

    // PROFILE_MISMATCH: a CONSERVATIVE client buys one share with 75%+ of that day's deposits
    static final BigDecimal MISMATCH_MIN_PCT = new BigDecimal("75");
    private static final Set<String> EQUITY = Set.of("LOCAL_EQUITY", "GLOBAL_EQUITY");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private AnomalyDetector() {
    }

    /** Runs every rule over the transactions of ONE account (any order). */
    public static List<Flag> detect(List<ScanTxn> accountTxns) {
        List<ScanTxn> txns = new ArrayList<>(accountTxns);
        txns.sort(Comparator.comparing(ScanTxn::date));   // stable: keeps database order within a day

        List<Flag> flags = new ArrayList<>();
        structuring(txns, flags);
        rapidInOut(txns, flags);
        dormantThenSpike(txns, flags);
        profileMismatch(txns, flags);
        return flags;
    }

    static void structuring(List<ScanTxn> txns, List<Flag> flags) {
        List<ScanTxn> near = txns.stream()
                .filter(t -> t.type().equals("DEPOSIT"))
                .filter(t -> t.amount().compareTo(STRUCTURING_MIN) >= 0 && t.amount().compareTo(STRUCTURING_MAX) < 0)
                .toList();
        for (int i = 0; i < near.size(); i++) {
            ScanTxn first = near.get(i);
            List<ScanTxn> window = near.subList(i, near.size()).stream()
                    .filter(t -> days(first, t) <= STRUCTURING_WINDOW_DAYS)
                    .toList();
            if (window.size() >= STRUCTURING_COUNT) {
                ScanTxn last = window.get(window.size() - 1);
                BigDecimal total = window.stream().map(ScanTxn::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                long span = days(first, last);
                Map<String, BigDecimal> metrics = new LinkedHashMap<>();
                metrics.put("depositCount", BigDecimal.valueOf(window.size()));
                metrics.put("totalDeposited", total);
                metrics.put("daysBetweenFirstAndLast", BigDecimal.valueOf(span));
                metrics.put("ruleMinAmount", STRUCTURING_MIN);
                metrics.put("ruleMaxAmount", STRUCTURING_MAX);
                flags.add(flag(AnomalyRule.STRUCTURING, first, last,
                        window.size() + " deposits of between R45,000 and R50,000 within " + span + " days",
                        metrics, window));
                return;
            }
        }
    }

    static void rapidInOut(List<ScanTxn> txns, List<Flag> flags) {
        for (ScanTxn in : txns) {
            if (!in.type().equals("DEPOSIT") || in.amount().compareTo(RAPID_MIN_DEPOSIT) < 0) {
                continue;
            }
            for (ScanTxn out : txns) {
                long gap = days(in, out);
                if (out.type().equals("WITHDRAWAL") && gap > 0 && gap <= RAPID_WINDOW_DAYS
                        && pct(out.amount(), in.amount()).compareTo(RAPID_MIN_OUT_PCT) >= 0) {
                    Map<String, BigDecimal> metrics = new LinkedHashMap<>();
                    metrics.put("depositAmount", in.amount());
                    metrics.put("withdrawnAmount", out.amount());
                    metrics.put("withdrawnPctOfDeposit", pct(out.amount(), in.amount()));
                    metrics.put("daysBetween", BigDecimal.valueOf(gap));
                    flags.add(flag(AnomalyRule.RAPID_IN_OUT, in, out,
                            pct(out.amount(), in.amount()) + "% of a large deposit was withdrawn " + gap
                                    + " days later",
                            metrics, List.of(in, out)));
                    return;
                }
            }
        }
    }

    static void dormantThenSpike(List<ScanTxn> txns, List<Flag> flags) {
        List<ScanTxn> cashFlows = txns.stream()
                .filter(t -> t.type().equals("DEPOSIT") || t.type().equals("WITHDRAWAL"))
                .toList();
        BigDecimal depositedSoFar = BigDecimal.ZERO;
        for (int i = 0; i < cashFlows.size(); i++) {
            ScanTxn t = cashFlows.get(i);
            if (i > 0 && t.type().equals("WITHDRAWAL") && depositedSoFar.signum() > 0) {
                ScanTxn previous = cashFlows.get(i - 1);
                long quiet = days(previous, t);
                BigDecimal share = pct(t.amount(), depositedSoFar);
                if (quiet >= DORMANT_DAYS && share.compareTo(DORMANT_MIN_PCT) >= 0) {
                    Map<String, BigDecimal> metrics = new LinkedHashMap<>();
                    metrics.put("quietDays", BigDecimal.valueOf(quiet));
                    metrics.put("withdrawnAmount", t.amount());
                    metrics.put("totalDepositedBefore", depositedSoFar);
                    metrics.put("withdrawnPctOfDeposits", share);
                    flags.add(flag(AnomalyRule.DORMANT_THEN_SPIKE, previous, t,
                            "After " + quiet + " quiet days, " + share + "% of all deposits was withdrawn",
                            metrics, List.of(previous, t)));
                    return;
                }
            }
            if (t.type().equals("DEPOSIT")) {
                depositedSoFar = depositedSoFar.add(t.amount());
            }
        }
    }

    static void profileMismatch(List<ScanTxn> txns, List<Flag> flags) {
        for (ScanTxn buy : txns) {
            if (!buy.type().equals("BUY") || !"CONSERVATIVE".equals(buy.riskProfile())
                    || !EQUITY.contains(buy.assetClass())) {
                continue;
            }
            List<ScanTxn> sameDayDeposits = txns.stream()
                    .filter(t -> t.type().equals("DEPOSIT") && t.date().equals(buy.date()))
                    .toList();
            BigDecimal deposited = sameDayDeposits.stream().map(ScanTxn::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (deposited.signum() > 0 && pct(buy.amount(), deposited).compareTo(MISMATCH_MIN_PCT) >= 0) {
                Map<String, BigDecimal> metrics = new LinkedHashMap<>();
                metrics.put("buyAmount", buy.amount());
                metrics.put("sameDayDeposits", deposited);
                metrics.put("buyPctOfDeposits", pct(buy.amount(), deposited));
                List<ScanTxn> evidence = new ArrayList<>(sameDayDeposits);
                evidence.add(buy);
                flags.add(flag(AnomalyRule.PROFILE_MISMATCH, buy, buy,
                        "A conservative client put " + pct(buy.amount(), deposited) + "% of a deposit into one share ("
                                + buy.ticker() + ")",
                        metrics, evidence));
                return;
            }
        }
    }

    private static Flag flag(AnomalyRule rule, ScanTxn start, ScanTxn end, String reason,
                             Map<String, BigDecimal> metrics, List<ScanTxn> evidence) {
        return new Flag(rule, start.accountId(), start.clientId(), start.date(), end.date(), reason,
                metrics, evidence.stream().map(Evidence::of).toList());
    }

    private static long days(ScanTxn from, ScanTxn to) {
        return ChronoUnit.DAYS.between(from.date(), to.date());
    }

    /** part as a percentage of whole, 2 decimals. */
    static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        return part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
