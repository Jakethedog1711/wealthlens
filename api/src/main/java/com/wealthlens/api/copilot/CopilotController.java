package com.wealthlens.api.copilot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Client letters and the advisor review workflow. */
@RestController
public class CopilotController {

    private final ClientLetterService letters;
    private final ReviewService reviews;
    private final AnomalyExplanationService anomalyExplanations;

    public CopilotController(ClientLetterService letters, ReviewService reviews,
                             AnomalyExplanationService anomalyExplanations) {
        this.letters = letters;
        this.reviews = reviews;
        this.anomalyExplanations = anomalyExplanations;
    }

    /** POST /anomalies/5/explanation : a compliance note for one flag (PENDING review, like everything AI). */
    @PostMapping("/anomalies/{id}/explanation")
    public AnomalyExplanationService.AnomalyExplanation explainAnomaly(@PathVariable long id) {
        return anomalyExplanations.explain(id);
    }

    /** POST /clients/4/client-letter : draft a letter (PENDING until an advisor approves it). */
    @PostMapping("/clients/{id}/client-letter")
    public ClientLetterService.LetterDraft draftLetter(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return letters.draft(id, asOf);
    }

    /** GET /ai-outputs/pending : the advisor's review queue. */
    @GetMapping("/ai-outputs/pending")
    public List<AiOutputRepository.AiOutputRecord> pending() {
        return reviews.pending();
    }

    /** GET /ai-outputs/3 : one output with the exact facts it was generated from. */
    @GetMapping("/ai-outputs/{id}")
    public AiOutputRepository.AiOutputRecord get(@PathVariable long id) {
        return reviews.get(id);
    }

    /** The JSON body of a review: {"decision": "APPROVED", "reviewer": "Nathi"} */
    public record ReviewRequest(@NotNull ReviewDecision decision, @NotBlank String reviewer) {
    }

    /** POST /ai-outputs/3/review : approve or reject. Only PENDING outputs can be reviewed. */
    @PostMapping("/ai-outputs/{id}/review")
    public AiOutputRepository.AiOutputRecord review(@PathVariable long id, @Valid @RequestBody ReviewRequest body) {
        return reviews.review(id, body.decision(), body.reviewer());
    }

    /** GET /clients/4/letters : what a client portal would show. APPROVED letters only, never drafts. */
    @GetMapping("/clients/{id}/letters")
    public List<AiOutputRepository.AiOutputRecord> approvedLetters(@PathVariable long id) {
        return reviews.approvedLetters(id);
    }
}
