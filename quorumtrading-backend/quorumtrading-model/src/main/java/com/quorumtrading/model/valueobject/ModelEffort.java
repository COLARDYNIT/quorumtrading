package com.quorumtrading.model.valueobject;

import java.util.Locale;
import java.util.Optional;

/**
 * How hard a model should think — a second dimension of model choice, distinct from which model.
 *
 * <p>Deliberately <b>not</b> folded into {@link ModelId}. A compound id like {@code sonnet:high}
 * would make {@code ModelId} stop being a model id, and would collide head-on with the
 * {@code :online} web-search decoration that already uses that syntax for something else entirely.
 * Two orthogonal dimensions get two fields.</p>
 *
 * <p>Not every engine has the concept. One that does not must <b>omit</b> the flag rather than
 * default it — inventing a value for a tool that never asked for one makes every call fail.</p>
 */
public enum ModelEffort {

    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX;

    /** The flag value a CLI expects: the enum name, lowercased. */
    public String flagValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parse a stored or user-supplied value, empty when it names no known level. */
    public static Optional<ModelEffort> fromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        for (ModelEffort effort : values()) {
            if (effort.name().equals(normalised)) {
                return Optional.of(effort);
            }
        }
        return Optional.empty();
    }
}
