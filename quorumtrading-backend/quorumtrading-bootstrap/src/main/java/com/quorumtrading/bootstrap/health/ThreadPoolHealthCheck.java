package com.quorumtrading.bootstrap.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Readiness
@ApplicationScoped
public class ThreadPoolHealthCheck implements HealthCheck {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolHealthCheck.class);

    private final MeterRegistry registry;
    private final ThreadPoolHealthConfig config;
    private final ThreadMXBean threadMXBean;

    // Track saturation start times per executor
    private final Map<String, Instant> saturationStartTimes = new ConcurrentHashMap<>();

    @Inject
    public ThreadPoolHealthCheck(MeterRegistry registry, ThreadPoolHealthConfig config) {
        this.registry = registry;
        this.config = config;
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("ThreadPool health check");

        boolean healthy = true;
        StringBuilder degradationReason = new StringBuilder();

        // Collect executor metrics from Micrometer
        healthy = collectExecutorMetrics(builder, degradationReason) && healthy;

        // Add JVM thread metrics
        addJvmThreadMetrics(builder);

        if (healthy) {
            return builder.up().build();
        } else {
            return builder.down()
                .withData("degradationReason", degradationReason.toString())
                .build();
        }
    }

    private boolean collectExecutorMetrics(HealthCheckResponseBuilder builder, StringBuilder degradationReason) {
        boolean healthy = true;

        for (Meter meter : registry.getMeters()) {
            String name = meter.getId().getName();

            // Look for executor metrics
            if (name.startsWith("executor.")) {
                String executorName = getExecutorName(meter);

                if (meter instanceof Gauge gauge) {
                    double value = gauge.value();
                    String metricKey = name;

                    // Add tag info to distinguish executors
                    if (executorName != null && !executorName.isEmpty()) {
                        metricKey = name.replace("executor.", "executor." + executorName + ".");
                    }

                    builder.withData(metricKey, formatValue(value));

                    // Check for queue saturation
                    if (name.equals("executor.queued") && executorName != null) {
                        double maxQueued = getMaxQueuedForExecutor(executorName);
                        if (maxQueued > 0) {
                            double utilization = (value / maxQueued) * 100;
                            builder.withData("executor." + executorName + ".queueUtilization",
                                String.format("%.1f%%", utilization));

                            if (utilization > config.queueUtilizationThreshold()) {
                                healthy = false;
                                degradationReason.append(String.format(
                                    "Executor '%s' queue utilization %.1f%% exceeds threshold %d%%. ",
                                    executorName, utilization, config.queueUtilizationThreshold()));
                                logger.warn("ThreadPool degraded: {} queue at {:.1f}%", executorName, utilization);
                            }
                        }
                    }

                    // Check for thread pool saturation
                    if (name.equals("executor.active") && executorName != null) {
                        double poolSize = getPoolSizeForExecutor(executorName);
                        if (poolSize > 0 && value >= poolSize) {
                            // All threads are active - track saturation duration
                            Instant saturationStart = saturationStartTimes.computeIfAbsent(
                                executorName, k -> Instant.now());
                            long saturationSeconds = java.time.Duration.between(saturationStart, Instant.now()).getSeconds();

                            builder.withData("executor." + executorName + ".saturationSeconds", saturationSeconds);

                            if (saturationSeconds > config.saturationDurationSeconds()) {
                                healthy = false;
                                degradationReason.append(String.format(
                                    "Executor '%s' saturated for %ds (threshold: %ds). ",
                                    executorName, saturationSeconds, config.saturationDurationSeconds()));
                                logger.warn("ThreadPool degraded: {} saturated for {}s", executorName, saturationSeconds);
                            }
                        } else {
                            // Not saturated anymore, reset tracking
                            saturationStartTimes.remove(executorName);
                        }
                    }

                    // Check for rejected tasks
                    if (name.equals("executor.rejected") && executorName != null) {
                        if (value > 0) {
                            healthy = false;
                            degradationReason.append(String.format(
                                "Executor '%s' has %.0f rejected tasks. ", executorName, value));
                            logger.warn("ThreadPool degraded: {} has {} rejected tasks", executorName, value);
                        }
                    }
                }
            }
        }

        return healthy;
    }

    private void addJvmThreadMetrics(HealthCheckResponseBuilder builder) {
        builder.withData("jvm.threads.live", threadMXBean.getThreadCount());
        builder.withData("jvm.threads.peak", threadMXBean.getPeakThreadCount());
        builder.withData("jvm.threads.daemon", threadMXBean.getDaemonThreadCount());

        // Count blocked threads
        long blockedCount = 0;
        for (long threadId : threadMXBean.getAllThreadIds()) {
            var threadInfo = threadMXBean.getThreadInfo(threadId);
            if (threadInfo != null && threadInfo.getThreadState() == Thread.State.BLOCKED) {
                blockedCount++;
            }
        }
        builder.withData("jvm.threads.blocked", blockedCount);
    }

    private String getExecutorName(Meter meter) {
        return meter.getId().getTag("name");
    }

    private double getMaxQueuedForExecutor(String executorName) {
        // Try to find the max queued metric for this executor
        return registry.getMeters().stream()
            .filter(m -> m.getId().getName().equals("executor.pool.max")
                && executorName.equals(m.getId().getTag("name")))
            .filter(m -> m instanceof Gauge)
            .map(m -> ((Gauge) m).value())
            .findFirst()
            .orElse(0.0);
    }

    private double getPoolSizeForExecutor(String executorName) {
        // Try to find the pool size metric for this executor
        return registry.getMeters().stream()
            .filter(m -> m.getId().getName().equals("executor.pool.size")
                && executorName.equals(m.getId().getTag("name")))
            .filter(m -> m instanceof Gauge)
            .map(m -> ((Gauge) m).value())
            .findFirst()
            .orElse(0.0);
    }

    private String formatValue(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }
}
