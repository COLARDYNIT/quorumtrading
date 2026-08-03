package com.quorumtrading.adapter.mcp.tool;

import com.quorumtrading.adapter.mcp.filter.FeatureFlagMcpFilters;
import com.quorumtrading.model.enums.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the two halves of MCP feature gating in step.
 *
 * <p>A gated tool is described in two places that have no compiler link between them: the guard in
 * its body, and the tool-name map in {@link FeatureFlagMcpFilters} that decides whether it appears
 * in {@code tools/list}. Either one alone is a bug that only shows up with the flag switched off —
 * a tool listed but refusing, or a tool silently unlisted while still working. Both are the kind
 * of thing nobody notices until a user reports it, so assert the pairing here.</p>
 */
class FeatureGatedToolContractTest {

    private static final Pattern TOOL_DECLARATION =
        Pattern.compile("@Tool\\s*\\(\\s*name\\s*=\\s*\"([a-z_]+)\"", Pattern.DOTALL);

    private record ToolSource(String file, String name, String body) {}

    private Path toolSourceDir() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path.resolve("quorumtrading-backend/quorumtrading-adapter-mcp/src/main/java/"
            + "com/quorumtrading/adapter/mcp/tool");
    }

    private List<ToolSource> allTools() throws IOException {
        List<ToolSource> tools = new ArrayList<>();
        try (Stream<Path> files = Files.list(toolSourceDir())) {
            for (Path file : files.filter(f -> f.toString().endsWith("Tools.java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher matcher = TOOL_DECLARATION.matcher(source);
                List<Integer> starts = new ArrayList<>();
                List<String> names = new ArrayList<>();
                while (matcher.find()) {
                    starts.add(matcher.start());
                    names.add(matcher.group(1));
                }
                for (int i = 0; i < starts.size(); i++) {
                    int end = i + 1 < starts.size() ? starts.get(i + 1) : source.length();
                    tools.add(new ToolSource(file.getFileName().toString(), names.get(i),
                        source.substring(starts.get(i), end)));
                }
            }
        }
        assertThat(tools).as("found @Tool declarations to check").isNotEmpty();
        return tools;
    }

    @Test
    @DisplayName("every tool that refuses on a disabled flag is also hidden from tools/list")
    void guardedToolsAreAlsoFiltered() throws IOException {
        Map<String, Feature> filtered = FeatureFlagMcpFilters.toolFeatures();

        List<String> advertised = allTools().stream()
            .filter(tool -> tool.body().contains("featureDisabled(Feature."))
            .filter(tool -> !filtered.containsKey(tool.name()))
            .map(tool -> tool.file() + "#" + tool.name())
            .toList();

        assertThat(advertised)
            .as("These tools refuse when their feature flag is off, but are still advertised by "
                + "tools/list. An agent will pick one and burn a turn discovering it does not "
                + "work. Add them to FeatureFlagMcpFilters.TOOL_FEATURES.")
            .isEmpty();
    }

    @Test
    @DisplayName("every tool hidden from tools/list also refuses when invoked directly")
    void filteredToolsAlsoGuard() throws IOException {
        List<ToolSource> tools = allTools();

        List<String> unguarded = new ArrayList<>();
        for (Map.Entry<String, Feature> entry : FeatureFlagMcpFilters.toolFeatures().entrySet()) {
            ToolSource tool = tools.stream()
                .filter(candidate -> candidate.name().equals(entry.getKey()))
                .findFirst()
                .orElse(null);
            if (tool == null) {
                unguarded.add(entry.getKey() + " (no such @Tool)");
            } else if (!tool.body().contains("featureDisabled(Feature." + entry.getValue().name())) {
                unguarded.add(tool.file() + "#" + tool.name());
            }
        }

        assertThat(unguarded)
            .as("These tools are hidden from tools/list when their flag is off but do not guard "
                + "their own body, so a client holding a cached tool list can still call them. "
                + "Hiding is a courtesy; the guard is the control.")
            .isEmpty();
    }

    @Test
    @DisplayName("both flagged features are actually represented in the MCP surface")
    void everyFeatureIsCovered() {
        assertThat(FeatureFlagMcpFilters.toolFeatures().values())
            .as("a feature with no gated MCP tools would silently be a REST-only flag")
            .contains(Feature.CHALLENGES, Feature.MARKET_RESEARCH);
    }
}
