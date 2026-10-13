package com.wealthlens.api.drift;

import com.wealthlens.api.client.RiskProfile;
import com.wealthlens.api.valuation.AssetClass;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TargetAllocationRepository {

    private final JdbcClient jdbc;

    public TargetAllocationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The target mix for one risk profile, from the target_allocations reference table. */
    public Map<AssetClass, Target> targetsFor(RiskProfile profile) {
        return jdbc.sql("""
                        SELECT asset_class, target_pct, tolerance_pct
                        FROM target_allocations
                        WHERE risk_profile = :profile
                        """)
                .param("profile", profile.name())
                .query((rs, row) -> Map.entry(
                        AssetClass.valueOf(rs.getString("asset_class")),
                        new Target(rs.getBigDecimal("target_pct"), rs.getBigDecimal("tolerance_pct"))))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
