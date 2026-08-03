'use client';

import styled from 'styled-components';
import { useAuth } from '../hooks/useAuth';
import { UserAvatar } from './UserAvatar';

const Container = styled.div`
  display: flex;
  align-items: center;
  gap: 0.75rem;
`;

const AuthButton = styled.button`
  padding: 0.5rem 1rem;
  border: 1px solid var(--cds-border-strong-01);
  border-radius: 4px;
  background: transparent;
  color: var(--cds-text-primary);
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.15s ease;

  &:hover {
    background: var(--cds-layer-hover-01);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const PrimaryButton = styled(AuthButton)`
  background: var(--cds-button-primary);
  border-color: var(--cds-button-primary);
  color: var(--cds-text-on-color);

  &:hover:not(:disabled) {
    background: var(--cds-button-primary-hover);
  }
`;

const UserInfo = styled.div`
  display: flex;
  align-items: center;
  gap: 0.5rem;
`;

const UserName = styled.span`
  font-size: 0.875rem;
  color: var(--cds-text-primary);
`;

const LogoutButton = styled(AuthButton)`
  padding: 0.375rem 0.75rem;
  font-size: 0.75rem;
`;

export function AuthButtons() {
  const { user, isLoading, isAuthenticated, providers, providersLoading, login, logout } = useAuth();

  if (isLoading || providersLoading) {
    return null;  // Don't show anything while loading
  }

  if (isAuthenticated && user) {
    return (
      <Container>
        <UserInfo>
          <UserAvatar user={user} size="small" />
          <UserName>{user.displayName || user.email}</UserName>
        </UserInfo>
        <LogoutButton onClick={() => logout()}>Sign out</LogoutButton>
      </Container>
    );
  }

  if (providers.length === 0) {
    return null;  // No providers available
  }

  // Show the first provider as primary button
  const primaryProvider = providers[0];

  return (
    <Container>
      <PrimaryButton onClick={() => login(primaryProvider.id)}>
        Sign in with {primaryProvider.name}
      </PrimaryButton>
    </Container>
  );
}
