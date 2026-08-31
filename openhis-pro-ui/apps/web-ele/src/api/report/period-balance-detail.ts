/**
 * 库房期初期末明细 — POST /api/app/inventoryPeriodBalanceDetail/{methodName}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { PageParams } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';

const BASE = '/inventoryPeriodBalanceDetail';

export const ITEM_TABLE_MEDICATION = 'med_medication_definition' as const;
export const ITEM_TABLE_DEVICE = 'adm_device_definition' as const;
export type PeriodBalanceDetailItemTable = typeof ITEM_TABLE_DEVICE | typeof ITEM_TABLE_MEDICATION;

export interface OptionDto {
  value?: string;
  label?: string;
  busNo?: string;
}

export interface LoadPeriodBalanceDetailFormInitResponse {
  warehouseOptions?: OptionDto[];
  backupBusNoOptions?: OptionDto[];
}

export interface PagePeriodBalanceDetailRequest extends PageParams {
  locationId: string;
  startBackupBusNo: string;
  endBackupBusNo?: string;
  /** 可选；不传时服务端按库位现库/期初快照 DISTINCT item_table 解析。 */
  itemTable?: PeriodBalanceDetailItemTable;
  keyword?: string;
  isBus?: boolean;
  isBalance?: boolean | null;
}

export interface PeriodBalanceDetailColumnDto {
  columnCode?: string;
  columnTitle?: string;
  valueType?: string;
  groupCode?: string;
}

export interface PeriodBalanceDetailListRowDto {
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
  /** 本库期间调价进价盈亏；无调价时可空。 */
  priceAdjustmentCostAmount?: number | string;
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

export interface PagePeriodBalanceDetailResponse {
  total?: number;
  pageNo?: number;
  pageSize?: number;
  locationFormEnum?: string;
  /** 服务端按库位解析后的物品主表。 */
  itemTable?: PeriodBalanceDetailItemTable;
  activeColumnCodes?: string[];
  columns?: PeriodBalanceDetailColumnDto[];
  rows?: PeriodBalanceDetailListRowDto[];
}

export type ExportPeriodBalanceDetailRequest = Omit<
  PagePeriodBalanceDetailRequest,
  'pageNo' | 'pageSize'
> & {
  /** 页面可见列 → 后端 Excel 列裁剪 */
  exportColumns?: ExcelExportColumnPayload[];
};

export interface ExportPeriodBalanceDetailResponse {
  fileName?: string;
  contentType?: string;
  fileBytes?: string;
  truncated?: boolean;
  exportedRowCount?: number;
}

/** 页面初始化：管辖库位与备份单号下拉。 */
export async function loadPeriodBalanceDetailFormInitApi() {
  return reportPost<LoadPeriodBalanceDetailFormInitResponse>(
    `${BASE}/loadPeriodBalanceDetailFormInit`,
    {},
  );
}

/** 库房期初期末明细分页查询。 */
export async function pagePeriodBalanceDetailApi(body: PagePeriodBalanceDetailRequest) {
  return reportPost<PagePeriodBalanceDetailResponse>(`${BASE}/pagePeriodBalanceDetail`, body);
}

/** 导出库房期初期末明细 Excel。 */
export async function exportPeriodBalanceDetailApi(body: ExportPeriodBalanceDetailRequest) {
  return reportPost<ExportPeriodBalanceDetailResponse>(`${BASE}/exportPeriodBalanceDetail`, body);
}
