/**
 * 退库入库单（只读台账）— 对接 ZXPlat {@code POST /api/app/inventoryWarehouseReturnStockIn/<method>}
 * type_enum=5 (CABINET_RETURN_STOCK_IN)。写 IF 仅后端网关拒写，前端不封装。
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_LOCATIONS_FILTER_ID,
  INV_ALL_WAREHOUSES_LABEL,
  mapLocationOptionsToCabinetRows,
  prependAllLocationsCabinetOption,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryWarehouseReturnStockIn';

export interface LocationOptionDto {
  locationId?: number | string;
  displayName?: string;
  active?: boolean;
}

export type WarehouseOptionDto = LocationOptionDto;

export interface SupplierOptionDto {
  supplierId?: number | string;
  supplierName?: string;
  supplierType?: string;
}

export interface LoadReturnStockInFormInitRequest {}

export interface LoadReturnStockInFormInitResponse {
  warehouseOptions?: LocationOptionDto[];
  departmentOptions?: LocationOptionDto[];
  pharmacyOptions?: LocationOptionDto[];
  supplierOptions?: SupplierOptionDto[];
}

export interface ReturnStockInListRowDto {
  id?: number | string;
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  categoryEnum?: number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  supplierId?: number | string;
  supplierName?: string;
  itemId?: number | string;
  itemTable?: string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  /** 单据总金额（聚合列表侧栏） */
  totalAmount?: number;
  applicantId?: number | string;
  creatorName?: string;
  approverName?: string;
  applyTime?: string;
}

export type WarehouseDocumentDetailRowDto = ReturnStockInListRowDto;

export interface PageReturnStockInsRequest {
  /** 目的药库；0 = 全部可见药库 */
  purposeLocationId: number | string;
  statusEnum?: number;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  /** 固定 5 = CABINET_RETURN_STOCK_IN */
  typeEnum?: number;
}

export interface PageReturnStockInsResponse {
  total?: number;
  totalCount?: number;
  content?: ReturnStockInListRowDto[];
}

export interface GetReturnStockInDetailRequest {
  busNo: string;
}

export interface ReturnStockInDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  /** 计量单位展示名；无则勿用 unitCode 冒充 */
  unitCodeDictText?: string;
  lotNumber?: string;
  startTime?: string;
  endTime?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  productName?: string;
  /** 产品目录（含 unitBands，详情数量/单位回显用） */
  productCatalog?: {
    unitBands?: unknown;
    unitBandsJson?: unknown;
    merchandiseName?: string;
    productName?: string;
    productBusNo?: string;
  };
}

export interface GetReturnStockInDetailResponse {
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number | string;
  lines: ReturnStockInDetailLineDto[];
}

export async function loadReturnStockInFormInitApi(body?: LoadReturnStockInFormInitRequest) {
  return requestClient.post<ApiResponse<LoadReturnStockInFormInitResponse>>(
    `${BASE}/loadReturnStockInFormInit`,
    body ?? {},
  );
}

export async function pageReturnStockInsApi(body: PageReturnStockInsRequest) {
  return requestClient.post<ApiResponse<PageReturnStockInsResponse>>(
    `${BASE}/pageReturnStockIns`,
    body,
  );
}

export async function getReturnStockInDetailApi(body: GetReturnStockInDetailRequest) {
  return requestClient.post<ApiResponse<GetReturnStockInDetailResponse>>(
    `${BASE}/getReturnStockInDetail`,
    body,
  );
}

/** typeEnum=5；退库入库单 CABINET_RETURN_STOCK_IN */
export const WHS_RSI_TYPE_ENUM = 5;
/** SupplyRequestType DEPARTMENT=2 */
export const WHS_RSI_LOCATION_TYPE_DEPARTMENT = 2;
/** SupplyRequestType PHARMACY=16 */
export const WHS_RSI_LOCATION_TYPE_PHARMACY = 16;
/** SupplyRequestType CABINET=11 */
export const WHS_RSI_LOCATION_TYPE_CABINET = 11;
/** SupplyCategory STOCK_SUPPLY=1 */
export const WHS_RSI_CATEGORY_STOCK = 1;

export {
  INV_ALL_LOCATIONS_FILTER_ID as WHS_RETURN_STOCK_IN_ALL_WAREHOUSES_PURPOSE_LOCATION_ID,
  INV_ALL_WAREHOUSES_LABEL as WHS_RETURN_STOCK_IN_ALL_WAREHOUSES_LABEL,
};

/** init 药库下拉 → 侧栏 cabinet 行（含「全部库房」）。 */
export function mapReturnStockInInitToCabinetRows(
  opts?: LocationOptionDto[],
): Record<string, unknown>[] {
  const rows = mapLocationOptionsToCabinetRows(
    (opts ?? []).map((o) => ({ locationId: o.locationId, displayName: o.displayName })),
    INV_ALL_WAREHOUSES_LABEL,
  );
  return prependAllLocationsCabinetOption(rows, INV_ALL_WAREHOUSES_LABEL);
}

/** 工作站左侧列表：默认全部药库；日期在前端筛（待审核 applyTime 可能为空）。 */
export async function pageReturnStockInWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: ReturnStockInListRowDto[]; total: number }> {
  const res = await pageReturnStockInsApi({
    purposeLocationId: resolveWorkbenchListPurposeLocationId(params?.purposeLocationId),
    typeEnum: WHS_RSI_TYPE_ENUM,
    keyword: params?.keyword?.trim() || undefined,
    pageNo: params?.pageNo ?? 1,
    pageSize: params?.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<ReturnStockInListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params?.keyword,
  ) as ReturnStockInListRowDto[];
  const filtered = (params?.applyTimeBegin && params?.applyTimeEnd) || params?.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}

export type ReturnFromDownstreamDetailLineDto = ReturnStockInDetailLineDto;
export type GetReturnFromDownstreamDetailResponse = GetReturnStockInDetailResponse;
