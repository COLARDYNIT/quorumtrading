/**
 * V1 Dense panels for the redesigned Simulation Detail page.
 *
 * Each panel is self-contained: reads from SimulationDataContext, renders
 * inside a v2 Panel chrome, and uses v2 primitives (ActionPill, VoteChip,
 * AgentCard) for inner content.
 *
 * Layout is composed in `app/simulations/[id]/page.tsx`.
 */
export { ChartPanel } from './ChartPanel';
export { HoldingsPanel } from './HoldingsPanel';
export { DecisionsPanel } from './DecisionsPanel';
export { AgentsPanel } from './AgentsPanel';
export { DcaPanel } from './DcaPanel';
export { ConsensusInspectModal } from './ConsensusInspectModal';
