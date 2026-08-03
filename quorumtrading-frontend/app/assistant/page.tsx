'use client';

import { Suspense, useCallback, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import styled from 'styled-components';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { TerminalChat, type QuickAction } from '../components/assistant/TerminalChat';
import type { TerminalChatHandle } from '../components/assistant/TerminalChat';
import { ChatHistorySidebar } from '../components/assistant/ChatHistorySidebar';
import { useChatHistory } from '@/app/hooks/useChatHistory';
import { useStrategies, useMarketResearch, useSimulations, useInstanceInfo } from '@/app/lib/api/hooks';
import type { StrategyResponse, SimulationResponse, MarketResearchResponse } from '@/app/lib/api/client';

function buildQuickActions(
  strategies: StrategyResponse[] | undefined,
  simulations: SimulationResponse[] | undefined,
  marketResearch: MarketResearchResponse[] | undefined,
): QuickAction[] {
  const actions: QuickAction[] = [];
  const activeSimulations = simulations?.filter(s => s.status === 'SIMULATING' || s.status === 'LIVE') || [];
  const completedSimulations = simulations?.filter(s => s.status === 'COMPLETED') || [];
  const hasStrategies = strategies && strategies.length > 0;
  const hasSimulations = simulations && simulations.length > 0;
  const hasResearch = marketResearch && marketResearch.length > 0;

  if (activeSimulations.length > 0) {
    actions.push({ label: `${activeSimulations.length} Active Sim${activeSimulations.length > 1 ? 's' : ''}`, message: 'Give me a status overview of all active simulations.', icon: '>' });
  }
  if (hasStrategies && completedSimulations.length > 0) {
    actions.push({ label: 'Compare Strategies', message: 'Compare the performance of all my strategies.', icon: '#' });
  }
  if (!hasResearch && hasStrategies) {
    actions.push({ label: 'New Research', message: 'Help me set up a new market research.', icon: '~' });
  }
  actions.push({ label: 'Check Indicators', message: 'Check technical indicators for BTC — RSI, MACD, SMA-50/200, Bollinger Bands.', icon: '^' });
  if (!hasStrategies && !hasSimulations) {
    actions.unshift({ label: 'Get Started', message: "I'm new here. Walk me through creating my first strategy and backtest.", icon: '>' });
  }
  return actions.slice(0, 4);
}

export default function AssistantPage() {
  return (
    <Suspense>
      <AssistantPageContent />
    </Suspense>
  );
}

function AssistantPageContent() {
  const searchParams = useSearchParams();
  const chatRef = useRef<TerminalChatHandle>(null);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const { sessions, isLoading: historyLoading, refreshSessions } = useChatHistory();
  const { data: strategies } = useStrategies();
  const { data: marketResearch } = useMarketResearch();
  const { data: simulations } = useSimulations();
  const { data: instance } = useInstanceInfo();

  const handleSessionChange = useCallback((sessionId: string | null) => {
    setActiveSessionId(sessionId);
    refreshSessions();
  }, [refreshSessions]);

  const initialMessage = searchParams.get('message');

  // The assistant needs a hosted conversational model. The self-host edition runs on whichever
  // engine the user selected — typically a local one that does not serve that path — so the chat
  // would fail on every message. The sidebar hides the entry; this covers a direct visit or a
  // bookmarked link, and points at the surfaces that do work here.
  if (instance?.edition === 'standalone') {
    return (
      <DashboardLayout>
        <Page>
          <PageHeader>
            <HeaderLeft>
              <H1>AI Assistant</H1>
              <Desc>Not available in the self-hosted edition.</Desc>
            </HeaderLeft>
          </PageHeader>
          <Desc>
            The assistant chat requires a hosted conversational model and is not part of the
            self-hosted edition. Everything it does is available through the MCP server — point
            Claude Code (or any MCP client) at this instance and ask it directly. The AI trading
            consensus itself is unaffected and runs on the engine you selected.
          </Desc>
        </Page>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <Page>
        <PageHeader>
          <HeaderLeft>
            <H1>AI Assistant</H1>
            <Desc>
              Council-aware chat. Reads your strategies, simulations, and recent decisions so you don&apos;t have
              to paste context every time.
            </Desc>
          </HeaderLeft>
        </PageHeader>

        <ChatContainer>
          <ChatHistorySidebar
            sessions={sessions}
            activeSessionId={activeSessionId}
            isLoading={historyLoading}
            mobileOpen={historyOpen}
            hidden={false}
            onMobileClose={() => setHistoryOpen(false)}
            onSelectSession={(id) => chatRef.current?.switchSession(id)}
            onNewChat={() => chatRef.current?.resetSession()}
            onDeleteSession={(id) => chatRef.current?.deleteSession(id)}
          />
          <TerminalChat
            ref={chatRef}
            quickActions={buildQuickActions(strategies, simulations, marketResearch)}
            initialMessage={initialMessage}
            onSessionChange={handleSessionChange}
            onHistoryToggle={() => setHistoryOpen(true)}
          />
        </ChatContainer>
      </Page>
    </DashboardLayout>
  );
}

// ─── Styled ──────────────────────────────────────────────────────────────

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 0 4px;
  min-width: 0;
  min-height: 0;
  flex: 1;
`;

const PageHeader = styled.div`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  flex-wrap: wrap;
  padding: 4px 0 0;
`;

const HeaderLeft = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 22px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const Desc = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
  max-width: 78ch;
`;

const ChatContainer = styled.div`
  display: flex;
  height: calc(100dvh - 200px);
  min-height: 480px;
  gap: 12px;
`;
