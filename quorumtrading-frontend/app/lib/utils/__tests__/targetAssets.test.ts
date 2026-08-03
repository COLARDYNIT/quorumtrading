import { assetTicker } from '../targetAssets';

describe('assetTicker', () => {
  it('returns bare tickers unchanged', () => {
    expect(assetTicker('AAPL')).toBe('AAPL');
  });

  it('strips an explicit CRYPTO type suffix', () => {
    expect(assetTicker('BTC:CRYPTO')).toBe('BTC');
  });

  it('strips an explicit STOCK type suffix', () => {
    expect(assetTicker('AAPL:STOCK')).toBe('AAPL');
  });

  it('only strips from the first colon', () => {
    expect(assetTicker('X:Y:Z')).toBe('X');
  });
});
