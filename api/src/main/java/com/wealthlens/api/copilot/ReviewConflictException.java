package com.wealthlens.api.copilot;

/** Thrown when someone tries to review an output that is not PENDING (it was already decided). */
public class ReviewConflictException extends RuntimeException {

    public ReviewConflictException(long id, String currentStatus) {
        super("AI output " + id + " is already " + currentStatus + "; only PENDING outputs can be reviewed");
    }
}
