import { parseA2UIPayload } from '../parser';

describe('parseA2UIPayload', () => {
  it('parses valid payload with single component', () => {
    const payload = JSON.stringify({
      surfaceUpdate: {
        components: [
          {
            id: 'c1',
            type: 'ConfidenceGauge',
            childIds: [],
            properties: {
              value: { literalNumber: 85 },
              label: { literalString: 'Confidence' },
            },
          },
        ],
      },
    });

    const result = parseA2UIPayload(payload);
    expect(result).not.toBeNull();
    expect(result!.roots).toHaveLength(1);
    expect(result!.roots[0].type).toBe('ConfidenceGauge');
    expect(result!.roots[0].props.value).toBe(85);
    expect(result!.roots[0].props.label).toBe('Confidence');
  });

  it('resolves nested component tree', () => {
    const payload = JSON.stringify({
      surfaceUpdate: {
        components: [
          {
            id: 'row1',
            type: 'Row',
            childIds: ['c1', 'c2'],
            properties: { gap: { literalString: '8px' } },
          },
          {
            id: 'c1',
            type: 'ActionBadge',
            childIds: [],
            properties: { action: { literalString: 'BUY' } },
          },
          {
            id: 'c2',
            type: 'ActionBadge',
            childIds: [],
            properties: { action: { literalString: 'SELL' } },
          },
        ],
      },
    });

    const result = parseA2UIPayload(payload);
    expect(result).not.toBeNull();
    expect(result!.roots).toHaveLength(1);
    expect(result!.roots[0].type).toBe('Row');
    expect(result!.roots[0].children).toHaveLength(2);
    expect(result!.roots[0].children[0].props.action).toBe('BUY');
    expect(result!.roots[0].children[1].props.action).toBe('SELL');
  });

  it('resolves BoundValue types correctly', () => {
    const payload = JSON.stringify({
      surfaceUpdate: {
        components: [
          {
            id: 'c1',
            type: 'TextBlock',
            childIds: [],
            properties: {
              text: { literalString: 'hello' },
              count: { literalNumber: 42 },
              active: { literalBoolean: true },
            },
          },
        ],
      },
    });

    const result = parseA2UIPayload(payload);
    expect(result).not.toBeNull();
    expect(result!.roots[0].props.text).toBe('hello');
    expect(result!.roots[0].props.count).toBe(42);
    expect(result!.roots[0].props.active).toBe(true);
  });

  it('resolves dataPath from data model', () => {
    const payload = JSON.stringify({
      surfaceUpdate: {
        components: [
          {
            id: 'c1',
            type: 'PriceDisplay',
            childIds: [],
            properties: {
              price: { dataPath: 'portfolio.totalValue' },
            },
          },
        ],
        dataModel: [
          { key: 'portfolio', value: { totalValue: 12345.67 } },
        ],
      },
    });

    const result = parseA2UIPayload(payload);
    expect(result).not.toBeNull();
    expect(result!.roots[0].props.price).toBe(12345.67);
  });

  it('returns null for invalid JSON', () => {
    expect(parseA2UIPayload('not json')).toBeNull();
  });

  it('returns null for empty string', () => {
    expect(parseA2UIPayload('')).toBeNull();
  });

  it('returns null for payload missing surfaceUpdate', () => {
    expect(parseA2UIPayload(JSON.stringify({ other: {} }))).toBeNull();
  });

  it('returns null for empty components array', () => {
    const payload = JSON.stringify({
      surfaceUpdate: { components: [] },
    });
    const result = parseA2UIPayload(payload);
    // Parser returns null for empty components
    expect(result).toBeNull();
  });
});
