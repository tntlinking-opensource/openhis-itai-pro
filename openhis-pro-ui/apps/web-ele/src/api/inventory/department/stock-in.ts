/**
 * 科室入库单（只读壳单台账）— 对接 Whale {@code POST /api/app/inventoryDepartmentStockIn/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_DEPT_PHARMACIES_LABEL,
  INV_ALL_WAREHOUSES_LABEL,
  isAllLocationsFilterOptionValue,
  mapInitOptionsToCabinetRowsWithAll,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryDepartmentStockIn';

/** {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_STOCK_IN} */
export const DEPT_STOCK_IN_TYPE_ENUM = 'DEPT_STOCK_IN';

export interface StockInOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadStockInFormInitRequest {}

export interface LoadStockInFormInitResponse {
  departmentPharmacyLocationOptions?: StockInOptionDto[];
  warehouseLocationOptions?: StockInOptionDto[];
  inboundItemTableTypeOptions?: StockInOptionDto[];
  statusOptions?: StockInOptionDto[];
  pharmacistOptions?: StockInOptionDto[];
  warehouseKeeperOptions?: StockInOptionDto[];
}

export interface StockInListRowDto {
  busNo?: string;
  typeEnum?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  sourceLocationName?: string;
  purposeLocationId?: number | string;
  applyTime?: string;
  originalBusNo?: string;
  applicantId?: number | string;
  /** 制单人姓名（后端 enrich，与出库列表 creatorName 同构） */
  creatorName?: string;
  approverName?: string;
  /** 同号各行 totalPrice 之和 */
  totalAmount?: number | string;
}

export interface PageStockInsRequest {
  purposeLocationId: number | string;
  sourceLocationId?: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  typeEnum?: string;
}

export interface PageStockInsResponse {
  total?: number;
  totalCount?: number;
  content?: StockInListRowDto[];
}

export interface GetStockInDetailRequest {
  busNo: string;
}

export interface StockInDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  startTime?: string;
  endTime?: string;
  productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown };
}

export interface GetStockInDetailResponse {
  busNo?: string;
  typeEnum?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum?: string;
  originalBusNo?: string;
  lines: StockInDetailLineDto[];
}

export async function loadStockInFormInitApi(body?: LoadStockInFormInitRequest) {
  return requestClient.post<ApiResponse<LoadStockInFormInitResponse>>(
    `${BASE}/loadStockInFormInit`,
    body ?? {},
  );
}

export async function pageStockInsApi(body: PageStockInsRequest) {
  return requestClient.post<ApiResponse<PageStockInsResponse>>(`${BASE}/pageStockIns`, body);
}

export async function getStockInDetailApi(body: GetStockInDetailRequest) {
  return requestClient.post<ApiResponse<GetStockInDetailResponse>>(
    `${BASE}/getStockInDetail`,
    body,
  );
}

/** init 科室药房下拉 → 侧栏 cabinet 行（含「全部科室药房」）。 */
export function mapDepartmentStockInInitToCabinetRows(
  list?: StockInOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(list ?? [], INV_ALL_DEPT_PHARMACIES_LABEL);
}

/** init 源药库下拉 → 侧栏 cabinet 行（含「全部库房」）。 */
export function mapDepartmentStockInInitWarehouseToCabinetRows(
  list?: StockInOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(list ?? [], INV_ALL_WAREHOUSES_LABEL);
}

/** 工作站左侧列表（只读）：purposeLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageDepartmentStockInWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  sourceLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: StockInListRowDto[]; total: number }> {
  const sourceRaw = params?.sourceLocationId;
  const sourceLocationId =
    sourceRaw === undefined ||
    sourceRaw === null ||
    String(sourceRaw).trim() === '' ||
    isAllLocationsFilterOptionValue(sourceRaw)
      ? undefined
      : sourceRaw;
  const res = await pageStockInsApi({
    purposeLocationId: resolveWorkbenchListPurposeLocationId(params?.purposeLocationId),
    sourceLocationId,
    keyword: params?.keyword?.trim() || undefined,
    typeEnum: DEPT_STOCK_IN_TYPE_ENUM,
    pageNo: params?.pageNo ?? 1,
    pageSize: params?.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<StockInListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params?.keyword,
  ) as StockInListRowDto[];
  const filtered = (params?.applyTimeBegin && params?.applyTimeEnd) || params?.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
