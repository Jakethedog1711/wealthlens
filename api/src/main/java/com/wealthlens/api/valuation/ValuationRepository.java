package com.wealthlens.api.valuation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries for valuation, written in plain SQL with Spring's JdbcClient.
 * Plain SQL suits reporting-style reads like "latest price per instrument" better than JPA.
 */
@Repository
public class ValuationRepository {

    private final JdbcClient jdbc;

    public ValuationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every transaction in all of the client's accounts, up to and including asOf. */
    public List<TransactionLine> transactionsForClient(Long clientId, LocalDate asOf) {
        return jdbc.sql("""
                        SELECT t.txn_type, t.instrument_id, t.quantity, t.amount
                        FROM transactions t
                        JOIN accounts a ON a.id = t.account_id
                        WHERE a.client_id = :clientId
                          AND t.trade_date <= :asOf
                        ORDER BY t.trade_date, t.id
                        """)
                .param("clientId", clientId)
                .param("asOf", asOf)
                .query((rs, row) -> new TransactionLine(
                        TxnType.valueOf(rs.getString("txn_type")),
                        rs.getObject("instrument_id", Long.class),     // null for cash movements
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("amount")))
                .list();
    }

    public Map<Long, InstrumentInfo> instruments() {
        return jdbc.sql("SELECT id, ticker, asset_class FROM instruments")
                .query((rs, row) -> new InstrumentInfo(
                        rs.getLong("id"),
                        rs.getString("ticker"),
                        AssetClass.valueOf(rs.getString("asset_class"))))
                .list().stream()
                .collect(Collectors.toMap(InstrumentInfo::id, i -> i));
    }

    /**
     * The most recent close price on or before asOf for each instrument.
     * DISTINCT ON is PostgreSQL's way of saying "keep only the first row per instrument",
     * and the ORDER BY makes that first row the latest date.
     */
    public Map<Long, BigDecimal> latestPrices(Collection<Long> instrumentIds, LocalDate asOf) {
        if (instrumentIds.isEmpty()) {
            return Map.of();                    // "IN ()" with an empty list is invalid SQL
        }
        return jdbc.sql("""
                        SELECT DISTINCT ON (instrument_id) instrument_id, close_price
                        FROM instrument_prices
                        WHERE instrument_id IN (:ids)
                          AND price_date <= :asOf
                        ORDER BY instrument_id, price_date DESC
                        """)
                .param("ids", instrumentIds)
                .param("asOf", asOf)
                .query((rs, row) -> Map.entry(rs.getLong("instrument_id"), rs.getBigDecimal("close_price")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** The last date we have prices for: the default valuation date. */
    public Optional<LocalDate> latestPriceDate() {
        return jdbc.sql("SELECT MAX(price_date) FROM instrument_prices")
                .query(LocalDate.class)
                .optional();
    }
}
