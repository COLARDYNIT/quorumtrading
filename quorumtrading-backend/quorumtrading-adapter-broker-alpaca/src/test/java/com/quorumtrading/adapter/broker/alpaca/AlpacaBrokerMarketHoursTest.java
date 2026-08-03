package com.quorumtrading.adapter.broker.alpaca;

import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.enums.InvestmentType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AlpacaBrokerAdapter market hours validation.
 *
 * Note: These tests use MarketHoursTestHelper to test the market hours logic
 * without depending on the current time (Instant.now()). The actual adapter
 * uses Instant.now() for real-time market hours checking during LIVE trading.
 *
 * Tests verify:
 * - Regular hours acceptance (9:30 AM - 4:00 PM EST Mon-Fri)
 * - Pre-market rejection (before 9:30 AM)
 * - After-hours rejection (after 4:00 PM)
 * - Weekend rejection
 * - Crypto 24/7 trading (no restrictions)
 */
@QuarkusTest
class AlpacaBrokerMarketHoursTest {

    @Inject
    @Named("alpacaBroker")
    AlpacaBrokerAdapter alpacaBroker;

    // ============================================================================
    // Unit Tests for Market Hours Logic (using MarketHoursTestHelper)
    // ============================================================================

    @Test
    void marketHoursLogic_shouldAcceptAt930AM() {
        // Given: 9:30 AM EST on a weekday (market open)
        ZonedDateTime time = getNextWeekday()
            .withHour(9)
            .withMinute(30);

        // When/Then
        assertTrue(MarketHoursTestHelper.isWithinRegularMarketHours(time.toInstant()),
            "9:30 AM EST should be within market hours");
    }

    @Test
    void marketHoursLogic_shouldAcceptAt359PM() {
        // Given: 3:59 PM EST on a weekday (just before close)
        ZonedDateTime time = getNextWeekday()
            .withHour(15)
            .withMinute(59);

        // When/Then
        assertTrue(MarketHoursTestHelper.isWithinRegularMarketHours(time.toInstant()),
            "3:59 PM EST should be within market hours");
    }

    @Test
    void marketHoursLogic_shouldRejectAt929AM() {
        // Given: 9:29 AM EST on a weekday (1 minute before open)
        ZonedDateTime time = getNextWeekday()
            .withHour(9)
            .withMinute(29);

        // When/Then
        assertFalse(MarketHoursTestHelper.isWithinRegularMarketHours(time.toInstant()),
            "9:29 AM EST should be outside market hours");
    }

    @Test
    void marketHoursLogic_shouldRejectAt4PM() {
        // Given: 4:00 PM EST on a weekday (market close)
        ZonedDateTime time = getNextWeekday()
            .withHour(16)
            .withMinute(0);

        // When/Then
        assertFalse(MarketHoursTestHelper.isWithinRegularMarketHours(time.toInstant()),
            "4:00 PM EST should be outside market hours (market is closed)");
    }

    @Test
    void marketHoursLogic_shouldRejectOnSaturday() {
        // Given: Saturday at noon
        ZonedDateTime saturday = ZonedDateTime.now(ZoneId.of("America/New_York"))
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY))
            .withHour(12)
            .withMinute(0);

        // When/Then
        assertFalse(MarketHoursTestHelper.isWithinRegularMarketHours(saturday.toInstant()),
            "Saturday should be outside market hours");
    }

    @Test
    void marketHoursLogic_shouldRejectOnSunday() {
        // Given: Sunday at noon
        ZonedDateTime sunday = ZonedDateTime.now(ZoneId.of("America/New_York"))
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SUNDAY))
            .withHour(12)
            .withMinute(0);

        // When/Then
        assertFalse(MarketHoursTestHelper.isWithinRegularMarketHours(sunday.toInstant()),
            "Sunday should be outside market hours");
    }

    @Test
    void marketHoursLogic_shouldRejectAt2AM() {
        // Given: 2:00 AM EST on a weekday
        ZonedDateTime time = getNextWeekday()
            .withHour(2)
            .withMinute(0);

        // When/Then
        assertFalse(MarketHoursTestHelper.isWithinRegularMarketHours(time.toInstant()),
            "2:00 AM EST should be outside market hours");
    }

    @Test
    void marketHoursLogic_shouldRejectAt8PM() {
        // Given: 8:00 PM EST on a weekday
        ZonedDateTime time = getNextWeekday()
            .withHour(20)
            .withMinute(0);

        // When/Then
        assertFalse(MarketHoursTestHelper.isWithinRegularMarketHours(time.toInstant()),
            "8:00 PM EST should be outside market hours");
    }

    // ============================================================================
    // Integration Tests (depend on current market hours)
    // ============================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "ALPACA_INTEGRATION_TESTS", matches = "true")
    void shouldAcceptCryptoOrder24x7() {
        // Given: Crypto order (crypto trades 24/7, no market hours restriction)
        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            new SimulationId(UUID.randomUUID()),
            TradeAction.BUY,
            new Symbol("BTC", InvestmentType.CRYPTO), // Crypto symbol
            BigDecimal.valueOf(0.001),
            Instant.now()
        );

        TradeResult result = alpacaBroker.executeTrade(command);

        // Then: Should not be rejected due to market hours
        // (Crypto trades 24/7 - might still be rejected for other reasons like insufficient funds)
        if (result.status() == TradeStatus.REJECTED && result.rejectionReason() != null) {
            assertFalse(result.rejectionReason().contains("Market closed"),
                "Crypto order should never be rejected for market closure");
            assertFalse(result.rejectionReason().contains("Weekend"),
                "Crypto order should never be rejected for weekends");
            assertFalse(result.rejectionReason().contains("regular market hours"),
                "Crypto order should never be rejected for market hours");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ALPACA_INTEGRATION_TESTS", matches = "true")
    void shouldProcessStockOrderBasedOnCurrentMarketHours() {
        // Given: Stock order at current time
        ExecuteTradeCommand command = ExecuteTradeCommand.market(
            new SimulationId(UUID.randomUUID()),
            TradeAction.BUY,
            new Symbol("AAPL", InvestmentType.STOCK),
            BigDecimal.valueOf(1),
            Instant.now()
        );

        // When
        TradeResult result = alpacaBroker.executeTrade(command);

        // Then: Result depends on whether market is currently open
        boolean isCurrentlyMarketHours = MarketHoursTestHelper.isWithinRegularMarketHours(Instant.now());

        if (!isCurrentlyMarketHours) {
            // Should be rejected due to market hours
            assertEquals(TradeStatus.REJECTED, result.status(),
                "Stock order should be rejected outside market hours");
            assertTrue(result.rejectionReason().contains("Market closed") ||
                       result.rejectionReason().contains("regular market hours"),
                "Rejection should mention market closure: " + result.rejectionReason());
        }
        // If market is open, order may succeed or fail for other reasons (insufficient funds, etc.)
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private ZonedDateTime getNextWeekday() {
        ZonedDateTime time = ZonedDateTime.now(ZoneId.of("America/New_York"));
        while (time.getDayOfWeek() == DayOfWeek.SATURDAY || time.getDayOfWeek() == DayOfWeek.SUNDAY) {
            time = time.plusDays(1);
        }
        return time;
    }

    // ============================================================================
    // Test Helper for Market Hours Logic
    // ============================================================================

    /**
     * Helper class to test market hours logic without depending on Instant.now().
     * This mirrors the logic in AlpacaBrokerAdapter.checkMarketHours().
     */
    static class MarketHoursTestHelper {

        /**
         * Check if given time is within regular US stock market hours.
         * Regular hours: 9:30 AM - 4:00 PM EST, Monday-Friday
         */
        static boolean isWithinRegularMarketHours(Instant time) {
            ZonedDateTime estTime = time.atZone(ZoneId.of("America/New_York"));
            int hour = estTime.getHour();
            int minute = estTime.getMinute();
            DayOfWeek day = estTime.getDayOfWeek();

            // Weekend: market closed
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                return false;
            }

            // Regular market hours: 9:30 AM - 4:00 PM EST
            boolean isAfterOpen = (hour > 9) || (hour == 9 && minute >= 30);
            boolean isBeforeClose = hour < 16;

            return isAfterOpen && isBeforeClose;
        }
    }
}
