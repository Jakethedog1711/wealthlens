package com.wealthlens.api.copilot;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Smoke test for the AI connection: GET /copilot/ping asks the model one fixed question.
 * If this works, Java -> Spring AI -> Bedrock -> model is wired up correctly.
 */
@RestController
public class CopilotPingController {

    private final ChatClient chat;
    private final String model;

    // Spring AI builds a ChatClient.Builder for us from application.properties
    public CopilotPingController(ChatClient.Builder builder,
                                 @Value("${spring.ai.bedrock.converse.chat.model}") String model) {
        this.chat = builder.build();
        this.model = model;
    }

    @GetMapping("/copilot/ping")
    public Map<String, Object> ping() {
        long start = System.currentTimeMillis();
        String answer = chat.prompt()
                .user("In one sentence, what does 'portfolio drift' mean for a wealth manager?")
                .call()
                .content();
        return Map.of(
                "model", model,
                "answer", answer,
                "latencyMs", System.currentTimeMillis() - start);
    }
}
