package com.ghouse.socialraven.constant;

public enum WorkspaceRole {
    OWNER,
    ADMIN,
    MEMBER,
    VIEWER;

    /** Numeric privilege level. Higher = more privileged. */
    public int rank() {
        return switch (this) {
            case OWNER  -> 3;
            case ADMIN  -> 2;
            case MEMBER -> 1;
            case VIEWER -> 0;
        };
    }

    /** Returns true if this role has at least the privilege of {@code required}. */
    public boolean isAtLeast(WorkspaceRole required) {
        return this.rank() >= required.rank();
    }
}
