/**
 * 药库库存明细 — 现库 IF-01/02/03/06/08/09；备份 IF-04/05/07/11。
 * {@code POST /api/app/inventoryWarehouseInventoryDetail/{methodName}}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/inventoryWarehouseInventoryDetail';

/** whale-core OptionDto，与 IF-01 一致。 */
export interface OptionDto {
  value?: string;
  label?: string;
}

/** IF-INV-DTL-01 入参（当前无业务字段）。 */
export type LoadInventoryDetailFormInitRequest = Record<string, never>;

/** IF-INV-DTL-01 出参。 */
export interface LoadInventoryDetailFormInitResponse {
  chrgitmLvOptions?: OptionDto[];
  inventoryStatusOptions?: OptionDto[];
  medicationCategoryOptions?: OptionDto[];
  deviceCategoryOptions?: OptionDto[];
  /** inventory_range 术语：2=数量等于0，3=数量大于0 */
  inventoryRangeOptions?: OptionDto[];
  warehouseOptions?: OptionDto[];
  supplierOptions?: OptionDto[];
  /** 系统设置：效期预警天数 */
  expireWarningDays?: number;
  /** 系统设置：库存预警包装单位阈值 */
  minPackageQuantityWarning?: number;
}

export const INVENTORY_WARNING_MODE_EXPIRE = 'expire' as const;
export const INVENTORY_WARNING_MODE_LOW_STOCK = 'lowStock' as const;
export type InventoryWarningMode =
  | typeof INVENTORY_WARNING_MODE_EXPIRE
  | typeof INVENTORY_WARNING_MODE_LOW_STOCK;

/** IF-INV-DTL-02 分页入参。 */
export interface PageInventoryDetailCurrentRequest {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  medicationCategoryCodes?: string[];
  deviceCategoryCodes?: string[];
  warehouseIds?: string[];
  supplierIds?: string[];
  chrgitmLvList?: number[];
  zeroStockOnly?: boolean;
  expireWithinDays?: number;
  inventoryStatusEnums?: string[];
  warningMode?: InventoryWarningMode;
}

/**
 * IF-INV-DTL-02 行 DTO。
 * 事实字段 + 服务端拼装的少量展示串/Label；价格口径均为包装单位。
 */
export interface InventoryDetailListRowDto {
  id?: string;
  itemTable?: string;
  itemId?: string;
  chargeItemDefinitionId?: string;
  productBusNo?: string;
  name?: string;
  merchandiseName?: string;
  totalVolume?: string;
  manufacturer?: string;
  approvalNumber?: string;
  ybNo?: string;
  traceNo?: string;
  pyStr?: string;
  wbStr?: string;
  lotNumber?: string;
  warehouseId?: string;
  warehouseName?: string;
  supplierId?: string;
  supplierName?: string;
  productionDate?: string;
  expirationDate?: string;
  remainingDays?: number | null;
  /** 供应状态码 */
  inventoryStatusEnum?: string;
  inventoryStatusLabel?: string;
  chrgitmLv?: number | null;
  chrgitmLvLabel?: string;
  medicationCategoryCode?: string;
  deviceCategoryCode?: string;
  categoryLabel?: string;
  doseFormCode?: string;
  doseFormLabel?: string;
  packageUnitCode?: string;
  packageUnitName?: string;
  splitUnitName?: string;
  /** 1 包装 = ratio 拆零 */
  packageUnitRatio?: number | string | null;
  packageStockText?: string;
  splitStockText?: string;
  /** 批号进价（元/包装单位） */
  lotCostPrice?: number | string | null;
  /** 批号售价（元/包装单位） */
  lotSalePrice?: number | string | null;
  lineCostTotal?: number | string | null;
  linePriceTotal?: number | string | null;
}

export interface RetireInventoryItemsRequest {
  inventoryItemIds: string[];
}

export interface RetireInventoryItemsResponse {
  affectedRows?: number;
}

export type ResumeInventoryItemsRequest = RetireInventoryItemsRequest;
export type ResumeInventoryItemsResponse = RetireInventoryItemsResponse;

export interface PageInventoryDetailCurrentResponse {
  content?: InventoryDetailListRowDto[];
  totalCount?: number | string;
  pageNo?: number;
  pageSize?: number;
}

export interface PageInventoryDetailCurrentResult {
  rows: InventoryDetailListRowDto[];
  totalElements: number;
  pageNo?: number;
  pageSize?: number;
}

export interface SumInventoryDetailCurrentRequest {
  keyword?: string;
  medicationCategoryCodes?: string[];
  deviceCategoryCodes?: string[];
  warehouseIds?: string[];
  supplierIds?: string[];
  chrgitmLvList?: number[];
  zeroStockOnly?: boolean;
  expireWithinDays?: number;
  inventoryStatusEnums?: string[];
  warningMode?: InventoryWarningMode;
}

export interface SumInventoryDetailCurrentResponse {
  sumLineCostTotal?: number | string | null;
  sumLinePriceTotal?: number | string | null;
  truncated?: boolean;
}

export type ExportInventoryDetailCurrentRequest = SumInventoryDetailCurrentRequest;

export interface ExportInventoryDetailCurrentResponse {
  fileName?: string;
  contentType?: string;
  /** Jackson 序列化 byte[] 为 base64 字符串 */
  body?: string;
  exportedRowCount?: number;
  truncated?: boolean;
}

/** 备份分页/汇总/导出行与现库列表 DTO 同构（冻结列 + 展示 enrich）。 */
export type InventoryDetailBackupListRowDto = InventoryDetailListRowDto;

/** 备份分页入参：在现库筛选基础上增加可选 backupBusNo（未传则后端解析当天最新批次）。 */
export interface PageInventoryDetailBackupRequest extends PageInventoryDetailCurrentRequest {
  backupBusNo?: string;
}

export interface PageInventoryDetailBackupResponse {
  content?: InventoryDetailBackupListRowDto[];
  totalCount?: number | string;
  pageNo?: number;
  pageSize?: number;
}

export interface PageInventoryDetailBackupResult {
  rows: InventoryDetailBackupListRowDto[];
  totalElements: number;
  pageNo?: number;
  pageSize?: number;
}

export interface SumInventoryDetailBackupRequest extends SumInventoryDetailCurrentRequest {
  backupBusNo?: string;
}

export type SumInventoryDetailBackupResponse = SumInventoryDetailCurrentResponse;

export type ExportInventoryDetailBackupRequest = SumInventoryDetailBackupRequest;

export type ExportInventoryDetailBackupResponse = ExportInventoryDetailCurrentResponse;

export type ListInventoryDetailBackupBusNoRequest = Record<string, never>;

export interface ListInventoryDetailBackupBusNoResponse {
  backupBusNoList?: string[];
}

/** IF-INV-DTL-12 工作台预警摘要 */
export type LoadInventoryWarningWorkbenchSummaryRequest = Record<string, never>;

export interface LoadInventoryWarningWorkbenchSummaryResponse {
  visible?: boolean;
  expireWarningCount?: number;
  lowStockWarningCount?: number;
  expireWarningDays?: number;
  minPackageQuantityWarning?: number;
}

export async function loadInventoryDetailFormInitApi(body?: LoadInventoryDetailFormInitRequest) {
  const res = await requestClient.post<ApiResponse<LoadInventoryDetailFormInitResponse>>(
    `${BASE}/loadInventoryDetailFormInit`,
    body ?? {},
  );
  return res.data;
}

export async function loadInventoryWarningWorkbenchSummaryApi(
  body?: LoadInventoryWarningWorkbenchSummaryRequest,
) {
  const res = await requestClient.post<ApiResponse<LoadInventoryWarningWorkbenchSummaryResponse>>(
    `${BASE}/loadInventoryWarningWorkbenchSummary`,
    body ?? {},
  );
  return res.data;
}

export async function pageInventoryDetailCurrentApi(
  body: PageInventoryDetailCurrentRequest,
): Promise<PageInventoryDetailCurrentResult> {
  const res = await requestClient.post<ApiResponse<PageInventoryDetailCurrentResponse>>(
    `${BASE}/pageInventoryDetailCurrent`,
    body,
  );
  const { content, totalCount, pageNo, pageSize } =
    parsePagedRowsFromApiData<InventoryDetailListRowDto>(res.data);
  return {
    rows: content,
    totalElements: totalCount,
    pageNo,
    pageSize,
  };
}

export async function sumInventoryDetailCurrentApi(body: SumInventoryDetailCurrentRequest) {
  const res = await requestClient.post<ApiResponse<SumInventoryDetailCurrentResponse>>(
    `${BASE}/sumInventoryDetailCurrent`,
    body,
  );
  return res.data;
}

export async function exportInventoryDetailCurrentApi(body: ExportInventoryDetailCurrentRequest) {
  const res = await requestClient.post<ApiResponse<ExportInventoryDetailCurrentResponse>>(
    `${BASE}/exportInventoryDetailCurrent`,
    body,
  );
  return res.data;
}

export async function retireInventoryItemsApi(body: RetireInventoryItemsRequest) {
  const res = await requestClient.post<ApiResponse<RetireInventoryItemsResponse>>(
    `${BASE}/retireInventoryItems`,
    body,
  );
  return res.data;
}

export async function resumeInventoryItemsApi(body: ResumeInventoryItemsRequest) {
  const res = await requestClient.post<ApiResponse<ResumeInventoryItemsResponse>>(
    `${BASE}/resumeInventoryItems`,
    body,
  );
  return res.data;
}

export async function pageInventoryDetailBackupApi(
  body: PageInventoryDetailBackupRequest,
): Promise<PageInventoryDetailBackupResult> {
  const res = await requestClient.post<ApiResponse<PageInventoryDetailBackupResponse>>(
    `${BASE}/pageInventoryDetailBackup`,
    body,
  );
  const { content, totalCount, pageNo, pageSize } =
    parsePagedRowsFromApiData<InventoryDetailBackupListRowDto>(res.data);
  return {
    rows: content,
    totalElements: totalCount,
    pageNo,
    pageSize,
  };
}

export async function sumInventoryDetailBackupApi(body: SumInventoryDetailBackupRequest) {
  const res = await requestClient.post<ApiResponse<SumInventoryDetailBackupResponse>>(
    `${BASE}/sumInventoryDetailBackup`,
    body,
  );
  return res.data;
}

export async function exportInventoryDetailBackupApi(body: ExportInventoryDetailBackupRequest) {
  const res = await requestClient.post<ApiResponse<ExportInventoryDetailBackupResponse>>(
    `${BASE}/exportInventoryDetailBackup`,
    body,
  );
  return res.data;
}

export async function listInventoryDetailBackupBusNoApi(
  body?: ListInventoryDetailBackupBusNoRequest,
) {
  const res = await requestClient.post<ApiResponse<ListInventoryDetailBackupBusNoResponse>>(
    `${BASE}/listInventoryDetailBackupBusNo`,
    body ?? {},
  );
  return res.data;
}

/** 现库行本库仍在库追溯码查询入参 */
export interface ListInStockTraceNumbersByInventoryItemRequest {
  locationId: string;
  itemId: string;
  lotNumber: string;
  itemTable?: string;
}

export interface InventoryItemTraceNumberRowDto {
  traceNo?: string;
  locationNetSplitQuantity?: number | string;
  traceUnitCode?: string;
  inHospitalStatusEnum?: string;
  inHospitalStatusLabel?: string;
}

export interface ListInStockTraceNumbersByInventoryItemResponse {
  locationId?: string;
  itemId?: string;
  lotNumber?: string;
  items?: InventoryItemTraceNumberRowDto[];
}

/** 查询指定库存行在本库仍在库的追溯码列表 */
export async function listInStockTraceNumbersByInventoryItemApi(
  body: ListInStockTraceNumbersByInventoryItemRequest,
) {
  const res = await requestClient.post<ApiResponse<ListInStockTraceNumbersByInventoryItemResponse>>(
    `${BASE}/listInStockTraceNumbersByInventoryItem`,
    body,
  );
  return res.data ?? { items: [] };
}

/** 单价 + 包装单位名展示 */
export function fmtPackageUnitPrice(
  price?: number | string | null,
  unitName?: string | null,
): string {
  if (price === null || price === undefined || price === '') {
    return '—';
  }
  const unit = unitName?.trim();
  return unit ? `${price}元/${unit}` : String(price);
}
