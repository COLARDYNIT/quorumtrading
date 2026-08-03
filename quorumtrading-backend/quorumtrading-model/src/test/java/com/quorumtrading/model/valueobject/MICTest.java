package com.quorumtrading.model.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class MICTest {

    @ParameterizedTest
    @ValueSource(strings = {"XNAS", "XETR", "XBRU", "XAMS", "ARCX", "IEXG", "BME2"})
    void shouldAcceptValidIso10383Codes(String code) {
        MIC mic = new MIC(code);

        assertThat(mic.code()).isEqualTo(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "US", "XNA", "XNASD", "xnas", "XN-S", "X NA", "1234ABC"})
    void shouldRejectInvalidCodes(String code) {
        assertThatThrownBy(() -> new MIC(code))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid MIC");
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> new MIC(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usDefaultIsAUsVenue() {
        assertThat(MIC.US_DEFAULT.code()).isEqualTo("XNAS");
        assertThat(MIC.US_DEFAULT.isUS()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"XNAS", "XNYS", "XASE", "ARCX", "BATS", "IEXG", "XCBO", "OOTC"})
    void recognisesPrincipalUsVenues(String code) {
        assertThat(new MIC(code).isUS()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"XETR", "XBRU", "XAMS", "XLON", "XPAR", "XMIL"})
    void europeanVenuesAreNotUs(String code) {
        assertThat(new MIC(code).isUS()).isFalse();
    }

    @Test
    void shouldSupportValueEquality() {
        assertThat(new MIC("XETR")).isEqualTo(new MIC("XETR"));
        assertThat(new MIC("XETR").hashCode()).isEqualTo(new MIC("XETR").hashCode());
        assertThat(new MIC("XETR")).isNotEqualTo(new MIC("XBRU"));
    }
}
