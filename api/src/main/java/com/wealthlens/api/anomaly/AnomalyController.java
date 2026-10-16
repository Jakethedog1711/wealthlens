package com.wealthlens.api.anomaly;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnomalyController {

    private final AnomalyService service;

    public AnomalyController(AnomalyService service) {
        this.service = service;
    }

    /** POST /anomalies/scan : run all four rules over every account. Safe to re-run. */
    @PostMapping("/anomalies/scan")
    public AnomalyService.ScanResult scan() {
        return service.scan();
    }

    /** GET /anomalies : the compliance queue. */
    @GetMapping("/anomalies")
    public List<StoredFlag> all() {
        return service.all();
    }

    /** GET /anomalies/5 : one flag with its metrics and evidence transactions. */
    @GetMapping("/anomalies/{id}")
    public StoredFlag get(@PathVariable long id) {
        return service.get(id);
    }
}
