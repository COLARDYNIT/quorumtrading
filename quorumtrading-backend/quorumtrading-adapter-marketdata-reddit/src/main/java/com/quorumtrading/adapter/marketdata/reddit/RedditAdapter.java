package com.quorumtrading.adapter.marketdata.reddit;

import com.quorumtrading.application.port.out.MarketDataPort;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.enums.MarketDataFeature;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.marketdata.DataQualityMetadata;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.HexFormat;

/**
 * Reddit API adapter for fetching cryptocurrency sentiment from Reddit.
 *
 * <p>Fetches hot posts from cryptocurrency-related subreddits using Reddit's
 * public JSON API. No authentication required for read-only access to public posts.</p>
 *
 * <p><strong>Implementation Details:</strong></p>
 * <ul>
 *   <li>Uses Reddit public JSON API (no OAuth needed for public data)</li>
 *   <li>Fetches from r/CryptoCurrency and r/wallstreetbets</li>
 *   <li>Extracts crypto symbol mentions (BTC, ETH, SOL, etc.)</li>
 *   <li>Creates MarketEvent with post metadata (score, comments, timestamp)</li>
 *   <li>Respects Reddit rate limits (~60 requests/minute)</li>
 * </ul>
 *
 * <p><strong>Data Extracted:</strong></p>
 * <ul>
 *   <li>Post title, score (upvotes), comment count</li>
 *   <li>Crypto symbol mentions (BTC, ETH, SOL, DOGE, etc.)</li>
 *   <li>Timestamp, author, permalink</li>
 * </ul>
 *
 * @see MarketDataPort
 */
@ApplicationScoped
public class RedditAdapter implements MarketDataPort {

    private static final Logger LOG = Logger.getLogger(RedditAdapter.class);

    @Inject
    MarketEventRepository repository;

    @ConfigProperty(name = "quorumtrading.providers.reddit.min-upvotes", defaultValue = "500")
    int minUpvotes;

    @ConfigProperty(name = "quorumtrading.providers.reddit.enabled", defaultValue = "true")
    boolean enabled;

    private static final String REDDIT_BASE_URL = "https://www.reddit.com";
    private static final String USER_AGENT = "QuorumTrading/1.0";
    private static final List<String> SUBREDDITS = List.of("CryptoCurrency", "wallstreetbets");
    private static final String WORLDNEWS_SUBREDDIT = "worldnews";

    // Common crypto symbols to detect in posts
    private static final Set<String> CRYPTO_SYMBOLS = Set.of(
        "BTC", "ETH", "SOL", "ADA", "DOGE", "DOT", "MATIC", "LINK",
        "UNI", "AVAX", "ATOM", "XRP", "LTC", "BCH", "SHIB", "APE",
        "BITCOIN", "ETHEREUM", "SOLANA", "CARDANO", "DOGECOIN", "POLKADOT",
        "POLYGON", "CHAINLINK", "UNISWAP", "AVALANCHE", "COSMOS", "RIPPLE",
        "LITECOIN", "BITCOIN CASH", "SHIBA INU", "APECOIN"
    );

    // Stock symbols and company names for r/worldnews detection
    private static final Set<String> STOCK_SYMBOLS = Set.of(
        "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "NVDA", "META", "NFLX",
        "PYPL", "ADBE", "ORCL", "APPLE", "MICROSOFT", "GOOGLE", "ALPHABET",
        "AMAZON", "TESLA", "NVIDIA", "FACEBOOK", "NETFLIX", "PAYPAL",
        "ADOBE", "ORACLE", "S&P 500", "S&P", "NASDAQ", "DOW JONES", "DOW",
        "EXXON", "CHEVRON", "BP", "SHELL", "OIL", "CRUDE",
        "GOLDMAN SACHS", "JP MORGAN", "JPMORGAN", "MORGAN STANLEY",
        "BANK OF AMERICA", "WELLS FARGO", "CITIGROUP"
    );

    // Financial keywords to filter r/worldnews posts for market relevance
    private static final Set<String> FINANCIAL_KEYWORDS = Set.of(
        "trade", "tariff", "economy", "economic", "stock", "market",
        "price", "inflation", "gdp", "shares", "earnings", "profit",
        "revenue", "billion", "million", "sanctions", "export", "import",
        "oil", "energy", "currency", "dollar", "euro", "yuan",
        "central bank", "fed", "ecb", "interest rate",
        "recession", "growth", "unemployment", "investor", "investment"
    );

    // Pattern to match crypto symbols in text (word boundaries to avoid false positives)
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("\\b(" +
        String.join("|", CRYPTO_SYMBOLS) + ")\\b", Pattern.CASE_INSENSITIVE);

    // Pattern to match all symbols (crypto + stocks) for worldnews extraction
    private static final Set<String> ALL_SYMBOLS = createAllSymbolsSet();
    private static final Pattern ALL_SYMBOLS_PATTERN = Pattern.compile("\\b(" +
        String.join("|", ALL_SYMBOLS) + ")\\b", Pattern.CASE_INSENSITIVE);

    private static Set<String> createAllSymbolsSet() {
        Set<String> allSymbols = new HashSet<>();
        allSymbols.addAll(CRYPTO_SYMBOLS);
        allSymbols.addAll(STOCK_SYMBOLS);
        return allSymbols;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<MarketEvent> fetchLatestData() {
        if (!enabled) {
            LOG.debug("Reddit adapter is disabled, skipping data fetch");
            return List.of();
        }

        // Fetch latest data: crypto sentiment + worldnews
        Instant end = Instant.now();
        Instant start = end.minus(24, ChronoUnit.HOURS);

        List<MarketEvent> allEvents = new ArrayList<>();

        // Fetch crypto sentiment from r/CryptoCurrency and r/wallstreetbets
        allEvents.addAll(fetchMarketSentiment(start, end));

        // Fetch financial worldnews from r/worldnews
        allEvents.addAll(fetchGeneralMarketNews(start, end));

        LOG.infof("Reddit adapter collected %d total events (%d sentiment + %d worldnews)",
                allEvents.size(),
                allEvents.stream().filter(e -> e.eventType() == EventType.SOCIAL).count(),
                allEvents.stream().filter(e -> e.eventType() == EventType.NEWS).count());

        return allEvents;
    }

    /**
     * Fetch Reddit data within a time range.
     * Reddit adapter ignores crypto and stock ticker parameters - it fetches general market sentiment + worldnews.
     *
     * @param cryptoTickers Crypto tickers - ignored by this adapter
     * @param stockTickers  Stock tickers - ignored by this adapter
     * @param start         Start time
     * @param end           End time
     * @return Market sentiment events + worldnews
     */
    @Override
    public List<MarketEvent> fetchDataInRange(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant start,
            Instant end) {
        if (!enabled) {
            LOG.debug("Reddit adapter is disabled, skipping data fetch");
            return List.of();
        }

        // Reddit adapter fetches general market sentiment + worldnews, ignoring ticker parameters
        List<MarketEvent> allEvents = new ArrayList<>();
        allEvents.addAll(fetchMarketSentiment(start, end));
        allEvents.addAll(fetchGeneralMarketNews(start, end));
        return allEvents;
    }

    public List<MarketEvent> fetchSymbolPriceData(Symbol symbol, Instant start, Instant end) {
        throw new UnsupportedOperationException("Reddit does not provide price data");
    }

    public List<MarketEvent> fetchSymbolNews(Symbol symbol, Instant start, Instant end) {
        throw new UnsupportedOperationException("Reddit does not provide news articles");
    }

    public List<MarketEvent> fetchSymbolSentiment(Symbol symbol, Instant start, Instant end) {
        LOG.debugf("Fetching Reddit sentiment for symbol %s from %s to %s", symbol.ticker(), start, end);

        // 1. Check cache
        List<MarketEvent> cachedEvents = repository.findBySourcePatternAndSymbolAndTimeRange(
            "reddit-%", symbol.ticker(), start, end
        ).stream()
            .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
            .collect(Collectors.toList());

        LOG.debugf("Found %d cached events for %s", cachedEvents.size(), symbol.ticker());

        // 2. Detect gaps
        List<TimeRange> gaps = detectGaps(cachedEvents, start, end);
        LOG.debugf("Detected %d gaps in cached data", gaps.size());

        // 3. Fetch missing data from API for each gap
        List<MarketEvent> freshEvents = new ArrayList<>();
        for (TimeRange gap : gaps) {
            try {
                for (String subreddit : SUBREDDITS) {
                    List<MarketEvent> gapEvents = fetchSubredditPosts(subreddit, symbol, gap.start(), gap.end());
                    freshEvents.addAll(gapEvents);
                }
            } catch (Exception e) {
                LOG.warnf(e, "Failed to fetch gap data for range %s", gap);
            }
        }

        LOG.debugf("Fetched %d fresh events from API", freshEvents.size());

        // 4. Persist new events
        if (!freshEvents.isEmpty()) {
            persistEvents(freshEvents);
        }

        // 5. Merge and return
        List<MarketEvent> allEvents = new ArrayList<>(cachedEvents);
        allEvents.addAll(freshEvents);
        allEvents.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));

        LOG.infof("Reddit adapter returning %d total events for %s (%d cached, %d fresh)",
            allEvents.size(), symbol.ticker(), cachedEvents.size(), freshEvents.size());

        return allEvents;
    }

    public List<MarketEvent> fetchGeneralMarketNews(Instant start, Instant end) {
        LOG.debugf("Fetching general market news from r/%s from %s to %s", WORLDNEWS_SUBREDDIT, start, end);

        List<MarketEvent> allEvents = new ArrayList<>();

        try {
            List<MarketEvent> events = fetchWorldNewsPosts(start, end);
            allEvents.addAll(events);
            LOG.debugf("Fetched %d events from r/%s", events.size(), WORLDNEWS_SUBREDDIT);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to fetch posts from r/%s", WORLDNEWS_SUBREDDIT);
        }

        // Filter out events that already exist in database (by content_hash)
        List<MarketEvent> newEvents = filterNewEvents(allEvents, start, end);

        int duplicatesFiltered = allEvents.size() - newEvents.size();
        if (duplicatesFiltered > 0) {
            LOG.debugf("Filtered out %d duplicate events already in database", duplicatesFiltered);
        }

        LOG.infof("Reddit adapter collected %d news events from r/%s (min upvotes: %d)",
                newEvents.size(), WORLDNEWS_SUBREDDIT, minUpvotes);
        return newEvents;
    }

    public List<MarketEvent> fetchEconomicIndicators(Instant start, Instant end) {
        throw new UnsupportedOperationException("Reddit does not provide economic indicators");
    }

    public List<MarketEvent> fetchMarketSentiment(Instant start, Instant end) {
        LOG.debugf("Fetching general market sentiment from %s to %s", start, end);

        List<MarketEvent> allEvents = new ArrayList<>();

        // Search for multiple popular crypto symbols to get general sentiment
        List<Symbol> popularSymbols = List.of(
            new Symbol("BTC", InvestmentType.CRYPTO),
            new Symbol("ETH", InvestmentType.CRYPTO),
            new Symbol("SOL", InvestmentType.CRYPTO));

        for (String subreddit : SUBREDDITS) {
            for (Symbol symbol : popularSymbols) {
                try {
                    List<MarketEvent> events = fetchSubredditPosts(subreddit, symbol, start, end);
                    allEvents.addAll(events);
                    LOG.debugf("Fetched %d events for %s from r/%s", events.size(), symbol.ticker(), subreddit);
                } catch (Exception e) {
                    LOG.warnf(e, "Failed to fetch posts for %s from r/%s", symbol.ticker(), subreddit);
                }
            }
        }

        // Filter out events that already exist in database (by content_hash)
        List<MarketEvent> newEvents = filterNewEvents(allEvents, start, end);

        int duplicatesFiltered = allEvents.size() - newEvents.size();
        if (duplicatesFiltered > 0) {
            LOG.debugf("Filtered out %d duplicate events already in database", duplicatesFiltered);
        }

        LOG.infof("Reddit adapter collected %d total events from %d subreddits",
                 newEvents.size(), SUBREDDITS.size());
        return newEvents;
    }

    public List<MarketEvent> searchNews(String query, Instant start, Instant end) {
        throw new UnsupportedOperationException("Reddit does not provide news articles");
    }

    @Override
    public String getProviderName() {
        return "reddit";
    }

    /**
     * Builds Reddit search URL for fetching posts by symbol.
     * Uses /search.json instead of /hot.json for better historical reach.
     *
     * @param subreddit Subreddit name
     * @param symbol Symbol to search for
     * @param after Pagination token (null for first page)
     * @return Complete search URL
     */
    private String buildSearchUrl(String subreddit, Symbol symbol, String after) {
        String base = String.format(
            "%s/r/%s/search.json?q=%s&restrict_sr=true&sort=new&limit=100",
            REDDIT_BASE_URL,
            subreddit,
            symbol.ticker()
        );
        return after != null ? base + "&after=" + after : base;
    }

    /**
     * Fetches posts from a specific subreddit using Reddit's search API with pagination.
     * Searches for symbol mentions and paginates back in time until reaching the start boundary.
     *
     * @param subreddit Subreddit name (without r/ prefix)
     * @param symbol Symbol to search for
     * @param start Start of time range (stop pagination when posts older than this)
     * @param end End of time range (filter out posts newer than this)
     * @return List of MarketEvent objects created from posts within time range
     */
    private List<MarketEvent> fetchSubredditPosts(String subreddit, Symbol symbol, Instant start, Instant end) {
        Client client = ClientBuilder.newClient();
        try {
            List<MarketEvent> allEvents = new ArrayList<>();
            String after = null;
            int maxIterations = 5; // Max ~500 posts total

            for (int i = 0; i < maxIterations; i++) {
                String url = buildSearchUrl(subreddit, symbol, after);

                Response response = client.target(url)
                    .request()
                    .header("User-Agent", USER_AGENT)
                    .get();

                if (response.getStatus() != 200) {
                    LOG.warnf("Reddit API returned status %d for r/%s", response.getStatus(), subreddit);
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> json = response.readEntity(Map.class);
                List<MarketEvent> batch = parsePosts(json, subreddit);

                // Filter to requested time range
                List<MarketEvent> filtered = batch.stream()
                    .filter(event -> !event.timestamp().isBefore(start) && !event.timestamp().isAfter(end))
                    .collect(Collectors.toList());

                allEvents.addAll(filtered);

                // Stop if we've gone past the start boundary (posts sorted newest first)
                if (!batch.isEmpty()) {
                    Instant oldestInBatch = batch.get(batch.size() - 1).timestamp();
                    if (oldestInBatch.isBefore(start)) {
                        LOG.debugf("Reached start boundary at %s, stopping pagination", oldestInBatch);
                        break;
                    }
                }

                // Get next page token
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) json.get("data");
                after = (String) data.get("after");

                if (after == null) {
                    LOG.debugf("No more pages available for r/%s", subreddit);
                    break;
                }

                // Small delay to respect rate limits
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            return allEvents;

        } finally {
            client.close();
        }
    }

    /**
     * Parses Reddit JSON response and creates MarketEvent objects.
     *
     * @param json JSON response from Reddit API
     * @param subreddit Subreddit name for metadata
     * @return List of MarketEvent objects
     */
    @SuppressWarnings("unchecked")
    private List<MarketEvent> parsePosts(Map<String, Object> json, String subreddit) {
        List<MarketEvent> events = new ArrayList<>();

        Map<String, Object> data = (Map<String, Object>) json.get("data");
        if (data == null) {
            return events;
        }

        List<Map<String, Object>> children = (List<Map<String, Object>>) data.get("children");
        if (children == null) {
            return events;
        }

        for (Map<String, Object> child : children) {
            Map<String, Object> post = (Map<String, Object>) child.get("data");
            if (post == null) continue;

            String title = (String) post.get("title");
            if (title == null) continue;

            // Extract crypto symbols from title
            Set<String> detectedSymbols = extractCryptoSymbols(title);

            // Only create event if crypto symbols were detected
            if (!detectedSymbols.isEmpty()) {
                MarketEvent event = createEventFromPost(post, subreddit, detectedSymbols);
                events.add(event);
            }
        }

        return events;
    }

    /**
     * Extracts crypto symbols mentioned in text.
     *
     * @param text Text to search for symbols
     * @return Set of detected crypto symbols (uppercase)
     */
    private Set<String> extractCryptoSymbols(String text) {
        Set<String> symbols = new HashSet<>();
        Matcher matcher = SYMBOL_PATTERN.matcher(text);

        while (matcher.find()) {
            symbols.add(matcher.group(1).toUpperCase());
        }

        return symbols;
    }

    /**
     * Creates a MarketEvent from a Reddit post.
     *
     * @param post Post data from Reddit API
     * @param subreddit Subreddit name
     * @param symbols Detected crypto symbols
     * @return MarketEvent object
     */
    private MarketEvent createEventFromPost(Map<String, Object> post,
                                           String subreddit,
                                           Set<String> symbols) {
        // Use first detected symbol as primary symbol
        String primarySymbol = symbols.iterator().next();

        // Extract timestamp (Reddit uses seconds since epoch)
        Instant timestamp;
        Object createdUtc = post.get("created_utc");
        if (createdUtc instanceof Number) {
            long epochSeconds = ((Number) createdUtc).longValue();
            timestamp = Instant.ofEpochSecond(epochSeconds);
        } else {
            timestamp = Instant.now();
        }

        // Build content with post metadata
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("title", post.get("title"));
        rawData.put("author", post.get("author"));
        rawData.put("score", post.get("score"));
        rawData.put("num_comments", post.get("num_comments"));
        rawData.put("permalink", post.get("permalink"));
        rawData.put("subreddit", subreddit);
        rawData.put("symbols", new ArrayList<>(symbols)); // All detected symbols
        rawData.put("selftext", post.get("selftext")); // Post body text if available

        return new MarketEvent(
            timestamp,
            EventType.SOCIAL,
            "reddit-" + subreddit,
            primarySymbol,
            rawData
        );
    }

    /**
     * Detect time ranges where cached data is missing or sparse.
     * Gaps are identified when:
     * - No data exists before first cached event
     * - Events are more than 24 hours apart (configurable threshold)
     * - No data exists after last cached event
     *
     * @param cached Cached events sorted by timestamp
     * @param start Start of requested time range
     * @param end End of requested time range
     * @return List of time ranges representing gaps
     */
    List<TimeRange> detectGaps(List<MarketEvent> cached, Instant start, Instant end) {
        if (cached.isEmpty()) {
            return List.of(new TimeRange(start, end));
        }

        List<TimeRange> gaps = new ArrayList<>();
        final long GAP_THRESHOLD_HOURS = 24;

        // Gap before first event
        Instant firstEventTime = cached.get(0).timestamp();
        if (start.isBefore(firstEventTime)) {
            gaps.add(new TimeRange(start, firstEventTime));
        }

        // Gaps between consecutive events
        for (int i = 0; i < cached.size() - 1; i++) {
            Instant current = cached.get(i).timestamp();
            Instant next = cached.get(i + 1).timestamp();
            long hoursBetween = ChronoUnit.HOURS.between(current, next);

            if (hoursBetween > GAP_THRESHOLD_HOURS) {
                gaps.add(new TimeRange(current, next));
            }
        }

        // Gap after last event
        Instant lastEventTime = cached.get(cached.size() - 1).timestamp();
        if (end.isAfter(lastEventTime)) {
            gaps.add(new TimeRange(lastEventTime, end));
        }

        return gaps;
    }

    /**
     * Persist events to database with caching metadata.
     * Uses repository port for persistence.
     */
    protected void persistEvents(List<MarketEvent> events) {
        for (MarketEvent event : events) {
            try {
                repository.save(event);
            } catch (Exception e) {
                LOG.debugf("Event already exists (duplicate): %s", event.timestamp());
            }
        }
    }

    /**
     * Generate SHA-256 hash of content for deduplication.
     */
    private String generateContentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "hash-error-" + content.hashCode();
        }
    }

    /**
     * Filter events to only return those not already in the database.
     * Generates content_hash for each event and checks against existing hashes.
     *
     * @param events Events to filter
     * @param start Start of time range (for efficient DB query)
     * @param end End of time range (for efficient DB query)
     * @return Only new events not in database
     */
    private List<MarketEvent> filterNewEvents(List<MarketEvent> events, Instant start, Instant end) {
        if (events.isEmpty()) {
            return events;
        }

        // Generate content hashes for all fetched events
        // IMPORTANT: Hash only STABLE fields (permalink) to avoid duplicates from changing scores/comments
        Map<String, MarketEvent> eventsByHash = new HashMap<>();
        for (MarketEvent event : events) {
            try {
                // Create stable hash from permalink only (unique identifier that doesn't change)
                String permalink = (String) event.rawData().get("permalink");
                String stableContent = permalink != null ? permalink : objectMapper.writeValueAsString(event.rawData());
                String contentHash = generateContentHash(stableContent);
                eventsByHash.put(contentHash, event);
            } catch (JsonProcessingException e) {
                LOG.warnf(e, "Failed to generate hash for event at %s, including it anyway", event.timestamp());
                eventsByHash.put("error-" + event.timestamp().toEpochMilli(), event);
            }
        }

        // Query database for existing content_hashes in this time range
        Set<String> existingHashes = repository.findContentHashesByTimeRange(start, end);

        // Filter out events whose hash already exists
        List<MarketEvent> newEvents = eventsByHash.entrySet().stream()
            .filter(entry -> !existingHashes.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());

        return newEvents;
    }

    /**
     * Fetches posts from r/worldnews with financial relevance filtering.
     * Only includes posts that:
     * - Have at least minUpvotes
     * - Contain financial keywords in title
     * - Link to external news sources (not self-posts)
     *
     * @param start Start of time range
     * @param end End of time range
     * @return List of MarketEvent objects (EventType.NEWS)
     */
    private List<MarketEvent> fetchWorldNewsPosts(Instant start, Instant end) {
        Client client = ClientBuilder.newClient();
        try {
            List<MarketEvent> allEvents = new ArrayList<>();
            String after = null;
            int maxIterations = 3; // Max ~300 posts total (enough for 24h period)

            for (int i = 0; i < maxIterations; i++) {
                // Fetch top posts from last day (Reddit's default sorting)
                String url = String.format(
                    "%s/r/%s/top.json?t=day&limit=100",
                    REDDIT_BASE_URL,
                    WORLDNEWS_SUBREDDIT
                );
                if (after != null) {
                    url += "&after=" + after;
                }

                Response response = client.target(url)
                    .request()
                    .header("User-Agent", USER_AGENT)
                    .get();

                if (response.getStatus() != 200) {
                    LOG.warnf("Reddit API returned status %d for r/%s", response.getStatus(), WORLDNEWS_SUBREDDIT);
                    break;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> json = response.readEntity(Map.class);
                List<MarketEvent> batch = parseWorldNewsPosts(json);

                // Filter to requested time range
                List<MarketEvent> filtered = batch.stream()
                    .filter(event -> !event.timestamp().isBefore(start) && !event.timestamp().isAfter(end))
                    .collect(Collectors.toList());

                allEvents.addAll(filtered);

                // Get next page token
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) json.get("data");
                after = (String) data.get("after");

                if (after == null) {
                    LOG.debugf("No more pages available for r/%s", WORLDNEWS_SUBREDDIT);
                    break;
                }

                // Small delay to respect rate limits
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            return allEvents;

        } finally {
            client.close();
        }
    }

    /**
     * Parses Reddit JSON response for r/worldnews posts.
     * Applies financial keyword filtering and upvote threshold.
     *
     * @param json JSON response from Reddit API
     * @return List of MarketEvent objects (EventType.NEWS)
     */
    @SuppressWarnings("unchecked")
    private List<MarketEvent> parseWorldNewsPosts(Map<String, Object> json) {
        List<MarketEvent> events = new ArrayList<>();

        Map<String, Object> data = (Map<String, Object>) json.get("data");
        if (data == null) {
            return events;
        }

        List<Map<String, Object>> children = (List<Map<String, Object>>) data.get("children");
        if (children == null) {
            return events;
        }

        for (Map<String, Object> child : children) {
            Map<String, Object> post = (Map<String, Object>) child.get("data");
            if (post == null) continue;

            String title = (String) post.get("title");
            if (title == null) continue;

            // Filter 1: Minimum upvotes threshold
            Object scoreObj = post.get("score");
            int score = scoreObj instanceof Number ? ((Number) scoreObj).intValue() : 0;
            if (score < minUpvotes) {
                continue;
            }

            // Filter 2: Must contain financial keywords
            if (!hasFinancialKeywords(title)) {
                continue;
            }

            // Filter 3: Only external links (not self-posts)
            Boolean isSelf = (Boolean) post.get("is_self");
            if (Boolean.TRUE.equals(isSelf)) {
                continue;
            }

            // Extract symbols from title and domain
            Set<String> detectedSymbols = extractAllSymbols(title);

            // Create NEWS event (always, even without specific symbols - use "MARKET" fallback)
            MarketEvent event = createNewsEventFromPost(post, detectedSymbols);
            events.add(event);
        }

        LOG.debugf("Parsed %d posts from r/%s after filtering", events.size(), WORLDNEWS_SUBREDDIT);
        return events;
    }

    /**
     * Checks if text contains any financial keywords.
     *
     * @param text Text to check
     * @return true if contains financial keywords
     */
    private boolean hasFinancialKeywords(String text) {
        String lowerText = text.toLowerCase();
        return FINANCIAL_KEYWORDS.stream()
            .anyMatch(lowerText::contains);
    }

    /**
     * Extracts all symbols (crypto + stocks) mentioned in text.
     *
     * @param text Text to search for symbols
     * @return Set of detected symbols (normalized to ticker symbols)
     */
    private Set<String> extractAllSymbols(String text) {
        Set<String> symbols = new HashSet<>();
        Matcher matcher = ALL_SYMBOLS_PATTERN.matcher(text);

        while (matcher.find()) {
            String match = matcher.group(1).toUpperCase();
            String normalized = normalizeSymbol(match);
            symbols.add(normalized);
        }

        return symbols;
    }

    /**
     * Normalizes company names and crypto names to ticker symbols.
     *
     * @param name Symbol name or ticker
     * @return Normalized ticker symbol
     */
    private String normalizeSymbol(String name) {
        return switch (name.toUpperCase()) {
            // Crypto normalization
            case "BITCOIN" -> "BTC";
            case "ETHEREUM" -> "ETH";
            case "SOLANA" -> "SOL";
            case "CARDANO" -> "ADA";
            case "DOGECOIN" -> "DOGE";
            case "POLKADOT" -> "DOT";
            case "POLYGON" -> "MATIC";
            case "CHAINLINK" -> "LINK";
            case "UNISWAP" -> "UNI";
            case "AVALANCHE" -> "AVAX";
            case "COSMOS" -> "ATOM";
            case "RIPPLE" -> "XRP";
            case "LITECOIN" -> "LTC";
            case "BITCOIN CASH" -> "BCH";
            case "SHIBA INU" -> "SHIB";
            case "APECOIN" -> "APE";
            // Stock normalization
            case "APPLE" -> "AAPL";
            case "MICROSOFT" -> "MSFT";
            case "GOOGLE", "ALPHABET" -> "GOOGL";
            case "AMAZON" -> "AMZN";
            case "TESLA" -> "TSLA";
            case "NVIDIA" -> "NVDA";
            case "FACEBOOK" -> "META";
            case "NETFLIX" -> "NFLX";
            case "PAYPAL" -> "PYPL";
            case "ADOBE" -> "ADBE";
            case "ORACLE" -> "ORCL";
            case "EXXON" -> "XOM";
            case "CHEVRON" -> "CVX";
            case "GOLDMAN SACHS" -> "GS";
            case "JP MORGAN", "JPMORGAN" -> "JPM";
            case "MORGAN STANLEY" -> "MS";
            case "BANK OF AMERICA" -> "BAC";
            case "WELLS FARGO" -> "WFC";
            case "CITIGROUP" -> "C";
            case "S&P 500", "S&P" -> "SPX";
            case "DOW JONES", "DOW" -> "DJI";
            case "OIL", "CRUDE" -> "CL";  // Crude oil futures symbol
            default -> name;
        };
    }

    /**
     * Creates a MarketEvent (EventType.NEWS) from a r/worldnews post.
     *
     * @param post Post data from Reddit API
     * @param symbols Detected symbols (may be empty)
     * @return MarketEvent object with EventType.NEWS
     */
    private MarketEvent createNewsEventFromPost(Map<String, Object> post, Set<String> symbols) {
        // Extract timestamp (Reddit uses seconds since epoch)
        Instant timestamp;
        Object createdUtc = post.get("created_utc");
        if (createdUtc instanceof Number) {
            long epochSeconds = ((Number) createdUtc).longValue();
            timestamp = Instant.ofEpochSecond(epochSeconds);
        } else {
            timestamp = Instant.now();
        }

        // Build rawData map with post metadata (similar to NewsAPI structure)
        Map<String, Object> rawData = new HashMap<>();
        rawData.put("title", post.get("title"));
        rawData.put("url", post.get("url"));  // External news article URL
        rawData.put("domain", post.get("domain"));  // e.g., "reuters.com", "bloomberg.com"
        rawData.put("score", post.get("score"));  // Upvotes
        rawData.put("num_comments", post.get("num_comments"));
        rawData.put("author", post.get("author"));
        rawData.put("permalink", post.get("permalink"));  // Reddit discussion link
        rawData.put("source", "r/worldnews");  // Mark source as Reddit
        rawData.put("symbols", new ArrayList<>(symbols));  // All detected symbols

        // Use first symbol or "MARKET" for general news (geopolitical, economic policy, etc.)
        String primarySymbol = symbols.isEmpty() ? "MARKET" : symbols.iterator().next();

        return new MarketEvent(
            timestamp,
            EventType.NEWS,  // Marked as NEWS, not REDDIT_POST
            "reddit-worldnews",  // Source identifier
            primarySymbol,
            rawData
        );
    }

    /**
     * Wrapper for fetch results with quality metadata.
     * Used to track cache hit ratio and data coverage.
     */
    public static class FetchResult {
        private final List<MarketEvent> events;
        private final DataQualityMetadata quality;

        public FetchResult(List<MarketEvent> events, DataQualityMetadata quality) {
            this.events = events;
            this.quality = quality;
        }

        public List<MarketEvent> events() {
            return events;
        }

        public DataQualityMetadata quality() {
            return quality;
        }
    }

    /**
     * Calculate covered seconds based on event count.
     * Simple approximation: assumes each event covers 1 hour.
     */
    private long calculateCoveredSeconds(List<MarketEvent> events, Instant start, Instant end) {
        if (events.isEmpty()) {
            return 0;
        }
        // Simple approximation: assume each event covers 1 hour
        // More sophisticated: analyze density and gaps
        return events.size() * 3600L;
    }

    /**
     * Fetch symbol sentiment with quality metadata tracking.
     * Calculates cache hit ratio and coverage percentage.
     *
     * @param symbol Symbol to fetch
     * @param start Start of time range
     * @param end End of time range
     * @return FetchResult with events and quality metadata
     */
    public FetchResult fetchWithQualityTracking(Symbol symbol, Instant start, Instant end) {
        List<MarketEvent> cachedEvents = repository.findBySourcePatternAndSymbolAndTimeRange(
            "reddit-%", symbol.ticker(), start, end
        ).stream()
            .sorted((a, b) -> a.timestamp().compareTo(b.timestamp()))
            .collect(Collectors.toList());

        List<TimeRange> gaps = detectGaps(cachedEvents, start, end);

        List<MarketEvent> freshEvents = new ArrayList<>();
        List<TimeRange> remainingGaps = new ArrayList<>();

        for (TimeRange gap : gaps) {
            try {
                for (String subreddit : SUBREDDITS) {
                    List<MarketEvent> gapEvents = fetchSubredditPosts(subreddit, symbol, gap.start(), gap.end());
                    if (gapEvents.isEmpty()) {
                        remainingGaps.add(gap);
                    }
                    freshEvents.addAll(gapEvents);
                }
            } catch (Exception e) {
                LOG.warnf(e, "Failed to fetch gap %s", gap);
                remainingGaps.add(gap);
            }
        }

        if (!freshEvents.isEmpty()) {
            persistEvents(freshEvents);
        }

        List<MarketEvent> allEvents = new ArrayList<>(cachedEvents);
        allEvents.addAll(freshEvents);
        allEvents.sort((a, b) -> a.timestamp().compareTo(b.timestamp()));

        // Calculate quality metrics
        long totalRangeSeconds = Duration.between(start, end).getSeconds();
        long cachedSeconds = calculateCoveredSeconds(cachedEvents, start, end);
        long freshSeconds = calculateCoveredSeconds(freshEvents, start, end);
        long coveredSeconds = cachedSeconds + freshSeconds;

        double cacheHitRatio = totalRangeSeconds > 0
            ? Math.min(1.0, (double) cachedSeconds / totalRangeSeconds)
            : 0.0;
        double coveragePercentage = totalRangeSeconds > 0
            ? Math.min(1.0, (double) coveredSeconds / totalRangeSeconds)
            : 0.0;

        DataQualityMetadata quality = new DataQualityMetadata(
            cacheHitRatio,
            coveragePercentage,
            remainingGaps,
            coveragePercentage < 0.90
        );

        LOG.infof("Quality tracking for %s: %s", symbol.ticker(), quality.summary());

        return new FetchResult(allEvents, quality);
    }

    @Override
    public Set<MarketDataFeature> getSupportedFeatures() {
        return Set.of(
            MarketDataFeature.SOCIAL_MEDIA,  // Social media posts (Reddit)
            MarketDataFeature.NEWS,          // Sentiment/news-like content
            MarketDataFeature.RESEARCH       // Free API suitable for research/backtesting
            // NOTE: BACKFILL intentionally omitted — Reddit search only returns recent posts,
            // not reliable historical data. Alpaca handles historical news backfill.
        );
    }
}
