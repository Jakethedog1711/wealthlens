package com.wealthlens.api.copilot;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DriftSummaryController {

    private final DriftSummaryService service;

    public DriftSummaryController(DriftSummaryService service) {
        this.service = service;
    }

    /**
     * POST /clients/15/drift-summary  (optionally ?asOf=2026-09-30)
     * POST, not GET: each call costs money and creates an ai_output record.
     */
    @PostMapping("/clients/{id}/drift-summary")
    public DriftSummaryResponse summarise(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.summarise(id, asOf);
    }
}
