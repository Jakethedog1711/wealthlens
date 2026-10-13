package com.wealthlens.api.drift;

import com.wealthlens.api.client.Client;
import com.wealthlens.api.client.ClientNotFoundException;
import com.wealthlens.api.client.ClientRepository;
import com.wealthlens.api.valuation.AssetClass;
import com.wealthlens.api.valuation.AssetClassValue;
import com.wealthlens.api.valuation.ClientValuation;
import com.wealthlens.api.valuation.ValuationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Drift builds on valuation: value the portfolio first, then compare it with the targets. */
@Service
@Transactional(readOnly = true)
public class DriftService {

    private final ClientRepository clients;
    private final ValuationService valuations;
    private final TargetAllocationRepository targets;
    private final DriftCalculator calculator = new DriftCalculator();

    public DriftService(ClientRepository clients, ValuationService valuations,
                        TargetAllocationRepository targets) {
        this.clients = clients;
        this.valuations = valuations;
        this.targets = targets;
    }

    public ClientDrift driftFor(Long clientId, LocalDate asOf) {
        Client client = clients.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
        ClientValuation valuation = valuations.valueClient(clientId, asOf);

        Map<AssetClass, BigDecimal> valuesByClass = valuation.assetClasses().stream()
                .collect(Collectors.toMap(AssetClassValue::assetClass, AssetClassValue::marketValue));

        DriftResult result = calculator.calculate(valuesByClass, targets.targetsFor(client.getRiskProfile()));

        return new ClientDrift(clientId, client.getRiskProfile(), valuation.asOf(), valuation.totalValue(),
                result.needsRebalance(), result.breaches(), result.rows());
    }
}
