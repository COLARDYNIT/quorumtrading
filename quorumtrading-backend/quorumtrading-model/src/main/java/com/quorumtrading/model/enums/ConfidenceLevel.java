package com.quorumtrading.model.enums;

/**
 * How much corroboration stands behind a resolution result.
 *
 * <p>This is <b>derived from evidence, never asserted by a language model</b>.
 * A model's own confidence is a statement about its fluency, not about whether
 * the instrument exists; the levels below are assigned by counting how many
 * independent sources agreed.</p>
 */
public enum ConfidenceLevel {

    /**
     * Independently corroborated: an authoritative source (the broker's own
     * instrument record) confirmed both the symbol and the company identity.
     */
    HIGH,

    /**
     * Partially corroborated. The symbol was confirmed tradable, but identity
     * could not be fully cross-checked — for example the broker exposes no
     * company name for the instrument.
     */
    MEDIUM,

    /**
     * Uncorroborated or contradicted: a single unverifiable source, an
     * unreachable broker, or evidence that actively disagreed.
     */
    LOW
}
