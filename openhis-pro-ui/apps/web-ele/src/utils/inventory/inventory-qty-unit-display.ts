/**
 * 库存单据明细：数量 + 单位合并单元格展示（与 vxe `InventoryQtyUnitCell` 配套）。
 */

type UnitListItem = { label?: string; value: string; value_dictText?: string };

/** 从 unitList + bandKind / 单位编码解析展示文案 */
export function inventoryUnitLabelFromRow(row: Record<string, unknown>, unitField: string): string {
  const unitVal = row[unitField];
  const unitList = row.unitList as UnitListItem[] | undefined;
  if (Array.isArray(unitList) && unitList.length > 0) {
    const hit = unitList.find((u) => String(u.value) === String(unitVal ?? ''));
    if (hit) {
      return String(hit.label ?? hit.value_dictText ?? hit.value ?? '').trim();
    }
  }
  return String(unitVal ?? '').trim();
}

/** 数量 + 单位合并展示，如 `5 盒` */
export function formatInventoryQtyUnitCellText(
  row: Record<string, unknown>,
  qtyField: string,
  unitField: string,
): string {
  const q = Number(row[qtyField]);
  const qtyPart = Number.isFinite(q) ? String(q) : '';
  const unitPart = inventoryUnitLabelFromRow(row, unitField);
  if (!qtyPart && !unitPart) return '';
  if (!unitPart) return qtyPart;
  if (!qtyPart) return unitPart;
  return `${qtyPart} ${unitPart}`;
}

/** 金额保留两位小数；非数字原样 trim 返回。 */
export function formatInventoryMoneyTwoDecimals(value: unknown): string {
  if (value === undefined || value === null || value === '') return '';
  const n = Number(value);
  if (Number.isFinite(n)) return n.toFixed(2);
  return String(value).trim();
}

/** 单价回显：两位小数 + `元`，如 `15.00 元`（与总金额一致，不拼接计量单位「盒/粒」） */
export function formatInventoryUnitPriceDisplay(
  row: Record<string, unknown>,
  priceField: string,
  _unitField?: string,
): string {
  const money = formatInventoryMoneyTwoDecimals(row[priceField]);
  if (!money) return '';
  return `${money} 元`;
}

/** 总金额回显：两位小数 + `元`，如 `150.00 元` */
export function formatInventoryTotalAmountDisplay(
  row: Record<string, unknown>,
  amountField: string,
): string {
  const money = formatInventoryMoneyTwoDecimals(row[amountField]);
  if (!money) return '';
  return `${money} 元`;
}
