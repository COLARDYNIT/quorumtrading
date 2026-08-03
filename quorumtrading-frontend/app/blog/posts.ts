/**
 * Blog posts manifest.
 * Posts are ordered newest first.
 *
 * When adding a new post:
 * 1. Create the markdown file in /app/blog/posts/[slug].ts
 * 2. Add the metadata here at the TOP of the array
 * 3. Add the import to postContents map in /app/blog/[slug]/page.tsx
 */

export interface BlogPost {
  slug: string;
  title: string;
  date: string; // ISO format: YYYY-MM-DD
  summary: string;
  tags: string[];
}

export const posts: BlogPost[] = [
  {
    slug: '2026-06-09-recurring-investment-plans',
    title: 'Recurring Investment Plans',
    date: '2026-06-09',
    summary: 'Automate dollar-cost averaging into your AI strategies — with performance accounting that counts your contributions as invested capital, never as profit.',
    tags: ['FEATURE', 'AI'],
  },
  {
    slug: '2026-03-25-challenges-and-new-dashboard',
    title: 'Challenges & Dashboard Redesign',
    date: '2026-03-25',
    summary: 'Compete on single-ticker challenges ranked by portfolio value, plus a redesigned dashboard that guides new users instead of dropping them into chat.',
    tags: ['FEATURE', 'UI'],
  },
  {
    slug: '2026-03-16-what-the-agents-see',
    title: 'What the Agents See',
    date: '2026-03-16',
    summary: 'A deep dive into exactly what data each AI agent receives — price stats, indicators, news, financials, and sentiment — and where it all comes from.',
    tags: ['AI', 'GUIDE'],
  },
  {
    slug: '2026-02-22-three-weeks-of-shipping',
    title: '188 Commits in Three Weeks',
    date: '2026-02-22',
    summary: 'Live Alpaca trading, buy-and-hold overlays, budget controls, Stripe payments, 5x faster consensus, and a full security audit.',
    tags: ['FEATURE', 'AI', 'UI'],
  },
  {
    slug: '2026-02-22-etfs-you-can-trade',
    title: 'ETFs You Can Trade on QuorumTrading',
    date: '2026-02-22',
    summary: 'A categorized guide to the ETFs available through Alpaca — from broad market trackers to sector bets, bonds, commodities, and leveraged plays.',
    tags: ['FEATURE', 'GUIDE'],
  },
  {
    slug: '2026-02-03-meet-the-assistant',
    title: 'Meet the Assistant',
    date: '2026-02-03',
    summary: 'A built-in AI assistant replaces the old dashboard. 23 models tested, chat costs cut 60x.',
    tags: ['FEATURE', 'AI'],
  },
  {
    slug: '2025-01-16-technical-indicators',
    title: 'Technical Indicators Exposed',
    date: '2025-01-16',
    summary: 'RSI, MACD, Bollinger Bands—see the quant math behind every AI trading decision.',
    tags: ['FEATURE', 'UI'],
  },
  {
    slug: '2025-01-13-ai-model-config',
    title: 'Choose Your AI Model',
    date: '2025-01-13',
    summary: 'Configure which LLM powers your trading agents. Set different models per context, see costs per decision.',
    tags: ['FEATURE', 'AI'],
  },
];

export function getPost(slug: string): BlogPost | undefined {
  return posts.find(p => p.slug === slug);
}
