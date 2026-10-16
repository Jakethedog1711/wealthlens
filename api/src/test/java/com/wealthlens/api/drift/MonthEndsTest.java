package com.wealthlens.api.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MonthEndsTest {

    @Test
    void oneDatePerMonthIncludingLeapFebruary() {
        assertThat(DriftSnapshotService.monthEnds(LocalDate.parse("2027-12-15"), LocalDate.parse("2028-03-31")))
                .containsExactly(LocalDate.parse("2027-12-31"), LocalDate.parse("2028-01-31"),
                        LocalDate.parse("2028-02-29"), LocalDate.parse("2028-03-31"));
    }

    @Test
    void aRangeEndingMidMonthStopsAtTheEndDate() {
        assertThat(DriftSnapshotService.monthEnds(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-09-15")))
                .containsExactly(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-15"));
    }

    @Test
    void ourDataYearGivesTwelveSnapshots() {
        assertThat(DriftSnapshotService.monthEnds(LocalDate.parse("2025-10-01"), LocalDate.parse("2026-09-30")))
                .hasSize(12)
                .startsWith(LocalDate.parse("2025-10-31"))
                .endsWith(LocalDate.parse("2026-09-30"));
    }
}
