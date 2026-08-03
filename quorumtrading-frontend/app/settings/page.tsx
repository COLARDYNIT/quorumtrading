'use client';

import { useState, useMemo } from 'react';
import styled from 'styled-components';
import Link from 'next/link';
import { DashboardLayout } from '../components/layouts/DashboardLayout';
import { SectionTitle } from '../components/shared';
import { TerminalPane } from '../components/terminal/TerminalPane';
import { TerminalText } from '../components/terminal/TerminalText';
import { fadeInUp, shadows } from '@/app/styles/animations';

const Container = styled.div`
  max-width: 1400px;
  margin: 0 auto;
  padding: ${p => p.theme.spacing.xl};

  @media (max-width: 480px) {
    padding: ${p => p.theme.spacing.sm};
  }
`;

const Header = styled.div`
  margin-bottom: ${p => p.theme.spacing.lg};
`;

const SearchBar = styled.input`
  width: 100%;
  padding: ${p => p.theme.spacing.md};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  color: ${p => p.theme.colors.text};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  margin-bottom: ${p => p.theme.spacing.lg};
  transition: all 0.2s ease;

  &::placeholder {
    color: ${p => p.theme.colors.textMuted};
  }

  &:focus {
    outline: none;
    border-color: ${p => p.theme.colors.terminalGreen};
    box-shadow: 0 0 0 2px ${p => p.theme.colors.terminalGreen}20;
  }
`;

const CategorySection = styled.div`
  margin-bottom: ${p => p.theme.spacing.xl};

  &:last-child {
    margin-bottom: 0;
  }
`;

const CategoryHeader = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  margin-bottom: ${p => p.theme.spacing.md};
  padding-bottom: ${p => p.theme.spacing.xs};
  border-bottom: 1px solid ${p => p.theme.colors.border};
`;

const CategoryIcon = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xl};
  color: ${p => p.theme.colors.terminalGreen};
`;

const CategoryTitle = styled.h2`
  color: ${p => p.theme.colors.terminalGreen};
  font-size: ${p => p.theme.typography.fontSize.lg};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin: 0;
`;

const CategoryCount = styled.span`
  font-size: ${p => p.theme.typography.fontSize.xs};
  color: ${p => p.theme.colors.textMuted};
  font-weight: ${p => p.theme.typography.fontWeight.normal};
  margin-left: auto;
`;

const SettingsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: ${p => p.theme.spacing.md};

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
`;

const SettingsCard = styled(Link)`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
  padding: ${p => p.theme.spacing.lg};
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  border-radius: ${p => p.theme.borderRadius.md};
  text-decoration: none;
  animation: ${fadeInUp} 0.4s ease-out both;
  transition: all 0.2s ease;
  position: relative;

  &:hover {
    border-color: ${p => p.theme.colors.terminalGreen};
    ${shadows.cardHover}
    transform: translateY(-2px);
  }
`;

const CardHeader = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${p => p.theme.spacing.sm};
`;

const CardIcon = styled.div`
  font-size: 20px;
  color: ${p => p.theme.colors.terminalGreen};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  min-width: 24px;
`;

const CardContent = styled.div`
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const CardTitle = styled.h3`
  color: ${p => p.theme.colors.text};
  font-size: ${p => p.theme.typography.fontSize.base};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
`;

const CardDescription = styled(TerminalText)`
  color: ${p => p.theme.colors.textMuted};
  font-size: ${p => p.theme.typography.fontSize.xs};
  line-height: 1.5;
`;

const Badge = styled.span`
  position: absolute;
  top: ${p => p.theme.spacing.sm};
  right: ${p => p.theme.spacing.sm};
  padding: 2px 8px;
  background: ${p => p.theme.colors.terminalGreen}20;
  color: ${p => p.theme.colors.terminalGreen};
  border: 1px solid ${p => p.theme.colors.terminalGreen};
  border-radius: ${p => p.theme.borderRadius.sm};
  font-size: 9px;
  font-weight: ${p => p.theme.typography.fontWeight.bold};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const EmptyState = styled.div`
  padding: ${p => p.theme.spacing.xl};
  text-align: center;
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
`;

interface SettingItem {
  id: string;
  href: string;
  icon: string;
  title: string;
  description: string;
  category: string;
  badge?: string;
  priority: number; // For sorting: 1 = highest priority
}

// Settings data with categorization and priority
const settingsData: SettingItem[] = [
  // Trading & Accounts (highest priority)
  {
    id: 'broker-accounts',
    href: '/settings/broker-accounts',
    icon: '[$]',
    title: 'Broker Accounts',
    description: 'Connect and manage your trading accounts. Configure mock accounts for backtesting or connect Alpaca for paper and live trading.',
    category: 'Trading & Accounts',
    priority: 1,
  },
  {
    id: 'budget',
    href: '/settings/budget',
    icon: '[+]',
    title: 'AI Budget',
    description: 'Manage your AI spending limit and add funds. View remaining budget and track monthly usage.',
    category: 'Trading & Accounts',
    priority: 2,
  },
  {
    id: 'api-tokens',
    href: '/settings/api-tokens',
    icon: '[#]',
    title: 'API Tokens',
    description: 'Generate API tokens for programmatic access. Use tokens with Claude Code, MCP clients, or custom integrations.',
    category: 'Security & Privacy',
    priority: 5,
  },
  {
    id: 'account',
    href: '/settings/account',
    icon: '[!]',
    title: 'Account',
    description: 'Manage your account. Delete your account and all associated data (GDPR right to erasure).',
    category: 'Security & Privacy',
    priority: 6,
  },
  // AI Configuration (medium-high priority)
  {
    id: 'config',
    href: '/settings/config',
    icon: '[*]',
    title: 'AI Models',
    description: 'Configure preferred AI models for each trading context. Select models for market research, backtesting, and live trading.',
    category: 'AI Configuration',
    badge: 'BETA',
    priority: 3,
  },
  {
    id: 'usage',
    href: '/settings/usage',
    icon: '[%]',
    title: 'AI Usage',
    description: 'Track your AI usage costs including consensus decisions and market research. View daily, weekly, and monthly breakdowns.',
    category: 'AI Configuration',
    priority: 4,
  },
];

// Group settings by category
interface SettingsCategory {
  name: string;
  icon: string;
  settings: SettingItem[];
}

export default function SettingsPage() {
  const [searchQuery, setSearchQuery] = useState('');

  // Filter and group settings
  const categorizedSettings = useMemo(() => {
    // Filter by search query
    const filtered = settingsData.filter(setting => {
      const query = searchQuery.toLowerCase();
      return (
        setting.title.toLowerCase().includes(query) ||
        setting.description.toLowerCase().includes(query) ||
        setting.category.toLowerCase().includes(query)
      );
    });

    // Group by category
    const grouped = filtered.reduce((acc, setting) => {
      if (!acc[setting.category]) {
        acc[setting.category] = [];
      }
      acc[setting.category].push(setting);
      return acc;
    }, {} as Record<string, SettingItem[]>);

    // Sort within each category by priority
    Object.keys(grouped).forEach(category => {
      grouped[category].sort((a, b) => a.priority - b.priority);
    });

    // Convert to array and define category order
    const categoryOrder = [
      'Trading & Accounts',
      'AI Configuration',
      'System & Display',
      'Security & Privacy',
    ];

    const categoryIcons: Record<string, string> = {
      'Trading & Accounts': '💰',
      'AI Configuration': '🤖',
      'System & Display': '⚙️',
      'Security & Privacy': '🔒',
    };

    return categoryOrder
      .filter(cat => grouped[cat] && grouped[cat].length > 0)
      .map(cat => ({
        name: cat,
        icon: categoryIcons[cat] || '📋',
        settings: grouped[cat],
      }));
  }, [searchQuery]);

  const totalSettings = categorizedSettings.reduce(
    (sum, cat) => sum + cat.settings.length,
    0
  );

  return (
    <DashboardLayout>
      <Container>
        <Header>
          <SectionTitle $size="lg">Settings</SectionTitle>
        </Header>

        <TerminalPane>
          <SearchBar
            type="text"
            placeholder="Search settings... (e.g., broker, AI, budget)"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />

          {categorizedSettings.length === 0 ? (
            <EmptyState>
              <TerminalText size="sm" variant="muted">
                No settings found matching &quot;{searchQuery}&quot;
              </TerminalText>
            </EmptyState>
          ) : (
            categorizedSettings.map((category, catIndex) => (
              <CategorySection key={category.name} style={{ animationDelay: `${catIndex * 0.1}s` }}>
                <CategoryHeader>
                  <CategoryIcon>{category.icon}</CategoryIcon>
                  <CategoryTitle>{category.name}</CategoryTitle>
                  <CategoryCount>
                    {category.settings.length} {category.settings.length === 1 ? 'setting' : 'settings'}
                  </CategoryCount>
                </CategoryHeader>
                <SettingsGrid>
                  {category.settings.map((setting, settingIndex) => (
                    <SettingsCard
                      key={setting.id}
                      href={setting.href}
                      style={{ animationDelay: `${(catIndex * 0.1) + (settingIndex * 0.05)}s` }}
                    >
                      {setting.badge && <Badge>{setting.badge}</Badge>}
                      <CardHeader>
                        <CardIcon>{setting.icon}</CardIcon>
                        <CardContent>
                          <CardTitle>{setting.title}</CardTitle>
                          <CardDescription>{setting.description}</CardDescription>
                        </CardContent>
                      </CardHeader>
                    </SettingsCard>
                  ))}
                </SettingsGrid>
              </CategorySection>
            ))
          )}
        </TerminalPane>
      </Container>
    </DashboardLayout>
  );
}
