package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.HistoricalEventJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.HistoricalEventPersistenceMapper;
import com.quorumtrading.application.port.out.HistoricalEventRepository;
import com.quorumtrading.model.entity.HistoricalEvent;
import com.quorumtrading.model.enums.AssetClass;
import com.quorumtrading.model.enums.EventCategory;
import com.quorumtrading.model.enums.MarketSentiment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
@Transactional
public class HistoricalEventRepositoryImpl implements HistoricalEventRepository {

    private final EntityManager entityManager;
    private final HistoricalEventPersistenceMapper mapper;

    public HistoricalEventRepositoryImpl(EntityManager entityManager, HistoricalEventPersistenceMapper mapper) {
        this.entityManager = entityManager;
        this.mapper = mapper;
    }

    @Override
    public List<HistoricalEvent> findAll() {
        List<HistoricalEventJpaEntity> entities = entityManager
                .createQuery("SELECT e FROM HistoricalEventJpaEntity e ORDER BY e.startDate", HistoricalEventJpaEntity.class)
                .getResultList();
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<HistoricalEvent> findBySlug(String slug) {
        List<HistoricalEventJpaEntity> entities = entityManager
                .createQuery("SELECT e FROM HistoricalEventJpaEntity e WHERE e.slug = :slug", HistoricalEventJpaEntity.class)
                .setParameter("slug", slug)
                .getResultList();
        return entities.stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public List<HistoricalEvent> findByFilters(
            Optional<EventCategory> category,
            Optional<AssetClass> assetClass,
            Optional<MarketSentiment> sentiment) {

        var jpql = new StringBuilder("SELECT e FROM HistoricalEventJpaEntity e WHERE 1=1");
        var params = new ArrayList<Object[]>();

        category.ifPresent(c -> {
            jpql.append(" AND e.category = :category");
            params.add(new Object[]{"category", c.name()});
        });
        assetClass.ifPresent(a -> {
            jpql.append(" AND e.assetClass = :assetClass");
            params.add(new Object[]{"assetClass", a.name()});
        });
        sentiment.ifPresent(s -> {
            jpql.append(" AND e.sentiment = :sentiment");
            params.add(new Object[]{"sentiment", s.name()});
        });

        jpql.append(" ORDER BY e.startDate");

        var query = entityManager.createQuery(jpql.toString(), HistoricalEventJpaEntity.class);
        for (Object[] p : params) {
            query.setParameter((String) p[0], p[1]);
        }

        return query.getResultList().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<HistoricalEvent> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String pattern = "%" + keyword.toLowerCase() + "%";
        List<HistoricalEventJpaEntity> entities = entityManager
                .createQuery(
                        "SELECT e FROM HistoricalEventJpaEntity e " +
                        "WHERE LOWER(e.keywords) LIKE :pattern " +
                        "OR LOWER(e.name) LIKE :pattern " +
                        "OR LOWER(e.slug) LIKE :pattern " +
                        "ORDER BY e.startDate",
                        HistoricalEventJpaEntity.class)
                .setParameter("pattern", pattern)
                .getResultList();
        return entities.stream().map(mapper::toDomain).toList();
    }
}
