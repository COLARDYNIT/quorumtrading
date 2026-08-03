import styled from 'styled-components';

export const MarkdownContainer = styled.div`
  font-size: ${p => p.theme.typography.fontSize.base};
  color: ${p => p.theme.colors.textMuted};
  line-height: 1.5;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  overflow-wrap: break-word;
  word-break: break-word;

  h1, h2, h3, h4, h5, h6 {
    color: ${p => p.theme.colors.terminalBlue};
    margin: 1.5em 0 0.5em 0;
    font-weight: ${p => p.theme.typography.fontWeight.bold};
  }

  h2 { font-size: 1.4em; }
  h3 { font-size: 1.2em; }

  p {
    margin: 0.75em 0;
  }

  strong {
    color: ${p => p.theme.colors.terminalGreen};
    font-weight: ${p => p.theme.typography.fontWeight.semibold};
  }

  em {
    color: ${p => p.theme.colors.text};
    font-style: italic;
  }

  a {
    color: ${p => p.theme.colors.terminalBlue};
    text-decoration: underline;

    &:hover {
      color: ${p => p.theme.colors.terminalGreen};
    }
  }

  ul, ol {
    margin: 0.5em 0;
    padding-left: 1.5em;
  }

  li {
    margin: 0.15em 0;

    &::marker {
      color: ${p => p.theme.colors.terminalGreen};
    }
  }

  code {
    background: ${p => p.theme.colors.background};
    color: ${p => p.theme.colors.terminalGreen};
    padding: 0.2em 0.4em;
    border-radius: 3px;
    font-size: 0.9em;
  }

  pre {
    background: ${p => p.theme.colors.background};
    border: 1px solid ${p => p.theme.colors.border};
    border-radius: ${p => p.theme.borderRadius.md};
    padding: ${p => p.theme.spacing.md};
    overflow-x: auto;
    margin: 1em 0;

    code {
      background: none;
      padding: 0;
      color: ${p => p.theme.colors.text};
    }
  }

  blockquote {
    border-left: 3px solid ${p => p.theme.colors.terminalGreen};
    margin: 1em 0;
    padding-left: 1em;
    color: ${p => p.theme.colors.textMuted};
    font-style: italic;
  }

  hr {
    border: none;
    border-top: 1px solid ${p => p.theme.colors.border};
    margin: 2em 0;
  }
`;
