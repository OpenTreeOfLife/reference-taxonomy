package org.opentreeoflife.conflict;

public enum Disposition {
    NONE ("none"),
    CONFLICTS_WITH ("conflicts_with"),
    RESOLVES ("resolves"),
    SUPPORTED_BY ("supported_by"),
    PATH_SUPPORTED_BY ("partial_path_of"),
    EXCLUDES ("excludes"),
    TIP ("tip");

    public String name;

    Disposition(String name) {
        this.name = name;
    }
}
