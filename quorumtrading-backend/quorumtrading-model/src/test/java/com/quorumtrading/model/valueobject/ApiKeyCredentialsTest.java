package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyCredentialsTest {

    @Test
    void shouldCreateWithValidCredentials() {
        ApiKeyCredentials creds = new ApiKeyCredentials("api-key", "api-secret");
        assertEquals("api-key", creds.apiKey());
        assertEquals("api-secret", creds.apiSecret());
    }

    @Test
    void shouldBeABrokerCredentials() {
        BrokerCredentials creds = new ApiKeyCredentials("api-key", "api-secret");
        assertInstanceOf(ApiKeyCredentials.class, creds);
    }

    @Test
    void shouldThrowOnNullApiKey() {
        assertThrows(IllegalArgumentException.class, () -> new ApiKeyCredentials(null, "secret"));
    }

    @Test
    void shouldThrowOnBlankApiKey() {
        assertThrows(IllegalArgumentException.class, () -> new ApiKeyCredentials("  ", "secret"));
    }

    @Test
    void shouldThrowOnNullApiSecret() {
        assertThrows(IllegalArgumentException.class, () -> new ApiKeyCredentials("api-key", null));
    }

    @Test
    void shouldThrowOnBlankApiSecret() {
        assertThrows(IllegalArgumentException.class, () -> new ApiKeyCredentials("api-key", "  "));
    }

    @Test
    void shouldHaveValueEquality() {
        ApiKeyCredentials a = new ApiKeyCredentials("api-key", "api-secret");
        ApiKeyCredentials b = new ApiKeyCredentials("api-key", "api-secret");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void shouldMaskInToString() {
        ApiKeyCredentials creds = new ApiKeyCredentials("my-api-key", "my-secret");
        String str = creds.toString();
        assertFalse(str.contains("my-api-key"));
        assertFalse(str.contains("my-secret"));
        assertTrue(str.contains("***"));
    }
}
