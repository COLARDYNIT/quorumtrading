package com.quorumtrading.model.valueobject;

/**
 * Why an engine can or cannot enumerate its models — a distinction that decides whether skipping
 * validation is honest or dangerous.
 *
 * <p>A single boolean conflated two very different states. When the probe of a local server failed,
 * the engine reported "no listing support", validation returned early, and <em>any string</em> was
 * accepted as a model preference. A transient outage therefore silently disabled the only check
 * standing between a user and a preference that can never run.</p>
 */
public enum ModelListingCapability {

    /** The engine advertises a catalogue right now; a pin can be checked against it. */
    ENUMERABLE,

    /** The engine has no notion of a catalogue. Validation is genuinely impossible, so it is skipped. */
    NOT_SUPPORTED,

    /**
     * The engine does have a catalogue but could not be reached. Validation is impossible
     * <em>right now</em> — refuse to validate rather than pass anything through.
     */
    UNAVAILABLE
}
