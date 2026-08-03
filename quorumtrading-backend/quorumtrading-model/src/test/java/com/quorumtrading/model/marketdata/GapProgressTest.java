package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GapProgressTest {

    private static MarketDataGap btcPriceGap() {
        return new MarketDataGap(
            new Symbol("BTC", InvestmentType.CRYPTO),
            EventType.PRICE,
            TimeRange.of(LocalDate.of(2022, 3, 1), LocalDate.of(2022, 3, 31))
        );
    }

    @Test
    void noData_marksGapAsNoDataWithZeroEvents() {
        GapProgress progress = GapProgress.noData(btcPriceGap(), "alpaca");

        assertThat(progress.status()).isEqualTo(GapProgress.GapStatus.NO_DATA);
        assertThat(progress.eventsFetched()).isZero();
        assertThat(progress.providerName()).isEqualTo("alpaca");
        assertThat(progress.errorMessage()).isNotNull();
    }

    @Test
    void noData_isDistinctFromSuccess() {
        GapProgress noData = GapProgress.noData(btcPriceGap(), "alpaca");
        GapProgress success = GapProgress.success(btcPriceGap(), "alpaca", 0);

        // Both report 0 events, but their status must differ so the operator can
        // tell "filled, no rows for non-trading days" apart from "no data at all".
        assertThat(noData.status()).isNotEqualTo(success.status());
        assertThat(success.status()).isEqualTo(GapProgress.GapStatus.SUCCESS);
    }
}
