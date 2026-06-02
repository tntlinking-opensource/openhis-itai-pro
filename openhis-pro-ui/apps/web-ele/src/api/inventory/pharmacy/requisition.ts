/**
 * 药房请领单 — 对接 {@code POST /api/app/inventoryPharmacyRequisition/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryPharmacyRequisition';

// --- DTO（与 Java 驼峰一致）---

export interface CabinetLocationOptionDto {
  locationId?: number | string;
  displayName?: string;
}

export interface PharmacyLocationOptionDto {
  /** 常为雪花 Long 字符串，与 init 下拉 {@code value} 一致 */
  locationId?: number | string;
  displayName?: string;
}

export interface LoadPharmacyRequisitionFormInitRequest {
  organizationId?: number;
}

export interface LoadPharmacyRequisitionFormInitResponse {
  cabinetLocationOptions: CabinetLocationOptionDto[];
  pharmacyLocationOptions: PharmacyLocationOptionDto[];
}

export interface PagePharmacyRequisitionsRequest {
  /** 目的药房，对应读模型 purpose_location_id */
  purposeLocationId: number;
  statusEnum?: number;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  typeEnum?: number;
}

export interface WarehouseDocumentDetailRowDto {
  id?: number;
  busNo?: string;
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
  totalPrice?: number;
}

export interface PagePharmacyRequisitionsResponse {
  total?: number;
  records: WarehouseDocumentDetailRowDto[];
}

export interface GetPharmacyRequisitionDetailRequest {
  busNo: string;
}

export interface PharmacyRequisitionDetailLineDto {
  id?: number;
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  productName?: string;
}

export interface GetPharmacyRequisitionDetailResponse {
  busNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  lines: PharmacyRequisitionDetailLineDto[];
}

export interface PharmacyRequisitionLineDto {
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  occurrenceTime?: string;
  reason?: string;
}

export interface CreatePharmacyRequisitionRequest {
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum: number;
  purposeTypeEnum: number;
  categoryEnum?: number;
  applicantId: number;
  applyTime: string;
  lines: PharmacyRequisitionLineDto[];
  typeEnum?: number;
}

export interface UpdatePharmacyRequisitionRequest {
  busNo: string;
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum: number;
  purposeTypeEnum: number;
  categoryEnum?: number;
  applicantId: number;
  applyTime: string;
  lines: PharmacyRequisitionLineDto[];
  typeEnum?: number;
}

export interface CreatePharmacyRequisitionResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdatePharmacyRequisitionResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitPharmacyRequisitionApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface WithdrawPharmacyRequisitionApprovalRequest {
  busNo: string;
}

export interface SubmitPharmacyRequisitionApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface WithdrawPharmacyRequisitionApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface AgreePharmacyRequisitionRequest {
  busNo: string;
  operatorId?: number;
  approvalClock?: string;
}

export interface AgreePharmacyRequisitionResponse {
  busNo?: string;
  stockOutBusNo?: string;
  pharmacyStockInBusNo?: string;
}

export interface RejectPharmacyRequisitionRequest {
  busNo: string;
  rejectReason?: string;
  operatorId?: number;
  approvalClock?: string;
}

export interface RemovePharmacyRequisitionRequest {
  busNo: string;
}

export interface PageProductsForPharmacyRequisitionRequest {
  productTableName?: string;
  keyword?: string;
  categoryCode?: string;
  activeFlag?: boolean;
  supplierId?: number;
  manufacturer?: string;
  includeInventory?: boolean;
  sourceLocationId?: number;
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

export interface PageProductsForPharmacyRequisitionResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

// --- 与后端 SupplyType / SupplyRequestType / SupplyCategory 序列号一致 ---

/** 药房请领单 */
export const PHA_QL_TYPE_ENUM = 12;
/** 源：药库 CABINET */
export const PHA_QL_SOURCE_TYPE_CABINET = 11;
/** 目的：药房 PHARMACY */
export const PHA_QL_PURPOSE_TYPE_PHARMACY = 16;
/** 库存供应 */
export const PHA_QL_CATEGORY_STOCK = 1;

// --- API ---

export async function loadPharmacyRequisitionFormInitApi(
  body?: LoadPharmacyRequisitionFormInitRequest,
) {
  return requestClient.post<ApiResponse<LoadPharmacyRequisitionFormInitResponse>>(
    `${BASE}/loadPharmacyRequisitionFormInit`,
    body ?? {},
  );
}

export async function pagePharmacyRequisitionsApi(body: PagePharmacyRequisitionsRequest) {
  return requestClient.post<ApiResponse<PagePharmacyRequisitionsResponse>>(
    `${BASE}/pagePharmacyRequisitions`,
    body,
  );
}

export async function getPharmacyRequisitionDetailApi(body: GetPharmacyRequisitionDetailRequest) {
  return requestClient.post<ApiResponse<GetPharmacyRequisitionDetailResponse>>(
    `${BASE}/getPharmacyRequisitionDetail`,
    body,
  );
}

export async function createPharmacyRequisitionApi(body: CreatePharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<CreatePharmacyRequisitionResponse>>(
    `${BASE}/createPharmacyRequisition`,
    body,
  );
}

export async function updatePharmacyRequisitionApi(body: UpdatePharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<UpdatePharmacyRequisitionResponse>>(
    `${BASE}/updatePharmacyRequisition`,
    body,
  );
}

export async function submitPharmacyRequisitionApprovalApi(
  body: SubmitPharmacyRequisitionApprovalRequest,
) {
  return requestClient.post<ApiResponse<SubmitPharmacyRequisitionApprovalResponse>>(
    `${BASE}/submitPharmacyRequisitionApproval`,
    body,
  );
}

export async function withdrawPharmacyRequisitionApprovalApi(
  body: WithdrawPharmacyRequisitionApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawPharmacyRequisitionApprovalResponse>>(
    `${BASE}/withdrawPharmacyRequisitionApproval`,
    body,
  );
}

export async function agreePharmacyRequisitionApi(body: AgreePharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<AgreePharmacyRequisitionResponse>>(
    `${BASE}/agreePharmacyRequisition`,
    body,
  );
}

export async function rejectPharmacyRequisitionApi(body: RejectPharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectPharmacyRequisition`,
    body,
  );
}

export async function removePharmacyRequisitionApi(body: RemovePharmacyRequisitionRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removePharmacyRequisition`,
    body,
  );
}

export async function pageProductsForPharmacyRequisitionApi(
  body: PageProductsForPharmacyRequisitionRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForPharmacyRequisitionResponse>>(
    `${BASE}/pageProductsForPharmacyRequisition`,
    body,
  );
}
