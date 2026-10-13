package com.wealthlens.api.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Maps one row of the "clients" table to a Java object.
 * The table is created by Flyway; this class must match it exactly
 * (Spring checks that on start-up because of ddl-auto=validate).
 */
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // Postgres assigns the id
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)                           // stored as text, e.g. 'MODERATE'
    @Column(name = "risk_profile", nullable = false, length = 20)
    private RiskProfile riskProfile;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;                      // filled in by the database

    protected Client() {
        // JPA needs an empty constructor to build objects from rows
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public RiskProfile getRiskProfile() { return riskProfile; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
