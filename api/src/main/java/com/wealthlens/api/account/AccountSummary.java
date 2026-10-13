package com.wealthlens.api.account;

import java.time.LocalDate;

public record AccountSummary(
        Long id, String accountNumber, AccountType accountType, String baseCurrency, LocalDate openedOn) {

    public static AccountSummary from(Account a) {
        return new AccountSummary(a.getId(), a.getAccountNumber(), a.getAccountType(),
                a.getBaseCurrency(), a.getOpenedOn());
    }
}
