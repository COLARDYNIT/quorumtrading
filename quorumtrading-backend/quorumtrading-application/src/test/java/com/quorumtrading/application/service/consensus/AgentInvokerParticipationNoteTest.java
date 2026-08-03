package com.quorumtrading.application.service.consensus;

import com.quorumtrading.application.orchestration.agents.Agent;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.enums.AgentRole;
import com.quorumtrading.model.orchestration.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AgentInvoker#buildParticipationNote} — the explanation attached to a consensus
 * decision naming which enabled agents produced no vote.
 *
 * <p>The note previously said "insufficient data or agent failure" for every absentee, which left
 * the reader unable to tell a deliberate abstention from a malfunction. These tests pin the two
 * causes apart.</p>
 */
class AgentInvokerParticipationNoteTest {

    private static final Set<AgentRole> ALL_THREE =
        Set.of(AgentRole.TECHNICAL, AgentRole.FUNDAMENTAL, AgentRole.SENTIMENT);

    @Test
    @DisplayName("An agent filtered out for missing data is reported as abstaining, with the reason")
    void insufficientData_reportsAbstentionWithReason() {
        AgentContext context = mock(AgentContext.class);
        AgentInvoker invoker = invokerWith(
            agent(AgentRole.TECHNICAL, Agent.ContextCheck.ok()),
            agent(AgentRole.FUNDAMENTAL,
                Agent.ContextCheck.insufficient("0 of 2 required news events stored")));

        String note = invoker.buildParticipationNote(
            context, ALL_THREE, List.of(decision(AgentRole.TECHNICAL)));

        assertThat(note)
            .contains("FUNDAMENTAL abstained")
            .contains("0 of 2 required news events stored")
            .doesNotContain("no decision returned");
    }

    @Test
    @DisplayName("An agent that had data but returned nothing is reported as an error/timeout")
    void agentWithDataButNoDecision_reportsFailure() {
        AgentContext context = mock(AgentContext.class);
        AgentInvoker invoker = invokerWith(
            agent(AgentRole.TECHNICAL, Agent.ContextCheck.ok()),
            agent(AgentRole.SENTIMENT, Agent.ContextCheck.ok()));

        String note = invoker.buildParticipationNote(
            context, ALL_THREE, List.of(decision(AgentRole.TECHNICAL)));

        // No cause was captured, which in production means the agent never came back at all.
        assertThat(note)
            .contains("SENTIMENT — no decision returned (timed out before answering)")
            .doesNotContain("abstained");
    }

    @Test
    @DisplayName("Abstention and failure in the same round are distinguished from each other")
    void mixedCauses_areReportedSeparately() {
        AgentContext context = mock(AgentContext.class);
        AgentInvoker invoker = invokerWith(
            agent(AgentRole.TECHNICAL, Agent.ContextCheck.ok()),
            agent(AgentRole.FUNDAMENTAL, Agent.ContextCheck.insufficient("no news stored")),
            agent(AgentRole.SENTIMENT, Agent.ContextCheck.ok()));

        String note = invoker.buildParticipationNote(
            context, ALL_THREE, List.of(decision(AgentRole.TECHNICAL)));

        assertThat(note).contains("FUNDAMENTAL abstained — no news stored");
        assertThat(note).contains("SENTIMENT — no decision returned (timed out before answering)");
    }

    @Test
    @DisplayName("Every enabled agent voted → no note at all")
    void fullParticipation_producesEmptyNote() {
        AgentContext context = mock(AgentContext.class);
        AgentInvoker invoker = invokerWith(
            agent(AgentRole.TECHNICAL, Agent.ContextCheck.ok()),
            agent(AgentRole.FUNDAMENTAL, Agent.ContextCheck.ok()),
            agent(AgentRole.SENTIMENT, Agent.ContextCheck.ok()));

        String note = invoker.buildParticipationNote(context, ALL_THREE, List.of(
            decision(AgentRole.TECHNICAL),
            decision(AgentRole.FUNDAMENTAL),
            decision(AgentRole.SENTIMENT)));

        assertThat(note).isEmpty();
    }

    @Test
    @DisplayName("Agents the strategy did not enable are not reported as missing")
    void disabledAgents_areNotReported() {
        AgentContext context = mock(AgentContext.class);
        AgentInvoker invoker = invokerWith(
            agent(AgentRole.TECHNICAL, Agent.ContextCheck.ok()),
            agent(AgentRole.SENTIMENT, Agent.ContextCheck.insufficient("no news stored")));

        String note = invoker.buildParticipationNote(
            context, Set.of(AgentRole.TECHNICAL), List.of(decision(AgentRole.TECHNICAL)));

        assertThat(note).isEmpty();
    }

    @Test
    @DisplayName("The root cause of an agent failure reaches the note, not the wrapper message")
    void agentFailureCauseIsNamedInTheNote() throws Exception {
        // The defect this closes: every agent wraps its failure as "<Role> agent analysis
        // failed", and only that wrapper was logged — so when the whole LLM engine was
        // unreachable, the router's precise diagnosis was discarded and every user-visible
        // surface said only "all specialized agents failed". An audit had to read container
        // logs to find out the engine was down.
        AgentContext context = mock(AgentContext.class);
        when(context.getTicker()).thenReturn("AAPL");
        when(context.getSimulationId()).thenReturn(
            new com.quorumtrading.model.valueobject.SimulationId(java.util.UUID.randomUUID()));

        Agent failing = agent(AgentRole.TECHNICAL, Agent.ContextCheck.ok());
        when(failing.analyze(org.mockito.ArgumentMatchers.any())).thenThrow(
            new RuntimeException("Technical agent analysis failed",
                new IllegalStateException(
                    "Selected LLM provider LOCAL_HTTP cannot serve calls right now")));

        AgentInvoker invoker = new AgentInvoker(
            List.of(failing), java.util.concurrent.Executors.newSingleThreadExecutor(),
            null, null, null, null, null, false);

        List<LLMDecision> decisions = invoker.queryAgentsInParallel(context);
        assertThat(decisions).isEmpty();

        String note = invoker.buildParticipationNote(
            context, Set.of(AgentRole.TECHNICAL), decisions);

        assertThat(note)
            .contains("Selected LLM provider LOCAL_HTTP cannot serve calls right now")
            .doesNotContain("Technical agent analysis failed");
    }

    @Test
    @DisplayName("an agent whose output could not be parsed is named, not counted as a participant")
    void parseFailedAgentIsNamed() {
        // A parse failure produces a synthesized SKIP decision (AgentResponseParser records it
        // with model "fallback"). Counting any decision as participation meant a round where the
        // model answered with garbage produced an EMPTY note — no agent, no cause. That became
        // the common failure mode once the engine was healthy.
        AgentContext context = mock(AgentContext.class);
        AgentInvoker invoker = invokerWith(
            agent(AgentRole.TECHNICAL, Agent.ContextCheck.ok()),
            agent(AgentRole.SENTIMENT, Agent.ContextCheck.ok()));

        String note = invoker.buildParticipationNote(context, ALL_THREE,
            List.of(decision(AgentRole.TECHNICAL), skipDecision(AgentRole.SENTIMENT)));

        assertThat(note)
            .contains("SENTIMENT — answered, but its output could not be parsed")
            .doesNotContain("abstained");
    }

    private static LLMDecision skipDecision(AgentRole role) {
        LLMDecision decision = mock(LLMDecision.class);
        when(decision.getRole()).thenReturn(role.name());
        when(decision.getRecommendation())
            .thenReturn(com.quorumtrading.model.enums.RecommendationType.SKIP);
        return decision;
    }

    private static AgentInvoker invokerWith(Agent... agents) {
        return new AgentInvoker(
            List.of(agents), null, null, null, null, null, null, false);
    }

    private static Agent agent(AgentRole role, Agent.ContextCheck check) {
        Agent agent = mock(Agent.class);
        when(agent.getRole()).thenReturn(role);
        when(agent.checkContext(org.mockito.ArgumentMatchers.any())).thenReturn(check);
        return agent;
    }

    private static LLMDecision decision(AgentRole role) {
        LLMDecision decision = mock(LLMDecision.class);
        when(decision.getRole()).thenReturn(role.name());
        // A real vote: anything but SKIP, which now means "produced nothing usable".
        org.mockito.Mockito.lenient().when(decision.getRecommendation())
            .thenReturn(com.quorumtrading.model.enums.RecommendationType.HOLD);
        return decision;
    }
}
