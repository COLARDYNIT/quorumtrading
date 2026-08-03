package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.MarketDataHorizonJpaEntity;
import com.quorumtrading.application.port.out.MarketDataHorizonRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.GranularityBucket;
import com.quorumtrading.model.enums.HorizonSource;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.marketdata.DataHorizon;
import com.quorumtrading.model.valueobject.Symbol;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Persistence adapter for measured provider data horizons.
 *
 * <p>Maps the domain {@link Symbol} to/from (ticker + investment_type) so the
 * value object reconstructs exactly. {@link #upsert} replaces the row for the
 * composite key.</p>
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class MarketDataHorizonRepositoryImpl implements MarketDataHorizonRepository {

    @Inject
    EntityManager entityManager;

    @Override
    public Optional<DataHorizon> find(String provider, Symbol symbol, EventType type, GranularityBucket bucket) {
        MarketDataHorizonJpaEntity.Key key = new MarketDataHorizonJpaEntity.Key(
                provider, symbol.ticker(), type.name(), bucket.name());
        return Optional.ofNullable(entityManager.find(MarketDataHorizonJpaEntity.class, key))
                .map(this::toDomain);
    }

    @Override
    public void upsert(DataHorizon horizon) {
        MarketDataHorizonJpaEntity.Key key = new MarketDataHorizonJpaEntity.Key(
                horizon.provider(),
                horizon.symbol().ticker(),
                horizon.dataType().name(),
                horizon.bucket().name());

        MarketDataHorizonJpaEntity entity = entityManager.find(MarketDataHorizonJpaEntity.class, key);
        boolean isNew = entity == null;
        if (isNew) {
            entity = new MarketDataHorizonJpaEntity();
            entity.provider = horizon.provider();
            entity.symbol = horizon.symbol().ticker();
            entity.eventType = horizon.dataType().name();
            entity.granularityBucket = horizon.bucket().name();
        }
        entity.investmentType = horizon.symbol().type().name();
        entity.earliestAvailable = horizon.earliestAvailable().orElse(null);
        entity.hasData = horizon.hasData();
        entity.probedAt = horizon.probedAt();
        entity.source = horizon.source().name();

        if (isNew) {
            entityManager.persist(entity);
        } else {
            entityManager.merge(entity);
        }
    }

    @Override
    public List<DataHorizon> findForSymbols(Set<Symbol> symbols, EventType type, GranularityBucket bucket) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        Set<String> tickers = symbols.stream().map(Symbol::ticker).collect(Collectors.toSet());

        return MarketDataHorizonJpaEntity.find(
                        "symbol IN :tickers AND eventType = :type AND granularityBucket = :bucket",
                        Parameters.with("tickers", tickers)
                                .and("type", type.name())
                                .and("bucket", bucket.name()))
                .list().stream()
                .map(e -> toDomain((MarketDataHorizonJpaEntity) e))
                .toList();
    }

    private DataHorizon toDomain(MarketDataHorizonJpaEntity e) {
        Symbol symbol = new Symbol(e.symbol, InvestmentType.valueOf(e.investmentType));
        Optional<LocalDate> earliest = e.hasData
                ? Optional.ofNullable(e.earliestAvailable)
                : Optional.empty();
        return new DataHorizon(
                e.provider,
                symbol,
                EventType.valueOf(e.eventType),
                GranularityBucket.valueOf(e.granularityBucket),
                earliest,
                e.probedAt,
                HorizonSource.valueOf(e.source));
    }
}
