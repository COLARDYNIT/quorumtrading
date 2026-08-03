/**
 * FAQ entries manifest.
 * Entries are grouped by category.
 *
 * When adding a new entry:
 * 1. Create the markdown file in /app/faq/content/[slug].ts
 * 2. Add the metadata here
 * 3. Add the import to faqContents map in /app/faq/[slug]/page.tsx
 */

export type FaqCategory = 'core' | 'trading' | 'platform';

export interface FaqEntry {
  slug: string;
  title: string;
  summary: string;
  category: FaqCategory;
}

export const FAQ_CATEGORIES: Record<FaqCategory, { label: string; description: string }> = {
  core: { label: 'Core Concepts', description: 'What QuorumTrading is and how it works' },
  trading: { label: 'Trading', description: 'Strategies, simulations, and portfolios' },
  platform: { label: 'Platform', description: 'Tools and integrations' },
};

export const entries: FaqEntry[] = [
  // Core Concepts
  {
    slug: 'what-is-quorumtrading',
    title: 'What is QuorumTrading?',
    summary: 'Multiple AI agents working together to make trading decisions.',
    category: 'core',
  },
  {
    slug: 'ai-agents',
    title: 'The AI Agents',
    summary: 'Technical, Fundamental, Sentiment, Orchestrator — an overview of the team.',
    category: 'core',
  },
  {
    slug: 'technical-agent',
    title: 'Technical Agent',
    summary: 'Reads price charts, RSI, MACD, Bollinger Bands, and multi-timeframe trends.',
    category: 'core',
  },
  {
    slug: 'fundamental-agent',
    title: 'Fundamental Agent',
    summary: 'Evaluates company health: P/E, margins, earnings, analyst ratings, and news.',
    category: 'core',
  },
  {
    slug: 'sentiment-agent',
    title: 'Sentiment Agent',
    summary: 'Gauges market mood from news headlines, Reddit posts, and sentiment scores.',
    category: 'core',
  },
  {
    slug: 'orchestrator-agent',
    title: 'Orchestrator',
    summary: 'Weighs all agent votes and recent trades to make the final call.',
    category: 'core',
  },
  {
    slug: 'consensus',
    title: 'Consensus',
    summary: 'How AI agents agree (or disagree) on what to do.',
    category: 'core',
  },

  // Trading
  {
    slug: 'strategies',
    title: 'Strategies',
    summary: 'Written rules that tell the AI agents what to look for and how to trade.',
    category: 'trading',
  },
  {
    slug: 'backtesting',
    title: 'Backtesting & Simulations',
    summary: 'Replay historical market data to test strategies before risking real money.',
    category: 'trading',
  },
  {
    slug: 'portfolio',
    title: 'Portfolio',
    summary: 'Track cash, positions, and total value during simulations or live sessions.',
    category: 'trading',
  },
  {
    slug: 'benchmarks',
    title: 'Benchmarks',
    summary: 'Compare your strategy against the S&P 500 or other baselines.',
    category: 'trading',
  },

  // Platform
  {
    slug: 'market-research',
    title: 'Market Research',
    summary: 'Describe an idea or thesis — AI finds tickers, analyzes them, and suggests strategies.',
    category: 'platform',
  },
  {
    slug: 'mcp-integration',
    title: 'MCP Terminal Integration',
    summary: 'Trade from Claude Code, Cursor, or any MCP-compatible AI agent.',
    category: 'platform',
  },
  {
    slug: 'live-trading',
    title: 'Live Trading',
    summary: 'Connect a broker and let AI agents trade with real money.',
    category: 'platform',
  },
  {
    slug: 'eu-tax',
    title: 'EU Trading & Tax',
    summary: 'How tax works on a European broker account — your broker handles it, and this is not tax advice.',
    category: 'platform',
  },
  {
    slug: 'budget',
    title: 'AI Budget',
    summary: 'Prepaid, non-refundable balance that powers your AI agent decisions.',
    category: 'platform',
  },
];

export function getEntry(slug: string): FaqEntry | undefined {
  return entries.find(e => e.slug === slug);
}

export function getEntriesByCategory(category: FaqCategory): FaqEntry[] {
  return entries.filter(e => e.category === category);
}
