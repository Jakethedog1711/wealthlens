package com.wealthlens.api.client;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data writes the SQL for us: findAll(page), findById(id), count() and more
 * come for free just by extending JpaRepository.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {
}
