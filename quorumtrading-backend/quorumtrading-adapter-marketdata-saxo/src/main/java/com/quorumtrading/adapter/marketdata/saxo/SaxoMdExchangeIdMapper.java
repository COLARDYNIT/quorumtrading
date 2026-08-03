package com.quorumtrading.adapter.marketdata.saxo;

import com.quorumtrading.model.valueobject.MIC;

import java.util.Map;

/**
 * Maps our canonical ISO-10383 {@link MIC} (the exchange a {@code Symbol} carries) to the Saxo
 * {@code ExchangeId} string the Reference Data search pins the venue with
 * ({@code GET /ref/v1/instruments?…&ExchangeId=…}).
 *
 * <p><b>MD2-live-quotes — module-local copy.</b> Adapters must not depend on each other
 * (ArchUnit-enforced), so the market-data module owns its own MIC↔ExchangeId table rather than
 * importing the broker-saxo {@code SaxoExchangeIdMapper}. The table is kept in lock-step with the
 * broker module's; the few venues whose Saxo {@code ExchangeId} diverges from the ISO MIC are
 * TODO-flagged identically.</p>
 *
 * <p><b>⚠️ TODO(MD2, unconfirmed — exotic venues):</b> for any MIC not in this table the resolver
 * falls back to passing the MIC code verbatim as the {@code ExchangeId} (best-effort) and logs the
 * fallback. The authoritative mapping is Saxo's {@code GET /ref/v1/exchanges} list (SAXO_ANSWERS
 * §2.5); wire a cached lookup if any venue's {@code ExchangeId} is found to diverge.</p>
 *
 * <p>Pure mapping helper — no I/O, no CDI.</p>
 */
final class SaxoMdExchangeIdMapper {

    /**
     * MIC → Saxo {@code ExchangeId}. Kept in lock-step with the European venues
     * {@code SymbolParser.MIC_BY_SUFFIX} can produce (and the broker module's mapper). For these
     * venues the Saxo {@code ExchangeId} equals the ISO MIC unless TODO-flagged.
     */
    private static final Map<String, String> EXCHANGE_ID_BY_MIC = Map.ofEntries(
        Map.entry("XETR", "XETR"),    // Germany — Xetra (Deutsche Börse)
        Map.entry("XPAR", "XPAR"),    // France — Euronext Paris
        Map.entry("XAMS", "XAMS"),    // Netherlands — Euronext Amsterdam
        Map.entry("XBRU", "XBRU"),    // Belgium — Euronext Brussels
        Map.entry("XMIL", "MIL"),     // ⚠️ Italy — Euronext Milan: Saxo uses "MIL", not ISO "XMIL"
        Map.entry("XMAD", "XMAD"),    // Spain — Bolsa de Madrid (BME)
        Map.entry("XLIS", "XLIS"),    // Portugal — Euronext Lisbon
        Map.entry("XLON", "LSE_SETS"),// ⚠️ UK — LSE main order book (verify)
        Map.entry("XSWX", "SWX"),     // ⚠️ Switzerland — SIX (verify)
        Map.entry("XWBO", "WBAG"),    // ⚠️ Austria — Wiener Börse (verify)
        Map.entry("XHEL", "HEL"),     // ⚠️ Finland — Nasdaq Helsinki (verify)
        Map.entry("XSTO", "SSE"),     // ⚠️ Sweden — Nasdaq Stockholm (verify)
        Map.entry("XCSE", "CSE"),     // ⚠️ Denmark — Nasdaq Copenhagen (verify)
        Map.entry("XOSL", "OSE"),     // ⚠️ Norway — Oslo Børs (verify)
        Map.entry("XDUB", "DUB")      // ⚠️ Ireland — Euronext Dublin (verify)
    );

    private SaxoMdExchangeIdMapper() {
        // static helper — not instantiable
    }

    /**
     * Resolves the Saxo {@code ExchangeId} to pin a venue search with, falling back to the bare
     * MIC code (best-effort) for venues not in the verified table. Returns {@code null} for a
     * null MIC so the {@code ExchangeId} query param is omitted.
     *
     * @param mic the exchange MIC, or {@code null}
     * @return the Saxo {@code ExchangeId} (mapped or MIC-verbatim), or {@code null} for a null MIC
     */
    static String exchangeIdOrMic(MIC mic) {
        if (mic == null) {
            return null;
        }
        return EXCHANGE_ID_BY_MIC.getOrDefault(mic.code(), mic.code());
    }

    /**
     * @param mic the exchange MIC
     * @return {@code true} if this MIC has a verified Saxo {@code ExchangeId} mapping (so the
     *     caller can decide whether to warn about an unverified fallback)
     */
    static boolean isMapped(MIC mic) {
        return mic != null && EXCHANGE_ID_BY_MIC.containsKey(mic.code());
    }
}
