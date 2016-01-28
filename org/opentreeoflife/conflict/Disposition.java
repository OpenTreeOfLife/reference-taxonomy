package org.opentreeoflife.conflict;

public enum Disposition {
    NONE,
    CONFLICTS_WITH,
    RESOLVES,
    SUPPORTED_BY,
    PATH_SUPPORTED_BY,

    // Not used
    CONGRUENT,                  // or IS_CONGRUENT_WITH ?
    RESOLVED_BY,
    SUPPORTS,
    SUPPORTS_PATH,
}
