package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.SimulationContributionJpaEntity;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.valueobject.ContributionId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SimulationContributionMapper {

    public SimulationContributionJpaEntity toJpaEntity(Contribution c) {
        SimulationContributionJpaEntity e = new SimulationContributionJpaEntity();
        e.setId(c.id().value());
        e.setSimulationId(c.simulationId().value());
        e.setAmount(c.amount().amount());
        e.setCurrency(c.amount().currency().name());
        e.setContributionDate(c.contributionDate());
        e.setSourcePlanId(c.sourcePlanId());
        e.setSourceAllocationId(c.sourceAllocationId());
        e.setCreatedAt(c.createdAt());
        return e;
    }

    public Contribution toDomain(SimulationContributionJpaEntity e) {
        Money amount = new Money(e.getAmount(), Money.Currency.valueOf(e.getCurrency()));
        return new Contribution(
                new ContributionId(e.getId()),
                new SimulationId(e.getSimulationId()),
                amount,
                e.getContributionDate(),
                e.getSourcePlanId(),
                e.getSourceAllocationId(),
                e.getCreatedAt()
        );
    }
}
