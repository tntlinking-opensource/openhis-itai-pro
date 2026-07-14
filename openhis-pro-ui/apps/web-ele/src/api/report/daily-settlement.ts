/**
 * 库房日结 — POST /api/app/inventoryWarehouseDailySettlement/{methodName}
 */
import type { PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

const BASE = '/inventoryWarehouseDailySettlement';

export const ITEM_TABLE_MEDICATION = 'med_medication_definition' as const;
export const ITEM_TABLE_DEVICE = 'adm_device_definition' as const;
export type DailySettlementItemTable = typeof ITEM_TABLE_DEVICE | typeof ITEM_TABLE_MEDICATION;

export interface OptionDto {
  value?: string;
  label?: string;
  busNo?: string;
}

export interface LoadDailySettlementFormInitResponse {
  warehouseOptions?: OptionDto[];
  backupBusNoOptions?: OptionDto[];
}

export interface PageDailySettlementRequest extends PageParams {
  locationId: string;
  startBackupBusNo: string;
  endBackupBusNo?: string;
  /** 可选；不传时服务端按库位现库/期初快照 DISTINCT item_table 解析。 */
  itemTable?: DailySettlementItemTable;
  keyword?: string;
  isBus?: boolean;
  isBalance?: boolean | null;
}

export interface DailySettlementColumnDto {
  columnCode?: string;
  columnTitle?: string;
  valueType?: string;
  groupCode?: string;
}

export interface DailySettlementListRowDto {
  itemId?: string;
  itemNo?: string;
  productName?: string;
  itemName?: string;
  specText?: string;
  manufacturerText?: string;
  packageUnitRatio?: number | string;
  packageUnitCodeLabel?: string;
  minUnitCodeLabel?: string;
  beginQuantity?: number | string;
  beginPackageStockText?: string;
  beginSplitStockText?: string;
  beginCostAmount?: number | string;
  beginSaleAmount?: number | string;
  endPackageStockText?: string;
  endSplitStockText?: string;
  endCostAmount?: number | string;
  endSaleAmount?: number | string;
  computedEndCostAmount?: number | string;
  computedEndSaleAmount?: number | string;
  quantityDiff?: number | string;
  costAmountDiff?: number | string;
  saleAmountDiff?: number | string;
  isBalance?: boolean;
  [key: string]: unknown;
}

export interface PageDailySettlementResponse {
  total?: number;
  pageNo?: number;
  pageSize?: number;
  locationFormEnum?: string;
  /** 服务端按库位解析后的物品主表。 */
  itemTable?: DailySettlementItemTable;
  activeColumnCodes?: string[];
  columns?: DailySettlementColumnDto[];
  rows?: DailySettlementListRowDto[];
}

export type ExportDailySettlementRequest = Omit<PageDailySettlementRequest, 'pageNo' | 'pageSize'>;

export interface ExportDailySettlementResponse {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
  truncated?: boolean;
  exportedRowCount?: number;
}

/** 页面初始化：管辖库位与备份单号下拉。 */
export async function loadDailySettlementFormInitApi() {
  return publicRequestClient.post<LoadDailySettlementFormInitResponse>(
    `${BASE}/loadDailySettlementFormInit`,
    {},
  );
}

/** 日结分页查询。 */
export async function pageDailySettlementApi(body: PageDailySettlementRequest) {
  return publicRequestClient.post<PageDailySettlementResponse>(`${BASE}/pageDailySettlement`, body);
}

/** 导出日结 Excel。 */
export async function exportDailySettlementApi(body: ExportDailySettlementRequest) {
  return publicRequestClient.post<ExportDailySettlementResponse>(
    `${BASE}/exportDailySettlement`,
    body,
  );
}
