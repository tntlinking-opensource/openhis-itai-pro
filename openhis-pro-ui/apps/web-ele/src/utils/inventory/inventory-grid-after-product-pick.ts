import { nextTick } from 'vue';

import {
  collectFormFocusableElements,
  EXTENDED_FORM_KEYBOARD_SELECTORS,
  focusFormControl,
} from '#/hooks/use-form-keyboard-navigation';

export interface InventoryGridVmLike {
  clearEdit?: () => void | Promise<void>;
  getRowNode?: (tr: Element) => { row?: unknown } | null;
  setEditCell?: (row: unknown, field: string) => void | Promise<void>;
  setEditRow?: (row: unknown) => void | Promise<void>;
}

/** 从 useVbenVxeGrid 返回的 gridApi 解析 vxe 实例 */
export function resolveInventoryGridVm(
  gridApi: { grid?: unknown } | null | undefined,
): InventoryGridVmLike | undefined {
  return gridApi?.grid as InventoryGridVmLike | undefined;
}

/** 关闭行内选品下拉（避免 autofocus / 方向键切格时误弹项目名称下拉） */
export function dismissInventoryProductPickDropdown() {
  for (const pulldown of document.querySelectorAll('.vxe-pulldown.is--active')) {
    pulldown.classList.remove('is--active');
    const input = pulldown.querySelector('.vxe-input--inner') as HTMLElement | null;
    input?.blur();
  }
}

/** 在表格 DOM 中定位指定数据行 */
function findGridRowElement(
  gridVm: InventoryGridVmLike,
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

/** 定位指定列内的主输入框（数量列优先 ElInputNumber） */
function findCellPrimaryInput(rowEl: HTMLElement | null, field: string): HTMLInputElement | null {
  const cell = rowEl?.querySelector(`td[colid="${field}"]`) as HTMLElement | null;
  const scope = cell ?? rowEl;
  if (!scope) return null;
  return (
    (scope.querySelector('.el-input-number input:not([disabled])') as HTMLInputElement | null) ??
    (scope.querySelector('input:not([disabled]):not([type="hidden"])') as HTMLInputElement | null)
  );
}

/** 强制聚焦并选中输入框 */
function forceFocusCellInput(input: HTMLInputElement) {
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

/** 聚焦指定列内首个可填控件 */
async function focusInventoryGridField<T>(
  gridApi: { grid?: unknown } | null | undefined,
  row: T,
  focusField: string,
  gridWrap?: HTMLElement | null,
): Promise<void> {
  const g = resolveInventoryGridVm(gridApi);
  if (!g) return;

  await nextTick();
  dismissInventoryProductPickDropdown();

  const rowEl = findGridRowElement(g, row, gridWrap);
  const input = findCellPrimaryInput(rowEl, focusField);
  if (input) {
    forceFocusCellInput(input);
    return;
  }

  const activeCell = (rowEl?.querySelector(`td[colid="${focusField}"]`) ??
    document.querySelector('.vxe-body--column.col--active')) as HTMLElement | null;
  if (!activeCell) return;

  const focusables = collectFormFocusableElements(
    activeCell,
    EXTENDED_FORM_KEYBOARD_SELECTORS,
    true,
  );
  if (!focusables.length) return;

  const target =
    focusables.find((el) => !!el.closest('.el-input-number')) ??
    focusables.find((el) => el.tagName === 'INPUT') ??
    focusables[0];
  focusFormControl(target);
  if (target instanceof HTMLInputElement && target.type !== 'hidden') {
    target.select();
  }
}

/**
 * cell 模式：选品后激活首填列（仅当前格展示编辑控件，配合方向键切格）。
 * 用于进货/出库等带键盘导航的单据。
 */
export async function focusInventoryGridAfterProductPick<T>(
  gridApi: { grid?: unknown } | null | undefined,
  row: T,
  focusField: string,
  gridWrap?: HTMLElement | null,
): Promise<void> {
  const g = resolveInventoryGridVm(gridApi);
  if (!g?.setEditCell) return;

  dismissInventoryProductPickDropdown();
  await g.clearEdit?.();
  await nextTick();
  await g.setEditCell(row, focusField);
  await focusInventoryGridField(gridApi, row, focusField, gridWrap);
}

/**
 * row 模式：选品后整行进入编辑态（所有可填列同时展示），并聚焦首填列。
 * 用于订货/盘点/药房入库等原本即为 row 模式的单据。
 */
export async function activateInventoryGridRowAfterProductPick<T>(
  gridApi: { grid?: unknown } | null | undefined,
  row: T,
  focusField?: string,
  gridWrap?: HTMLElement | null,
): Promise<void> {
  const g = resolveInventoryGridVm(gridApi);
  if (!g?.setEditRow) return;

  dismissInventoryProductPickDropdown();
  await g.clearEdit?.();
  await nextTick();
  await g.setEditRow(row);
  if (!focusField) return;
  await focusInventoryGridField(gridApi, row, focusField, gridWrap);
}

/** 已选品行：方向键切列时跳过项目名称（避免误聚焦选品下拉） */
export function filterInventoryEditFieldOrder<T>(
  order: readonly string[],
  row: T,
  productField: string,
  hasProduct: (row: T) => boolean,
): string[] {
  return order.filter((field) => !(field === productField && hasProduct(row)));
}
