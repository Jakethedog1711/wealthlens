package com.wealthlens.api.copilot;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Human in the loop: an advisor approves or rejects every AI output before it can reach a client. */
@Service
public class ReviewService {

    private final AiOutputRepository repo;

    public ReviewService(AiOutputRepository repo) {
        this.repo = repo;
    }

    public List<AiOutputRepository.AiOutputRecord> pending() {
        return repo.findByStatus("PENDING");
    }

    public AiOutputRepository.AiOutputRecord get(long id) {
        return repo.findById(id).orElseThrow(() -> new AiOutputNotFoundException(id));
    }

    @Transactional
    public AiOutputRepository.AiOutputRecord review(long id, ReviewDecision decision, String reviewer) {
        AiOutputRepository.AiOutputRecord current = get(id);          // 404 if it doesn't exist
        if (!repo.review(id, decision, reviewer)) {
            throw new ReviewConflictException(id, current.reviewStatus());   // 409 if already decided
        }
        return get(id);
    }

    public List<AiOutputRepository.AiOutputRecord> approvedLetters(long clientId) {
        return repo.approvedLetters(clientId);
    }
}
