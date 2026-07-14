import { useBreakpoints } from '@vueuse/core';

import { FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX } from '#/constants/layout/viewport-breakpoints';

/**
 * 查询区控件布局：视口宽度 ≥ {@link FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX} 时使用固定控件宽度；
 * 低于该值时使用栅格铺满（适配 1600 等窄屏）。
 */
export function useFixedQueryControlLayout() {
  const breakpoints = useBreakpoints({
    fixedQueryControl: FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX,
  });

  const useFixedQueryControlWidth = breakpoints.greaterOrEqual('fixedQueryControl');

  return { useFixedQueryControlWidth };
}
