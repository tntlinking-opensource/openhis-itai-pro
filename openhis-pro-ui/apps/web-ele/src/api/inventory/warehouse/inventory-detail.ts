/**
 * 药库库存明细 — AS-INV-WHS-004-SDD IF-INV-DTL-01/02/03/06/08/09。
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
}

/** IF-INV-DTL-02 分页入参（与 AbstractPageQuery + PageInventoryDetailCurrentRequest 对齐）。 */
export interface PageInventoryDetailCurrentRequest {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  medicationCategoryCodes?: string[];
  deviceCategoryCodes?: string[];
  /** 库房主键多选；Long 以 string 传递 */
  warehouseIds?: string[];
  supplierIds?: string[];
  chrgitmLvList?: number[];
  /** true 仅零库存；false 仅正库存；不传不限 */
  zeroStockOnly?: boolean;
  /** BR-003 剩余天数 ≤ 参数 */
  expireWithinDays?: number;
  /** PublicationStatus#getCode() */
  inventoryStatusEnums?: string[];
}

/** IF-INV-DTL-02 行 DTO（与后端 InventoryDetailListRowDto 同名字段）。 */
export interface InventoryDetailListRowDto {
  id?: string;
  itemTable?: string;
  itemId?: string;
  chargeItemDefinitionId?: string;
  name?: string;
  pyStr?: string;
  wbStr?: string;
  lotNumber?: string;
  /** 库存数量展示串（如 2盒3片，后端拼接） */
  displayQuantityText?: string;
  displayCostUnitPrice?: number | string | null;
  displayCostUnitName?: string;
  displayPriceUnitPrice?: number | string | null;
  displayPriceUnitName?: string;
  unitCode?: string;
  unitName?: string;
  warehouseId?: string;
  warehouseName?: string;
  supplierId?: string;
  supplierName?: string;
  expirationDate?: string;
  productionDate?: string;
  inventoryStatusEnum?: string;
  inventoryStatusText?: string;
  chrgitmLv?: number | null;
  chrgitmLvText?: string;
  medicationCategoryCode?: string;
  medicationCategoryText?: string;
  deviceCategoryCode?: string;
  deviceCategoryText?: string;
  lineCostTotal?: number | string | null;
  linePriceTotal?: number | string | null;
  /** 若后端扩展追溯码字段则展示；当前 DTO 可能未下发 */
  traceNo?: string;
}

/** IF-INV-DTL-08 停供 */
export interface RetireInventoryItemsRequest {
  inventoryItemIds: string[];
}

export interface RetireInventoryItemsResponse {
  affectedRows?: number;
}

/** IF-INV-DTL-09 恢复供应 */
export type ResumeInventoryItemsRequest = RetireInventoryItemsRequest;

export type ResumeInventoryItemsResponse = RetireInventoryItemsResponse;

/** IF-INV-DTL-02 分页出参（PagedResult：content + totalCount）。 */
export interface PageInventoryDetailCurrentResponse {
  content?: InventoryDetailListRowDto[];
  totalCount?: number | string;
  pageNo?: number;
  pageSize?: number;
}

/** 页面消费的分页结果（由 API 层从 PagedResult 归一化）。 */
export interface PageInventoryDetailCurrentResult {
  rows: InventoryDetailListRowDto[];
  totalElements: number;
  pageNo?: number;
  pageSize?: number;
}

/** IF-INV-DTL-03 汇总入参（无分页键）。 */
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
}

/** IF-INV-DTL-03 汇总出参。 */
export interface SumInventoryDetailCurrentResponse {
  sumLineCostTotal?: number | string | null;
  sumLinePriceTotal?: number | string | null;
  truncated?: boolean;
}

/** IF-INV-DTL-06 导出入参（与汇总筛选同构）。 */
export type ExportInventoryDetailCurrentRequest = SumInventoryDetailCurrentRequest;

/** IF-INV-DTL-06 导出出参。 */
export interface ExportInventoryDetailCurrentResponse {
  fileName?: string;
  contentType?: string;
  /** JSON 多为 Base64 字符串；联调以实际为准 */
  body?: string | number[] | null;
  exportedRowCount?: number;
  truncated?: boolean;
}

/** IF-INV-DTL-01 */
export async function loadInventoryDetailFormInitApi(body?: LoadInventoryDetailFormInitRequest) {
  const res = await requestClient.post<ApiResponse<LoadInventoryDetailFormInitResponse>>(
    `${BASE}/loadInventoryDetailFormInit`,
    body ?? {},
  );
  return res.data;
}

/** IF-INV-DTL-02 */
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

/** IF-INV-DTL-03 */
export async function sumInventoryDetailCurrentApi(body: SumInventoryDetailCurrentRequest) {
  const res = await requestClient.post<ApiResponse<SumInventoryDetailCurrentResponse>>(
    `${BASE}/sumInventoryDetailCurrent`,
    body,
  );
  return res.data;
}

/** IF-INV-DTL-06 */
export async function exportInventoryDetailCurrentApi(body: ExportInventoryDetailCurrentRequest) {
  const res = await requestClient.post<ApiResponse<ExportInventoryDetailCurrentResponse>>(
    `${BASE}/exportInventoryDetailCurrent`,
    body,
  );
  return res.data;
}

/** IF-INV-DTL-08 */
export async function retireInventoryItemsApi(body: RetireInventoryItemsRequest) {
  const res = await requestClient.post<ApiResponse<RetireInventoryItemsResponse>>(
    `${BASE}/retireInventoryItems`,
    body,
  );
  return res.data;
}

/** IF-INV-DTL-09 */
export async function resumeInventoryItemsApi(body: ResumeInventoryItemsRequest) {
  const res = await requestClient.post<ApiResponse<ResumeInventoryItemsResponse>>(
    `${BASE}/resumeInventoryItems`,
    body,
  );
  return res.data;
}
