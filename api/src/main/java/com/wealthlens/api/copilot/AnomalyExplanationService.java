package com.wealthlens.api.copilot;

import com.wealthlens.api.anomaly.AnomalyService;
import com.wealthlens.api.anomaly.StoredFlag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The rules engine decides WHAT is suspicious; the LLM only writes the explanation for a human.
 * The guardrail here is strict: every number in the note must appear somewhere in the facts JSON.
 */
@Service
public class AnomalyExplanationService {

    private final AnomalyService anomalies;
    private final AiGenerator ai;
    private final JsonMapper json;

    public AnomalyExplanationService(AnomalyService anomalies, AiGenerator ai, JsonMapper json) {
        this.anomalies = anomalies;
        this.ai = ai;
        this.json = json;
    }

    /** Exactly what the LLM sees. No names or account numbers: it doesn't need them to explain a pattern. */
    public record AnomalyFacts(String rule, String riskProfile, String accountType, LocalDate startDate,
                               LocalDate endDate, String reason, JsonNode metrics, JsonNode evidence) {
    }

    public record AnomalyExplanation(long aiOutputId, long anomalyId, String explanation,
                                     List<String> unverifiedNumbers, String reviewStatus, String promptVersion,
                                     AnomalyFacts facts) {
    }

    public AnomalyExplanation explain(long anomalyId) {
        StoredFlag f = anomalies.get(anomalyId);
        AnomalyFacts facts = new AnomalyFacts(f.rule().name(), f.riskProfile(), f.accountType(), f.startDate(),
                f.endDate(), f.reason(), f.metrics(), f.evidence());
        String factsJson = json.writeValueAsString(facts);

        AiGenerator.Generated g = ai.generate("ANOMALY_EXPLANATION", f.clientId(),
                Prompts.ANOMALY_EXPLANATION_SYSTEM, Prompts.ANOMALY_EXPLANATION_VERSION,
                factsJson, NumberGuard.numbersInText(factsJson));

        return new AnomalyExplanation(g.aiOutputId(), anomalyId, g.text(), g.unverifiedNumbers(), "PENDING",
                g.promptVersion(), facts);
    }
}
