'use client';

import styled from 'styled-components';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import Link from 'next/link';
import type { ChatMessage } from '@/app/hooks/useChat';
import { blink } from '@/app/styles/animations';
import { splitA2UIBlocks } from '@/app/lib/a2ui/splitBlocks';
import { A2UIRenderer } from '@/app/components/a2ui/A2UIRenderer';
import type { UserAction } from '@/app/lib/a2ui/types';

const MessageLine = styled.div<{ $isUser: boolean }>`
  padding: 0 20px;
  margin-bottom: ${p => p.$isUser ? '10px' : '15px'};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  line-height: 1.7;
  word-break: break-word;

  ${p => p.$isUser ? `
    white-space: pre-wrap;
    color: ${p.theme.colors.terminalGreen};
    font-weight: 600;
  ` : `
    color: ${p.theme.colors.textLight};
  `}
`;

const MarkdownContent = styled.div`
  /* Reset stray borders on all block elements */
  > *, p, h1, h2, h3, h4, h5, h6, ul, ol, li, blockquote, pre, hr {
    border: none;
    outline: none;
  }

  /* Headings */
  h1, h2, h3, h4, h5, h6 {
    color: ${p => p.theme.colors.terminalBlue};
    margin: ${p => p.theme.spacing.sm} 0 ${p => p.theme.spacing.xs};
    font-size: ${p => p.theme.typography.fontSize.xs};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }

  /* Paragraphs — tight spacing to avoid double line breaks */
  p {
    margin: 0 0 0.3em;
  }

  p:last-child {
    margin-bottom: 0;
  }

  /* Links — internal routes become Next.js Links */
  a {
    color: ${p => p.theme.colors.terminalBlue};
    text-decoration: underline;
    text-underline-offset: 2px;

    &:hover {
      color: ${p => p.theme.colors.terminalGreen};
    }
  }

  /* Code — inline only */
  code {
    background: ${p => p.theme.colors.background};
    padding: 1px 4px;
    border-radius: 3px;
    font-size: 0.9em;
    color: ${p => p.theme.colors.terminalAmber};
    border: none;
  }

  pre {
    background: ${p => p.theme.colors.background};
    padding: ${p => p.theme.spacing.sm};
    border-radius: ${p => p.theme.borderRadius.sm};
    border: none;
    overflow-x: auto;
    margin: ${p => p.theme.spacing.xs} 0;

    code {
      background: none;
      padding: 0;
      border: none;
      color: ${p => p.theme.colors.textPrimary};
    }
  }

  /* Horizontal rule */
  hr {
    border: none;
    border-top: 1px solid ${p => p.theme.colors.border};
    margin: ${p => p.theme.spacing.md} 0;
    opacity: 0.5;
  }

  /* Lists */
  ul, ol {
    margin: ${p => p.theme.spacing.xs} 0;
    padding-left: ${p => p.theme.spacing.lg};
  }

  li {
    margin-bottom: 2px;
  }

  /* Bold */
  strong {
    color: ${p => p.theme.colors.terminalGreen};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }

  /* Tables */
  table {
    border-collapse: collapse;
    width: 100%;
    margin: ${p => p.theme.spacing.xs} 0;
    font-size: ${p => p.theme.typography.fontSize.xs};
  }

  th, td {
    border: 1px solid ${p => p.theme.colors.border};
    padding: 4px 8px;
    text-align: left;
  }

  th {
    background: ${p => p.theme.colors.background};
    color: ${p => p.theme.colors.terminalBlue};
  }
`;

const Cursor = styled.span`
  display: inline-block;
  width: 8px;
  height: 1.1em;
  background: ${p => p.theme.colors.terminalGreen};
  margin-left: 1px;
  vertical-align: text-bottom;
  animation: ${blink} 1s step-end infinite;
`;

interface ChatMessageBubbleProps {
  message: ChatMessage;
  onAction?: (action: UserAction) => void;
}

/**
 * Custom link renderer that uses Next.js Link for internal routes.
 */
function MarkdownLink({ href, children, ...props }: React.AnchorHTMLAttributes<HTMLAnchorElement>) {
  if (href && href.startsWith('/')) {
    return (
      <Link href={href} {...props}>
        {children}
      </Link>
    );
  }
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" {...props}>
      {children}
    </a>
  );
}

/**
 * Render assistant content with interleaved text (markdown) and A2UI blocks.
 */
function renderContentWithA2UI(content: string, onAction?: (action: UserAction) => void) {
  const blocks = splitA2UIBlocks(content);

  // Fast path: no A2UI tags, render pure markdown
  if (blocks.length === 1 && blocks[0].type === 'text') {
    return (
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{ a: MarkdownLink as never }}
      >
        {blocks[0].content}
      </ReactMarkdown>
    );
  }

  return (
    <>
      {blocks.map((block, i) =>
        block.type === 'a2ui' ? (
          <A2UIRenderer key={i} payload={block.content} onAction={onAction} />
        ) : (
          <ReactMarkdown
            key={i}
            remarkPlugins={[remarkGfm]}
            components={{ a: MarkdownLink as never }}
          >
            {block.content}
          </ReactMarkdown>
        )
      )}
    </>
  );
}

export function ChatMessageBubble({ message, onAction }: ChatMessageBubbleProps) {
  const isUser = message.role === 'user';

  // Skip empty assistant messages (e.g. tool-call-only turns with no text)
  if (!isUser && !message.content && !message.isStreaming) return null;

  return (
    <MessageLine $isUser={isUser}>
      {isUser ? (
        <>{`> ${message.content}`}</>
      ) : (
        <MarkdownContent>
          {renderContentWithA2UI(message.content, onAction)}
          {message.isStreaming && <Cursor />}
        </MarkdownContent>
      )}
    </MessageLine>
  );
}
