package com.quorumtrading.application.usecase.result;

import com.quorumtrading.model.enums.GapClassification;
import com.quorumtrading.model.marketdata.MarketDataGap;

/**
 * A {@link MarketDataGap} enriched with its actionability classification, the
 * provider that would fill it (null when none), and the estimated number of
 * records the backfill would produce.
 *
 * @param gap              the underlying gap
 * @param classification   why the gap can (or cannot) be filled now
 * @param provider         provider name that would fill it, or {@code null} if no source
 * @param estimatedRecords expected row count to backfill (never negative)
 */
public record ClassifiedGap(
    MarketDataGap gap,
    GapClassification classification,
    String provider,
    int estimatedRecords
) {}
