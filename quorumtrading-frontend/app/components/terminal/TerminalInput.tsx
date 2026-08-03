'use client';

import { useId } from 'react';
import styled from 'styled-components';

export interface TerminalInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
}

export interface TerminalTextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  helperText?: string;
}

const InputContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
  width: 100%;
`;

const Label = styled.label`
  color: ${({ theme }) => theme.colors.foreground};
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: ${({ theme }) => theme.typography.fontSize.sm};
  font-weight: ${({ theme }) => theme.typography.fontWeight.semibold};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const StyledInput = styled.input`
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.foreground};
  border: 1px solid ${({ theme }) => theme.colors.foreground}40;
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: ${({ theme }) => theme.typography.fontSize.base};
  outline: none;
  transition: all 0.2s ease;

  &:focus {
    border-color: ${({ theme }) => theme.colors.foreground};
    box-shadow: 0 0 8px ${({ theme }) => theme.colors.foreground}60;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &::placeholder {
    color: ${({ theme }) => theme.colors.textMuted};
  }
`;

const StyledTextarea = styled.textarea`
  background: ${({ theme }) => theme.colors.background};
  color: ${({ theme }) => theme.colors.foreground};
  border: 1px solid ${({ theme }) => theme.colors.foreground}40;
  padding: ${({ theme }) => `${theme.spacing.sm} ${theme.spacing.md}`};
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: ${({ theme }) => theme.typography.fontSize.base};
  outline: none;
  transition: all 0.2s ease;
  resize: vertical;
  min-height: 120px;

  &:focus {
    border-color: ${({ theme }) => theme.colors.foreground};
    box-shadow: 0 0 8px ${({ theme }) => theme.colors.foreground}60;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &::placeholder {
    color: ${({ theme }) => theme.colors.textMuted};
  }

  /* Custom scrollbar */
  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-track {
    background: ${({ theme }) => theme.colors.background};
    border: 1px solid ${({ theme }) => theme.colors.foreground}20;
  }

  &::-webkit-scrollbar-thumb {
    background: ${({ theme }) => theme.colors.foreground}40;
  }

  &::-webkit-scrollbar-thumb:hover {
    background: ${({ theme }) => theme.colors.foreground}60;
  }
`;

const HelperText = styled.span`
  color: ${({ theme }) => theme.colors.textMuted};
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: ${({ theme }) => theme.typography.fontSize.xs};
  line-height: 1.4;
`;

const ErrorText = styled.span`
  color: ${({ theme }) => theme.colors.error};
  font-family: ${({ theme }) => theme.typography.fontFamily.mono};
  font-size: ${({ theme }) => theme.typography.fontSize.sm};
`;

export function TerminalInput({ label, error, helperText, id, ...props }: TerminalInputProps) {
  const autoId = useId();
  const inputId = id || autoId;

  return (
    <InputContainer>
      {label && <Label htmlFor={inputId}>{label}</Label>}
      <StyledInput id={inputId} {...props} />
      {helperText && <HelperText>{helperText}</HelperText>}
      {error && <ErrorText>{error}</ErrorText>}
    </InputContainer>
  );
}

export function TerminalTextarea({ label, error, helperText, id, ...props }: TerminalTextareaProps) {
  const autoId = useId();
  const textareaId = id || autoId;

  return (
    <InputContainer>
      {label && <Label htmlFor={textareaId}>{label}</Label>}
      <StyledTextarea id={textareaId} {...props} />
      {helperText && <HelperText>{helperText}</HelperText>}
      {error && <ErrorText>{error}</ErrorText>}
    </InputContainer>
  );
}
