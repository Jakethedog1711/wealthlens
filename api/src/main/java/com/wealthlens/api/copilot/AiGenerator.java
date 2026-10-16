package com.wealthlens.api.copilot;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The one place that calls the LLM. Every AI feature goes through here, so every feature
 * automatically gets the same guardrail and the same audit record. No feature can "forget".
 */
@Component
public class AiGenerator {

    private final ChatClient chat;
    private final AiOutputRepository aiOutputs;
    private final String model;

    public AiGenerator(ChatClient.Builder chatBuilder, AiOutputRepository aiOutputs,
                       @Value("${spring.ai.bedrock.converse.chat.model}") String model) {
        this.chat = chatBuilder.build();
        this.aiOutputs = aiOutputs;
        this.model = model;
    }

    public record Generated(long aiOutputId, String text, List<String> unverifiedNumbers, String model,
                            String promptVersion, Integer inputTokens, Integer outputTokens, long latencyMs) {
    }

    /**
     * @param factsJson      the exact facts sent to the model (also stored for audit)
     * @param allowedNumbers every number the model may mention (for the guardrail)
     */
    public Generated generate(String feature, Long clientId, String systemPrompt, String promptVersion,
                              String factsJson, Collection<BigDecimal> allowedNumbers) {
        long start = System.currentTimeMillis();
        ChatResponse response = chat.prompt()
                .system(systemPrompt)
                .user(factsJson)
                .call()
                .chatResponse();
        long latencyMs = System.currentTimeMillis() - start;

        String text = response.getResult().getOutput().getText().strip();
        Usage usage = response.getMetadata().getUsage();
        Integer inTokens = usage == null ? null : usage.getPromptTokens();
        Integer outTokens = usage == null ? null : usage.getCompletionTokens();

        List<String> unverified = NumberGuard.unknownNumbers(text, allowedNumbers);

        long id = aiOutputs.save(new AiOutputRepository.NewAiOutput(
                feature, clientId, factsJson, promptVersion, model, text, inTokens, outTokens, latencyMs));

        return new Generated(id, text, unverified, model, promptVersion, inTokens, outTokens, latencyMs);
    }
}
