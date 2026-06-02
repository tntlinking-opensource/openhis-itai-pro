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
