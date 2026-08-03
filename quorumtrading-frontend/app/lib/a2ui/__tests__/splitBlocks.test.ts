import { splitA2UIBlocks } from '../splitBlocks';

describe('splitA2UIBlocks', () => {
  it('returns single text block when no a2ui tags', () => {
    const result = splitA2UIBlocks('Hello world');
    expect(result).toEqual([{ type: 'text', content: 'Hello world' }]);
  });

  it('extracts a single a2ui block', () => {
    const json = '{"surface_update":{}}';
    const result = splitA2UIBlocks(`before<a2ui>${json}</a2ui>after`);
    expect(result).toHaveLength(3);
    expect(result[0]).toEqual({ type: 'text', content: 'before' });
    expect(result[1]).toEqual({ type: 'a2ui', content: json });
    expect(result[2]).toEqual({ type: 'text', content: 'after' });
  });

  it('handles multiple a2ui blocks', () => {
    const result = splitA2UIBlocks(
      'start<a2ui>{"a":1}</a2ui>middle<a2ui>{"b":2}</a2ui>end'
    );
    expect(result).toHaveLength(5);
    expect(result[0]).toEqual({ type: 'text', content: 'start' });
    expect(result[1]).toEqual({ type: 'a2ui', content: '{"a":1}' });
    expect(result[2]).toEqual({ type: 'text', content: 'middle' });
    expect(result[3]).toEqual({ type: 'a2ui', content: '{"b":2}' });
    expect(result[4]).toEqual({ type: 'text', content: 'end' });
  });

  it('handles a2ui block at start', () => {
    const result = splitA2UIBlocks('<a2ui>{"x":1}</a2ui>after');
    expect(result).toHaveLength(2);
    expect(result[0]).toEqual({ type: 'a2ui', content: '{"x":1}' });
    expect(result[1]).toEqual({ type: 'text', content: 'after' });
  });

  it('handles a2ui block at end', () => {
    const result = splitA2UIBlocks('before<a2ui>{"x":1}</a2ui>');
    expect(result).toHaveLength(2);
    expect(result[0]).toEqual({ type: 'text', content: 'before' });
    expect(result[1]).toEqual({ type: 'a2ui', content: '{"x":1}' });
  });

  it('returns text block for malformed tags (unclosed)', () => {
    const result = splitA2UIBlocks('before<a2ui>unclosed');
    expect(result).toEqual([{ type: 'text', content: 'before<a2ui>unclosed' }]);
  });

  it('handles empty string', () => {
    const result = splitA2UIBlocks('');
    expect(result).toEqual([]);
  });

  it('filters out empty text blocks between adjacent a2ui tags', () => {
    const result = splitA2UIBlocks('<a2ui>{"a":1}</a2ui><a2ui>{"b":2}</a2ui>');
    // Empty text between the two blocks should be filtered
    const nonEmpty = result.filter(b => b.content.trim() !== '' || b.type === 'a2ui');
    expect(nonEmpty).toHaveLength(2);
    expect(nonEmpty[0].type).toBe('a2ui');
    expect(nonEmpty[1].type).toBe('a2ui');
  });
});
