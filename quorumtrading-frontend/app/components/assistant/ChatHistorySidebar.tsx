'use client';

import { useCallback } from 'react';
import styled from 'styled-components';
import type { ChatSessionSummary } from '@/app/lib/api/chat';
import { fadeInUp } from '@/app/styles/animations';

// ===== Styled Components =====

const SidebarContainer = styled.div<{ $hidden: boolean }>`
  display: flex;
  flex-direction: column;
  width: ${p => p.$hidden ? '0' : '240px'};
  min-width: ${p => p.$hidden ? '0' : '240px'};
  opacity: ${p => p.$hidden ? '0' : '1'};
  overflow: hidden;
  background: ${p => p.theme.colors.surface};
  border: ${p => p.$hidden ? 'none' : `1px solid ${p.theme.colors.border}`};
  border-radius: 12px;
  animation: ${fadeInUp} 0.3s ease-out both;
  transition: width 0.3s ease, min-width 0.3s ease, opacity 0.2s ease;

  @media (max-width: 768px) {
    display: none;
  }
`;

const SidebarHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.background};
  flex-shrink: 0;
`;

const SidebarTitle = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.textDimmed};
  letter-spacing: 0.5px;
  text-transform: uppercase;
`;

const NewChatButton = styled.button`
  background: none;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;

  &:hover {
    border-color: ${p => p.theme.colors.terminalGreen}50;
    color: ${p => p.theme.colors.terminalGreen};
  }
`;

const SessionList = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;

  scrollbar-width: thin;
  scrollbar-color: ${p => p.theme.colors.border} transparent;

  &::-webkit-scrollbar {
    width: 3px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.border};
    border-radius: 2px;
  }
`;

const SessionItem = styled.div<{ $isActive: boolean }>`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  cursor: pointer;
  transition: all 0.1s ease;
  border-left: 2px solid ${p => p.$isActive ? p.theme.colors.terminalGreen : 'transparent'};
  background: ${p => p.$isActive ? `${p.theme.colors.terminalGreen}08` : 'transparent'};

  &:hover {
    background: ${p => p.theme.colors.background};
  }
`;

const SessionContent = styled.div`
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
`;

const SessionTitle = styled.span<{ $isActive: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.$isActive ? p.theme.colors.terminalGreen : p.theme.colors.textLight};
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
`;

const SessionDate = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  color: ${p => p.theme.colors.textDimmed};
  opacity: 0.6;
`;

const DeleteButton = styled.button`
  background: none;
  border: none;
  color: ${p => p.theme.colors.textDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  padding: 2px 4px;
  cursor: pointer;
  opacity: 0;
  transition: all 0.1s ease;
  flex-shrink: 0;

  ${SessionItem}:hover & {
    opacity: 0.5;
  }

  &:hover {
    opacity: 1 !important;
    color: ${p => p.theme.colors.error};
  }
`;

const EmptyState = styled.div`
  padding: 20px 14px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.textDimmed};
  text-align: center;
  opacity: 0.5;
`;

// ===== Mobile drawer =====

const MobileDrawerOverlay = styled.div<{ $open: boolean }>`
  display: none;

  @media (max-width: 768px) {
    display: ${p => p.$open ? 'block' : 'none'};
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 1300;
  }
`;

const MobileDrawer = styled.div<{ $open: boolean }>`
  display: none;

  @media (max-width: 768px) {
    display: flex;
    flex-direction: column;
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    width: 280px;
    max-width: 80vw;
    background: ${p => p.theme.colors.surface};
    border-right: 1px solid ${p => p.theme.colors.border};
    z-index: 1301;
    transform: translateX(${p => p.$open ? '0' : '-100%'});
    transition: transform 0.2s ease;
  }
`;

// ===== Helper =====

function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const days = Math.floor(hours / 24);

  if (hours < 1) return 'just now';
  if (hours < 24) return `${hours}h ago`;
  if (days === 1) return 'yesterday';
  if (days < 7) return `${days}d ago`;
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

// ===== Component =====

interface ChatHistorySidebarProps {
  sessions: ChatSessionSummary[];
  activeSessionId: string | null;
  isLoading: boolean;
  mobileOpen: boolean;
  hidden?: boolean;
  onMobileClose: () => void;
  onSelectSession: (sessionId: string) => void;
  onNewChat: () => void;
  onDeleteSession: (sessionId: string) => void;
}

export function ChatHistorySidebar({
  sessions,
  activeSessionId,
  isLoading,
  mobileOpen,
  hidden = false,
  onMobileClose,
  onSelectSession,
  onNewChat,
  onDeleteSession,
}: ChatHistorySidebarProps) {
  const handleSelect = useCallback((sessionId: string) => {
    onSelectSession(sessionId);
    onMobileClose();
  }, [onSelectSession, onMobileClose]);

  const handleDelete = (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation();
    onDeleteSession(sessionId);
  };

  const sessionListContent = (
    <>
      <SidebarHeader>
        <SidebarTitle>History</SidebarTitle>
        <NewChatButton onClick={onNewChat}>+ new</NewChatButton>
      </SidebarHeader>
      <SessionList>
        {isLoading ? (
          <EmptyState>loading...</EmptyState>
        ) : sessions.length === 0 ? (
          <EmptyState>no conversations yet</EmptyState>
        ) : (
          sessions.map(session => (
            <SessionItem
              key={session.sessionId}
              $isActive={session.sessionId === activeSessionId}
              onClick={() => handleSelect(session.sessionId)}
            >
              <SessionContent>
                <SessionTitle $isActive={session.sessionId === activeSessionId}>
                  {session.title || 'New conversation'}
                </SessionTitle>
                <SessionDate>{formatDate(session.lastActivityAt)}</SessionDate>
              </SessionContent>
              <DeleteButton
                onClick={(e) => handleDelete(e, session.sessionId)}
                title="Delete conversation"
              >
                x
              </DeleteButton>
            </SessionItem>
          ))
        )}
      </SessionList>
    </>
  );

  return (
    <>
      {/* Desktop sidebar */}
      <SidebarContainer $hidden={hidden}>
        {sessionListContent}
      </SidebarContainer>

      {/* Mobile drawer */}
      <MobileDrawerOverlay $open={mobileOpen} onClick={onMobileClose} />
      <MobileDrawer $open={mobileOpen}>
        {sessionListContent}
      </MobileDrawer>
    </>
  );
}
