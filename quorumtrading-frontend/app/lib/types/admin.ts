export interface AdminUserInfo {
  id: string;
  email: string;
  displayName: string | null;
  role: string;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface BudgetSummary {
  totalAllocated: number;
  totalLifetimeSpend: number;
  depletedCount: number;
}

export interface AdminDashboardResponse {
  userCount: number;
  researchCount: number;
  strategyCount: number;
  simulationCount: number;
  simulationsByStatus: Record<string, number>;
  recentUsers: AdminUserInfo[];
  budgetSummary: BudgetSummary;
}

export interface UserWithBudget {
  id: string;
  email: string;
  displayName: string | null;
  role: string;
  createdAt: string;
  lastLoginAt: string | null;
  budgetLimit: number | null;
  lifetimeSpend: number;
  budgetStatus: 'AVAILABLE' | 'WARNING' | 'DEPLETED';
}

export interface AdminUserSearchResponse {
  users: UserWithBudget[];
}

export interface DbPoolStats {
  activeConnections: number;
  idleConnections: number;
  maxConnections: number;
}

export interface AdminHealthResponse {
  heapUsed: number;
  heapMax: number;
  cpuUsage: number;
  threadCount: number;
  uptimeSeconds: number;
  dbPool: DbPoolStats;
}
