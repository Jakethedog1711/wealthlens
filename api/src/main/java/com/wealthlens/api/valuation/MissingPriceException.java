package com.wealthlens.api.valuation;

public class MissingPriceException extends RuntimeException {

    public MissingPriceException(String ticker) {
        super("No price available for " + ticker + " on or before the valuation date");
    }
}
