package com.quorumtrading.adapter.marketdata.rss;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class RSSFeedAdapter implements MarketDataPort {

    private static final Logger logger = Logger.getLogger(RSSFeedAdapter.class);

    private final List<String> feedUrls;
    private final boolean enabled;
    private final RSSParser parser;
    private final HttpClient httpClient;

    @Inject
    public RSSFeedAdapter(
            @ConfigProperty(name = "quorumtrading.providers.rss.feed-urls") List<String> feedUrls,
            @ConfigProperty(name = "quorumtrading.providers.rss.enabled", defaultValue = "true") boolean enabled) {
        this.feedUrls = feedUrls != null ? feedUrls : Collections.emptyList();
        this.enabled = enabled;
        this.parser = new RSSParser();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.ALWAYS)  // Follow 301/302/etc redirects
            .build();
    }

    @Override
    public List<MarketEvent> fetchLatestData() {
        // Fetch latest news from last 24 hours
        Instant end = Instant.now();
        Instant start = end.minus(24, java.time.temporal.ChronoUnit.HOURS);
        return fetchRSSFeeds(start, end);
    }

    @Override
    public String getProviderName() {
        return "RSS Feeds";
    }

    /**
     * Fetch RSS feeds within a date range.
     * This method is used by both fetchLatestData() and can be called directly
     * for historical data collection.
     */
    public List<MarketEvent> fetchRSSFeeds(Instant start, Instant end) {
        if (!enabled) {
            logger.debug("RSSFeedAdapter is disabled");
            return Collections.emptyList();
        }

        if (feedUrls.isEmpty()) {
            logger.warn("No RSS feed URLs configured");
            return Collections.emptyList();
        }

        List<MarketEvent> allEvents = new ArrayList<>();

        for (String feedUrl : feedUrls) {
            try {
                logger.debugf("Fetching RSS feed: %s", feedUrl);

                HttpResponse<InputStream> response = fetchWithRetry(feedUrl, 3);  // 3 retry attempts

                if (response == null || response.statusCode() != 200) {
                    int statusCode = response != null ? response.statusCode() : 0;
                    logger.warnf("Failed to fetch RSS feed %s: HTTP %d", feedUrl, statusCode);
                    continue;
                }

                // Extract source name from URL
                String sourceName = extractSourceName(feedUrl);

                // Parse feed
                List<Map<String, Object>> items = parser.parse(response.body(), sourceName);

                // Convert to MarketEvents
                for (Map<String, Object> item : items) {
                    // Parse pubDate string back to Instant
                    Instant pubDate = null;
                    Object pubDateObj = item.get("pubDate");
                    if (pubDateObj instanceof String) {
                        try {
                            pubDate = Instant.parse((String) pubDateObj);
                        } catch (Exception e) {
                            logger.warnf(e, "Failed to parse pubDate: %s", pubDateObj);
                            pubDate = Instant.now();
                        }
                    }

                    // Filter by date range
                    if (pubDate != null && (pubDate.isBefore(start) || pubDate.isAfter(end))) {
                        continue;
                    }

                    MarketEvent event = new MarketEvent(
                        pubDate != null ? pubDate : Instant.now(), // timestamp
                        EventType.NEWS,                            // eventType
                        sourceName,                                // source
                        "MARKET",                                  // symbol - generic market news
                        item                                       // rawData
                    );

                    allEvents.add(event);
                }

                logger.debugf("Fetched %d items from %s", items.size(), feedUrl);

            } catch (Exception e) {
                logger.errorf(e, "Failed to fetch RSS feed: %s", feedUrl);
            }
        }

        logger.infof("Fetched %d total RSS feed events", allEvents.size());
        return allEvents;
    }

    private String extractSourceName(String feedUrl) {
        try {
            URI uri = URI.create(feedUrl);
            String host = uri.getHost();

            // Remove www. prefix
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            // Capitalize first letter
            return host.substring(0, 1).toUpperCase() + host.substring(1);

        } catch (Exception e) {
            return "RSS";
        }
    }

    /**
     * Fetches RSS feed with exponential backoff retry for rate limits (HTTP 429).
     * Handles temporary rate limiting gracefully without failing the entire feed.
     *
     * @param feedUrl URL to fetch
     * @param maxAttempts Number of retry attempts (initial + retries)
     * @return HttpResponse if successful, null if all attempts failed
     */
    private HttpResponse<InputStream> fetchWithRetry(String feedUrl, int maxAttempts) {
        int attempt = 1;
        long backoffMs = 2000;  // Start with 2 second backoff (was 1s, too aggressive)

        while (attempt <= maxAttempts) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(feedUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "QuorumTrading/1.0 (RSS Feed Aggregator)")
                    .header("Accept-Language", "en-US,en;q=0.9")  // Add language header to look more like browser
                    .GET()
                    .build();

                HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
                );

                // Success (200) - valid response
                if (response.statusCode() == 200) {
                    // Verify response is not empty
                    if (response.body() != null) {
                        return response;
                    }
                }

                // Rate limited (429) or Too Many Requests (503) - retry with exponential backoff
                if (response.statusCode() == 429 || response.statusCode() == 503) {
                    if (attempt < maxAttempts) {
                        logger.warnf("RSS feed %s rate limited/throttled (HTTP %d), retrying in %dms (attempt %d/%d)",
                            feedUrl, response.statusCode(), backoffMs, attempt, maxAttempts);
                        Thread.sleep(backoffMs);
                        backoffMs = Math.min(backoffMs * 3, 120000);  // Exponential backoff, cap at 2 minutes
                        attempt++;
                        continue;
                    } else {
                        logger.warnf("RSS feed %s exhausted retries after rate limiting (HTTP %d)",
                            feedUrl, response.statusCode());
                        return response;  // Return null-like response to indicate persistent blocking
                    }
                }

                // Temporary server errors - retry
                if (response.statusCode() >= 500 && response.statusCode() < 600) {
                    if (attempt < maxAttempts) {
                        logger.debugf("RSS feed %s server error (HTTP %d), retrying in %dms (attempt %d/%d)",
                            feedUrl, response.statusCode(), backoffMs, attempt, maxAttempts);
                        Thread.sleep(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, 60000);
                        attempt++;
                        continue;
                    }
                }

                // Other HTTP errors (301, 302, 403, 404, etc) - no retry, return as-is for caller to handle
                logger.warnf("RSS feed %s returned HTTP %d (no retry)", feedUrl, response.statusCode());
                return response;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.errorf("RSS feed fetch interrupted: %s", feedUrl);
                return null;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    logger.debugf("RSS feed fetch error (attempt %d/%d): %s - %s",
                        attempt, maxAttempts, feedUrl, e.getMessage());
                    try {
                        Thread.sleep(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, 60000);
                        attempt++;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    logger.errorf("RSS feed fetch failed after %d attempts: %s - %s", maxAttempts, feedUrl, e.getMessage());
                    return null;
                }
            }
        }

        return null;
    }

    @Override
    public Set<MarketDataFeature> getSupportedFeatures() {
        return Set.of(
            MarketDataFeature.NEWS,          // News articles from RSS feeds
            MarketDataFeature.DAILY,         // News is aggregated daily
            MarketDataFeature.RESEARCH       // Free feeds suitable for research/backtesting
            // NOTE: BACKFILL intentionally omitted — RSS feeds only return current articles,
            // not historical news. Alpaca/Finnhub handle historical news backfill.
        );
    }
}
