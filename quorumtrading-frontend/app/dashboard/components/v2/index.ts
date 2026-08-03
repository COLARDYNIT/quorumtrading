/**
 * Control Room dashboard primitives. Composed in `app/dashboard/page.tsx`.
 * Each panel reads from its own queries — there is no shared dashboard
 * context, because most panels are independent of each other.
 */
export { AccountScopeRow } from './AccountScopeRow';
export type { ScopeId, ScopeAccount } from './AccountScopeRow';
export { ControlKpis } from './ControlKpis';
export { CtaBanner } from './CtaBanner';
export { LiveStrategiesPanel } from './LiveStrategiesPanel';
export { RecentDecisionsPanel } from './RecentDecisionsPanel';
export { AgentHeatmapPanel } from './AgentHeatmapPanel';
export { UpcomingPanel } from './UpcomingPanel';
export { StrategyRosterPanel } from './StrategyRosterPanel';
export { NotificationsPanel } from './NotificationsPanel';
export { ResearchPanel } from './ResearchPanel';
export { ChallengePanel } from './ChallengePanel';
export { OnboardingHero } from './OnboardingHero';
export { SparkLine } from './SparkLine';
