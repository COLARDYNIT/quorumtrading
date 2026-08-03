package com.quorumtrading.model.orchestration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationParserTest {

    @ParameterizedTest
    @CsvSource({
        "30s,   30",
        "5m,    300",
        "5min,  300",
        "10m,   600",
        "10min, 600",
        "1h,    3600",
        "6h,    21600",
        "1d,    86400",
        "1w,    604800",
        "2w,    1209600",
    })
    void shouldParseValidDurations(String input, int expectedSeconds) {
        assertThat(DurationParser.parseToSeconds(input)).isEqualTo(expectedSeconds);
        assertThat(DurationParser.parse(input)).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @Test
    void shouldHandleWhitespace() {
        assertThat(DurationParser.parseToSeconds("  1h  ")).isEqualTo(3600);
    }

    @Test
    void shouldBeCaseInsensitive() {
        assertThat(DurationParser.parseToSeconds("1H")).isEqualTo(3600);
        assertThat(DurationParser.parseToSeconds("5M")).isEqualTo(300);
        assertThat(DurationParser.parseToSeconds("5MIN")).isEqualTo(300);
        assertThat(DurationParser.parseToSeconds("1W")).isEqualTo(604800);
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> DurationParser.parse(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null or blank");
    }

    @Test
    void shouldRejectBlank() {
        assertThatThrownBy(() -> DurationParser.parse("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("null or blank");
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThatThrownBy(() -> DurationParser.parse("abc"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid duration format");
    }

    @Test
    void shouldRejectMissingUnit() {
        assertThatThrownBy(() -> DurationParser.parse("100"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid duration format");
    }

    @Test
    void shouldRejectMissingValue() {
        assertThatThrownBy(() -> DurationParser.parse("h"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid duration format");
    }

    @Test
    void shouldRejectUnknownUnit() {
        assertThatThrownBy(() -> DurationParser.parse("5x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid duration format");
    }
}
