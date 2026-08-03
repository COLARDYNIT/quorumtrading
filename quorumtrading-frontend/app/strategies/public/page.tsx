'use client';

import { DashboardLayout } from '../../components/layouts/DashboardLayout';
import { LibraryView } from './components/v2';

export default function PublicStrategiesPage() {
  return (
    <DashboardLayout>
      <LibraryView />
    </DashboardLayout>
  );
}
