'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import styled from 'styled-components';
import { textGlow } from '@/app/styles/theme/effects';
import { useBreadcrumbOptional } from '@/app/contexts/BreadcrumbContext';

const BreadcrumbContainer = styled.nav`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: ${p => p.theme.spacing.md};
  padding: ${p => p.theme.spacing.md} 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const BreadcrumbPath = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  flex-wrap: wrap;
  min-width: 0;
  flex: 1;
`;

const PageActions = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  flex-shrink: 0;
`;

const BreadcrumbItem = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
`;

const BreadcrumbLink = styled(Link)`
  color: ${p => p.theme.colors.textMuted};
  text-decoration: none;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  transition: all 0.2s ease;

  &:hover {
    color: ${p => p.theme.colors.primary};
    ${textGlow}
  }
`;

const BreadcrumbCurrent = styled.span`
  color: ${p => p.theme.colors.primary};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  ${textGlow}
`;

const BreadcrumbSeparator = styled.span`
  color: ${p => p.theme.colors.border};
  user-select: none;
`;

// Route label mappings — keep in sync with Sidebar navRoutes
const routeLabels: Record<string, string> = {
  '': 'Dashboard',
  'strategies': 'Strategies',
  'public': 'Library',
  'simulations': 'Simulations',
  'benchmarks': 'Benchmarks',
  'assistant': 'AI Assistant',
  'challenges': 'Challenges',
  'research': 'Research',
  'settings': 'Settings',
  'broker-accounts': 'Broker Accounts',
  'api-tokens': 'API Tokens',
  'config': 'AI Config',
  'usage': 'AI Usage',
  'budget': 'Budget',
  'account': 'Account',
  'blog': 'Changelog',
  'sessions': 'Sessions',
  'wizard': 'New Session',
  'viewer': 'Viewer',
  'edit': 'Edit',
  'simulate': 'Simulate',
  'new': 'New',
};

// Custom breadcrumb patterns for specific routes
// Note: Simulation pages use BreadcrumbContext to set breadcrumbs dynamically (with strategy link)
const customBreadcrumbPatterns: Record<string, (segments: string[]) => Array<{ label: string; path: string }>> = {
  '/admin/challenges/new': () => [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Admin', path: '/admin/dashboard' },
    { label: 'New Challenge', path: '/admin/challenges/new' },
  ],
  '/admin/feature-flags': () => [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Admin', path: '/admin/dashboard' },
    { label: 'Feature Flags', path: '/admin/feature-flags' },
  ],
  '/strategies/[id]': (segments) => [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Strategies', path: '/strategies' },
    { label: 'Details', path: `/strategies/${segments[1]}` },
  ],
  '/strategies/[id]/edit': (segments) => [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Strategies', path: '/strategies' },
    { label: `Strategy #${segments[1]?.slice(0, 8)}`, path: `/strategies/${segments[1]}` },
    { label: 'Edit', path: `/strategies/${segments[1]}/edit` },
  ],
  '/research/[id]': (segments) => [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Research', path: '/research' },
    { label: `Research #${segments[1]?.slice(0, 8)}`, path: `/research/${segments[1]}` },
  ],
};

export function Breadcrumb() {
  const pathname = usePathname();
  const breadcrumbContext = useBreadcrumbOptional();

  // Skip breadcrumb on homepage and dashboard
  if (pathname === '/' || pathname === '/dashboard') {
    return null;
  }

  // Use context-provided breadcrumbs if available (e.g., from simulation pages)
  if (breadcrumbContext?.customBreadcrumbs) {
    const contextBreadcrumbs = breadcrumbContext.customBreadcrumbs;
    return (
      <BreadcrumbContainer>
        <BreadcrumbPath>
          {contextBreadcrumbs.map((crumb, index) => (
            <BreadcrumbItem key={crumb.path}>
              {index < contextBreadcrumbs.length - 1 ? (
                <>
                  <BreadcrumbLink href={crumb.path}>
                    {crumb.label}
                  </BreadcrumbLink>
                  <BreadcrumbSeparator>/</BreadcrumbSeparator>
                </>
              ) : (
                <BreadcrumbCurrent>{crumb.label}</BreadcrumbCurrent>
              )}
            </BreadcrumbItem>
          ))}
        </BreadcrumbPath>
        {breadcrumbContext.pageActions && (
          <PageActions>{breadcrumbContext.pageActions}</PageActions>
        )}
      </BreadcrumbContainer>
    );
  }

  const segments = pathname.split('/').filter(Boolean);

  // Check for custom breadcrumb patterns
  for (const [pattern, generator] of Object.entries(customBreadcrumbPatterns)) {
    const patternSegments = pattern.split('/').filter(Boolean);

    if (segments.length === patternSegments.length) {
      const matches = patternSegments.every((seg, i) =>
        seg.startsWith('[') || seg === segments[i]
      );

      if (matches) {
        const breadcrumbs = generator(segments);
        return (
          <BreadcrumbContainer>
            <BreadcrumbPath>
              {breadcrumbs.map((crumb, index) => (
                <BreadcrumbItem key={crumb.path}>
                  {index < breadcrumbs.length - 1 ? (
                    <>
                      <BreadcrumbLink href={crumb.path}>
                        {crumb.label}
                      </BreadcrumbLink>
                      <BreadcrumbSeparator>/</BreadcrumbSeparator>
                    </>
                  ) : (
                    <BreadcrumbCurrent>{crumb.label}</BreadcrumbCurrent>
                  )}
                </BreadcrumbItem>
              ))}
            </BreadcrumbPath>
            {breadcrumbContext?.pageActions && (
              <PageActions>{breadcrumbContext.pageActions}</PageActions>
            )}
          </BreadcrumbContainer>
        );
      }
    }
  }

  // Default breadcrumb generation
  const breadcrumbs: Array<{ label: string; path: string }> = [
    { label: 'Dashboard', path: '/dashboard' },
  ];

  let currentPath = '';
  segments.forEach((segment, index) => {
    currentPath += `/${segment}`;

    // Check if segment is a UUID (skip or shorten)
    const isUUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(segment);

    const label = isUUID
      ? `#${segment.slice(0, 8)}`
      : routeLabels[segment] || segment.charAt(0).toUpperCase() + segment.slice(1);

    breadcrumbs.push({
      label,
      path: currentPath,
    });
  });

  return (
    <BreadcrumbContainer>
      <BreadcrumbPath>
        {breadcrumbs.map((crumb, index) => (
          <BreadcrumbItem key={crumb.path}>
            {index < breadcrumbs.length - 1 ? (
              <>
                <BreadcrumbLink href={crumb.path}>
                  {crumb.label}
                </BreadcrumbLink>
                <BreadcrumbSeparator>/</BreadcrumbSeparator>
              </>
            ) : (
              <BreadcrumbCurrent>{crumb.label}</BreadcrumbCurrent>
            )}
          </BreadcrumbItem>
        ))}
      </BreadcrumbPath>
      {breadcrumbContext?.pageActions && (
        <PageActions>{breadcrumbContext.pageActions}</PageActions>
      )}
    </BreadcrumbContainer>
  );
}
