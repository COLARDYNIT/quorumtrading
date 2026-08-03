package com.quorumtrading.adapter.mcp.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An argument a tool declares must be read by the method that declares it.
 *
 * <p>A parameter the server accepts and never looks at is indistinguishable, from the caller's
 * side, from one it honours — the same failure as {@code maxStrategiesActive}, which was accepted,
 * persisted, echoed back on every read, and enforced nowhere. An agent driving this platform can
 * only learn a knob does nothing by watching results that do not change.</p>
 *
 * <p>This surface was clean when the check was written (71 tools, 167 arguments, none unused), so
 * this is a ratchet rather than a fix: it exists to keep it that way. It is deliberately shallow —
 * it proves an argument is referenced, not that it is honoured, because "declared and never read"
 * is the failure that is both automatable and the one that actually happened.</p>
 */
class ToolArgumentUsageTest {

    private static final Pattern TOOL_ANNOTATION = Pattern.compile("@Tool\\s*\\(");
    private static final Pattern TOOL_NAME = Pattern.compile("name\\s*=\\s*\"([a-z_]+)\"");
    private static final Pattern METHOD_SIGNATURE =
            Pattern.compile("public\\s+\\w[\\w<>,.\\s\\[\\]]*\\s+(\\w+)\\s*\\((.*?)\\)\\s*\\{", Pattern.DOTALL);
    private static final Pattern IDENTIFIER = Pattern.compile("\\b([A-Za-z_]\\w*)\\b");

    private Path toolSourceDir() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path.resolve("quorumtrading-backend/quorumtrading-adapter-mcp/src/main/java/"
                + "com/quorumtrading/adapter/mcp/tool");
    }

    /**
     * Remove comments before parsing a parameter list: a declaration's name is its last
     * identifier, and prose in a trailing comment would otherwise be read as the name.
     */
    private String stripComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//[^\n]*", " ");
    }

    /** Split a parameter list on top-level commas, ignoring those inside generics. */
    private List<String> splitParameters(String parameterList) {
        List<String> groups = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : parameterList.toCharArray()) {
            if (c == '<' || c == '(' || c == '[') depth++;
            if (c == '>' || c == ')' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                groups.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) {
            groups.add(current.toString());
        }
        return groups;
    }

    /** The declared name of a parameter is the last identifier in its declaration. */
    private String parameterName(String declaration) {
        Matcher matcher = IDENTIFIER.matcher(declaration);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }

    @Test
    @DisplayName("no MCP tool declares an argument its own method never reads")
    void everyToolArgumentIsRead() throws IOException {
        List<String> unused = new ArrayList<>();
        int toolCount = 0;
        int argumentCount = 0;

        try (Stream<Path> files = Files.list(toolSourceDir())) {
            for (Path file : files.filter(f -> f.toString().endsWith("Tools.java")).sorted().toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);

                List<Integer> starts = new ArrayList<>();
                Matcher annotations = TOOL_ANNOTATION.matcher(source);
                while (annotations.find()) {
                    starts.add(annotations.start());
                }

                for (int i = 0; i < starts.size(); i++) {
                    int start = starts.get(i);
                    int end = i + 1 < starts.size() ? starts.get(i + 1) : source.length();
                    String chunk = source.substring(start, end);

                    Matcher name = TOOL_NAME.matcher(chunk);
                    if (!name.find()) {
                        continue;
                    }
                    String tool = name.group(1);
                    toolCount++;

                    Matcher signature = METHOD_SIGNATURE.matcher(chunk);
                    if (!signature.find()) {
                        continue;
                    }
                    String body = chunk.substring(signature.end());

                    for (String declaration : splitParameters(stripComments(signature.group(2)))) {
                        String parameter = parameterName(declaration);
                        if (parameter == null) {
                            continue;
                        }
                        argumentCount++;
                        if (!Pattern.compile("\\b" + Pattern.quote(parameter) + "\\b").matcher(body).find()) {
                            unused.add(file.getFileName() + "#" + tool + " -> " + parameter);
                        }
                    }
                }
            }
        }

        assertThat(toolCount).as("tools found to check").isGreaterThan(50);
        assertThat(argumentCount).as("tool arguments found to check").isGreaterThan(100);
        assertThat(unused)
                .as("These tool arguments are accepted and never read, so a caller setting one gets "
                    + "no behaviour change and no warning. Use the argument or remove it from the "
                    + "tool signature.")
                .isEmpty();
    }
}
