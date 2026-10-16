package com.wealthlens.api.anomaly;

public class AnomalyNotFoundException extends RuntimeException {

    public AnomalyNotFoundException(long id) {
        super("Anomaly flag " + id + " not found");
    }
}
