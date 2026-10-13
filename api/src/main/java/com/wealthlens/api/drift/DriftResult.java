package com.wealthlens.api.drift;

import java.util.List;

public record DriftResult(List<DriftRow> rows, int breaches, boolean needsRebalance) {
}
