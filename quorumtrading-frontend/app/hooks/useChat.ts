'use client';

import { useState, useCallback, useRef, useEffect } from 'react';
import {
  createChatSession,
  getChatSession,
  listChatSessions,
  deleteChatSession as deleteChatSessionApi,
  sendMessage as sendChatMessage,
  updateSessionModel as updateModel,
  ChatStreamEvent,
  ChatUsageDto,
  ChatMessageDto,
  FollowUpSuggestion,
} from '@/app/lib/api/chat';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'tool_group';
  content: string;
  isStreaming?: boolean;
  toolCalls?: ToolCall[];
}

export interface ToolCall {
  callId: string;
  toolName: string;
  args: string;
  result?: string;
  status: 'running' | 'done';
  startedAt?: number;
}

interface UseChatState {
  messages: ChatMessage[];
  isLoading: boolean;
  isCreatingSession: boolean;
  sessionId: string | null;
  title: string | null;
  model: string | null;
  cumulativeUsage: ChatUsageDto | null;
  error: string | null;
  suggestions: FollowUpSuggestion[];
}

const SESSION_STORAGE_KEY = 'quorumtrading:chat:sessionId';

let messageIdCounter = 0;
function nextMessageId(): string {
  return `msg-${Date.now()}-${++messageIdCounter}`;
}

export function useChat() {
  const [state, setState] = useState<UseChatState>({
    messages: [],
    isLoading: false,
    isCreatingSession: false,
    sessionId: null,
    title: null,
    model: null,
    cumulativeUsage: null,
    error: null,
    suggestions: [],
  });

  const abortRef = useRef(false);
  const toolGroupIdRef = useRef<string | null>(null);
  const onSessionChangeRef = useRef<(() => void) | null>(null);

  // Restore session on mount: sessionStorage > last active session > new session
  useEffect(() => {
    const storedSessionId = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (storedSessionId) {
      reconnect(storedSessionId);
    } else {
      // No tab-local session — try to resume the most recent existing session
      resumeLastSessionOrCreate();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const initSession = useCallback(async () => {
    setState(prev => ({ ...prev, isCreatingSession: true, error: null }));
    try {
      const session = await createChatSession();
      sessionStorage.setItem(SESSION_STORAGE_KEY, session.sessionId);

      const initialMessages: ChatMessage[] = [];
      if (session.greeting) {
        initialMessages.push({
          id: nextMessageId(),
          role: 'assistant',
          content: session.greeting,
        });
      }

      setState(prev => ({
        ...prev,
        isCreatingSession: false,
        sessionId: session.sessionId,
        title: session.title || null,
        model: session.model,
        messages: initialMessages,
        cumulativeUsage: session.cumulativeUsage,
        suggestions: session.suggestions || [],
      }));

      // Notify listeners that session list changed (new session created)
      onSessionChangeRef.current?.();
    } catch (error) {
      setState(prev => ({
        ...prev,
        isCreatingSession: false,
        error: error instanceof Error ? error.message : 'Failed to create session',
      }));
    }
  }, []);

  const reconnect = useCallback(async (sessionId: string) => {
    setState(prev => ({ ...prev, isCreatingSession: true, error: null }));
    try {
      const session = await getChatSession(sessionId);

      const messages: ChatMessage[] = session.messages
        .filter((m: ChatMessageDto) => m.role === 'user' || m.role === 'assistant')
        .map((m: ChatMessageDto) => ({
          id: nextMessageId(),
          role: m.role as 'user' | 'assistant',
          content: m.content,
        }));

      setState(prev => ({
        ...prev,
        isCreatingSession: false,
        sessionId: session.sessionId,
        title: session.title || null,
        model: session.model,
        messages,
        cumulativeUsage: session.cumulativeUsage,
      }));
    } catch {
      // Session expired or invalid — create a new one
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
      await initSession();
    }
  }, [initSession]);

  const resumeLastSessionOrCreate = useCallback(async () => {
    try {
      const sessions = await listChatSessions();
      if (sessions.length > 0) {
        // Sessions are sorted by lastActivityAt desc — resume the most recent
        await reconnect(sessions[0].sessionId);
        sessionStorage.setItem(SESSION_STORAGE_KEY, sessions[0].sessionId);
        return;
      }
    } catch {
      // Failed to list sessions — fall through to create new
    }
    await initSession();
  }, [reconnect, initSession]);

  const switchSession = useCallback(async (sessionId: string) => {
    if (state.isLoading) return;
    sessionStorage.setItem(SESSION_STORAGE_KEY, sessionId);
    toolGroupIdRef.current = null;
    setState(prev => ({
      ...prev,
      messages: [],
      isLoading: false,
      suggestions: [],
      error: null,
    }));
    await reconnect(sessionId);
  }, [state.isLoading, reconnect]);

  const sendMessageFn = useCallback(async (text: string) => {
    if (!state.sessionId || state.isLoading) return;

    abortRef.current = false;
    toolGroupIdRef.current = null;

    const userMessage: ChatMessage = {
      id: nextMessageId(),
      role: 'user',
      content: text,
    };

    const assistantMessageId = nextMessageId();

    setState(prev => ({
      ...prev,
      isLoading: true,
      error: null,
      messages: [...prev.messages, userMessage],
      suggestions: [],
    }));

    let assistantContent = '';

    const handleEvent = (event: ChatStreamEvent) => {
      if (abortRef.current) return;

      switch (event.type) {
        case 'TextResponse':
          assistantContent += event.content;
          setState(prev => {
            const existing = prev.messages.find(m => m.id === assistantMessageId);
            if (existing) {
              return {
                ...prev,
                messages: prev.messages.map(m =>
                  m.id === assistantMessageId
                    ? { ...m, content: assistantContent }
                    : m
                ),
              };
            }
            return {
              ...prev,
              messages: [
                ...prev.messages,
                {
                  id: assistantMessageId,
                  role: 'assistant' as const,
                  content: assistantContent,
                  isStreaming: true,
                },
              ],
            };
          });
          break;

        case 'ToolStart': {
          const newToolCall: ToolCall = {
            callId: event.callId,
            toolName: event.toolName,
            args: event.args,
            status: 'running' as const,
            startedAt: Date.now(),
          };
          setState(prev => {
            const groupId = toolGroupIdRef.current;
            const existingGroup = groupId ? prev.messages.find(m => m.id === groupId) : null;
            if (existingGroup && existingGroup.role === 'tool_group') {
              return {
                ...prev,
                messages: prev.messages.map(m =>
                  m.id === groupId
                    ? { ...m, toolCalls: [...(m.toolCalls || []), newToolCall] }
                    : m
                ),
              };
            }
            const newGroupId = nextMessageId();
            toolGroupIdRef.current = newGroupId;
            return {
              ...prev,
              messages: [
                ...prev.messages,
                {
                  id: newGroupId,
                  role: 'tool_group' as const,
                  content: '',
                  toolCalls: [newToolCall],
                },
              ],
            };
          });
          break;
        }

        case 'ToolResult': {
          const groupId = toolGroupIdRef.current;
          if (groupId) {
            setState(prev => ({
              ...prev,
              messages: prev.messages.map(m =>
                m.id === groupId
                  ? {
                      ...m,
                      toolCalls: (m.toolCalls || []).map(tc =>
                        tc.callId === event.callId
                          ? { ...tc, result: event.result, status: 'done' as const }
                          : tc
                      ),
                    }
                  : m
              ),
            }));
          }
          break;
        }

        case 'Done':
          toolGroupIdRef.current = null;
          setState(prev => {
            const newTitle = event.title || prev.title;
            const titleChanged = newTitle !== prev.title;

            // Notify session list if title was generated
            if (titleChanged) {
              setTimeout(() => onSessionChangeRef.current?.(), 0);
            }

            return {
              ...prev,
              isLoading: false,
              title: newTitle,
              suggestions: event.suggestions || [],
              cumulativeUsage: {
                promptTokens: event.usage.promptTokens,
                completionTokens: event.usage.completionTokens,
                totalTokens: event.usage.totalTokens,
                billedCost: String(event.sessionCost),
              },
              messages: prev.messages.map(m =>
                m.id === assistantMessageId
                  ? { ...m, isStreaming: false }
                  : m
              ),
            };
          });
          break;

        case 'Error':
          setState(prev => ({
            ...prev,
            isLoading: false,
            error: event.message,
          }));
          break;
      }
    };

    await sendChatMessage(
      state.sessionId,
      text,
      handleEvent,
      (error) => {
        setState(prev => ({
          ...prev,
          isLoading: false,
          error: error.message,
        }));
      },
    );
  }, [state.sessionId, state.isLoading]);

  const changeModel = useCallback(async (newModel: string) => {
    if (!state.sessionId) return;
    try {
      await updateModel(state.sessionId, newModel);
      setState(prev => ({ ...prev, model: newModel }));
    } catch (error) {
      setState(prev => ({
        ...prev,
        error: error instanceof Error ? error.message : 'Failed to update model',
      }));
    }
  }, [state.sessionId]);

  const resetSession = useCallback(async () => {
    sessionStorage.removeItem(SESSION_STORAGE_KEY);
    toolGroupIdRef.current = null;
    setState({
      messages: [],
      isLoading: false,
      isCreatingSession: false,
      sessionId: null,
      title: null,
      model: null,
      cumulativeUsage: null,
      error: null,
      suggestions: [],
    });
    await initSession();
  }, [initSession]);

  const deleteSession = useCallback(async (sessionId: string) => {
    try {
      await deleteChatSessionApi(sessionId);
      // If deleting the active session, create a new one
      if (sessionId === state.sessionId) {
        await resetSession();
      }
      onSessionChangeRef.current?.();
    } catch (error) {
      setState(prev => ({
        ...prev,
        error: error instanceof Error ? error.message : 'Failed to delete session',
      }));
    }
  }, [state.sessionId, resetSession]);

  return {
    messages: state.messages,
    isLoading: state.isLoading,
    isCreatingSession: state.isCreatingSession,
    sessionId: state.sessionId,
    title: state.title,
    model: state.model,
    cumulativeUsage: state.cumulativeUsage,
    error: state.error,
    suggestions: state.suggestions,
    sendMessage: sendMessageFn,
    changeModel,
    resetSession,
    switchSession,
    deleteSession,
    onSessionChangeRef,
  };
}
