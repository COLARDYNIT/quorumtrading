package com.quorumtrading.application.usecase.marketdata;

/**
 * Input port for reading price-provider health — the market-data counterpart to
 * {@code GetLlmProviderStatusUseCase}.
 *
 * <p>Implementation is a POJO wired in the bootstrap {@code ApplicationConfig}.</p>
 */
public interface GetMarketDataStatusUseCase {

    /**
     * Per-provider state.
     *
     * @param probe whether to make a live call to each usable provider. Configuration alone cannot
     *     tell you whether a key still works; a revoked key looks exactly like a working one until
     *     something asks.
     * @return the status snapshot
     */
    MarketDataStatus get(boolean probe);
}
