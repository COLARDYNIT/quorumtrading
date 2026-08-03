/**
 * Split mixed text + A2UI content on `<a2ui>...</a2ui>` tag boundaries.
 *
 * Returns an ordered array of blocks, each tagged as 'text' or 'a2ui'.
 * Malformed / unclosed tags are treated as plain text.
 */
export interface ContentBlock {
  type: 'text' | 'a2ui';
  content: string;
}

const A2UI_REGEX = /<a2ui>([\s\S]*?)<\/a2ui>/g;

export function splitA2UIBlocks(content: string): ContentBlock[] {
  if (!content) return [];

  const blocks: ContentBlock[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  // Reset regex state
  A2UI_REGEX.lastIndex = 0;

  while ((match = A2UI_REGEX.exec(content)) !== null) {
    // Text before the tag
    if (match.index > lastIndex) {
      const text = content.slice(lastIndex, match.index).trim();
      if (text) {
        blocks.push({ type: 'text', content: text });
      }
    }

    // A2UI block (inner content of the tag)
    const a2uiContent = match[1].trim();
    if (a2uiContent) {
      blocks.push({ type: 'a2ui', content: a2uiContent });
    }

    lastIndex = match.index + match[0].length;
  }

  // Remaining text after last tag
  if (lastIndex < content.length) {
    const text = content.slice(lastIndex).trim();
    if (text) {
      blocks.push({ type: 'text', content: text });
    }
  }

  // If no tags were found, return the whole thing as text
  if (blocks.length === 0 && content.trim()) {
    blocks.push({ type: 'text', content: content.trim() });
  }

  return blocks;
}
