/**
 * Target-asset tokens use the backend's shared "TICKER[:TYPE]" convention:
 * a bare ticker is a STOCK, an explicit suffix selects the type ("BTC:CRYPTO").
 * The API round-trips the full token (so editing preserves the asset type),
 * but chips/labels should show just the ticker.
 */

/** Strip the ":TYPE" suffix for display: "BTC:CRYPTO" → "BTC", "AAPL" → "AAPL". */
export function assetTicker(asset: string): string {
  const colon = asset.indexOf(':');
  return colon >= 0 ? asset.slice(0, colon) : asset;
}
