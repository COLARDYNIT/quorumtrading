/**
 * A2UI v0.8 TypeScript interfaces.
 *
 * Subset of the Google A2UI declarative UI protocol used by QuorumTrading.
 * @see https://github.com/google/A2UI
 */

// ---- Bound Values ----

/** A literal or data-model-bound value. */
export interface BoundValue {
  literalString?: string;
  literalNumber?: number;
  literalBoolean?: boolean;
  /** Dot-path into the DataModel, e.g. "portfolio.totalValue" */
  dataPath?: string;
}

// ---- Components ----

/** A flat component node as emitted by the LLM. */
export interface A2UIComponent {
  /** Unique id within the surface. */
  id: string;
  /** Component type from the catalog, e.g. "ConfidenceGauge". */
  type: string;
  /** Ordered child component ids (for layout components). */
  childIds?: string[];
  /** Arbitrary props — values are BoundValue or plain JSON scalars. */
  properties: Record<string, BoundValue | string | number | boolean>;
}

// ---- Data Model ----

export interface DataModelUpdate {
  key: string;
  value: unknown;
}

// ---- Surface Update ----

export interface SurfaceUpdate {
  surfaceId?: string;
  components: A2UIComponent[];
  dataModel?: DataModelUpdate[];
}

// ---- Top-level message ----

export interface A2UIMessage {
  surfaceUpdate: SurfaceUpdate;
}

// ---- User Actions (emitted by interactive components) ----

export interface UserAction {
  /** Component id that triggered the action. */
  componentId: string;
  /** Semantic action type, e.g. "sell", "show_portfolio". */
  actionType: string;
  /** Optional payload (e.g. { symbol: "BTC", qty: 1 }). */
  payload?: Record<string, unknown>;
}

// ---- Resolved tree node used by the renderer ----

export interface ComponentNode {
  id: string;
  type: string;
  /** Resolved props — BoundValues are already resolved to concrete values. */
  props: Record<string, unknown>;
  children: ComponentNode[];
}

/** Result of parsing a full A2UI payload. */
export interface ParsedSurface {
  roots: ComponentNode[];
  dataModel: Record<string, unknown>;
}
