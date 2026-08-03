package com.quorumtrading.model.valueobject;

import com.quorumtrading.model.enums.SaxoEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SaxoOAuthCredentialsTest {

    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

    private SaxoOAuthCredentials withFreshToken() {
        return new SaxoOAuthCredentials(
                "app-key",
                "app-secret",
                "refresh-1",
                "access-1",
                NOW.plus(Duration.ofMinutes(20)),
                SaxoEnvironment.SIM
        );
    }

    @Test
    void shouldCreateWithValidMandatoryFields() {
        SaxoOAuthCredentials creds = withFreshToken();
        assertEquals("app-key", creds.appKey());
        assertEquals("app-secret", creds.appSecret());
        assertEquals("refresh-1", creds.refreshToken());
        assertEquals("access-1", creds.accessToken());
        assertEquals(SaxoEnvironment.SIM, creds.environment());
    }

    @Test
    void shouldBeABrokerCredentials() {
        BrokerCredentials creds = withFreshToken();
        assertInstanceOf(SaxoOAuthCredentials.class, creds);
    }

    @Test
    void shouldAllowNullAccessTokenAndExpiryForFreshlyOnboarded() {
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-1", null, null, SaxoEnvironment.LIVE);
        assertNull(creds.accessToken());
        assertNull(creds.accessTokenExpiry());
    }

    @Test
    void shouldThrowOnBlankAppKey() {
        assertThrows(IllegalArgumentException.class, () -> new SaxoOAuthCredentials(
                "  ", "app-secret", "refresh-1", "access-1", NOW, SaxoEnvironment.SIM));
    }

    @Test
    void shouldThrowOnBlankAppSecret() {
        assertThrows(IllegalArgumentException.class, () -> new SaxoOAuthCredentials(
                "app-key", "  ", "refresh-1", "access-1", NOW, SaxoEnvironment.SIM));
    }

    @Test
    void shouldThrowOnBlankRefreshToken() {
        assertThrows(IllegalArgumentException.class, () -> new SaxoOAuthCredentials(
                "app-key", "app-secret", "  ", "access-1", NOW, SaxoEnvironment.SIM));
    }

    @Test
    void shouldThrowOnNullEnvironment() {
        assertThrows(IllegalArgumentException.class, () -> new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-1", "access-1", NOW, null));
    }

    // ---- withRotatedTokens ----

    @Test
    void withRotatedTokensReplacesTokensAndPreservesAppCredentials() {
        SaxoOAuthCredentials original = withFreshToken();
        Instant newExpiry = NOW.plus(Duration.ofMinutes(20));

        SaxoOAuthCredentials rotated =
                original.withRotatedTokens("refresh-2", "access-2", newExpiry);

        assertEquals("refresh-2", rotated.refreshToken());
        assertEquals("access-2", rotated.accessToken());
        assertEquals(newExpiry, rotated.accessTokenExpiry());
        // app credentials + environment preserved
        assertEquals("app-key", rotated.appKey());
        assertEquals("app-secret", rotated.appSecret());
        assertEquals(SaxoEnvironment.SIM, rotated.environment());
        // original is unchanged (immutability)
        assertEquals("refresh-1", original.refreshToken());
        assertEquals("access-1", original.accessToken());
    }

    @Test
    void withRotatedTokensRejectsBlankAccessToken() {
        SaxoOAuthCredentials original = withFreshToken();
        assertThrows(IllegalArgumentException.class,
                () -> original.withRotatedTokens("refresh-2", "  ", NOW));
    }

    @Test
    void withRotatedTokensRejectsNullExpiry() {
        SaxoOAuthCredentials original = withFreshToken();
        assertThrows(NullPointerException.class,
                () -> original.withRotatedTokens("refresh-2", "access-2", null));
    }

    // ---- isAccessTokenExpired ----

    @Test
    void notExpiredWhenWellWithinValidityAndSkew() {
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-1", "access-1",
                NOW.plus(Duration.ofMinutes(20)), SaxoEnvironment.SIM);
        assertFalse(creds.isAccessTokenExpired(NOW, Duration.ofMinutes(2)));
    }

    @Test
    void expiredWhenPastExpiry() {
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-1", "access-1",
                NOW.minus(Duration.ofSeconds(1)), SaxoEnvironment.SIM);
        assertTrue(creds.isAccessTokenExpired(NOW, Duration.ZERO));
    }

    @Test
    void expiredWhenWithinSkewWindowOfExpiry() {
        // expires in 1 minute, but skew is 2 minutes -> treat as expired (proactive refresh)
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-1", "access-1",
                NOW.plus(Duration.ofMinutes(1)), SaxoEnvironment.SIM);
        assertTrue(creds.isAccessTokenExpired(NOW, Duration.ofMinutes(2)));
    }

    @Test
    void expiredExactlyAtSkewBoundary() {
        // expiry - skew == now -> not before -> expired
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-1", "access-1",
                NOW.plus(Duration.ofMinutes(2)), SaxoEnvironment.SIM);
        assertTrue(creds.isAccessTokenExpired(NOW, Duration.ofMinutes(2)));
    }

    @Test
    void expiredWhenNoAccessToken() {
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-1", null, null, SaxoEnvironment.SIM);
        assertTrue(creds.isAccessTokenExpired(NOW, Duration.ofMinutes(2)));
    }

    @Test
    void expiredWhenNoExpiryRecorded() {
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-1", "access-1", null, SaxoEnvironment.SIM);
        assertTrue(creds.isAccessTokenExpired(NOW, Duration.ofMinutes(2)));
    }

    @Test
    void isAccessTokenExpiredRejectsNullArgs() {
        SaxoOAuthCredentials creds = withFreshToken();
        assertThrows(NullPointerException.class, () -> creds.isAccessTokenExpired(null, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> creds.isAccessTokenExpired(NOW, null));
    }

    @Test
    void isAccessTokenExpiredRejectsNegativeSkew() {
        SaxoOAuthCredentials creds = withFreshToken();
        assertThrows(IllegalArgumentException.class,
                () -> creds.isAccessTokenExpired(NOW, Duration.ofMinutes(-1)));
    }

    // ---- redaction ----

    @Test
    void toStringRedactsAllSecretMaterial() {
        SaxoOAuthCredentials creds = withFreshToken();
        String str = creds.toString();
        assertFalse(str.contains("app-key"));
        assertFalse(str.contains("app-secret"));
        assertFalse(str.contains("refresh-1"));
        assertFalse(str.contains("access-1"));
        assertTrue(str.contains("***"));
        // non-secret metadata is allowed to appear
        assertTrue(str.contains("SIM"));
    }

    @Test
    void toStringShowsNullWhenAccessTokenAbsent() {
        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
                "app-key", "app-secret", "refresh-1", null, null, SaxoEnvironment.SIM);
        String str = creds.toString();
        assertTrue(str.contains("accessToken=null"));
    }
}
