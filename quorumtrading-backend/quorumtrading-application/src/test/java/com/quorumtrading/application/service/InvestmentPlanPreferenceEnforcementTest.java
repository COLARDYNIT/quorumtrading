package com.quorumtrading.application.service;

import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every plan preference must be read by something that can act on it.
 *
 * <p>{@code maxStrategiesActive} and {@code preferExistingHoldings} were accepted by the API,
 * persisted, echoed back on every read — and enforced nowhere. The only mention of
 * {@code maxStrategiesActive} in main code was inside the LLM picker's prompt text, which is
 * advisory at best and absent entirely under the default heuristic picker. A plan created with a
 * cap of one was observed running two live simulations; the field looked like a control and was
 * decoration.</p>
 *
 * <p>A field a user can set is a promise. This test does not prove the enforcement is correct —
 * only that a consumer exists at all, which is the failure that actually happened. It is
 * deliberately a source scan: reflection cannot tell you whether anybody reads a value.</p>
 */
class InvestmentPlanPreferenceEnforcementTest {

    /** Files that merely define or transport the value object, so a mention there proves nothing. */
    private static final List<String> NOT_A_CONSUMER = List.of(
            "InvestmentPlanPreferences.java",
            "InvestmentPlan.java"
    );

    private Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path;
    }

    private List<Path> mainSources() throws IOException {
        Path root = repoRoot().resolve("quorumtrading-backend");
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> NOT_A_CONSUMER.stream()
                            .noneMatch(excluded -> p.getFileName().toString().equals(excluded)))
                    .toList();
        }
    }

    @Test
    @DisplayName("no plan preference is accepted, stored and then read by nobody")
    void everyPreferenceHasAConsumer() throws IOException {
        List<Path> sources = mainSources();
        assertThat(sources).as("main sources to scan").isNotEmpty();

        List<String> orphans = new ArrayList<>();
        for (RecordComponent component : InvestmentPlanPreferences.class.getRecordComponents()) {
            String accessor = component.getName() + "()";
            boolean consumed = false;
            for (Path source : sources) {
                if (Files.readString(source, StandardCharsets.UTF_8).contains(accessor)) {
                    consumed = true;
                    break;
                }
            }
            if (!consumed) {
                orphans.add(component.getName());
            }
        }

        assertThat(orphans)
                .as("These InvestmentPlanPreferences fields are settable through the API but no "
                    + "main-source file reads them, so a user configuring one gets no behaviour "
                    + "change and no warning: %s. Enforce the field or remove it from the record.",
                    orphans)
                .isEmpty();
    }

    @Test
    @DisplayName("the two fields that were silently ignored are enforced where money moves")
    void theCapAndTopUpPreferenceAreEnforcedInTheExecutionPath() throws IOException {
        Path executor = repoRoot().resolve("quorumtrading-backend/quorumtrading-application/src/main/"
                + "java/com/quorumtrading/application/service/ExecuteInvestmentPlanService.java");
        String source = Files.readString(executor, StandardCharsets.UTF_8);

        assertThat(source)
                .as("maxStrategiesActive must be checked in the service, not only described to an "
                    + "LLM picker that may not be the configured one")
                .contains("maxStrategiesActive()");
        assertThat(source)
                .as("preferExistingHoldings must be checked in the service for the same reason")
                .contains("preferExistingHoldings()");
    }
}
