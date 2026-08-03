package com.quorumtrading.application.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * Out-port for single-use OAuth state storage with a time-to-live.
 *
 * <p>The OAuth2 authorization-code flow uses an opaque {@code state} value to bind the redirect
 * back to the originating request and defend against CSRF. This port stores the state-keyed
 * onboarding context when onboarding begins and atomically consumes it on callback. Two invariants
 * matter:</p>
 * <ul>
 *   <li><strong>Single use</strong> — {@link #takeOnce} returns the stored value at most once; a
 *       replay of the same {@code state} yields empty (defends against authorization-code replay).</li>
 *   <li><strong>TTL</strong> — entries expire after the supplied {@code ttl}, so an abandoned
 *       onboarding does not leave a usable state behind.</li>
 * </ul>
 *
 * <p>Implementations are typically Redis-backed (atomic {@code GETDEL} + {@code EX}); the value is
 * an opaque, already-serialized onboarding context owned by the calling service.</p>
 */
public interface OAuthStateStorePort {

    /**
     * Stores a value keyed by a single-use {@code state}, expiring after {@code ttl}.
     *
     * @param state an opaque, unguessable state key (non-blank)
     * @param value the opaque value to associate with the state (non-null)
     * @param ttl   how long the entry remains retrievable before it expires (non-null, positive)
     */
    void put(String state, String value, Duration ttl);

    /**
     * Atomically retrieves and removes the value for {@code state}.
     *
     * <p>Returns the stored value exactly once; any subsequent call for the same {@code state}
     * (or one whose TTL has elapsed) returns empty. The get-and-delete must be atomic so a
     * concurrent callback cannot consume the same state twice.</p>
     *
     * @param state the state key to consume (non-blank)
     * @return the stored value if present and not yet consumed, otherwise empty
     */
    Optional<String> takeOnce(String state);
}
