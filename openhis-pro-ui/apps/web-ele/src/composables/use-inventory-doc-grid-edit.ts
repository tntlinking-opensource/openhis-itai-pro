import type { Ref } from 'vue';

import { ref } from 'vue';

import type { InventoryDocGridEditConfig } from '#/utils/inventory/inventory-doc-grid-edit';
import {
  activateInventoryDocGridCell,
  clearInventoryDocGridPersistedFormRows,
  createInventoryDocGridEditConfig,
  handleInventoryDocGridCellClick,
  handleInventoryDocGridKeydown,
  handleInventoryDocGridSurfaceMouseDown,
  InventoryFormModeRowRegistry,
  onInventoryDocGridProductSelected,
  patchInventoryDocGridKeepSource,
  refreshInventoryDocGridRow,
  syncInventoryRowFormModeComplete,
} from '#/utils/inventory/inventory-doc-grid-edit';

export interface UseInventoryDocGridEditOptions<TRow extends object> {
  getGridApi: () => { grid?: unknown } | null | undefined;
  gridWrapRef: Ref<HTMLElement | null>;
  documentEditable: Ref<boolean> | (() => boolean);
  config: InventoryDocGridEditConfig<TRow>;
  collectRows?: () => TRow[];
  enableKeyboard?: boolean;
}

/** 库房/药房明细表：row 编辑态 + 未完成行常驻表单 + 方向键导航 */
export function useInventoryDocGridEdit<TRow extends object>(
  options: UseInventoryDocGridEditOptions<TRow>,
) {
  const formModeRegistry = new InventoryFormModeRowRegistry();
  const lastEditField = ref<string | undefined>(undefined);

  const isEditable = () =>
    typeof options.documentEditable === 'function'
      ? options.documentEditable()
      : options.documentEditable.value;

  function buildSession() {
    return {
      config: options.config,
      formModeRegistry,
      lastEditField,
      gridApi: options.getGridApi(),
      gridWrap: options.gridWrapRef.value,
      documentEditable: isEditable,
      collectRows: options.collectRows,
    };
  }

  /** vxe editConfig（row 模式、不 autoClear） */
  function patchEditConfig(base?: Record<string, unknown>) {
    return {
      ...base,
      ...createInventoryDocGridEditConfig(isEditable(), isEditable, options.config.editableFields),
    };
  }

  /** gridOptions 需 keepSource，reloadRow 刷新未完成行 cell 才生效 */
  function patchGridOptions(base: Record<string, unknown>) {
    return patchInventoryDocGridKeepSource({
      ...base,
      editConfig: patchEditConfig(base.editConfig as Record<string, unknown> | undefined),
    });
  }

  /** 是否以表单展示（含未完成常驻态） */
  function shouldShowRowAsForm(row: TRow): boolean {
    return formModeRegistry.shouldShowForm(row, options.config.rowKey, isEditable());
  }

  /** 行数据变更后同步完成态 */
  function syncRowFormMode(row: TRow): void {
    void syncInventoryRowFormModeComplete(buildSession(), row);
  }

  /** 选品完成 */
  async function onProductSelected(row: TRow): Promise<void> {
    await onInventoryDocGridProductSelected(buildSession(), row);
  }

  /** 进入指定行/列 */
  async function activateCell(row: TRow, field: string): Promise<void> {
    await activateInventoryDocGridCell(buildSession(), row, field);
  }

  /** 新行 / 批量行进入表单模式 */
  async function enterRowFormMode(row: TRow, focusField?: string): Promise<void> {
    formModeRegistry.enter(row, options.config.rowKey);
    await activateInventoryDocGridCell(
      buildSession(),
      row,
      focusField ?? options.config.firstFillFieldAfterPick,
    );
  }

  /** 重置（换单、空态、只读） */
  function resetFormMode(): void {
    const rows = options.collectRows?.() ?? [];
    formModeRegistry.clear();
    lastEditField.value = undefined;
    void clearInventoryDocGridPersistedFormRows(options.getGridApi(), rows);
  }

  function onCellClick(params: {
    column?: { field?: string; type?: string };
    row: TRow;
    $event?: Event;
  }): void {
    handleInventoryDocGridCellClick(buildSession(), params);
  }

  function onSurfaceMouseDown(ev: MouseEvent): void {
    handleInventoryDocGridSurfaceMouseDown(buildSession(), ev);
  }

  async function onGridKeydown(event: KeyboardEvent): Promise<void> {
    if (options.enableKeyboard === false) return;
    await handleInventoryDocGridKeydown(buildSession(), event);
  }

  function clearGridEdit(): void {
    void (options.getGridApi()?.grid as { clearEdit?: () => void } | undefined)?.clearEdit?.();
  }

  /** 批量添加等多行：仅标记表单态，不抢当前编辑行 */
  function markRowFormMode(row: TRow): void {
    formModeRegistry.enter(row, options.config.rowKey);
    void refreshInventoryDocGridRow(options.getGridApi(), row);
  }

  return {
    patchEditConfig,
    patchGridOptions,
    shouldShowRowAsForm,
    syncRowFormMode,
    onProductSelected,
    activateCell,
    enterRowFormMode,
    markRowFormMode,
    resetFormMode,
    onCellClick,
    onSurfaceMouseDown,
    onGridKeydown,
    clearGridEdit,
    lastEditField,
  };
}
