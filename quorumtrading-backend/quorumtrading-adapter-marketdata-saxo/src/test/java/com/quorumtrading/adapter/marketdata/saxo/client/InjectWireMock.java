package com.quorumtrading.adapter.marketdata.saxo.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link com.github.tomakehurst.wiremock.WireMockServer} field in a test to receive
 * the running WireMock instance started by {@link SaxoOpenApiWireMockResource}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectWireMock {
}
