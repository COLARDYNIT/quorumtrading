'use client';

import styled from 'styled-components';
import { User } from '../types/user';

interface UserAvatarProps {
  user: User;
  size?: 'small' | 'medium' | 'large';
}

const sizeMap = {
  small: '24px',
  medium: '32px',
  large: '48px',
};

const AvatarContainer = styled.div<{ $size: string }>`
  width: ${props => props.$size};
  height: ${props => props.$size};
  border-radius: 50%;
  overflow: hidden;
  background: var(--cds-layer-accent-01);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: calc(${props => props.$size} * 0.4);
  color: var(--cds-text-primary);
  border: 1px solid var(--cds-border-subtle-00);
`;

const AvatarImage = styled.img`
  width: 100%;
  height: 100%;
  object-fit: cover;
`;

const Initials = styled.span`
  text-transform: uppercase;
  font-weight: 600;
`;

function getInitials(user: User): string {
  if (user.displayName) {
    return user.displayName.split(' ').map(n => n[0]).join('').slice(0, 2);
  }
  return user.email[0];
}

export function UserAvatar({ user, size = 'medium' }: UserAvatarProps) {
  const sizeValue = sizeMap[size];

  if (user.avatarUrl) {
    return (
      <AvatarContainer $size={sizeValue}>
        <AvatarImage src={user.avatarUrl} alt={user.displayName || user.email} />
      </AvatarContainer>
    );
  }

  return (
    <AvatarContainer $size={sizeValue}>
      <Initials>{getInitials(user)}</Initials>
    </AvatarContainer>
  );
}
