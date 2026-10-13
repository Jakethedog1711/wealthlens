package com.wealthlens.api.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Maps one row of the "accounts" table. */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Kept as a plain id for now. We'll add a full JPA relationship only if we need one.
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @JdbcTypeCode(SqlTypes.CHAR)          // the column is CHAR(3), not VARCHAR
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "opened_on", nullable = false)
    private LocalDate openedOn;

    protected Account() {
    }

    public Long getId() { return id; }
    public Long getClientId() { return clientId; }
    public String getAccountNumber() { return accountNumber; }
    public AccountType getAccountType() { return accountType; }
    public String getBaseCurrency() { return baseCurrency; }
    public LocalDate getOpenedOn() { return openedOn; }
}
