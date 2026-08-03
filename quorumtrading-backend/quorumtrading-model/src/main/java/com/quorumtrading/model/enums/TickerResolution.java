package com.quorumtrading.model.enums;

/**
 * The outcome of resolving a human's words ("Toyota", "LVMH", "SpaceX") to a
 * tradable instrument.
 *
 * <p><b>Why this exists.</b> Ticker resolution previously reported its outcome
 * through a single {@code tradableOnBroker} boolean plus prose. That collapsed
 * genuinely different situations onto the same machine-readable answer: a
 * company that is definitely private, a company the engine has simply never
 * heard of, and a company that listed after the engine's training cutoff all
 * came back identical apart from their wording. A caller — human or agent —
 * could not tell "this does not exist" from "I could not find out", so the
 * only safe reading of a negative answer was the pessimistic one, and users
 * abandoned searches for instruments the platform could actually trade.</p>
 *
 * <p>Each constant below is a distinct claim with distinct consequences, so a
 * caller can branch on it instead of parsing English.</p>
 */
public enum TickerResolution {

    /**
     * An instrument was identified <em>and</em> verified tradable through the
     * validating broker. The result's ticker is safe to use in a strategy.
     *
     * <p>Note this says nothing about <em>which</em> listing was selected —
     * check {@code listingType}, because the tradable route may be a
     * depositary receipt rather than the company's primary listing.</p>
     */
    RESOLVED,

    /**
     * The company was identified and its listing(s) are known, but none of them
     * can be traded through the validating broker — typically a European or
     * Asian venue against a US-only broker.
     *
     * <p>This is a statement about the <em>broker</em>, not about the company or
     * the platform. Attaching a broker that covers the venue changes it.</p>
     */
    TRADABLE_ELSEWHERE,

    /**
     * A candidate ticker was found, but verification contradicted it: the
     * broker's own record for that symbol names a different company than the
     * one that was asked for.
     *
     * <p>This is the "LVMH resolves to Moelis &amp; Company" case — a root
     * ticker on one venue colliding with an unrelated company's ticker on
     * another. The instrument is deliberately <b>not</b> reported as tradable
     * and the ticker is withheld, because acting on it would trade the wrong
     * company. Failing closed is the whole point of this state.</p>
     */
    AMBIGUOUS,

    /**
     * The company exists but has no public listing, and the answer was
     * corroborated — the engine that answered had live web access.
     *
     * <p>Only use this when the "not listed" claim is actually trustworthy. If
     * the answering engine could not check, the honest answer is
     * {@link #UNVERIFIED}.</p>
     */
    NOT_LISTED,

    /**
     * Could not determine the answer either way.
     *
     * <p>Covers: an engine with no web access asserting a company is private
     * (a listing after its training cutoff is invisible to it), a broker that
     * was unreachable for validation, and an engine that explicitly hedged.
     * The distinguishing property is that a negative answer here is an absence
     * of evidence, not evidence of absence — so callers should invite the user
     * to supply a ticker directly rather than reporting "does not exist".</p>
     */
    UNVERIFIED
}
