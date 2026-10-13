package com.wealthlens.api.valuation;

import com.wealthlens.api.client.ClientNotFoundException;
import com.wealthlens.api.client.ClientRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fetches the data (repository), then hands it to the pure calculator (PortfolioValuator). */
@Service
@Transactional(readOnly = true)
public class ValuationService {

    private final ClientRepository clients;
    private final ValuationRepository data;
    private final PortfolioValuator valuator = new PortfolioValuator();

    public ValuationService(ClientRepository clients, ValuationRepository data) {
        this.clients = clients;
        this.data = data;
    }

    /** @param asOf valuation date; null means "the latest date we have prices for" */
    public ClientValuation valueClient(Long clientId, LocalDate asOf) {
        if (!clients.existsById(clientId)) {
            throw new ClientNotFoundException(clientId);
        }
        LocalDate date = asOf != null ? asOf : data.latestPriceDate().orElse(LocalDate.now());

        List<TransactionLine> transactions = data.transactionsForClient(clientId, date);
        Set<Long> instrumentIds = transactions.stream()
                .map(TransactionLine::instrumentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Valuation valuation = valuator.value(
                transactions, data.instruments(), data.latestPrices(instrumentIds, date));
        return ClientValuation.of(clientId, date, valuation);
    }
}
