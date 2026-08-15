/**
 * 进销存单据访问约束：须拉上游 / 只读壳单 / 可自由编辑。
 * 口径见 whale-doc/health/InventoryAppService/INV-inventory-功能清单与改造状态.md §三。
 */
import { ElMessage } from 'element-plus';

import {
  applyHydratedInventoryDetailQtyToRow,
  applyInventoryRowQtyUnitList,
  hydrateInventoryDetailLineQtyForGrid,
  type InventoryQtyUnitListRow,
} from '#/utils/inventory/inventory-doc-grid-common';

/** 须从上游拉取的单据：欢迎空态引导文案 */
export const INV_DOC_UPSTREAM_WELCOME = {
  returnToSupplier: '退货单须关联已同意的进货单，可退明细将从进货单带入，不支持自由选品',
  returnToWarehouse: '可新建空白退库单自行选品，也可从已同意入库单带入可退明细',
  deptReturn: '可新建空白退库单自行选品，也可从已同意入库单带入可退明细',
  deptIssueReturn: '科室退领单须关联已同意的领用单，可退明细将从领用单带入，不支持自由选品',
} as const;

/** 须从上游单据拉取明细的单据提示文案 */
export const INV_DOC_UPSTREAM_HINT = {
  returnToSupplier: '药库退货单禁止自由选品，请先选择进货单并通过「从进货单选择」拉取可退明细',
  returnToWarehouse: '可直接在明细中选品退库；也可通过「从入库单选择」带入可退明细',
  deptReturn: '可直接在明细中选品退库；也可通过「从入库单选择」带入可退明细',
  deptIssueReturn: '科室退领单须关联已同意领用单，请点击「从领用单选择」拉取可退明细，禁止自由选品',
} as const;

/** 只读壳单（禁止新建/改单/删单）提示文案 */
export const INV_DOC_READONLY_SHELL_HINT = {
  stockIn: '入库单为系统自动生成的只读台账，禁止新建或修改',
  transferIn: '调入单为对侧调出 agree 自动生成的只读壳单，禁止新建或修改',
  returnStockIn: '退货入库单为退库 agree 自动生成的只读台账，禁止新建或修改',
} as const;

export function warnInventoryDocRestriction(message: string): void {
  ElMessage.warning(message);
}

export function hasNonEmptyBusNo(value?: null | number | string): boolean {
  return value !== undefined && value !== null && String(value).trim() !== '';
}

/** 可退/可拉明细行 → 网格行（退货/退库类） */
export interface ReturnableLinkLineDto {
  itemTable?: string;
  itemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  returnableQuantity?: number;
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  lotNumber?: string;
  traceNo?: string;
  reason?: string;
  remake?: string;
  price?: number;
  totalPrice?: number;
  productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown };
  unitBands?: unknown;
  unitBandsJson?: unknown;
}

export function mapReturnableLinkLineToGridRow<
  T extends InventoryQtyUnitListRow & Record<string, unknown>,
>(ln: ReturnableLinkLineDto, rowIdx: number, extra?: Partial<T>): T {
  const itemIdRaw = ln.itemId;
  const name = String(ln.productName ?? '').trim();
  const qtyRaw = ln.returnableQuantity ?? ln.itemQuantity;
  const unitCode = String(ln.unitCode ?? '').trim();
  // 仅保留真实字典文案；禁止用裸 unitCode 冒充，否则会压过计量带单位名
  const unitLabel = String(ln.unitCodeDictText ?? '').trim();
  const row = {
    id: `ln-link-${rowIdx}-${String(itemIdRaw ?? rowIdx)}`,
    medicinePick: name ? { id: itemIdRaw, name } : undefined,
    itemTable: ln.itemTable,
    itemId: itemIdRaw,
    productName: name,
    specification: String(ln.specification ?? ''),
    manufacturer: String(ln.manufacturer ?? ''),
    itemQuantity: qtyRaw === undefined ? undefined : Number(qtyRaw),
    unitCode,
    unitCodeDictText: unitLabel || undefined,
    lotNumber: String(ln.lotNumber ?? ''),
    traceNo: String(ln.traceNo ?? ''),
    reason: String(ln.reason ?? ''),
    remake: String(ln.remake ?? ''),
    price: ln.price,
    totalPrice: ln.totalPrice,
    ...extra,
  } as T;

  const hydrated = hydrateInventoryDetailLineQtyForGrid({
    itemQuantity: row.itemQuantity,
    unitCode,
    unitCodeDictText: unitLabel,
    productCatalog: ln.productCatalog,
    unitBands: ln.unitBands,
    unitBandsJson: ln.unitBandsJson,
  });
  applyHydratedInventoryDetailQtyToRow(row, hydrated);
  if (!row.unitBands?.length) {
    applyInventoryRowQtyUnitList(row);
  }
  return row;
}
