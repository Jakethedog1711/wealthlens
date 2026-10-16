package com.wealthlens.api.copilot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guardrail: checks that every number in the AI's text exists in the facts we gave it.
 * The LLM is allowed to explain numbers, never to make them up. A number counts as known
 * if some fact rounds to it (so "12.3" matches a fact of 12.34, and "12" matches 12.4).
 */
final class NumberGuard {

    // 1,234,567.89 or 12.5 or 30 (commas allowed as thousands separators)
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private NumberGuard() {
    }

    /** Numbers in the text that no fact accounts for. Empty list = the text is grounded. */
    static List<String> unknownNumbers(String text, Collection<BigDecimal> facts) {
        List<String> unknown = new ArrayList<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String raw = m.group();
            BigDecimal n = new BigDecimal(raw.replace(",", ""));
            boolean known = facts.stream().anyMatch(f -> roundsTo(f, n));
            if (!known && !unknown.contains(raw)) {
                unknown.add(raw);
            }
        }
        return unknown;
    }

    private static boolean roundsTo(BigDecimal fact, BigDecimal n) {
        int scale = Math.max(n.scale(), 0);
        return fact.abs().setScale(scale, RoundingMode.HALF_UP).compareTo(n) == 0;
    }
}
