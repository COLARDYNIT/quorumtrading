'use client';

import { useState, useRef, useEffect } from 'react';
import styled from 'styled-components';
import type { ToolCall } from '@/app/hooks/useChat';
import { useBrailleSpinner } from '@/app/lib/hooks';

const Container = styled.div`
  padding: 0 20px;
`;

const SummaryLine = styled.div<{ $clickable?: boolean }>`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 14px;
  line-height: 1.7;
  color: ${p => p.theme.colors.terminalBlue};
  opacity: 0.9;
  white-space: pre-wrap;
  word-break: break-word;
  cursor: ${p => (p.$clickable ? 'pointer' : 'default')};
  display: flex;
  align-items: center;
  gap: 6px;

  &:hover {
    opacity: ${p => (p.$clickable ? 1 : 0.9)};
  }
`;

const Chevron = styled.span`
  font-size: 12px;
  margin-left: auto;
  user-select: none;
`;

const DetailList = styled.div`
  padding-left: 1.5em;
`;

const DetailLine = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  line-height: 1.7;
  color: ${p => p.theme.colors.textMuted};
  white-space: pre-wrap;
  word-break: break-word;
`;

const SpinnerChar = styled.span`
  color: ${p => p.theme.colors.terminalAmber};
  font-weight: 600;
`;

interface ToolCallIndicatorProps {
  toolCalls: ToolCall[];
}

function summarizeResult(result: string): string {
  try {
    const parsed = JSON.parse(result);
    if (parsed.message) return String(parsed.message);
    if (parsed.status) return String(parsed.status);
    if (parsed.name && parsed.id) return `${parsed.name} (${String(parsed.id).slice(0, 8)}...)`;
    if (typeof parsed === 'string') return parsed.slice(0, 100);
    if (Array.isArray(parsed)) return `${parsed.length} items`;
    const flat = JSON.stringify(parsed);
    return flat.length > 100 ? flat.slice(0, 100) + '...' : flat;
  } catch {
    return result.length > 100 ? result.slice(0, 100) + '...' : result;
  }
}

function formatElapsed(ms: number): string {
  if (ms < 1000) return '< 1s';
  if (ms < 10000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.round(ms / 1000)}s`;
}

export function ToolCallIndicator({ toolCalls }: ToolCallIndicatorProps) {
  const [expanded, setExpanded] = useState(false);
  const allDone = toolCalls.length > 0 && toolCalls.every(tc => tc.status === 'done');
  const isRunning = !allDone;
  const spinnerFrame = useBrailleSpinner(isRunning);

  // Freeze elapsed time when all tools complete.
  // State, not a ref: this is written from an effect and read while rendering the summary line.
  // A ref write schedules no re-render, so the frozen figure only ever reached the screen when
  // something else happened to re-render at the same moment — and reading a ref during render is
  // what react-hooks/refs flags, because under concurrent rendering there is no guarantee the
  // value read belongs to the render being committed.
  const [frozenElapsed, setFrozenElapsed] = useState<number | null>(null);
  useEffect(() => {
    if (allDone && frozenElapsed === null) {
      const startTime = toolCalls[0]?.startedAt;
      setFrozenElapsed(startTime ? Date.now() - startTime : 0);
    }
  }, [allDone, toolCalls, frozenElapsed]);

  // Reset frozen time when new tool calls come in (new group)
  const prevLengthRef = useRef(toolCalls.length);
  useEffect(() => {
    if (toolCalls.length < prevLengthRef.current) {
      setFrozenElapsed(null);
    }
    prevLengthRef.current = toolCalls.length;
  }, [toolCalls.length]);

  if (toolCalls.length === 0) return null;

  const lastRunning = [...toolCalls].reverse().find(tc => tc.status === 'running');
  const lastToolName = lastRunning?.toolName ?? toolCalls[toolCalls.length - 1].toolName;

  const startTime = toolCalls[0]?.startedAt;
  const elapsed = allDone
    ? frozenElapsed ?? 0
    : startTime
      ? Date.now() - startTime
      : 0;

  if (isRunning) {
    return (
      <Container>
        <SummaryLine>
          <SpinnerChar>{spinnerFrame}</SpinnerChar> Calling {lastToolName}...
        </SummaryLine>
      </Container>
    );
  }

  const toolCount = toolCalls.length;
  const label = toolCount === 1 ? '1 tool' : `${toolCount} tools`;

  return (
    <Container>
      <SummaryLine $clickable onClick={() => setExpanded(!expanded)}>
        <span>{'\u26A1'} Used {label} ({formatElapsed(elapsed)})</span>
        <Chevron>{expanded ? '\u25BE' : '\u25B8'}</Chevron>
      </SummaryLine>
      {expanded && (
        <DetailList>
          {toolCalls.map(tc => (
            <DetailLine key={tc.callId}>
              {'\u2713'} {tc.toolName}{tc.result ? ` \u2192 ${summarizeResult(tc.result)}` : ''}
            </DetailLine>
          ))}
        </DetailList>
      )}
    </Container>
  );
}
