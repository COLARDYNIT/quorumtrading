package com.quorumtrading.adapter.marketdata.tiingo.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * Adds Tiingo API authentication header to all REST client requests.
 *
 * Tiingo uses a single Token-based auth header:
 * Authorization: Token &lt;api-token&gt;
 */
@ApplicationScoped
public class TiingoAuthHeadersFactory implements ClientHeadersFactory {

    @ConfigProperty(name = "quorumtrading.marketdata.tiingo.api-token")
    String apiToken;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                   MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("Authorization", "Token " + apiToken);
        return headers;
    }
}
