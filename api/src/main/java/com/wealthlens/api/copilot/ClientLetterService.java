package com.wealthlens.api.copilot;

import com.wealthlens.api.client.Client;
import com.wealthlens.api.client.ClientNotFoundException;
import com.wealthlens.api.client.ClientRepository;
import com.wealthlens.api.drift.ClientDrift;
import com.wealthlens.api.drift.DriftService;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Drafts a client-facing letter about their drift. It is only a DRAFT until an advisor approves it. */
@Service
public class ClientLetterService {

    private final ClientRepository clients;
    private final DriftService drift;
    private final AiGenerator ai;
    private final JsonMapper json;

    public ClientLetterService(ClientRepository clients, DriftService drift, AiGenerator ai, JsonMapper json) {
        this.clients = clients;
        this.drift = drift;
        this.ai = ai;
        this.json = json;
    }

    /** The facts a letter is written from: the first name (for "Dear ...") plus the drift numbers. */
    public record LetterFacts(String clientFirstName, ClientDrift drift) {
    }

    public record LetterDraft(long aiOutputId, String letter, java.util.List<String> unverifiedNumbers,
                              String reviewStatus, String promptVersion, LetterFacts facts) {
    }

    public LetterDraft draft(Long clientId, LocalDate asOf) {
        Client client = clients.findById(clientId).orElseThrow(() -> new ClientNotFoundException(clientId));
        LetterFacts facts = new LetterFacts(client.getFirstName(), drift.driftFor(clientId, asOf));

        AiGenerator.Generated g = ai.generate("CLIENT_LETTER", clientId, Prompts.CLIENT_LETTER_SYSTEM,
                Prompts.CLIENT_LETTER_VERSION, json.writeValueAsString(facts),
                DriftSummaryService.numbersIn(facts.drift()));

        return new LetterDraft(g.aiOutputId(), g.text(), g.unverifiedNumbers(), "PENDING",
                g.promptVersion(), facts);
    }
}
