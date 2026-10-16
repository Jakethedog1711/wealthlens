package com.wealthlens.api.copilot;

import com.wealthlens.api.drift.ClientDrift;
import com.wealthlens.api.drift.DriftRow;
import com.wealthlens.api.drift.DriftService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * "Java calculates, the LLM explains":
 *   1. DriftService calculates the numbers (deterministic, tested).
 *   2. Those exact numbers go to the LLM as JSON, with strict rules in the system prompt.
 *   3. NumberGuard checks the reply only uses numbers from the facts.
 *   4. Everything (facts, prompt version, model, text, tokens, timing) is saved to ai_output,
 *      with review_status PENDING: an advisor approves it before any client sees it.
 */
@Service
public class DriftSummaryService {

    private final DriftService drift;
    private final ChatClient chat;
    private final AiOutputRepository aiOutputs;
    private final JsonMapper json;
    private final String model;

    public DriftSummaryService(DriftService drift, ChatClient.Builder chatBuilder, AiOutputRepository aiOutputs,
                               JsonMapper json, @Value("${spring.ai.bedrock.converse.chat.model}") String model) {
        this.drift = drift;
        this.chat = chatBuilder.build();
        this.aiOutputs = aiOutputs;
        this.json = json;
        this.model = model;
    }

    public DriftSummaryResponse summarise(Long clientId, LocalDate asOf) {
        // 1. Facts, calculated by Java
        ClientDrift facts = drift.driftFor(clientId, asOf);
        String factsJson = json.writeValueAsString(facts);

        // 2. Ask the LLM to explain them
        long start = System.currentTimeMillis();
        ChatResponse response = chat.prompt()
                .system(Prompts.DRIFT_SUMMARY_SYSTEM)
                .user(factsJson)
                .call()
                .chatResponse();
        long latencyMs = System.currentTimeMillis() - start;

        String text = response.getResult().getOutput().getText().strip();
        Usage usage = response.getMetadata().getUsage();
        Integer inTokens = usage == null ? null : usage.getPromptTokens();
        Integer outTokens = usage == null ? null : usage.getCompletionTokens();

        // 3. Guardrail: every number in the text must come from the facts
        List<String> unverified = NumberGuard.unknownNumbers(text, numbersIn(facts));

        // 4. Audit trail
        long id = aiOutputs.save(new AiOutputRepository.NewAiOutput(
                "DRIFT_SUMMARY", clientId, factsJson, Prompts.DRIFT_SUMMARY_VERSION, model,
                text, inTokens, outTokens, latencyMs));

        return new DriftSummaryResponse(id, text, unverified, "PENDING", model,
                Prompts.DRIFT_SUMMARY_VERSION, inTokens, outTokens, latencyMs, facts);
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
