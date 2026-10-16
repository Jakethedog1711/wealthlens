package com.wealthlens.api.copilot;

import com.wealthlens.api.drift.ClientDrift;
import com.wealthlens.api.drift.DriftRow;
import com.wealthlens.api.drift.DriftService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * "Java calculates, the LLM explains":
 *   1. DriftService calculates the numbers (deterministic, tested).
 *   2. AiGenerator sends those exact numbers to the LLM, checks the reply with NumberGuard,
 *      and saves everything to ai_output with review_status PENDING.
 */
@Service
public class DriftSummaryService {

    private final DriftService drift;
    private final AiGenerator ai;
    private final JsonMapper json;

    public DriftSummaryService(DriftService drift, AiGenerator ai, JsonMapper json) {
        this.drift = drift;
        this.ai = ai;
        this.json = json;
    }

    public DriftSummaryResponse summarise(Long clientId, LocalDate asOf) {
        ClientDrift facts = drift.driftFor(clientId, asOf);
        AiGenerator.Generated g = ai.generate("DRIFT_SUMMARY", clientId, Prompts.DRIFT_SUMMARY_SYSTEM,
                Prompts.DRIFT_SUMMARY_VERSION, json.writeValueAsString(facts), numbersIn(facts));

        return new DriftSummaryResponse(g.aiOutputId(), g.text(), g.unverifiedNumbers(), "PENDING", g.model(),
                g.promptVersion(), g.inputTokens(), g.outputTokens(), g.latencyMs(), facts);
    }

    /** Every number the LLM may legitimately mention: the drift figures, totals, tolerance and date parts. */
    static List<BigDecimal> numbersIn(ClientDrift facts) {
        List<BigDecimal> numbers = new ArrayList<>();
        numbers.add(facts.totalValue());
        numbers.add(BigDecimal.valueOf(facts.breaches()));
        numbers.add(BigDecimal.valueOf(facts.asOf().getYear()));
        numbers.add(BigDecimal.valueOf(facts.asOf().getMonthValue()));
        numbers.add(BigDecimal.valueOf(facts.asOf().getDayOfMonth()));
        for (DriftRow row : facts.assetClasses()) {
            numbers.add(row.marketValue());
            numbers.add(row.actualPct());
            numbers.add(row.targetPct());
            numbers.add(row.driftPct());
            numbers.add(row.tolerancePct());
        }
        return numbers;
    }
}
