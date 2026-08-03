package com.quorumtrading.adapter.broker.alpaca.client;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;

/**
 * Adds Alpaca API authentication headers to all REST client requests.
 *
 * Alpaca requires two headers:
 * - APCA-API-KEY-ID: Your API key
 * - APCA-API-SECRET-KEY: Your API secret
 */
@ApplicationScoped
public class AlpacaAuthHeadersFactory implements ClientHeadersFactory {

    @ConfigProperty(name = "quorumtrading.broker.alpaca.api-key")
    String apiKey;

    @ConfigProperty(name = "quorumtrading.broker.alpaca.api-secret")
    String apiSecret;

    @Override
    public MultivaluedMap<String, String> update(MultivaluedMap<String, String> incomingHeaders,
                                                   MultivaluedMap<String, String> clientOutgoingHeaders) {
        MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();

        // Add Alpaca authentication headers
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.add("APCA-API-KEY-ID", apiKey);
        }

        if (apiSecret != null && !apiSecret.isEmpty()) {
            headers.add("APCA-API-SECRET-KEY", apiSecret);
        }

        return headers;
    }
}
