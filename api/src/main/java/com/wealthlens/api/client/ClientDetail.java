package com.wealthlens.api.client;

import com.wealthlens.api.account.AccountSummary;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Everything about one client, including their accounts. */
public record ClientDetail(
        Long id,
        String firstName,
        String lastName,
        String email,
        LocalDate dateOfBirth,
        RiskProfile riskProfile,
        OffsetDateTime createdAt,
        List<AccountSummary> accounts) {

    static ClientDetail from(Client c, List<AccountSummary> accounts) {
        return new ClientDetail(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getDateOfBirth(), c.getRiskProfile(), c.getCreatedAt(), accounts);
    }
}
