'use client';

import { Suspense, useState, useCallback, useEffect } from 'react';
import { usePathname } from 'next/navigation';
import styled from 'styled-components';
import { useWebSocket } from '@/app/lib/websocket';
import { Sidebar } from './Sidebar';
import { Breadcrumb } from './Breadcrumb';
import { BreadcrumbProvider } from '@/app/contexts/BreadcrumbContext';
import { AppHeader } from './AppHeader';

const HEADER_HEIGHT = '57px';

const LayoutContainer = styled.div`
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: ${p => p.theme.colors.background};
`;

const ContentWrapper = styled.div`
  display: flex;
  flex: 1;
  min-width: 0;
  overflow: hidden;
`;

const SidebarWrapper = styled.div`
  width: 220px;
  flex-shrink: 0;
  border-right: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.surface};

  @media (max-width: 968px) {
    display: none;
  }
`;

const MobileOverlay = styled.div<{ $isOpen: boolean }>`
  display: none;
  position: fixed;
  inset: 0;
  z-index: 1200;
  visibility: ${p => p.$isOpen ? 'visible' : 'hidden'};

  @media (max-width: 968px) {
    display: block;
  }
`;

const MobileBackdrop = styled.div<{ $isOpen: boolean }>`
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  opacity: ${p => p.$isOpen ? 1 : 0};
  transition: opacity 0.25s ease;
`;

const MobileDrawer = styled.div<{ $isOpen: boolean }>`
  position: absolute;
  top: 0;
  left: 0;
  bottom: 0;
  width: 260px;
  background: ${p => p.theme.colors.surface};
  border-right: 1px solid ${p => p.theme.colors.border};
  transform: translateX(${p => p.$isOpen ? '0' : '-100%'});
  transition: transform 0.25s ease;
  overflow-y: auto;
`;

const MainContainer = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
`;

const Main = styled.main`
  flex: 1;
  padding: ${p => p.theme.spacing.xl};

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.md};
  }
`;

const ContentArea = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

interface DashboardLayoutProps {
  children: React.ReactNode;
}

export function DashboardLayout({ children }: DashboardLayoutProps) {
  const { isConnected } = useWebSocket();
  const pathname = usePathname();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  // Close mobile menu on navigation
  useEffect(() => {
    setIsMobileMenuOpen(false);
  }, [pathname]);

  const handleMenuToggle = useCallback(() => {
    setIsMobileMenuOpen(prev => !prev);
  }, []);

  const handleCloseMenu = useCallback(() => {
    setIsMobileMenuOpen(false);
  }, []);

  return (
    <BreadcrumbProvider>
      <LayoutContainer>
        <AppHeader onMenuToggle={handleMenuToggle} />
        <ContentWrapper>
          {/* Two sidebars are always mounted by design: this one is hidden below 968px and
              the mobile drawer above it. Both are in the DOM, so e2e selectors must scope to a
              region rather than the page — these test ids are that contract. */}
          <SidebarWrapper data-testid="sidebar-desktop">
            <Suspense fallback={null}>
              <Sidebar isConnected={isConnected} />
            </Suspense>
          </SidebarWrapper>
          <MainContainer>
            <Main>
              <ContentArea data-testid="main-content">
                <Breadcrumb />
                {children}
              </ContentArea>
            </Main>
          </MainContainer>
        </ContentWrapper>

        <MobileOverlay $isOpen={isMobileMenuOpen}>
          <MobileBackdrop $isOpen={isMobileMenuOpen} onClick={handleCloseMenu} />
          <MobileDrawer $isOpen={isMobileMenuOpen} data-testid="sidebar-mobile">
            <Suspense fallback={null}>
              <Sidebar isConnected={isConnected} />
            </Suspense>
          </MobileDrawer>
        </MobileOverlay>
      </LayoutContainer>
    </BreadcrumbProvider>
  );
}
