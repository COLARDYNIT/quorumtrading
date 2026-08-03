'use client';

import styled from 'styled-components';

interface SparkLineProps {
  data: number[];
  positive: boolean;
  width?: number;
  height?: number;
}

const Svg = styled.svg`
  display: block;
`;

export function SparkLine({ data, positive, width = 84, height = 22 }: SparkLineProps) {
  if (data.length < 2) {
    return <Svg viewBox={`0 0 ${width} ${height}`} style={{ width, height }} />;
  }
  const padX = 1;
  const padY = 3;
  const min = Math.min(...data);
  const max = Math.max(...data);
  const range = max - min || 1;
  const pts = data
    .map((v, i) => {
      const x = padX + (i / (data.length - 1)) * (width - padX * 2);
      const y = height - padY - ((v - min) / range) * (height - padY * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
  const stroke = positive ? 'var(--qt-positive, #06ffa5)' : 'var(--qt-negative, #ff3366)';
  return (
    <Svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" style={{ width, height }}>
      <polyline points={pts} fill="none" stroke={stroke} strokeWidth={1.25} />
    </Svg>
  );
}
