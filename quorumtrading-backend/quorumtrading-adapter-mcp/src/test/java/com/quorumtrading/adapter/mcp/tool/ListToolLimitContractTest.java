package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.auth.McpUserContext;
import com.quorumtrading.application.usecase.ListStrategiesUseCase;
import com.quorumtrading.application.usecase.query.ListStrategiesQuery;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One {@code limit} contract across every list tool.
 *
 * <p>Probing the six list tools with the same value produced four different answers: {@code -3}
 * leaked {@code fromIndex(0) > toIndex(-3)} from list_strategies and list_simulations, produced
 * "Max results cannot be negative" from list_benchmarks and list_challenges, and was ignored
 * outright by list_notifications and list_investment_plans — where {@code limit=0} returned every
 * row the account had. A caller cannot learn a bound from that; it has to try each tool.</p>
 *
 * <p>These tests assert the answer is now the same shape everywhere, and specifically that neither
 * of the two old messages can come back: {@code fromIndex} is a {@code java.util} internal that
 * names no parameter, and "Max results cannot be negative" names no parameter either.</p>
 */
@QuarkusTest
@DisplayName("list tool limit contract")
class ListToolLimitContractTest {

    @Inject StrategyTools strategyTools;
    @Inject SimulationTools simulationTools;
    @Inject BenchmarkTools benchmarkTools;
    @Inject ChallengeTools challengeTools;
    @Inject NotificationTools notificationTools;
    @Inject InvestmentPlanTools investmentPlanTools;

    @InjectMock McpUserContext userContext;
    @InjectMock ListStrategiesUseCase listStrategiesUseCase;

    private static final UserId USER_ID = UserId.generate();

    @BeforeEach
    void setUp() {
        when(userContext.getUserId()).thenReturn(USER_ID);
    }

    /** Each list tool reduced to "given a limit, produce a response". */
    private Map<String, Function<Optional<Integer>, ToolResponse>> listTools() {
        Map<String, Function<Optional<Integer>, ToolResponse>> tools = new LinkedHashMap<>();
        tools.put("list_strategies", limit -> strategyTools.listStrategies(limit, Optional.empty()));
        tools.put("list_simulations",
                limit -> simulationTools.listSimulations(Optional.empty(), limit, Optional.empty()));
        tools.put("list_benchmarks", limit -> benchmarkTools.listBenchmarks(limit));
        tools.put("list_challenges", limit -> challengeTools.listChallenges(Optional.empty(), limit));
        tools.put("list_notifications",
                limit -> notificationTools.listNotifications(Optional.empty(), limit));
        tools.put("list_investment_plans",
                limit -> investmentPlanTools.listInvestmentPlans(Optional.empty(), limit));
        return tools;
    }

    private String textOf(ToolResponse response) {
        return response.firstContent().asText().text();
    }

    @Test
    @DisplayName("a negative limit is refused by every list tool, naming the parameter and the bound")
    void shouldRefuseNegativeLimitEverywhere() {
        listTools().forEach((tool, call) -> {
            ToolResponse response = call.apply(Optional.of(-3));

            assertThat(response.isError()).as("%s must refuse limit=-3", tool).isTrue();
            assertThat(textOf(response)).as("%s error message", tool)
                    .contains("limit must be between 1 and")
                    .contains("(got -3)")
                    .doesNotContain("fromIndex")
                    .doesNotContain("Max results cannot be negative");
        });
    }

    @Test
    @DisplayName("limit=0 is refused, not answered with an empty page that reads as 'no data'")
    void shouldRefuseZeroLimitEverywhere() {
        listTools().forEach((tool, call) -> {
            ToolResponse response = call.apply(Optional.of(0));

            assertThat(response.isError()).as("%s must refuse limit=0", tool).isTrue();
            assertThat(textOf(response)).as("%s error message", tool)
                    .contains("limit must be between 1 and")
                    .contains("(got 0)");
        });
    }

    /**
     * Asserted through list_strategies specifically: {@code ListStrategiesQuery} passes the bound
     * through untouched, so the clamp can only come from the tool. Several other query records
     * clamp again in the application layer, which would make the same assertion pass whether or
     * not the tool clamped.
     */
    @Test
    @DisplayName("above the documented max clamps at the tool, before the query is built")
    void shouldClampToTheDocumentedMax() {
        strategyTools.listStrategies(Optional.of(5000), Optional.empty());

        ArgumentCaptor<ListStrategiesQuery> query = ArgumentCaptor.forClass(ListStrategiesQuery.class);
        verify(listStrategiesUseCase).execute(query.capture());
        // One over the documented max of 100: list tools over-fetch a single row to learn hasMore.
        assertThat(query.getValue().limit()).contains(101);
    }

    /**
     * An over-rejection guard, not evidence the validation works: it passes whenever a valid limit
     * is accepted, including on the unfixed code. It is here so a future tightening of the bound
     * cannot start refusing limits the tools document as legal.
     */
    @Test
    @DisplayName("a valid limit is still accepted (guard against over-rejection)")
    void shouldAcceptAValidLimit() {
        when(userContext.getUserId()).thenReturn(new UserId(UUID.randomUUID()));

        listTools().forEach((tool, call) ->
                assertThat(call.apply(Optional.of(1)).isError())
                        .as("%s must accept limit=1", tool).isFalse());
    }
}
