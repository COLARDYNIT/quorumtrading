'use client';

import Link from 'next/link';
import styled from 'styled-components';

interface LibraryEmptyProps {
  signedIn: boolean;
}

const Wrap = styled.section`
  margin-top: 28px;
  padding: 72px 32px;
  border: 1px dashed ${p => p.theme.colors.border};
  background: ${p => p.theme.colors.surface};
  border-radius: 4px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;

  & > .glyph {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 56px;
    color: ${p => p.theme.colors.fgSubtle};
    line-height: 1;
  }
  & > h2 {
    font-family: ${p => p.theme.typography.fontFamily.sans};
    font-size: 24px;
    color: ${p => p.theme.colors.fgStrong};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
    margin: 0;
  }
  & > p {
    max-width: 56ch;
    color: ${p => p.theme.colors.fgMuted};
    font-size: 14.5px;
    line-height: 1.55;
    margin: 0;
  }
  & code {
    font-family: ${p => p.theme.typography.fontFamily.mono};
    font-size: 13.5px;
    color: ${p => p.theme.colors.foreground};
    background: ${p => p.theme.colors.background};
    border: 1px solid ${p => p.theme.colors.borderSubtle};
    padding: 0 4px;
    border-radius: 2px;
  }
`;

const Actions = styled.div`
  display: flex;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
  justify-content: center;
`;

const CtaSolid = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  color: ${p => p.theme.colors.fgInverse};
  background: ${p => p.theme.colors.brand};
  border: 1px solid ${p => p.theme.colors.brand};
  border-radius: 2px;
  text-decoration: none;

  &:hover { background: ${p => p.theme.colors.brandHover}; border-color: ${p => p.theme.colors.brandHover}; }
`;

const CtaGhost = styled(Link)`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  color: ${p => p.theme.colors.foreground};
  border: 1px solid ${p => p.theme.colors.border};
  background: transparent;
  border-radius: 2px;
  text-decoration: none;

  &:hover { border-color: ${p => p.theme.colors.fgMuted}; }
`;

const Tip = styled.div`
  margin-top: 16px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  color: ${p => p.theme.colors.fgDimmed};
`;

export function LibraryEmpty({ signedIn }: LibraryEmptyProps) {
  return (
    <Wrap>
      <div className="glyph">∅</div>
      <h2>No public strategies yet.</h2>
      <p>
        Once a strategy author flips <code>publicVisible: true</code>, it lands on this page — markdown body,
        YAML config, performance across the author&apos;s sims and all. Be the first to ship one.
      </p>
      <Actions>
        <CtaSolid href={signedIn ? '/strategies/new' : '/auth/signin'}>
          {signedIn ? 'Author a strategy →' : 'Sign up to author →'}
        </CtaSolid>
        <CtaGhost href="/faq">Read the publishing guide</CtaGhost>
      </Actions>
      <Tip>tip · forking your own strategy into a private sim is free and doesn&apos;t require publishing</Tip>
    </Wrap>
  );
}
