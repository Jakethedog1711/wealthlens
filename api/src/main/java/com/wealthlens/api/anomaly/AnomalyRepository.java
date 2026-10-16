package com.wealthlens.api.anomaly;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AnomalyRepository {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public AnomalyRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Every deposit, withdrawal and buy, with the client's risk profile and the instrument's asset class. */
    public List<ScanTxn> loadScanTxns() {
        return jdbc.sql("""
                        SELECT t.account_id, a.client_id, c.risk_profile, t.txn_type, t.trade_date, t.amount,
                               i.ticker, i.asset_class
                        FROM transactions t
                        JOIN accounts a ON a.id = t.account_id
                        JOIN clients c ON c.id = a.client_id
                        LEFT JOIN instruments i ON i.id = t.instrument_id
                        WHERE t.txn_type IN ('DEPOSIT', 'WITHDRAWAL', 'BUY')
                        ORDER BY t.account_id, t.trade_date, t.id
                        """)
                .query((rs, row) -> new ScanTxn(
                        rs.getLong("account_id"),
                        rs.getLong("client_id"),
                        rs.getString("risk_profile"),
                        rs.getString("txn_type"),
                        rs.getObject("trade_date", LocalDate.class),
                        rs.getBigDecimal("amount"),
                        rs.getString("ticker"),
                        rs.getString("asset_class")))
                .list();
    }

    /** Insert, or update the existing flag for the same rule + account + start date (keeps its id). */
    public void upsert(Flag f) {
        jdbc.sql("""
                        INSERT INTO anomaly_flags (rule, client_id, account_id, start_date, end_date, reason,
                                                   metrics, evidence)
                        VALUES (:rule, :clientId, :accountId, :start, :end, :reason,
                                CAST(:metrics AS jsonb), CAST(:evidence AS jsonb))
                        ON CONFLICT (rule, account_id, start_date) DO UPDATE
                        SET end_date = EXCLUDED.end_date, reason = EXCLUDED.reason,
                            metrics = EXCLUDED.metrics, evidence = EXCLUDED.evidence, detected_at = now()
                        """)
                .param("rule", f.rule().name())
                .param("clientId", f.clientId())
                .param("accountId", f.accountId())
                .param("start", f.startDate())
                .param("end", f.endDate())
                .param("reason", f.reason())
                .param("metrics", json.writeValueAsString(f.metrics()))
                .param("evidence", json.writeValueAsString(f.evidence()))
                .update();
    }

    private static final String SELECT = """
            SELECT f.id, f.rule, f.client_id, c.first_name || ' ' || c.last_name AS client_name, c.risk_profile,
                   f.account_id, a.account_number, a.account_type, f.start_date, f.end_date, f.reason,
                   f.metrics::text AS metrics, f.evidence::text AS evidence, f.detected_at
            FROM anomaly_flags f
            JOIN clients c ON c.id = f.client_id
            JOIN accounts a ON a.id = f.account_id
            """;

    private RowMapper<StoredFlag> mapper() {
        return (rs, row) -> {
            Timestamp detected = rs.getTimestamp("detected_at");
            return new StoredFlag(
                    rs.getLong("id"),
                    AnomalyRule.valueOf(rs.getString("rule")),
                    rs.getLong("client_id"),
                    rs.getString("client_name"),
                    rs.getString("risk_profile"),
                    rs.getLong("account_id"),
                    rs.getString("account_number"),
                    rs.getString("account_type"),
                    rs.getObject("start_date", LocalDate.class),
                    rs.getObject("end_date", LocalDate.class),
                    rs.getString("reason"),
                    json.readTree(rs.getString("metrics")),
                    json.readTree(rs.getString("evidence")),
                    detected == null ? null : detected.toInstant());
        };
    }

    public List<StoredFlag> findAll() {
        return jdbc.sql(SELECT + " ORDER BY f.rule, f.start_date").query(mapper()).list();
    }

    public Optional<StoredFlag> findById(long id) {
        return jdbc.sql(SELECT + " WHERE f.id = :id").param("id", id).query(mapper()).optional();
    }
}
