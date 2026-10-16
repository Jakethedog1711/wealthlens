package com.wealthlens.api.drift;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DriftSnapshotController {

    private final DriftSnapshotService service;

    public DriftSnapshotController(DriftSnapshotService service) {
        this.service = service;
    }

    /**
     * POST /drift/snapshots?from=2025-10-01&to=2026-09-30
     * Saves drift for every client at every month-end in the range.
     * POST, not GET: it changes data, and GET requests must never do that.
     */
    @PostMapping("/drift/snapshots")
    public DriftSnapshotService.SnapshotSummary snapshot(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must not be before 'from'");
        }
        return service.snapshot(DriftSnapshotService.monthEnds(from, to));
    }
}
