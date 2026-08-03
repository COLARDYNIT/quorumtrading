package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.MockBrokerOrderJpaEntity;
import com.quorumtrading.application.port.out.MockBrokerStatePort;
import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SimulationId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter for MockBrokerAdapter state.
 * Stores order history in the mock_broker_orders table for cluster-safe access.
 */
@ApplicationScoped
public class MockBrokerStatePersistenceAdapter implements MockBrokerStatePort {

    private static final Logger log = LoggerFactory.getLogger(MockBrokerStatePersistenceAdapter.class);

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public void saveOrder(ExecuteTradeCommand command, TradeResult result) {
        MockBrokerOrderJpaEntity entity = new MockBrokerOrderJpaEntity();
        entity.setSimulationId(command.simulationId().value());
        entity.setOrderId(result.orderId().value().toString());
        entity.setSymbol(command.symbol().ticker());
        entity.setSide(command.action().name());
        entity.setQuantity(command.quantity());
        entity.setPrice(result.executionPrice() != null ? result.executionPrice().amount() : BigDecimal.ZERO);
        entity.setStatus(result.status().name());
        entity.setCreatedAt(result.executedAt());

        entityManager.persist(entity);

        log.debug("Saved order {} for simulation {}",
            result.orderId().value().toString().substring(0, 8),
            command.simulationId().value().toString().substring(0, 8));
    }

    @Override
    @Transactional
    public Optional<TradeResult> findOrder(OrderId orderId) {
        try {
            MockBrokerOrderJpaEntity entity = entityManager
                .createQuery(
                    "SELECT o FROM MockBrokerOrderJpaEntity o WHERE o.orderId = :orderId",
                    MockBrokerOrderJpaEntity.class)
                .setParameter("orderId", orderId.value().toString())
                .getSingleResult();

            return Optional.of(toTradeResult(entity));
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void deleteOrdersBySimulation(SimulationId simulationId) {
        int deleted = entityManager.createQuery(
                "DELETE FROM MockBrokerOrderJpaEntity o WHERE o.simulationId = :simId")
            .setParameter("simId", simulationId.value())
            .executeUpdate();

        if (deleted > 0) {
            log.debug("Deleted {} orders for simulation {}", deleted,
                simulationId.value().toString().substring(0, 8));
        }
    }

    private TradeResult toTradeResult(MockBrokerOrderJpaEntity entity) {
        OrderId orderId = new OrderId(UUID.fromString(entity.getOrderId()));
        Money price = Money.usd(entity.getPrice());

        String status = entity.getStatus();
        if ("EXECUTED".equals(status)) {
            return TradeResult.executed(orderId, price, entity.getQuantity(), entity.getCreatedAt());
        } else {
            return TradeResult.rejected(orderId, "Order was rejected", entity.getCreatedAt());
        }
    }
}
