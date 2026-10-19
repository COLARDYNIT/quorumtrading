/**
 * Copy-level tests for the keyless-install UX (issue #7): the historic-event
 * consequence line and the provider-status banner say the fix (a free data key)
 * up front, before any click can fail.
 */
import { eventConsequence } from '../EventRows';
import { bannerCopy } from '../ProviderStatusBanner';
import type { MarketDataStatusResponse } from '@/app/lib/types/dataAvailability';

const KEYLESS: MarketDataStatusResponse = {
  cryptoUsable: true,
  stockUsable: false,
  providers: [
    {
      name: 'alpaca',
      enabled: true,
      credentialed: false,
      usable: false,
      assetClasses: ['CRYPTO', 'STOCK'],
      detail: 'Switched on but holding no credentials.',
    },
    {
      name: 'kraken',
      enabled: true,
      credentialed: true,
      usable: true,
      assetClasses: ['CRYPTO'],
      detail: 'Enabled and credentialed.',
    },
  ],
  hint: 'Crypto candles are served (keyless). No usable stock provider.',
};

describe('eventConsequence', () => {
  const usable = { stock: false, crypto: true };

  it('states the consequence for an all-stock event (GameStop Squeeze)', () => {
    const line = eventConsequence(['GME', 'AMC', 'BB', 'NOK'], usable);
    expect(line).toContain('needs a market-data key');
    expect(line).toContain('no symbol of this event can be fetched');
  });

  it('names the skipped symbols on a mixed event', () => {
    const line = eventConsequence(['SPY', 'BTC'], usable);
    expect(line).toContain('SPY');
    expect(line).toContain('will be skipped');
    expect(line).not.toContain('BTC,');
  });

  it('is silent when every symbol has a usable provider', () => {
    expect(eventConsequence(['GME'], { stock: true, crypto: true })).toBeNull();
    expect(eventConsequence(['GME'], undefined)).toBeNull();
  });
});

describe('bannerCopy', () => {
  it('tells a keyless install that stocks need a free key and crypto works', () => {
    const copy = bannerCopy(KEYLESS);
    expect(copy).not.toBeNull();
    expect(copy!.headline).toContain('Stocks need a free data key');
    expect(copy!.headline).toContain('crypto works with zero keys');
    expect(copy!.body).toContain('Tiingo');
    expect(copy!.body).toContain('Alpaca');
    expect(copy!.body).toContain('not missing history');
  });

  it('renders nothing when both asset classes are usable', () => {
    expect(bannerCopy({ ...KEYLESS, stockUsable: true, cryptoUsable: true })).toBeNull();
  });

  it('surfaces the backend hint when no provider is usable at all', () => {
    const copy = bannerCopy({ ...KEYLESS, cryptoUsable: false, hint: 'No price provider is usable.' });
    expect(copy!.headline).toContain('No market-data provider is usable');
    expect(copy!.body).toBe('No price provider is usable.');
  });

  it('covers the crypto-only-missing case', () => {
    const copy = bannerCopy({ ...KEYLESS, stockUsable: true, cryptoUsable: false });
    expect(copy!.headline).toContain('Crypto has no usable provider');
  });
});
