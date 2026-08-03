/**
 * Instance descriptor — what kind of deployment the frontend is talking to.
 *
 * Mirrors GET /api/v1/instance (anonymous, available on every edition). One frontend build
 * serves all modes: the index page and broker-region choices adapt to this at runtime.
 */

export type InstanceEdition = 'saas' | 'standalone';

export type BrokerRegionId = 'ALPACA' | 'SAXO';

export interface InstanceInfo {
  /** 'saas' = hosted multi-user; 'standalone' = single-user self-host. */
  edition: InstanceEdition;
  /** Broker integrations this instance offers (US = ALPACA, EU = SAXO). */
  brokers: BrokerRegionId[];
  /**
   * Whether the recurring-investment (DCA) scheduler runs on this instance.
   *
   * Off by default everywhere — it moves real money, so enabling it is a deliberate operator
   * step. While it is false, an ACTIVE plan will sit with a `nextExecutionAt` date and never
   * fire, so no surface may promise monthly execution without checking this first.
   */
  automaticInvestmentsEnabled: boolean;
}
