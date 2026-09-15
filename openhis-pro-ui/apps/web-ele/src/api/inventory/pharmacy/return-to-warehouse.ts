/**
 * 药房退库单 — 对接 {@code POST /api/app/inventoryPharmacyReturnToWarehouse/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  coerceWorkbenchListLocationIdForApi,
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_PHARMACIES_LABEL,
  mapInitOptionsToCabinetRowsWithAll,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryPharmacyReturnToWarehouse';

export interface PharmacyLocationOptionDto {
  /** Snowflake 库位主键：须 string，禁止 Number() */
  locationId?: number | string;
  displayName?: string;
}

export interface WarehouseLocationOptionDto {
  /** Snowflake 库位主键：须 string，禁止 Number() */
  locationId?: number | string;
  displayName?: string;
}

export interface LoadReturnToWarehouseFormInitRequest {}

export interface LoadReturnToWarehouseFormInitResponse {
  pharmacyLocationOptions: PharmacyLocationOptionDto[];
  warehouseLocationOptions: WarehouseLocationOptionDto[];
}

/** 列表行（读模型） */
export interface ReturnToWarehouseListRowDto {
  id?: number;
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  supplierId?: number;
  supplierName?: string;
  itemId?: number;
  itemTable?: string;
  itemQuantity?: number;
  unitCode?: string;
  totalAmount?: number;
  applicantId?: number;
  creatorName?: string;
  approverName?: string;
  applyTime?: string;
}

export interface PageReturnToWarehousesRequest {
  sourceLocationId: number | string;
  statusEnum?: number;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageReturnToWarehousesResponse {
  total?: number;
  totalCount?: number;
  content?: ReturnToWarehouseListRowDto[];
}

export interface GetReturnToWarehouseDetailRequest {
  busNo: string;
}

export interface ReturnToWarehouseDetailLineDto {
  id?: number;
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  /** 计量单位展示名；无则勿用 unitCode 冒充 */
  unitCodeDictText?: string;
  lotNumber?: string;
  /** 效期止（YYYY-MM-DD 或 datetime）；打印有效期列 */
  endTime?: string;
  /** 效期起；打印不用，仅对齐后端详情 */
  startTime?: string;
  traceNo?: string;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  price?: number;
  totalPrice?: number;
  productName?: string;
  /** 规格（读模型 totalVolume） */
  specification?: string;
  /** 厂家/产地（读模型富化） */
  manufacturer?: string;
  /** 产品目录（含 unitBands / totalVolume，详情单位回显用） */
  productCatalog?: {
    unitBands?: unknown;
    unitBandsJson?: unknown;
    totalVolume?: string;
    productBusNo?: string;
  };
}

export interface GetReturnToWarehouseDetailResponse {
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  supplierId?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  lines: ReturnToWarehouseDetailLineDto[];
}

export interface ReturnToWarehouseLineDto {
  itemTable?: string;
  /** Snowflake：请求体须 string，禁止 Number() */
  itemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  price?: number;
  totalPrice?: number;
}

export interface CreateReturnToWarehouseRequest {
  /** 可选：关联已同意药房入库单；空则按源药房库存自由选品 */
  originalBusNo?: string;
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnToWarehouseLineDto[];
}

export interface UpdateReturnToWarehouseRequest {
  busNo: string;
  sourceLocationId: number | string;
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnToWarehouseLineDto[];
}

export interface CreateReturnToWarehouseResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdateReturnToWarehouseResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitReturnToWarehouseApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitReturnToWarehouseApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface WithdrawReturnToWarehouseApprovalRequest {
  busNo: string;
}

export interface WithdrawReturnToWarehouseApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface AgreeReturnToWarehouseRequest {
  busNo: string;
}

export interface RejectReturnToWarehouseRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveReturnToWarehouseRequest {
  busNo: string;
}

export interface PageProductsForReturnToWarehouseRequest {
  /** 源药房位置（库存选品门禁） */
  sourceLocationId: number | string;
  productTableName?: string;
  keyword?: string;
  /** true=仅返回账面量>0 的批号行 */
  positiveQuantityOnly?: boolean;
  pageNo: number;
  pageSize: number;
  sortField?: string;
  sortOrder?: string;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number | string;
  productTableName?: string;
  productBusNo?: string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  expirationDate?: string;
  availableQuantity?: number;
  unitBands?: unknown;
  purchaseUnitPrice?: number;
  inventoryItemId?: number | string;
}

export interface PageProductsForReturnToWarehouseResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export interface PageLinkableReturnSourceDocumentsRequest {
  /** 药房位置（入库单 purpose = 药房） */
  sourceLocationId: number | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export async function loadReturnToWarehouseFormInitApi(
  body?: LoadReturnToWarehouseFormInitRequest,
) {
  return requestClient.post<ApiResponse<LoadReturnToWarehouseFormInitResponse>>(
    `${BASE}/loadReturnToWarehouseFormInit`,
    body ?? {},
  );
}

export async function pageReturnToWarehousesApi(body: PageReturnToWarehousesRequest) {
  return requestClient.post<ApiResponse<PageReturnToWarehousesResponse>>(
    `${BASE}/pageReturnToWarehouses`,
    body,
  );
}

export async function getReturnToWarehouseDetailApi(body: GetReturnToWarehouseDetailRequest) {
  return requestClient.post<ApiResponse<GetReturnToWarehouseDetailResponse>>(
    `${BASE}/getReturnToWarehouseDetail`,
    body,
  );
}

export async function createReturnToWarehouseApi(body: CreateReturnToWarehouseRequest) {
  return requestClient.post<ApiResponse<CreateReturnToWarehouseResponse>>(
    `${BASE}/createReturnToWarehouse`,
    body,
  );
}

export async function updateReturnToWarehouseApi(body: UpdateReturnToWarehouseRequest) {
  return requestClient.post<ApiResponse<UpdateReturnToWarehouseResponse>>(
    `${BASE}/updateReturnToWarehouse`,
    body,
  );
}

export async function submitReturnToWarehouseApprovalApi(
  body: SubmitReturnToWarehouseApprovalRequest,
) {
  return requestClient.post<ApiResponse<SubmitReturnToWarehouseApprovalResponse>>(
    `${BASE}/submitReturnToWarehouseApproval`,
    body,
  );
}

export async function withdrawReturnToWarehouseApprovalApi(
  body: WithdrawReturnToWarehouseApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawReturnToWarehouseApprovalResponse>>(
    `${BASE}/withdrawReturnToWarehouseApproval`,
    body,
  );
}

export async function agreeReturnToWarehouseApi(body: AgreeReturnToWarehouseRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreeReturnToWarehouse`,
    body,
  );
}

export async function rejectReturnToWarehouseApi(body: RejectReturnToWarehouseRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectReturnToWarehouse`,
    body,
  );
}

export async function removeReturnToWarehouseApi(body: RemoveReturnToWarehouseRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeReturnToWarehouse`,
    body,
  );
}

export async function pageProductsForReturnToWarehouseApi(
  body: PageProductsForReturnToWarehouseRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForReturnToWarehouseResponse>>(
    `${BASE}/pageProductsForReturnToWarehouse`,
    body,
  );
}

export async function pageLinkableReturnSourceDocumentsApi(
  body: PageLinkableReturnSourceDocumentsRequest,
) {
  return requestClient.post<ApiResponse<PageReturnToWarehousesResponse>>(
    `${BASE}/pageLinkableReturnSourceDocuments`,
    body,
  );
}

export interface GetLinkablePharmacyStockInDetailRequest {
  busNo: string;
  sourceLocationId?: number | string;
  excludeReturnBusNo?: string;
}

export interface ReturnablePharmacyStockInLineDto {
  itemTable?: string;
  itemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  returnableQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  /** 产品目录（含 unitBands，可退行单位回显用） */
  productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown };
  lotNumber?: string;
  traceNo?: string;
  remake?: string;
}

export interface GetLinkablePharmacyStockInDetailResponse {
  originalBusNo?: string;
  stockInBusNo?: string;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  supplierId?: number | string;
  categoryEnum?: string;
  practitionerId?: number | string;
  lines: ReturnablePharmacyStockInLineDto[];
}

export async function getLinkablePharmacyStockInDetailApi(
  body: GetLinkablePharmacyStockInDetailRequest,
) {
  return requestClient.post<ApiResponse<GetLinkablePharmacyStockInDetailResponse>>(
    `${BASE}/getLinkablePharmacyStockInDetail`,
    body,
  );
}

/** 药房 init → 侧栏 cabinet 行（含「全部药房」）。 */
export function mapReturnToWarehouseInitToCabinetRows(
  opts?: PharmacyLocationOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(
    (opts ?? []).map((o) => ({
      value: o.locationId,
      label: o.displayName,
    })),
    INV_ALL_PHARMACIES_LABEL,
  );
}

/** 工作站左侧列表：sourceLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageReturnToWarehouseWorkbenchListApi(params: {
  sourceLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: ReturnToWarehouseListRowDto[]; total: number }> {
  const sid = coerceWorkbenchListLocationIdForApi(params.sourceLocationId);
  const res = await pageReturnToWarehousesApi({
    sourceLocationId: sid,
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<ReturnToWarehouseListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params.applyTimeBegin,
    params.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params.keyword,
  ) as ReturnToWarehouseListRowDto[];
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}

/** SupplyRequestType：PHARMACY=16，CABINET=11；SupplyCategory STOCK_SUPPLY=1 */
export const PHA_RTW_SOURCE_TYPE_PHARMACY = 16;
export const PHA_RTW_PURPOSE_TYPE_CABINET = 11;
export const PHA_RTW_CATEGORY_STOCK = 1;
