package com.wealthlens.api.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Values a portfolio from its transactions. Pure Java: no database, no Spring.
 * That makes it fast and easy to unit-test, and keeps the money maths in one place.
 *
 * Rules (transactions are the source of truth):
 *   units held = BUY quantities - SELL quantities
 *   cash       = DEPOSIT + SELL + DIVIDEND - BUY - WITHDRAWAL - FEE
 *   value      = units x latest price, plus cash
 */
public class PortfolioValuator {

    private static final int MONEY_SCALE = 2;      // cents
    private static final int PCT_SCALE = 2;        // e.g. 35.27 %
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /**
     * @param transactions every transaction up to the valuation date
     * @param instruments  instrument details by id
     * @param prices       latest close price by instrument id, on or before the valuation date
     */
    public Valuation value(List<TransactionLine> transactions,
                           Map<Long, InstrumentInfo> instruments,
                           Map<Long, BigDecimal> prices) {

        // 1. Replay the transactions to get units per instrument and the cash balance
        Map<Long, BigDecimal> units = new HashMap<>();
        BigDecimal cash = BigDecimal.ZERO;
        for (TransactionLine t : transactions) {
            switch (t.type()) {
                case BUY -> {
                    units.merge(t.instrumentId(), t.quantity(), BigDecimal::add);
                    cash = cash.subtract(t.amount());
                }
                case SELL -> {
                    units.merge(t.instrumentId(), t.quantity().negate(), BigDecimal::add);
                    cash = cash.add(t.amount());
                }
                case DEPOSIT, DIVIDEND -> cash = cash.add(t.amount());
                case WITHDRAWAL, FEE -> cash = cash.subtract(t.amount());
            }
        }

        // 2. Value each position that is still open
        List<HoldingValue> holdings = new ArrayList<>();
        Map<AssetClass, BigDecimal> byClass = new EnumMap<>(AssetClass.class);
        for (var entry : units.entrySet()) {
            BigDecimal held = entry.getValue();
            InstrumentInfo instrument = instruments.get(entry.getKey());
            if (held.signum() < 0) {
                throw new IllegalStateException("Negative units for " + instrument.ticker()
                        + ": more was sold than bought. The transaction data is inconsistent.");
            }
            if (held.signum() == 0) {
                continue;                                   // fully sold: nothing left to value
            }
            BigDecimal price = prices.get(entry.getKey());
            if (price == null) {
                throw new MissingPriceException(instrument.ticker());
            }
            BigDecimal marketValue = money(held.multiply(price));
            holdings.add(new HoldingValue(instrument.ticker(), instrument.assetClass(),
                    plainUnits(held), price, marketValue, null));
            byClass.merge(instrument.assetClass(), marketValue, BigDecimal::add);
        }
        cash = money(cash);
        if (cash.signum() != 0) {
            byClass.merge(AssetClass.CASH, cash, BigDecimal::add);
        }

        // 3. Totals and weights (each holding's share of the whole portfolio)
        BigDecimal total = byClass.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<HoldingValue> weighted = holdings.stream()
                .map(h -> new HoldingValue(h.ticker(), h.assetClass(), h.units(), h.price(),
                        h.marketValue(), pct(h.marketValue(), total)))
                .sorted(Comparator.comparing(HoldingValue::marketValue).reversed())
                .toList();

        List<AssetClassValue> classes = byClass.entrySet().stream()
                .map(e -> new AssetClassValue(e.getKey(), e.getValue(), pct(e.getValue(), total)))
                .toList();                                  // EnumMap keeps enum order

        return new Valuation(total, cash, weighted, classes);
    }

    /** 2.000000 -> 2 and 60.000000 -> 60 (not 6E+1, which is how BigDecimal shortens it). */
    private static BigDecimal plainUnits(BigDecimal units) {
        BigDecimal stripped = units.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(PCT_SCALE);
        }
        return part.multiply(HUNDRED).divide(total, PCT_SCALE, RoundingMode.HALF_UP);
    }
}
