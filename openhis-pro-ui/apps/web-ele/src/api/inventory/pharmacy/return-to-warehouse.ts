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
  locationId?: number;
  displayName?: string;
}

export interface WarehouseLocationOptionDto {
  locationId?: number;
  displayName?: string;
}

export interface LoadReturnToWarehouseFormInitRequest {
  organizationId?: number;
}

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
  itemId?: number;
  itemTable?: string;
  itemQuantity?: number;
  unitCode?: string;
  applyTime?: string;
  approvalTime?: string;
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
  records?: ReturnToWarehouseListRowDto[];
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
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  productName?: string;
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
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  occurrenceTime?: string;
  reason?: string;
  price?: number;
  totalPrice?: number;
}

export interface CreateReturnToWarehouseRequest {
  originalBusNo: string;
  sourceLocationId: number | string;
  purposeLocationId: number;
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
  approverId?: string;
  approvalClock?: string;
}

export interface RejectReturnToWarehouseRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason?: string;
}

export interface RemoveReturnToWarehouseRequest {
  busNo: string;
}

export interface PageProductsForReturnToWarehouseRequest {
  productTableName?: string;
  keyword?: string;
  categoryCode?: string;
  activeFlag?: boolean;
  supplierId?: number;
  manufacturer?: string;
  includeInventory?: boolean;
  pageNo: number;
  pageSize: number;
  sortField?: string;
  sortOrder?: string;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number;
  productTableName?: string;
  productName?: string;
  merchandiseName?: string;
  unitCode?: string;
  minUnitCode?: string;
  categoryCode?: string;
  price?: number;
  purchasePrice?: number;
  manufacturer?: string;
  pyStr?: string;
  wbStr?: string;
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
