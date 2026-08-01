import type { VxeGridPropTypes } from '#/adapter/vxe-table';

import { watch } from 'vue';

import { useCompactViewport } from '#/composables/use-compact-viewport';
import { withInvDocDetailColWidths } from '#/constants/layout/inventory-doc-compact-presets';

/**
 * 将库房明细列宽随视口同步到 gridOptions / vxe loadColumn。
 * 供尚未走 useInvDocDetailGrid、直接 useVbenVxeGrid 的主区复用。
 */
export function bindInvDocCompactColumns(options: {
  /** 各页 default 列定义（勿就地改宽；缩放由本函数产出新数组） */
  baseColumns: VxeGridPropTypes.Column[];
  gridOptions: { columns?: VxeGridPropTypes.Column[] };
  getGrid?: () => { loadColumn?: (cols: VxeGridPropTypes.Column[]) => void } | null | undefined;
}): void {
  const { isCompactViewport } = useCompactViewport();

  /** 按当前视口写回列宽 */
  function applyColumns(): void {
    const next = withInvDocDetailColWidths(options.baseColumns, isCompactViewport.value);
    options.gridOptions.columns = next;
    options.getGrid?.()?.loadColumn?.(next);
  }

  watch(
    isCompactViewport,
    () => {
      applyColumns();
    },
    { immediate: true },
  );
}
