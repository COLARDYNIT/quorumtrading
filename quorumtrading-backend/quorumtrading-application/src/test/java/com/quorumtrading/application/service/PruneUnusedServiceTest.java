package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.MarketResearchRepository;
import com.quorumtrading.application.port.out.ResearchExecutionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.command.PruneUnusedCommand;
import com.quorumtrading.application.usecase.result.PruneUnusedResult;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruning is irreversible, and "unused" means zero simulations — which every strategy is until its
 * first backtest. So a dry run is the only way to discover that the five strategies you just wrote
 * and have not backtested yet are the five about to be deleted.
 */
class PruneUnusedServiceTest {

    private StrategyRepository strategyRepository;
    private SimulationRepository simulationRepository;
    private MarketResearchRepository marketResearchRepository;
    private ResearchExecutionRepository researchExecutionRepository;
    private PruneUnusedService service;
    private final UserId owner = new UserId(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        strategyRepository = Mockito.mock(StrategyRepository.class);
        simulationRepository = Mockito.mock(SimulationRepository.class);
        marketResearchRepository = Mockito.mock(MarketResearchRepository.class);
        researchExecutionRepository = Mockito.mock(ResearchExecutionRepository.class);
        service = new PruneUnusedService(strategyRepository, simulationRepository,
            marketResearchRepository, researchExecutionRepository);
        when(marketResearchRepository.findByOwnerId(any())).thenReturn(List.of());
    }

    private Strategy unusedStrategy(String name) {
        Strategy s = Mockito.mock(Strategy.class);
        StrategyId id = new StrategyId(UUID.randomUUID());
        when(s.getId()).thenReturn(id);
        when(s.getName()).thenReturn(name);
        when(simulationRepository.findByStrategyId(id)).thenReturn(List.of());
        return s;
    }

    @Test
    void dryRunDeletesNothing() {
        List<Strategy> drafts = List.of(unusedStrategy("Draft A"), unusedStrategy("Draft B"));
        when(strategyRepository.findByOwnerId(owner)).thenReturn(drafts);

        PruneUnusedResult result = service.execute(
            new PruneUnusedCommand(owner, true, true, true));

        verify(strategyRepository, never()).deleteById(any());
        assertThat(result.dryRun()).isTrue();
        assertThat(result.deletedStrategies()).isEqualTo(2);
    }

    @Test
    void dryRunNamesWhatWouldGo() {
        // A count alone cannot answer "is that my new work?" — the names can.
        List<Strategy> drafts = List.of(unusedStrategy("Draft A"), unusedStrategy("Draft B"));
        when(strategyRepository.findByOwnerId(owner)).thenReturn(drafts);

        PruneUnusedResult result = service.execute(
            new PruneUnusedCommand(owner, true, true, true));

        assertThat(result.strategyNames()).containsExactly("Draft A", "Draft B");
    }

    @Test
    void confirmedRunDeletesAndStillNamesWhatWent() {
        List<Strategy> drafts = List.of(unusedStrategy("Draft A"));
        when(strategyRepository.findByOwnerId(owner)).thenReturn(drafts);

        PruneUnusedResult result = service.execute(
            new PruneUnusedCommand(owner, true, true, false));

        verify(strategyRepository).deleteById(any());
        assertThat(result.dryRun()).isFalse();
        assertThat(result.deletedStrategies()).isEqualTo(1);
        assertThat(result.strategyNames()).containsExactly("Draft A");
    }

    @Test
    void aStrategyWithSimulationsIsNeverTouched() {
        Strategy used = Mockito.mock(Strategy.class);
        StrategyId id = new StrategyId(UUID.randomUUID());
        when(used.getId()).thenReturn(id);
        when(simulationRepository.findByStrategyId(id))
            .thenReturn(List.of(Mockito.mock(com.quorumtrading.model.entity.Simulation.class)));
        List<Strategy> used1 = List.of(used);
        when(strategyRepository.findByOwnerId(owner)).thenReturn(used1);

        PruneUnusedResult result = service.execute(
            new PruneUnusedCommand(owner, true, true, false));

        verify(strategyRepository, never()).deleteById(any());
        assertThat(result.deletedStrategies()).isZero();
    }

    /** The three-arg constructor must keep deleting, or existing callers change behaviour silently. */
    @Test
    void legacyCommandStillDeletes() {
        List<Strategy> drafts = List.of(unusedStrategy("Draft A"));
        when(strategyRepository.findByOwnerId(owner)).thenReturn(drafts);

        PruneUnusedResult result = service.execute(new PruneUnusedCommand(owner, true, true));

        verify(strategyRepository).deleteById(any());
        assertThat(result.dryRun()).isFalse();
    }
}
