'use client';

import { LandingV2 } from './components/landing/v2';
import { LocalHome } from './components/local/LocalHome';
import { useInstanceInfo } from './lib/api/hooks';

/**
 * Index page — adapts to the deployment at runtime so one build serves every edition.
 *
 * The marketing hero is the DEFAULT render, shown immediately (and in the prerendered
 * HTML — SEO) while the /instance probe is in flight; only a confirmed `standalone`
 * answer swaps in the local home. The hosted SaaS must never blank-flash or lose its
 * prerendered hero to a probe round-trip; a standalone instance briefly showing the
 * hero before the swap (one localhost round-trip) is the acceptable side of the trade.
 */
export default function LandingPage() {
  const { data: instance } = useInstanceInfo();

  if (instance?.edition === 'standalone') return <LocalHome />;
  return <LandingV2 />;
}
