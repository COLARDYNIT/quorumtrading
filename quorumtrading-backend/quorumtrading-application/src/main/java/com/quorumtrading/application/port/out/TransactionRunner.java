package com.quorumtrading.application.port.out;

import java.util.function.Supplier;

/**
 * Runs a unit of work inside a single transaction owned by the persistence adapter.
 *
 * <p>Application services are POJOs by convention (wired in {@code ApplicationConfig}, enforced by
 * ArchUnit), so they cannot carry {@code @Transactional} themselves — an annotation on a POJO is
 * inert, and self-invocation would make it inert even on a bean. A use case that must make two
 * repository writes atomic therefore has to ask an adapter to open the boundary.</p>
 *
 * <p>This exists for the spend ledger. {@code RecordLLMUsageUseCase} writes a
 * {@code llm_usage_log} row and updates {@code user_budgets}; each repository method carries its
 * own {@code @Transactional}, so before this port the two committed independently and could
 * diverge — a budget charged with no matching ledger row, which is precisely the drift the V90/V91
 * migrations exist to repair after the fact.</p>
 *
 * <p>Implementations must join an existing transaction if one is already active, so nesting a call
 * inside an outer boundary does not commit early.</p>
 */
public interface TransactionRunner {

    /**
     * Execute {@code work} in a transaction and return its result.
     *
     * <p>Any exception propagates to the caller <em>after</em> the transaction has been rolled
     * back, so a caller that catches it is guaranteed none of the work was persisted.</p>
     *
     * @param work the unit of work; runs exactly once
     * @param <T>  the result type
     * @return whatever {@code work} returned
     */
    <T> T inTransaction(Supplier<T> work);
}
