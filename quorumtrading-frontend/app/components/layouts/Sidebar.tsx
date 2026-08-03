'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { usePathname, useSearchParams } from 'next/navigation';
import styled from 'styled-components';
import { textGlow } from '@/app/styles/theme/effects';
import { useAuth } from '@/app/hooks/useAuth';
import { featureFlags } from '@/app/lib/featureFlags';
import { useFeatureEnabled, useInstanceInfo } from '@/app/lib/api/hooks';


const SidebarContainer = styled.nav`
  display: flex;
  flex-direction: column;
  height: 100%;
`;

const SidebarFooter = styled.div`
  padding: ${p => p.theme.spacing.md} ${p => p.theme.spacing.lg};
  border-top: 1px solid ${p => p.theme.colors.border};
`;

const ConnectionStatus = styled.div<{ $connected: boolean }>`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.$connected ? p.theme.colors.success : p.theme.colors.error};
  opacity: 0.7;

  &::before {
    content: '';
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: ${p => p.$connected ? p.theme.colors.success : p.theme.colors.error};
    box-shadow: 0 0 6px ${p => p.$connected ? p.theme.colors.success : p.theme.colors.error};
  }
`;

const Nav = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: ${p => p.theme.spacing.md} 0;

  /* Custom scrollbar */
  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.border};
    border-radius: 2px;

    &:hover {
      background: ${p => p.theme.colors.terminalGreen}40;
    }
  }
`;

const NavSection = styled.div`
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const NavDivider = styled.div`
  height: 1px;
  background: ${p => p.theme.colors.border};
  margin: ${p => p.theme.spacing.md} ${p => p.theme.spacing.lg};
`;

const NavItem = styled(Link)<{ $active: boolean }>`
  display: flex;
  align-items: center;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.lg};
  color: ${p => p.$active ? p.theme.colors.terminalGreen : p.theme.colors.textMuted};
  text-decoration: none;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.$active ? p.theme.typography.fontWeight.semibold : p.theme.typography.fontWeight.normal};
  background: ${p => p.$active ? `${p.theme.colors.terminalGreen}08` : 'transparent'};
  transition: all 0.15s ease;

  &:hover {
    background: ${p => p.theme.colors.hover};
    color: ${p => p.theme.colors.terminalGreen};
  }

  ${p => p.$active && textGlow}
`;

const NavItemLabel = styled.span`
  text-transform: uppercase;
  letter-spacing: 0.08em;
`;

const SubMenu = styled.div<{ $isOpen: boolean }>`
  max-height: ${p => p.$isOpen ? '500px' : '0'};
  overflow: hidden;
  transition: max-height 0.3s ease;
`;

const SubMenuItem = styled(Link)<{ $active: boolean }>`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.lg};
  padding-left: calc(${p => p.theme.spacing.lg} + ${p => p.theme.spacing.lg});
  color: ${p => p.$active ? p.theme.colors.terminalBlue : p.theme.colors.textMuted};
  text-decoration: none;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  font-weight: ${p => p.$active ? p.theme.typography.fontWeight.semibold : p.theme.typography.fontWeight.normal};
  transition: all 0.15s ease;

  &::before {
    content: '|_';
    color: ${p => p.theme.colors.border};
    font-size: ${p => p.theme.typography.fontSize.xs};
  }

  &:hover {
    color: ${p => p.theme.colors.terminalBlue};
    padding-left: calc(${p => p.theme.spacing.lg} + ${p => p.theme.spacing.lg} + 4px);
  }

  ${p => p.$active && `
    text-shadow: 0 0 8px ${p.theme.colors.terminalBlue}88;
  `}
`;

const ExpandableNavItem = styled.button<{ $active: boolean; $expanded: boolean }>`
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.lg};
  color: ${p => p.$active ? p.theme.colors.terminalGreen : p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  font-weight: ${p => p.$active ? p.theme.typography.fontWeight.semibold : p.theme.typography.fontWeight.normal};
  border: none;
  background: ${p => p.$active ? `${p.theme.colors.terminalGreen}08` : 'transparent'};
  text-align: left;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    background: ${p => p.theme.colors.hover};
    color: ${p => p.theme.colors.terminalGreen};
  }

  ${p => p.$active && textGlow}
`;

const ExpandableNavItemContent = styled.div`
  display: flex;
  align-items: center;
`;

const ExpandIcon = styled.span<{ $expanded: boolean }>`
  font-size: 10px;
  color: ${p => p.theme.colors.textMuted};
  transition: transform 0.2s ease, color 0.15s ease;
  transform: rotate(${p => p.$expanded ? '90deg' : '0deg'});
  cursor: pointer;
  padding: 4px;

  &:hover {
    color: ${p => p.theme.colors.terminalGreen};
  }
`;


interface NavRoute {
  path: string;
  label: string;
  subRoutes?: Array<{
    path: string;
    label: string;
  }>;
}

const navRoutes: NavRoute[] = [
  { path: '/dashboard', label: 'Dashboard', subRoutes: [
    { path: '/dashboard', label: 'Overview' },
    { path: '/assistant', label: 'AI Assistant' },
  ]},
  { path: '/research', label: 'Research' },
  { path: '/strategies', label: 'Strategies', subRoutes: [
    { path: '/strategies', label: 'Strategies' },
    { path: '/strategies/public', label: 'Library' },
  ]},
  {
    path: '/simulations',
    label: 'Simulations',
    subRoutes: [
      { path: '/simulations?type=live', label: 'Live' },
      { path: '/simulations?type=backtest', label: 'Backtest' },
    ],
  },
  ...(featureFlags.investmentPlans ? [{
    path: '/investment-plans',
    label: 'Investment Plans',
    subRoutes: [
      { path: '/investment-plans', label: 'Plans' },
      { path: '/investment-plans/new', label: 'New' },
    ],
  }] : []),
  {
    path: '/benchmarks',
    label: 'Benchmarks',
    subRoutes: [
      { path: '/benchmarks?type=live', label: 'Live' },
      { path: '/benchmarks?type=backtest', label: 'Backtest' },
      { path: '/benchmarks/new', label: 'New' },
    ],
  },
  {
    path: '/challenges',
    label: 'Challenges',
    subRoutes: [
      { path: '/challenges?status=ACTIVE', label: 'Active' },
      { path: '/challenges?status=COMPLETED', label: 'Completed' },
    ],
  },
  {
    path: '/settings',
    label: 'Settings',
    subRoutes: [
      { path: '/settings/broker-accounts', label: 'Broker Accounts' },
      { path: '/settings/api-tokens', label: 'API Tokens' },
      { path: '/settings/config', label: 'AI Config' },
      { path: '/settings/usage', label: 'AI Usage' },
      { path: '/settings/budget', label: 'Budget' },
      { path: '/settings/account', label: 'Account' },
    ],
  },
];

interface SidebarProps {
  isConnected?: boolean;
}

const NavSectionLabel = styled.div`
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.lg};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10px;
  color: ${p => p.theme.colors.terminalBlue};
  text-transform: uppercase;
  letter-spacing: 0.12em;
  opacity: 0.6;
`;

export function Sidebar({ isConnected = false }: SidebarProps) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const { data: instance } = useInstanceInfo();
  const [expandedRoutes, setExpandedRoutes] = useState<string[]>([]);

  // Hosted-only features. The assistant needs a hosted conversational model that the self-hosted
  // edition does not have, so its entry is hidden rather than left as a link that always fails;
  // the route guards itself for direct visits. Market research used to be here too — it is now
  // governed by the `market-research` feature flag below instead.
  const hostedOnlyPaths = ['/assistant'];
  const editionRoutes = instance?.edition === 'standalone'
    ? navRoutes
        .map(route => route.subRoutes
          ? { ...route, subRoutes: route.subRoutes.filter(sub => !hostedOnlyPaths.includes(sub.path)) }
          : route)
        .filter(route => !hostedOnlyPaths.includes(route.path))
    : navRoutes;

  // Runtime feature flags. Unlike the edition check above, these can be flipped by an
  // administrator without a redeploy and can be targeted at individual people, so the answer comes
  // from the API rather than from the build. Hiding the entry is a courtesy only — the backend
  // refuses a disabled feature on its own, and typing the URL directly is expected to still reach
  // the page (which will show whatever the API tells it).
  const challengesEnabled = useFeatureEnabled('challenges');
  const marketResearchEnabled = useFeatureEnabled('market-research');
  const disabledFeaturePaths = [
    ...(challengesEnabled ? [] : ['/challenges']),
    ...(marketResearchEnabled ? [] : ['/research']),
  ];
  const routes = disabledFeaturePaths.length === 0
    ? editionRoutes
    : editionRoutes.filter(route => !disabledFeaturePaths.includes(route.path));

  // Auto-expand sections when navigating to their pages
  useEffect(() => {
    // Find routes that have subRoutes and check if current pathname matches
    const routesToExpand = routes
      .filter(route => route.subRoutes && pathname.startsWith(route.path))
      .map(route => route.path);

    // Add routes to expanded list if not already there
    if (routesToExpand.length > 0) {
      setExpandedRoutes(prev => {
        const newRoutes = routesToExpand.filter(path => !prev.includes(path));
        if (newRoutes.length > 0) {
          return [...prev, ...newRoutes];
        }
        return prev;
      });
    }
  }, [pathname]);

  const handleExpandableClick = (path: string) => {
    // Toggle expand/collapse — don't navigate, let user pick a submenu item
    setExpandedRoutes(prev =>
      prev.includes(path)
        ? prev.filter(p => p !== path)
        : [...prev, path]
    );
  };

  const toggleExpanded = (path: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setExpandedRoutes(prev =>
      prev.includes(path)
        ? prev.filter(p => p !== path)
        : [...prev, path]
    );
  };

  const isActive = (path: string) => {
    if (path === '/') return pathname === '/';
    return pathname.startsWith(path);
  };

  const isSubRouteActive = (parentPath: string, subRoutes?: Array<{ path: string; label: string }>) => {
    if (!subRoutes) return false;
    return subRoutes.some(sub => {
      const subPath = sub.path.split('?')[0];
      return pathname.startsWith(subPath);
    });
  };

  return (
    <SidebarContainer>
      <Nav>
        {routes.map((route) => (
          <NavSection key={route.path}>
            {route.subRoutes ? (
              <>
                <ExpandableNavItem
                  $active={isActive(route.path) || isSubRouteActive(route.path, route.subRoutes)}
                  $expanded={expandedRoutes.includes(route.path)}
                  onClick={() => handleExpandableClick(route.path)}
                >
                  <ExpandableNavItemContent>
                    <NavItemLabel>{route.label}</NavItemLabel>
                  </ExpandableNavItemContent>
                  <ExpandIcon
                    $expanded={expandedRoutes.includes(route.path)}
                    onClick={(e) => toggleExpanded(route.path, e)}
                  >
                    &gt;
                  </ExpandIcon>
                </ExpandableNavItem>
                <SubMenu $isOpen={expandedRoutes.includes(route.path)}>
                  {route.subRoutes.map((subRoute) => {
                    const subPath = subRoute.path.split('?')[0];
                    const subQueryString = subRoute.path.includes('?') ? subRoute.path.split('?')[1] : null;

                    // For routes with query params (like /benchmarks?type=live), check exact path + query
                    // For routes without query params (like /benchmarks/new), check exact path match
                    let isSubActive = false;
                    if (subQueryString) {
                      // Has query param - parse it and check if current URL matches
                      const queryParams = new URLSearchParams(subQueryString);
                      const queryKey = queryParams.keys().next().value;
                      const queryValue = queryKey ? queryParams.get(queryKey) : null;

                      isSubActive = pathname === subPath &&
                        queryKey !== undefined &&
                        searchParams.get(queryKey) === queryValue;
                    } else {
                      // No query param - exact match only
                      isSubActive = pathname === subPath;
                    }

                    return (
                      <SubMenuItem
                        key={subRoute.path}
                        href={subRoute.path}
                        $active={isSubActive}
                      >
                        {subRoute.label}
                      </SubMenuItem>
                    );
                  })}
                </SubMenu>
              </>
            ) : (
              <NavItem
                href={route.path}
                $active={isActive(route.path)}
              >
                <NavItemLabel>{route.label}</NavItemLabel>
              </NavItem>
            )}
          </NavSection>
        ))}

        {isAdmin && (
          <>
            <NavDivider />
            <NavSectionLabel>Admin</NavSectionLabel>
            <NavSection>
              <NavItem href="/admin/dashboard" $active={isActive('/admin/dashboard')}>
                <NavItemLabel>Dashboard</NavItemLabel>
              </NavItem>
            </NavSection>
            <NavSection>
              <NavItem href="/admin/data-availability" $active={isActive('/admin/data-availability')}>
                <NavItemLabel>Data Availability</NavItemLabel>
              </NavItem>
            </NavSection>
            <NavSection>
              <NavItem href="/admin/feature-flags" $active={isActive('/admin/feature-flags')}>
                <NavItemLabel>Feature Flags</NavItemLabel>
              </NavItem>
            </NavSection>
            {challengesEnabled && (
              <NavSection>
                <NavItem href="/admin/challenges/new" $active={isActive('/admin/challenges/new')}>
                  <NavItemLabel>New Challenge</NavItemLabel>
                </NavItem>
              </NavSection>
            )}
          </>
        )}

        <NavDivider />
        <NavSection>
          <NavItem href="https://www.reddit.com/r/quorumtrading/" target="_blank" rel="noopener noreferrer" $active={false}>
            <NavItemLabel>Community</NavItemLabel>
          </NavItem>
          <NavItem href="/blog" $active={isActive('/blog')}>
            <NavItemLabel>Changelog</NavItemLabel>
          </NavItem>
          <NavItem href="/faq" $active={isActive('/faq')}>
            <NavItemLabel>FAQ</NavItemLabel>
          </NavItem>
        </NavSection>
      </Nav>
      <SidebarFooter>
        <ConnectionStatus $connected={isConnected}>
          {isConnected ? 'Connected' : 'Disconnected'}
        </ConnectionStatus>
      </SidebarFooter>
    </SidebarContainer>
  );
}
