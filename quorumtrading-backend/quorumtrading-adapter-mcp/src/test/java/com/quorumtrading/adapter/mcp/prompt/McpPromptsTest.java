package com.quorumtrading.adapter.mcp.prompt;

import io.quarkiverse.mcp.server.PromptMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guided prompts, which had no test at all.
 *
 * <p>A prompt is the surface a newcomer picks a workflow from, and it is rendered text rather than
 * a schema — so nothing stopped it drifting away from the tools it drives. Both flagship prompts
 * had: {@code create_backtest} told the agent to pass a {@code symbol} argument
 * {@code create_simulation} rejects and then to call {@code start_simulation}, which its own
 * description says not to; {@code setup_live_trading} gated on a strategy status the platform never
 * emits and used a status filter that returns backtests when asked for real-money runs.</p>
 *
 * <p>{@code ToolDescriptionContractTest} guards the general shape — no prompt may name a tool that
 * does not exist. These assert the specific instructions a reader acts on.</p>
 */
class McpPromptsTest {

    private final McpPrompts prompts = new McpPrompts();

    private String render(List<PromptMessage> messages) {
        assertThat(messages).hasSize(1);
        return messages.get(0).content().asText().text();
    }

    @Nested
    @DisplayName("create_backtest")
    class CreateBacktest {

        private String body() {
            return render(prompts.createBacktest("BTC", "January 2026"));
        }

        @Test
        @DisplayName("interpolates the symbol and period it was given")
        void interpolatesItsArguments() {
            assertThat(body()).contains("BTC").contains("January 2026");
        }

        @Test
        @DisplayName("does not tell the agent to pass a symbol to create_simulation")
        void doesNotInventASymbolArgument() {
            // create_simulation's own description: "you do NOT need to specify a symbol here".
            assertThat(body()).doesNotContain("- Symbol:");
        }

        @Test
        @DisplayName("does not treat start_simulation as a routine step")
        void doesNotCallStartSimulation() {
            assertThat(body()).doesNotContain("Call start_simulation");
        }

        @Test
        @DisplayName("makes the user price the run before it is created, because creating starts it")
        void pricesTheRunFirst() {
            assertThat(body())
                    .contains("estimate_backtest_cost")
                    .contains("STARTS it");
        }
    }

    @Nested
    @DisplayName("setup_live_trading")
    class SetupLiveTrading {

        private String body() {
            return render(prompts.setupLiveTrading());
        }

        @Test
        @DisplayName("does not gate on a strategy status the domain never emits")
        void doesNotRequireAPhantomPublishedStatus() {
            assertThat(body()).doesNotContain("PUBLISHED");
        }

        @Test
        @DisplayName("warns that a status=LIVE filter also returns backtests")
        void warnsThatLiveStatusIsNotAboutMoney() {
            // This is the one question the prompt exists to answer safely.
            assertThat(body()).contains("realMoney");
        }

        @Test
        @DisplayName("still demands explicit user confirmation before creating a live run")
        void keepsTheConfirmationStep() {
            assertThat(body()).contains("explicit user confirmation");
        }
    }

    @Test
    @DisplayName("analyze_simulation checks whether the result is trustworthy before reporting it")
    void analyzeSimulationChecksNoDecisionsReason() {
        // A run that skipped every evaluation still completes at 0.00%, which is byte-identical to
        // one that evaluated properly and held. Reporting that P&L without checking is confidently
        // wrong, and the guide names this as the first question to ask of any finished run.
        assertThat(render(prompts.analyzeSimulation("11111111-1111-1111-1111-111111111111")))
                .contains("noDecisionsReason");
    }
}
