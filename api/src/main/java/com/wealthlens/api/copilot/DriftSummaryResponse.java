package com.wealthlens.api.copilot;

import com.wealthlens.api.drift.ClientDrift;
import java.util.List;

/**
 * What the API returns: the AI's text, the exact facts it was given, and audit details.
 * `unverifiedNumbers` lists any number in the text that isn't in the facts (should be empty).
 */
public record DriftSummaryResponse(
        Long aiOutputId,
        String summary,
        List<String> unverifiedNumbers,
        String reviewStatus,
        String model,
        String promptVersion,
        Integer inputTokens,
        Integer outputTokens,
        long latencyMs,
        ClientDrift facts) {
}
