'use client';

import { useParams } from 'next/navigation';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import styled from 'styled-components';
import { LandingLayout } from '@/app/components/layouts/LandingLayout';
import { Panel } from '@/app/components/shared/v2';
import { MarkdownContainer } from '@/app/components/shared';
import { CouncilSection } from '@/app/components/landing/CouncilSection';
import { QuorumExplainer } from '@/app/components/landing/QuorumExplainer';
import { McpTerminalHero } from '@/app/components/landing/McpTerminalHero';
import { TimeMachineSection } from '@/app/components/landing/TimeMachineSection';
import { getEntry, entries, FAQ_CATEGORIES } from '../entries';

const faqContents: Record<string, () => Promise<{ content: string }>> = {
  'what-is-quorumtrading': () => import('../content/what-is-quorumtrading'),
  'ai-agents': () => import('../content/ai-agents'),
  'technical-agent': () => import('../content/technical-agent'),
  'fundamental-agent': () => import('../content/fundamental-agent'),
  'sentiment-agent': () => import('../content/sentiment-agent'),
  'orchestrator-agent': () => import('../content/orchestrator-agent'),
  'consensus': () => import('../content/consensus'),
  'strategies': () => import('../content/strategies'),
  'backtesting': () => import('../content/backtesting'),
  'portfolio': () => import('../content/portfolio'),
  'benchmarks': () => import('../content/benchmarks'),
  'market-research': () => import('../content/market-research'),
  'mcp-integration': () => import('../content/mcp-integration'),
  'live-trading': () => import('../content/live-trading'),
  'eu-tax': () => import('../content/eu-tax'),
  'budget': () => import('../content/budget'),
};

export default function FaqEntryPage() {
  const params = useParams();
  const slug = params.slug as string;
  const entry = getEntry(slug);
  const [content, setContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadContent() {
      if (slug && faqContents[slug]) {
        try {
          const mod = await faqContents[slug]();
          setContent(mod.content);
        } catch (e) {
          console.error('Failed to load FAQ entry:', e);
          setContent(null);
        }
      }
      setLoading(false);
    }
    loadContent();
  }, [slug]);

  if (loading) {
    return (
      <LandingLayout>
        <Shell>
          <Loading>Loading…</Loading>
        </Shell>
      </LandingLayout>
    );
  }

  if (!entry || !content) {
    return (
      <LandingLayout>
        <Shell>
          <BackLink href="/faq">← Back to FAQ</BackLink>
          <Panel title="404 · Entry not found">
            <NotFoundBody>The requested FAQ entry does not exist.</NotFoundBody>
          </Panel>
        </Shell>
      </LandingLayout>
    );
  }

  const categoryMeta = FAQ_CATEGORIES[entry.category];
  const relatedEntries = entries
    .filter(e => e.category === entry.category && e.slug !== slug)
    .slice(0, 4);

  return (
    <LandingLayout>
      <Shell>
        <BackLink href="/faq">← Back to FAQ</BackLink>
        <PageHeader>
          <Crumb>
            <Link href="/faq">FAQ</Link>
            <CrumbSep>·</CrumbSep>
            <CrumbCurrent>{categoryMeta.label}</CrumbCurrent>
          </Crumb>
          <H1>{entry.title}</H1>
        </PageHeader>

        {slug === 'consensus' && <>
          <CouncilSection embedded />
          <QuorumExplainer embedded />
        </>}
        {slug === 'mcp-integration' && <McpTerminalHero embedded />}
        {slug === 'backtesting' && <TimeMachineSection embedded />}

        <Panel noBodyPadding title="">
          <Article>
            <MarkdownContainer>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
            </MarkdownContainer>
          </Article>
        </Panel>

        {relatedEntries.length > 0 && (
          <Panel title="Related questions" count={relatedEntries.length} noBodyPadding>
            <QaRows>
              {relatedEntries.map((related) => (
                <QaRow key={related.slug} href={`/faq/${related.slug}`}>
                  <QaTextCell>
                    <QaQ>{related.title}</QaQ>
                    <QaSummary>{related.summary}</QaSummary>
                  </QaTextCell>
                  <QaArrow>→</QaArrow>
                </QaRow>
              ))}
            </QaRows>
          </Panel>
        )}
      </Shell>
    </LandingLayout>
  );
}

// ─── Styled ──────────────────────────────────────────────────────────────

const Shell = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 1120px;
  margin: 0 auto;
  padding: 32px 24px 64px;
  width: 100%;
  min-width: 0;
`;

const BackLink = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgMuted};
  text-decoration: none;
  padding: 4px 0;
  transition: color 120ms ease;

  &:hover { color: ${p => p.theme.colors.foreground}; }
`;

const PageHeader = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0 0;
`;

const Crumb = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};

  & a {
    color: ${p => p.theme.colors.fgMuted};
    text-decoration: none;
    transition: color 120ms ease;
  }
  & a:hover { color: ${p => p.theme.colors.foreground}; }
`;

const CrumbSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const CrumbCurrent = styled.span`
  color: ${p => p.theme.colors.brand};
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 28px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
  line-height: 1.25;

  @media (max-width: 768px) { font-size: 24px; }
`;

const Article = styled.article`
  padding: 26px 28px 30px;
  max-width: 760px;
  margin: 0 auto;
`;

const QaRows = styled.div`
  display: flex;
  flex-direction: column;
`;

const QaRow = styled(Link)`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 13px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  text-decoration: none;
  color: inherit;
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.018); }
  &:hover .qa-arrow { color: ${p => p.theme.colors.brand}; }
`;

const QaTextCell = styled.span`
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
`;

const QaQ = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14.5px;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
`;

const QaSummary = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.45;
`;

const QaArrow = styled.span.attrs({ className: 'qa-arrow' })`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 15px;
  color: ${p => p.theme.colors.fgDimmed};
  transition: color 120ms ease;
  justify-self: end;
`;

const Loading = styled.div`
  text-align: center;
  padding: 48px 24px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  color: ${p => p.theme.colors.fgMuted};
`;

const NotFoundBody = styled.div`
  padding: 18px 16px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  color: ${p => p.theme.colors.fgMuted};
`;
