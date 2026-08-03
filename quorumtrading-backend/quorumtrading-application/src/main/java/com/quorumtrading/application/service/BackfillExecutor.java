package com.quorumtrading.application.service;

import com.quorumtrading.application.event.BackfillProgressEvent;
import com.quorumtrading.application.event.BackfillStatusChangedEvent;
import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.enums.BackfillStatus;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.exception.MarketDataFetchException;
import com.quorumtrading.model.marketdata.BackfillJob;
import com.quorumtrading.model.marketdata.GapProgress;
import com.quorumtrading.model.marketdata.MarketDataGap;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.RateLimit;
import com.quorumtrading.model.valueobject.Symbol;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that executes backfill jobs by fetching market data from providers.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Break down gaps into smaller batches (100 days at a time)</li>
 *   <li>Call market data adapters to fetch historical data</li>
 *   <li>Store fetched events in MarketEventRepository</li>
 *   <li>Track progress and update job status</li>
 *   <li>Respect rate limits via RateLimitTracker</li>
 * </ul>
 *
 * <p>Design Notes:</p>
 * <ul>
 *   <li>Uses available MarketDataPort instances from CDI</li>
 *   <li>Processes gaps sequentially, allowing partial progress</li>
 *   <li>Skips gaps if rate limited (job remains IN_PROGRESS for next cycle)</li>
 *   <li>Continues processing on individual gap failures</li>
 * </ul>
 */
@ApplicationScoped
public class BackfillExecutor {

    private static final Logger LOG = Logger.getLogger(BackfillExecutor.class);
    private static final int BATCH_SIZE_DAYS = 100;

    @Inject
    BackfillJobRepository jobRepository;

    @Inject
    MarketEventRepository eventRepository;

    @Inject
    Instance<MarketDataPort> marketDataPorts;

    @Inject
    RateLimitTracker rateLimitTracker;

    @Inject
    DataHorizonService dataHorizonService;

    @Inject
    Event<BackfillProgressEvent> progressEvent;

    @Inject
    Event<BackfillStatusChangedEvent> statusEvent;

    @jakarta.annotation.PostConstruct
    void init() {
        LOG.info("BackfillExecutor initializing...");
        if (marketDataPorts.isUnsatisfied()) {
            LOG.warn("No MarketDataPort instances available at initialization!");
        } else {
            LOG.infof("Available MarketDataPort instances for backfill:");
            marketDataPorts.forEach(provider -> {
                LOG.infof("  - %s: features=%s, hasBackfill=%s",
                         provider.getProviderName(),
                         provider.getSupportedFeatures(),
                         provider.hasFeature(MarketDataFeature.BACKFILL));
            });
        }
    }

    /**
     * Execute a backfill job by processing all its gaps.
     *
     * <p>Process as many gaps as possible within rate limits.
     * Progress is saved incrementally. Job is marked COMPLETED when all gaps are processed.
     * If rate limits are hit, job remains IN_PROGRESS for next scheduler cycle.</p>
     *
     * <p>Tracks per-gap status for detailed frontend reporting:</p>
     * <ul>
     *   <li>SUCCESS: Gap was filled with data from provider</li>
     *   <li>FAILED: Provider error occurred (with error message)</li>
     *   <li>SKIPPED: No provider available for this gap type</li>
     * </ul>
     *
     * @param job the backfill job to execute
     */
    public void execute(BackfillJob job) {
        List<MarketDataGap> gaps = job.getGaps();
        int totalGaps = gaps.size();

        // Extract all symbols from the job's gaps for use when fetching market-wide data (NEWS).
        // NEWS gaps are market-wide (no symbol), but providers like Alpaca and Finnhub can fetch
        // ticker-specific news when given the symbols.
        java.util.Set<Symbol> jobSymbols = gaps.stream()
            .map(MarketDataGap::symbol)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());

        // Resume from where we left off - use recordsProcessed as gap index
        int alreadyProcessed = job.getRecordsProcessed() != null ? job.getRecordsProcessed() : 0;
        int processedGaps = alreadyProcessed;
        int skippedGaps = 0;  // Gaps with no provider available (not an error)
        int failedGaps = 0;   // Gaps that threw an exception (actual error)
        int noDataGaps = 0;   // Gaps where a provider ran but returned no data (not an error, not a fill)

        // Track per-gap progress for detailed reporting
        List<GapProgress> allGapProgress = new ArrayList<>();

        LOG.infof("Executing backfill job %s with %d gaps (resuming from gap %d)",
            job.getId(), totalGaps, alreadyProcessed);

        // Fire status changed event: PENDING -> IN_PROGRESS (only if starting fresh)
        if (alreadyProcessed == 0) {
            statusEvent.fire(new BackfillStatusChangedEvent(
                job.getId(),
                BackfillStatus.PENDING,
                BackfillStatus.IN_PROGRESS,
                null,
                totalGaps
            ));
        }

        // Process gaps starting from where we left off
        for (int i = alreadyProcessed; i < totalGaps; i++) {
            MarketDataGap gap = gaps.get(i);
            GapProgress currentGapProgress;

            // Find ALL providers that can handle this gap (for fallback)
            List<MarketDataPort> providers = findProvidersForGap(gap);
            if (providers.isEmpty()) {
                String skipReason = buildNoProviderMessage(gap);
                LOG.warnf("No provider available for gap: %s - %s", gap, skipReason);

                // Create skipped gap progress with detailed reason
                currentGapProgress = GapProgress.skipped(gap, skipReason);
                allGapProgress.add(currentGapProgress);

                skippedGaps++;
                processedGaps++;

                // Update progress even for skipped gaps
                int progressPercentage = (processedGaps * 100) / totalGaps;
                job.updateProgress(processedGaps, totalGaps);
                jobRepository.update(job);

                progressEvent.fire(new BackfillProgressEvent(
                    job.getId(),
                    progressPercentage,
                    processedGaps,
                    totalGaps,
                    currentGapProgress,
                    List.copyOf(allGapProgress)
                ));

                LOG.infof("Skipped gap %d/%d for job %s (%d%%) - no provider",
                    processedGaps, totalGaps, job.getId(), progressPercentage);
                continue;
            }

            // Try each provider until one succeeds (fallback chain)
            boolean gapProcessed = false;
            String lastErrorMessage = null;
            String successProvider = null;
            String lastZeroEventsProvider = null; // Track provider that returned 0 without error
            int eventsFetched = 0;

            for (MarketDataPort provider : providers) {
                try {
                    // Check rate limit
                    RateLimit rateLimit = provider.getRateLimit();
                    if (rateLimitTracker.wouldExceedLimit(provider.getProviderName(), rateLimit)) {
                        LOG.infof("Rate limit reached for %s, trying next provider",
                            provider.getProviderName());
                        continue; // Try next provider
                    }

                    // Process gap and track events fetched
                    eventsFetched = processGapWithCount(gap, provider, job.getGranularity(), jobSymbols);

                    // Only consider it a success if we actually got data
                    // If provider returns 0 events, try the next provider
                    if (eventsFetched > 0) {
                        successProvider = provider.getProviderName();
                        gapProcessed = true;
                        LOG.infof("Successfully processed gap via %s - %d events", successProvider, eventsFetched);
                        break; // Success with data, no need to try other providers
                    } else {
                        lastZeroEventsProvider = provider.getProviderName();
                        LOG.warnf("Provider %s returned 0 events for gap %s, trying next provider...",
                            provider.getProviderName(), gap);
                        // Continue to try next provider
                    }

                } catch (MarketDataFetchException e) {
                    if (e.isRetryable()) {
                        // Rate limited or transient error — schedule retry for the entire job
                        job.scheduleRetry(Instant.now().plus(10, ChronoUnit.MINUTES));
                        jobRepository.update(job);
                        LOG.warnf("Rate limited by %s for job %s (gap %d/%d), retry scheduled in 10 minutes",
                            e.getProviderName(), job.getId(), i + 1, totalGaps);

                        statusEvent.fire(new BackfillStatusChangedEvent(
                            job.getId(),
                            BackfillStatus.IN_PROGRESS,
                            BackfillStatus.RETRY_PENDING,
                            "Rate limited by " + e.getProviderName() + ", retrying in 10 minutes",
                            totalGaps
                        ));
                        return; // Stop processing — don't burn more rate limit
                    }
                    // Non-retryable: treat as provider failure, try next provider
                    lastErrorMessage = e.getMessage();
                    LOG.warnf("Provider %s failed (non-retryable, HTTP %d): %s",
                        e.getProviderName(), e.getHttpStatus(), e.getMessage());
                } catch (Exception e) {
                    lastErrorMessage = extractErrorMessage(e);
                    LOG.warnf("Provider %s failed for gap %s: %s, trying next provider...",
                        provider.getProviderName(), gap, lastErrorMessage);
                    // Continue to try next provider
                }
            }

            processedGaps++;

            if (gapProcessed) {
                // Create successful gap progress (got data)
                currentGapProgress = GapProgress.success(gap, successProvider, eventsFetched);
                allGapProgress.add(currentGapProgress);

                // Feedback: we just observed real data at this gap's start → the
                // provider's earliest can be no later than that. Lowers the cached horizon.
                recordHorizonObserved(gap);

                LOG.infof("Processed gap %d/%d for job %s - %d events via %s",
                    processedGaps, totalGaps, job.getId(), eventsFetched, successProvider);
            } else if (lastZeroEventsProvider != null && lastErrorMessage == null) {
                // All providers ran without error but returned 0 events. Gaps only cover
                // dates where data is expected (the coverage analysis excludes weekends and
                // market holidays), so this means the provider genuinely has no data for this
                // range — not a fill. Record it as NO_DATA so we never report it as success.
                currentGapProgress = GapProgress.noData(gap, lastZeroEventsProvider);
                allGapProgress.add(currentGapProgress);

                noDataGaps++;

                // Feedback: the provider has no data for this past, expected range →
                // raise the cached horizon so future backfills/sims don't retry it.
                recordHorizonNoData(gap);

                LOG.infof("Processed gap %d/%d for job %s - no data available from %s for this range",
                    processedGaps, totalGaps, job.getId(), lastZeroEventsProvider);
            } else {
                // All providers failed with errors
                String errorMessage = lastErrorMessage != null ? lastErrorMessage :
                    "All " + providers.size() + " providers failed";
                LOG.errorf("Failed to process gap %s: %s (tried %d providers)",
                    gap, errorMessage, providers.size());

                currentGapProgress = GapProgress.failed(gap, providers.get(0).getProviderName(), errorMessage);
                allGapProgress.add(currentGapProgress);

                failedGaps++;
            }

            // Update progress
            int progressPercentage = (processedGaps * 100) / totalGaps;
            job.updateProgress(processedGaps, totalGaps);
            jobRepository.update(job);

            // Fire progress event with per-gap details
            progressEvent.fire(new BackfillProgressEvent(
                job.getId(),
                progressPercentage,
                processedGaps,
                totalGaps,
                currentGapProgress,
                List.copyOf(allGapProgress)
            ));
        }

        // Mark as completed if all gaps processed (including skipped/no-data ones)
        if (processedGaps == totalGaps) {
            int successfulGaps = processedGaps - skippedGaps - failedGaps - noDataGaps;

            // Total rows actually fetched and stored across all gaps — this is what we
            // report to the operator, NOT the gap count. A job can "complete" having
            // stored zero rows (e.g. a provider with no history for the range).
            int totalEventsFetched = allGapProgress.stream()
                .filter(g -> g.eventsFetched() != null)
                .mapToInt(GapProgress::eventsFetched)
                .sum();

            // Build detailed summary of failures
            String failureSummary = buildFailureSummary(allGapProgress);

            // Only fail if there were actual errors (FAILED gaps), not just missing providers
            // (SKIPPED) or providers with no data for the range (NO_DATA) — those are expected
            // outcomes and shouldn't cause failure.
            if (failedGaps > 0) {
                String errorMessage = String.format(
                    "Backfill failed: %d/%d gaps failed with errors. %s",
                    failedGaps, totalGaps, failureSummary
                );
                job.markFailed(errorMessage);
                jobRepository.update(job);

                // Fire status changed event: IN_PROGRESS -> FAILED
                statusEvent.fire(new BackfillStatusChangedEvent(
                    job.getId(),
                    BackfillStatus.IN_PROGRESS,
                    BackfillStatus.FAILED,
                    errorMessage,
                    totalEventsFetched
                ));

                LOG.errorf("Backfill job %s FAILED: %d gaps failed, %d no-data, %d skipped, %d with data out of %d",
                    job.getId(), failedGaps, noDataGaps, skippedGaps, successfulGaps, totalGaps);
            } else {
                // Success: Mark as completed (skipped/no-data gaps are OK)
                job.markCompleted();
                jobRepository.update(job);

                // Honest completion note: surface gaps that had no data or no provider so the
                // operator understands a "completed" job that stored nothing.
                String completionNote = buildCompletionNote(allGapProgress);

                // Fire status changed event: IN_PROGRESS -> COMPLETED.
                // The 5th argument carries ROWS FETCHED (not gap count).
                statusEvent.fire(new BackfillStatusChangedEvent(
                    job.getId(),
                    BackfillStatus.IN_PROGRESS,
                    BackfillStatus.COMPLETED,
                    completionNote.isEmpty() ? null : completionNote,
                    totalEventsFetched
                ));

                if (totalEventsFetched == 0) {
                    LOG.warnf("Backfill job %s completed but stored 0 rows (%d gaps had no data, %d skipped, " +
                        "%d reported success; linkedSimulation=%s). No data was available from any provider " +
                        "for the requested range.",
                        job.getId(), noDataGaps, skippedGaps, successfulGaps,
                        job.getLinkedSimulationId().orElse(null));
                } else {
                    LOG.infof("Backfill job %s completed: %d rows fetched across %d gaps with data, " +
                        "%d no-data, %d skipped (no provider available)",
                        job.getId(), totalEventsFetched, successfulGaps, noDataGaps, skippedGaps);
                }
            }
        } else {
            // Partial completion - job remains IN_PROGRESS for next run
            LOG.infof("Backfill job %s paused at %d/%d gaps (will resume in next cycle)",
                job.getId(), processedGaps, totalGaps);
        }
    }

    /**
     * Feedback to the data-horizon cache when a gap was filled: we observed real
     * data at the gap's start, so the provider's earliest is no later than that.
     * Symbol-specific gaps only (market-wide NEWS/SOCIAL have no per-symbol horizon).
     * Never lets a feedback failure break the backfill.
     */
    private void recordHorizonObserved(MarketDataGap gap) {
        if (gap.symbol() == null) {
            return;
        }
        try {
            dataHorizonService.recordObservedFetch(gap.symbol(), gap.dataType(), gap.period().getStart());
        } catch (Exception e) {
            LOG.debugf("Horizon observed-fetch feedback failed for %s %s: %s",
                gap.symbol().ticker(), gap.dataType(), e.getMessage());
        }
    }

    /**
     * Feedback to the data-horizon cache when a gap had no data: the provider has
     * nothing for this past, expected range, so raise the cached horizon.
     * Symbol-specific gaps only. Never lets a feedback failure break the backfill.
     */
    private void recordHorizonNoData(MarketDataGap gap) {
        if (gap.symbol() == null) {
            return;
        }
        try {
            dataHorizonService.recordNoData(gap.symbol(), gap.dataType(), gap.period());
        } catch (Exception e) {
            LOG.debugf("Horizon no-data feedback failed for %s %s: %s",
                gap.symbol().ticker(), gap.dataType(), e.getMessage());
        }
    }

    /**
     * Build a user-friendly message explaining why no provider is available.
     */
    private String buildNoProviderMessage(MarketDataGap gap) {
        MarketDataFeature assetFeature = determineAssetFeature(gap);
        MarketDataFeature dataFeature = determineDataFeature(gap);

        StringBuilder msg = new StringBuilder("No provider supports: BACKFILL");
        if (assetFeature != null) {
            msg.append(" + ").append(assetFeature.name());
        }
        if (dataFeature != null) {
            msg.append(" + ").append(dataFeature.name());
        }
        return msg.toString();
    }

    /**
     * Extract a user-friendly error message from an exception.
     */
    private String extractErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) {
            return e.getClass().getSimpleName();
        }

        // Look for common patterns and extract useful info
        if (message.contains("429") || message.toLowerCase().contains("rate limit")) {
            return "Rate limit exceeded";
        }
        if (message.contains("401") || message.contains("403")) {
            return "Authentication failed";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "Request timed out";
        }
        if (message.contains("connection") || message.contains("connect")) {
            return "Connection failed";
        }

        // Truncate long messages
        return message.length() > 100 ? message.substring(0, 100) + "..." : message;
    }

    /**
     * Try to get the provider name for a gap (for error reporting).
     */
    private String tryGetProviderName(MarketDataGap gap) {
        try {
            MarketDataPort provider = findProviderForGap(gap);
            return provider != null ? provider.getProviderName() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Build a summary of failures for the final status message.
     */
    private String buildFailureSummary(List<GapProgress> allGapProgress) {
        long failedCount = allGapProgress.stream()
            .filter(g -> g.status() == GapProgress.GapStatus.FAILED)
            .count();
        long skippedCount = allGapProgress.stream()
            .filter(g -> g.status() == GapProgress.GapStatus.SKIPPED)
            .count();

        if (failedCount == 0 && skippedCount == 0) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        if (skippedCount > 0) {
            summary.append(skippedCount).append(" gaps had no provider");
        }
        if (failedCount > 0) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(failedCount).append(" gaps failed");
        }
        return summary.toString();
    }

    /**
     * Build an honest note for a completed (non-failed) job, describing gaps that
     * could not be filled. Returns "" when every gap was filled with data.
     */
    private String buildCompletionNote(List<GapProgress> allGapProgress) {
        long noDataCount = allGapProgress.stream()
            .filter(g -> g.status() == GapProgress.GapStatus.NO_DATA)
            .count();
        long skippedCount = allGapProgress.stream()
            .filter(g -> g.status() == GapProgress.GapStatus.SKIPPED)
            .count();

        List<String> parts = new ArrayList<>();
        if (noDataCount > 0) {
            parts.add(noDataCount + " gap(s) had no data available from any provider for the requested range");
        }
        if (skippedCount > 0) {
            parts.add(skippedCount + " gap(s) had no provider available");
        }
        return String.join("; ", parts);
    }

    /**
     * Find all providers that can fetch data for the given gap using feature-based selection.
     *
     * <p>Selects providers based on required features:</p>
     * <ul>
     *   <li>BACKFILL is always required (historical data fetching)</li>
     *   <li>CRYPTO or STOCK based on symbol's investment type</li>
     *   <li>NEWS or SOCIAL_MEDIA based on event type</li>
     *   <li>CANDLES for PRICE data</li>
     * </ul>
     *
     * @param gap the market data gap
     * @return List of MarketDataPort instances that support all required features, empty if none
     */
    private List<MarketDataPort> findProvidersForGap(MarketDataGap gap) {
        if (marketDataPorts.isUnsatisfied()) {
            LOG.warn("No MarketDataPort instances available");
            return List.of();
        }

        // BACKFILL is always required for backfill operations
        MarketDataFeature requiredAssetFeature = determineAssetFeature(gap);
        MarketDataFeature requiredDataFeature = determineDataFeature(gap);

        LOG.debugf("Finding providers for gap: symbol=%s, dataType=%s, requiredAsset=%s, requiredData=%s",
            gap.symbol(), gap.dataType(), requiredAssetFeature, requiredDataFeature);

        // Log each provider's evaluation for debugging
        marketDataPorts.forEach(provider -> {
            boolean hasBackfill = provider.hasFeature(MarketDataFeature.BACKFILL);
            boolean hasAsset = requiredAssetFeature == null || provider.hasFeature(requiredAssetFeature);
            boolean hasData = requiredDataFeature == null || provider.hasFeature(requiredDataFeature);
            LOG.debugf("  Evaluating %s: hasBackfill=%s, hasAsset(%s)=%s, hasData(%s)=%s, features=%s",
                      provider.getProviderName(), hasBackfill, requiredAssetFeature, hasAsset,
                      requiredDataFeature, hasData, provider.getSupportedFeatures());
        });

        // Find ALL providers that support required features (for fallback)
        List<MarketDataPort> matchingProviders = marketDataPorts.stream()
            .filter(provider -> provider.hasFeature(MarketDataFeature.BACKFILL))
            .filter(provider -> requiredAssetFeature == null || provider.hasFeature(requiredAssetFeature))
            .filter(provider -> requiredDataFeature == null || provider.hasFeature(requiredDataFeature))
            .toList();

        // EU-instrument preference: for a European-venue symbol, move providers that explicitly
        // cover the instrument (e.g. Saxo) to the FRONT of the fallback chain so the name is priced
        // from the venue/currency/clock it trades on. Additive and byte-identical for US/crypto/
        // market-wide gaps — see prefersInstrumentCoveringProviders.
        matchingProviders = preferInstrumentCoveringProviders(matchingProviders, gap.symbol());

        if (!matchingProviders.isEmpty()) {
            LOG.infof("Found %d providers for gap: %s %s - %s",
                matchingProviders.size(), gap.symbol(), gap.dataType(),
                matchingProviders.stream().map(MarketDataPort::getProviderName).toList());
        } else {
            LOG.warnf("No provider found supporting: BACKFILL + %s + %s",
                requiredAssetFeature, requiredDataFeature);
        }

        return matchingProviders;
    }

    /**
     * EU-instrument routing preference (additive, mock-safe).
     *
     * <p>For a <b>European-venue</b> symbol, stable-partition the already feature-matched provider
     * list so providers that explicitly {@link MarketDataPort#coversInstrument(Symbol) cover} the
     * instrument (a venue-native source like Saxo) come first, with all others kept in their original
     * order as a fallback. This makes an EU-MIC gap PREFER the venue feed without removing any
     * provider from the chain.</p>
     *
     * <p><b>Byte-identical for everything else.</b> When the symbol is null (market-wide NEWS/SOCIAL)
     * or non-European (US bare ticker, crypto), the original list is returned <i>unchanged</i> and
     * {@code coversInstrument} is never consulted — so US/crypto routing and existing tests are
     * completely untouched.</p>
     */
    private List<MarketDataPort> preferInstrumentCoveringProviders(List<MarketDataPort> providers, Symbol symbol) {
        if (symbol == null || !symbol.isEuropean() || providers.size() < 2) {
            return providers;
        }
        // A provider PREFERS the EU venue iff it both advertises the EU_INSTRUMENT capability and
        // explicitly covers this instrument. The EU_INSTRUMENT gate is what distinguishes a venue-
        // native source (Saxo) from a US-default provider whose coversInstrument() returns the
        // interface default true — without it, every provider would look "covering" and Saxo would
        // get no preference.
        List<MarketDataPort> venueNative = new ArrayList<>();
        List<MarketDataPort> others = new ArrayList<>();
        for (MarketDataPort provider : providers) {
            if (provider.hasFeature(MarketDataFeature.EU_INSTRUMENT) && provider.coversInstrument(symbol)) {
                venueNative.add(provider);
            } else {
                others.add(provider);
            }
        }
        if (venueNative.isEmpty() || others.isEmpty()) {
            // No reordering needed (all venue-native, or none) — preserve the original order exactly.
            return providers;
        }
        LOG.debugf("EU-instrument routing: %s prefers %s ahead of %s",
            symbol.canonical(),
            venueNative.stream().map(MarketDataPort::getProviderName).toList(),
            others.stream().map(MarketDataPort::getProviderName).toList());
        List<MarketDataPort> ordered = new ArrayList<>(providers.size());
        ordered.addAll(venueNative);
        ordered.addAll(others);
        return ordered;
    }

    /**
     * Find the first provider for a gap (legacy method for compatibility).
     * @deprecated Use findProvidersForGap for fallback support
     */
    @SuppressWarnings("unused")
    private MarketDataPort findProviderForGap(MarketDataGap gap) {
        List<MarketDataPort> providers = findProvidersForGap(gap);
        return providers.isEmpty() ? null : providers.get(0);
    }

    /**
     * Determine the required asset feature based on symbol type.
     *
     * @param gap the market data gap
     * @return CRYPTO, STOCK, or null for generic data (news)
     */
    private MarketDataFeature determineAssetFeature(MarketDataGap gap) {
        if (gap.symbol() == null) {
            return null; // Generic data (e.g., market-wide news)
        }

        InvestmentType investmentType = gap.symbol().type();
        if (investmentType == InvestmentType.CRYPTO) {
            return MarketDataFeature.CRYPTO;
        } else if (investmentType == InvestmentType.STOCK) {
            return MarketDataFeature.STOCK;
        }

        return null; // Unknown type
    }

    /**
     * Determine the required data feature based on event type.
     *
     * @param gap the market data gap
     * @return CANDLES for PRICE, NEWS for NEWS, SOCIAL_MEDIA for SENTIMENT, or null
     */
    private MarketDataFeature determineDataFeature(MarketDataGap gap) {
        EventType dataType = gap.dataType();
        if (dataType == null) {
            return null;
        }

        return switch (dataType) {
            case PRICE -> MarketDataFeature.CANDLES;
            case NEWS -> MarketDataFeature.NEWS;
            case SOCIAL -> MarketDataFeature.SOCIAL_MEDIA;
        };
    }

    /**
     * Process a single gap by fetching data in batches and storing events.
     * Returns the total number of events fetched.
     *
     * <p>Breaks large date ranges into smaller batches (100 days) to avoid
     * overwhelming providers and to allow incremental progress.</p>
     *
     * @param gap the market data gap to fill
     * @param provider the market data provider to use
     * @return total number of events fetched and stored
     */
    private int processGapWithCount(MarketDataGap gap, MarketDataPort provider, com.quorumtrading.model.enums.Granularity granularity, java.util.Set<Symbol> jobSymbols) {
        LocalDate current = gap.period().getStart();
        LocalDate end = gap.period().getEnd();

        String symbolDisplay = gap.symbol() != null ? gap.symbol().ticker() : "MARKET";
        LOG.infof("Processing gap: %s for %s from %s to %s (granularity: %s)",
            gap.dataType(), symbolDisplay, current, end, granularity.getDisplayName());

        int totalEventsFetched = 0;

        while (!current.isAfter(end)) {
            // Fetch batch of data
            LocalDate batchEnd = current.plusDays(BATCH_SIZE_DAYS - 1);
            if (batchEnd.isAfter(end)) {
                batchEnd = end;
            }

            totalEventsFetched += fetchAndStoreWithCount(gap, provider, current, batchEnd, granularity, jobSymbols);

            current = batchEnd.plusDays(1);
        }

        return totalEventsFetched;
    }

    /**
     * Process a single gap by fetching data in batches and storing events.
     *
     * <p>Breaks large date ranges into smaller batches (100 days) to avoid
     * overwhelming providers and to allow incremental progress.</p>
     *
     * @param gap the market data gap to fill
     * @param provider the market data provider to use
     * @deprecated Use processGapWithCount instead
     */
    @SuppressWarnings("unused")
    private void processGap(MarketDataGap gap, MarketDataPort provider, com.quorumtrading.model.enums.Granularity granularity) {
        processGapWithCount(gap, provider, granularity, java.util.Set.of());
    }

    /**
     * Fetch data for a date range and store events in repository.
     * Returns the number of events stored.
     *
     * <p>Handles both symbol-specific gaps (PRICE) and market-wide gaps (NEWS/SOCIAL):</p>
     * <ul>
     *   <li>Symbol-specific: Passes ticker to provider for targeted fetch</li>
     *   <li>Market-wide: Passes empty ticker sets to fetch general market data</li>
     * </ul>
     *
     * @param gap the market data gap being filled
     * @param provider the market data provider
     * @param from start date (inclusive)
     * @param to end date (inclusive)
     * @return number of events stored
     */
    private int fetchAndStoreWithCount(MarketDataGap gap, MarketDataPort provider, LocalDate from, LocalDate to, com.quorumtrading.model.enums.Granularity granularity, java.util.Set<Symbol> jobSymbols) {
        String symbolDisplay = gap.symbol() != null ? gap.symbol().ticker() : "MARKET";

        try {
            LOG.infof("Fetching %s data for %s from %s to %s using %s",
                gap.dataType(), symbolDisplay, from, to, provider.getProviderName());

            // Convert LocalDate to Instant for provider API
            Instant startInstant = from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            Instant endInstant = to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);

            // Determine ticker sets based on gap type
            java.util.Set<String> cryptoTickers;
            java.util.Set<String> stockTickers;

            if (gap.isMarketWide()) {
                // Market-wide gap (NEWS/SOCIAL): pass job symbols so providers like Alpaca and
                // Finnhub can fetch ticker-specific news alongside general market news
                cryptoTickers = jobSymbols.stream()
                    .filter(s -> s.type() == InvestmentType.CRYPTO)
                    .map(Symbol::ticker)
                    .collect(java.util.stream.Collectors.toSet());
                stockTickers = jobSymbols.stream()
                    .filter(s -> s.type() == InvestmentType.STOCK)
                    .map(Symbol::ticker)
                    .collect(java.util.stream.Collectors.toSet());
            } else {
                // Symbol-specific gap (PRICE): pass the specific ticker
                Symbol symbol = gap.symbol();
                if (symbol.type() == InvestmentType.CRYPTO) {
                    cryptoTickers = java.util.Set.of(symbol.ticker());
                    stockTickers = java.util.Set.of();
                } else {
                    cryptoTickers = java.util.Set.of();
                    stockTickers = java.util.Set.of(symbol.ticker());
                }
            }

            // Fetch from market data port with explicit granularity
            List<MarketEvent> events = provider.fetchDataInRange(
                cryptoTickers,
                stockTickers,
                startInstant,
                endInstant,
                granularity
            );

            // Filter events to match gap's event type
            List<MarketEvent> filteredEvents = events.stream()
                .filter(event -> event.eventType() == gap.dataType())
                .toList();

            // Store events
            int storedCount = 0;
            for (MarketEvent event : filteredEvents) {
                eventRepository.save(event);
                storedCount++;
            }

            // Track API call for rate limiting
            rateLimitTracker.recordCall(provider.getProviderName());

            LOG.infof("Stored %d %s events for %s (fetched %d total events)",
                storedCount, gap.dataType(), symbolDisplay, events.size());

            return storedCount;

        } catch (MarketDataFetchException e) {
            // Let typed market data exceptions propagate for proper handling in the provider loop
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Failed to fetch %s data for %s", gap.dataType(), symbolDisplay);
            throw new RuntimeException("Backfill failed: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch data for a date range and store events in repository.
     *
     * @deprecated Use fetchAndStoreWithCount instead
     */
    @SuppressWarnings("unused")
    private void fetchAndStore(MarketDataGap gap, MarketDataPort provider, LocalDate from, LocalDate to, com.quorumtrading.model.enums.Granularity granularity) {
        fetchAndStoreWithCount(gap, provider, from, to, granularity, java.util.Set.of());
    }
}
