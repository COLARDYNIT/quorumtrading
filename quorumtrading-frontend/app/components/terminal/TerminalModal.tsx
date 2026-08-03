'use client';

import { useEffect } from 'react';
import styled from 'styled-components';
import { TerminalText } from './TerminalText';
import { TerminalButton } from './TerminalButton';

export interface TerminalModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  width?: 'sm' | 'md' | 'lg' | 'xl';
}

const Overlay = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'isOpen',
})<{ isOpen: boolean }>`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.85);
  display: ${({ isOpen }) => (isOpen ? 'flex' : 'none')};
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
`;

const ModalContainer = styled.div.withConfig({
  shouldForwardProp: (prop) => prop !== 'width',
})<{ width: 'sm' | 'md' | 'lg' | 'xl' }>`
  background: ${({ theme }) => theme.colors.background};
  border: 2px solid ${({ theme }) => theme.colors.foreground};
  box-shadow: 0 0 20px ${({ theme }) => theme.colors.foreground}60;
  max-height: 90vh;
  overflow: auto;

  width: ${({ width }) => {
    switch (width) {
      case 'sm':
        return '400px';
      case 'lg':
        return '800px';
      case 'xl':
        return '1000px';
      default:
        return '600px';
    }
  }};

  @media (max-width: 768px) {
    width: 90vw;
  }
`;

const ModalHeader = styled.div`
  padding: ${({ theme }) => theme.spacing.md};
  border-bottom: 1px solid ${({ theme }) => theme.colors.foreground}40;
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

const ModalTitle = styled(TerminalText).attrs({ size: 'lg', weight: 'bold' })`
  text-transform: uppercase;
`;

const CloseButton = styled.button`
  background: none;
  border: none;
  color: ${({ theme }) => theme.colors.foreground};
  font-size: ${({ theme }) => theme.typography.fontSize['3xl']};
  cursor: pointer;
  padding: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;

  &:hover {
    color: ${({ theme }) => theme.colors.error};
    text-shadow: 0 0 8px ${({ theme }) => theme.colors.error};
  }
`;

const ModalBody = styled.div`
  padding: ${({ theme }) => theme.spacing.md};
`;

const ModalFooter = styled.div`
  padding: ${({ theme }) => theme.spacing.md};
  border-top: 1px solid ${({ theme }) => theme.colors.foreground}40;
  display: flex;
  gap: ${({ theme }) => theme.spacing.sm};
  justify-content: flex-end;
`;

export function TerminalModal({
  isOpen,
  onClose,
  title,
  children,
  footer,
  width = 'md',
}: TerminalModalProps) {
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };

    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      document.body.style.overflow = 'hidden';
    }

    return () => {
      document.removeEventListener('keydown', handleEscape);
      document.body.style.overflow = 'unset';
    };
  }, [isOpen, onClose]);

  const handleOverlayClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  if (!isOpen) {
    return null;
  }

  return (
    <Overlay isOpen={isOpen} onClick={handleOverlayClick}>
      <ModalContainer width={width}>
        <ModalHeader>
          <ModalTitle>{title}</ModalTitle>
          <CloseButton onClick={onClose} aria-label="Close modal">
            ×
          </CloseButton>
        </ModalHeader>
        <ModalBody>{children}</ModalBody>
        {footer && <ModalFooter>{footer}</ModalFooter>}
      </ModalContainer>
    </Overlay>
  );
}
