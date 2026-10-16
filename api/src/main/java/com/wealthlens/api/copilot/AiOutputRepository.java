package com.wealthlens.api.copilot;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes the ai_output audit table (V4 migration). Every AI answer is stored, no exceptions. */
@Repository
public class AiOutputRepository {

    private final JdbcClient jdbc;

    public AiOutputRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record NewAiOutput(String feature, Long clientId, String inputFactsJson, String promptVersion,
                              String model, String outputText, Integer inputTokens, Integer outputTokens,
                              long latencyMs) {
    }

    /** One stored output, as the review screens need it. inputFactsJson is the raw JSON text. */
    public record AiOutputRecord(long id, String feature, Long clientId, String promptVersion, String model,
                                 String outputText, String reviewStatus, String reviewedBy,
                                 Instant reviewedAt, Instant createdAt, String inputFactsJson) {
    }

    private static final String SELECT = """
            SELECT id, feature, client_id, prompt_version, model, output_text, review_status,
                   reviewed_by, reviewed_at, created_at, input_facts::text AS input_facts
            FROM ai_output
            """;

    private static final RowMapper<AiOutputRecord> MAPPER = (rs, row) -> new AiOutputRecord(
            rs.getLong("id"),
            rs.getString("feature"),
            rs.getObject("client_id", Long.class),
            rs.getString("prompt_version"),
            rs.getString("model"),
            rs.getString("output_text"),
            rs.getString("review_status"),
            rs.getString("reviewed_by"),
            toInstant(rs.getTimestamp("reviewed_at")),
            toInstant(rs.getTimestamp("created_at")),
            rs.getString("input_facts"));

    private static Instant toInstant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    /** Saves one output and returns its id. review_status starts as PENDING (database default). */
    public long save(NewAiOutput o) {
        return jdbc.sql("""
                        INSERT INTO ai_output (feature, client_id, input_facts, prompt_version, model,
                                               output_text, input_tokens, output_tokens, latency_ms)
                        VALUES (:feature, :clientId, CAST(:facts AS jsonb), :promptVersion, :model,
                                :text, :inTokens, :outTokens, :latency)
                        RETURNING id
                        """)
                .param("feature", o.feature())
                .param("clientId", o.clientId())
                .param("facts", o.inputFactsJson())
                .param("promptVersion", o.promptVersion())
                .param("model", o.model())
                .param("text", o.outputText())
                .param("inTokens", o.inputTokens())
                .param("outTokens", o.outputTokens())
                .param("latency", (int) o.latencyMs())
                .query(Long.class)
                .single();
    }

    public Optional<AiOutputRecord> findById(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id").param("id", id).query(MAPPER).optional();
    }

    public List<AiOutputRecord> findByStatus(String status) {
        return jdbc.sql(SELECT + " WHERE review_status = :status ORDER BY created_at")
                .param("status", status).query(MAPPER).list();
    }

    /** Only APPROVED client letters: the only AI text a client is ever allowed to see. */
    public List<AiOutputRecord> approvedLetters(long clientId) {
        return jdbc.sql(SELECT + """
                         WHERE client_id = :clientId
                           AND feature = 'CLIENT_LETTER'
                           AND review_status = 'APPROVED'
                         ORDER BY reviewed_at DESC
                        """)
                .param("clientId", clientId).query(MAPPER).list();
    }

    /**
     * Records a decision, but only if the output is still PENDING. The WHERE clause makes this safe
     * even if two advisors click at the same moment: the second update changes 0 rows.
     * @return true if the decision was recorded
     */
    public boolean review(long id, ReviewDecision decision, String reviewer) {
        int changed = jdbc.sql("""
                        UPDATE ai_output
                        SET review_status = :decision, reviewed_by = :reviewer, reviewed_at = now()
                        WHERE id = :id AND review_status = 'PENDING'
                        """)
                .param("decision", decision.name())
                .param("reviewer", reviewer)
                .param("id", id)
                .update();
        return changed == 1;
    }
}
