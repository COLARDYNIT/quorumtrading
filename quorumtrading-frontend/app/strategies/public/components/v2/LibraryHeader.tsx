'use client';

import styled from 'styled-components';

interface LibraryHeaderProps {
  count: number;
  authors: number;
}

const Wrap = styled.section`
  padding: 48px 0 32px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  flex-wrap: wrap;

  @media (max-width: 768px) {
    padding: 36px 0 20px;
    flex-direction: column;
    align-items: flex-start;
  }
`;

const Copy = styled.div`
  max-width: 640px;
`;

const Eyebrow = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};
  margin-bottom: 14px;

  & > .gly {
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: ${p => p.theme.colors.brand};
    box-shadow: 0 0 6px rgba(0, 255, 65, 0.45);
  }
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 36px;
  line-height: 1.1;
  letter-spacing: -0.02em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0 0 14px;

  @media (max-width: 768px) {
    font-size: 30px;
  }
`;

const Lede = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 15px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
`;

const Meta = styled.div`
  text-align: right;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
  display: flex;
  flex-direction: column;
  gap: 2px;

  & > b {
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.medium};
    font-size: 14px;
  }

  @media (max-width: 768px) { text-align: left; }
`;

export function LibraryHeader({ count, authors }: LibraryHeaderProps) {
  return (
    <Wrap>
      <Copy>
        <Eyebrow><span className="gly" />Public library</Eyebrow>
        <H1>Strategies the community<br />is running with real money.</H1>
        <Lede>
          Each strategy is a markdown document with a YAML config. Fork one into your account, backtest against
          history, then promote it through Mock → paper → live. Every number on this page comes from a public
          backtest — no live capital is exposed.
        </Lede>
      </Copy>
      <Meta>
        <span>Indexed</span>
        <b>{count} public strateg{count === 1 ? 'y' : 'ies'}</b>
        <span>across {authors} author{authors === 1 ? '' : 's'}</span>
      </Meta>
    </Wrap>
  );
}
