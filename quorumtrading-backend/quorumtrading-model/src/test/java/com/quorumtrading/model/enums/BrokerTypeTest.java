package com.quorumtrading.model.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrokerTypeTest {

    @Test
    void shouldHaveExpectedValues() {
        assertEquals(5, BrokerType.values().length);
        assertNotNull(BrokerType.MOCK);
        assertNotNull(BrokerType.ALPACA_PAPER);
        assertNotNull(BrokerType.ALPACA_LIVE);
        assertNotNull(BrokerType.SAXO_SIM);
        assertNotNull(BrokerType.SAXO_LIVE);
    }

    @Test
    void requiresCredentialsMatrix() {
        assertFalse(BrokerType.MOCK.requiresCredentials());
        assertTrue(BrokerType.ALPACA_PAPER.requiresCredentials());
        assertTrue(BrokerType.ALPACA_LIVE.requiresCredentials());
        assertTrue(BrokerType.SAXO_SIM.requiresCredentials());
        assertTrue(BrokerType.SAXO_LIVE.requiresCredentials());
    }

    @Test
    void supportsFractionalSharesMatrix() {
        assertTrue(BrokerType.MOCK.supportsFractionalShares());
        assertTrue(BrokerType.ALPACA_PAPER.supportsFractionalShares());
        assertTrue(BrokerType.ALPACA_LIVE.supportsFractionalShares());
        // Saxo equities/ETFs are whole-share only (cash residue carried forward).
        assertFalse(BrokerType.SAXO_SIM.supportsFractionalShares());
        assertFalse(BrokerType.SAXO_LIVE.supportsFractionalShares());
    }

    @Test
    void isLiveMatrix() {
        assertFalse(BrokerType.MOCK.isLive());
        assertFalse(BrokerType.ALPACA_PAPER.isLive());
        assertTrue(BrokerType.ALPACA_LIVE.isLive());
        assertFalse(BrokerType.SAXO_SIM.isLive());
        assertTrue(BrokerType.SAXO_LIVE.isLive());
    }

    @Test
    void isSaxoMatrix() {
        assertFalse(BrokerType.MOCK.isSaxo());
        assertFalse(BrokerType.ALPACA_PAPER.isSaxo());
        assertFalse(BrokerType.ALPACA_LIVE.isSaxo());
        assertTrue(BrokerType.SAXO_SIM.isSaxo());
        assertTrue(BrokerType.SAXO_LIVE.isSaxo());
    }

    @Test
    void isAlpacaMatrix() {
        assertFalse(BrokerType.MOCK.isAlpaca());
        assertTrue(BrokerType.ALPACA_PAPER.isAlpaca());
        assertTrue(BrokerType.ALPACA_LIVE.isAlpaca());
        assertFalse(BrokerType.SAXO_SIM.isAlpaca());
        assertFalse(BrokerType.SAXO_LIVE.isAlpaca());
    }

    @Test
    void usesOAuthMatrix() {
        assertFalse(BrokerType.MOCK.usesOAuth());
        assertFalse(BrokerType.ALPACA_PAPER.usesOAuth());
        assertFalse(BrokerType.ALPACA_LIVE.usesOAuth());
        assertTrue(BrokerType.SAXO_SIM.usesOAuth());
        assertTrue(BrokerType.SAXO_LIVE.usesOAuth());
    }

    @Test
    void saxoEnvironmentHasSimAndLive() {
        assertEquals(2, SaxoEnvironment.values().length);
        assertNotNull(SaxoEnvironment.SIM);
        assertNotNull(SaxoEnvironment.LIVE);
    }
}
