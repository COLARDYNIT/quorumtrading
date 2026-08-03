/**
 * Parse A2UI JSONL payload into a resolved component tree.
 *
 * The payload is a single JSON object (A2UIMessage) containing a SurfaceUpdate
 * with a flat list of components and an optional data model.
 * This parser:
 *   1. Builds the data model from dataModel updates.
 *   2. Resolves BoundValue references to concrete values.
 *   3. Converts the flat adjacency list into a nested ComponentNode tree.
 */
import type {
  A2UIMessage,
  A2UIComponent,
  BoundValue,
  ComponentNode,
  ParsedSurface,
} from './types';

/**
 * Resolve a BoundValue to its concrete JS value.
 */
function resolveBoundValue(
  val: BoundValue | string | number | boolean,
  dataModel: Record<string, unknown>,
): unknown {
  if (val === null || val === undefined) return val;
  if (typeof val !== 'object') return val;

  const bv = val as BoundValue;
  if (bv.literalString !== undefined) return bv.literalString;
  if (bv.literalNumber !== undefined) return bv.literalNumber;
  if (bv.literalBoolean !== undefined) return bv.literalBoolean;
  if (bv.dataPath) {
    return resolveDataPath(bv.dataPath, dataModel);
  }
  // If it's a plain object without BoundValue keys, return as-is
  return val;
}

/**
 * Walk a dot-path like "portfolio.totalValue" into the data model.
 */
function resolveDataPath(path: string, dataModel: Record<string, unknown>): unknown {
  const parts = path.split('.');
  let current: unknown = dataModel;
  for (const part of parts) {
    if (current === null || current === undefined || typeof current !== 'object') {
      return undefined;
    }
    current = (current as Record<string, unknown>)[part];
  }
  return current;
}

/**
 * Convert a flat component into a tree node, recursively resolving children.
 */
function buildNode(
  component: A2UIComponent,
  componentMap: Map<string, A2UIComponent>,
  dataModel: Record<string, unknown>,
  visited: Set<string>,
): ComponentNode {
  // Resolve all properties
  const props: Record<string, unknown> = {};
  for (const [key, val] of Object.entries(component.properties)) {
    props[key] = resolveBoundValue(val, dataModel);
  }

  // Resolve children recursively (guard against cycles)
  const children: ComponentNode[] = [];
  if (component.childIds) {
    for (const childId of component.childIds) {
      if (visited.has(childId)) continue;
      const child = componentMap.get(childId);
      if (!child) continue;
      visited.add(childId);
      children.push(buildNode(child, componentMap, dataModel, visited));
    }
  }

  return {
    id: component.id,
    type: component.type,
    props,
    children,
  };
}

/**
 * Parse an A2UI JSON payload string into a ParsedSurface.
 * Returns null on any parse error.
 */
export function parseA2UIPayload(payload: string): ParsedSurface | null {
  if (!payload || !payload.trim()) return null;

  try {
    const message: A2UIMessage = JSON.parse(payload);
    if (!message?.surfaceUpdate?.components) return null;

    const { components, dataModel: dataModelUpdates } = message.surfaceUpdate;
    if (!Array.isArray(components) || components.length === 0) return null;

    // Build data model
    const dataModel: Record<string, unknown> = {};
    if (dataModelUpdates) {
      for (const update of dataModelUpdates) {
        dataModel[update.key] = update.value;
      }
    }

    // Build lookup map
    const componentMap = new Map<string, A2UIComponent>();
    for (const comp of components) {
      componentMap.set(comp.id, comp);
    }

    // Find root nodes (not referenced as children of any other component)
    const childIds = new Set<string>();
    for (const comp of components) {
      if (comp.childIds) {
        for (const cid of comp.childIds) {
          childIds.add(cid);
        }
      }
    }
    const rootComponents = components.filter(c => !childIds.has(c.id));

    // Build tree from each root
    const visited = new Set<string>();
    const roots: ComponentNode[] = [];
    for (const root of rootComponents) {
      visited.add(root.id);
      roots.push(buildNode(root, componentMap, dataModel, visited));
    }

    return { roots, dataModel };
  } catch {
    return null;
  }
}
