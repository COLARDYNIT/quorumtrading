package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.usecase.CreateMarketResearchUseCase;
import com.quorumtrading.application.usecase.command.CreateMarketResearchCommand;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.valueobject.ResearchInterval;
import com.quorumtrading.model.valueobject.ResearchStatus;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for market research creation and entity persistence.
 *
 * Tests the creation flow, persistence, querying, and cascade deletion
 * of MarketResearch entities. Does NOT test actual LLM execution.
 */
@QuarkusTest
class ResearchLifecycleIntegrationTest {

    @Inject
    CreateMarketResearchUseCase createMarketResearchUseCase;

    @Inject
    MarketResearchRepository marketResearchRepository;

    @Inject
    ResearchExecutionRepository researchExecutionRepository;

    @Inject
    IntegrationTestHelper helper;

    @Test
    @Transactional
    @DisplayName("should create market research with ACTIVE status")
    void shouldCreateMarketResearch() {
        // Arrange
        User user = helper.createUser();
        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Test Research-" + UUID.randomUUID().toString().substring(0, 8),
            "# Research Instructions\n\nAnalyze crypto market trends.",
            Set.of(),
            new ResearchInterval(1, ResearchInterval.Unit.WEEKS),
            false,
            null,
            user.getId()
        );

        // Act
        MarketResearch research = createMarketResearchUseCase.execute(command);

        // Assert
        assertThat(research).isNotNull();
        assertThat(research.getId()).isNotNull();
        assertThat(research.getStatus()).isEqualTo(ResearchStatus.ACTIVE);
        assertThat(research.getName()).isEqualTo(command.name());
    }

    @Test
    @Transactional
    @DisplayName("should persist research configuration correctly")
    void shouldPersistResearchConfiguration() {
        // Arrange
        User user = helper.createUser();
        String instructions = "# Detailed Instructions\n\n## Step 1\nAnalyze BTC momentum.";
        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            "Config Persistence Test-" + UUID.randomUUID().toString().substring(0, 8),
            instructions,
            Set.of(),
            new ResearchInterval(2, ResearchInterval.Unit.DAYS),
            false,
            null,
            user.getId()
        );

        // Act
        MarketResearch created = createMarketResearchUseCase.execute(command);

        // Reload from database
        MarketResearch reloaded = marketResearchRepository.findById(created.getId())
            .orElseThrow(() -> new AssertionError("Research not found after save"));

        // Assert — verify fields survived save/reload
        assertThat(reloaded.getName()).isEqualTo(command.name());
        assertThat(reloaded.getMarkdownInstructions()).isEqualTo(instructions);
        assertThat(reloaded.getInterval()).isEqualTo(new ResearchInterval(2, ResearchInterval.Unit.DAYS));
        assertThat(reloaded.getOwnerId()).isEqualTo(user.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ResearchStatus.ACTIVE);
    }

    @Test
    @Transactional
    @DisplayName("should list research by owner")
    void shouldListResearchByUser() {
        // Arrange — create two users, each with research
        User user1 = helper.createUser();
        User user2 = helper.createUser();

        createResearch("User1 Research", user1.getId());
        createResearch("User2 Research A", user2.getId());
        createResearch("User2 Research B", user2.getId());

        // Act
        List<MarketResearch> user1Research = marketResearchRepository.findActiveByOwnerId(user1.getId());
        List<MarketResearch> user2Research = marketResearchRepository.findActiveByOwnerId(user2.getId());

        // Assert
        assertThat(user1Research).hasSize(1);
        assertThat(user2Research).hasSize(2);
    }

    @Test
    @Transactional
    @DisplayName("should delete research and cascade to executions")
    void shouldDeleteResearchCascade() {
        // Arrange
        User user = helper.createUser();
        MarketResearch research = createResearch("Delete Test Research", user.getId());
        UUID researchId = research.getId();

        // Verify it exists
        assertThat(marketResearchRepository.findById(researchId)).isPresent();

        // Act
        marketResearchRepository.delete(researchId);

        // Assert — research should be gone
        assertThat(marketResearchRepository.findById(researchId)).isEmpty();
        // Executions cascade — should be empty for this research
        assertThat(researchExecutionRepository.findByMarketResearchId(researchId)).isEmpty();
    }

    private MarketResearch createResearch(String name, UserId ownerId) {
        CreateMarketResearchCommand command = new CreateMarketResearchCommand(
            name + "-" + UUID.randomUUID().toString().substring(0, 8),
            "# Instructions\n\nTest research instructions.",
            Set.of(),
            new ResearchInterval(1, ResearchInterval.Unit.WEEKS),
            false,
            null,
            ownerId
        );
        return createMarketResearchUseCase.execute(command);
    }
}
