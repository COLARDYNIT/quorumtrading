'use client';

import styled from 'styled-components';
import { AppHeader } from './AppHeader';

const LayoutContainer = styled.div`
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: ${p => p.theme.colors.background};
`;

const Main = styled.main`
  flex: 1;
  width: 100%;
`;

const Footer = styled.footer`
  border-top: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.surface};
  padding: ${p => p.theme.spacing.xl} 0;
`;

const FooterContent = styled.div`
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 ${p => p.theme.spacing.xl};
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: ${p => p.theme.spacing.xl};

  @media (max-width: 768px) {
    padding: 0 ${p => p.theme.spacing.md};
    grid-template-columns: 1fr;
    gap: ${p => p.theme.spacing.lg};
  }
`;

const FooterSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const FooterTitle = styled.h3`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.terminalBlue};
  margin-bottom: ${p => p.theme.spacing.xs};
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.typography.letterSpacing.wide};
`;

const FooterLink = styled.a<{ $placeholder?: boolean }>`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.$placeholder ? p.theme.colors.textMuted + '50' : p.theme.colors.textMuted};
  text-decoration: none;
  transition: ${p => p.theme.transition.fast};
  cursor: ${p => p.$placeholder ? 'not-allowed' : 'pointer'};

  &:hover {
    color: ${p => p.$placeholder ? p.theme.colors.textMuted + '50' : p.theme.colors.terminalGreen};
    text-shadow: ${p => p.$placeholder ? 'none' : `0 0 8px ${p.theme.colors.terminalGreen}88`};
  }
`;

const Copyright = styled.div`
  max-width: 1440px;
  margin: 0 auto;
  padding: ${p => p.theme.spacing.lg} ${p => p.theme.spacing.xl} 0;
  text-align: center;
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  border-top: 1px solid ${p => p.theme.colors.border};
  margin-top: ${p => p.theme.spacing.xl};

  @media (max-width: 768px) {
    padding: ${p => p.theme.spacing.lg} ${p => p.theme.spacing.md} 0;
  }
`;

interface LandingLayoutProps {
  children: React.ReactNode;
}

export function LandingLayout({ children }: LandingLayoutProps) {
  return (
    <LayoutContainer>
      <AppHeader />

      <Main>{children}</Main>

      <Footer>
        <FooterContent>
          <FooterSection>
            <FooterTitle>Product</FooterTitle>
            <FooterLink href="/faq/ai-agents">AI Agents</FooterLink>
            <FooterLink href="/faq/backtesting">Backtesting &amp; Simulations</FooterLink>
            <FooterLink href="/faq/market-research">Market Research</FooterLink>
            <FooterLink href="/faq/live-trading">Live Trading</FooterLink>
          </FooterSection>

          <FooterSection>
            <FooterTitle>Resources</FooterTitle>
            <FooterLink href="/faq">FAQ</FooterLink>
            <FooterLink href="/faq/mcp-integration">MCP Integration</FooterLink>
            <FooterLink href="/blog">Changelog</FooterLink>
            <FooterLink href="https://www.reddit.com/r/quorumtrading/" target="_blank" rel="noopener noreferrer">Community</FooterLink>
          </FooterSection>

          <FooterSection>
            <FooterTitle>Legal</FooterTitle>
            <FooterLink href="/terms">Terms of Service</FooterLink>
            <FooterLink href="/privacy">Privacy Policy</FooterLink>
          </FooterSection>

          <FooterSection>
            <FooterTitle>Company</FooterTitle>
            <FooterLink href="https://colardyn-it.be/" target="_blank" rel="noopener noreferrer">Colardyn IT CommV</FooterLink>
            <FooterLink as="span">BTW BE 0892.696.641</FooterLink>
            <FooterLink as="span">Frederik de Merodestraat 50, 2800 Mechelen</FooterLink>
            <FooterLink href="mailto:frederikcolardyn@colardyn-it.be">frederikcolardyn@colardyn-it.be</FooterLink>
          </FooterSection>

        </FooterContent>

        <Copyright>
          QuorumTrading is experimental. AI decisions do not constitute financial advice. Trade at your own risk.
          <br />
          &copy; {new Date().getFullYear()} QuorumTrading. All rights reserved.
        </Copyright>
      </Footer>
    </LayoutContainer>
  );
}
