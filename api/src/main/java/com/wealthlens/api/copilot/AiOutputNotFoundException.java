package com.wealthlens.api.copilot;

public class AiOutputNotFoundException extends RuntimeException {

    public AiOutputNotFoundException(long id) {
        super("AI output " + id + " not found");
    }
}
