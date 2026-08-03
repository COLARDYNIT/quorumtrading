'use client';

import styled from 'styled-components';
import { EventType } from '@/app/lib/types/dataAvailability';
import { isCrypto } from './coverageDerive';

interface FilterRailProps {
  availableSymbols: string[];
  selectedSymbols: string[];
  onSymbolsChange: (symbols: string[]) => void;
  dataTypes: EventType[];
  onDataTypesChange: (types: EventType[]) => void;
}

const Rail = styled.div`
  display: flex;
  flex-direction: column;
  gap: 16px;
`;

const Sec = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
`;

const FLabel = styled.span`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 10.5px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
`;

const GroupLabel = styled.div`
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 9.5px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgSubtle};
  padding: 6px 6px 2px;
`;

const FCheck = styled.label<{ $on: boolean }>`
  display: flex;
  align-items: center;
  gap: 9px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  color: ${p => p.theme.colors.foreground};
  padding: 4px 6px;
  border-radius: ${p => p.theme.borderRadius.sm};
  cursor: pointer;

  &:hover {
    background: rgba(255, 255, 255, 0.03);
  }

  .box {
    width: 14px;
    height: 14px;
    border-radius: 3px;
    border: 1px solid ${p => p.theme.colors.border};
    display: inline-flex;
    align-items: center;
    justify-content: center;
    ${p =>
      p.$on &&
      `background: #06ffa5; border-color: #06ffa5; color: ${p.theme.colors.background};`}
  }
  .box svg {
    width: 9px;
    height: 9px;
  }
  .cd {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    margin-left: auto;
  }
  .cd.eq {
    background: ${p => p.theme.colors.info};
  }
  .cd.cr {
    background: #06ffa5;
  }
  .note {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 9.5px;
    color: ${p => p.theme.colors.fgDimmed};
    margin-left: auto;
  }
`;

function Check({ on }: { on: boolean }) {
  return (
    <span className="box">
      {on && (
        <svg viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M1.5 5l2.5 2.5L8.5 2" />
        </svg>
      )}
    </span>
  );
}

export function FilterRail({
  availableSymbols,
  selectedSymbols,
  onSymbolsChange,
  dataTypes,
  onDataTypesChange,
}: FilterRailProps) {
  const active = new Set(selectedSymbols);

  const toggleSymbol = (sym: string) => {
    if (active.has(sym)) onSymbolsChange(selectedSymbols.filter(s => s !== sym));
    else onSymbolsChange([...selectedSymbols, sym]);
  };

  const toggleType = (t: EventType) => {
    if (dataTypes.includes(t)) onDataTypesChange(dataTypes.filter(x => x !== t));
    else onDataTypesChange([...dataTypes, t]);
  };

  // Classify into equities vs crypto where we can infer it (best-effort, no
  // fabricated market labels). If neither group is empty we render the split;
  // otherwise we render one flat list.
  const eq = availableSymbols.filter(s => !isCrypto(s));
  const cr = availableSymbols.filter(s => isCrypto(s));
  const split = eq.length > 0 && cr.length > 0;

  return (
    <Rail>
      <Sec>
        <FLabel>
          Symbols · {active.size}/{availableSymbols.length}
        </FLabel>
        {split ? (
          <>
            <GroupLabel>Equities · weekends closed</GroupLabel>
            {eq.map(s => (
              <FCheck key={s} $on={active.has(s)} onClick={() => toggleSymbol(s)}>
                <Check on={active.has(s)} />
                {s}
                <span className="cd eq" />
              </FCheck>
            ))}
            <GroupLabel>Crypto · 24/7</GroupLabel>
            {cr.map(s => (
              <FCheck key={s} $on={active.has(s)} onClick={() => toggleSymbol(s)}>
                <Check on={active.has(s)} />
                {s}
                <span className="cd cr" />
              </FCheck>
            ))}
          </>
        ) : (
          availableSymbols.map(s => (
            <FCheck key={s} $on={active.has(s)} onClick={() => toggleSymbol(s)}>
              <Check on={active.has(s)} />
              {s}
              <span className={`cd ${isCrypto(s) ? 'cr' : 'eq'}`} />
            </FCheck>
          ))
        )}
      </Sec>

      <Sec>
        <FLabel>Data types</FLabel>
        {Object.values(EventType).map(t => (
          <FCheck key={t} $on={dataTypes.includes(t)} onClick={() => toggleType(t)}>
            <Check on={dataTypes.includes(t)} />
            {t}
          </FCheck>
        ))}
      </Sec>
    </Rail>
  );
}
