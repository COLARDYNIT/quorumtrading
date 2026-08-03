'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { LandingLayout } from '@/app/components/layouts/LandingLayout';
import { Panel } from '@/app/components/shared/v2';
import { hueColor, tagBase, type LabelHue } from '@/app/styles/labels';
import { posts } from './posts';

type TagKind = 'feature' | 'fix' | 'perf' | 'break' | 'neutral';

function tagKind(tag: string): TagKind {
  const t = tag.toLowerCase();
  if (t === 'feature') return 'feature';
  if (t === 'fix') return 'fix';
  if (t === 'perf' || t === 'ai' || t === 'ui') return 'perf';
  if (t === 'break' || t === 'breaking') return 'break';
  return 'neutral';
}

export default function BlogPage() {
  return (
    <LandingLayout>
      <Shell>
        <PageHeader>
          <Crumb>
            <Link href="/">Home</Link>
            <CrumbSep>/</CrumbSep>
            <CrumbCurrent>Changelog</CrumbCurrent>
          </Crumb>
          <H1>Changelog</H1>
          <Desc>
            What changed and when. Engineering-honest. No marketing spin — fixes are flagged, breaking
            changes get the loud chip.
          </Desc>
        </PageHeader>

        <Panel title="Changelog" count={posts.length} noBodyPadding>
          {posts.length === 0 ? (
            <EmptyState>
              <EmptyIcon>·</EmptyIcon>
              <EmptyTitle>Nothing shipped yet.</EmptyTitle>
              <EmptyBody>
                Releases land here. Subscribe via RSS or follow updates inside the app.
              </EmptyBody>
            </EmptyState>
          ) : (
            <Rows>
              <HeaderRowBar>
                <span>Date</span>
                <span>Release</span>
                <RightAligned>Tags</RightAligned>
              </HeaderRowBar>
              {posts.map((post) => (
                <RowLink key={post.slug} href={`/blog/${post.slug}`}>
                  <DateCell>{post.date}</DateCell>
                  <TitleCell>
                    <Ttl>{post.title}</Ttl>
                    <Summary>{post.summary}</Summary>
                  </TitleCell>
                  <Tags>
                    {post.tags.map((tag) => (
                      <TagChip key={tag} $kind={tagKind(tag)}>{tag.toLowerCase()}</TagChip>
                    ))}
                  </Tags>
                </RowLink>
              ))}
            </Rows>
          )}
        </Panel>
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

const Rows = styled.div`
  display: flex;
  flex-direction: column;
`;

const ROW_COLS = '110px minmax(0, 1fr) 220px';

const HeaderRowBar = styled.div`
  display: grid;
  grid-template-columns: ${ROW_COLS};
  gap: 14px;
  align-items: center;
  padding: 10px 18px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 11px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  background: ${p => p.theme.colors.surface};
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};

  @media (max-width: 768px) {
    display: none;
  }
`;

const RightAligned = styled.span`
  text-align: right;
`;

const RowLink = styled(Link)`
  display: grid;
  grid-template-columns: ${ROW_COLS};
  gap: 14px;
  align-items: center;
  padding: 13px 18px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
  text-decoration: none;
  color: inherit;
  transition: background 120ms ease;

  &:last-child { border-bottom: none; }
  &:hover { background: rgba(255,255,255,0.018); }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: 8px;
    padding: 14px 18px;
  }
`;

const DateCell = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-variant-numeric: tabular-nums;
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const TitleCell = styled.span`
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
`;

const Ttl = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.medium};
  line-height: 1.35;
`;

const Summary = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 13.5px;
  color: ${p => p.theme.colors.fgMuted};
  line-height: 1.5;
`;

const Tags = styled.span`
  display: inline-flex;
  gap: 5px;
  justify-self: end;
  flex-wrap: wrap;

  @media (max-width: 768px) {
    justify-self: start;
  }
`;

const TAG_HUE: Record<TagKind, LabelHue> = {
  feature: 'brand',
  fix: 'amber',
  perf: 'cyan',
  break: 'pink',
  neutral: 'dim',
};

const TagChip = styled.span<{ $kind: TagKind }>`
  ${tagBase}
  color: ${p => hueColor[TAG_HUE[p.$kind]](p.theme.colors)};
`;

const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 32px;
  gap: 10px;
  color: ${p => p.theme.colors.fgMuted};
  font-size: 14px;
  line-height: 1.55;
  max-width: 56ch;
  margin: 0 auto;
`;

const EmptyIcon = styled.div`
  width: 40px;
  height: 40px;
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: ${p => p.theme.colors.fgDimmed};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 17px;
`;

const EmptyTitle = styled.h3`
  color: ${p => p.theme.colors.fgStrong};
  font-size: 15px;
  margin: 4px 0 0;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const EmptyBody = styled.p`
  margin: 0;
`;
