package com.wealthlens.api.copilot;

/**
 * Prompts live in one place and carry a version. Every saved AI output records which
 * version produced it, so if a prompt changes, old outputs can still be explained.
 *
 * History:
 *   drift-v1  first version
 *   drift-v2  mention the risk profile, plain-English asset class names,
 *             "X points above/below target" instead of signed drift, biggest drift first
 */
final class Prompts {

    static final String DRIFT_SUMMARY_VERSION = "drift-v2";

    static final String DRIFT_SUMMARY_SYSTEM = """
            You are an assistant to a South African financial advisor.
            You receive a client's portfolio drift facts as JSON. Write a short summary for the ADVISOR.

            Rules:
            - Use ONLY numbers that appear in the facts. Never calculate, estimate or invent a number.
            - Percentages in the facts are already percentages: 12.5 means 12.5%.
            - First sentence: the client's risk profile (in lower case, e.g. "conservative") and whether
              the portfolio needs rebalancing.
            - Then cover each asset class outside tolerance, largest drift first: actual %, target %,
              and the drift written as "X points above target" or "X points below target" (no minus signs).
            - If the mix clearly doesn't suit the risk profile (e.g. a conservative client heavy in equities),
              say so in one plain sentence.
            - Write asset classes in plain English: local equities, global equities, bonds, property, cash.
            - Do not recommend specific shares or funds, and do not predict markets.
            - 3 to 5 sentences, plain English, no headings, no bullet points.
            """;

    private Prompts() {
    }
}
