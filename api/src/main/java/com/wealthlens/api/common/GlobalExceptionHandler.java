package com.wealthlens.api.common;

import com.wealthlens.api.anomaly.AnomalyNotFoundException;
import com.wealthlens.api.client.ClientNotFoundException;
import com.wealthlens.api.copilot.AiOutputNotFoundException;
import com.wealthlens.api.copilot.ReviewConflictException;
import com.wealthlens.api.valuation.MissingPriceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into clean HTTP error responses, in the standard "problem detail"
 * format (RFC 9457), instead of a stack trace or a generic 500 error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ClientNotFoundException.class)
    public ProblemDetail handleClientNotFound(ClientNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Client not found");
        return problem;
    }

    /** Prices haven't been loaded for that date: a data problem, not a server bug. */
    @ExceptionHandler(MissingPriceException.class)
    public ProblemDetail handleMissingPrice(MissingPriceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), ex.getMessage());
        problem.setTitle("Price data missing");
        return problem;
    }

    /** A bad request parameter (e.g. 'to' before 'from'): the caller's mistake, so 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(AiOutputNotFoundException.class)
    public ProblemDetail handleAiOutputNotFound(AiOutputNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("AI output not found");
        return problem;
    }

    /** 409 Conflict: the request is valid, but clashes with the current state (already reviewed). */
    @ExceptionHandler(ReviewConflictException.class)
    public ProblemDetail handleReviewConflict(ReviewConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Already reviewed");
        return problem;
    }

    @ExceptionHandler(AnomalyNotFoundException.class)
    public ProblemDetail handleAnomalyNotFound(AnomalyNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Anomaly flag not found");
        return problem;
    }
}
