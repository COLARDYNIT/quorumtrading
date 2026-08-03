package com.quorumtrading.adapter.llm.local;

import com.quorumtrading.model.valueobject.LLMUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parser behaviour against <b>real</b> {@code opencode run --format json} output.
 *
 * <p>The fixtures below are verbatim captures from opencode 1.18.10 on 2026-08-03, not invented
 * shapes. A parser tested only against a schema someone imagined passes while production fails.</p>
 */
class OpencodeResponseParserTest {

    private final OpencodeResponseParser parser = new OpencodeResponseParser();

    /** Verbatim successful run (prompt: "Reply with exactly the word PONG and nothing else."). */
    private static final String REAL_SUCCESS = """
            {"type":"step_start","timestamp":1785759891791,"sessionID":"ses_0386ad86cffeLLKhzJrtIv9eAl","part":{"id":"prt_fc7958145001TlAKapt9eZHsqH","messageID":"msg_fc795282f001RAqL7gbFRDCPC1","sessionID":"ses_0386ad86cffeLLKhzJrtIv9eAl","snapshot":"282a99b00230b340caa3d5ab6f98e61350ee6e79","type":"step-start"}}
            {"type":"text","timestamp":1785759892874,"sessionID":"ses_0386ad86cffeLLKhzJrtIv9eAl","part":{"id":"prt_fc7958503001W9G91QvD440MYL","messageID":"msg_fc795282f001RAqL7gbFRDCPC1","sessionID":"ses_0386ad86cffeLLKhzJrtIv9eAl","type":"text","text":"PONG","time":{"start":1785759892739,"end":1785759892870}}}
            {"type":"step_finish","timestamp":1785759892949,"sessionID":"ses_0386ad86cffeLLKhzJrtIv9eAl","part":{"id":"prt_fc79585d30015YYenBGMj6Jayq","reason":"stop","snapshot":"282a99b00230b340caa3d5ab6f98e61350ee6e79","messageID":"msg_fc795282f001RAqL7gbFRDCPC1","sessionID":"ses_0386ad86cffeLLKhzJrtIv9eAl","type":"step-finish","tokens":{"total":13822,"input":13800,"output":12,"reasoning":10,"cache":{"write":0,"read":0}},"cost":0}}
            """;

    /** Verbatim failure (invalid model). Note: exit code 1, and stderr was EMPTY. */
    private static final String REAL_ERROR =
            "{\"type\":\"error\",\"timestamp\":1785759547652,"
            + "\"sessionID\":\"ses_0386fbf9bffedSEAYEQt40RjaA\","
            + "\"error\":{\"name\":\"UnknownError\",\"data\":{\"message\":"
            + "\"Unexpected server error. Check server logs for details.\",\"ref\":\"err_20193095\"}}}";

    @Test
    @DisplayName("extracts the assistant text from the event stream")
    void extractsAssistantText() throws Exception {
        assertThat(parser.parse(REAL_SUCCESS)).isEqualTo("PONG");
    }

    @Test
    @DisplayName("concatenates a response streamed across several text events")
    void concatenatesStreamedText() throws Exception {
        String streamed = """
                {"type":"text","part":{"type":"text","text":"BUY "}}
                {"type":"text","part":{"type":"text","text":"AAPL"}}
                """;

        assertThat(parser.parse(streamed)).isEqualTo("BUY AAPL");
    }

    /**
     * ANTI-FABRICATION. An error event must never be handed on as if it were an answer — downstream
     * this becomes a trading decision.
     */
    @Test
    @DisplayName("an error event throws and never becomes response text")
    void errorEventThrows() {
        assertThatThrownBy(() -> parser.parse(REAL_ERROR))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("UnknownError")
                .hasMessageContaining("Unexpected server error")
                .hasMessageContaining("err_20193095");
    }

    /** ANTI-FABRICATION. Zero bytes is what the stdin hang produced; it is not an empty answer. */
    @Test
    @DisplayName("empty output throws rather than returning an empty answer")
    void emptyOutputThrows() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("no output");
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("no output");
    }

    @Test
    @DisplayName("a stream carrying no assistant text throws rather than synthesizing one")
    void noTextEventThrows() {
        String noText = "{\"type\":\"step_finish\",\"part\":{\"type\":\"step-finish\","
                + "\"tokens\":{\"total\":5,\"input\":4,\"output\":1,\"reasoning\":0}}}";

        assertThatThrownBy(() -> parser.parse(noText))
                .isInstanceOf(CLIExecutionException.class)
                .hasMessageContaining("refusing to synthesize");
    }

    @Test
    @DisplayName("reports the token counts opencode actually printed")
    void reportsRealTokenCounts() {
        Optional<LLMUsage> usage = parser.parseUsage(REAL_SUCCESS);

        assertThat(usage).isPresent();
        LLMUsage actual = usage.get();
        assertThat(actual.promptTokens()).isEqualTo(13800);
        // 12 output + 10 reasoning: reasoning tokens are generated, not prompt.
        assertThat(actual.completionTokens()).isEqualTo(22);
        // Taken as printed, not recomputed — opencode counts reasoning in the total.
        assertThat(actual.totalTokens()).isEqualTo(13822);
    }

    /**
     * Verified: a full successful run contains the string "model" zero times. Reporting the
     * requested id here would launder a guess into a stored fact, so it stays null.
     */
    @Test
    @DisplayName("does not name an answering model, because opencode never reports one")
    void doesNotInventTheAnsweringModel() {
        assertThat(parser.parseUsage(REAL_SUCCESS)).isPresent();
        assertThat(parser.parseUsage(REAL_SUCCESS).get().model()).isNull();
    }

    @Test
    @DisplayName("usage is absent — not zero — when the tool reported none")
    void absentUsageIsEmptyNotZero() {
        String noUsage = "{\"type\":\"text\",\"part\":{\"type\":\"text\",\"text\":\"HOLD\"}}";

        assertThat(parser.parseUsage(noUsage)).isEmpty();
        assertThat(parser.parseUsage("")).isEmpty();
        assertThat(parser.parseUsage(null)).isEmpty();
    }

    @Test
    @DisplayName("ignores non-JSON chatter interleaved in the stream")
    void ignoresNonJsonLines() throws Exception {
        String noisy = """
                some banner text opencode printed
                {"type":"text","part":{"type":"text","text":"SELL"}}
                """;

        assertThat(parser.parse(noisy)).isEqualTo("SELL");
    }

    @Test
    @DisplayName("a truncated JSON event is skipped, not fatal")
    void skipsMalformedJsonEvents() throws Exception {
        // A killed child can leave a half-written final line. The answer already received
        // should still come back.
        String truncated = """
                {"type":"text","part":{"type":"text","text":"HOLD"}}
                {"type":"step_finish","part":{"tokens":{"input":
                """;

        assertThat(parser.parse(truncated)).isEqualTo("HOLD");
        assertThat(parser.parseUsage(truncated)).isEmpty();
    }

    @Test
    @DisplayName("a step_finish carrying no token block reports no usage")
    void stepFinishWithoutTokensReportsNoUsage() {
        String noTokens = "{\"type\":\"step_finish\",\"part\":{\"type\":\"step-finish\"}}";

        assertThat(parser.parseUsage(noTokens)).isEmpty();
    }

    @Test
    @DisplayName("identifies itself as the opencode parser")
    void reportsItsToolName() {
        assertThat(parser.getToolName()).isEqualTo("opencode");
    }
}
