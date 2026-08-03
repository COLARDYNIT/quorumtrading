'use client';

import styled, { keyframes } from 'styled-components';

export interface TerminalSkeletonProps {
  width?: string;
  height?: string;
  variant?: 'text' | 'rect' | 'circle';
  count?: number;
}

const shimmer = keyframes`
  0% {
    background-position: -468px 0;
  }
  100% {
    background-position: 468px 0;
  }
`;

const SkeletonBase = styled.div<{ width?: string; height?: string }>`
  background: linear-gradient(
    to right,
    ${({ theme }) => theme.colors.tertiary} 0%,
    ${({ theme }) => theme.colors.secondary}40 20%,
    ${({ theme }) => theme.colors.tertiary} 40%,
    ${({ theme }) => theme.colors.tertiary} 100%
  );
  background-size: 800px 104px;
  animation: ${shimmer} 1.5s linear infinite;
  border: 1px solid ${({ theme }) => theme.colors.foreground}20;
  width: ${({ width }) => width || '100%'};
  height: ${({ height }) => height || '16px'};
`;

const SkeletonText = styled(SkeletonBase)`
  height: ${({ height }) => height || '14px'};
  margin-bottom: 8px;
  &:last-child {
    margin-bottom: 0;
  }
`;

const SkeletonRect = styled(SkeletonBase)`
  height: ${({ height }) => height || '100px'};
`;

const SkeletonCircle = styled(SkeletonBase)<{ width?: string; height?: string }>`
  border-radius: 50%;
  width: ${({ width, height }) => width || height || '40px'};
  height: ${({ width, height }) => height || width || '40px'};
`;

const SkeletonContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

export function TerminalSkeleton({
  width,
  height,
  variant = 'text',
  count = 1,
}: TerminalSkeletonProps) {
  const skeletons = Array.from({ length: count }, (_, i) => i);

  const renderSkeleton = (key: number) => {
    switch (variant) {
      case 'circle':
        return <SkeletonCircle key={key} width={width} height={height} />;
      case 'rect':
        return <SkeletonRect key={key} width={width} height={height} />;
      case 'text':
      default:
        return <SkeletonText key={key} width={width} height={height} />;
    }
  };

  if (count === 1) {
    return renderSkeleton(0);
  }

  return (
    <SkeletonContainer>
      {skeletons.map((key) => renderSkeleton(key))}
    </SkeletonContainer>
  );
}

// Preset skeleton patterns for common use cases
export const SkeletonPatterns = {
  StrategyCard: () => (
    <SkeletonContainer>
      <TerminalSkeleton variant="text" width="60%" height="18px" />
      <TerminalSkeleton variant="text" width="40%" height="14px" />
      <TerminalSkeleton variant="text" width="80%" height="14px" count={2} />
      <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
        <TerminalSkeleton variant="rect" width="80px" height="32px" />
        <TerminalSkeleton variant="rect" width="80px" height="32px" />
      </div>
    </SkeletonContainer>
  ),

  SimulationRow: () => (
    <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
      <TerminalSkeleton variant="text" width="150px" />
      <TerminalSkeleton variant="rect" width="80px" height="24px" />
      <TerminalSkeleton variant="text" width="100px" />
      <TerminalSkeleton variant="text" width="80px" />
      <TerminalSkeleton variant="rect" width="60px" height="28px" />
    </div>
  ),

  Text: ({ lines = 3 }: { lines?: number }) => (
    <TerminalSkeleton variant="text" count={lines} />
  ),
};
