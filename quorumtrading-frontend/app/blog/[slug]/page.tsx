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
import { hueColor, tagBase, type LabelHue } from '@/app/styles/labels';
import { getPost } from '../posts';

type TagKind = 'feature' | 'fix' | 'perf' | 'break' | 'neutral';

function tagKind(tag: string): TagKind {
  const t = tag.toLowerCase();
  if (t === 'feature') return 'feature';
  if (t === 'fix') return 'fix';
  if (t === 'perf' || t === 'ai' || t === 'ui') return 'perf';
  if (t === 'break' || t === 'breaking') return 'break';
  return 'neutral';
}

const postContents: Record<string, () => Promise<{ content: string }>> = {
  '2026-06-09-recurring-investment-plans': () => import('../posts/2026-06-09-recurring-investment-plans'),
  '2026-03-25-challenges-and-new-dashboard': () => import('../posts/2026-03-25-challenges-and-new-dashboard'),
  '2026-03-16-what-the-agents-see': () => import('../posts/2026-03-16-what-the-agents-see'),
  '2026-02-22-three-weeks-of-shipping': () => import('../posts/2026-02-22-three-weeks-of-shipping'),
  '2026-02-22-etfs-you-can-trade': () => import('../posts/2026-02-22-etfs-you-can-trade'),
  '2026-02-03-meet-the-assistant': () => import('../posts/2026-02-03-meet-the-assistant'),
  '2025-01-16-technical-indicators': () => import('../posts/2025-01-16-technical-indicators'),
  '2025-01-13-ai-model-config': () => import('../posts/2025-01-13-ai-model-config'),
};

export default function BlogPostPage() {
  const params = useParams();
  const slug = params.slug as string;
  const post = getPost(slug);
  const [content, setContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadContent() {
      if (slug && postContents[slug]) {
        try {
          const mod = await postContents[slug]();
          setContent(mod.content);
        } catch (e) {
          console.error('Failed to load post:', e);
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

  if (!post || !content) {
    return (
      <LandingLayout>
        <Shell>
          <BackLink href="/blog">← Back to changelog</BackLink>
          <Panel title="404 · Post not found">
            <NotFoundBody>The requested post does not exist.</NotFoundBody>
          </Panel>
        </Shell>
      </LandingLayout>
    );
  }

  return (
    <LandingLayout>
      <Shell>
        <BackLink href="/blog">← Back to changelog</BackLink>
        <PageHeader>
          <Crumb>
            <Link href="/blog">Changelog</Link>
            <CrumbSep>·</CrumbSep>
            <MonoDate>{post.date}</MonoDate>
          </Crumb>
          <H1>{post.title}</H1>
          <MetaRow>
            {post.tags.map((tag) => (
              <TagChip key={tag} $kind={tagKind(tag)}>{tag.toLowerCase()}</TagChip>
            ))}
          </MetaRow>
        </PageHeader>

        <Panel noBodyPadding title="">
          <Article>
            <MarkdownContainer>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
            </MarkdownContainer>
          </Article>
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

const MonoDate = styled.span`
  color: ${p => p.theme.colors.foreground};
  font-variant-numeric: tabular-nums;
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

const MetaRow = styled.div`
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 2px;
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

const Article = styled.article`
  padding: 26px 28px 30px;
  max-width: 760px;
  margin: 0 auto;
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
