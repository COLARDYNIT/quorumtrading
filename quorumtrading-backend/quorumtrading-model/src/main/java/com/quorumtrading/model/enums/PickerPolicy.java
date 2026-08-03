package com.quorumtrading.model.enums;

/**
 * Strategy selection policy applied each time a recurring investment fires.
 *
 * <p>Phase 3 ships with {@link #AI_PICK} only — additional policies
 * ({@code FIXED}, {@code ROTATION}, {@code LATEST_RESEARCH}) are reserved
 * so schema/API doesn't need to change later.</p>
 */
public enum PickerPolicy {
    /** A picker port (typically LLM-backed) selects a strategy from the user's eligible pool. */
    AI_PICK
}
