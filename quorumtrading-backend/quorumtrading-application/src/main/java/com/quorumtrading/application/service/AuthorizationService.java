package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.UserId;

/**
 * Service for authorization checks on domain entities.
 * Determines whether a user can view, edit, or delete resources based on ownership and visibility.
 * <p>
 * Central authorization logic used by all application services.
 * All three entry flows (REST, MCP, ChatToolExecutor) are covered
 * because authorization is enforced at the use case layer.
 * </p>
 *
 * <p><strong>A null viewer is denied, not exempted.</strong> Callers acting on the platform's
 * own behalf (schedulers, startup recovery, event handlers) must pass {@link UserId#SYSTEM}
 * explicitly. Services previously wrapped these calls in {@code if (userId != null)}, which
 * turned an unresolved caller into a silent bypass rather than a rejection.</p>
 */
public class AuthorizationService {

    // ===== Strategy =====

    public boolean canView(UserId viewer, Strategy strategy) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        if (strategy.isPublicVisible()) {
            return true;
        }
        return strategy.getOwnerId().equals(viewer);
    }

    public boolean canEdit(UserId viewer, Strategy strategy) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        return strategy.getOwnerId().equals(viewer);
    }

    public boolean canDelete(UserId viewer, Strategy strategy) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        return strategy.getOwnerId().equals(viewer);
    }

    // ===== Simulation (ownership derived from Strategy) =====

    /**
     * Read access to a simulation, without a resolved broker account.
     *
     * <p>Fails closed: the parent strategy being public does <em>not</em> open the simulation
     * up unless we can prove the run is mock-backed. See the four-argument overload.</p>
     */
    public boolean canView(UserId viewer, Simulation simulation, Strategy strategy) {
        return canView(viewer, simulation, strategy, null);
    }

    /**
     * Read access to a simulation.
     *
     * <p>Publishing a strategy shares its <em>backtests</em> — that is the point of a public
     * strategy library. It must never share a run wired to a real broker account: a paper or
     * live simulation exposes actual positions, cash movements and spend on someone's real
     * account. Strategy visibility is not consent to that.</p>
     *
     * <p>Note {@code isBacktest()} is only {@code endDate != null}, so a genuine paper/live run
     * with an end date reports {@code true} — it is not a safe proxy for "no real money". The
     * broker account is the authority, and an account we cannot resolve counts as real.</p>
     *
     * @param account the simulation's broker account, or {@code null} if unresolved
     */
    public boolean canView(UserId viewer, Simulation simulation, Strategy strategy, BrokerAccount account) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        if (isOwner(viewer, simulation, strategy)) {
            return true;
        }
        if (!strategy.isPublicVisible()) {
            return false;
        }
        return isMockBacked(simulation, account);
    }

    /**
     * True only when the run demonstrably moves no real money.
     *
     * <p>A simulation with no broker account at all predates per-account routing and replayed
     * through the global mock broker, so a bounded (backtest) one is safe to share. Anything
     * else needs a resolved MOCK account.</p>
     */
    private boolean isMockBacked(Simulation simulation, BrokerAccount account) {
        if (account != null) {
            return account.isMock();
        }
        return simulation.getBrokerAccountId() == null && simulation.isBacktest();
    }

    /**
     * Write access is anchored on the simulation's own owner where available, falling back to
     * the strategy's owner for rows predating the denormalised column. Deriving it purely from
     * the strategy means transferring or publishing a strategy would move write rights over
     * historical runs.
     */
    private boolean isOwner(UserId viewer, Simulation simulation, Strategy strategy) {
        UserId owner = simulation.getOwnerUserId() != null
            ? simulation.getOwnerUserId()
            : strategy.getOwnerId();
        return owner != null && owner.equals(viewer);
    }

    public boolean canDelete(UserId viewer, Simulation simulation, Strategy strategy) {
        return canMutate(viewer, simulation, strategy);
    }

    public boolean canEdit(UserId viewer, Simulation simulation, Strategy strategy) {
        return canMutate(viewer, simulation, strategy);
    }

    /** Mutating a simulation is owner-only. Public visibility grants reads, never writes. */
    private boolean canMutate(UserId viewer, Simulation simulation, Strategy strategy) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        return isOwner(viewer, simulation, strategy);
    }

    // ===== Benchmark =====

    public boolean canView(UserId viewer, Benchmark benchmark) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        if (benchmark.getOwnerId() == null) {
            return true; // Legacy benchmarks without owner are accessible
        }
        return benchmark.getOwnerId().equals(viewer);
    }

    public boolean canDelete(UserId viewer, Benchmark benchmark) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        // An unowned benchmark is readable by anyone (see canView) but deliberately NOT
        // deletable: "we cannot tell who owns this" is a reason to withhold a destructive
        // action, not to grant it to every authenticated user.
        if (benchmark.getOwnerId() == null) {
            return false;
        }
        return benchmark.getOwnerId().equals(viewer);
    }

    // ===== MarketResearch =====

    public boolean canView(UserId viewer, MarketResearch research) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        return research.getOwnerId().equals(viewer);
    }

    public boolean canEdit(UserId viewer, MarketResearch research) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        return research.getOwnerId().equals(viewer);
    }

    public boolean canDelete(UserId viewer, MarketResearch research) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        return research.getOwnerId().equals(viewer);
    }

    // ===== BrokerAccount =====

    public boolean canView(UserId viewer, BrokerAccount account) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        return account.getUserId().equals(viewer);
    }

    public boolean canEdit(UserId viewer, BrokerAccount account) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        return account.getUserId().equals(viewer);
    }

    public boolean canDelete(UserId viewer, BrokerAccount account) {
        if (viewer == null) {
            return false;
        }
        if (viewer.isSystem()) {
            return true;
        }
        return account.getUserId().equals(viewer);
    }

    // ===== Require helpers (throw AccessDeniedException) =====

    public void requireView(UserId viewer, Strategy strategy) {
        if (!canView(viewer, strategy)) {
            throw new AccessDeniedException(viewer, "Strategy", strategy.getId().value().toString());
        }
    }

    public void requireEdit(UserId viewer, Strategy strategy) {
        if (!canEdit(viewer, strategy)) {
            throw new AccessDeniedException(viewer, "Strategy", strategy.getId().value().toString());
        }
    }

    public void requireDelete(UserId viewer, Strategy strategy) {
        if (!canDelete(viewer, strategy)) {
            throw new AccessDeniedException(viewer, "Strategy", strategy.getId().value().toString());
        }
    }

    public void requireView(UserId viewer, Simulation simulation, Strategy strategy) {
        if (!canView(viewer, simulation, strategy)) {
            throw new AccessDeniedException(viewer, "Simulation", simulation.getSimulationId().value().toString());
        }
    }

    public void requireDelete(UserId viewer, Simulation simulation, Strategy strategy) {
        if (!canDelete(viewer, simulation, strategy)) {
            throw new AccessDeniedException(viewer, "Simulation", simulation.getSimulationId().value().toString());
        }
    }

    public void requireEdit(UserId viewer, Simulation simulation, Strategy strategy) {
        if (!canEdit(viewer, simulation, strategy)) {
            throw new AccessDeniedException(viewer, "Simulation", simulation.getSimulationId().value().toString());
        }
    }

    public void requireView(UserId viewer, Benchmark benchmark) {
        if (!canView(viewer, benchmark)) {
            throw new AccessDeniedException(viewer, "Benchmark", benchmark.getBenchmarkId().value().toString());
        }
    }

    public void requireDelete(UserId viewer, Benchmark benchmark) {
        if (!canDelete(viewer, benchmark)) {
            throw new AccessDeniedException(viewer, "Benchmark", benchmark.getBenchmarkId().value().toString());
        }
    }

    public void requireView(UserId viewer, MarketResearch research) {
        if (!canView(viewer, research)) {
            throw new AccessDeniedException(viewer, "MarketResearch", research.getId().toString());
        }
    }

    public void requireEdit(UserId viewer, MarketResearch research) {
        if (!canEdit(viewer, research)) {
            throw new AccessDeniedException(viewer, "MarketResearch", research.getId().toString());
        }
    }

    public void requireDelete(UserId viewer, MarketResearch research) {
        if (!canDelete(viewer, research)) {
            throw new AccessDeniedException(viewer, "MarketResearch", research.getId().toString());
        }
    }

    public void requireView(UserId viewer, BrokerAccount account) {
        if (!canView(viewer, account)) {
            throw new AccessDeniedException(viewer, "BrokerAccount", account.getId().value().toString());
        }
    }

    public void requireEdit(UserId viewer, BrokerAccount account) {
        if (!canEdit(viewer, account)) {
            throw new AccessDeniedException(viewer, "BrokerAccount", account.getId().value().toString());
        }
    }

    public void requireDelete(UserId viewer, BrokerAccount account) {
        if (!canDelete(viewer, account)) {
            throw new AccessDeniedException(viewer, "BrokerAccount", account.getId().value().toString());
        }
    }
}
