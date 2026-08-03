package com.quorumtrading.adapter.rest.resource;

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
 * A query or path parameter a resource declares must be read by the method that declares it.
 *
 * <p>Two were not, and they failed in different ways. {@code GET /data-availability/coverage} took
 * a {@code strategyId} documented as being "for future strategy-specific requirements" — advertised
 * in the OpenAPI spec, read by nothing, so a caller could tailor a request that was never tailored.
 * Worse, {@code DELETE /market-research/{researchId}/executions/{executionId}} ignored the parent
 * {@code researchId} entirely: any research id in the path deleted the execution, so the nesting
 * the URL promised was never enforced.</p>
 *
 * <p>A declared parameter is a claim about what the endpoint reads. This holds that claim, and is
 * the REST counterpart of {@code ToolArgumentUsageTest} on the MCP side.</p>
 */
class ResourceParameterUsageTest {

    private static final Pattern METHOD_SIGNATURE =
            Pattern.compile("public\\s+[\\w<>,.\\s\\[\\]]+\\s+(\\w+)\\s*\\(([^{;]*?)\\)\\s*\\{", Pattern.DOTALL);
    private static final Pattern IDENTIFIER = Pattern.compile("\\b([A-Za-z_]\\w*)\\b");

    private Path resourceDir() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("CLAUDE.md"))) {
            path = path.getParent();
        }
        assertThat(path).as("repository root containing CLAUDE.md").isNotNull();
        return path.resolve("quorumtrading-backend/quorumtrading-adapter-rest/src/main/java/"
                + "com/quorumtrading/adapter/rest/resource");
    }

    /** The body of a method whose opening brace sits at {@code openBraceIndex}. */
    private String methodBody(String source, int openBraceIndex) {
        int depth = 0;
        for (int i = openBraceIndex; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBraceIndex + 1, i);
                }
            }
        }
        return source.substring(openBraceIndex);
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

    private String parameterName(String declaration) {
        Matcher matcher = IDENTIFIER.matcher(declaration);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }

    @Test
    @DisplayName("no resource declares a query or path parameter its own method never reads")
    void everyDeclaredParameterIsRead() throws IOException {
        List<String> unused = new ArrayList<>();
        int checked = 0;

        try (Stream<Path> files = Files.list(resourceDir())) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher signature = METHOD_SIGNATURE.matcher(source);

                while (signature.find()) {
                    String parameters = signature.group(2);
                    if (!parameters.contains("@QueryParam") && !parameters.contains("@PathParam")) {
                        continue;
                    }
                    String body = methodBody(source, signature.end() - 1);

                    for (String declaration : splitParameters(stripComments(parameters))) {
                        if (!declaration.contains("@QueryParam") && !declaration.contains("@PathParam")) {
                            continue;
                        }
                        String parameter = parameterName(declaration);
                        if (parameter == null) {
                            continue;
                        }
                        checked++;
                        if (!Pattern.compile("\\b" + Pattern.quote(parameter) + "\\b").matcher(body).find()) {
                            unused.add(file.getFileName() + "#" + signature.group(1) + " -> " + parameter);
                        }
                    }
                }
            }
        }

        assertThat(checked).as("declared query/path parameters found to check").isGreaterThan(100);
        assertThat(unused)
                .as("These endpoint parameters are declared — and so advertised in the OpenAPI "
                    + "spec — but never read. Either use the parameter or remove it; a path segment "
                    + "that is ignored means the URL is making a promise the handler does not keep.")
                .isEmpty();
    }
}
