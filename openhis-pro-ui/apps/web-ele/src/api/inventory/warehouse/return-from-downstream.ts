/**
 * 药库退库单（下游退回）— 对接 Whale {@code POST /api/app/inventoryWarehouseReturnFromDownstream/<method>}
 * type_enum=5 (CABINET_RETURN_FROM_DOWNSTREAM)
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryWarehouseReturnFromDownstream';

export interface LocationOptionDto {
  locationId?: number;
  displayName?: string;
  active?: boolean;
}

export interface LoadReturnFromDownstreamFormInitRequest {
  organizationId?: number;
}

export interface LoadReturnFromDownstreamFormInitResponse {
  warehouseOptions: LocationOptionDto[];
  departmentOptions: LocationOptionDto[];
  pharmacyOptions: LocationOptionDto[];
}

export interface WarehouseDocumentDetailRowDto {
  id?: number;
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  categoryEnum?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  supplierId?: number;
  itemId?: number;
  itemTable?: string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  applyTime?: string;
  approvalTime?: string;
}

export interface PageReturnFromDownstreamsRequest {
  purposeLocationId: number;
  statusEnum?: number;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  /** 药库退库单 typeEnum 排序号 = 5 */
  typeEnum?: number;
}

export interface PageReturnFromDownstreamsResponse {
  total?: number;
  records: WarehouseDocumentDetailRowDto[];
}

export interface GetReturnFromDownstreamDetailRequest {
  busNo: string;
}

export interface ReturnFromDownstreamDetailLineDto {
  id?: number;
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  productName?: string;
}

export interface GetReturnFromDownstreamDetailResponse {
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnFromDownstreamDetailLineDto[];
}

export interface ReturnFromDownstreamLineDto {
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
}

export interface CreateReturnFromDownstreamRequest {
  /** 可选：关联发放单号（退库可关联发放单） */
  originalBusNo?: string;
  /** 退库来源位置（科室/药房） */
  sourceLocationId: number;
  /** 退库目的药库 */
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnFromDownstreamLineDto[];
}

export interface UpdateReturnFromDownstreamRequest {
  busNo: string;
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnFromDownstreamLineDto[];
  /** 禁止传入；后端 SDD 约定 */
  rowVersion?: number;
}

export interface CreateReturnFromDownstreamResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdateReturnFromDownstreamResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitReturnFromDownstreamApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitReturnFromDownstreamApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface WithdrawReturnFromDownstreamApprovalRequest {
  busNo: string;
}

export interface WithdrawReturnFromDownstreamApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface AgreeReturnFromDownstreamRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectReturnFromDownstreamRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason: string;
}

export interface RemoveReturnFromDownstreamRequest {
  busNo: string;
}

export interface PageLinkableDeliveriesRequest {
  /** 退库来源科室/药房 */
  sourceLocationId: number;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageLinkableDeliveriesResponse {
  total?: number;
  records: WarehouseDocumentDetailRowDto[];
}

export interface PageProductsForReturnFromDownstreamRequest {
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

export interface PageProductsForReturnFromDownstreamResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

// ==================== API 方法 ====================

export async function loadReturnFromDownstreamFormInitApi(
  body?: LoadReturnFromDownstreamFormInitRequest,
) {
  return requestClient.post<ApiResponse<LoadReturnFromDownstreamFormInitResponse>>(
    `${BASE}/loadReturnFromDownstreamFormInit`,
    body ?? {},
  );
}

export async function pageReturnFromDownstreamsApi(body: PageReturnFromDownstreamsRequest) {
  return requestClient.post<ApiResponse<PageReturnFromDownstreamsResponse>>(
    `${BASE}/pageReturnFromDownstreams`,
    body,
  );
}

export async function getReturnFromDownstreamDetailApi(body: GetReturnFromDownstreamDetailRequest) {
  return requestClient.post<ApiResponse<GetReturnFromDownstreamDetailResponse>>(
    `${BASE}/getReturnFromDownstreamDetail`,
    body,
  );
}

export async function createReturnFromDownstreamApi(body: CreateReturnFromDownstreamRequest) {
  return requestClient.post<ApiResponse<CreateReturnFromDownstreamResponse>>(
    `${BASE}/createReturnFromDownstream`,
    body,
  );
}

export async function updateReturnFromDownstreamApi(body: UpdateReturnFromDownstreamRequest) {
  return requestClient.post<ApiResponse<UpdateReturnFromDownstreamResponse>>(
    `${BASE}/updateReturnFromDownstream`,
    body,
  );
}

export async function submitReturnFromDownstreamApprovalApi(
  body: SubmitReturnFromDownstreamApprovalRequest,
) {
  return requestClient.post<ApiResponse<SubmitReturnFromDownstreamApprovalResponse>>(
    `${BASE}/submitReturnFromDownstreamApproval`,
    body,
  );
}

export async function withdrawReturnFromDownstreamApprovalApi(
  body: WithdrawReturnFromDownstreamApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawReturnFromDownstreamApprovalResponse>>(
    `${BASE}/withdrawReturnFromDownstreamApproval`,
    body,
  );
}

export async function agreeReturnFromDownstreamApi(body: AgreeReturnFromDownstreamRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreeReturnFromDownstream`,
    body,
  );
}

export async function rejectReturnFromDownstreamApi(body: RejectReturnFromDownstreamRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectReturnFromDownstream`,
    body,
  );
}

export async function removeReturnFromDownstreamApi(body: RemoveReturnFromDownstreamRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeReturnFromDownstream`,
    body,
  );
}

export async function pageLinkableDeliveriesApi(body: PageLinkableDeliveriesRequest) {
  return requestClient.post<ApiResponse<PageLinkableDeliveriesResponse>>(
    `${BASE}/pageLinkableDeliveries`,
    body,
  );
}

export async function pageProductsForReturnFromDownstreamApi(
  body: PageProductsForReturnFromDownstreamRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForReturnFromDownstreamResponse>>(
    `${BASE}/pageProductsForReturnFromDownstream`,
    body,
  );
}

/** typeEnum=5；药库退库单 CABINET_RETURN_FROM_DOWNSTREAM */
export const WHS_RFD_TYPE_ENUM = 5;
/** SupplyRequestType DEPARTMENT=2 */
export const WHS_RFD_LOCATION_TYPE_DEPARTMENT = 2;
/** SupplyRequestType PHARMACY=16 */
export const WHS_RFD_LOCATION_TYPE_PHARMACY = 16;
/** SupplyRequestType CABINET=11 */
export const WHS_RFD_LOCATION_TYPE_CABINET = 11;
/** SupplyCategory STOCK_SUPPLY=1 */
export const WHS_RFD_CATEGORY_STOCK = 1;
