import { nextTick } from 'vue';

import {
  collectFormFocusableElements,
  EXTENDED_FORM_KEYBOARD_SELECTORS,
  focusFormControl,
  isFormSelectDropdownTarget,
  isFormSelectExpanded,
  isFormSelectTarget,
} from '#/hooks/use-form-keyboard-navigation';

/** 行上标记：非 vxe 当前编辑行时仍以表单控件展示（未完成录入） */
export const INV_FORM_MODE_ROW_FLAG = '__invFormMode';

export interface InventoryDocGridVmLike {
  clearEdit?: () => void | Promise<void>;
  getCellElement?: (row: unknown, field: string) => HTMLElement | null;
  getColumnByField?: (field: string) => { field?: string; id?: string } | null;
  getColumnById?: (colid: string) => { field?: string } | null;
  getColumnNode?: (cell: Element) => { item?: { field?: string } } | null;
  getEditRecord?: () => { column?: { field?: string }; row?: unknown } | null;
  getFullData?: () => unknown[];
  getRowNode?: (tr: Element) => { row?: unknown } | null;
  setEditRow?: (row: unknown) => void | Promise<void>;
}

export interface InventoryDocGridEditConfig<TRow extends object> {
  rowKey: (row: TRow) => string;
  productField: string;
  editFieldOrder: readonly string[];
  editableFields: ReadonlySet<string>;
  verticalNavFields: ReadonlySet<string>;
  firstFillFieldAfterPick: string;
  hasProduct: (row: TRow) => boolean;
  isRowComplete: (row: TRow) => boolean;
  /** 方向键上下换行时是否跳过无产品行，默认 true */
  skipRowWithoutProduct?: (row: TRow) => boolean;
}

/** 解析 useVbenVxeGrid 的 gridApi.grid */
export function resolveInventoryDocGridVm(
  gridApi: { grid?: unknown } | null | undefined,
): InventoryDocGridVmLike | undefined {
  return gridApi?.grid as InventoryDocGridVmLike | undefined;
}

/** 刷新单行 cell 渲染（依赖 grid keepSource，见 patchInventoryDocGridKeepSource） */
export async function refreshInventoryDocGridRow(
  gridApi: { grid?: unknown } | null | undefined,
  row: object,
): Promise<void> {
  const g = gridApi?.grid as
    | { reloadRow?: (r: object, record?: object) => void | Promise<void> }
    | undefined;
  if (!g?.reloadRow) return;
  await g.reloadRow(row, row);
}

/** 清除所有行的常驻表单标记并刷新（换单、只读） */
export async function clearInventoryDocGridPersistedFormRows(
  gridApi: { grid?: unknown } | null | undefined,
  rows: object[],
): Promise<void> {
  const touched: object[] = [];
  for (const row of rows) {
    if (shouldInventoryRowShowPersistedForm(row)) {
      setInventoryRowPersistedForm(row, false);
      touched.push(row);
    }
  }
  await Promise.all(touched.map((row) => refreshInventoryDocGridRow(gridApi, row)));
}

/** vxe-table reloadRow 依赖 keep-source */
export function patchInventoryDocGridKeepSource(
  gridOptions: Record<string, unknown>,
): Record<string, unknown> {
  return { ...gridOptions, keepSource: true };
}

/** 非 vxe 编辑行时是否仍以表单展示 */
export function shouldInventoryRowShowPersistedForm(row: object): boolean {
  return (row as Record<string, unknown>)[INV_FORM_MODE_ROW_FLAG] === true;
}

/** 设置/清除行常驻表单标记 */
export function setInventoryRowPersistedForm(row: object, enabled: boolean): void {
  const target = row as Record<string, unknown>;
  if (enabled) {
    target[INV_FORM_MODE_ROW_FLAG] = true;
  } else {
    delete target[INV_FORM_MODE_ROW_FLAG];
  }
}

/** 关闭行内选品下拉（避免 autofocus / 方向键误弹项目名称） */
export function dismissInventoryProductPickDropdown(): void {
  for (const pulldown of document.querySelectorAll('.vxe-pulldown.is--active')) {
    pulldown.classList.remove('is--active');
    const input = pulldown.querySelector('.vxe-input--inner') as HTMLElement | null;
    input?.blur();
  }
}

/** 已选品行：方向键切列时跳过选品列 */
export function filterInventoryEditFieldOrder<TRow>(
  order: readonly string[],
  row: TRow,
  productField: string,
  hasProduct: (row: TRow) => boolean,
): string[] {
  return order.filter((field) => !(field === productField && hasProduct(row)));
}

function findGridRowElement(
  gridVm: InventoryDocGridVmLike,
  row: unknown,
  gridWrap?: HTMLElement | null,
): HTMLElement | null {
  if (!gridWrap || !gridVm.getRowNode) return null;
  for (const tr of gridWrap.querySelectorAll('tr.vxe-body--row')) {
    const node = gridVm.getRowNode(tr);
    if (node?.row === row) {
      return tr as HTMLElement;
    }
  }
  return null;
}

function findGridCellScope(
  gridVm: InventoryDocGridVmLike,
  row: unknown,
  rowEl: HTMLElement | null,
  field: string,
): HTMLElement | null {
  const fromApi = gridVm.getCellElement?.(row, field);
  if (fromApi) return fromApi;

  const colid = gridVm.getColumnByField?.(field)?.id ?? field;
  if (!rowEl) return null;
  return (
    (rowEl.querySelector(`td[colid="${colid}"]`) as HTMLElement | null) ??
    (rowEl.querySelector(`.vxe-body--column[colid="${colid}"]`) as HTMLElement | null) ??
    (rowEl.querySelector(`.vxe-body-cell--wrapper[colid="${colid}"]`) as HTMLElement | null)
  );
}

function resolveEditFieldFromTarget(
  target: HTMLElement,
  gridVm: InventoryDocGridVmLike,
  editableFields: ReadonlySet<string>,
): string | undefined {
  const cell = target.closest(
    'td[colid], .vxe-body--column[colid], .vxe-body-cell--wrapper[colid]',
  ) as HTMLElement | null;
  if (!cell) return undefined;

  const fieldFromNode = gridVm.getColumnNode?.(cell)?.item?.field;
  if (fieldFromNode && editableFields.has(fieldFromNode)) {
    return fieldFromNode;
  }

  const colid = cell.getAttribute('colid');
  if (!colid) return undefined;

  const fieldFromId = gridVm.getColumnById?.(colid)?.field;
  if (fieldFromId && editableFields.has(fieldFromId)) {
    return fieldFromId;
  }

  if (editableFields.has(colid)) {
    return colid;
  }
  return undefined;
}

function forceFocusCellInput(input: HTMLInputElement): void {
  input.focus({ preventScroll: true });
  input.select();
  requestAnimationFrame(() => {
    input.focus({ preventScroll: true });
    input.select();
    setTimeout(() => {
      input.focus({ preventScroll: true });
      input.select();
    }, 40);
  });
}

function collectGridCellFocusables(cell: HTMLElement, skipSelect: boolean): HTMLElement[] {
  const list = collectFormFocusableElements(cell, EXTENDED_FORM_KEYBOARD_SELECTORS, true);
  if (!skipSelect) return list;
  return list.filter((el) => !isFormSelectTarget(el));
}

function resolveEditRowFromTarget<TRow>(
  target: HTMLElement,
  gridVm: InventoryDocGridVmLike,
): TRow | undefined {
  if (!gridVm.getRowNode) return undefined;
  const rowEl = target.closest('tr.vxe-body--row');
  if (!rowEl) return undefined;
  return gridVm.getRowNode(rowEl)?.row as TRow | undefined;
}

function shouldKeepCaretNavigation(event: KeyboardEvent, target: HTMLElement): boolean {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
    return false;
  }
  if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement)) {
    return false;
  }
  if (target.readOnly || target.disabled || target.closest('.el-select, .el-tree-select')) {
    return false;
  }
  const { selectionEnd, selectionStart, value } = target;
  if (selectionStart === null || selectionEnd === null || selectionStart !== selectionEnd) {
    return selectionStart !== selectionEnd;
  }
  if (event.key === 'ArrowLeft') {
    return selectionStart > 0;
  }
  return selectionEnd < value.length;
}

function isFocusInGridCell<TRow extends object>(
  field: string,
  row: TRow,
  gridVm: InventoryDocGridVmLike,
  gridWrap: HTMLElement | null,
  editableFields: ReadonlySet<string>,
): boolean {
  const active = document.activeElement as HTMLElement | null;
  if (!active) return false;
  if (resolveEditFieldFromTarget(active, gridVm, editableFields) !== field) return false;
  const rowEl = findGridRowElement(gridVm, row, gridWrap);
  return !!rowEl?.contains(active);
}

/** 未完成行 ID 集合：失焦后仍保持表单展示 */
export class InventoryFormModeRowRegistry {
  private readonly ids = new Set<string>();

  has(id: string): boolean {
    return this.ids.has(id);
  }

  enter<TRow extends object>(row: TRow, rowKey: (row: TRow) => string): void {
    const id = rowKey(row);
    this.ids.add(id);
    setInventoryRowPersistedForm(row, true);
  }

  exit<TRow extends object>(row: TRow, rowKey: (row: TRow) => string): void {
    const id = rowKey(row);
    this.ids.delete(id);
    setInventoryRowPersistedForm(row, false);
  }

  clear(): void {
    this.ids.clear();
  }

  shouldShowForm<TRow extends object>(
    row: TRow,
    rowKey: (row: TRow) => string,
    documentEditable: boolean,
  ): boolean {
    return documentEditable && this.ids.has(rowKey(row));
  }
}

export interface InventoryDocGridEditSession<TRow extends object> {
  config: InventoryDocGridEditConfig<TRow>;
  formModeRegistry: InventoryFormModeRowRegistry;
  lastEditField: { value: string | undefined };
  gridApi: { grid?: unknown } | null | undefined;
  gridWrap: HTMLElement | null | undefined;
  documentEditable: () => boolean;
  collectRows?: () => TRow[];
}

/** 同步完成态：已完成则退出常驻表单并刷新展示 */
export async function syncInventoryRowFormModeComplete<TRow extends object>(
  session: InventoryDocGridEditSession<TRow>,
  row: TRow,
): Promise<void> {
  const { config, formModeRegistry } = session;
  if (!config.isRowComplete(row)) return;
  const wasPersisted =
    formModeRegistry.has(config.rowKey(row)) || shouldInventoryRowShowPersistedForm(row);
  if (!wasPersisted) return;
  formModeRegistry.exit(row, config.rowKey);
  await refreshInventoryDocGridRow(session.gridApi, row);
}

/** 确保行进入 vxe row 编辑（同行不重复 setEditRow） */
export async function ensureInventoryDocGridRowEdit<TRow extends object>(
  session: InventoryDocGridEditSession<TRow>,
  row: TRow,
): Promise<boolean> {
  const g = resolveInventoryDocGridVm(session.gridApi);
  if (!g?.setEditRow) return false;
  const editingRow = g.getEditRecord?.()?.row;
  if (editingRow === row) return true;
  if (editingRow) {
    const prev = editingRow as TRow;
    if (!session.config.isRowComplete(prev)) {
      session.formModeRegistry.enter(prev, session.config.rowKey);
      await refreshInventoryDocGridRow(session.gridApi, prev);
    } else {
      session.formModeRegistry.exit(prev, session.config.rowKey);
    }
    await g.clearEdit?.();
    await nextTick();
  }
  await g.setEditRow(row);
  await nextTick();
  return true;
}

/** 聚焦指定列内主输入控件 */
export async function focusInventoryDocGridField<TRow extends object>(
  session: InventoryDocGridEditSession<TRow>,
  row: TRow,
  field: string,
): Promise<void> {
  const g = resolveInventoryDocGridVm(session.gridApi);
  if (!g) return;

  dismissInventoryProductPickDropdown();

  for (let attempt = 0; attempt < 4; attempt++) {
    await nextTick();
    if (attempt > 0) {
      await new Promise<void>((resolve) => {
        setTimeout(resolve, 40);
      });
    }

    const rowEl = findGridRowElement(g, row, session.gridWrap ?? null);
    const scope = findGridCellScope(g, row, rowEl, field);
    if (!scope) continue;

    const focusables = collectGridCellFocusables(scope, true);
    if (focusables.length === 0) continue;

    let target = focusables[0];
    if (field === '_qtyUnit') {
      target =
        focusables.find((el) => !!el.closest('.el-input-number')) ??
        focusables.find((el) => el.tagName === 'INPUT') ??
        focusables[0];
    }

    if (
      target instanceof HTMLInputElement &&
      target.type !== 'hidden' &&
      !target.closest('.el-select, .el-tree-select')
    ) {
      forceFocusCellInput(target);
      return;
    }

    focusFormControl(target);
    if (target instanceof HTMLInputElement && target.type !== 'hidden') {
      target.select();
    }
    return;
  }
}

/** 进入行表单模式并聚焦指定列 */
export async function activateInventoryDocGridCell<TRow extends object>(
  session: InventoryDocGridEditSession<TRow>,
  row: TRow,
  field: string,
): Promise<void> {
  const { config } = session;
  session.lastEditField.value = field;
  session.formModeRegistry.enter(row, config.rowKey);
  void syncInventoryRowFormModeComplete(session, row);

  if (field !== config.productField) {
    dismissInventoryProductPickDropdown();
  }

  await ensureInventoryDocGridRowEdit(session, row);

  if (field !== config.productField) {
    await focusInventoryDocGridField(session, row, field);
  }
}

/** 选品后：整行表单 + 聚焦首填列 */
export async function onInventoryDocGridProductSelected<TRow extends object>(
  session: InventoryDocGridEditSession<TRow>,
  row: TRow,
): Promise<void> {
  await activateInventoryDocGridCell(session, row, session.config.firstFillFieldAfterPick);
}

/** 明细表 cellClick */
export function handleInventoryDocGridCellClick<TRow extends object>(
  session: InventoryDocGridEditSession<TRow>,
  params: {
    column?: { field?: string; type?: string };
    row: TRow;
  },
): void {
  if (!session.documentEditable()) return;
  const g = resolveInventoryDocGridVm(session.gridApi);
  if (!g?.setEditRow) return;

  const column = params.column;
  if (!column || column.type === 'checkbox' || column.type === 'seq') return;
  const field = column.field;
  if (!field || !session.config.editableFields.has(field)) return;

  session.lastEditField.value = field;
  session.formModeRegistry.enter(params.row, session.config.rowKey);
  void syncInventoryRowFormModeComplete(session, params.row);

  const editingRow = g.getEditRecord?.()?.row as TRow | undefined;

  if (editingRow === params.row) {
    if (
      field !== session.config.productField &&
      !isFocusInGridCell(
        field,
        params.row,
        g,
        session.gridWrap ?? null,
        session.config.editableFields,
      )
    ) {
      dismissInventoryProductPickDropdown();
      void focusInventoryDocGridField(session, params.row, field);
    }
    return;
  }

  void nextTick(async () => {
    await ensureInventoryDocGridRowEdit(session, params.row);
    if (field !== session.config.productField) {
      dismissInventoryProductPickDropdown();
      await focusInventoryDocGridField(session, params.row, field);
    }
  });
}

/** 点击表格外：已完成行才退出编辑 */
export function handleInventoryDocGridSurfaceMouseDown<TRow extends object>(
  session: InventoryDocGridEditSession<TRow>,
  ev: MouseEvent,
): void {
  if (!session.documentEditable()) return;
  const target = ev.target as HTMLElement;
  if (target.closest('.el-popper, .el-select__popper, .el-picker-panel, .el-dialog, .el-overlay')) {
    return;
  }

  const g = resolveInventoryDocGridVm(session.gridApi);
  if (!g) return;

  const editingRow = g.getEditRecord?.()?.row as TRow | undefined;
  if (!editingRow) return;

  const rowEl = target.closest('tr.vxe-body--row');
  if (rowEl) {
    const node = g.getRowNode?.(rowEl);
    if (node?.row === editingRow) return;
    return;
  }

  if (!session.config.isRowComplete(editingRow)) {
    session.formModeRegistry.enter(editingRow, session.config.rowKey);
    void refreshInventoryDocGridRow(session.gridApi, editingRow).then(() => g.clearEdit?.());
    return;
  }

  session.formModeRegistry.exit(editingRow, session.config.rowKey);
  void refreshInventoryDocGridRow(session.gridApi, editingRow).then(() => g.clearEdit?.());
}

function findNeighborRow<TRow extends object>(
  rows: TRow[],
  rowIndex: number,
  delta: -1 | 1,
  hasProduct: (row: TRow) => boolean,
): TRow | undefined {
  for (let i = rowIndex + delta; i >= 0 && i < rows.length; i += delta) {
    const row = rows[i];
    if (row && hasProduct(row)) {
      return row;
    }
  }
  return undefined;
}

/** 明细表方向键导航 */
export async function handleInventoryDocGridKeydown<TRow extends object>(
  session: InventoryDocGridEditSession<TRow>,
  event: KeyboardEvent,
): Promise<void> {
  if (!session.documentEditable()) return;
  const key = event.key;
  if (!['ArrowDown', 'ArrowLeft', 'ArrowRight', 'ArrowUp'].includes(key)) {
    return;
  }

  const target = event.target as HTMLElement;
  if (
    isFormSelectDropdownTarget(target) ||
    isFormSelectExpanded(target) ||
    target.closest('.el-picker-panel, .el-date-picker, .vxe-pulldown, .vxe-pulldown--panel')
  ) {
    return;
  }

  const g = resolveInventoryDocGridVm(session.gridApi);
  if (!g?.setEditRow) return;

  const editRec = g.getEditRecord?.();
  const currentRow = (editRec?.row ?? resolveEditRowFromTarget<TRow>(target, g)) as
    | TRow
    | undefined;

  const fromDom = resolveEditFieldFromTarget(target, g, session.config.editableFields);
  const currentField =
    fromDom ??
    session.lastEditField.value ??
    (editRec?.column?.field && session.config.editableFields.has(editRec.column.field)
      ? editRec.column.field
      : undefined);

  if (!currentRow || !currentField) return;

  session.lastEditField.value = currentField;

  if (currentField === session.config.productField) {
    return;
  }

  const rows = (g.getFullData?.() ?? session.collectRows?.() ?? []) as TRow[];
  const rowIndex = rows.indexOf(currentRow);
  if (rowIndex === -1) return;

  if (key === 'ArrowUp' || key === 'ArrowDown') {
    if (!session.config.verticalNavFields.has(currentField)) {
      return;
    }
    const neighbor = findNeighborRow(
      rows,
      rowIndex,
      key === 'ArrowUp' ? -1 : 1,
      session.config.hasProduct,
    );
    if (!neighbor) return;
    event.preventDefault();
    event.stopPropagation();
    await activateInventoryDocGridCell(session, neighbor, currentField);
    return;
  }

  if (shouldKeepCaretNavigation(event, target)) {
    return;
  }

  const activeCell = (target.closest('.vxe-body--column') ??
    target.closest('td[colid]') ??
    target.closest('.vxe-body-cell--wrapper')) as HTMLElement | null;
  if (activeCell) {
    const focusables = collectGridCellFocusables(activeCell, true);
    const idx = focusables.findIndex((el) => el === target || el.contains(target));
    if (idx !== -1) {
      const delta = key === 'ArrowRight' ? 1 : -1;
      const nextIndex = idx + delta;
      if (nextIndex >= 0 && nextIndex < focusables.length) {
        event.preventDefault();
        event.stopPropagation();
        focusFormControl(focusables[nextIndex]);
        return;
      }
    }
  }

  const fieldOrder = filterInventoryEditFieldOrder(
    session.config.editFieldOrder,
    currentRow,
    session.config.productField,
    session.config.hasProduct,
  );
  const fieldIndex = fieldOrder.indexOf(currentField);
  if (fieldIndex === -1) return;

  const nextFieldIndex = key === 'ArrowRight' ? fieldIndex + 1 : fieldIndex - 1;
  if (nextFieldIndex < 0 || nextFieldIndex >= fieldOrder.length) {
    return;
  }

  event.preventDefault();
  event.stopPropagation();
  await activateInventoryDocGridCell(session, currentRow, fieldOrder[nextFieldIndex] as string);
}

/** row 模式 editConfig 基线 */
export function createInventoryDocGridEditConfig(
  enabled: boolean,
  isEditable: () => boolean,
  editableFields?: ReadonlySet<string>,
): {
  autoClear: boolean;
  beforeEditMethod: (params: { column?: { field?: string; type?: string } }) => boolean;
  enabled: boolean;
  mode: 'row';
  trigger: 'click';
} {
  return {
    enabled,
    trigger: 'click',
    mode: 'row',
    autoClear: false,
    beforeEditMethod: ({ column }) => {
      if (!isEditable()) return false;
      const type = column?.type;
      if (type === 'checkbox' || type === 'seq') return false;
      const field = column?.field;
      if (field && editableFields && !editableFields.has(field)) return false;
      return true;
    },
  };
}
