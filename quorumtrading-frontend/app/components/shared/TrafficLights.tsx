'use client';

import styled, { css } from 'styled-components';

// ===== Styled Components =====

const Container = styled.div`
  display: flex;
  gap: 7px;

  &:hover span {
    opacity: 1;
  }
`;

const DotButton = styled.button<{ $color: string; $interactive: boolean; $glow: boolean }>`
  position: relative;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: ${p => p.$color};
  border: none;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, box-shadow 0.15s ease;

  ${p => p.$interactive ? css`
    opacity: 0.85;
    cursor: pointer;

    &:hover {
      transform: scale(1.2);
    }
    &:focus-visible {
      outline: 2px solid ${p.theme.colors.terminalBlue};
      outline-offset: 2px;
    }
  ` : css`
    opacity: 0.35;
    cursor: default;
  `}

  ${p => p.$glow && css`
    box-shadow: 0 0 6px ${p.$color}aa;
    opacity: 1;
  `}
`;

const DotIcon = styled.span<{ $interactive: boolean }>`
  opacity: 0;
  transition: opacity 0.15s ease;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  line-height: 1;

  svg {
    width: 8px;
    height: 8px;
    stroke-width: 2.5;
    stroke-linecap: round;
    stroke-linejoin: round;
    fill: none;
  }

  ${p => !p.$interactive && css`
    display: none;
  `}
`;

// ===== Icons =====

function CloseIcon() {
  return (
    <svg viewBox="0 0 12 12">
      <line x1="3" y1="3" x2="9" y2="9" stroke="#4a0002" />
      <line x1="9" y1="3" x2="3" y2="9" stroke="#4a0002" />
    </svg>
  );
}

function MinimizeIcon() {
  return (
    <svg viewBox="0 0 12 12">
      <line x1="3" y1="6" x2="9" y2="6" stroke="#5a3a00" />
    </svg>
  );
}

function MaximizeIcon() {
  return (
    <svg viewBox="0 0 12 12">
      <polyline points="3,9 3,3 9,3" stroke="#0a4a00" fill="none" />
      <line x1="3" y1="3" x2="9" y2="9" stroke="#0a4a00" />
    </svg>
  );
}

// ===== Component =====

interface TrafficLightsProps {
  onClose?: () => void;
  onMinimize?: () => void;
  onMaximize?: () => void;
  maximized?: boolean;
}

export function TrafficLights({ onClose, onMinimize, onMaximize, maximized }: TrafficLightsProps) {
  return (
    <Container>
      <DotButton
        $color="#ff5f56"
        $interactive={!!onClose}
        $glow={false}
        onClick={onClose}
        aria-label="Close"
        tabIndex={onClose ? 0 : -1}
        type="button"
      >
        <DotIcon $interactive={!!onClose}><CloseIcon /></DotIcon>
      </DotButton>
      <DotButton
        $color="#ffbd2e"
        $interactive={!!onMinimize}
        $glow={false}
        onClick={onMinimize}
        aria-label="Minimize"
        tabIndex={onMinimize ? 0 : -1}
        type="button"
      >
        <DotIcon $interactive={!!onMinimize}><MinimizeIcon /></DotIcon>
      </DotButton>
      <DotButton
        $color="#27ca40"
        $interactive={!!onMaximize}
        $glow={!!maximized}
        onClick={onMaximize}
        aria-label="Maximize"
        tabIndex={onMaximize ? 0 : -1}
        type="button"
      >
        <DotIcon $interactive={!!onMaximize}><MaximizeIcon /></DotIcon>
      </DotButton>
    </Container>
  );
}
