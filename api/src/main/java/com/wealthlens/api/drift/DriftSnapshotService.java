package com.wealthlens.api.drift;

import com.wealthlens.api.client.ClientRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculates drift for every client on a set of dates and saves it to allocation_snapshots.
 * That table is what Power BI reads: dashboards should read stored results, not call the
 * API 2,400 times. Safe to re-run: existing rows are updated, not duplicated.
 */
@Service
public class DriftSnapshotService {

    private final ClientRepository clients;
    private final DriftService drift;
    private final JdbcClient jdbc;

    public DriftSnapshotService(ClientRepository clients, DriftService drift, JdbcClient jdbc) {
        this.clients = clients;
        this.drift = drift;
        this.jdbc = jdbc;
    }

    /** The last day of every month from `from` to `to`, plus `to` itself if it isn't a month-end. */
    static List<LocalDate> monthEnds(LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            LocalDate end = m.atEndOfMonth();
            dates.add(end.isAfter(to) ? to : end);
        }
        return dates;
    }

    /** One transaction for the whole run: either every snapshot is saved, or none are. */
    @Transactional
    public SnapshotSummary snapshot(List<LocalDate> dates) {
        List<Long> clientIds = clients.findAll().stream().map(c -> c.getId()).toList();
        int rows = 0;
        for (LocalDate date : dates) {
            for (Long clientId : clientIds) {
                ClientDrift result = drift.driftFor(clientId, date);
                for (DriftRow row : result.assetClasses()) {
                    rows += jdbc.sql("""
                                    INSERT INTO allocation_snapshots
                                        (client_id, snapshot_date, asset_class, market_value,
                                         actual_pct, target_pct, drift_pct)
                                    VALUES (:client, :date, :assetClass, :value, :actual, :target, :drift)
                                    ON CONFLICT (client_id, snapshot_date, asset_class) DO UPDATE
                                        SET market_value = EXCLUDED.market_value,
                                            actual_pct   = EXCLUDED.actual_pct,
                                            target_pct   = EXCLUDED.target_pct,
                                            drift_pct    = EXCLUDED.drift_pct,
                                            calculated_at = now()
                                    """)
                            .param("client", clientId)
                            .param("date", date)
                            .param("assetClass", row.assetClass().name())
                            .param("value", row.marketValue())
                            .param("actual", row.actualPct())
                            .param("target", row.targetPct())
                            .param("drift", row.driftPct())
                            .update();
                }
            }
        }
        return new SnapshotSummary(dates, clientIds.size(), rows);
    }

    public record SnapshotSummary(List<LocalDate> dates, int clients, int rowsWritten) {
    }
}
