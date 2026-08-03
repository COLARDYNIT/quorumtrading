/**
 * Runtime feature flags.
 *
 * Distinct from `InstanceInfo`, which describes the deployment and is cached forever: a flag can
 * be targeted at individual people, so two users of the same instance can legitimately get
 * different answers and the result must be re-fetched per session rather than treated as static.
 */

/** Flag names, matching `Feature.flagName()` on the backend. */
export type FeatureName = 'challenges' | 'market-research';

export interface FeatureAvailability {
  /** Flag name to whether that feature is switched on for the current caller. */
  features: Partial<Record<FeatureName, boolean>>;
}

/** One activation strategy, in Unleash's vocabulary. */
export interface FeatureFlagStrategy {
  /** `default` (everyone), `userWithId` (listed emails), `flexibleRollout` (percentage). */
  name: string;
  parameters: Record<string, string>;
}

/** Admin view of one flag. */
export interface FeatureFlag {
  name: FeatureName | string;
  description: string;
  enabled: boolean;
  strategies: FeatureFlagStrategy[];
  updatedAt: string;
  /** User id of the administrator who last changed it; null for a seeded flag. */
  updatedBy: string | null;
}

export interface UpdateFeatureFlagRequest {
  enabled: boolean;
  /**
   * Convenience over `strategies`: switch the flag on for these email addresses only. Leave
   * undefined or empty to mean "everyone".
   */
  enabledForEmails?: string[];
  strategies?: FeatureFlagStrategy[];
}
