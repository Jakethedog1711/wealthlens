package com.wealthlens.api.client;

/** One line in the client list. A record is a small, read-only data class. */
public record ClientSummary(Long id, String fullName, String email, RiskProfile riskProfile) {

    static ClientSummary from(Client c) {
        return new ClientSummary(c.getId(), c.getFirstName() + " " + c.getLastName(),
                c.getEmail(), c.getRiskProfile());
    }
}
