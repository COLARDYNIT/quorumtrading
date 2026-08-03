'use client';

import { use, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import styled, { keyframes } from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { useChallenge, useChallengeChartData, useSimulations } from '@/app/lib/api/hooks';
import {
  ChallengeDetailHeader,
  ChallengeChartPanel,
  ChallengeRulesPanel,
  ChallengePreRoll,
  LeaderboardRail,
  JoinChallengeModal,
} from './components/v2';

interface PageProps {
  params: Promise<{ id: string }>;
}

export default function ChallengeDetailPage({ params }: PageProps) {
  const { id } = use(params);
  const router = useRouter();
  const searchParams = useSearchParams();

  const { data: challenge, isLoading: chLoading, error: chError } = useChallenge(id);
  const hasActiveEntries =
    challenge?.entries?.some((e) => e.status === 'SIMULATING' || e.status === 'LIVE') ?? false;
  const { data: chart } = useChallengeChartData(
    challenge?.id ?? id,
    challenge?.status !== 'UPCOMING',
    hasActiveEntries,
  );
  const { data: userSimulations } = useSimulations();

  const yourSimIds = useMemo(() => {
    const s = new Set<string>();
    userSimulations?.forEach((sim) => s.add(sim.id));
    return s;
  }, [userSimulations]);

  const yourEntry = useMemo(
    () => challenge?.entries.find((e) => yourSimIds.has(e.simulationId)) ?? null,
    [challenge, yourSimIds],
  );

  const [joinOpen, setJoinOpen] = useState(false);
  const [deepLinkStrategyId, setDeepLinkStrategyId] = useState<string | null>(null);

  // Auto-open on ?joinWith=<strategyId> when the challenge is ACTIVE + accepting.
  const joinWith = searchParams.get('joinWith');
  useEffect(() => {
    if (
      joinWith &&
      challenge &&
      challenge.status === 'ACTIVE' &&
      challenge.acceptingEntries
    ) {
      setDeepLinkStrategyId(joinWith);
      setJoinOpen(true);
      // Strip the param so a refresh doesn't reopen the modal.
      const params = new URLSearchParams(searchParams.toString());
      params.delete('joinWith');
      router.replace(`/challenges/${id}${params.toString() ? '?' + params.toString() : ''}`, { scroll: false });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [joinWith, challenge?.status, challenge?.acceptingEntries]);

  if (chError) {
    return (
      <DashboardLayout>
        <Page>
          <ErrorBox>
            Failed to load challenge — {chError instanceof Error ? chError.message : 'unknown error'}.
          </ErrorBox>
        </Page>
      </DashboardLayout>
    );
  }

  if (chLoading || !challenge) {
    return (
      <DashboardLayout>
        <Page>
          <LoadingSkeleton />
        </Page>
      </DashboardLayout>
    );
  }

  const onJoin = () => setJoinOpen(true);
  const onCloseJoin = () => {
    setJoinOpen(false);
    setDeepLinkStrategyId(null);
  };

  return (
    <DashboardLayout>
      <Page>
        <ChallengeDetailHeader ch={challenge} yourRank={yourEntry?.rank ?? null} />

        <Body>
          <Left>
            {challenge.status === 'UPCOMING' ? (
              <ChallengePreRoll ch={challenge} />
            ) : chart ? (
              <ChallengeChartPanel ch={challenge} chart={chart} />
            ) : (
              <ChartLoading>Loading equity curves…</ChartLoading>
            )}
            <ChallengeRulesPanel ch={challenge} />
            {challenge.status === 'COMPLETED' && challenge.strategiesPublic && (
              <RevealCallout>
                <strong>Open challenge · strategies revealed</strong>
                <p>
                  Every entrant agreed their <code>strategy.md</code> becomes public on
                  completion. Click any rank above to open that sim and read the strategy in
                  full — markdown + YAML.
                </p>
              </RevealCallout>
            )}
          </Left>
          <Right>
            <LeaderboardRail
              ch={challenge}
              chart={chart}
              yourSimIds={yourSimIds}
              onJoin={onJoin}
            />
          </Right>
        </Body>

        <JoinChallengeModal
          ch={challenge}
          open={joinOpen}
          initialStrategyId={deepLinkStrategyId}
          hasExistingEntry={!!yourEntry}
          onClose={onCloseJoin}
        />
      </Page>
    </DashboardLayout>
  );
}

// ─── Loading skeleton ───────────────────────────────────────────────────────

function LoadingSkeleton() {
  return (
    <>
      <SkelHeader>
        <SkelLine $w={360} $h={26} />
        <SkelLine $w={520} $h={11} $mt={10} />
        <SkelLine $w={420} $h={11} $mt={6} />
      </SkelHeader>
      <Body>
        <Left>
          <SkelChart>
            <SkelLine $w={180} $h={12} />
            <SkelLine $w={140} $h={11} $mt={6} />
            <SkelBlock $h={300} />
          </SkelChart>
          <SkelBlock $h={260} />
        </Left>
        <Right>
          <SkelBlock $h={96} />
          <SkelBlock $h={300} />
          <SkelBlock $h={110} />
        </Right>
      </Body>
    </>
  );
}

// ─── Styles ─────────────────────────────────────────────────────────────────

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0 24px;
  min-width: 0;
  animation: ${keyframes`from{opacity:0; transform: translateY(4px);} to{opacity:1; transform:none;}`} 200ms ease-out;
`;

const Body = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.75fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: flex-start;

  @media (max-width: 968px) {
    grid-template-columns: 1fr;
  }
`;

const Left = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
`;

const Right = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
`;

const ChartLoading = styled.div`
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => p.theme.colors.surface};
  border-radius: 4px;
  padding: 40px;
  text-align: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13px;
  color: ${p => p.theme.colors.fgDimmed};
`;

const RevealCallout = styled.div`
  border: 1px solid rgba(0, 217, 255, 0.4);
  background:
    linear-gradient(135deg, rgba(0, 217, 255, 0.04), transparent 60%),
    ${p => p.theme.colors.surface};
  border-radius: 4px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  & strong {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 14px;
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    color: ${p => p.theme.colors.info};
  }
  & p {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 13.5px;
    line-height: 1.55;
    color: ${p => p.theme.colors.fgMuted};
    margin: 0;
  }
  & code {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    color: ${p => p.theme.colors.foreground};
    background: transparent;
  }
`;

const ErrorBox = styled.div`
  border: 1px solid rgba(255, 51, 102, 0.4);
  background: rgba(255, 51, 102, 0.06);
  color: ${p => p.theme.colors.negative};
  padding: 14px 16px;
  border-radius: 4px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 13.5px;
`;

// ─── Skeleton ───────────────────────────────────────────────────────────────

const shimmer = keyframes`
  0% { background-position: -200px 0; }
  100% { background-position: 200px 0; }
`;

const SkelLine = styled.span<{ $w: number; $h: number; $mt?: number }>`
  display: block;
  width: ${p => p.$w}px;
  height: ${p => p.$h}px;
  margin-top: ${p => p.$mt ?? 0}px;
  background: linear-gradient(
    90deg,
    ${p => p.theme.colors.borderSubtle} 0%,
    rgba(255, 255, 255, 0.04) 50%,
    ${p => p.theme.colors.borderSubtle} 100%
  );
  background-size: 200px 100%;
  border-radius: 1px;
  animation: ${shimmer} 1.6s linear infinite;
`;

const SkelBlock = styled.span<{ $h: number }>`
  display: block;
  width: 100%;
  height: ${p => p.$h}px;
  background: linear-gradient(
    90deg,
    ${p => p.theme.colors.borderSubtle} 0%,
    rgba(255, 255, 255, 0.04) 50%,
    ${p => p.theme.colors.borderSubtle} 100%
  );
  background-size: 200px 100%;
  border-radius: 4px;
  animation: ${shimmer} 1.6s linear infinite;
`;

const SkelHeader = styled.div`
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-bottom: 14px;
  border-bottom: 1px solid ${p => p.theme.colors.borderSubtle};
`;

const SkelChart = styled.div`
  border: 1px solid ${p => p.theme.colors.borderSubtle};
  background: ${p => p.theme.colors.surface};
  border-radius: 4px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
`;
