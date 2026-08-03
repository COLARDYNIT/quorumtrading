package com.quorumtrading.adapter.persistence;

import com.quorumtrading.application.port.out.TransactionRunner;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.function.Supplier;

/**
 * Opens a JTA transaction on behalf of an application-layer POJO.
 *
 * <p>{@code Transactional.TxType.REQUIRED} (the default) means an already-active transaction is
 * joined rather than replaced, so the repository calls made inside {@code work} — each carrying
 * its own {@code @Transactional} — enlist in this one boundary instead of committing separately.
 * That is the whole point: it is what makes a ledger row and the budget update it pays for either
 * both land or neither.</p>
 */
@ApplicationScoped
public class TransactionRunnerImpl implements TransactionRunner {

    @Override
    @Transactional
    public <T> T inTransaction(Supplier<T> work) {
        return work.get();
    }
}
