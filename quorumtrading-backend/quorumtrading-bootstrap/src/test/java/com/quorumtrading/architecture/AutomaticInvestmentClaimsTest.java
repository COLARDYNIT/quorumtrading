package com.quorumtrading.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the product tells a user about automatic investing must match what it will actually do.
 *
 * <p>The DCA scheduler ships disabled in every environment — deliberately, because it moves real
 * money on live broker accounts — and that default is documented in six places. But no
 * <em>user-facing</em> surface carried it, and five asserted the opposite at the exact moment of
 * commitment: the MCP tool description ("the system will pick a strategy and fund it"), the
 * OpenAPI description ("the scheduler does this automatically each month"), and three strings in
 * the plan UI. A user created a plan, was shown {@code ACTIVE} and a {@code nextExecutionAt} date,
 * and nothing ever happened.</p>
 *
 * <p>For a set-and-forget product, silent inaction on a promise about money is the one failure
 * that must never be quiet — worse than an error, because nothing prompts anyone to look. This is
 * a copy problem with a code fix: the surfaces have to read the flag instead of assuming it.</p>
 *
 * <p>Scans source text because that is where the claims live. If you are here because this test
 * failed on a string you added, the question to answer is whether the sentence is true on a
 * default install.</p>
 */
class AutomaticInvestmentClaimsTest {

    /**
     * Sentences that state automatic monthly execution as fact. Each was live in the product and
     * false on every default install.
     */
    private static final List<String> UNCONDITIONAL_CLAIMS = List.of(
            "the system will pick a strategy",
            "does this automatically each month");

    private Path repoRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path;
    }

    private String read(String relativePath) throws IOException {
        Path file = repoRoot().resolve(relativePath);
        assertThat(file).as("expected file to exist: " + relativePath).exists();
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** The premise every other assertion here depends on. */
    @Test
    @DisplayName("the DCA scheduler is still disabled by default")
    void schedulerIsDisabledByDefault() throws IOException {
        String properties = read("quorumtrading-backend/quorumtrading-bootstrap/"
                + "src/main/resources/application.properties");

        assertThat(properties)
                .as("if this default ever flips, the copy this test guards can be unhedged again "
                    + "— but that is a deliberate change, not an incidental one")
                .contains("quorumtrading.scheduler.investments.enabled=false");
    }

    @Test
    @DisplayName("no tool or API description promises unconditional monthly execution")
    void noDescriptionPromisesUnconditionalExecution() throws IOException {
        List<String> surfaces = List.of(
                "quorumtrading-backend/quorumtrading-adapter-mcp/src/main/java/com/"
                        + "quorumtrading/adapter/mcp/tool/InvestmentPlanTools.java",
                "quorumtrading-backend/quorumtrading-adapter-rest/src/main/java/com/"
                        + "quorumtrading/adapter/rest/resource/InvestmentPlanResource.java");

        for (String surface : surfaces) {
            String source = read(surface);
            for (String claim : UNCONDITIONAL_CLAIMS) {
                assertThat(source)
                        .as("%s states automatic execution as fact; it is off by default, so the "
                            + "description has to say the plan fires only when an operator has "
                            + "enabled the scheduler", surface)
                        .doesNotContain(claim);
            }
        }
    }

    /**
     * The plan detail page rendered the literal string {@code 'on schedule'} as its status delta
     * for any ACTIVE plan — the most direct possible assurance, on the screen a user opens to
     * check whether their money moved.
     */
    @Test
    @DisplayName("the plan UI gates its schedule claims on the flag rather than assuming")
    void planUiGatesScheduleClaimsOnTheFlag() throws IOException {
        String source = read("quorumtrading-frontend/app/investment-plans/[id]/page.tsx");

        // The phrase itself is fine — it is true when the scheduler is on. What is not fine is
        // rendering it without asking, which is what this page did for every ACTIVE plan.
        if (source.contains("on schedule")) {
            assertThat(source)
                    .as("'on schedule' is a claim about the future of someone's money; it may "
                        + "only be shown where automaticExecutionEnabled has been consulted")
                    .contains("automaticExecutionEnabled");
        }
    }

    @Test
    @DisplayName("the create form warns when nothing will run the plan")
    void createFormWarnsWhenAutomaticExecutionIsOff() throws IOException {
        String source = read("quorumtrading-frontend/app/investment-plans/new/page.tsx");

        assertThat(source)
                .as("the moment a user commits to a recurring plan is the moment they must learn "
                    + "it will not recur on this instance")
                .contains("automaticInvestmentsEnabled");
    }

    /**
     * The UI stated 13:30 UTC while the cron fires at 06:00 UTC — wrong by seven and a half hours
     * even once the scheduler is switched on. A time quoted to the user has to come from the same
     * place the scheduler reads.
     */
    @Test
    @DisplayName("any execution time shown to a user matches the configured cron")
    void statedExecutionTimeMatchesTheCron() throws IOException {
        String properties = read("quorumtrading-backend/quorumtrading-bootstrap/"
                + "src/main/resources/application.properties");
        Matcher cron = Pattern.compile(
                        "quorumtrading\\.scheduler\\.investments\\.cron=\\S+\\s+(\\d+)\\s+(\\d+)\\s")
                .matcher(properties);
        assertThat(cron.find()).as("investments cron must be configured").isTrue();
        String configuredTime = String.format("%02d:%02d",
                Integer.parseInt(cron.group(2)), Integer.parseInt(cron.group(1)));

        Pattern statedTime = Pattern.compile("(\\d{2}:\\d{2}) UTC");
        for (String page : List.of(
                "quorumtrading-frontend/app/investment-plans/new/page.tsx",
                "quorumtrading-frontend/app/investment-plans/[id]/page.tsx")) {
            Matcher stated = statedTime.matcher(read(page));
            while (stated.find()) {
                assertThat(stated.group(1))
                        .as("%s quotes an execution time the scheduler does not use", page)
                        .isEqualTo(configuredTime);
            }
        }
    }
}
