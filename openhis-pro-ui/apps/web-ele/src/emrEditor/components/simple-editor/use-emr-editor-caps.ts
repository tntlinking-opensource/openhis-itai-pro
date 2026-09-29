import type { ComputedRef } from 'vue';

import { computed, inject } from 'vue';

export interface EmrEditorCaps {
  layoutLocked: boolean;
  readOnly: boolean;
}

/**
 * SimpleEditor provide；NodeView 用于在「固定模板」下仍放行可编辑岛屿。
 */
export function injectEmrEditorCaps(): ComputedRef<EmrEditorCaps> {
  return inject(
    'emrEditorCaps',
    computed(() => ({ layoutLocked: false, readOnly: false })),
  );
}

export function useEmrIslandDomEditable(
  islandWhenLocked: ComputedRef<boolean>,
): ComputedRef<boolean> {
  const caps = injectEmrEditorCaps();
  return computed(() => {
    if (caps.value.readOnly) return false;
    /* 未锁定时与 props.readOnly 对齐，避免 setEditable 的 watch 晚一帧导致岛屿仍不可点 */
    if (!caps.value.layoutLocked) return true;
    return islandWhenLocked.value;
  });
}

/** 与插入目标、Word 化点击拦截共用的填写岛 contenteditable 选择器 */
const FILLABLE_ISLAND_EDITABLE_SELECTOR = [
  '.emr-fillable-field__input',
  '.emr-inline-text__field',
  '.emr-de__text-field',
  '.emr-free-text__text',
].join(', ');

/** 节点自身只读（患者绑定芯片等），不得随编辑器解锁改成可填 */
const FILLABLE_STRUCTURAL_READONLY_SELECTOR = [
  '.emr-fillable--readonly',
  '.emr-de--readonly',
  '.emr-fillable-field__shell--readonly',
  '.emr-metadata-chip--readonly',
].join(', ');

/**
 * 把填写岛 contenteditable 写成与编辑器只读态一致。
 * 岛内 span 用了 v-once，只读打开历史再复用时 Vue 不会改属性，不写 DOM 就会一直改不了。
 */
export function syncFillableIslandContentEditable(
  root: ParentNode | null | undefined,
  editable: boolean,
) {
  if (!root || typeof root.querySelectorAll !== 'function') return;
  const islands = root.querySelectorAll(FILLABLE_ISLAND_EDITABLE_SELECTOR);
  for (const node of islands) {
    if (!(node instanceof HTMLElement)) continue;
    const structurallyReadonly = Boolean(node.closest(FILLABLE_STRUCTURAL_READONLY_SELECTOR));
    const next = editable && !structurallyReadonly;
    if (node.isContentEditable === next) continue;
    node.contentEditable = next ? 'true' : 'false';
  }
}
