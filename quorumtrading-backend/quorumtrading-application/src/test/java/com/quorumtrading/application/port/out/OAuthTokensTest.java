package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.SaxoEnvironment;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthTokensTest {

    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

    @Test
    void rejectsBlankAccessToken() {
        assertThatThrownBy(() -> new OAuthTokens("  ", "refresh", NOW.plusSeconds(60), SaxoEnvironment.SIM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("accessToken");
    }

    @Test
    void rejectsBlankRefreshToken() {
        assertThatThrownBy(() -> new OAuthTokens("access", "", NOW.plusSeconds(60), SaxoEnvironment.SIM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refreshToken");
    }

    @Test
    void rejectsNullExpiryAndEnvironment() {
        assertThatThrownBy(() -> new OAuthTokens("access", "refresh", null, SaxoEnvironment.SIM))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("expiry");
        assertThatThrownBy(() -> new OAuthTokens("access", "refresh", NOW.plusSeconds(60), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("environment");
    }

    @Test
    void ofDerivesAbsoluteExpiryFromLifetime() {
        OAuthTokens tokens = OAuthTokens.of("access", "refresh", NOW, 1200, SaxoEnvironment.LIVE);

        assertThat(tokens.expiry()).isEqualTo(NOW.plusSeconds(1200));
        assertThat(tokens.environment()).isEqualTo(SaxoEnvironment.LIVE);
        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(tokens.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void ofRejectsNonPositiveLifetime() {
        assertThatThrownBy(() -> OAuthTokens.of("access", "refresh", NOW, 0, SaxoEnvironment.SIM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresInSeconds");
    }

    @Test
    void isExpiredHonoursSkew() {
        OAuthTokens tokens = OAuthTokens.of("access", "refresh", NOW, 1200, SaxoEnvironment.SIM);
        Instant expiry = NOW.plusSeconds(1200);

        // Well before expiry, no skew -> not expired.
        assertThat(tokens.isExpired(NOW, Duration.ZERO)).isFalse();

        // Exactly at expiry -> expired.
        assertThat(tokens.isExpired(expiry, Duration.ZERO)).isTrue();

        // 5 minutes before expiry but with a 6-minute skew -> treated as expired (proactive refresh).
        assertThat(tokens.isExpired(expiry.minusSeconds(300), Duration.ofMinutes(6))).isTrue();

        // 5 minutes before expiry with a 1-minute skew -> still valid.
        assertThat(tokens.isExpired(expiry.minusSeconds(300), Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void isExpiredRejectsNegativeSkew() {
        OAuthTokens tokens = OAuthTokens.of("access", "refresh", NOW, 1200, SaxoEnvironment.SIM);
        assertThatThrownBy(() -> tokens.isExpired(NOW, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("skew");
    }

    @Test
    void toStringRedactsSecretMaterial() {
        OAuthTokens tokens = new OAuthTokens("super-secret-access", "super-secret-refresh",
                NOW.plusSeconds(60), SaxoEnvironment.SIM);

        String rendered = tokens.toString();
        assertThat(rendered)
                .doesNotContain("super-secret-access")
                .doesNotContain("super-secret-refresh")
                .contains("accessToken=***")
                .contains("refreshToken=***")
                .contains("SIM");
    }
}
