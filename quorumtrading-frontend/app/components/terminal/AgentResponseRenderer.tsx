'use client';

import { useMemo } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import styled from 'styled-components';

const MarkdownContainer = styled.div`
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.7;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  overflow-wrap: break-word;
  word-break: break-word;

  h1, h2, h3, h4, h5, h6 {
    color: ${p => p.theme.colors.terminalBlue};
    margin: 1.5em 0 0.5em 0;
    font-weight: ${p => p.theme.typography.fontWeight.bold};
  }

  h1 { font-size: 1.4em; }
  h2 { font-size: 1.2em; }
  h3 { font-size: 1.1em; }

  p {
    margin: 1em 0;
  }

  strong {
    color: ${p => p.theme.colors.terminalGreen};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }

  em {
    color: ${p => p.theme.colors.text};
    font-style: italic;
  }

  a {
    color: ${p => p.theme.colors.terminalBlue};
    text-decoration: underline;

    &:hover {
      color: ${p => p.theme.colors.terminalGreen};
    }
  }

  ul, ol {
    margin: 1em 0;
    padding-left: 1.5em;
  }

  li {
    margin: 0.5em 0;

    &::marker {
      color: ${p => p.theme.colors.terminalGreen};
    }
  }

  code {
    background: ${p => p.theme.colors.background};
    color: ${p => p.theme.colors.terminalGreen};
    padding: 0.2em 0.4em;
    border-radius: 3px;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 0.9em;
  }

  pre {
    background: ${p => p.theme.colors.background};
    border: 1px solid ${p => p.theme.colors.border};
    border-radius: ${p => p.theme.borderRadius.md};
    padding: ${p => p.theme.spacing.md};
    overflow-x: auto;
    margin: 1em 0;

    code {
      background: none;
      padding: 0;
      color: ${p => p.theme.colors.text};
    }
  }

  blockquote {
    border-left: 3px solid ${p => p.theme.colors.terminalGreen};
    margin: 1em 0;
    padding-left: 1em;
    color: ${p => p.theme.colors.textMuted};
    font-style: italic;
  }

  hr {
    border: none;
    border-top: 1px solid ${p => p.theme.colors.border};
    margin: 2em 0;
  }

  table {
    border-collapse: collapse;
    width: 100%;
    margin: 1em 0;
  }

  th, td {
    border: 1px solid ${p => p.theme.colors.border};
    padding: 0.5em;
    text-align: left;
  }

  th {
    background: ${p => p.theme.colors.surface};
    color: ${p => p.theme.colors.terminalBlue};
  }
`;

const CodeBlock = styled.pre`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.md};
  margin: 0;
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.text};
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 400px;
  overflow-y: auto;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  line-height: 1.4;
`;

const CodeLabel = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const JsonContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.md};
`;

const JsonField = styled.div`
  padding: ${p => p.theme.spacing.sm} ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.background};
  border-left: 3px solid ${p => p.theme.colors.terminalGreen};
  border-radius: 0 ${p => p.theme.borderRadius.sm} ${p => p.theme.borderRadius.sm} 0;
`;

const JsonFieldLabel = styled.div`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.terminalBlue};
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: ${p => p.theme.spacing.xs};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const JsonFieldValue = styled.div`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.6;
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

const ScoreBadge = styled.span<{ $score: number }>`
  display: inline-block;
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  border-radius: ${p => p.theme.borderRadius.md};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-size: ${p => p.theme.typography.fontSize.sm};
  background: ${p => {
    if (p.$score >= 80) return p.theme.colors.score.high.background;
    if (p.$score >= 60) return p.theme.colors.score.medium.background;
    return p.theme.colors.score.low.background;
  }};
  color: ${p => {
    if (p.$score >= 80) return p.theme.colors.score.high.text;
    if (p.$score >= 60) return p.theme.colors.score.medium.text;
    return p.theme.colors.score.low.text;
  }};
  border: 1px solid ${p => {
    if (p.$score >= 80) return p.theme.colors.score.high.border;
    if (p.$score >= 60) return p.theme.colors.score.medium.border;
    return p.theme.colors.score.low.border;
  }};
`;

const RecommendationBadge = styled.span<{ $recommendation: string }>`
  display: inline-block;
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  border-radius: ${p => p.theme.borderRadius.md};
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  font-size: ${p => p.theme.typography.fontSize.sm};
  background: ${p => p.$recommendation === 'APPROVE' ? p.theme.colors.score.high.background : p.theme.colors.score.low.background};
  color: ${p => p.$recommendation === 'APPROVE' ? p.theme.colors.score.high.text : p.theme.colors.score.low.text};
  border: 1px solid ${p => p.$recommendation === 'APPROVE' ? p.theme.colors.score.high.border : p.theme.colors.score.low.border};
`;

interface AgentResponseRendererProps {
  content: string;
  agentType?: string;
}

/**
 * Extract JSON from content that may be wrapped in markdown code fences.
 */
function extractJSON(str: string): string {
  let trimmed = str.trim();
  // Strip markdown code fences: ```json ... ``` or ``` ... ```
  const fenceMatch = trimmed.match(/^```(?:json)?\s*\n?([\s\S]*?)\n?\s*```$/);
  if (fenceMatch) {
    trimmed = fenceMatch[1].trim();
  }
  return trimmed;
}

function isJSON(str: string): boolean {
  const cleaned = extractJSON(str);
  return cleaned.startsWith('{') && cleaned.endsWith('}');
}

function isYAMLFrontmatter(str: string): boolean {
  return str.trim().startsWith('---');
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function parseViabilityJSON(str: string): Record<string, any> | null {
  try {
    const cleaned = extractJSON(str);
    const parsed = JSON.parse(cleaned);
    if (typeof parsed === 'object' && parsed !== null && typeof parsed.viabilityScore === 'number') {
      return parsed;
    }
    return null;
  } catch {
    return null;
  }
}

/** Convert camelCase keys to readable labels: "currentMarketConditions" → "Current Market Conditions" */
function formatFieldLabel(key: string): string {
  return key
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, c => c.toUpperCase())
    .trim();
}

/** Fields to display first in order, then remaining fields follow */
const PRIORITY_FIELDS = [
  'currentMarketConditions',
  'assetAccessibility',
  'entryFeasibility',
  'exitFeasibility',
  'opportunityQuality',
  'riskAssessment',
  'timingAnalysis',
  'capitalUtilization',
  'recommendedTickers',
  'majorConcerns',
  'detailedReasoning',
];

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function ViabilityAssessmentDisplay({ assessment }: { assessment: Record<string, any> }) {
  // Collect all string/array fields except score and recommendation (shown in header)
  const skipKeys = new Set(['viabilityScore', 'recommendation']);
  const allKeys = Object.keys(assessment).filter(k => !skipKeys.has(k));

  // Sort: priority fields first (in order), then any remaining fields
  const sortedKeys = [
    ...PRIORITY_FIELDS.filter(k => allKeys.includes(k)),
    ...allKeys.filter(k => !PRIORITY_FIELDS.includes(k)),
  ];

  return (
    <JsonContainer>
      <JsonField>
        <JsonFieldLabel>Viability Score</JsonFieldLabel>
        <JsonFieldValue>
          <ScoreBadge $score={assessment.viabilityScore}>
            {assessment.viabilityScore}/100
          </ScoreBadge>
          {assessment.recommendation && (
            <>
              {' '}
              <RecommendationBadge $recommendation={assessment.recommendation}>
                {assessment.recommendation}
              </RecommendationBadge>
            </>
          )}
        </JsonFieldValue>
      </JsonField>

      {sortedKeys.map(key => {
        const value = assessment[key];
        if (value == null || value === '' || value === 'None' || value === 'N/A') return null;

        // Array values (majorConcerns, etc.)
        if (Array.isArray(value)) {
          const filtered = value.filter((v: string) => v && v !== 'None' && v !== 'N/A');
          if (filtered.length === 0) return null;
          return (
            <JsonField key={key}>
              <JsonFieldLabel>{formatFieldLabel(key)}</JsonFieldLabel>
              <JsonFieldValue>
                <ul style={{ margin: 0, paddingLeft: '1.5em' }}>
                  {filtered.map((item: string, i: number) => (
                    <li key={i}>{String(item)}</li>
                  ))}
                </ul>
              </JsonFieldValue>
            </JsonField>
          );
        }

        // String/number values
        return (
          <JsonField key={key}>
            <JsonFieldLabel>{formatFieldLabel(key)}</JsonFieldLabel>
            <JsonFieldValue>{String(value)}</JsonFieldValue>
          </JsonField>
        );
      })}
    </JsonContainer>
  );
}

export function AgentResponseRenderer({ content, agentType }: AgentResponseRendererProps) {
  const renderedContent = useMemo(() => {
    if (!content) return null;

    // Check for Viability Assessor JSON output
    if (agentType === 'VIABILITY_ASSESSOR' || isJSON(content)) {
      const viabilityData = parseViabilityJSON(content);
      if (viabilityData) {
        return <ViabilityAssessmentDisplay assessment={viabilityData} />;
      }
    }

    // Check for Strategy Designer YAML output
    if (agentType === 'STRATEGY_DESIGNER' || isYAMLFrontmatter(content)) {
      return (
        <div>
          <CodeLabel>Strategy Definition (YAML)</CodeLabel>
          <CodeBlock>{content}</CodeBlock>
        </div>
      );
    }

    // Default: render as markdown
    return (
      <MarkdownContainer>
        <ReactMarkdown remarkPlugins={[remarkGfm]}>
          {content}
        </ReactMarkdown>
      </MarkdownContainer>
    );
  }, [content, agentType]);

  return <>{renderedContent}</>;
}
