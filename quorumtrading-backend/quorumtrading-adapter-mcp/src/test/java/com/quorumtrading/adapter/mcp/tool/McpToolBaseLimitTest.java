package com.quorumtrading.adapter.mcp.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shared {@code limit} contract every MCP list tool now goes through.
 *
 * <p>Before this helper existed the same argument behaved four different ways across six tools:
 * {@code limit=-3} leaked {@code fromIndex(0) > toIndex(-3)} from two of them, produced
 * "Max results cannot be negative" from two more, and was ignored outright by the last two — where
 * {@code limit=0} returned <em>every</em> row. These tests pin the one behaviour.</p>
 */
@DisplayName("McpToolBase limit handling")
class McpToolBaseLimitTest {

    private final TestableToolBase tool = new TestableToolBase();

    @Test
    @DisplayName("absent limit falls back to the tool's documented default")
    void shouldUseDefaultWhenAbsent() {
        assertThat(tool.parseLimit(Optional.empty(), 20, 100)).isEqualTo(20);
    }

    @Test
    @DisplayName("a limit inside the range is honoured exactly")
    void shouldHonourAnInRangeLimit() {
        assertThat(tool.parseLimit(Optional.of(7), 20, 100)).isEqualTo(7);
    }

    @Test
    @DisplayName("exactly the documented max is valid, not off-by-one rejected")
    void shouldAcceptExactlyTheMax() {
        assertThat(tool.parseLimit(Optional.of(100), 20, 100)).isEqualTo(100);
    }

    @Test
    @DisplayName("above the documented max clamps down instead of over-returning")
    void shouldClampAboveTheMax() {
        assertThat(tool.parseLimit(Optional.of(5000), 20, 100)).isEqualTo(100);
    }

    @Test
    @DisplayName("zero is refused — an empty page is indistinguishable from an empty account")
    void shouldRejectZero() {
        assertThatThrownBy(() -> tool.parseLimit(Optional.of(0), 20, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100 (got 0)");
    }

    @Test
    @DisplayName("a negative limit is refused with a message naming the parameter and the bound")
    void shouldRejectNegative() {
        assertThatThrownBy(() -> tool.parseLimit(Optional.of(-3), 20, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100 (got -3)");
    }

    @Test
    @DisplayName("the rejection names the tool's own parameter, not always 'limit'")
    void shouldNameTheParameter() {
        assertThatThrownBy(() -> tool.parseLimit(Optional.of(-1), 5, 20, "executionLimit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("executionLimit must be between 1 and 20 (got -1)");
    }

    @Test
    @DisplayName("a short result set is returned whole with hasMore=false")
    void shouldReturnEverythingBelowTheLimit() {
        McpToolBase.Page<String> page = tool.paginate(List.of("a", "b"), 5);

        assertThat(page.items()).containsExactly("a", "b");
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    @DisplayName("exactly limit rows is not 'more' — the over-fetch row is the only evidence")
    void shouldNotClaimMoreOnAnExactPage() {
        McpToolBase.Page<String> page = tool.paginate(List.of("a", "b", "c"), 3);

        assertThat(page.items()).containsExactly("a", "b", "c");
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    @DisplayName("an over-fetched set is trimmed and the extra row never reaches the caller")
    void shouldTrimAnOverFetchedSet() {
        McpToolBase.Page<String> page = tool.paginate(List.of("a", "b", "c", "d"), 3);

        assertThat(page.items()).containsExactly("a", "b", "c");
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    @DisplayName("truncation re-checks the bound rather than leaking a java.util index error")
    void shouldRefuseToTruncateWithAnInvalidBound() {
        assertThatThrownBy(() -> tool.paginate(List.of("a", "b"), -3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit")
                .hasMessageNotContaining("fromIndex");
    }

    /** Minimal concrete subclass exposing the protected helpers under test. */
    private static class TestableToolBase extends McpToolBase {
        @Override
        public int parseLimit(Optional<Integer> limit, int defaultLimit, int maxLimit) {
            return super.parseLimit(limit, defaultLimit, maxLimit);
        }

        @Override
        public int parseLimit(Optional<Integer> limit, int defaultLimit, int maxLimit, String paramName) {
            return super.parseLimit(limit, defaultLimit, maxLimit, paramName);
        }

        @Override
        public <T> Page<T> paginate(List<T> fetched, int effectiveLimit) {
            return super.paginate(fetched, effectiveLimit);
        }
    }
}
