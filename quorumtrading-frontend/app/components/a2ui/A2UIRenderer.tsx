'use client';

import { useMemo } from 'react';
import styled from 'styled-components';
import { parseA2UIPayload } from '@/app/lib/a2ui/parser';
import { getComponent } from '@/app/lib/a2ui/catalog';
import type { ComponentNode, UserAction } from '@/app/lib/a2ui/types';

interface A2UIRendererProps {
  payload: string;
  onAction?: (action: UserAction) => void;
}

/** Recursively render a ComponentNode tree. */
function RenderNode({ node, onAction }: { node: ComponentNode; onAction?: (action: UserAction) => void }) {
  const Component = getComponent(node.type);

  const childElements = node.children.length > 0
    ? node.children.map(child => (
        <RenderNode key={child.id} node={child} onAction={onAction} />
      ))
    : undefined;

  if (!Component) {
    return <FallbackComponent type={node.type} props={node.props} />;
  }

  return (
    <Component {...node.props} onAction={onAction}>
      {childElements}
    </Component>
  );
}

/** Shown for unknown component types — terminal-style JSON dump. */
function FallbackComponent({ type, props }: { type: string; props: Record<string, unknown> }) {
  return (
    <FallbackPre>
      <FallbackLabel>Unknown: {type}</FallbackLabel>
      {JSON.stringify(props, null, 2)}
    </FallbackPre>
  );
}

export function A2UIRenderer({ payload, onAction }: A2UIRendererProps) {
  const surface = useMemo(() => parseA2UIPayload(payload), [payload]);

  if (!surface) return null;

  return (
    <A2UIContainer>
      {surface.roots.map(root => (
        <RenderNode key={root.id} node={root} onAction={onAction} />
      ))}
    </A2UIContainer>
  );
}

const A2UIContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const FallbackPre = styled.pre`
  background: ${p => p.theme.colors.background};
  border: 1px dashed ${p => p.theme.colors.border};
  padding: ${p => p.theme.spacing.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  overflow-x: auto;
  white-space: pre-wrap;
  margin: 0;
`;

const FallbackLabel = styled.div`
  color: ${p => p.theme.colors.warning};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  margin-bottom: ${p => p.theme.spacing.xs};
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-size: ${p => p.theme.typography.fontSize.xs};
`;
