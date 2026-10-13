package com.wealthlens.api.drift;

public enum DriftStatus {
    WITHIN_TOLERANCE,
    OVERWEIGHT,     // holds more of this asset class than the target allows
    UNDERWEIGHT     // holds less than the target allows
}
