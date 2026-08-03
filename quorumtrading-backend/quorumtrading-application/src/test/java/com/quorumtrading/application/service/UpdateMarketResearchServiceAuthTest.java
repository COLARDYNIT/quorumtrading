package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.usecase.command.UpdateMarketResearchCommand;
import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Every other operation on MarketResearch checked ownership; this one had no authorization at
 * all, so any authenticated user could rewrite another user's research instructions and target
 * tickers — the inputs that drive automated strategy generation and auto-backtests.
 */
@ExtendWith(MockitoExtension.class)
class UpdateMarketResearchServiceAuthTest {

    @Mock private MarketResearchRepository marketResearchRepository;

    private UpdateMarketResearchService service;

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final UserId OTHER = new UserId(UUID.randomUUID());
    private static final UUID RESEARCH_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UpdateMarketResearchService(marketResearchRepository, new AuthorizationService());
    }

    private void stubOwnedResearch() {
        MarketResearch research = mock(MarketResearch.class);
        lenient().when(research.getId()).thenReturn(RESEARCH_ID);
        lenient().when(research.getOwnerId()).thenReturn(OWNER);
        when(marketResearchRepository.findById(RESEARCH_ID)).thenReturn(Optional.of(research));
    }

    @Test
    void rejectsUpdateByNonOwner() {
        stubOwnedResearch();

        UpdateMarketResearchCommand command = new UpdateMarketResearchCommand(
            RESEARCH_ID, "Hijacked", null, null, null, OTHER);

        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(AccessDeniedException.class);

        verify(marketResearchRepository, never()).save(any());
    }

    @Test
    void rejectsUpdateWithNoUser() {
        stubOwnedResearch();

        UpdateMarketResearchCommand command = new UpdateMarketResearchCommand(
            RESEARCH_ID, "Hijacked", null, null, null, null);

        assertThatThrownBy(() -> service.execute(command))
            .isInstanceOf(AccessDeniedException.class);

        verify(marketResearchRepository, never()).save(any());
    }

    @Test
    void allowsUpdateByOwner() {
        stubOwnedResearch();
        when(marketResearchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UpdateMarketResearchCommand command = new UpdateMarketResearchCommand(
            RESEARCH_ID, "Renamed", null, null, null, OWNER);

        service.execute(command);

        verify(marketResearchRepository).save(any());
    }
}
