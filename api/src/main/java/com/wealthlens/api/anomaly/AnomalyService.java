package com.wealthlens.api.anomaly;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnomalyService {

    private final AnomalyRepository repo;

    public AnomalyService(AnomalyRepository repo) {
        this.repo = repo;
    }

    public record ScanResult(int accountsScanned, int transactionsScanned, int flagsRaised,
                             Map<AnomalyRule, Long> flagsByRule) {
    }

    /** Load transactions, run the rules account by account, save the flags. */
    @Transactional
    public ScanResult scan() {
        List<ScanTxn> txns = repo.loadScanTxns();
        Map<Long, List<ScanTxn>> byAccount = txns.stream()
                .collect(Collectors.groupingBy(ScanTxn::accountId, LinkedHashMap::new, Collectors.toList()));

        Map<AnomalyRule, Long> byRule = new EnumMap<>(AnomalyRule.class);
        for (AnomalyRule rule : AnomalyRule.values()) {
            byRule.put(rule, 0L);
        }
        int raised = 0;
        for (List<ScanTxn> accountTxns : byAccount.values()) {
            for (Flag flag : AnomalyDetector.detect(accountTxns)) {
                repo.upsert(flag);
                byRule.merge(flag.rule(), 1L, Long::sum);
                raised++;
            }
        }
        return new ScanResult(byAccount.size(), txns.size(), raised, byRule);
    }

    @Transactional(readOnly = true)
    public List<StoredFlag> all() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public StoredFlag get(long id) {
        return repo.findById(id).orElseThrow(() -> new AnomalyNotFoundException(id));
    }
}
