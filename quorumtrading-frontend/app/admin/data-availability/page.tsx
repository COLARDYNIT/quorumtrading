'use client';

import { Suspense } from 'react';
import styled from 'styled-components';
import { DashboardLayout } from '@/app/components/layouts/DashboardLayout';
import { DataAvailabilityDashboardV2 } from '@/app/components/data-availability/v2';
import { TerminalSkeleton } from '@/app/components/terminal/TerminalSkeleton';

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.lg};
  padding: ${p => p.theme.spacing.lg};
  max-width: 1920px;
  margin: 0 auto;

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.sm};
    gap: ${p => p.theme.spacing.md};
  }
`;

export default function DataAvailabilityPage() {
  return (
    <DashboardLayout>
      <Suspense fallback={<Container><TerminalSkeleton count={10} /></Container>}>
        <Container>
          <DataAvailabilityDashboardV2 />
        </Container>
      </Suspense>
    </DashboardLayout>
  );
}
