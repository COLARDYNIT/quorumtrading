'use client';

import { use } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import styled from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { TerminalButton } from '@/app/components/terminal';
import { usePublicStrategy, useCloneStrategy } from '@/app/lib/api/hooks';
import { textGlow } from '@/app/styles/theme/effects';
import { fadeInUp, shadows, textShadows } from '@/app/styles/animations';

const Container = styled.div`
  max-width: 1400px;
  margin: 0 auto;
  padding: ${p => p.theme.spacing.lg};
  animation: ${fadeInUp} 0.5s ease-out;
  overflow: hidden;

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.sm};
  }
`;

const Header = styled.div`
  margin-bottom: ${p => p.theme.spacing['2xl']};
`;

const HeaderTop = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: ${p => p.theme.spacing.xl};
  margin-bottom: ${p => p.theme.spacing.lg};

  @media (max-width: 968px) {
    flex-direction: column;
  }

  @media (max-width: 480px) {
    gap: ${p => p.theme.spacing.sm};
  }
`;

const HeaderContent = styled.div`
  flex: 1;
`;

const Title = styled.h1`
  font-size: ${p => p.theme.typography.fontSize['2xl']};
  color: ${p => p.theme.colors.terminalGreen};
  margin: 0 0 ${p => p.theme.spacing.md} 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  line-height: 1.3;
  text-shadow: ${textShadows.primary};
  ${textGlow}
  word-break: break-word;

  @media (max-width: 768px) {
    font-size: ${p => p.theme.typography.fontSize.xl};
  }

  @media (max-width: 480px) {
    font-size: ${p => p.theme.typography.fontSize.lg};
  }
`;

const MetaRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    gap: ${p => p.theme.spacing.xs};
  }
`;

const MetaBadge = styled.span`
  display: inline-flex;
  align-items: center;
  gap: ${p => p.theme.spacing.xs};
  padding: ${p => p.theme.spacing.xs} ${p => p.theme.spacing.sm};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
`;

const MetaLabel = styled.span`
  color: ${p => p.theme.colors.textMuted};
`;

const MetaValue = styled.span`
  color: ${p => p.theme.colors.terminalBlue};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

const ActionButtons = styled.div`
  display: flex;
  gap: ${p => p.theme.spacing.sm};
  flex-shrink: 0;

  @media (max-width: 968px) {
    width: 100%;
    flex-wrap: wrap;
  }

  @media (max-width: 480px) {
    flex-shrink: 1;
  }
`;

const Section = styled.section`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  padding: ${p => p.theme.spacing.lg};
  margin-bottom: ${p => p.theme.spacing.lg};
  box-shadow: ${shadows.card};
  animation: ${fadeInUp} 0.5s ease-out 0.2s both;

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.md};
  }
`;

const SectionTitle = styled.h2`
  font-size: ${p => p.theme.typography.fontSize.xl};
  color: ${p => p.theme.colors.terminalBlue};
  margin: 0 0 ${p => p.theme.spacing.lg} 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  text-shadow: ${textShadows.cyan};

  @media (max-width: 480px) {
    font-size: ${p => p.theme.typography.fontSize.lg};
  }
`;

const CodeBlock = styled.pre`
  background: ${p => p.theme.colors.background};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.sm};
  padding: ${p => p.theme.spacing.md};
  overflow-x: auto;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.foreground};
  margin: 0;

  &::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }

  &::-webkit-scrollbar-track {
    background: ${p => p.theme.colors.background};
    border: 1px solid ${p => p.theme.colors.border};
  }

  &::-webkit-scrollbar-thumb {
    background: ${p => p.theme.colors.foreground}40;
  }

  &::-webkit-scrollbar-thumb:hover {
    background: ${p => p.theme.colors.foreground}60;
  }
`;

// YAML syntax highlighting (same as strategy detail page)
const YamlKey = styled.span`
  color: ${p => p.theme.colors.terminalBlue};
`;

const YamlValue = styled.span`
  color: ${p => p.theme.colors.terminalGreen};
`;

const YamlString = styled.span`
  color: ${p => p.theme.colors.warning};
`;

const YamlComment = styled.span`
  color: ${p => p.theme.colors.textMuted};
  font-style: italic;
`;

const LoadingMessage = styled.div`
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  text-align: center;
  padding: ${p => p.theme.spacing.xl};
`;

const ErrorMessage = styled.div`
  color: ${p => p.theme.colors.error};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  padding: ${p => p.theme.spacing.lg};
  border: 1px solid ${p => p.theme.colors.error};
  background: ${p => p.theme.colors.error}10;
  margin-bottom: ${p => p.theme.spacing.lg};
`;

function highlightYaml(content: string): React.ReactNode[] {
  return content.split('\n').map((line, i) => {
    if (line.trim().startsWith('#')) {
      return <YamlComment key={i}>{line}{'\n'}</YamlComment>;
    }

    const colonIndex = line.indexOf(':');
    if (colonIndex > -1 && !line.trim().startsWith('-')) {
      const key = line.substring(0, colonIndex + 1);
      const value = line.substring(colonIndex + 1);

      const trimmedValue = value.trim();
      if (trimmedValue.startsWith('"') || trimmedValue.startsWith("'")) {
        return <span key={i}><YamlKey>{key}</YamlKey><YamlString>{value}</YamlString>{'\n'}</span>;
      }

      return <span key={i}><YamlKey>{key}</YamlKey><YamlValue>{value}</YamlValue>{'\n'}</span>;
    }

    if (line.trim().startsWith('-')) {
      return <span key={i}><YamlValue>{line}</YamlValue>{'\n'}</span>;
    }

    return <span key={i}>{line}{'\n'}</span>;
  });
}

function formatDate(isoString: string | undefined): string {
  if (!isoString) return '';
  const date = new Date(isoString);
  return date.toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

interface PublicStrategyDetailPageProps {
  params: Promise<{
    id: string;
  }>;
}

export default function PublicStrategyDetailPage({ params }: PublicStrategyDetailPageProps) {
  const { id } = use(params);
  const router = useRouter();
  const { data: strategy, isLoading, error } = usePublicStrategy(id);
  const cloneMutation = useCloneStrategy();

  const handleClone = () => {
    cloneMutation.mutate(id, {
      onSuccess: () => {
        router.push('/strategies');
      },
    });
  };

  if (isLoading) {
    return (
      <DashboardLayout>
        <Container>
          <LoadingMessage>Loading strategy...</LoadingMessage>
        </Container>
      </DashboardLayout>
    );
  }

  if (error) {
    return (
      <DashboardLayout>
        <Container>
          <ErrorMessage>
            Failed to load strategy: {error instanceof Error ? error.message : 'Unknown error'}
          </ErrorMessage>
          <Link href="/strategies/public">
            <TerminalButton variant="secondary">Back to Library</TerminalButton>
          </Link>
        </Container>
      </DashboardLayout>
    );
  }

  if (!strategy) {
    return (
      <DashboardLayout>
        <Container>
          <ErrorMessage>Strategy not found</ErrorMessage>
          <Link href="/strategies/public">
            <TerminalButton variant="secondary">Back to Library</TerminalButton>
          </Link>
        </Container>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <Container>
        <Header>
          <HeaderTop>
            <HeaderContent>
              <Title>{strategy.name}</Title>
              <MetaRow>
                <MetaBadge>
                  <MetaLabel>Created</MetaLabel>
                  <MetaValue>{formatDate(strategy.createdAt)}</MetaValue>
                </MetaBadge>
                <MetaBadge>
                  <MetaLabel>Version</MetaLabel>
                  <MetaValue>{strategy.version}</MetaValue>
                </MetaBadge>
              </MetaRow>
            </HeaderContent>
            <ActionButtons>
              <TerminalButton
                variant="primary"
                onClick={handleClone}
                disabled={cloneMutation.isPending}
              >
                {cloneMutation.isPending ? 'Cloning...' : 'Use This Strategy'}
              </TerminalButton>
              <Link href="/strategies/public">
                <TerminalButton variant="ghost">Back to Library</TerminalButton>
              </Link>
            </ActionButtons>
          </HeaderTop>
        </Header>

        <Section>
          <SectionTitle>Strategy Configuration</SectionTitle>
          <CodeBlock>
            {highlightYaml(strategy.markdownContent)}
          </CodeBlock>
        </Section>
      </Container>
    </DashboardLayout>
  );
}
