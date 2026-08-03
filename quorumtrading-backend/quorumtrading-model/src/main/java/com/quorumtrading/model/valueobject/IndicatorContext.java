package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Container for all ticker indicators passed to agents.
 *
 * <p><b>DDD Classification:</b> Value Object</p>
 * <p>Provides formatted output for LLM consumption via toPromptText().</p>
 *
 * <p><b>Note:</b> No JPA/JSON annotations - this is a pure domain object.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * Map&lt;Symbol, IndicatorSnapshot&gt; snapshots = new HashMap&lt;&gt;();
 * snapshots.put(aaplSymbol, aaplSnapshot);
 * snapshots.put(nvdaSymbol, nvdaSnapshot);
 * IndicatorContext context = new IndicatorContext(snapshots);
 *
 * // Format for LLM prompt
 * String promptSection = context.toPromptText();
 * </pre>
 *
 * <p><b>Output Format:</b></p>
 * <pre>
 * ## Technical Indicators (Stocks Only)
 *
 * ### AAPL
 * - SMA-50: $185.42
 * - SMA-200: $178.91
 * - RSI-14: 62.3 (neutral)
 * - MACD: Line=2.15, Signal=1.89, Histogram=+0.26 (bullish)
 * - Bollinger Bands: Upper=$192.10, Middle=$185.00, Lower=$177.90
 *
 * *Note: Technical indicators not calculated for crypto assets.*
 * </pre>
 *
 * @param tickerIndicators Map of stock symbols to their calculated indicator snapshots
 *
 * @see IndicatorSnapshot
 * @see IndicatorValue
 * @see IndicatorType
 */
public record IndicatorContext(
    Map<Symbol, IndicatorSnapshot> tickerIndicators
) {
    /**
     * RSI threshold for overbought condition (>=70).
     */
    private static final BigDecimal RSI_OVERBOUGHT = new BigDecimal("70");

    /**
     * RSI threshold for oversold condition (<=30).
     */
    private static final BigDecimal RSI_OVERSOLD = new BigDecimal("30");

    /**
     * Canonical constructor with null validation.
     */
    public IndicatorContext {
        Objects.requireNonNull(tickerIndicators, "tickerIndicators is required");
    }

    /**
     * Creates an empty context with no indicators.
     *
     * @return A new IndicatorContext with empty ticker indicators map
     */
    public static IndicatorContext empty() {
        return new IndicatorContext(Map.of());
    }

    /**
     * Format indicators as readable text for LLM agents.
     *
     * <p>The output is structured as markdown with:</p>
     * <ul>
     *   <li>Header identifying these as stock-only indicators</li>
     *   <li>Each ticker as a level-3 heading</li>
     *   <li>Indicators as bulleted list items</li>
     *   <li>Interpretations for RSI (overbought/oversold/neutral) and MACD (bullish/bearish)</li>
     *   <li>Footer noting crypto assets are excluded</li>
     * </ul>
     *
     * @return Formatted text suitable for inclusion in LLM prompts
     */
    public String toPromptText() {
        if (tickerIndicators.isEmpty()) {
            return "No technical indicators available.";
        }

        // The header and footer must describe what the block ACTUALLY contains. Hardcoding
        // "(Stocks Only)" above a populated crypto table, and "not calculated for crypto"
        // below it, told the model to disbelieve data it was being handed — which is how an
        // Orchestrator came to use EMA-50 as a price proxy for BTC.
        boolean hasCrypto = tickerIndicators.keySet().stream()
            .anyMatch(symbol -> symbol.type() == com.quorumtrading.model.enums.InvestmentType.CRYPTO);
        boolean hasStocks = tickerIndicators.keySet().stream()
            .anyMatch(symbol -> symbol.type() != com.quorumtrading.model.enums.InvestmentType.CRYPTO);

        StringBuilder sb = new StringBuilder();
        sb.append("## Technical Indicators");
        if (hasStocks && !hasCrypto) {
            sb.append(" (Stocks Only)");
        } else if (hasCrypto && !hasStocks) {
            sb.append(" (Crypto Only)");
        }
        sb.append("\n\n");

        tickerIndicators.entrySet().stream()
            .sorted((a, b) -> a.getKey().ticker().compareTo(b.getKey().ticker()))
            .forEach(entry -> formatTicker(sb, entry.getKey(), entry.getValue()));

        if (!hasCrypto) {
            sb.append("\n*Note: Technical indicators not calculated for crypto assets.*");
        }
        return sb.toString();
    }

    /**
     * Format a single ticker's indicators.
     */
    private void formatTicker(StringBuilder sb, Symbol ticker, IndicatorSnapshot snapshot) {
        sb.append("### ").append(ticker.ticker()).append("\n");

        // Moving Averages
        formatMovingAverage(sb, snapshot);

        // RSI
        formatRSI(sb, snapshot);

        // MACD
        formatMACD(sb, snapshot);

        // Bollinger Bands
        formatBollinger(sb, snapshot);

        sb.append("\n");
    }

    /**
     * Format Moving Average indicator values (SMA and EMA).
     */
    private void formatMovingAverage(StringBuilder sb, IndicatorSnapshot snapshot) {
        IndicatorValue value = snapshot.values().get(IndicatorType.MOVING_AVERAGE);
        String warning = snapshot.warnings().get(IndicatorType.MOVING_AVERAGE);

        if (value instanceof IndicatorValue.MovingAverageValue ma) {
            // SMA section
            sb.append("- SMA-50: ").append(formatPrice(ma.sma50())).append("\n");
            if (ma.sma200() != null) {
                sb.append("- SMA-200: ").append(formatPrice(ma.sma200())).append("\n");
                if (ma.sma50() != null) {
                    appendGapLine(sb, "SMA", ma.sma50(), ma.sma200());
                }
            } else {
                sb.append("- SMA-200: N/A");
                if (warning != null) {
                    sb.append(" (").append(warning).append(")");
                }
                sb.append("\n");
            }

            // EMA section (only shown when at least one EMA is available)
            if (ma.ema50() != null || ma.ema200() != null) {
                if (ma.ema50() != null) {
                    sb.append("- EMA-50: ").append(formatPrice(ma.ema50())).append("\n");
                }
                if (ma.ema200() != null) {
                    sb.append("- EMA-200: ").append(formatPrice(ma.ema200())).append("\n");
                }
                if (ma.ema50() != null && ma.ema200() != null) {
                    appendGapLine(sb, "EMA", ma.ema50(), ma.ema200());
                }
            }
        } else if (warning != null) {
            sb.append("- Moving Averages: N/A (").append(warning).append(")\n");
        }
    }

    /**
     * Append a gap percentage line comparing short vs long moving average.
     */
    private void appendGapLine(StringBuilder sb, String label, BigDecimal short50, BigDecimal long200) {
        BigDecimal gap = short50.subtract(long200)
            .multiply(new BigDecimal("100"))
            .divide(long200, 2, RoundingMode.HALF_UP);
        String sign = gap.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        String direction = gap.compareTo(BigDecimal.ZERO) >= 0 ? "ABOVE" : "BELOW";
        BigDecimal absGap = gap.abs();
        sb.append("- ").append(label).append("-50 vs ").append(label).append("-200: ")
            .append(sign).append(gap).append("%")
            .append(" (").append(label).append("-50 is ").append(absGap).append("% ").append(direction)
            .append(" ").append(label).append("-200)").append("\n");
    }

    /**
     * Format RSI indicator value with interpretation.
     *
     * <p>Interpretations:</p>
     * <ul>
     *   <li>RSI >= 70: (overbought)</li>
     *   <li>RSI <= 30: (oversold)</li>
     *   <li>Otherwise: (neutral)</li>
     * </ul>
     */
    private void formatRSI(StringBuilder sb, IndicatorSnapshot snapshot) {
        IndicatorValue value = snapshot.values().get(IndicatorType.RSI);
        String warning = snapshot.warnings().get(IndicatorType.RSI);

        if (value instanceof IndicatorValue.RSIValue rsi) {
            sb.append("- RSI-14: ").append(rsi.rsi14().setScale(1, RoundingMode.HALF_UP));
            sb.append(getRSIInterpretation(rsi.rsi14()));
            sb.append("\n");
        } else if (warning != null) {
            sb.append("- RSI-14: N/A (").append(warning).append(")\n");
        }
    }

    /**
     * Format MACD indicator values with interpretation.
     *
     * <p>Interpretations:</p>
     * <ul>
     *   <li>Histogram > 0: (bullish)</li>
     *   <li>Histogram < 0: (bearish)</li>
     *   <li>Histogram = 0: (neutral)</li>
     * </ul>
     */
    private void formatMACD(StringBuilder sb, IndicatorSnapshot snapshot) {
        IndicatorValue value = snapshot.values().get(IndicatorType.MACD);
        String warning = snapshot.warnings().get(IndicatorType.MACD);

        if (value instanceof IndicatorValue.MACDValue macd) {
            // Size all three components off the smallest, so the histogram — usually the
            // smallest and the one rules are written against — is not rounded to nothing.
            int macdScale = promptScaleFor(smallestMagnitude(
                macd.macdLine(), macd.signalLine(), macd.histogram()));
            sb.append("- MACD: Line=").append(macd.macdLine().setScale(macdScale, RoundingMode.HALF_UP).toPlainString());
            sb.append(", Signal=").append(macd.signalLine().setScale(macdScale, RoundingMode.HALF_UP).toPlainString());
            String hist = macd.histogram().setScale(macdScale, RoundingMode.HALF_UP).toPlainString();
            sb.append(", Histogram=").append(macd.histogram().signum() >= 0 ? "+" + hist : hist);
            sb.append(getMACDInterpretation(macd.histogram()));
            sb.append("\n");
        } else if (warning != null) {
            sb.append("- MACD: N/A (").append(warning).append(")\n");
        }
    }

    /**
     * Format Bollinger Bands indicator values.
     */
    private void formatBollinger(StringBuilder sb, IndicatorSnapshot snapshot) {
        IndicatorValue value = snapshot.values().get(IndicatorType.BOLLINGER_BANDS);
        String warning = snapshot.warnings().get(IndicatorType.BOLLINGER_BANDS);

        if (value instanceof IndicatorValue.BollingerValue bb) {
            sb.append("- Bollinger Bands: Upper=").append(formatPrice(bb.upper()));
            sb.append(", Middle=").append(formatPrice(bb.middle()));
            sb.append(", Lower=").append(formatPrice(bb.lower()));
            sb.append("\n");
        } else if (warning != null) {
            sb.append("- Bollinger Bands: N/A (").append(warning).append(")\n");
        }
    }

    /**
     * Format a price for the prompt, keeping enough decimals to stay meaningful.
     *
     * @param price The price value to format
     * @return Formatted price string (e.g., "$185.42") or "N/A" if null
     */
    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "N/A";
        }
        return "$" + price.setScale(promptScaleFor(price), RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Format a decimal value with sign prefix.
     *
     * @param value The decimal value to format
     * @return Formatted string with +/- prefix (e.g., "+0.26" or "-0.50") or "N/A" if null
     */
    private String formatSignedDecimal(BigDecimal value) {
        if (value == null) {
            return "N/A";
        }
        String formatted = value.setScale(promptScaleFor(value), RoundingMode.HALF_UP).toPlainString();
        return value.compareTo(BigDecimal.ZERO) >= 0 ? "+" + formatted : formatted;
    }

    /**
     * Decimal places to show an agent, chosen for the value's magnitude.
     *
     * <p>This is the last place a correct number can still be destroyed. Every value
     * here was printed at two decimals, which is right for a $185 share and useless
     * below a dollar: a coin's Bollinger bands of 0.0775 / 0.0727 / 0.0680 reached
     * the agent as "$0.08 / $0.07 / $0.07" with the middle and lower band collapsed
     * onto the same number, and a MACD histogram of 0.0002 arrived as "+0.00". The
     * agents said so in their own reasoning — "the histogram is reported as
     * approximately +0.00" — and held every round.</p>
     *
     * <p>Fixing the calculation was not enough on its own; a rule written against a
     * band or a histogram is unreachable if the model never sees the difference.
     * Values at or above 1.0 keep two decimals, so equity prompts are unchanged.</p>
     */
    /** Smallest non-zero magnitude among the values, or ONE when all are zero. */
    private static BigDecimal smallestMagnitude(BigDecimal... values) {
        BigDecimal smallest = null;
        for (BigDecimal v : values) {
            if (v == null || v.signum() == 0) {
                continue;
            }
            BigDecimal abs = v.abs();
            if (smallest == null || abs.compareTo(smallest) < 0) {
                smallest = abs;
            }
        }
        return smallest != null ? smallest : BigDecimal.ONE;
    }

    public static int promptScaleFor(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return 2;
        }
        BigDecimal abs = value.abs();
        // floor(log10(abs)), computed exactly rather than through a double.
        int magnitude = abs.precision() - abs.scale() - 1;
        if (magnitude >= 0) {
            return 2;
        }
        // Keep roughly four significant figures. A fixed floor does not work here: an
        // earlier ladder stopped at eight decimals, which is fine at a tenth of a cent
        // and useless below it — every value a meme coin's agent saw became a multiple
        // of 1e-8, and its MACD histogram could only be 0, +/-1e-8 or +/-2e-8. A rule
        // reading "histogram rising" was reading quantisation noise.
        return Math.min(-magnitude + 3, 18);
    }

    /**
     * Formats a price for an agent prompt at a scale its magnitude can carry.
     *
     * <p>Shared so every prompt-building path formats the same way. {@code %.2f}
     * renders a sub-cent asset as {@code $0.00}, and an agent handed that says the
     * price is missing and substitutes something — in one audit, a number the user
     * had typed into their strategy prose.</p>
     */
    public static String formatPromptPrice(BigDecimal price) {
        if (price == null) {
            return "N/A";
        }
        return price.setScale(promptScaleFor(price), RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Get RSI interpretation string based on value.
     *
     * @param rsi The RSI value (0-100)
     * @return Interpretation: "(overbought)", "(oversold)", or "(neutral)"
     */
    private String getRSIInterpretation(BigDecimal rsi) {
        if (rsi.compareTo(RSI_OVERBOUGHT) >= 0) {
            return " (overbought)";
        }
        if (rsi.compareTo(RSI_OVERSOLD) <= 0) {
            return " (oversold)";
        }
        return " (neutral)";
    }

    /**
     * Get MACD interpretation string based on histogram value.
     *
     * @param histogram The MACD histogram value
     * @return Interpretation: "(bullish)", "(bearish)", or "(neutral)"
     */
    private String getMACDInterpretation(BigDecimal histogram) {
        if (histogram == null) {
            return "";
        }
        if (histogram.compareTo(BigDecimal.ZERO) > 0) {
            return " (bullish)";
        }
        if (histogram.compareTo(BigDecimal.ZERO) < 0) {
            return " (bearish)";
        }
        return " (neutral)";
    }

    /**
     * Converts the indicator context to a JSON-serializable map structure.
     * Used for persisting indicator snapshots with LLM decisions for audit trail.
     *
     * <p><b>Output Structure:</b></p>
     * <pre>
     * {
     *   "AAPL": {
     *     "calculatedAt": "2026-01-15T14:00:00Z",
     *     "indicators": {
     *       "RSI_14": { "value": 62.3, "interpretation": "neutral" },
     *       "SMA_50": { "value": 185.42 },
     *       "SMA_200": { "value": 178.91 },
     *       "MACD": { "line": 2.15, "signal": 1.89, "histogram": 0.26, "interpretation": "bullish" },
     *       "BOLLINGER": { "upper": 192.10, "middle": 185.00, "lower": 177.90 }
     *     },
     *     "warnings": []
     *   }
     * }
     * </pre>
     *
     * @return Map structure suitable for JSON serialization
     */
    public Map<String, Object> toStorageMap() {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<Symbol, IndicatorSnapshot> entry : tickerIndicators.entrySet()) {
            String ticker = entry.getKey().ticker();
            IndicatorSnapshot snapshot = entry.getValue();

            Map<String, Object> tickerData = new LinkedHashMap<>();
            tickerData.put("calculatedAt", DateTimeFormatter.ISO_INSTANT.format(snapshot.calculatedAt()));

            Map<String, Object> indicators = new LinkedHashMap<>();

            // RSI
            IndicatorValue rsiValue = snapshot.values().get(IndicatorType.RSI);
            if (rsiValue instanceof IndicatorValue.RSIValue rsi) {
                Map<String, Object> rsiMap = new LinkedHashMap<>();
                rsiMap.put("value", rsi.rsi14());
                rsiMap.put("interpretation", getRSIInterpretationSimple(rsi.rsi14()));
                indicators.put("RSI_14", rsiMap);
            }

            // Moving Averages (SMA and EMA)
            IndicatorValue maValue = snapshot.values().get(IndicatorType.MOVING_AVERAGE);
            if (maValue instanceof IndicatorValue.MovingAverageValue ma) {
                if (ma.sma50() != null) {
                    Map<String, Object> sma50Map = new LinkedHashMap<>();
                    sma50Map.put("value", ma.sma50());
                    indicators.put("SMA_50", sma50Map);
                }
                if (ma.sma200() != null) {
                    Map<String, Object> sma200Map = new LinkedHashMap<>();
                    sma200Map.put("value", ma.sma200());
                    indicators.put("SMA_200", sma200Map);
                }
                if (ma.ema50() != null) {
                    Map<String, Object> ema50Map = new LinkedHashMap<>();
                    ema50Map.put("value", ma.ema50());
                    indicators.put("EMA_50", ema50Map);
                }
                if (ma.ema200() != null) {
                    Map<String, Object> ema200Map = new LinkedHashMap<>();
                    ema200Map.put("value", ma.ema200());
                    indicators.put("EMA_200", ema200Map);
                }
            }

            // MACD
            IndicatorValue macdValue = snapshot.values().get(IndicatorType.MACD);
            if (macdValue instanceof IndicatorValue.MACDValue macd) {
                Map<String, Object> macdMap = new LinkedHashMap<>();
                macdMap.put("line", macd.macdLine());
                macdMap.put("signal", macd.signalLine());
                macdMap.put("histogram", macd.histogram());
                macdMap.put("interpretation", getMACDInterpretationSimple(macd.histogram()));
                indicators.put("MACD", macdMap);
            }

            // Bollinger Bands
            IndicatorValue bbValue = snapshot.values().get(IndicatorType.BOLLINGER_BANDS);
            if (bbValue instanceof IndicatorValue.BollingerValue bb) {
                Map<String, Object> bbMap = new LinkedHashMap<>();
                bbMap.put("upper", bb.upper());
                bbMap.put("middle", bb.middle());
                bbMap.put("lower", bb.lower());
                indicators.put("BOLLINGER", bbMap);
            }

            tickerData.put("indicators", indicators);
            tickerData.put("warnings", snapshot.warnings().values().stream().toList());

            result.put(ticker, tickerData);
        }

        return result;
    }

    /**
     * Get simple RSI interpretation without parentheses.
     */
    private String getRSIInterpretationSimple(BigDecimal rsi) {
        if (rsi.compareTo(RSI_OVERBOUGHT) >= 0) {
            return "overbought";
        }
        if (rsi.compareTo(RSI_OVERSOLD) <= 0) {
            return "oversold";
        }
        return "neutral";
    }

    /**
     * Get simple MACD interpretation without parentheses.
     */
    private String getMACDInterpretationSimple(BigDecimal histogram) {
        if (histogram == null) {
            return "unknown";
        }
        if (histogram.compareTo(BigDecimal.ZERO) > 0) {
            return "bullish";
        }
        if (histogram.compareTo(BigDecimal.ZERO) < 0) {
            return "bearish";
        }
        return "neutral";
    }
}
