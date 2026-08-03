package com.quorumtrading.adapter.llm.router;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Scheduled task to periodically log provider metrics.
 * Logs summary statistics for all tracked providers.
 */
@ApplicationScoped
public class MetricsLogger {

    private static final Logger LOG = Logger.getLogger(MetricsLogger.class);

    private Map<String, ProviderMetrics> metricsMap;

    /**
     * Inject metrics map from router.
     */
    public void setMetricsMap(Map<String, ProviderMetrics> metricsMap) {
        this.metricsMap = metricsMap;
    }

    /**
     * Log metrics for all providers.
     * Runs every 5 minutes by default. Can be configured via application.properties.
     */
    @Scheduled(every = "300s")
    void logMetrics() {
        if (metricsMap == null || metricsMap.isEmpty()) {
            LOG.debug("No provider metrics to log");
            return;
        }

        LOG.info("=== LLM Provider Metrics ===");
        for (Map.Entry<String, ProviderMetrics> entry : metricsMap.entrySet()) {
            ProviderMetrics m = entry.getValue();
            LOG.infof("Provider: %s | Invocations: %d | Success: %d | Errors: %d | Error Rate: %.2f%% | Avg Response Time: %.0fms",
                entry.getKey(),
                m.getTotalInvocations(),
                m.getSuccessCount(),
                m.getErrorCount(),
                m.getErrorRate() * 100,
                m.getAverageResponseTimeMs());
        }
    }
}
