/**
 * v2 design-system primitives. Built for the dense, Bloomberg-leaning anchor
 * pages. Sans chrome, mono numerics, neutral foreground, brand reserved for
 * identity + primary CTA only.
 *
 * Do not mix with `app/components/shared/AnimatedCard` / `SectionTitle` /
 * `PageSection` — those are the older mono-everywhere generation and will be
 * deprecated as pages migrate.
 */
export { StatusPill } from './StatusPill';
export type { StatusPillKind } from './StatusPill';

export { ActionPill } from './ActionPill';
export type { ActionKind } from './ActionPill';

export { Tag } from './Tag';

export { Badge } from './Badge';

export { Switch } from './Switch';

export { RowAction, TextAction } from './RowAction';
export type { RowActionKind } from './RowAction';

export { VoteChip, VoteChipRow } from './VoteChip';
export type { VoteAgent } from './VoteChip';

export { Panel } from './Panel';

export { KpiStrip } from './KpiStrip';
export type { KpiItem } from './KpiStrip';

export { AgentCard } from './AgentCard';
export type { AgentKind } from './AgentCard';

export { SimHeader } from './SimHeader';

export { HedgedValue } from './HedgedValue';
