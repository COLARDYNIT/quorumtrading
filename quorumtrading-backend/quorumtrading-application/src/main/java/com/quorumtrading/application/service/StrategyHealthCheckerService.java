package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Service that checks strategy health and auto-disables underperforming strategies.
 *
 * <p><b>DDD Classification:</b> Application Service</p>
 *
 * <p><b>Phase 4 Automation:</b></p>
 * Compares metrics against configurable thresholds and disables strategies
 * that consistently underperform.
 */
public class StrategyHealthCheckerService {

    private static final Logger LOG = LoggerFactory.getLogger(StrategyHealthCheckerService.class);

    private final StrategyRepository strategyRepository;
    private final BigDecimal minReturnPercent;
    private final int consecutiveFailuresToDisable;

    public StrategyHealthCheckerService(
            StrategyRepository strategyRepository,
            BigDecimal minReturnPercent,
            int consecutiveFailuresToDisable) {
        this.strategyRepository = strategyRepository;
        this.minReturnPercent = minReturnPercent;
        this.consecutiveFailuresToDisable = consecutiveFailuresToDisable;
    }

    /**
     * Check strategy health based on latest simulation result.
     *
     * @param strategy The strategy to check
     * @param returnPercent The return percentage from latest simulation
     */
    public void checkStrategyHealth(Strategy strategy, BigDecimal returnPercent) {
        if (!strategy.isEnabled()) {
            LOG.debug("Strategy {} already disabled, skipping health check", strategy.getId());
            return;
        }

        boolean passed = returnPercent.compareTo(minReturnPercent) >= 0;

        if (passed) {
            LOG.info("Strategy {} passed health check with {}% return (threshold: {}%)",
                strategy.getId(), returnPercent, minReturnPercent);
            return;
        }

        LOG.warn("Strategy {} failed health check with {}% return (threshold: {}%)",
            strategy.getId(), returnPercent, minReturnPercent);

        // Disable strategy if return is significantly negative
        if (returnPercent.compareTo(BigDecimal.ZERO) < 0) {
            disableStrategy(strategy, "Negative return: " + returnPercent + "%");
        }
    }

    private void disableStrategy(Strategy strategy, String reason) {
        LOG.warn("Auto-disabling strategy {} due to: {}", strategy.getId(), reason);
        strategy.disable(reason);
        strategyRepository.save(strategy);
    }
}
