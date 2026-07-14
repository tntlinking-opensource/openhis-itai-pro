import { computed } from 'vue';

import { useWindowSize } from '@vueuse/core';

import {
  isCompactViewportWidth,
  pickViewportPreset,
  type ViewportSizePreset,
} from '#/constants/layout/viewport-breakpoints';

/**
 * 视口 compact 档（<1680）响应式工具：供 JS 侧按 default/compact 预设切换（如表格列宽、侧栏 inline width）。
 */
export function useCompactViewport() {
  const { width: viewportWidth } = useWindowSize();

  const isCompactViewport = computed(() => isCompactViewportWidth(viewportWidth.value));

  function pickPreset<T>(preset: ViewportSizePreset<T>) {
    return computed(() => pickViewportPreset(viewportWidth.value, preset));
  }

  return {
    viewportWidth,
    isCompactViewport,
    pickPreset,
  };
}
