package com.quorumtrading.application.service;

import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Mints + verifies one-shot unsubscribe tokens embedded in outgoing emails.
 *
 * <p><b>Token format:</b> {@code userIdBase64.typeNameBase64.signature}
 * where signature = HMAC-SHA256(userIdBase64.typeNameBase64, secret).
 * No expiry — unsubscribe links are valid as long as the secret holds.</p>
 *
 * <p><b>Per-type, not global.</b> A user who unsubscribes from
 * {@code INVESTMENT_SKIPPED} still receives {@code INVESTMENT_EXECUTED} emails.
 * The endpoint that processes the token toggles only that type's EMAIL channel.</p>
 *
 * <p><b>Why HMAC and not a DB lookup:</b> tokens stay short, no DB write per email,
 * and there is no token-revocation problem — we trust the secret to rotate. Rotating
 * the secret invalidates all outstanding unsubscribe links, which is acceptable since
 * the alternative (toggling email back on) is trivial in app settings.</p>
 */
public class UnsubscribeTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;

    public UnsubscribeTokenService(String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException(
                    "Unsubscribe secret must be at least 16 characters (got " +
                            (secret == null ? 0 : secret.length()) + ")");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String mint(UserId userId, NotificationType type) {
        String userPart = encode(userId.value().toString());
        String typePart = encode(type.name());
        String payload = userPart + "." + typePart;
        String signature = sign(payload);
        return payload + "." + signature;
    }

    /**
     * Verify a token and extract its (userId, type) payload.
     * Returns empty if the signature is invalid or the token is malformed.
     */
    public Optional<Verified> verify(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String[] parts = token.split("\\.");
        if (parts.length != 3) return Optional.empty();
        String userPart = parts[0];
        String typePart = parts[1];
        String providedSig = parts[2];
        String expectedSig = sign(userPart + "." + typePart);
        if (!constantTimeEquals(expectedSig, providedSig)) {
            return Optional.empty();
        }
        try {
            UserId userId = new UserId(UUID.fromString(decode(userPart)));
            NotificationType type = NotificationType.valueOf(decode(typePart));
            return Optional.of(new Verified(userId, type));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record Verified(UserId userId, NotificationType type) {}

    // -----------------------------------------------------------------

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return ENCODER.encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign unsubscribe token", e);
        }
    }

    private static String encode(String value) {
        return ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(DECODER.decode(value), StandardCharsets.UTF_8);
    }

    /** Constant-time comparison to avoid timing attacks on signature verification. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
