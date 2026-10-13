package com.wealthlens.api.drift;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DriftController {

    private final DriftService service;

    public DriftController(DriftService service) {
        this.service = service;
    }

    /**
     * GET /clients/15/drift                  drift at the latest price date
     * GET /clients/15/drift?asOf=2026-03-31  drift at the end of March
     */
    @GetMapping("/clients/{id}/drift")
    public ClientDrift drift(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.driftFor(id, asOf);
    }
}
