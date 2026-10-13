package com.wealthlens.api.client;

import com.wealthlens.api.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP endpoints for clients. Turns web requests into service calls and back into JSON. */
@RestController
@RequestMapping("/clients")
public class ClientController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ClientService service;

    public ClientController(ClientService service) {
        this.service = service;
    }

    /** GET /clients?page=0&size=20 : one page of clients, sorted by surname. */
    @GetMapping
    public PageResponse<ClientSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Never trust the caller: keep page >= 0 and size between 1 and 100
        return service.list(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }

    /** GET /clients/42 : one client with their accounts, or 404 if there is no such client. */
    @GetMapping("/{id}")
    public ClientDetail get(@PathVariable Long id) {
        return service.get(id);
    }
}
