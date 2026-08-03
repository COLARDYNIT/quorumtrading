export { parseA2UIPayload } from './parser';
export { registerComponent, getComponent, hasComponent } from './catalog';
export { splitA2UIBlocks } from './splitBlocks';
export { registerA2UICatalog } from './registerCatalog';
export type {
  A2UIMessage,
  A2UIComponent,
  BoundValue,
  ComponentNode,
  ParsedSurface,
  UserAction,
  SurfaceUpdate,
  DataModelUpdate,
} from './types';
export type { ContentBlock } from './splitBlocks';
