package com.quorumtrading.adapter.broker.saxo;

import com.quorumtrading.model.valueobject.MIC;

import java.util.Map;
import java.util.Optional;

/**
 * Maps our canonical ISO-10383 {@link MIC} (the exchange the {@code Symbol} carries) to the
 * Saxo {@code ExchangeId} string the Reference Data search pins the venue with
 * ({@code GET /ref/v1/instruments?…&ExchangeId=…}).
 *
 * <p><b>SB3-uic-resolver.</b> Saxo addresses a venue by an internal {@code ExchangeId} code,
 * which is <em>usually</em> the ISO MIC but not contractually guaranteed to be (SAXO_ANSWERS
 * §2.5 ⚠️). For the European venues we trade today the MIC and the Saxo {@code ExchangeId}
 * coincide (Xetra {@code XETR}, Euronext Brussels {@code XBRU}, Amsterdam {@code XAMS}, Paris
 * {@code XPAR}, …), so the table below maps each known MIC to its Saxo {@code ExchangeId}
 * one-to-one. The mapping is intentionally explicit rather than "pass the MIC through" so an
 * exotic venue whose Saxo code diverges from its MIC can be corrected here in one place.</p>
 *
 * <p><b>⚠️ TODO(SB3, unconfirmed — exotic venues):</b> for any MIC not in this table the
 * resolver falls back to passing the MIC code verbatim as the {@code ExchangeId} (best-effort)
 * and logs that the mapping is unverified. The authoritative mapping is the Saxo
 * {@code GET /ref/v1/exchanges} reference list (plan §2.5); wire a one-time cached lookup of
 * that endpoint here if any venue's {@code ExchangeId} is found to diverge from its MIC.</p>
 *
 * <p>Pure mapping helper — no I/O, no CDI; lives in the adapter module because the MIC↔venue
 * relationship is Saxo-specific.</p>
 */
final class SaxoExchangeIdMapper {

    /**
     * MIC → Saxo {@code ExchangeId}. Kept in lock-step with the European venues
     * {@code SymbolParser.MIC_BY_SUFFIX} can produce. For these venues the Saxo
     * {@code ExchangeId} equals the ISO MIC; the few that may diverge are TODO-flagged.
     */
    private static final Map<String, String> EXCHANGE_ID_BY_MIC = Map.ofEntries(
        Map.entry("XETR", "XETR"),   // Germany — Xetra (Deutsche Börse)
        Map.entry("XPAR", "XPAR"),   // France — Euronext Paris
        Map.entry("XAMS", "XAMS"),   // Netherlands — Euronext Amsterdam
        Map.entry("XBRU", "XBRU"),   // Belgium — Euronext Brussels
        Map.entry("XMIL", "MIL"),    // ⚠️ Italy — Euronext Milan: Saxo uses "MIL", not the ISO MIC "XMIL"
        Map.entry("XMAD", "XMAD"),   // Spain — Bolsa de Madrid (BME)
        Map.entry("XLIS", "XLIS"),   // Portugal — Euronext Lisbon
        Map.entry("XLON", "LSE_SETS"),// ⚠️ UK — LSE: Saxo segments LSE; "LSE_SETS" is the main order book (verify)
        Map.entry("XSWX", "SWX"),    // ⚠️ Switzerland — SIX: Saxo uses "SWX" (verify)
        Map.entry("XWBO", "WBAG"),   // ⚠️ Austria — Wiener Börse: Saxo uses "WBAG" (verify)
        Map.entry("XHEL", "HEL"),    // ⚠️ Finland — Nasdaq Helsinki: Saxo uses "HEL" (verify)
        Map.entry("XSTO", "SSE"),    // ⚠️ Sweden — Nasdaq Stockholm: Saxo uses "SSE" (verify)
        Map.entry("XCSE", "CSE"),    // ⚠️ Denmark — Nasdaq Copenhagen: Saxo uses "CSE" (verify)
        Map.entry("XOSL", "OSE"),    // ⚠️ Norway — Oslo Børs: Saxo uses "OSE" (verify)
        Map.entry("XDUB", "DUB"),    // ⚠️ Ireland — Euronext Dublin: Saxo uses "DUB"/"ISE" (verify)
        // US venues — a US symbol should not reach the Saxo resolver, but map the principal
        // venues so a stray US ExchangeId pin is at least intelligible.
        Map.entry("XNAS", "NASDAQ"),
        Map.entry("XNYS", "NYSE"),
        Map.entry("ARCX", "ARCX")
    );

    private SaxoExchangeIdMapper() {
        // static helper — not instantiable
    }

    /**
     * Returns the Saxo {@code ExchangeId} for a MIC, if a verified mapping exists.
     *
     * @param mic the exchange MIC, or {@code null}
     * @return the mapped Saxo {@code ExchangeId}, or empty if the MIC is null or unmapped
     */
    static Optional<String> exchangeIdFor(MIC mic) {
        if (mic == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(EXCHANGE_ID_BY_MIC.get(mic.code()));
    }

    /**
     * Resolves the Saxo {@code ExchangeId} to pin a venue search with, falling back to the bare
     * MIC code (best-effort) for venues not in the verified table. Returns {@code null} for a
     * null MIC so the {@code ExchangeId} query param is omitted (US / unqualified search).
     *
     * <p>The caller logs the fallback so an exotic venue whose Saxo {@code ExchangeId} diverges
     * from its MIC surfaces in production rather than silently mis-searching.</p>
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
