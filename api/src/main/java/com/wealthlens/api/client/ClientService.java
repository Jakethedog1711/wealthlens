package com.wealthlens.api.client;

import com.wealthlens.api.account.AccountRepository;
import com.wealthlens.api.account.AccountSummary;
import com.wealthlens.api.common.PageResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic lives here, not in the controller. The controller only deals with HTTP;
 * this class decides what data to fetch and how to shape it.
 */
@Service
@Transactional(readOnly = true)     // these methods only read, never write
public class ClientService {

    private final ClientRepository clients;
    private final AccountRepository accounts;

    // Spring passes the repositories in automatically ("constructor injection")
    public ClientService(ClientRepository clients, AccountRepository accounts) {
        this.clients = clients;
        this.accounts = accounts;
    }

    public PageResponse<ClientSummary> list(int page, int size) {
        var request = PageRequest.of(page, size, Sort.by("lastName", "firstName", "id"));
        return PageResponse.from(clients.findAll(request).map(ClientSummary::from));
    }

    public ClientDetail get(Long id) {
        Client client = clients.findById(id).orElseThrow(() -> new ClientNotFoundException(id));
        List<AccountSummary> clientAccounts = accounts.findByClientIdOrderByOpenedOn(id).stream()
                .map(AccountSummary::from)
                .toList();
        return ClientDetail.from(client, clientAccounts);
    }
}
