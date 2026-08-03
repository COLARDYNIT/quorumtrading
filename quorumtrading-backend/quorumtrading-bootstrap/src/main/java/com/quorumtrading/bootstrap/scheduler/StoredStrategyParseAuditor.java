package com.quorumtrading.bootstrap.scheduler;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.orchestration.StrategyConfigParser;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Reports, at boot, every stored strategy that no longer parses.
 *
 * <p>The strategy parser was tightened to reject input it does not honour — unknown frontmatter
 * keys, unknown agent roles, an unrecognised {@code consensus_method} — because silently ignoring
 * them meant a strategy ran on defaults nobody chose. That is the right trade, but it is
 * retroactive: a strategy saved under the lenient parser and stored with {@code TECHNCIAL} in its
 * agent list used to run (as all three agents, at triple the cost); now it fails.</p>
 *
 * <p>Without this, the owner's first notice would be a FAILED simulation with a parse error, some
 * time after an upgrade that looked clean. Every affected strategy is named here instead, at boot,
 * with the reason and the owner — so an operator can fix them before a user trips over one.</p>
 *
 * <p>Read-only and never fatal: a strategy that fails to parse is logged, not modified, and a
 * failure of the audit itself must not stop the application from starting.</p>
 */
@ApplicationScoped
public class StoredStrategyParseAuditor {

    private static final Logger LOG = Logger.getLogger(StoredStrategyParseAuditor.class);

    /** Enough detail to identify the problem without turning the boot log into a data dump. */
    private static final int MAX_REPORTED = 50;

    @Inject
    StrategyRepository strategyRepository;

    private final StrategyConfigParser parser = new StrategyConfigParser();

    void onStartup(@Observes StartupEvent event) {
        try {
            audit();
        } catch (Exception e) {
            // A diagnostic must never be the reason the application will not boot.
            LOG.warnf(e, "Stored-strategy parse audit could not run; skipping it");
        }
    }

    private void audit() {
        List<Strategy> strategies = strategyRepository.findAll();
        if (strategies.isEmpty()) {
            return;
        }

        List<String> broken = new ArrayList<>();
        for (Strategy strategy : strategies) {
            try {
                parser.parse(strategy.getContent());
            } catch (Exception e) {
                broken.add(String.format("  - %s (%s, owner %s): %s",
                        strategy.getName(),
                        strategy.getId().value(),
                        strategy.getOwnerId() != null ? strategy.getOwnerId().value() : "unknown",
                        firstLineOf(e.getMessage())));
            }
        }

        if (broken.isEmpty()) {
            LOG.infof("Strategy audit: all %d stored strategies parse cleanly", strategies.size());
            return;
        }

        LOG.warnf("Strategy audit: %d of %d stored strategies no longer parse and will FAIL any "
                + "simulation that uses them. The parser now rejects frontmatter it cannot honour "
                + "(unknown keys, unknown agent roles, unrecognised consensus_method) instead of "
                + "ignoring it. Edit each strategy to fix the reported problem:%n%s",
                broken.size(), strategies.size(),
                String.join(System.lineSeparator(),
                        broken.size() > MAX_REPORTED ? broken.subList(0, MAX_REPORTED) : broken));

        if (broken.size() > MAX_REPORTED) {
            LOG.warnf("Strategy audit: %d further affected strategies not listed",
                    broken.size() - MAX_REPORTED);
        }
    }

    private String firstLineOf(String message) {
        if (message == null) {
            return "no detail";
        }
        int newline = message.indexOf(System.lineSeparator());
        return newline < 0 ? message : message.substring(0, newline);
    }
}
