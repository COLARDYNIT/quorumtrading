'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { LandingLayout } from '@/app/components/layouts/LandingLayout';
import { Panel } from '@/app/components/shared/v2';
import { entries, FAQ_CATEGORIES, type FaqCategory } from './entries';

const categories: FaqCategory[] = ['core', 'trading', 'platform'];

export default function FaqPage() {
  const total = entries.length;
  return (
    <LandingLayout>
      <Shell>
        <PageHeader>
          <HeaderTopRow>
            <HeaderLeft>
              <Crumb>
                <Link href="/">Home</Link>
                <CrumbSep>/</CrumbSep>
                <CrumbCurrent>FAQ</CrumbCurrent>
              </Crumb>
              <H1>Frequently asked questions</H1>
              <Desc>
                Short answers to the questions that came up most while onboarding the first traders.
                If yours isn&apos;t here, the in-app assistant reads from the same source.
              </Desc>
              <Meta>
                <b>{total}</b> question{total === 1 ? '' : 's'} across <b>{categories.length}</b> topic{categories.length === 1 ? '' : 's'}
              </Meta>
            </HeaderLeft>
          </HeaderTopRow>
        </PageHeader>

        {categories.map((category) => {
          const items = entries.filter((e) => e.category === category);
          if (items.length === 0) return null;
          const meta = FAQ_CATEGORIES[category];
          return (
            <Panel
              key={category}
              title={meta.label}
              count={items.length}
              headerActions={<HeaderNote>{meta.description}</HeaderNote>}
              noBodyPadding
            >
              <QaRows>
                {items.map((entry) => (
                  <QaRow key={entry.slug} href={`/faq/${entry.slug}`}>
                    <QaTextCell>
                      <QaQ>{entry.title}</QaQ>
                      <QaSummary>{entry.summary}</QaSummary>
                    </QaTextCell>
                    <QaArrow>→</QaArrow>
                  </QaRow>
                ))}
              </QaRows>
            </Panel>
          );
        })}
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

const PageHeader = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0 0;
`;

const HeaderTopRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 14px;
  flex-wrap: wrap;
`;

const HeaderLeft = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
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
  font-size: 24px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const Desc = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
  max-width: 78ch;
`;

const Meta = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgDimmed};

  & b {
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
  }
`;

const HeaderNote = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
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
