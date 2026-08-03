/**
 * A2UI Component Catalog — maps type strings to React components.
 */
import type { ComponentType } from 'react';

export interface A2UICatalogComponentProps {
  /** Resolved props from the A2UI payload. */
  [key: string]: unknown;
  /** Pre-resolved child ComponentNodes, rendered by the Renderer. */
  children?: React.ReactNode;
}

const registry = new Map<string, ComponentType<A2UICatalogComponentProps>>();

export function registerComponent(type: string, component: ComponentType<A2UICatalogComponentProps>) {
  registry.set(type, component);
}

export function getComponent(type: string): ComponentType<A2UICatalogComponentProps> | undefined {
  return registry.get(type);
}

export function hasComponent(type: string): boolean {
  return registry.has(type);
}
