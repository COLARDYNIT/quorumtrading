'use client';

import { useState, useRef, useEffect, useCallback, forwardRef, useImperativeHandle } from 'react';
import styled from 'styled-components';
import { useChat } from '@/app/hooks/useChat';
import { ChatMessageBubble } from './ChatMessageBubble';
import { ToolCallIndicator } from './ToolCallIndicator';
import { ModelSelector } from './ModelSelector';
import { fadeInUp, pulse } from '@/app/styles/animations';
import { boxGlow } from '@/app/styles/theme/effects';
import { useBrailleSpinner } from '@/app/lib/hooks';
import { TrafficLights } from '@/app/components/shared';
import type { UserAction } from '@/app/lib/a2ui/types';

// ===== Styled Components =====

const TerminalWindow = styled.div`
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 300px;
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 12px;
  overflow: hidden;
  animation: ${fadeInUp} 0.5s ease-out both;
  ${boxGlow}
`;

const TitleBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: ${p => p.theme.colors.background};
  border-bottom: 1px solid ${p => p.theme.colors.border};
  flex-shrink: 0;
  gap: 12px;
`;

const HistoryToggle = styled.button`
  display: none;
  background: none;
  border: 1px solid ${p => p.theme.colors.border};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;

  &:hover {
    border-color: ${p => p.theme.colors.terminalBlue}50;
    color: ${p => p.theme.colors.terminalBlue};
  }

  @media (max-width: 768px) {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
`;

const TitleBarLeft = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  min-width: 0;

  @media (max-width: 480px) {
    display: none;
  }
`;

const TitleText = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.textDimmed};
  letter-spacing: 0.5px;
  white-space: nowrap;
`;

const TitleBarRight = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  min-width: 0;
  flex: 1;
  justify-content: flex-end;
`;

const MessageArea = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: 12px 0;
  display: flex;
  flex-direction: column;

  /* Custom scrollbar — thin, like the hero terminal */
  scrollbar-width: thin;
  scrollbar-color: ${p => p.theme.colors.border} transparent;

  &::-webkit-scrollbar {
    width: 4px;
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.border};
    border-radius: 2px;
  }
`;

const LoadingIndicator = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 20px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  line-height: 1.7;
  color: ${p => p.theme.colors.textMuted};
`;

const Spinner = styled.span`
  color: ${p => p.theme.colors.terminalGreen};
  font-size: ${p => p.theme.typography.fontSize.sm};
`;

const LoadingBar = styled.div`
  height: 2px;
  margin: 0 20px;
  background: ${p => p.theme.colors.border};
  overflow: hidden;
  border-radius: 1px;

  &::after {
    content: '';
    display: block;
    height: 100%;
    width: 30%;
    background: ${p => p.theme.colors.primary};
    animation: loadingSweep 1.5s ease-in-out infinite;
  }

  @keyframes loadingSweep {
    0% { transform: translateX(-100%); }
    50% { transform: translateX(250%); }
    100% { transform: translateX(-100%); }
  }
`;

const LOADING_MESSAGES = [
  'analyzing your request',
  'consulting the agents',
  'reviewing market data',
  'processing indicators',
  'evaluating strategy context',
  'preparing response',
];

function useLoadingMessage(isLoading: boolean): string {
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (!isLoading) { setIndex(0); return; }
    const interval = setInterval(() => {
      setIndex(prev => (prev + 1) % LOADING_MESSAGES.length);
    }, 2500);
    return () => clearInterval(interval);
  }, [isLoading]);
  return LOADING_MESSAGES[index];
}

const EmptyState = styled.div`
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  opacity: 0.5;
  animation: ${pulse} 2s ease-in-out infinite;
`;

const InputBar = styled.form`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  padding: 8px 16px;
  border-top: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.background};
  flex-shrink: 0;
`;

const Prompt = styled.span`
  color: ${p => p.theme.colors.terminalGreen};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  line-height: 32px;
  flex-shrink: 0;
  user-select: none;
  font-weight: 600;
`;

const TextArea = styled.textarea`
  flex: 1;
  background: transparent;
  border: none;
  color: ${p => p.theme.colors.textPrimary};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  outline: none;
  padding: 0;
  line-height: 32px;
  resize: none;
  overflow-y: hidden;
  min-height: 32px;
  max-height: 192px;

  &::placeholder {
    color: ${p => p.theme.colors.textMuted};
    opacity: 0.4;
  }

  &:disabled {
    opacity: 0.5;
  }
`;

const StatusBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  border-top: 1px solid ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.background};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.textDimmed};
  flex-shrink: 0;
`;

const ErrorBanner = styled.div`
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.error}15;
  border-top: 1px solid ${p => p.theme.colors.error}30;
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  flex-shrink: 0;
`;

const ErrorLink = styled.a`
  color: ${p => p.theme.colors.terminalBlue};
  text-decoration: underline;
  cursor: pointer;
  &:hover { opacity: 0.8; }
`;

// ===== Quick Actions =====

export interface QuickAction {
  label: string;
  message: string;
  icon?: string;
}

export interface TerminalChatHandle {
  focusInput: () => void;
  sendMessage: (message: string) => void;
  switchSession: (sessionId: string) => void;
  deleteSession: (sessionId: string) => void;
  resetSession: () => void;
  sessionId: string | null;
}

interface TerminalChatProps {
  quickActions?: QuickAction[];
  initialMessage?: string | null;
  className?: string;
  onSessionChange?: (sessionId: string | null) => void;
  onHistoryToggle?: () => void;
  onMaximize?: () => void;
  onToggleStats?: () => void;
  isMaximized?: boolean;
}

const WelcomeState = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: ${p => p.theme.spacing.lg};
  padding: ${p => p.theme.spacing.xl};
`;

const WelcomeText = styled.div`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  opacity: 0.7;
`;

const QuickActionsRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: ${p => p.theme.spacing.sm};
  max-width: 480px;
`;

const QuickActionChip = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  background: transparent;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 4px;
  color: ${p => p.theme.colors.textDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;

  &:hover {
    border-color: ${p => p.theme.colors.terminalBlue}60;
    color: ${p => p.theme.colors.terminalBlue};
  }
`;

const SuggestionsRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.md} 20px ${p => p.theme.spacing.xs};
  animation: ${fadeInUp} 0.3s ease-out both;
`;

// ===== Component =====

export const TerminalChat = forwardRef<TerminalChatHandle, TerminalChatProps>(function TerminalChat({ quickActions, initialMessage, className, onSessionChange, onHistoryToggle, onMaximize, onToggleStats, isMaximized }, ref) {
  const {
    messages,
    isLoading,
    isCreatingSession,
    sessionId,
    model,
    cumulativeUsage,
    error,
    suggestions,
    sendMessage,
    changeModel,
    resetSession,
    switchSession,
    deleteSession,
    onSessionChangeRef,
  } = useChat();

  // Keep a fresh ref to the prop to avoid stale closures in useChat's callbacks.
  // Assigned in an effect rather than during render: mutating a ref while rendering is a side
  // effect in the render phase, which React may run more than once or throw away. The ref is only
  // ever read from callbacks, never during render, so updating it after commit is soon enough.
  const onSessionChangeLatest = useRef(onSessionChange);
  useEffect(() => {
    onSessionChangeLatest.current = onSessionChange;
  }, [onSessionChange]);

  // Wire into useChat's onSessionChangeRef — wraps prop to pass current sessionId
  useEffect(() => {
    onSessionChangeRef.current = () => onSessionChangeLatest.current?.(sessionId);
  }, [onSessionChangeRef, sessionId]);

  // Also fire directly when sessionId changes (covers creation + switching)
  useEffect(() => {
    if (sessionId) {
      onSessionChangeLatest.current?.(sessionId);
    }
  }, [sessionId]);

  const [inputValue, setInputValue] = useState('');
  const messageAreaRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const spinnerFrame = useBrailleSpinner(isLoading);
  const loadingMessage = useLoadingMessage(isLoading);

  // Auto-resize textarea to fit content
  const resizeTextArea = useCallback(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 192)}px`;
    el.style.overflowY = el.scrollHeight > 192 ? 'auto' : 'hidden';
  }, []);

  useEffect(() => {
    resizeTextArea();
  }, [inputValue, resizeTextArea]);

  useImperativeHandle(ref, () => ({
    focusInput: () => {
      inputRef.current?.focus();
    },
    sendMessage,
    switchSession,
    deleteSession,
    resetSession,
    sessionId,
  }), [sendMessage, switchSession, deleteSession, resetSession, sessionId]);

  // Auto-send initialMessage when session is ready
  const initialMessageSentRef = useRef(false);
  useEffect(() => {
    if (initialMessage && sessionId && !isLoading && !isCreatingSession && !initialMessageSentRef.current) {
      initialMessageSentRef.current = true;
      sendMessage(initialMessage);
    }
  }, [initialMessage, sessionId, isLoading, isCreatingSession, sendMessage]);

  // Auto-scroll to bottom on new messages or tool calls
  const scrollToBottom = useCallback(() => {
    if (messageAreaRef.current) {
      messageAreaRef.current.scrollTop = messageAreaRef.current.scrollHeight;
    }
  }, []);

  useEffect(() => {
    const hasUserMessages = messages.some(m => m.role === 'user');
    if (hasUserMessages) {
      scrollToBottom();
    }
  }, [messages, scrollToBottom]);

  // Focus input when session is ready
  useEffect(() => {
    if (!isCreatingSession && !isLoading) {
      inputRef.current?.focus();
    }
  }, [isCreatingSession, isLoading]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const text = inputValue.trim();
    if (!text || isLoading) return;
    setInputValue('');

    if (text === '/clear') {
      resetSession();
      return;
    }

    sendMessage(text);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  // Handle A2UI user actions (e.g. TradeButton clicks) by converting to chat messages
  const handleA2UIAction = useCallback((action: UserAction) => {
    const parts = [action.actionType];
    if (action.payload?.symbol) parts.push(String(action.payload.symbol));
    const text = parts.join(' ');
    sendMessage(text);
  }, [sendMessage]);

  const usageText = cumulativeUsage
    ? `tokens: ${cumulativeUsage.totalTokens.toLocaleString()} | cost: $${parseFloat(cumulativeUsage.billedCost).toFixed(3)}`
    : '';

  return (
    <TerminalWindow className={className}>
      <TitleBar>
        {onHistoryToggle && (
          <HistoryToggle onClick={onHistoryToggle} type="button">
            history
          </HistoryToggle>
        )}
        <TitleBarLeft>
          <TrafficLights onMinimize={onToggleStats} onMaximize={onMaximize} maximized={isMaximized} />
          <TitleText>AI ASSISTANT</TitleText>
        </TitleBarLeft>
        <TitleBarRight>
          <ModelSelector currentModel={model} onChange={changeModel} />
        </TitleBarRight>
      </TitleBar>

      <MessageArea ref={messageAreaRef}>
        {isCreatingSession ? (
          <EmptyState>
            <Spinner>{spinnerFrame}</Spinner>
            {initialMessage
              ? ' preparing your request — this takes a few seconds'
              : ' connecting to AI assistant'}
          </EmptyState>
        ) : (() => {
          const hasUserMessages = messages.some(m => m.role === 'user');
          // Use backend suggestions if available (after greeting), otherwise fall back to frontend quickActions
          const hasBackendSuggestions = !hasUserMessages && suggestions.length > 0;
          const showQuickActions = !hasUserMessages && !hasBackendSuggestions && quickActions && quickActions.length > 0;

          return (
            <>
              {messages.map(msg =>
                msg.role === 'tool_group'
                  ? <ToolCallIndicator key={msg.id} toolCalls={msg.toolCalls || []} />
                  : <ChatMessageBubble key={msg.id} message={msg} onAction={handleA2UIAction} />
              )}
              {hasBackendSuggestions ? (
                <SuggestionsRow>
                  {suggestions.map(s => (
                    <QuickActionChip key={s.message} onClick={() => sendMessage(s.message)} title={s.message} type="button">
                      {s.message}
                    </QuickActionChip>
                  ))}
                </SuggestionsRow>
              ) : showQuickActions ? (
                <WelcomeState>
                  {messages.length === 0 && (
                    <WelcomeText>How can I help with your trading today?</WelcomeText>
                  )}
                  <QuickActionsRow>
                    {quickActions.map(action => (
                      <QuickActionChip
                        key={action.label}
                        onClick={() => sendMessage(action.message)}
                        type="button"
                      >
                        {action.icon && <span>{action.icon}</span>}
                        {action.label}
                      </QuickActionChip>
                    ))}
                  </QuickActionsRow>
                </WelcomeState>
              ) : messages.length === 0 ? (
                <EmptyState>type a message to get started</EmptyState>
              ) : null}
            </>
          );
        })()}

        {/* Show suggestions after user has sent at least one message (not for initial greeting) */}
        {!isLoading && suggestions.length > 0 && messages.some(m => m.role === 'user') && (
          <SuggestionsRow>
            {suggestions.map(s => (
              <QuickActionChip key={s.message} onClick={() => sendMessage(s.message)} title={s.message} type="button">
                {s.message}
              </QuickActionChip>
            ))}
          </SuggestionsRow>
        )}

        {isLoading && (() => {
          const last = messages[messages.length - 1];
          if (!last) return false;
          if (last.role === 'user') return true;
          if (last.role === 'tool_group') return last.toolCalls?.every(tc => tc.status === 'done') ?? true;
          return false;
        })() && (
          <>
            <LoadingIndicator>
              <Spinner>{spinnerFrame}</Spinner> {loadingMessage}
            </LoadingIndicator>
            <LoadingBar />
          </>
        )}
      </MessageArea>

      {error && (
        <ErrorBanner>
          {error.startsWith('BUDGET_DEPLETED:') ? (
            <>error: {error.slice('BUDGET_DEPLETED:'.length)} <ErrorLink href="/settings/budget">Top up budget</ErrorLink></>
          ) : error.startsWith('DAILY_LIMIT:') ? (
            <>error: {error.slice('DAILY_LIMIT:'.length)} <ErrorLink href="/settings/budget">Adjust limit</ErrorLink></>
          ) : error.startsWith('FREE_MODEL_LIMIT:') ? (
            <>error: {error.slice('FREE_MODEL_LIMIT:'.length)}</>
          ) : (
            <>error: {error}</>
          )}
        </ErrorBanner>
      )}

      <InputBar onSubmit={handleSubmit}>
        <Prompt>&gt;</Prompt>
        <TextArea
          ref={inputRef}
          value={inputValue}
          onChange={e => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={isCreatingSession ? 'connecting...' : 'ask anything about your trading...'}
          disabled={isLoading || isCreatingSession}
          autoComplete="off"
          spellCheck={false}
          rows={1}
        />
      </InputBar>

      <StatusBar>
        <span>{model || 'loading...'}</span>
        <span>{usageText}</span>
      </StatusBar>
    </TerminalWindow>
  );
});
