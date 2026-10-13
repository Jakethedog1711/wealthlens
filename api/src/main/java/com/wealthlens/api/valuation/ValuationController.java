package com.wealthlens.api.valuation;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValuationController {

    private final ValuationService service;

    public ValuationController(ValuationService service) {
        this.service = service;
    }

    /**
     * GET /clients/15/valuation                  value at the latest price date
     * GET /clients/15/valuation?asOf=2026-03-31  value at the end of March
     */
    @GetMapping("/clients/{id}/valuation")
    public ClientValuation value(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.valueClient(id, asOf);
    }
}
