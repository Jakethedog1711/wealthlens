package com.wealthlens.api.copilot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The guardrail that stops the LLM from slipping invented numbers into a summary. */
class NumberGuardTest {

    private static final List<BigDecimal> FACTS = List.of(
            new BigDecimal("12.34"), new BigDecimal("35"), new BigDecimal("-22.66"), new BigDecimal("1234567.89"));

    @Test
    void textUsingOnlyFactNumbersPasses() {
        String text = "Local equity is at 12.34% against a 35% target, a drift of 22.66 points.";
        assertThat(NumberGuard.unknownNumbers(text, FACTS)).isEmpty();
    }

    @Test
    void roundedVersionsOfFactsAreAllowed() {
        assertThat(NumberGuard.unknownNumbers("About 12.3%, roughly 12%, drift 23 points.", FACTS)).isEmpty();
    }

    @Test
    void thousandsSeparatorsAreUnderstood() {
        assertThat(NumberGuard.unknownNumbers("The portfolio is worth R1,234,567.89.", FACTS)).isEmpty();
    }

    @Test
    void anInventedNumberIsCaught() {
        assertThat(NumberGuard.unknownNumbers("Equity should be cut by 7.5% to reach 35%.", FACTS))
                .containsExactly("7.5");
    }

    @Test
    void negativeFactsMatchTheirSizeInText() {
        // the text says "22.66 points underweight", the fact is stored as -22.66
        assertThat(NumberGuard.unknownNumbers("22.66 points underweight", FACTS)).isEmpty();
    }
}
