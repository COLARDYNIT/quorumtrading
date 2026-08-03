/**
 * Chat API client for the AI Assistant.
 * Handles session management and SSE-based message streaming.
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8085/api/v1';

// ===== Types =====

export interface ChatSessionResponse {
  sessionId: string;
  greeting: string | null;
  model: string;
  messages: ChatMessageDto[];
  cumulativeUsage: ChatUsageDto;
  suggestions?: FollowUpSuggestion[];
  title?: string | null;
}

export interface ChatSessionSummary {
  sessionId: string;
  title: string | null;
  model: string;
  createdAt: string;
  lastActivityAt: string;
}

export interface ChatMessageDto {
  role: 'user' | 'assistant' | 'system';
  content: string;
}

export interface ChatUsageDto {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  billedCost: string;
}

/**
 * Raw LLM usage from SSE Done events (matches backend LLMUsage record).
 * Unlike ChatUsageDto, this has rawCost instead of billedCost.
 */
interface LLMUsageRaw {
  model: string | null;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
  rawCost: number;
}

export interface FollowUpSuggestion {
  label: string;
  message: string;
}

/**
 * SSE event types matching backend ChatStreamEvent sealed interface.
 */
export type ChatStreamEvent =
  | { type: 'TextResponse'; content: string }
  | { type: 'ToolStart'; callId: string; toolName: string; args: string }
  | { type: 'ToolResult'; callId: string; result: string }
  | { type: 'Done'; usage: LLMUsageRaw; sessionCost: number; suggestions?: FollowUpSuggestion[]; title?: string | null }
  | { type: 'Error'; message: string };

// ===== API Functions =====

export async function createChatSession(): Promise<ChatSessionResponse> {
  const response = await fetch(`${API_BASE_URL}/chat/sessions`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
  });

  if (!response.ok) {
    throw new Error(`Failed to create chat session: ${response.status}`);
  }

  return response.json();
}

export async function getChatSession(sessionId: string): Promise<ChatSessionResponse> {
  const response = await fetch(`${API_BASE_URL}/chat/sessions/${sessionId}`, {
    credentials: 'include',
    headers: { 'Accept': 'application/json' },
  });

  if (!response.ok) {
    throw new Error(`Failed to get chat session: ${response.status}`);
  }

  return response.json();
}

export async function deleteChatSession(sessionId: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/chat/sessions/${sessionId}`, {
    method: 'DELETE',
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Failed to delete chat session: ${response.status}`);
  }
}

export async function listChatSessions(): Promise<ChatSessionSummary[]> {
  const response = await fetch(`${API_BASE_URL}/chat/sessions`, {
    credentials: 'include',
    headers: { 'Accept': 'application/json' },
  });

  if (!response.ok) {
    throw new Error(`Failed to list chat sessions: ${response.status}`);
  }

  return response.json();
}

export async function updateSessionModel(sessionId: string, model: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/chat/sessions/${sessionId}/model`, {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ model }),
  });

  if (!response.ok) {
    throw new Error(`Failed to update session model: ${response.status}`);
  }
}

/**
 * Send a message to the chat session via POST-based SSE.
 * Parses the SSE stream and invokes the callback for each event.
 */
export async function sendMessage(
  sessionId: string,
  content: string,
  onEvent: (event: ChatStreamEvent) => void,
  onError?: (error: Error) => void,
): Promise<void> {
  const controller = new AbortController();

  try {
    const response = await fetch(`${API_BASE_URL}/chat/sessions/${sessionId}/messages`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream',
      },
      body: JSON.stringify({ content }),
      signal: controller.signal,
    });

    if (!response.ok) {
      let errorMessage = `Chat request failed (${response.status})`;
      try {
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json') || contentType.includes('application/problem+json')) {
          const problem = await response.json();
          if (response.status === 402) {
            errorMessage = `BUDGET_DEPLETED:${problem.detail || 'Your AI budget has been depleted. Please top up your budget to continue.'}`;
          } else if (response.status === 429 && problem.type?.includes('daily-spend-limit')) {
            errorMessage = `DAILY_LIMIT:${problem.detail || 'Your daily spend limit has been reached. Try again tomorrow or increase your limit.'}`;
          } else if (response.status === 429) {
            errorMessage = `FREE_MODEL_LIMIT:${problem.detail || 'Rate limit exceeded. Try again later or switch to a different model.'}`;
          } else {
            errorMessage = problem.detail || problem.title || problem.message || errorMessage;
          }
        } else {
          const text = await response.text();
          if (text) errorMessage += `: ${text}`;
        }
      } catch {
        // Failed to parse error body
      }
      throw new Error(errorMessage);
    }

    if (!response.body) {
      throw new Error('No response body for SSE stream');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      // Process complete SSE messages (separated by double newline)
      const parts = buffer.split('\n\n');
      buffer = parts.pop() || '';

      for (const part of parts) {
        const dataLine = part
          .split('\n')
          .find(line => line.startsWith('data:'));

        if (dataLine) {
          const jsonStr = dataLine.slice(5).trim();
          if (jsonStr) {
            try {
              const event = JSON.parse(jsonStr) as ChatStreamEvent;
              onEvent(event);
            } catch {
              // Skip malformed JSON
            }
          }
        }
      }
    }

    // Process any remaining buffer
    if (buffer.trim()) {
      const dataLine = buffer
        .split('\n')
        .find(line => line.startsWith('data:'));

      if (dataLine) {
        const jsonStr = dataLine.slice(5).trim();
        if (jsonStr) {
          try {
            const event = JSON.parse(jsonStr) as ChatStreamEvent;
            onEvent(event);
          } catch {
            // Skip malformed JSON
          }
        }
      }
    }
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') return;
    const err = error instanceof Error ? error : new Error(String(error));
    onError?.(err);
    onEvent({ type: 'Error', message: err.message });
  }
}
