package com.quorumtrading.application.orchestration;

import com.quorumtrading.model.orchestration.StrategyConfig;
import com.quorumtrading.model.orchestration.StrategyConfigParser;

/**
 * Application-layer adapter for strategy configuration parsing.
 *
 * This is a thin adapter that delegates to the domain-layer StrategyConfigParser.
 * The domain layer contains the pure business logic for strategy interpretation,
 * while this adapter provides application-level services (e.g., CDI injection,
 * transactional behavior, error handling specific to application concerns).
 *
 * <p><b>DDD Classification:</b></p>
 * This is an Application Service (adapter) - it bridges the domain service
 * to application-level concerns. The actual parsing logic is domain-level.
 *
 * @see StrategyConfigParser
 */
public class StrategyParser {

    private final StrategyConfigParser domainParser = new StrategyConfigParser();

    /**
     * Parses a strategy markdown file into a StrategyConfig object.
     * Delegates to the domain-layer StrategyConfigParser.
     *
     * @param markdown The markdown content with YAML frontmatter
     * @return StrategyConfig domain object
     * @throws IllegalArgumentException if markdown is invalid
     */
    public StrategyConfig parse(String markdown) {
        return domainParser.parse(markdown);
    }
}
