/**
 * Runtime LLM provider selection (self-host edition).
 *
 * Mirrors the backend's LlmProviderStatusResponse from GET /api/v1/llm/providers.
 * The endpoints 404 on deployments where the selection feature is disabled (hosted SaaS) —
 * callers must treat 404 as "feature not present", not as an error.
 */

export type LlmProviderId = 'LOCAL' | 'LOCAL_HTTP' | 'OPENROUTER';

export interface LlmProviderEntry {
  /** Stable provider id. */
  id: LlmProviderId;
  /** Human-readable name, e.g. "Local (claude CLI)". */
  label: string;
  /** Whether the provider could serve a call right now (CLI present / key configured). */
  available: boolean;
  /** Whether this is the active provider. */
  selected: boolean;
  /** One-line explanation of the availability state. */
  detail: string;
}

export interface LlmProviderStatus {
  /** Active provider id, or null when none has been chosen yet (first run). */
  selected: LlmProviderId | null;
  /** ISO timestamp of the active selection, or null. */
  configuredAt: string | null;
  providers: LlmProviderEntry[];
}

export interface SelectLlmProviderRequest {
  provider: LlmProviderId;
  /** OpenRouter API key to store (encrypted at rest); only needed when none is configured. */
  apiKey?: string;
}
