export interface ChallengeResponse {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  ticker: string;
  tickerType: 'STOCK' | 'CRYPTO';
  initialCash: number;
  simulationStartDate: string;
  simulationEndDate: string;
  participationOpen: string;
  participationClose: string;
  status: 'UPCOMING' | 'ACTIVE' | 'COMPLETED';
  isBacktest: boolean;
  acceptingEntries: boolean;
  participantCount: number;
  createdAt: string;
  strategiesPublic: boolean;
  entries: ChallengeEntryResponse[];
}

export interface ChallengeEntryResponse {
  rank: number;
  simulationId: string;
  simulationName: string;
  strategyName: string;
  ownerDisplayName: string;
  currentValue: number;
  returnPercent: string | null;
  status: 'PENDING' | 'WAITING_FOR_DATA' | 'SIMULATING' | 'LIVE' | 'COMPLETED' | 'FAILED';
  llmCostUsd: string | null;
  executionTimeSeconds: number | null;
}

export interface CreateChallengeRequest {
  name: string;
  description?: string;
  ticker: string;
  investmentType?: string;
  initialCash: number;
  simulationStartDate: string;
  simulationEndDate: string;
  participationOpen: string;
  participationClose: string;
  strategiesPublic?: boolean;
}

export interface EligibleStrategy {
  id: string;
  name: string;
  compliant: boolean;
  violations: string[];
}

export interface JoinChallengeRequest {
  simulationId: string;
}

export interface ChallengeChartData {
  simulations: ChallengeChartSimulation[];
  points: (number | null)[][];
  buyAndHold: {
    ticker: string;
    returnPercent: number;
    finalValue: number;
  } | null;
}

export interface ChallengeChartSimulation {
  id: string;
  name: string;
  strategyName: string;
  ownerDisplayName: string;
  status: string;
  startValue: number;
}

export function buildStrategyLink(ticker: string, tickerType: 'STOCK' | 'CRYPTO', challengeId?: string): string {
  const param = tickerType === 'CRYPTO' ? 'crypto' : 'stock';
  const base = `/strategies/new?${param}=${ticker}`;
  return challengeId ? `${base}&challengeId=${challengeId}` : base;
}

export function getChallengeStatusColor(status: ChallengeResponse['status']): string {
  switch (status) {
    case 'UPCOMING': return '#ff9800';
    case 'ACTIVE': return '#4caf50';
    case 'COMPLETED': return '#9e9e9e';
  }
}

export function formatChallengeTimeRemaining(participationClose: string): string {
  const end = new Date(participationClose);
  const now = new Date();
  const diff = end.getTime() - now.getTime();

  if (diff <= 0) return 'Ended';

  const days = Math.floor(diff / (1000 * 60 * 60 * 24));
  const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));

  if (days > 0) return `${days}d ${hours}h remaining`;
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
  return `${hours}h ${minutes}m remaining`;
}

export function formatExecutionTime(seconds: number | null): string {
  if (seconds == null) return '—';
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  const hours = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  return `${hours}h ${mins}m`;
}
