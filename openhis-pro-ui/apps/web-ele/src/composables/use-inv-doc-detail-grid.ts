import type { Ref } from 'vue';
import type { VxeGridPropTypes } from '#/adapter/vxe-table';

import { nextTick, reactive, ref, watch } from 'vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useCompactViewport } from '#/composables/use-compact-viewport';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
import { withInvDocDetailColWidths } from '#/constants/layout/inventory-doc-compact-presets';
import type { InventoryDocGridEditConfig } from '#/utils/inventory/inventory-doc-grid-edit';
import {
  collectInventoryGridFullData,
  createDefaultInvDocGridOptions,
} from '#/utils/inventory/inventory-doc-grid-common';

export interface UseInvDocDetailGridOptions<TRow extends object> {
  columns: VxeGridPropTypes.Column[];
  documentEditable: Ref<boolean> | (() => boolean);
  config: InventoryDocGridEditConfig<TRow>;
  /** 行编辑结束（如重算金额）后再 syncRowFormMode */
  onEditClosed?: (row: TRow) => void;
  /** vxe beforeEditMethod */
  beforeEditMethod?: () => boolean;
  enableKeyboard?: boolean;
  /**
   * 是否随视口自动缩放明细列宽（默认 true）。
   * 订货单等已自行 pickPreset 列宽的页面传 false，避免双重压缩。
   */
  compactColumns?: boolean;
}

/**
 * 库房单据明细表：vxe grid + 键盘导航 + 未完成行表单态。
 * 返回 Grid 组件、gridApi、gridEdit 与 collectGridRows。
 */
export function useInvDocDetailGrid<TRow extends object>(
  options: UseInvDocDetailGridOptions<TRow>,
) {
  const gridWrapRef = ref<HTMLElement | null>(null);
  const { isCompactViewport } = useCompactViewport();
  const enableCompactColumns = options.compactColumns !== false;

  /** 按视口产出列定义（关闭 compactColumns 时原样使用入参） */
  function resolveColumns(): VxeGridPropTypes.Column[] {
    if (!enableCompactColumns) {
      return options.columns;
    }
    return withInvDocDetailColWidths(options.columns, isCompactViewport.value);
  }

  const gridOptions = reactive(
    createDefaultInvDocGridOptions<TRow>({
      columns: resolveColumns(),
      beforeEditMethod: options.beforeEditMethod,
    }),
  );

  let gridEdit!: ReturnType<typeof useInventoryDocGridEdit<TRow>>;

  function isEditable(): boolean {
    return typeof options.documentEditable === 'function'
      ? options.documentEditable()
      : options.documentEditable.value;
  }

  /** 当前表格全量行 */
  function collectGridRows(): TRow[] {
    return collectInventoryGridFullData<TRow>(gridApi ?? null);
  }

  /** 绑定键盘导航容器 DOM（供 InvDocDetailGridShell 调用） */
  function bindGridWrap(el: HTMLElement | null): void {
    gridWrapRef.value = el;
  }

  const [Grid, gridApi] = useVbenVxeGrid<TRow>({
    gridOptions: gridOptions as Record<string, unknown>,
    gridEvents: {
      cellClick: (params: {
        column?: { field?: string; type?: string };
        row: TRow;
        $event?: Event;
      }) => gridEdit.onCellClick(params),
      editClosed(params: { row: TRow }) {
        options.onEditClosed?.(params.row);
        gridEdit.syncRowFormMode(params.row);
      },
    },
    class: 'min-h-0 flex-1 !rounded-none !bg-transparent shadow-none',
    gridClass: '!p-0',
  });

  gridEdit = useInventoryDocGridEdit<TRow>({
    getGridApi: () => gridApi ?? null,
    gridWrapRef,
    documentEditable: options.documentEditable,
    collectRows: collectGridRows,
    config: options.config,
    enableKeyboard: options.enableKeyboard,
  });

  /** 同步 documentEditable → editConfig.enabled / showIcon */
  function patchGridEditConfig(): void {
    const editable = isEditable();
    const patched = gridEdit.patchGridOptions({
      ...(gridOptions as Record<string, unknown>),
      editConfig: {
        ...(gridOptions.editConfig as Record<string, unknown>),
        enabled: editable,
        // 只读时隐藏列头编辑图标，避免「看起来还能改」
        showIcon: editable,
      },
    });
    Object.assign(gridOptions, patched);
  }

  watch(
    () => isEditable(),
    (editable) => {
      patchGridEditConfig();
      void nextTick(() => {
        if (!editable) {
          gridEdit.resetFormMode();
          gridEdit.clearGridEdit();
        }
      });
    },
    { immediate: true },
  );

  // 窄屏切换时同步明细列宽（不改列结构）
  if (enableCompactColumns) {
    watch(isCompactViewport, () => {
      const next = resolveColumns();
      gridOptions.columns = next;
      const grid = gridApi?.grid as
        | { loadColumn?: (cols: VxeGridPropTypes.Column[]) => void }
        | null
        | undefined;
      grid?.loadColumn?.(next);
    });
  }

  return {
    Grid,
    gridApi,
    gridWrapRef,
    bindGridWrap,
    gridEdit,
    gridOptions,
    collectGridRows,
    patchGridEditConfig,
  };
}
