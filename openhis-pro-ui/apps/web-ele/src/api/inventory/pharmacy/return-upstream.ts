/**
 * 药房退库单 — 对接 {@code POST /api/app/inventoryPharmacyReturnUpstream/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryPharmacyReturnUpstream';

export interface PharmacyLocationOptionDto {
  locationId?: number;
  displayName?: string;
}

export interface WarehouseLocationOptionDto {
  locationId?: number;
  displayName?: string;
}

export interface LoadReturnUpstreamFormInitRequest {
  organizationId?: number;
}

export interface LoadReturnUpstreamFormInitResponse {
  pharmacyLocationOptions: PharmacyLocationOptionDto[];
  warehouseLocationOptions: WarehouseLocationOptionDto[];
}

/** 列表行（读模型） */
export interface ReturnUpstreamListRowDto {
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

export interface PageReturnUpstreamsRequest {
  sourceLocationId: number;
  statusEnum?: number;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageReturnUpstreamsResponse {
  total?: number;
  records: ReturnUpstreamListRowDto[];
}

export interface GetReturnUpstreamDetailRequest {
  busNo: string;
}

export interface ReturnUpstreamDetailLineDto {
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

export interface GetReturnUpstreamDetailResponse {
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
  lines: ReturnUpstreamDetailLineDto[];
}

export interface ReturnUpstreamLineDto {
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

export interface CreateReturnUpstreamRequest {
  originalBusNo: string;
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnUpstreamLineDto[];
}

export interface UpdateReturnUpstreamRequest {
  busNo: string;
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnUpstreamLineDto[];
}

export interface CreateReturnUpstreamResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdateReturnUpstreamResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitReturnUpstreamApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitReturnUpstreamApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface WithdrawReturnUpstreamApprovalRequest {
  busNo: string;
}

export interface WithdrawReturnUpstreamApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface AgreeReturnUpstreamRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectReturnUpstreamRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason?: string;
}

export interface RemoveReturnUpstreamRequest {
  busNo: string;
}

export interface PageProductsForReturnUpstreamRequest {
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

export interface PageProductsForReturnUpstreamResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export interface PageLinkableReturnSourceDocumentsRequest {
  /** 药房位置（入库单 purpose = 药房） */
  sourceLocationId: number;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export async function loadReturnUpstreamFormInitApi(body?: LoadReturnUpstreamFormInitRequest) {
  return requestClient.post<ApiResponse<LoadReturnUpstreamFormInitResponse>>(
    `${BASE}/loadReturnUpstreamFormInit`,
    body ?? {},
  );
}

export async function pageReturnUpstreamsApi(body: PageReturnUpstreamsRequest) {
  return requestClient.post<ApiResponse<PageReturnUpstreamsResponse>>(
    `${BASE}/pageReturnUpstreams`,
    body,
  );
}

export async function getReturnUpstreamDetailApi(body: GetReturnUpstreamDetailRequest) {
  return requestClient.post<ApiResponse<GetReturnUpstreamDetailResponse>>(
    `${BASE}/getReturnUpstreamDetail`,
    body,
  );
}

export async function createReturnUpstreamApi(body: CreateReturnUpstreamRequest) {
  return requestClient.post<ApiResponse<CreateReturnUpstreamResponse>>(
    `${BASE}/createReturnUpstream`,
    body,
  );
}

export async function updateReturnUpstreamApi(body: UpdateReturnUpstreamRequest) {
  return requestClient.post<ApiResponse<UpdateReturnUpstreamResponse>>(
    `${BASE}/updateReturnUpstream`,
    body,
  );
}

export async function submitReturnUpstreamApprovalApi(body: SubmitReturnUpstreamApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitReturnUpstreamApprovalResponse>>(
    `${BASE}/submitReturnUpstreamApproval`,
    body,
  );
}

export async function withdrawReturnUpstreamApprovalApi(
  body: WithdrawReturnUpstreamApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawReturnUpstreamApprovalResponse>>(
    `${BASE}/withdrawReturnUpstreamApproval`,
    body,
  );
}

export async function agreeReturnUpstreamApi(body: AgreeReturnUpstreamRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreeReturnUpstream`,
    body,
  );
}

export async function rejectReturnUpstreamApi(body: RejectReturnUpstreamRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectReturnUpstream`,
    body,
  );
}

export async function removeReturnUpstreamApi(body: RemoveReturnUpstreamRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeReturnUpstream`,
    body,
  );
}

export async function pageProductsForReturnUpstreamApi(body: PageProductsForReturnUpstreamRequest) {
  return requestClient.post<ApiResponse<PageProductsForReturnUpstreamResponse>>(
    `${BASE}/pageProductsForReturnUpstream`,
    body,
  );
}

export async function pageLinkableReturnSourceDocumentsApi(
  body: PageLinkableReturnSourceDocumentsRequest,
) {
  return requestClient.post<ApiResponse<PageReturnUpstreamsResponse>>(
    `${BASE}/pageLinkableReturnSourceDocuments`,
    body,
  );
}

/** SupplyRequestType：PHARMACY=16，CABINET=11；SupplyCategory STOCK_SUPPLY=1 */
export const PHA_RU_SOURCE_TYPE_PHARMACY = 16;
export const PHA_RU_PURPOSE_TYPE_CABINET = 11;
export const PHA_RU_CATEGORY_STOCK = 1;
