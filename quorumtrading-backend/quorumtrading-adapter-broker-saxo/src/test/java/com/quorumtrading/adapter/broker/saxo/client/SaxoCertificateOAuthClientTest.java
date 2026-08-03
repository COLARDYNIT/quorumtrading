package com.quorumtrading.adapter.broker.saxo.client;

import com.quorumtrading.model.enums.SaxoEnvironment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Unit test for the gated, disabled certificate-grant client (OA6-cert-stub).
 *
 * <p>Plain JUnit — no CDI/Quarkus boot — pinning the gate flag directly via
 * {@link SaxoCertificateOAuthClient#withCertEnabled(boolean)} so the disabled path is
 * asserted deterministically without config. Verifies the boundary: the cert path is a
 * disabled stub that throws a clear "CBA is institutional-only / not enabled for retail"
 * error and exchanges nothing.</p>
 */
class SaxoCertificateOAuthClientTest {

    @Test
    void exchangeCertificateThrowsClearDisabledErrorWhenGateOff() {
        SaxoCertificateOAuthClient client = SaxoCertificateOAuthClient.withCertEnabled(false);

        IllegalStateException ex = catchThrowableOfType(
                () -> client.exchangeCertificate(SaxoEnvironment.SIM),
                IllegalStateException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getMessage())
                .contains("certificate auth (CBA) is not enabled")
                .contains("INSTITUTIONAL")
                .contains("retail")
                .contains(SaxoCertificateOAuthClient.CERT_ENABLED_PROPERTY);
    }

    @Test
    void exchangeCertificateDisabledForLiveEnvironmentToo() {
        SaxoCertificateOAuthClient client = SaxoCertificateOAuthClient.withCertEnabled(false);

        assertThatThrownBy(() -> client.exchangeCertificate(SaxoEnvironment.LIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INSTITUTIONAL");
    }

    @Test
    void exchangeCertificateEnabledButDeferredSignalsBoundary() {
        SaxoCertificateOAuthClient client = SaxoCertificateOAuthClient.withCertEnabled(true);

        // Flag flipped on, but the real exchange is intentionally not built yet — must
        // signal the deferred boundary rather than return a fake token.
        assertThatThrownBy(() -> client.exchangeCertificate(SaxoEnvironment.SIM))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("not implemented yet");
    }

    @Test
    void exchangeCertificateRejectsNullEnvironment() {
        SaxoCertificateOAuthClient client = SaxoCertificateOAuthClient.withCertEnabled(false);

        assertThatThrownBy(() -> client.exchangeCertificate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("environment");
    }

    @Test
    void authCodeAndRefreshAreNotImplementedOnCertSkeleton() {
        SaxoCertificateOAuthClient client = SaxoCertificateOAuthClient.withCertEnabled(true);

        assertThatThrownBy(() ->
                client.exchangeAuthorizationCode("code", "https://app/cb", SaxoEnvironment.SIM))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() ->
                client.refreshAccessToken("refresh-token", SaxoEnvironment.SIM))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
