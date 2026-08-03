'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import styled from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { SectionTitle } from '@/app/components/shared/SectionTitle';
import { AnimatedCard, CardContent, CardGrid } from '@/app/components/shared/AnimatedCard';
import { useAuth } from '@/app/hooks/useAuth';
import { useAdminDashboard, useAdminUserSearch, useAdminHealth } from '@/app/lib/api/hooks';
import type { UserWithBudget } from '@/app/lib/types/admin';

const PageContainer = styled.div`
  padding: ${p => p.theme.spacing.xl};
  max-width: 1400px;
  margin: 0 auto;
`;

const Section = styled.section`
  margin-bottom: ${p => p.theme.spacing.xl};
`;

const StatValue = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 28px;
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  color: ${p => p.theme.colors.terminalGreen};
  text-shadow: 0 0 12px ${p => p.theme.colors.terminalGreen}44;
`;

const StatLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-top: ${p => p.theme.spacing.xs};
`;

const StatusBreakdown = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${p => p.theme.spacing.sm};
  margin-top: ${p => p.theme.spacing.sm};
`;

const StatusBadge = styled.span<{ $color: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: color-mix(in srgb, ${p => p.$color} 10%, transparent);
  color: ${p => p.$color};
  border: 1px solid color-mix(in srgb, ${p => p.$color} 20%, transparent);
`;

const SearchInput = styled.input`
  width: 100%;
  max-width: 400px;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  outline: none;
  transition: border-color 0.2s ease;

  &:focus {
    border-color: ${p => p.theme.colors.terminalGreen};
  }

  &::placeholder {
    color: ${p => p.theme.colors.textMuted};
    opacity: 0.5;
  }
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
`;

const Th = styled.th`
  text-align: left;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  color: ${p => p.theme.colors.terminalBlue};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  text-transform: uppercase;
  letter-spacing: 0.08em;
  border-bottom: 1px solid ${p => p.theme.colors.border};
  white-space: nowrap;
`;

const Td = styled.td`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  color: ${p => p.theme.colors.textMuted};
  border-bottom: 1px solid ${p => p.theme.colors.border}44;
  white-space: nowrap;
`;

const RoleBadge = styled.span<{ $isAdmin: boolean }>`
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: ${p => p.$isAdmin ? p.theme.colors.terminalAmber + '22' : p.theme.colors.border + '44'};
  color: ${p => p.$isAdmin ? p.theme.colors.terminalAmber : p.theme.colors.textMuted};
`;

const ProgressBar = styled.div<{ $percent: number; $color: string }>`
  height: 8px;
  border-radius: 4px;
  background: ${p => p.theme.colors.border}44;
  overflow: hidden;
  position: relative;

  &::after {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    height: 100%;
    width: ${p => Math.min(p.$percent, 100)}%;
    background: ${p => p.$color};
    border-radius: 4px;
    transition: width 0.5s ease;
  }
`;

const HealthGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: ${p => p.theme.spacing.md};
`;

const HealthItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const HealthLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.06em;
`;

const HealthValue = styled.div<{ $color?: string }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.$color || p.theme.colors.text};
`;

const EmptyState = styled.div`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  padding: ${p => p.theme.spacing.lg};
  text-align: center;
  opacity: 0.6;
`;

const STATUS_COLORS: Record<string, string> = {
  PENDING: 'var(--qt-muted)',
  WAITING_FOR_DATA: 'var(--qt-agent-fundamental)',
  SIMULATING: 'var(--qt-info)',
  LIVE: 'var(--qt-agent-technical)',
  COMPLETED: 'var(--qt-info)',
  FAILED: 'var(--qt-err)',
};

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
}

function formatUptime(seconds: number): string {
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function formatDate(iso: string | null): string {
  if (!iso) return '-';
  const d = new Date(iso);
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
}

export default function AdminDashboardPage() {
  const router = useRouter();
  const { user, isLoading: authLoading } = useAuth();
  const [searchQuery, setSearchQuery] = useState('');

  const { data: dashboard, isLoading: dashLoading } = useAdminDashboard();
  const { data: searchResults, isLoading: searchLoading } = useAdminUserSearch(searchQuery);
  const { data: health } = useAdminHealth();

  // Redirect non-admin users
  if (!authLoading && (!user || user.role !== 'ADMIN')) {
    router.replace('/dashboard');
    return null;
  }

  if (authLoading) return null;

  const heapPercent = health ? (health.heapUsed / health.heapMax) * 100 : 0;
  const cpuPercent = health ? health.cpuUsage * 100 : 0;

  return (
    <DashboardLayout>
      <PageContainer>
        <SectionTitle $size="xl" $color="cyan">Admin Dashboard</SectionTitle>

        {/* Platform Overview */}
        <Section>
          <SectionTitle $size="md">Platform Overview</SectionTitle>
          <CardGrid $columns={4}>
            <AnimatedCard $static>
              <CardContent>
                <StatValue>{dashboard?.userCount ?? '-'}</StatValue>
                <StatLabel>Users</StatLabel>
              </CardContent>
            </AnimatedCard>
            <AnimatedCard $static>
              <CardContent>
                <StatValue>{dashboard?.researchCount ?? '-'}</StatValue>
                <StatLabel>Research</StatLabel>
              </CardContent>
            </AnimatedCard>
            <AnimatedCard $static>
              <CardContent>
                <StatValue>{dashboard?.strategyCount ?? '-'}</StatValue>
                <StatLabel>Strategies</StatLabel>
              </CardContent>
            </AnimatedCard>
            <AnimatedCard $static>
              <CardContent>
                <StatValue>{dashboard?.simulationCount ?? '-'}</StatValue>
                <StatLabel>Simulations</StatLabel>
                {dashboard?.simulationsByStatus && (
                  <StatusBreakdown>
                    {Object.entries(dashboard.simulationsByStatus).map(([status, count]) => (
                      <StatusBadge key={status} $color={STATUS_COLORS[status] || '#888'}>
                        {status}: {count}
                      </StatusBadge>
                    ))}
                  </StatusBreakdown>
                )}
              </CardContent>
            </AnimatedCard>
          </CardGrid>
        </Section>

        {/* Budget Overview */}
        <Section>
          <SectionTitle $size="md">Budget Overview</SectionTitle>
          <CardGrid $columns={3}>
            <AnimatedCard $static>
              <CardContent>
                <StatValue>${dashboard?.budgetSummary?.totalAllocated?.toFixed(2) ?? '0.00'}</StatValue>
                <StatLabel>Total Allocated</StatLabel>
              </CardContent>
            </AnimatedCard>
            <AnimatedCard $static>
              <CardContent>
                <StatValue>${dashboard?.budgetSummary?.totalLifetimeSpend?.toFixed(2) ?? '0.00'}</StatValue>
                <StatLabel>Lifetime Spend</StatLabel>
              </CardContent>
            </AnimatedCard>
            <AnimatedCard $static>
              <CardContent>
                <StatValue>{dashboard?.budgetSummary?.depletedCount ?? 0}</StatValue>
                <StatLabel>Depleted Budgets</StatLabel>
              </CardContent>
            </AnimatedCard>
          </CardGrid>
        </Section>

        {/* User Search */}
        <Section>
          <SectionTitle $size="md">User Search</SectionTitle>
          <AnimatedCard $static $delay={100}>
            <CardContent>
              <SearchInput
                type="text"
                placeholder="Search by email or name (min 2 chars)..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
              {searchQuery.length >= 2 && (
                <div style={{ marginTop: '16px', overflowX: 'auto' }}>
                  {searchLoading ? (
                    <EmptyState>Searching...</EmptyState>
                  ) : searchResults?.users && searchResults.users.length > 0 ? (
                    <Table>
                      <thead>
                        <tr>
                          <Th>Email</Th>
                          <Th>Name</Th>
                          <Th>Role</Th>
                          <Th>Budget</Th>
                          <Th>Spend</Th>
                          <Th>Status</Th>
                          <Th>Last Login</Th>
                        </tr>
                      </thead>
                      <tbody>
                        {searchResults.users.map((u: UserWithBudget) => (
                          <tr key={u.id}>
                            <Td>{u.email}</Td>
                            <Td>{u.displayName || '-'}</Td>
                            <Td><RoleBadge $isAdmin={u.role === 'ADMIN'}>{u.role}</RoleBadge></Td>
                            <Td>{u.budgetLimit != null ? `$${u.budgetLimit.toFixed(2)}` : 'Unlimited'}</Td>
                            <Td>${u.lifetimeSpend.toFixed(2)}</Td>
                            <Td>
                              <StatusBadge $color={
                                u.budgetStatus === 'DEPLETED' ? 'var(--qt-err)' :
                                u.budgetStatus === 'WARNING' ? 'var(--qt-agent-fundamental)' : 'var(--qt-agent-technical)'
                              }>
                                {u.budgetStatus}
                              </StatusBadge>
                            </Td>
                            <Td>{formatDate(u.lastLoginAt)}</Td>
                          </tr>
                        ))}
                      </tbody>
                    </Table>
                  ) : (
                    <EmptyState>No users found</EmptyState>
                  )}
                </div>
              )}
            </CardContent>
          </AnimatedCard>
        </Section>

        {/* Recent Users */}
        {dashboard?.recentUsers && dashboard.recentUsers.length > 0 && (
          <Section>
            <SectionTitle $size="md">Recent Users</SectionTitle>
            <AnimatedCard $static $delay={200}>
              <CardContent>
                <Table>
                  <thead>
                    <tr>
                      <Th>Email</Th>
                      <Th>Name</Th>
                      <Th>Role</Th>
                      <Th>Joined</Th>
                      <Th>Last Login</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {dashboard.recentUsers.map((u) => (
                      <tr key={u.id}>
                        <Td>{u.email}</Td>
                        <Td>{u.displayName || '-'}</Td>
                        <Td><RoleBadge $isAdmin={u.role === 'ADMIN'}>{u.role}</RoleBadge></Td>
                        <Td>{formatDate(u.createdAt)}</Td>
                        <Td>{formatDate(u.lastLoginAt)}</Td>
                      </tr>
                    ))}
                  </tbody>
                </Table>
              </CardContent>
            </AnimatedCard>
          </Section>
        )}

        {/* Health Telemetry */}
        <Section>
          <SectionTitle $size="md">Health Telemetry</SectionTitle>
          <AnimatedCard $static $delay={300}>
            <CardContent>
              {health ? (
                <HealthGrid>
                  <HealthItem>
                    <HealthLabel>Heap Memory</HealthLabel>
                    <HealthValue $color={heapPercent > 85 ? 'var(--qt-err)' : heapPercent > 70 ? 'var(--qt-agent-fundamental)' : 'var(--qt-agent-technical)'}>
                      {formatBytes(health.heapUsed)} / {formatBytes(health.heapMax)}
                    </HealthValue>
                    <ProgressBar
                      $percent={heapPercent}
                      $color={heapPercent > 85 ? 'var(--qt-err)' : heapPercent > 70 ? 'var(--qt-agent-fundamental)' : 'var(--qt-agent-technical)'}
                    />
                  </HealthItem>
                  <HealthItem>
                    <HealthLabel>CPU Usage</HealthLabel>
                    <HealthValue $color={cpuPercent > 80 ? 'var(--qt-err)' : cpuPercent > 50 ? 'var(--qt-agent-fundamental)' : 'var(--qt-agent-technical)'}>
                      {cpuPercent.toFixed(1)}%
                    </HealthValue>
                    <ProgressBar
                      $percent={cpuPercent}
                      $color={cpuPercent > 80 ? 'var(--qt-err)' : cpuPercent > 50 ? 'var(--qt-agent-fundamental)' : 'var(--qt-agent-technical)'}
                    />
                  </HealthItem>
                  <HealthItem>
                    <HealthLabel>Threads</HealthLabel>
                    <HealthValue>{health.threadCount}</HealthValue>
                  </HealthItem>
                  <HealthItem>
                    <HealthLabel>Uptime</HealthLabel>
                    <HealthValue>{formatUptime(health.uptimeSeconds)}</HealthValue>
                  </HealthItem>
                  <HealthItem>
                    <HealthLabel>DB Active</HealthLabel>
                    <HealthValue>{health.dbPool.activeConnections}</HealthValue>
                  </HealthItem>
                  <HealthItem>
                    <HealthLabel>DB Idle</HealthLabel>
                    <HealthValue>{health.dbPool.idleConnections}</HealthValue>
                  </HealthItem>
                </HealthGrid>
              ) : (
                <EmptyState>Loading health data...</EmptyState>
              )}
            </CardContent>
          </AnimatedCard>
        </Section>
      </PageContainer>
    </DashboardLayout>
  );
}
