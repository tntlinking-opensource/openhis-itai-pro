import type { Ref } from 'vue';
import type { VxeGridPropTypes } from '#/adapter/vxe-table';

import { nextTick, reactive, ref, watch } from 'vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';
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
}

/**
 * 库房单据明细表：vxe grid + 键盘导航 + 未完成行表单态。
 * 返回 Grid 组件、gridApi、gridEdit 与 collectGridRows。
 */
export function useInvDocDetailGrid<TRow extends object>(
  options: UseInvDocDetailGridOptions<TRow>,
) {
  const gridWrapRef = ref<HTMLElement | null>(null);

  const gridOptions = reactive(
    createDefaultInvDocGridOptions<TRow>({
      columns: options.columns,
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

  /** 同步 documentEditable → editConfig.enabled */
  function patchGridEditConfig(): void {
    const patched = gridEdit.patchGridOptions({
      ...(gridOptions as Record<string, unknown>),
      editConfig: {
        ...(gridOptions.editConfig as Record<string, unknown>),
        enabled: isEditable(),
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
