package com.quorumtrading.adapter.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quorumtrading.application.port.out.FinancialDataFetcher;
import com.quorumtrading.application.port.out.FinancialDataPort;
import com.quorumtrading.model.marketdata.AnalystConsensus;
import com.quorumtrading.model.marketdata.EarningsRecord;
import com.quorumtrading.model.marketdata.FinancialMetrics;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-cached decorator for {@link FinancialDataPort}.
 * Delegates actual fetching to {@link FinancialDataFetcher} (Finnhub adapter)
 * and caches results in Redis for 24 hours.
 *
 * Architecture: This class is the only {@link FinancialDataPort} bean in the CDI container.
 * The Finnhub adapter implements {@link FinancialDataFetcher} (not the port), so there's
 * no ambiguity — clean hexagonal with no adapter-to-adapter dependency.
 */
@ApplicationScoped
@RegisterForReflection(targets = {
    FinancialMetrics.class,
    EarningsRecord.class,
    AnalystConsensus.class,
})
public class RedisFinancialDataCache implements FinancialDataPort {

    private static final Logger LOG = Logger.getLogger(RedisFinancialDataCache.class);
    private static final String KEY_PREFIX = "quorumtrading:financial:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    @Inject
    RedisDataSource redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    FinancialDataFetcher fetcher;

    @Override
    public Optional<FinancialMetrics> getFinancialMetrics(Symbol symbol) {
        String cacheKey = KEY_PREFIX + symbol.ticker();

        // Try Redis cache first
        try {
            ValueCommands<String, String> commands = redisDataSource.value(String.class);
            String cached = commands.get(cacheKey);
            if (cached != null) {
                LOG.debugf("FinancialDataCache: Redis hit for %s", symbol.ticker());
                FinancialMetrics metrics = objectMapper.readValue(cached, FinancialMetrics.class);
                return Optional.of(metrics);
            }
        } catch (Exception e) {
            LOG.warnf("FinancialDataCache: Redis read failed for %s: %s", symbol.ticker(), e.getMessage());
            // Fall through to fetch
        }

        // Cache miss — delegate to fetcher
        Optional<FinancialMetrics> result = fetcher.fetchFinancialMetrics(symbol);

        // Store in Redis
        result.ifPresent(metrics -> {
            try {
                String json = objectMapper.writeValueAsString(metrics);
                ValueCommands<String, String> commands = redisDataSource.value(String.class);
                commands.set(cacheKey, json);
                redisDataSource.key().expire(cacheKey, CACHE_TTL.toSeconds());
                LOG.infof("FinancialDataCache: cached %s in Redis (TTL=%s)", symbol.ticker(), CACHE_TTL);
            } catch (JsonProcessingException e) {
                LOG.warnf("FinancialDataCache: failed to serialize %s: %s", symbol.ticker(), e.getMessage());
            } catch (Exception e) {
                LOG.warnf("FinancialDataCache: Redis write failed for %s: %s", symbol.ticker(), e.getMessage());
            }
        });

        return result;
    }

    @Override
    public String getProviderName() {
        return "Cached(" + fetcher.getProviderName() + ")";
    }
}
