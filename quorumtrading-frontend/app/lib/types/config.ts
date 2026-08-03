/**
 * Types for AI Configuration settings
 */

/**
 * Trading context for model selection
 */
export type TradingContext = 'RESEARCH' | 'BACKTESTING' | 'PAPER_TRADING' | 'LIVE_TRADING' | 'CHAT';

/**
 * Model tier classification from backend (based on A/B testing)
 */
export type ModelTier = 'RECOMMENDED' | 'PROVEN' | 'BUDGET' | 'EXPERIMENTAL' | 'AVOID';

/**
 * Display labels for model tiers
 */
export const TIER_LABELS: Record<ModelTier, string> = {
  RECOMMENDED: 'Recommended (Best Value)',
  PROVEN: 'Proven (Tested & Working)',
  BUDGET: 'Budget (Trusted Provider)',
  EXPERIMENTAL: 'Experimental (Untested)',
  AVOID: 'Avoid (Known Issues)',
};

/**
 * Tier ordering for display (lower = better)
 */
export const TIER_ORDER: ModelTier[] = ['RECOMMENDED', 'PROVEN', 'BUDGET', 'EXPERIMENTAL', 'AVOID'];

/**
 * LLM model information from the backend
 */
export interface LLMModel {
  id: string;
  name: string;
  description: string | null;
  provider: string | null;
  contextLength: number;
  promptCostPerMillion: number;
  completionCostPerMillion: number;
  modalities: string[];
  supportsWebSearch: boolean;
  isFree: boolean;
  tier?: ModelTier | null;
  tierReason?: string | null;
}

/**
 * Agent roles for per-agent model overrides (trading contexts)
 */
export type AgentRole = 'TECHNICAL' | 'FUNDAMENTAL' | 'SENTIMENT' | 'ORCHESTRATOR';

export const AGENT_ROLES: { key: AgentRole; label: string }[] = [
  { key: 'TECHNICAL', label: 'Technical' },
  { key: 'FUNDAMENTAL', label: 'Fundamental' },
  { key: 'SENTIMENT', label: 'Sentiment' },
  { key: 'ORCHESTRATOR', label: 'Orchestrator' },
];

/**
 * Agent roles for research context (different sub-agents than trading)
 */
export type ResearchAgentRole = 'NEWS_SENTIMENT_ANALYST' | 'STRATEGY_DESIGNER' | 'VIABILITY_ASSESSOR';

export const RESEARCH_AGENT_ROLES: { key: ResearchAgentRole; label: string }[] = [
  { key: 'NEWS_SENTIMENT_ANALYST', label: 'News & Sentiment Analyst' },
  { key: 'STRATEGY_DESIGNER', label: 'Strategy Designer' },
  { key: 'VIABILITY_ASSESSOR', label: 'Viability Assessor' },
];

/**
 * Returns the applicable agent roles for a given trading context.
 * CHAT has no sub-agents, RESEARCH has research-specific agents, others use trading agents.
 */
export function getAgentRolesForContext(context: TradingContext): { key: string; label: string }[] {
  switch (context) {
    case 'CHAT':
      return [];
    case 'RESEARCH':
      return RESEARCH_AGENT_ROLES;
    default:
      return AGENT_ROLES;
  }
}

/**
 * User's model preferences for each trading context
 */
export interface ModelPreferences {
  research: string | null;
  backtesting: string | null;
  paperTrading: string | null;
  liveTrading: string | null;
  chat: string | null;
  agentOverrides?: Record<string, Record<string, string | null>> | null;
}

/**
 * Request to update model preferences
 */
export interface UpdateModelPreferencesRequest {
  research?: string | null;
  backtesting?: string | null;
  paperTrading?: string | null;
  liveTrading?: string | null;
  chat?: string | null;
  agentOverrides?: Record<string, Record<string, string | null>> | null;
}

/**
 * Context metadata for display
 */
export interface ContextInfo {
  key: TradingContext;
  label: string;
  description: string;
  fieldName: keyof ModelPreferences;
}

/**
 * What one trading context will actually run, as computed by the backend.
 *
 * There is deliberately no `defaultModel` on {@link ContextInfo} any more. The frontend used to
 * hardcode the backend's defaults because no endpoint exposed them — and, being an unowned
 * duplicate, they drifted: this file went on advertising `google/gemini-2.0-flash-001` as the
 * paper-trading default long after the backend replaced it and marked it sunset. Fetch, never
 * restate.
 */
export interface EffectiveModel {
  context: TradingContext;
  /** What the resolution chain chose. */
  requested: string | null;
  /** What the active engine will actually run; null when nothing servable resolved. */
  effective: string | null;
  /** user_override | system_default | strategy_override | unresolved */
  source: string;
  decidedByTier: string;
  /** The engine that will serve it, null when none is selected. */
  engine: string | null;
  /** Why effective differs from requested, when it does. */
  substitutionReason: string | null;
  effort: string | null;
}

export interface EffectiveModelsResponse {
  models: EffectiveModel[];
}

/**
 * The model a context will actually run, or null when it is not known yet.
 *
 * One helper rather than the same three-line lookup written out in every page — three copies of a
 * lookup is three chances for one of them to render `requested` instead of `effective`, which is
 * exactly the class of disagreement this endpoint exists to end.
 *
 * Null covers two honest cases and is deliberately not distinguished here: the fetch has not landed,
 * and the active engine will not serve the configured model. A caller renders an em dash for both.
 */
export function describeFallbackKind(kind: FallbackKind): string {
  switch (kind) {
    case 'MODEL_CHAIN':
      return 'model chain';
    case 'TOOL_CHAIN':
      // Tools, not models — the CLI engine runs YOUR model on whichever tool answers.
      return 'installed tools';
    default:
      return 'no chain';
  }
}

/**
 * Split a model id into the vendor namespace and the name, for display.
 *
 * A local server advertises bare names (`llama3.2`) with no namespace, which is exactly what
 * distinguishes them from hosted ids — so an absent vendor is normal, not missing data.
 */
export function splitModelId(modelId: string): { vendor: string | null; name: string } {
  const slash = modelId.indexOf('/');
  if (slash <= 0) {
    return { vendor: null, name: modelId };
  }
  return { vendor: modelId.slice(0, slash), name: modelId.slice(slash + 1) };
}

export function effectiveModelFor(
  response: EffectiveModelsResponse | undefined,
  context: TradingContext
): string | null {
  return response?.models.find((m) => m.context === context)?.effective ?? null;
}

/**
 * What "fallback" means on the engine that is actually selected.
 *
 * This used to be one engine's model chain served verbatim from config, rendered as if universal —
 * so a deployment running a local server was shown four OpenRouter models, not one of which it
 * could serve. The chain and its *kind* now come from the active engine.
 */
export type FallbackKind = 'MODEL_CHAIN' | 'TOOL_CHAIN' | 'SERVER_DEFAULT' | 'NONE';

export interface FallbackConfig {
  /** The selected engine, or null when none has been chosen. */
  engine: string | null;
  kind: FallbackKind;
  /** Models for OpenRouter, INSTALLED tools for the CLI engine, empty when there is no chain. */
  fallbackChain: string[];
  description: string;
  /** Whether the platform would fall through to a DIFFERENT engine. False with runtime selection. */
  crossEngineFailover: boolean;
}

/**
 * Request to estimate the AI budget cost of a backtest or benchmark
 */
export interface CostEstimateRequest {
  strategyIds: string[];
  startDate: string;  // ISO 8601
  endDate: string;    // ISO 8601
}

/**
 * Estimated AI budget cost for a backtest or benchmark
 */
export interface CostEstimateResponse {
  estimatedCost: number;
  lowerBound: number;
  upperBound: number;
  tradingDays: number;
  totalLLMCalls: number;
  tickerCount: number;
  agentsPerRound: number;
  modelId: string;
  modelName: string;
  costPerCall: number;
}

/**
 * News data availability — earliest date from which historical news is available
 */
export interface NewsAvailabilityResponse {
  earliestNewsDate: string;  // ISO date (YYYY-MM-DD)
}

/**
 * Model reliability entry from the backend
 */
export interface ModelReliabilityEntry {
  modelId: string;
  successCount: number;
  failureCount: number;
  failureRate: number;
}

/**
 * Model reliability stats response
 */
export interface ModelReliabilityResponse {
  entries: ModelReliabilityEntry[];
}

/**
 * All trading contexts with their display info
 */
export const TRADING_CONTEXTS: ContextInfo[] = [
  {
    key: 'RESEARCH',
    label: 'Market Research',
    description: 'Requires web search capability (:online models). Choose a model with strong reasoning for market analysis.',
    fieldName: 'research',
  },
  {
    key: 'BACKTESTING',
    label: 'Backtesting',
    description: 'Runs many AI calls per trading day. Use fast, cost-effective models to keep budgets low.',
    fieldName: 'backtesting',
  },
  {
    key: 'PAPER_TRADING',
    label: 'Paper Trading',
    description: 'Simulated live trading with real-time decisions. Balance cost and quality.',
    fieldName: 'paperTrading',
  },
  {
    key: 'LIVE_TRADING',
    label: 'Live Trading',
    description: 'Real money at stake. Use the most capable and reliable model you can afford.',
    fieldName: 'liveTrading',
  },
  {
    key: 'CHAT',
    label: 'AI Agent',
    description: 'General AI assistant. Use a model with strong reasoning and conversational ability.',
    fieldName: 'chat',
  },
];
