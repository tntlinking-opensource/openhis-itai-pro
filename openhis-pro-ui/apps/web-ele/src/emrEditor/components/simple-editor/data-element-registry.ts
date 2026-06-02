import type { DataElementDef } from './data-element-types';

const registry = new Map<string, DataElementDef>();

export function registerDataElement(def: DataElementDef) {
  registry.set(def.id, def);
}

export function unregisterDataElement(id: string) {
  registry.delete(id);
}

export function getDataElementDef(id: string): DataElementDef | undefined {
  if (!id) return undefined;
  return registry.get(id);
}

export function clearDataElementRegistry() {
  registry.clear();
}

/** 将目录中的选项序列化为节点 attrs.optionsJson */
export function serializeOptionsFromDef(def: DataElementDef): string {
  if (!def.valueSet?.length) return '[]';
  return JSON.stringify(def.valueSet.map((v) => ({ value: v.code, label: v.label })));
}
