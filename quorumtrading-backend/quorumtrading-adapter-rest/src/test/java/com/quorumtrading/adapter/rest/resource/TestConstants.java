package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.model.valueobject.UserId;

import java.util.UUID;

/**
 * Shared test constants for REST adapter tests.
 */
public final class TestConstants {

    public static final UserId TEST_USER_ID = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private TestConstants() {
        // Utility class
    }
}
