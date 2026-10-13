package com.wealthlens.api.account;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // Spring Data reads the method name and writes the query:
    // SELECT * FROM accounts WHERE client_id = ? ORDER BY opened_on
    List<Account> findByClientIdOrderByOpenedOn(Long clientId);
}
