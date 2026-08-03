package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.AssetClass;
import com.quorumtrading.model.enums.EventCategory;
import com.quorumtrading.model.enums.MarketSentiment;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoricalEventTest {

    @Test
    void shouldCreateValidEvent() {
        var event = new HistoricalEvent(
                1L, "covid-crash", "COVID Crash",
                LocalDate.of(2020, 2, 20), LocalDate.of(2020, 4, 20),
                EventCategory.CRISIS, AssetClass.BOTH, MarketSentiment.BEARISH,
                "Black swan pandemic crash.", "S&P 500: -34%",
                List.of("SPY", "BTC"), List.of("covid", "pandemic"));

        assertEquals("covid-crash", event.slug());
        assertEquals(2, event.suggestedAssets().size());
        assertEquals(2, event.keywords().size());
    }

    @Test
    void shouldRejectEndDateBeforeStartDate() {
        assertThrows(IllegalArgumentException.class, () ->
                new HistoricalEvent(1L, "test", "Test",
                        LocalDate.of(2020, 5, 1), LocalDate.of(2020, 1, 1),
                        EventCategory.CRISIS, AssetClass.STOCK, MarketSentiment.BEARISH,
                        "desc", "impact", List.of(), List.of()));
    }

    @Test
    void shouldRejectNullSlug() {
        assertThrows(NullPointerException.class, () ->
                new HistoricalEvent(1L, null, "Test",
                        LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1),
                        EventCategory.CRISIS, AssetClass.STOCK, MarketSentiment.BEARISH,
                        "desc", "impact", List.of(), List.of()));
    }

    @Test
    void shouldMakeListsImmutable() {
        var event = new HistoricalEvent(
                1L, "test", "Test",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1),
                EventCategory.CRISIS, AssetClass.STOCK, MarketSentiment.BEARISH,
                "desc", "impact", List.of("SPY"), List.of("keyword"));

        assertThrows(UnsupportedOperationException.class, () -> event.suggestedAssets().add("NEW"));
        assertThrows(UnsupportedOperationException.class, () -> event.keywords().add("NEW"));
    }

    @Test
    void shouldHandleNullLists() {
        var event = new HistoricalEvent(
                1L, "test", "Test",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1),
                EventCategory.CRISIS, AssetClass.STOCK, MarketSentiment.BEARISH,
                "desc", "impact", null, null);

        assertEquals(List.of(), event.suggestedAssets());
        assertEquals(List.of(), event.keywords());
    }

    @Test
    void shouldMatchKeyword() {
        var event = new HistoricalEvent(
                1L, "covid-crash", "COVID Crash",
                LocalDate.of(2020, 2, 20), LocalDate.of(2020, 4, 20),
                EventCategory.CRISIS, AssetClass.BOTH, MarketSentiment.BEARISH,
                "desc", "impact", List.of(), List.of("covid", "pandemic", "coronavirus"));

        assertTrue(event.matchesKeyword("covid"));
        assertTrue(event.matchesKeyword("COVID"));
        assertTrue(event.matchesKeyword("pandemic"));
        assertFalse(event.matchesKeyword("bitcoin"));
        assertFalse(event.matchesKeyword(null));
        assertFalse(event.matchesKeyword(""));
    }

    @Test
    void shouldMatchKeywordByNameAndSlug() {
        var event = new HistoricalEvent(
                1L, "covid-crash", "COVID Crash",
                LocalDate.of(2020, 2, 20), LocalDate.of(2020, 4, 20),
                EventCategory.CRISIS, AssetClass.BOTH, MarketSentiment.BEARISH,
                "desc", "impact", List.of(), List.of());

        assertTrue(event.matchesKeyword("crash"));
        assertTrue(event.matchesKeyword("covid-crash"));
    }
}
