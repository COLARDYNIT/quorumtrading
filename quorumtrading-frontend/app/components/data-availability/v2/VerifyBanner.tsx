'use client';

import styled from 'styled-components';

interface VerifyBannerProps {
  /** Real event name that was backfilled. */
  eventName: string;
  /** Real symbols the calendar jumped to. */
  symbols: string[];
  /** Real range label, e.g. "Jan 2022". */
  rangeLabel: string;
  onView: () => void;
}

const Banner = styled.div`
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 16px;
  border: 1px solid rgba(0, 217, 255, 0.45);
  border-radius: ${p => p.theme.borderRadius.md};
  background: rgba(0, 217, 255, 0.06);
`;

const Ic = styled.span`
  width: 30px;
  height: 30px;
  border-radius: 50%;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: ${p => p.theme.colors.terminalBlue};
  border: 1.5px solid rgba(0, 217, 255, 0.5);
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 15px;
`;

const Txt = styled.span`
  flex: 1;
  min-width: 0;
  b {
    font-size: 13.5px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: 600;
  }
  span {
    display: block;
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 11px;
    color: ${p => p.theme.colors.fgMuted};
    margin-top: 2px;
  }
`;

const ViewBtn = styled.button`
  white-space: nowrap;
  flex-shrink: 0;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 12px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  padding: 7px 14px;
  border-radius: ${p => p.theme.borderRadius.sm};
  border: 1px solid ${p => p.theme.colors.terminalBlue};
  background: rgba(0, 217, 255, 0.1);
  color: ${p => p.theme.colors.terminalBlue};
  cursor: pointer;
  transition: ${p => p.theme.transition.fast};

  &:hover {
    background: rgba(0, 217, 255, 0.18);
  }
`;

export function VerifyBanner({ eventName, symbols, rangeLabel, onView }: VerifyBannerProps) {
  return (
    <Banner>
      <Ic>↩</Ic>
      <Txt>
        <b>Backfilled &ldquo;{eventName}&rdquo; — verify it landed</b>
        <span>
          Jumped the calendar to {symbols.join(' · ')} · {rangeLabel} (the exact symbols + range just
          backfilled)
        </span>
      </Txt>
      <ViewBtn onClick={onView}>View coverage</ViewBtn>
    </Banner>
  );
}
