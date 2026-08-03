package com.quorumtrading.bootstrap.health;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quorumtrading.health.threadpool")
public interface ThreadPoolHealthConfig {

    /**
     * Queue utilization percentage threshold (0-100).
     * Health check returns DOWN if any executor's queue utilization exceeds this value.
     */
    @WithDefault("80")
    int queueUtilizationThreshold();

    /**
     * Saturation duration in seconds.
     * Health check returns DOWN if all threads are active for longer than this duration.
     */
    @WithDefault("30")
    int saturationDurationSeconds();
}
