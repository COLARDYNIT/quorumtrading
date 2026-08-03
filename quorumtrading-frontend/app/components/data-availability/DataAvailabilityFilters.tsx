'use client';

import styled from 'styled-components';
import { EventType } from '@/app/lib/types/dataAvailability';
import { TerminalButton } from '@/app/components/terminal';
import { format, addMonths, subMonths } from 'date-fns';

interface Props {
  availableSymbols: string[];  // All symbols that can be selected
  selectedSymbols: string[];   // Currently selected symbols
  onSymbolsChange: (symbols: string[]) => void;
  dataTypes: EventType[];
  onDataTypesChange: (types: EventType[]) => void;
  selectedMonth: Date;
  onMonthChange: (date: Date) => void;
}

const Container = styled.div`
  background: ${p => p.theme.colors.surface};
  border: 1px solid ${p => p.theme.colors.border};
  padding: ${p => p.theme.spacing.lg};
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.lg};
`;

const SectionTitle = styled.h3`
  font-size: ${p => p.theme.typography.fontSize.lg};
  color: ${p => p.theme.colors.primary};
  margin: 0;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
`;

const FilterSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.sm};
`;

const FilterLabel = styled.label`
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.textMuted};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: ${p => p.theme.spacing.xs};
`;

const CheckboxGroup = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${p => p.theme.spacing.xs};
`;

const CheckboxLabel = styled.label`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.sm};
  color: ${p => p.theme.colors.foreground};
  cursor: pointer;
  padding: ${p => p.theme.spacing.xs};
  transition: ${p => p.theme.transition.fast};

  &:hover {
    background: ${p => p.theme.colors.hover};
  }
`;

const Checkbox = styled.input.attrs({ type: 'checkbox' })`
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: ${p => p.theme.colors.primary};
`;

const MonthNavigator = styled.div`
  display: flex;
  align-items: center;
  gap: ${p => p.theme.spacing.sm};
`;

const MonthDisplay = styled.div`
  flex: 1;
  text-align: center;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.theme.colors.foreground};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
`;

export function DataAvailabilityFilters({
  availableSymbols,
  selectedSymbols,
  onSymbolsChange,
  dataTypes,
  onDataTypesChange,
  selectedMonth,
  onMonthChange,
}: Props) {
  const handleSymbolToggle = (symbol: string) => {
    if (selectedSymbols.includes(symbol)) {
      // Allow deselecting (but show a warning if trying to deselect all)
      onSymbolsChange(selectedSymbols.filter(s => s !== symbol));
    } else {
      onSymbolsChange([...selectedSymbols, symbol]);
    }
  };

  const handleDataTypeToggle = (type: EventType) => {
    if (dataTypes.includes(type)) {
      onDataTypesChange(dataTypes.filter(t => t !== type));
    } else {
      onDataTypesChange([...dataTypes, type]);
    }
  };

  return (
    <Container>
      <SectionTitle>FILTERS</SectionTitle>

      {/* Symbol Selection */}
      <FilterSection>
        <FilterLabel>Symbols ({selectedSymbols.length}/{availableSymbols.length})</FilterLabel>
        <CheckboxGroup>
          {availableSymbols.map(symbol => (
            <CheckboxLabel key={symbol}>
              <Checkbox
                checked={selectedSymbols.includes(symbol)}
                onChange={() => handleSymbolToggle(symbol)}
              />
              {symbol}
            </CheckboxLabel>
          ))}
        </CheckboxGroup>
      </FilterSection>

      {/* Data Type Selection */}
      <FilterSection>
        <FilterLabel>Data Types</FilterLabel>
        <CheckboxGroup>
          {Object.values(EventType).map(type => (
            <CheckboxLabel key={type}>
              <Checkbox
                checked={dataTypes.includes(type)}
                onChange={() => handleDataTypeToggle(type)}
              />
              {type}
            </CheckboxLabel>
          ))}
        </CheckboxGroup>
      </FilterSection>

      {/* Month Selection */}
      <FilterSection>
        <FilterLabel>Month</FilterLabel>
        <MonthNavigator>
          <TerminalButton
            size="sm"
            onClick={() => onMonthChange(subMonths(selectedMonth, 1))}
          >
            &lt;
          </TerminalButton>
          <MonthDisplay>
            {format(selectedMonth, 'MMM yyyy')}
          </MonthDisplay>
          <TerminalButton
            size="sm"
            onClick={() => onMonthChange(addMonths(selectedMonth, 1))}
          >
            &gt;
          </TerminalButton>
        </MonthNavigator>
      </FilterSection>
    </Container>
  );
}
