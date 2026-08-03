package com.quorumtrading.model.valueobject;

import java.util.UUID;

public record UserId(UUID value) {

    /**
     * The principal for work the platform performs on its own behalf: schedulers, startup
     * recovery, and event handlers that act without a requesting user.
     *
     * <p>Ownership checks used to be written {@code if (requestingUserId != null) require(...)},
     * so a null user meant <em>skip the check</em>. That is fail-open: an adapter that failed to
     * resolve the caller silently bypassed authorization entirely. Authorization now denies null
     * and only bypasses for this explicit principal, so a system caller has to say so in code.</p>
     *
     * <p>This is a sentinel, not a row in the users table — never persist it as an owner.</p>
     */
    public static final UserId SYSTEM = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId value cannot be null");
        }
    }

    /** True for the platform's own principal, which bypasses ownership checks. */
    public boolean isSystem() {
        return SYSTEM.value.equals(this.value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId fromString(String value) {
        return new UserId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "UserId[" + value + "]";
    }
}
