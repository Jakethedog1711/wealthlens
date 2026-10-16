package com.wealthlens.api.anomaly;

/** The four suspicious patterns the engine looks for. Names match the planted answer key. */
public enum AnomalyRule {
    /** Several deposits just under R50,000 in a short window: splitting money to stay under a limit. */
    STRUCTURING,
    /** A large deposit withdrawn again within days: money passing through the account. */
    RAPID_IN_OUT,
    /** A long-quiet account suddenly emptied. */
    DORMANT_THEN_SPIKE,
    /** A conservative client putting a whole deposit into a single share. */
    PROFILE_MISMATCH
}
