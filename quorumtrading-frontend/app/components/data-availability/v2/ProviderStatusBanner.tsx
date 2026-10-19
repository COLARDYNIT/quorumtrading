'use client';

import styled from 'styled-components';
import { StatusPill } from '@/app/components/shared/v2';
import type { MarketDataStatusResponse } from '@/app/lib/types/dataAvailability';

/**
 * Keyless-install banner for the Data Availability page.
 *
 * Renders ONLY when an asset class has no usable (enabled + credentialed)
 * provider — the state `get_market_data_status` always reported but the web UI
 * never showed. Says the fix (a free data key) before the user can misread a
 * failed stock backfill as a coverage problem, and lists every provider's
 * credential state so "which key?" has an answer on the same screen.
 */
interface ProviderStatusBannerProps {
  status: MarketDataStatusResponse | undefined;
}

const AMBER = '#ffb000';

const Wrap = styled.div`
  border: 1px solid rgba(255, 176, 0, 0.45);
  background: rgba(255, 176, 0, 0.06);
  border-radius: ${p => p.theme.borderRadius.md};
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;

  .headline {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${AMBER};
  }
  .body {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 12.5px;
    color: ${p => p.theme.colors.fgMuted};
    max-width: 86ch;
  }
`;

const Providers = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
`;

const ProviderChip = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 7px;
  .name {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 12px;
    color: ${p => p.theme.colors.fgStrong};
  }
`;

/** Pill state for one provider: usable / needs key / switched off. */
function providerPill(p: MarketDataStatusResponse['providers'][number]): {
  kind: 'live' | 'restricted' | 'neutral';
  label: string;
} {
  if (p.usable) return { kind: 'live', label: 'ready' };
  if (!p.enabled) return { kind: 'neutral', label: 'off' };
  return { kind: 'restricted', label: 'no key' };
}

/** Headline + body copy for the unusable asset classes. */
export function bannerCopy(status: MarketDataStatusResponse): {
  headline: string;
  body: string;
} | null {
  const { stockUsable, cryptoUsable } = status;
  if (stockUsable && cryptoUsable) return null;
  if (!stockUsable && cryptoUsable) {
    return {
      headline: 'Stocks need a free data key — crypto works with zero keys',
      body:
        'No stock provider on this deployment holds credentials, so stock backfills return ' +
        'nothing and stock-heavy historic events cannot be fetched. Add a free key — Tiingo ' +
        '(email signup) or Alpaca — set it in your environment and restart the backend. ' +
        'This is a configuration gap, not missing history: the data exists once a key is set.',
    };
  }
  if (stockUsable && !cryptoUsable) {
    return {
      headline: 'Crypto has no usable provider on this deployment',
      body:
        'Stock candles are served, but no enabled provider can fetch crypto. Enable one of ' +
        'the keyless crypto sources or configure a provider that carries crypto candles.',
    };
  }
  return {
    headline: 'No market-data provider is usable — every backfill will return nothing',
    body: status.hint,
  };
}

export function ProviderStatusBanner({ status }: ProviderStatusBannerProps) {
  if (!status) return null;
  const copy = bannerCopy(status);
  if (!copy) return null;

  return (
    <Wrap role="status">
      <span className="headline">{copy.headline}</span>
      <span className="body">{copy.body}</span>
      <Providers>
        {status.providers.map(p => {
          const pill = providerPill(p);
          return (
            <ProviderChip key={p.name} title={p.detail}>
              <span className="name">{p.name}</span>
              <StatusPill kind={pill.kind} still compact>
                {pill.label}
              </StatusPill>
            </ProviderChip>
          );
        })}
      </Providers>
    </Wrap>
  );
}
