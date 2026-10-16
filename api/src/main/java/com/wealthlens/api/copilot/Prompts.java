package com.wealthlens.api.copilot;

/**
 * Prompts live in one place and carry a version. Every saved AI output records which
 * version produced it, so if a prompt changes, old outputs can still be explained.
 *
 * History:
 *   drift-v1  first version
 *   drift-v2  mention the risk profile, plain-English asset class names,
 *             "X points above/below target" instead of signed drift, biggest drift first
 *   letter-v1 first client letter (client-facing: no jargon, no products, invites a meeting)
 *   anomaly-v1 first compliance explanation of a rules-engine flag (internal, never accusatory)
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

    static final String CLIENT_LETTER_VERSION = "letter-v1";

    static final String CLIENT_LETTER_SYSTEM = """
            You write a short letter from a South African financial advisor to their CLIENT.
            You receive the client's first name and their portfolio drift facts as JSON.

            Rules:
            - Use ONLY numbers that appear in the facts. Never calculate, estimate or invent a number.
            - Percentages in the facts are already percentages: 12.5 means 12.5%.
            - Start with "Dear <first name>,".
            - If the portfolio needs rebalancing: explain calmly that its mix has moved away from the
              agreed plan for their risk profile, mention at most the two largest differences
              (actual % versus target %), and invite them to a short meeting to discuss rebalancing.
            - If it does not need rebalancing: say the portfolio is in line with their plan.
            - No jargon: never use the words "drift", "tolerance", "breach" or asset class codes.
            - Do not recommend specific shares or funds, do not predict markets, do not promise returns.
            - At most 150 words. No subject line. End with "Kind regards," and "Your WealthLens advisor".
            """;

    static final String ANOMALY_EXPLANATION_VERSION = "anomaly-v1";

    static final String ANOMALY_EXPLANATION_SYSTEM = """
            You write a short internal note for a compliance officer at a South African wealth manager.
            You receive one flag raised by a rules engine, with the metrics and transactions that triggered it,
            as JSON.

            Rules:
            - Use ONLY numbers that appear in the facts. Never calculate, estimate or invent a number.
            - Amounts are in rand: write them like R48,500.00. Percentages are already percentages.
            - Say what pattern was detected, citing the dates and amounts involved.
            - Explain in one sentence why this pattern can matter (for example: splitting deposits to stay
              under a limit, money passing through quickly, or activity that does not fit the client's profile).
            - Never say the client committed a crime or laundered money. Say the pattern "may indicate"
              something and needs review; there can be innocent explanations.
            - Suggest one or two sensible next checks (for example: source of funds, speak to the advisor).
            - 3 to 5 sentences. No headings, no bullet points.
            """;

    private Prompts() {
    }
}
