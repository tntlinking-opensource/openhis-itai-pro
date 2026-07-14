import type { Ref } from 'vue';

import type { useInventoryDocGridEdit } from '#/composables/use-inventory-doc-grid-edit';

export interface UseInvDocGridRowOpsOptions<TRow extends object> {
  documentEditable: Ref<boolean> | (() => boolean);
  getGridApi: () => { grid?: unknown } | null | undefined;
  gridEdit: Pick<ReturnType<typeof useInventoryDocGridEdit<TRow>>, 'enterRowFormMode'>;
  createEmptyRow: () => TRow;
  /** 行是否已选品（默认按 itemId 判断，由调用方传入 isPresentItemId 包装） */
  hasProduct: (row: TRow) => boolean;
  /** 新增空行后聚焦列，默认 medicinePick */
  newRowFocusField?: string;
}

/** 库房明细表：添加行 / 删除行 / 清理未选品空行 */
export function useInvDocGridRowOps<TRow extends object>(
  options: UseInvDocGridRowOpsOptions<TRow>,
) {
  const newRowFocusField = options.newRowFocusField ?? 'medicinePick';

  function isEditable(): boolean {
    return typeof options.documentEditable === 'function'
      ? options.documentEditable()
      : options.documentEditable.value;
  }

  /** 行尚未选品 */
  function isRowWithoutProduct(row: TRow): boolean {
    return !options.hasProduct(row);
  }

  /** 插入空行并进入选品列 */
  async function addItem(): Promise<void> {
    if (!isEditable()) return;
    const $grid = options.getGridApi()?.grid as
      | {
          $nextTick?: () => Promise<void>;
          insertAt?: (record: TRow, pos: null) => Promise<{ row?: TRow }>;
          scrollToRow?: (row: TRow) => Promise<void>;
        }
      | undefined;
    if (!$grid?.insertAt) return;
    const record = options.createEmptyRow();
    const { row } = await $grid.insertAt(record, null);
    await $grid.$nextTick?.();
    if (row) {
      await $grid.scrollToRow?.(row);
      await options.gridEdit.enterRowFormMode(row, newRowFocusField);
    }
  }

  /** 删除勾选行或单行 */
  async function deleteItem(row: 'select' | TRow): Promise<void> {
    if (!isEditable()) return;
    const $grid = options.getGridApi()?.grid as
      | {
          getCheckboxRecords?: () => TRow[];
          remove?: (rows: TRow | TRow[]) => Promise<void>;
        }
      | undefined;
    if (!$grid?.remove) return;
    const checked = $grid.getCheckboxRecords?.() ?? [];
    if (row === 'select' && checked.length === 0) return;
    await $grid.remove(row === 'select' ? checked : row);
  }

  /** 批量回填前移除未选品的占位行 */
  async function removeEmptyRows(): Promise<void> {
    const $grid = options.getGridApi()?.grid as
      | {
          getFullData?: () => TRow[];
          remove?: (rows: TRow[]) => Promise<void>;
        }
      | undefined;
    if (!$grid?.getFullData || !$grid.remove) return;
    const rows = $grid.getFullData() ?? [];
    const emptyRows = rows.filter((r) => isRowWithoutProduct(r));
    if (emptyRows.length === 0) return;
    await $grid.remove(emptyRows);
  }

  return {
    addItem,
    deleteItem,
    isRowWithoutProduct,
    removeEmptyRows,
  };
}
