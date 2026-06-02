/**
 * 药库采购申请 / 损益（T5 VXE 明细）共用：行模型、选药回填、金额重算、保存载荷、审批口径。
 * 价格与单位换算逻辑与采购退货 VXE 表对齐，批次库存等退货专有字段在此省略。
 */

import type { VxeGridPropTypes } from '#/adapter/vxe-table';

import {
  formatInventoryTotalAmountDisplay,
  formatInventoryUnitPriceDisplay,
} from '#/utils/inventory/inventory-qty-unit-display';

/** 药品分页 / 选药弹层行（与 medication-info 接口字段宽松对齐） */
export type PurchaseOrderMedicationRecord = Record<string, unknown> & {
  id?: string;
  busNo?: string;
  name?: string;
  price?: number;
  retailPrice?: number;
  unitCode?: string;
  unitCode_dictText?: string;
  partPercent?: number | string;
  specificationInventory?: number;
  batchInventory?: number;
  unitList?: UnitListEntry[];
};

export type PurchaseOrderDetailGridRow = Record<string, unknown> & {
  medicineInfo?: { id?: string; name?: string };
  itemId?: string;
  itemBusNo?: string;
  itemName?: string;
  itemQuantity?: number;
  unitCode?: string;
  unitList?: UnitListEntry[];
  partPercent?: number | string;
  price?: number;
  retailPrice?: number;
  priceMaxUnit?: number;
  priceMinUnit?: number;
  retailPriceMaxUnit?: number;
  retailPriceMinUnit?: number | string;
  totalPrice?: number;
  totalRetailPrice?: number;
  totalPriceMask?: number;
  totalRetailPriceMask?: number;
  specificationInventory?: number;
};

type UnitListEntry = Record<string, unknown> & {
  value?: string;
  value_dictText?: string;
  priceMask?: number | string;
  retailPriceMask?: number | string;
  specificationInventoryMax?: number;
  specificationInventoryMin?: number;
  specificationInventoryMaxUnit?: string;
  specificationInventoryMinUnit?: string;
};

export type PurchaseRequestOrderRowLike = {
  statusEnum?: number | string;
  statusEnum_enumText?: string;
};

export function isPurchaseRequestApproved(row?: null | PurchaseRequestOrderRowLike): boolean {
  if (!row) return false;
  if (row.statusEnum_enumText === '同意') return true;
  const s = row.statusEnum;
  if (s === 'AGREE' || s === 'agree') return true;
  return s === 3 || s === '3';
}

function partPercentOf(row: PurchaseOrderDetailGridRow): number {
  const n = Number.parseInt(String(row.partPercent ?? 1), 10);
  return Number.isFinite(n) && n > 0 ? n : 1;
}

function getCurrentUnit(row: PurchaseOrderDetailGridRow): null | UnitListEntry {
  const list = row.unitList;
  const code = row.unitCode;
  if (!list?.length || !code) return null;
  return list.find((item) => item.value === code) ?? null;
}

function buildPrice(item: PurchaseOrderDetailGridRow): void {
  const pp = partPercentOf(item);
  const list = item.unitList;
  const isMaxUnit = list?.length ? item.unitCode === list[0]!.value : true;
  if (isMaxUnit) {
    const p = Number(item.price ?? 0);
    const rp = Number.parseFloat(String(item.retailPrice ?? 0));
    item.priceMaxUnit = p;
    item.priceMinUnit = p / pp;
    item.retailPriceMaxUnit = rp;
    item.retailPriceMinUnit = (rp / pp).toFixed(2);
  } else {
    const p = Number(item.price ?? 0);
    const rp = Number.parseFloat(String(item.retailPrice ?? 0));
    item.priceMaxUnit = Number.parseFloat((p * pp).toFixed(2));
    item.priceMinUnit = p;
    item.retailPriceMaxUnit = Number.parseFloat((rp * pp).toFixed(2));
    item.retailPriceMinUnit = rp;
  }
}

function buildUnitList(row: PurchaseOrderDetailGridRow): void {
  const specInv = Number(row.specificationInventory ?? 0);
  const batchInv = Number(row.batchInventory ?? 0);
  const pp = partPercentOf(row);
  const unitCodeDictText = String(row.unitCode_dictText ?? row.unitCodeDictText ?? '');
  const minUnitText = String(row.minUnitCode_dictText ?? '');

  (row.unitList ?? []).forEach((item) => {
    if (item.value === row.unitCode) {
      item.priceMask = row.price;
      item.retailPriceMask = row.retailPrice;
      item.totalPriceMask = row.totalPrice;
      item.totalRetailPriceMask = row.totalRetailPrice;
      item.specificationInventoryOriginal = specInv;
      item.specificationInventoryMax = Math.floor(specInv / pp);
      item.specificationInventoryMin = specInv % pp;
      item.specificationInventoryMaxUnit = unitCodeDictText;
      item.specificationInventoryMinUnit = minUnitText;
      item.batchInventoryOriginal = batchInv;
      item.batchInventoryMax = Math.floor(batchInv / pp);
      item.batchInventoryMin = batchInv % pp;
      item.batchInventoryMaxUnit = unitCodeDictText;
      item.batchInventoryMinUnit = minUnitText;
    } else {
      item.priceMask = Number(row.price ?? 0) / pp;
      item.retailPriceMask = (Number.parseFloat(String(row.retailPrice ?? 0)) / pp).toFixed(2);
      item.totalPriceMask = row.totalPrice;
      item.totalRetailPriceMask = row.totalRetailPrice;
      item.specificationInventoryOriginal = specInv;
      item.specificationInventoryMin = specInv;
      item.specificationInventoryMinUnit = minUnitText;
      item.batchInventoryOriginal = batchInv;
      item.batchInventoryMin = batchInv;
      item.batchInventoryMinUnit = minUnitText;
    }
  });
}

function rebuildTableData(row: PurchaseOrderDetailGridRow): void {
  buildPrice(row);
  buildUnitList(row);
}

function fallbackUnitList(row: PurchaseOrderDetailGridRow): UnitListEntry[] {
  const code = row.unitCode ? String(row.unitCode) : '';
  if (!code) return [];
  const text = String(row.unitCode_dictText ?? row.unitCodeDictText ?? code);
  return [{ value: code, value_dictText: text }];
}

/** 选中药品后写入当前编辑行并重建单位/价格掩码 */
export function applyMedicineToRow(
  row: PurchaseOrderDetailGridRow,
  med: PurchaseOrderMedicationRecord,
): void {
  Object.assign(row, {
    ...med,
    itemId: med.id,
    itemBusNo: med.busNo,
    itemName: med.name,
    unitCode: med.unitCode,
    unitCodeDictText: med.unitCode_dictText,
    remake: med.remake ?? row.remake,
    medicineInfo: { id: med.id, name: med.name },
  });
  if (!row.unitList?.length) {
    row.unitList = fallbackUnitList(row);
  }
  if (!row.partPercent) {
    row.partPercent = 1;
  }
  const q = Number(row.itemQuantity ?? 0);
  if (!q || q <= 0) {
    row.itemQuantity = 1;
  }
  rebuildTableData(row);
  recalcRowAmounts(row);
}

/** 单位或数量变化后同步单价与金额 */
export function recalcRowAmounts(row: PurchaseOrderDetailGridRow): void {
  const list = row.unitList;
  const isMaxUnit = list?.length ? row.unitCode === list[0]!.value : true;
  if (isMaxUnit) {
    row.price = Number(row.priceMaxUnit ?? row.price ?? 0);
    row.retailPrice = Number(row.retailPriceMaxUnit ?? row.retailPrice ?? 0);
  } else {
    row.price = Number(row.priceMinUnit ?? row.price ?? 0);
    row.retailPrice = Number.parseFloat(String(row.retailPriceMinUnit ?? row.retailPrice ?? 0));
  }
  const quantity = Number.parseFloat(String(row.itemQuantity ?? 0)) || 0;
  const price = Number.parseFloat(String(row.price ?? 0)) || 0;
  const retailPrice = Number.parseFloat(String(row.retailPrice ?? 0)) || 0;
  row.totalPrice = Number.parseFloat((quantity * price).toFixed(2));
  row.totalPriceMask = row.totalPrice;
  row.totalRetailPrice = Number.parseFloat((quantity * retailPrice).toFixed(2));
  row.totalRetailPriceMask = row.totalRetailPrice;
  rebuildTableData(row);
}

export function formatPriceCell(
  row: PurchaseOrderDetailGridRow,
  priceType: 'price' | 'retailPrice',
): string {
  const field = priceType === 'price' ? 'price' : 'retailPrice';
  const u = getCurrentUnit(row);
  if (
    priceType === 'price' &&
    u?.priceMask !== undefined &&
    u?.priceMask !== null &&
    u?.priceMask !== ''
  ) {
    return formatInventoryUnitPriceDisplay({ ...row, [field]: u.priceMask }, field, 'unitCode');
  }
  if (
    priceType === 'retailPrice' &&
    u?.retailPriceMask !== undefined &&
    u?.retailPriceMask !== null &&
    u?.retailPriceMask !== ''
  ) {
    return formatInventoryUnitPriceDisplay(
      { ...row, [field]: u.retailPriceMask },
      field,
      'unitCode',
    );
  }
  return formatInventoryUnitPriceDisplay(row, field, 'unitCode');
}

export function formatAmountCell(
  row: PurchaseOrderDetailGridRow,
  amountField: 'totalPrice' | 'totalRetailPrice',
): string {
  const u = getCurrentUnit(row);
  if (
    amountField === 'totalPrice' &&
    u?.totalPriceMask !== undefined &&
    u?.totalPriceMask !== null &&
    u?.totalPriceMask !== ''
  ) {
    return formatInventoryTotalAmountDisplay({ [amountField]: u.totalPriceMask }, amountField);
  }
  if (
    amountField === 'totalRetailPrice' &&
    u?.totalRetailPriceMask !== undefined &&
    u?.totalRetailPriceMask !== null &&
    u?.totalRetailPriceMask !== ''
  ) {
    return formatInventoryTotalAmountDisplay(
      { [amountField]: u.totalRetailPriceMask },
      amountField,
    );
  }
  return formatInventoryTotalAmountDisplay(row, amountField);
}

export function formatInventoryCell(row: PurchaseOrderDetailGridRow): string {
  const u = getCurrentUnit(row);
  const list = row.unitList;
  if (u && list?.length) {
    const isMax = row.unitCode === list[0]?.value;
    if (isMax && u.specificationInventoryMax != null && u.specificationInventoryMin != null) {
      const head = `${u.specificationInventoryMax}${u.specificationInventoryMaxUnit ?? ''}`;
      const min = Number(u.specificationInventoryMin);
      if (min > 0) {
        return `${head} ${u.specificationInventoryMin}${u.specificationInventoryMinUnit ?? ''}`;
      }
      return head;
    }
    if (!isMax && u.specificationInventoryMin != null) {
      return `${u.specificationInventoryMin}${u.specificationInventoryMinUnit ?? ''}`;
    }
  }
  const n = row.specificationInventory;
  if (n === undefined || n === null || n === '') return '';
  return String(n);
}

/** 接口明细行 → 表格行（就地补 medicineInfo / unitList 并重建掩码） */
export function mapDetailRecordToGridRow(raw: Record<string, unknown>): PurchaseOrderDetailGridRow {
  const row = { ...raw } as PurchaseOrderDetailGridRow;
  if (row.itemId && row.itemName && !row.medicineInfo) {
    row.medicineInfo = { id: String(row.itemId), name: String(row.itemName) };
  }
  if (!row.unitList?.length) {
    row.unitList = fallbackUnitList(row);
  }
  if (!row.partPercent) {
    row.partPercent = 1;
  }
  rebuildTableData(row);
  recalcRowAmounts(row);
  return row;
}

export function purchaseOrderDetailDefaultRow(): PurchaseOrderDetailGridRow {
  return {
    itemQuantity: 1,
    price: 0,
    retailPrice: 0,
    totalPrice: 0,
    totalRetailPrice: 0,
    totalPriceMask: 0,
    totalRetailPriceMask: 0,
    specificationInventory: 0,
    partPercent: 1,
    unitList: [],
  } as PurchaseOrderDetailGridRow;
}

export const purchaseOrderDetailValidRules: VxeGridPropTypes.EditRules = {
  medicineInfo: [{ required: true, message: '请选择药品' }],
  totalVolume: [{ required: true, message: '请输入规格', trigger: 'blur' }],
  unitCode: [{ required: true, message: '请选择单位', trigger: 'change' }],
  itemQuantity: [
    {
      validator: (_rule, value, callback) => {
        if (value === undefined || value === null || value === '') {
          callback(new Error('请输入数量'));
          return;
        }
        if (Number(value) <= 0) {
          callback(new Error('数量须大于 0'));
          return;
        }
        callback();
      },
      trigger: ['blur', 'change'],
    },
  ],
};

const GRID_OMIT_KEYS = new Set(['medicineInfo', 'checkbox', 'seq']);

/** 表格行 → 保存接口数组（行字段覆盖头字段中的同名字段） */
export function gridRowsToSavePayload(
  rows: PurchaseOrderDetailGridRow[],
  head: Record<string, unknown>,
): Record<string, unknown>[] {
  return rows.map((r) => {
    const out: Record<string, unknown> = { ...head };
    for (const [k, v] of Object.entries(r)) {
      if (GRID_OMIT_KEYS.has(k)) continue;
      out[k] = v;
    }
    const mi = r.medicineInfo;
    if (mi?.id != null && out.itemId === undefined) {
      out.itemId = mi.id;
    }
    if (mi?.name != null && out.itemName === undefined) {
      out.itemName = mi.name;
    }
    return out;
  });
}
