package com.quorumtrading.application.service;

import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsubscribeTokenServiceTest {

    private static final String SECRET = "test-secret-at-least-sixteen-chars-long";

    @Test
    void roundTripsUserIdAndType() {
        UnsubscribeTokenService svc = new UnsubscribeTokenService(SECRET);
        UserId userId = new UserId(UUID.randomUUID());
        String token = svc.mint(userId, NotificationType.INVESTMENT_EXECUTED);

        Optional<UnsubscribeTokenService.Verified> verified = svc.verify(token);
        assertThat(verified).isPresent();
        assertThat(verified.get().userId()).isEqualTo(userId);
        assertThat(verified.get().type()).isEqualTo(NotificationType.INVESTMENT_EXECUTED);
    }

    @Test
    void rejectsTokenWithTamperedSignature() {
        UnsubscribeTokenService svc = new UnsubscribeTokenService(SECRET);
        String token = svc.mint(new UserId(UUID.randomUUID()), NotificationType.GENERIC);
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + "AAAAAAAA";
        assertThat(svc.verify(tampered)).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        UnsubscribeTokenService a = new UnsubscribeTokenService(SECRET);
        UnsubscribeTokenService b = new UnsubscribeTokenService("different-secret-also-long-enough");
        String token = a.mint(new UserId(UUID.randomUUID()), NotificationType.PLAN_PAUSED);
        assertThat(b.verify(token)).isEmpty();
    }

    @Test
    void rejectsMalformedToken() {
        UnsubscribeTokenService svc = new UnsubscribeTokenService(SECRET);
        assertThat(svc.verify(null)).isEmpty();
        assertThat(svc.verify("")).isEmpty();
        assertThat(svc.verify("no-dots-here")).isEmpty();
        assertThat(svc.verify("a.b")).isEmpty();
        assertThat(svc.verify("a.b.c.d.e")).isEmpty();
    }

    @Test
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new UnsubscribeTokenService("too-short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UnsubscribeTokenService(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void differentTypesProduceDifferentTokens() {
        UnsubscribeTokenService svc = new UnsubscribeTokenService(SECRET);
        UserId userId = new UserId(UUID.randomUUID());
        String t1 = svc.mint(userId, NotificationType.INVESTMENT_EXECUTED);
        String t2 = svc.mint(userId, NotificationType.INVESTMENT_SKIPPED);
        assertThat(t1).isNotEqualTo(t2);
    }
}
