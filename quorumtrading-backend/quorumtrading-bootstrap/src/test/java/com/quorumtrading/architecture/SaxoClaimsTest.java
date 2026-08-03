package com.quorumtrading.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the docs promise a European user about Saxo.
 *
 * <p>Saxo's status had drifted into four incompatible framings across four documents: "European
 * venues — not shipped" in the capabilities table, a modes table listing {@code SAXO_SIM} /
 * {@code SAXO_LIVE} as working, "EU markets" with no caveat at all in the broker configuration
 * reference, and a working four-step setup recipe in the local-setup guide. A user in Europe
 * reading those could not tell whether registering a Saxo developer app — an afternoon's work —
 * would get them anywhere.</p>
 *
 * <p>It would not. The decision is that <b>Saxo/EU is experimental</b>: European equity trading is
 * unavailable on the hosted service and experimental-only on a self-hosted instance. And
 * connecting Saxo does not by itself unlock a European venue anywhere, because
 * {@code TickerValidationHelper} refuses exchange-qualified tickers at strategy creation whatever
 * broker is attached — so there is no strategy a user could write that would trade one.</p>
 *
 * <p>Every document that invites a user toward Saxo must say that at the point of invitation. This
 * is a text guard rather than a behaviour test because the defect was never in the behaviour: the
 * code has been consistent throughout, and only the documentation disagreed with it.</p>
 */
class SaxoClaimsTest {

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

    @Test
    @DisplayName("every Saxo-facing doc states that linking it does not unlock EU venues")
    void saxoDocsDoNotImplyEuropeanMarketAccess() throws IOException {
        for (String doc : List.of(
                "docs/CAPABILITIES.md",
                "docs/configuration/BROKER_CONFIGURATION.md",
                "docs/operations/SAXO_READINESS.md",
                "LOCAL_SETUP.md")) {
            assertThat(read(doc))
                    .as("%s points a user at Saxo without answering the one question a European "
                        + "user is actually asking: will this let me trade EU stocks?", doc)
                    .containsAnyOf("exchange-qualified", "does not unlock", "not EU market access");
        }
    }

    @Test
    @DisplayName("every Saxo-facing doc marks it experimental")
    void saxoDocsMarkItExperimental() throws IOException {
        for (String doc : List.of(
                "docs/CAPABILITIES.md",
                "docs/configuration/BROKER_CONFIGURATION.md",
                "LOCAL_SETUP.md")) {
            assertThat(read(doc).toLowerCase())
                    .as("%s must not present Saxo as a supported integration", doc)
                    .contains("experimental");
        }
    }

    /**
     * The premise the guards above rest on. If this refusal is ever lifted — which is the work that
     * would make European trading real — the Saxo docs guarded here become wrong and must be
     * revisited in the same change. This test is how you find them.
     */
    @Test
    @DisplayName("exchange-qualified tickers are still refused regardless of broker")
    void exchangeQualifiedTickersAreStillRefused() throws IOException {
        String helper = read("quorumtrading-backend/quorumtrading-application/src/main/java/"
                + "com/quorumtrading/application/service/TickerValidationHelper.java");

        assertThat(helper)
                .as("the refusal is what makes 'Saxo does not unlock EU venues' true")
                .contains("exchange-qualified tickers are not supported yet");
    }
}
