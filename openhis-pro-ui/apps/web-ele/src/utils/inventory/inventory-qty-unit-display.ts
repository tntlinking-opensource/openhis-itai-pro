/**
 * 库存单据明细：数量 + 单位合并单元格展示（与 vxe `InventoryQtyUnitCell` 配套）。
 * 默认单价/金额 UI 展示 2 位（BR-MON-001）；
 * **中药**（medication_category_code=`4`）单价展示 3 位；
 * **期初单**单价特例展示 6 位（与 MONEY_CALC_DP 对齐）。
 */

import {
  Decimal,
  MONEY_CALC_DP,
  MONEY_DISPLAY_DP,
  MONEY_TCM_UNIT_PRICE_DP,
  formatMoneyDisplay,
  toMoneyDecimal,
} from '#/utils/format/money-scale';

type UnitListItem = { label?: string; value: string; value_dictText?: string };

/** 与医嘱域 HERBAL_MEDICATION_CATEGORY_CODE / medication_category_code 中草药码一致 */
export const INVENTORY_HERBAL_MEDICATION_CATEGORY_CODE = '4';

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

/** 金额展示两位小数（HALF_UP）；空值返回空串；非数字原样 trim。 */
export function formatInventoryMoneyTwoDecimals(value: unknown): string {
  if (value === undefined || value === null || value === '') return '';
  const n = Number(value);
  if (Number.isFinite(n)) return formatMoneyDisplay(n);
  return String(value).trim();
}

/**
 * 从行 / 选品 / 目录提示解析药品分类码（优先行上 `categoryCode`，其次 `productCatalog`）。
 */
export function resolveInventoryRowCategoryCode(source?: null | Record<string, unknown>): string {
  if (!source) return '';
  const direct = String(source.categoryCode ?? '').trim();
  if (direct) return direct;
  const catalog = source.productCatalog as null | Record<string, unknown> | undefined;
  if (catalog) {
    const nested = String(catalog.categoryCode ?? '').trim();
    if (nested) return nested;
  }
  return '';
}

/** 是否中药饮片/中草药行（medication_category_code = 4） */
export function isInventoryHerbalMedicationRow(row?: null | Record<string, unknown>): boolean {
  return resolveInventoryRowCategoryCode(row) === INVENTORY_HERBAL_MEDICATION_CATEGORY_CODE;
}

/**
 * 单价展示小数位：显式传入优先（如期初 6）；否则中药 3、其余 2。
 */
export function resolveInventoryUnitPriceDisplayDp(
  row?: null | Record<string, unknown>,
  explicitDp?: number,
): number {
  if (explicitDp !== undefined && Number.isFinite(explicitDp) && explicitDp >= 0) {
    return explicitDp;
  }
  if (isInventoryHerbalMedicationRow(row)) {
    return MONEY_TCM_UNIT_PRICE_DP;
  }
  return MONEY_DISPLAY_DP;
}

/**
 * 将分类码写入行模型，供单价单元格按中药三位展示。
 * `source` 可为选品行、详情行或 `productCatalog` 自身。
 */
export function applyInventoryRowCategoryCode(
  row: Record<string, unknown>,
  source?: null | Record<string, unknown>,
): void {
  const code = resolveInventoryRowCategoryCode(source ?? row);
  if (code) {
    row.categoryCode = code;
  }
}

/**
 * 单价回显 + `元`。
 * @param displayDp 展示小数位：默认 2；中药 3；期初单传 {@link MONEY_CALC_DP}（6）
 */
export function formatInventoryUnitPriceDisplay(
  row: Record<string, unknown>,
  priceField: string,
  _unitField?: string,
  displayDp: number = MONEY_DISPLAY_DP,
): string {
  const money = formatInventoryUnitPriceDecimals(row[priceField], displayDp);
  if (!money) return '';
  return `${money} 元`;
}

/**
 * 单价展示小数位；空值返回空串；非数字原样 trim。
 * 按 `displayDp` HALF_UP 格式化（2 / 3 / 6 等），禁止一律压成两位。
 */
export function formatInventoryUnitPriceDecimals(
  value: unknown,
  displayDp: number = MONEY_DISPLAY_DP,
): string {
  if (value === undefined || value === null || value === '') return '';
  const n = Number(value);
  if (!Number.isFinite(n)) return String(value).trim();
  const dp =
    Number.isFinite(displayDp) && displayDp >= 0 ? Math.floor(displayDp) : MONEY_DISPLAY_DP;
  return toMoneyDecimal(n).toDecimalPlaces(dp, Decimal.ROUND_HALF_UP).toFixed(dp);
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

export { MONEY_CALC_DP, MONEY_DISPLAY_DP, MONEY_TCM_UNIT_PRICE_DP };
