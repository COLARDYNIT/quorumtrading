package com.quorumtrading.adapter.mcp.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No MCP tool truncates a result set by hand.
 *
 * <p>{@code list_strategies} with {@code limit=-3} answered {@code fromIndex(0) > toIndex(-3)}.
 * That is {@link java.util.List#subList}'s own message: it names no parameter, suggests no fix, and
 * tells the caller about an index it never supplied. The mechanism is statically detectable — a
 * tool passing a caller-supplied bound straight into {@code subList} — so this check exists to make
 * that shape unmergeable rather than to be re-diagnosed from a bug report next time.</p>
 *
 * <p>Use {@link McpToolBase#paginate(List, int)}, which validates the bound at the point of
 * truncation, and {@link McpToolBase#parseLimit(java.util.Optional, int, int)} to obtain it.</p>
 */
@DisplayName("MCP tool pagination guard")
class ToolPaginationGuardTest {

    /**
     * Exact fully-qualified file names allowed to call {@code subList}, never wildcards, so a new
     * tool cannot inherit the exemption by being named like an old one.
     *
     * <p>Empty, and meant to stay that way: {@code paginate} truncates with {@code Stream#limit},
     * which needs no index arithmetic, so there is currently no tool with a reason to be here.
     * Adding an entry needs a reason in the commit message.</p>
     */
    private static final Set<String> ALLOWED_TO_CALL_SUBLIST = Set.of();

    private Path toolSourceDir() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path.resolve("quorumtrading-backend/quorumtrading-adapter-mcp/src/main/java/"
                + "com/quorumtrading/adapter/mcp/tool");
    }

    @Test
    @DisplayName("no tool class calls subList — the raw index error leaked from exactly that call")
    void noToolClassCallsSubListDirectly() throws IOException {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        try (Stream<Path> files = Files.list(toolSourceDir())) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                scanned++;
                String name = file.getFileName().toString();
                if (ALLOWED_TO_CALL_SUBLIST.contains(name)) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                if (source.contains(".subList(")) {
                    offenders.add(name);
                }
            }
        }

        assertThat(scanned).as("tool source files found to scan").isGreaterThan(10);
        assertThat(offenders)
                .as("These classes truncate a result set with List#subList. A caller-supplied bound "
                    + "reaching subList surfaces 'fromIndex(0) > toIndex(-3)' — a java.util internal "
                    + "that names no parameter. Use McpToolBase#paginate(list, parseLimit(...)) "
                    + "instead, which validates the bound where the truncation happens.")
                .isEmpty();
    }
}
