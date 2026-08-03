package com.quorumtrading.model.valueobject;

/**
 * Sealed marker for the credential material a broker account authenticates with.
 *
 * <p>The supertype deliberately exposes <strong>no</strong> {@code apiKey()} /
 * {@code apiSecret()} accessors: not every broker authenticates with a static
 * key/secret pair (Saxo uses rotating OAuth2 tokens). Callers that need the
 * Alpaca-style key/secret must narrow to {@link ApiKeyCredentials} via pattern
 * matching, which keeps each implementation's invariants self-contained.</p>
 *
 * <p>Permitted implementations:</p>
 * <ul>
 *   <li>{@link ApiKeyCredentials} — static {@code apiKey}/{@code apiSecret} pair
 *       (Alpaca paper/live).</li>
 *   <li>{@link SaxoOAuthCredentials} — OAuth2 app credentials plus a rotating
 *       refresh token and a short-lived access token (Saxo SIM/LIVE).</li>
 * </ul>
 *
 * <p>This is a domain value object: it carries no JPA/JSON/Jackson annotations.</p>
 */
public sealed interface BrokerCredentials permits ApiKeyCredentials, SaxoOAuthCredentials {
}
