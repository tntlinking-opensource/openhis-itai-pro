import { onBeforeUnmount, watch } from 'vue';

import { useWindowSize } from '@vueuse/core';

import {
  COMPACT_VIEWPORT_ROOT_CLASS,
  isCompactViewportWidth,
} from '#/constants/layout/viewport-breakpoints';

/**
 * 视口 <1680（含 1600）：在 html 根节点挂载紧凑布局 class，供全局 shell CSS 使用；
 * 不写 preferences，避免窄屏高度被持久化到本地偏好。
 */
export function useLayoutCompactViewport() {
  const { width: viewportWidth } = useWindowSize();

  function syncCompactClass(width: number) {
    document.documentElement.classList.toggle(
      COMPACT_VIEWPORT_ROOT_CLASS,
      isCompactViewportWidth(width),
    );
  }

  watch(viewportWidth, syncCompactClass, { immediate: true });

  onBeforeUnmount(() => {
    document.documentElement.classList.remove(COMPACT_VIEWPORT_ROOT_CLASS);
  });
}
