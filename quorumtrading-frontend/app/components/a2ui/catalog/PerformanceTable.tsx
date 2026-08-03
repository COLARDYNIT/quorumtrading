'use client';

import styled from 'styled-components';

interface PerformanceTableProps {
  headers?: unknown;
  rows?: unknown;
  [key: string]: unknown;
}

export function PerformanceTable({ headers, rows }: PerformanceTableProps) {
  if (!Array.isArray(headers) || !Array.isArray(rows)) return null;

  return (
    <TableWrapper>
      <Table>
        <thead>
          <tr>
            {(headers as string[]).map((h, i) => (
              <Th key={i}>{String(h)}</Th>
            ))}
          </tr>
        </thead>
        <tbody>
          {(rows as unknown[][]).map((row, ri) => (
            <tr key={ri}>
              {(Array.isArray(row) ? row : []).map((cell, ci) => {
                const cellStr = String(cell ?? '');
                const isNumeric = !isNaN(Number(cellStr)) && cellStr !== '';
                const numVal = isNumeric ? Number(cellStr) : 0;
                const isPercentLike = cellStr.includes('%');
                const isPositive = isPercentLike ? numVal > 0 : false;
                const isNegative = isPercentLike ? numVal < 0 : false;

                return (
                  <Td key={ci} $positive={isPositive} $negative={isNegative}>
                    {cellStr}
                  </Td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </Table>
    </TableWrapper>
  );
}

const TableWrapper = styled.div`
  overflow-x: auto;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
`;

const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
`;

const Th = styled.th`
  text-align: left;
  padding: 8px 12px;
  background: ${p => p.theme.colors.background};
  color: ${p => p.theme.colors.terminalBlue};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid ${p => p.theme.colors.border};
  white-space: nowrap;
`;

const Td = styled.td<{ $positive?: boolean; $negative?: boolean }>`
  padding: 6px 12px;
  border-bottom: 1px solid ${p => p.theme.colors.border};
  color: ${p => {
    if (p.$positive) return p.theme.colors.terminalGreen;
    if (p.$negative) return p.theme.colors.terminalPink;
    return p.theme.colors.textMuted;
  }};
  white-space: nowrap;
`;
