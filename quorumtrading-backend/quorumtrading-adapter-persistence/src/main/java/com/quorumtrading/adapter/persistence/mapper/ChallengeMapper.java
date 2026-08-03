package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.ChallengeJpaEntity;
import com.quorumtrading.model.entity.Challenge;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ChallengeMapper {

    public ChallengeJpaEntity toJpaEntity(Challenge challenge) {
        ChallengeJpaEntity entity = new ChallengeJpaEntity();
        if (challenge.getChallengeId() != null) entity.setId(challenge.getChallengeId().value());
        entity.setSlug(challenge.getSlug());
        entity.setName(challenge.getName());
        entity.setDescription(challenge.getDescription());
        entity.setTicker(challenge.getTicker().ticker());
        entity.setInitialCash(challenge.getInitialCash().amount());
        entity.setSimulationStartDate(challenge.getSimulationStartDate());
        entity.setSimulationEndDate(challenge.getSimulationEndDate());
        entity.setParticipationOpen(challenge.getParticipationOpen());
        entity.setParticipationClose(challenge.getParticipationClose());
        entity.setCreatedAt(challenge.getCreatedAt());
        entity.setCreatedBy(challenge.getCreatedBy().value());
        entity.setStrategiesPublic(challenge.isStrategiesPublic());
        return entity;
    }

    public Challenge toDomain(ChallengeJpaEntity entity) {
        ChallengeId id = new ChallengeId(entity.getId());
        UserId createdBy = new UserId(entity.getCreatedBy());
        String ticker = entity.getTicker();
        InvestmentType type = inferInvestmentType(ticker);
        Symbol symbol = new Symbol(ticker, type);
        Money initialCash = new Money(entity.getInitialCash(), Money.Currency.USD);

        return Challenge.reconstitute(
            id, entity.getSlug(), entity.getName(), entity.getDescription(), symbol, initialCash,
            entity.getSimulationStartDate(), entity.getSimulationEndDate(),
            entity.getParticipationOpen(), entity.getParticipationClose(),
            entity.getCreatedAt(), createdBy, entity.isStrategiesPublic()
        );
    }

    private InvestmentType inferInvestmentType(String ticker) {
        return switch (ticker) {
            case "BTC", "ETH", "SOL", "DOGE", "ADA", "XRP", "DOT", "AVAX", "MATIC", "LINK" ->
                InvestmentType.CRYPTO;
            default -> InvestmentType.STOCK;
        };
    }
}
