/**
 * 药库退货单（向供应商退货）— 对接 Whale {@code POST /api/app/inventoryWarehouseReturnToSupplier/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryWarehouseReturnToSupplier';

export interface WarehouseOptionDto {
  locationId?: number;
  displayName?: string;
  active?: boolean;
}

export interface SupplierOptionDto {
  supplierId?: number;
  supplierName?: string;
  supplierType?: string;
}

export interface LoadReturnToSupplierFormInitRequest {
  organizationId?: number;
}

export interface LoadReturnToSupplierFormInitResponse {
  warehouseOptions: WarehouseOptionDto[];
  supplierOptions: SupplierOptionDto[];
}

export interface ReturnToSupplierListRowDto {
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

export interface PageReturnToSuppliersRequest {
  sourceLocationId: number;
  statusEnum?: number;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  /** 药库退货单 typeEnum 排序号 = 3 */
  typeEnum?: number;
}

export interface PageReturnToSuppliersResponse {
  total?: number;
  records: ReturnToSupplierListRowDto[];
}

export interface GetReturnToSupplierDetailRequest {
  busNo: string;
}

export interface ReturnToSupplierDetailLineDto {
  id?: number;
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  productName?: string;
}

export interface GetReturnToSupplierDetailResponse {
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
  lines: ReturnToSupplierDetailLineDto[];
}

export interface ReturnToSupplierLineDto {
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
}

export interface CreateReturnToSupplierRequest {
  /** 必填：已同意的药库进货单号 */
  originalBusNo: string;
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnToSupplierLineDto[];
}

export interface UpdateReturnToSupplierRequest {
  busNo: string;
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnToSupplierLineDto[];
  /** 禁止传入；后端 SDD 约定 */
  rowVersion?: number;
}

export interface CreateReturnToSupplierResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdateReturnToSupplierResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitReturnToSupplierApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitReturnToSupplierApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface WithdrawReturnToSupplierApprovalRequest {
  busNo: string;
}

export interface WithdrawReturnToSupplierApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface AgreeReturnToSupplierRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectReturnToSupplierRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason?: string;
}

export interface RemoveReturnToSupplierRequest {
  busNo: string;
}

export interface PageProductsForReturnToSupplierRequest {
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

export interface PageProductsForReturnToSupplierResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export interface PageLinkableGoodsReceiptsRequest {
  /** 药库（源位置），与后端 IF-WHS-RT-12 一致 */
  sourceLocationId: number;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageLinkableGoodsReceiptsResponse {
  total?: number;
  records: ReturnToSupplierListRowDto[];
}

export async function loadReturnToSupplierFormInitApi(body?: LoadReturnToSupplierFormInitRequest) {
  return requestClient.post<ApiResponse<LoadReturnToSupplierFormInitResponse>>(
    `${BASE}/loadReturnToSupplierFormInit`,
    body ?? {},
  );
}

export async function pageReturnToSuppliersApi(body: PageReturnToSuppliersRequest) {
  return requestClient.post<ApiResponse<PageReturnToSuppliersResponse>>(
    `${BASE}/pageReturnToSuppliers`,
    body,
  );
}

export async function getReturnToSupplierDetailApi(body: GetReturnToSupplierDetailRequest) {
  return requestClient.post<ApiResponse<GetReturnToSupplierDetailResponse>>(
    `${BASE}/getReturnToSupplierDetail`,
    body,
  );
}

export async function createReturnToSupplierApi(body: CreateReturnToSupplierRequest) {
  return requestClient.post<ApiResponse<CreateReturnToSupplierResponse>>(
    `${BASE}/createReturnToSupplier`,
    body,
  );
}

export async function updateReturnToSupplierApi(body: UpdateReturnToSupplierRequest) {
  return requestClient.post<ApiResponse<UpdateReturnToSupplierResponse>>(
    `${BASE}/updateReturnToSupplier`,
    body,
  );
}

export async function submitReturnToSupplierApprovalApi(
  body: SubmitReturnToSupplierApprovalRequest,
) {
  return requestClient.post<ApiResponse<SubmitReturnToSupplierApprovalResponse>>(
    `${BASE}/submitReturnToSupplierApproval`,
    body,
  );
}

export async function withdrawReturnToSupplierApprovalApi(
  body: WithdrawReturnToSupplierApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawReturnToSupplierApprovalResponse>>(
    `${BASE}/withdrawReturnToSupplierApproval`,
    body,
  );
}

export async function agreeReturnToSupplierApi(body: AgreeReturnToSupplierRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreeReturnToSupplier`,
    body,
  );
}

export async function rejectReturnToSupplierApi(body: RejectReturnToSupplierRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectReturnToSupplier`,
    body,
  );
}

export async function removeReturnToSupplierApi(body: RemoveReturnToSupplierRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeReturnToSupplier`,
    body,
  );
}

export async function pageProductsForReturnToSupplierApi(
  body: PageProductsForReturnToSupplierRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForReturnToSupplierResponse>>(
    `${BASE}/pageProductsForReturnToSupplier`,
    body,
  );
}

export async function pageLinkableGoodsReceiptsApi(body: PageLinkableGoodsReceiptsRequest) {
  return requestClient.post<ApiResponse<PageLinkableGoodsReceiptsResponse>>(
    `${BASE}/pageLinkableGoodsReceipts`,
    body,
  );
}

/** typeEnum=3；SupplyRequestType CABINET=11；SupplyCategory STOCK_SUPPLY=1 */
export const WHS_RT_TYPE_ENUM = 3;
export const WHS_RT_LOCATION_TYPE_CABINET = 11;
export const WHS_RT_CATEGORY_STOCK = 1;
