package com.quorumtrading.adapter.rest.dto.response;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(description = "JVM and database health telemetry")
public record AdminHealthResponse(
    @Schema(description = "JVM heap memory used in bytes")
    long heapUsed,

    @Schema(description = "JVM heap memory max in bytes")
    long heapMax,

    @Schema(description = "System CPU usage (0.0-1.0)")
    double cpuUsage,

    @Schema(description = "Number of live threads")
    int threadCount,

    @Schema(description = "JVM uptime in seconds")
    long uptimeSeconds,

    @Schema(description = "Database connection pool stats")
    DbPoolStats dbPool
) {
    @RegisterForReflection
    @Schema(description = "Database connection pool statistics")
    public record DbPoolStats(
        int activeConnections,
        int idleConnections,
        int maxConnections
    ) {}
}
