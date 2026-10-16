package com.wealthlens.api.copilot;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Writes to the ai_output audit table (V4 migration). Every AI answer is stored, no exceptions. */
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
}
