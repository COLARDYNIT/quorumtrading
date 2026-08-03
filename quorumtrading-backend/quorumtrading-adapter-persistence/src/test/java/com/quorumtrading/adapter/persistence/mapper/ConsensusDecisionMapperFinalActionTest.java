package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.ConsensusDecisionJpaEntity;
import com.quorumtrading.application.orchestration.agents.ConsensusActionParser;
import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.enums.ConsensusActionType;
import com.quorumtrading.model.enums.ConsensusMethod;
import com.quorumtrading.model.valueobject.SimulationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parse-on-read / raw-write round-trip for {@code final_action}: the JSONB column keeps
 * storing the exact raw string (write path byte-identical), while the domain gains a
 * typed view derived once in the mapper. Covers all four historical row shapes:
 * W1 (system JSON), W2 (arbitrary LLM JSON), W3 (legacy no-market-data SKIP literal
 * with "reason"), W4 (V3-era plain scalar rows).
 */
class ConsensusDecisionMapperFinalActionTest {

    private final ConsensusDecisionMapper mapper = new ConsensusDecisionMapper();

    private ConsensusDecisionJpaEntity entityWithFinalAction(String finalAction) {
        ConsensusDecisionJpaEntity entity = new ConsensusDecisionJpaEntity();
        entity.simulationId = UUID.randomUUID();
        entity.decisionTimestamp = Instant.now();
        entity.consensusMethod = "MAJORITY";
        entity.finalAction = finalAction;
        entity.executed = false;
        return entity;
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // W1: system-generated JSON (fixed key order via Jackson)
        "{\"action\":\"HOLD\",\"symbol\":\"AAPL\",\"quantity\":0.0,\"confidence\":0.5,\"reasoning\":\"tie\"}",
        // W2: arbitrary (cleaned) LLM JSON
        "{\"action\": \"BUY\", \"symbol\": \"NVDA\", \"quantity\": 2, \"confidence\": 0.9, \"reasoning\": \"up\", \"extra\": true}",
        // W3: legacy no-market-data SKIP literal ("reason", no symbol/quantity)
        "{\"action\": \"SKIP\", \"reason\": \"No market data available\"}",
        // W4: legacy V3-era plain scalar
        "BUY",
        // unparseable garbage must survive a round-trip untouched too
        "{broken json"
    })
    void rawStringRoundTripsByteIdentically(String raw) {
        ConsensusDecision domain = mapper.toDomain(entityWithFinalAction(raw));
        assertThat(domain.getFinalAction()).isEqualTo(raw);

        ConsensusDecisionJpaEntity written = mapper.toJpaEntity(domain);
        assertThat(written.finalAction).isEqualTo(raw);
    }

    @Test
    void readDerivesTypedView() {
        ConsensusDecision w3 = mapper.toDomain(entityWithFinalAction(
            "{\"action\": \"SKIP\", \"reason\": \"No market data available\"}"));
        assertThat(w3.getAction().type()).isEqualTo(ConsensusActionType.SKIP);
        assertThat(w3.getAction().reasoning()).isEqualTo("No market data available");

        ConsensusDecision legacyScalar = mapper.toDomain(entityWithFinalAction("BUY"));
        assertThat(legacyScalar.getAction().type()).isEqualTo(ConsensusActionType.BUY);

        ConsensusDecision garbage = mapper.toDomain(entityWithFinalAction("{broken json"));
        assertThat(garbage.getAction().type()).isEqualTo(ConsensusActionType.UNKNOWN);
    }

    @Test
    void typedDomainWritesRawForm() {
        ConsensusDecision decision = new ConsensusDecision(
            new SimulationId(UUID.randomUUID()), Instant.now(), ConsensusMethod.MAJORITY,
            ConsensusActionParser.hold("AAPL", 0.0, 0.5, "tie"));

        ConsensusDecisionJpaEntity entity = mapper.toJpaEntity(decision);

        assertThat(entity.finalAction)
            .isEqualTo("{\"action\":\"HOLD\",\"symbol\":\"AAPL\",\"quantity\":0.0,\"confidence\":0.5,\"reasoning\":\"tie\"}");
    }
}
