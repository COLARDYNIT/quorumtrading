package com.quorumtrading.bootstrap.event;

import com.quorumtrading.application.event.ApiTokenUsedEvent;
import com.quorumtrading.application.port.out.ApiTokenRepository;
import com.quorumtrading.model.entity.ApiToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async CDI event handler that updates the last_used_at timestamp
 * when an API token is used for authentication.
 *
 * <p>Uses {@code @ObservesAsync} so token usage tracking does not
 * block the MCP request/response cycle.</p>
 */
@ApplicationScoped
public class ApiTokenUsedEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiTokenUsedEventHandler.class);

    @Inject
    ApiTokenRepository apiTokenRepository;

    @Transactional
    void onApiTokenUsed(@ObservesAsync ApiTokenUsedEvent event) {
        if (event.tokenId() == null) {
            return;
        }

        try {
            apiTokenRepository.findById(event.tokenId()).ifPresent(token -> {
                token.recordUsage();
                apiTokenRepository.save(token);
                LOG.debug("Updated last_used_at for API token {} (prefix={})", event.tokenId(), token.getTokenPrefix());
            });
        } catch (Exception e) {
            LOG.warn("Failed to update last_used_at for API token {}: {}", event.tokenId(), e.getMessage());
        }
    }
}
