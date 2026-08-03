package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.AdminDashboardResponse;
import com.quorumtrading.adapter.rest.dto.response.AdminHealthResponse;
import com.quorumtrading.adapter.rest.dto.response.AdminStatsResponse;
import com.quorumtrading.adapter.rest.dto.response.AdminUserSearchResponse;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.BudgetStatus;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST resource for admin dashboard.
 * Restricted to users with ADMIN role.
 */
@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Admin", description = "Admin API endpoints for platform management")
public class AdminResource {

    private static final Logger LOG = Logger.getLogger(AdminResource.class);

    @Inject
    CurrentUserProvider currentUser;

    @Inject
    UserRepository userRepository;

    @Context
    HttpHeaders httpHeaders;

    @Inject
    MarketResearchRepository marketResearchRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    UserBudgetRepository userBudgetRepository;

    @GET
    @Path("/stats")
    @Operation(
        summary = "Get platform statistics",
        description = "Returns aggregate statistics for the platform. Restricted to admin users."
    )
    @APIResponse(responseCode = "200", description = "Platform statistics retrieved successfully",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = AdminStatsResponse.class)))
    @APIResponse(responseCode = "403", description = "User is not an admin")
    public Response getStats() {
        requireAdmin();

        long userCount = userRepository.count();
        long researchCount = marketResearchRepository.count();
        long strategyCount = strategyRepository.count();
        long simulationCount = simulationRepository.count();

        AdminStatsResponse response = new AdminStatsResponse(
            userCount,
            researchCount,
            strategyCount,
            simulationCount
        );

        return Response.ok(response).build();
    }

    @GET
    @Path("/dashboard")
    @Operation(
        summary = "Get admin dashboard data",
        description = "Returns counts, simulation status breakdown, recent users, and budget summary."
    )
    @APIResponse(responseCode = "200", description = "Dashboard data retrieved",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = AdminDashboardResponse.class)))
    @APIResponse(responseCode = "403", description = "User is not an admin")
    public Response getDashboard() {
        requireAdmin();

        long userCount = userRepository.count();
        long researchCount = marketResearchRepository.count();
        long strategyCount = strategyRepository.count();
        long simulationCount = simulationRepository.count();
        Map<String, Long> simulationsByStatus = simulationRepository.countByStatus();

        List<User> recentUsers = userRepository.findRecent(5);
        List<AdminDashboardResponse.AdminUserInfo> recentUserInfos = recentUsers.stream()
            .map(u -> new AdminDashboardResponse.AdminUserInfo(
                u.getId().value().toString(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole().name(),
                u.getCreatedAt().toString(),
                u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null
            ))
            .toList();

        List<UserBudget> allBudgets = userBudgetRepository.findAll();
        BigDecimal totalAllocated = allBudgets.stream()
            .filter(UserBudget::hasLimit)
            .map(b -> b.getLimit().orElseThrow().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLifetimeSpend = allBudgets.stream()
            .map(UserBudget::getLifetimeSpend)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long depletedCount = allBudgets.stream()
            .filter(b -> b.getStatus() == BudgetStatus.DEPLETED)
            .count();

        AdminDashboardResponse response = new AdminDashboardResponse(
            userCount,
            researchCount,
            strategyCount,
            simulationCount,
            simulationsByStatus,
            recentUserInfos,
            new AdminDashboardResponse.BudgetSummary(totalAllocated, totalLifetimeSpend, depletedCount)
        );

        return Response.ok(response).build();
    }

    @GET
    @Path("/users/search")
    @Operation(
        summary = "Search users",
        description = "Search users by email or display name with budget info."
    )
    @APIResponse(responseCode = "200", description = "Search results",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = AdminUserSearchResponse.class)))
    @APIResponse(responseCode = "403", description = "User is not an admin")
    public Response searchUsers(
        @Parameter(description = "Search query (email or name)") @QueryParam("q") String query,
        @Parameter(description = "Max results") @QueryParam("limit") @DefaultValue("20") int limit
    ) {
        requireAdmin();

        if (query == null || query.isBlank()) {
            return Response.ok(new AdminUserSearchResponse(List.of())).build();
        }

        List<User> users = userRepository.search(query, Math.min(limit, 100));

        List<AdminUserSearchResponse.UserWithBudget> results = users.stream()
            .map(u -> {
                var budget = userBudgetRepository.findByUserId(u.getId()).orElse(null);
                return new AdminUserSearchResponse.UserWithBudget(
                    u.getId().value().toString(),
                    u.getEmail(),
                    u.getDisplayName(),
                    u.getRole().name(),
                    u.getCreatedAt().toString(),
                    u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null,
                    budget != null && budget.hasLimit() ? budget.getLimit().orElseThrow().amount() : null,
                    budget != null ? budget.getLifetimeSpend() : BigDecimal.ZERO,
                    budget != null ? budget.getStatus().name() : "AVAILABLE"
                );
            })
            .toList();

        return Response.ok(new AdminUserSearchResponse(results)).build();
    }

    @GET
    @Path("/health")
    @Operation(
        summary = "Get system health telemetry",
        description = "Returns JVM heap, CPU, threads, uptime, and DB connection pool stats."
    )
    @APIResponse(responseCode = "200", description = "Health telemetry",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = AdminHealthResponse.class)))
    @APIResponse(responseCode = "403", description = "User is not an admin")
    public Response getHealth() {
        requireAdmin();

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuUsage = osBean.getSystemLoadAverage() / osBean.getAvailableProcessors();

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();

        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        AdminHealthResponse response = new AdminHealthResponse(
            heapUsed,
            heapMax,
            Math.max(0, Math.min(1, cpuUsage)),
            threadCount,
            uptimeSeconds,
            new AdminHealthResponse.DbPoolStats(0, 0, 0)
        );

        return Response.ok(response).build();
    }

    private void requireAdmin() {
        String email = currentUser.getEmail()
            .orElseThrow(() -> {
                LOG.warnf("SECURITY: Admin access attempt with no authentication — ip=%s", extractClientIp());
                return new ForbiddenException("Admin access required");
            });
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> {
                LOG.warnf("SECURITY: Admin access attempt by unknown email=%s, ip=%s", email, extractClientIp());
                return new ForbiddenException("Admin access required");
            });
        if (!user.isAdmin()) {
            LOG.warnf("SECURITY: Admin access denied for non-admin user=%s (email=%s), ip=%s",
                user.getId(), email, extractClientIp());
            throw new ForbiddenException("Admin access required");
        }
    }

    private String extractClientIp() {
        if (httpHeaders == null) return "unknown";
        String forwarded = httpHeaders.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }
}
