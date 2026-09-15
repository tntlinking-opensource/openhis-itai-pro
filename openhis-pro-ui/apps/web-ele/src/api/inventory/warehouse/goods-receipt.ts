/**
 * 药库进货单 — 对接 Whale {@code POST /api/app/inventoryWarehouseGoodsReceipt/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryWarehouseGoodsReceipt';

/** 与订货模块下拉 DTO 形状一致，可直接复用类型时从 purchase-order 导入亦可 */
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

export interface LoadGoodsReceiptFormInitRequest {}

export interface LoadGoodsReceiptFormInitResponse {
  warehouseOptions: WarehouseOptionDto[];
  supplierOptions: SupplierOptionDto[];
}

/** 进货列表行（读模型 + API 排序号） */
export interface GoodsReceiptListRowDto {
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
}

export interface PageGoodsReceiptsRequest {
  /** 当前药库（源位置），与后端 pageGoodsReceipts 一致 */
  sourceLocationId: number;
  statusEnum?: number;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  /** 默认药库进货单 typeEnum 排序号 = 2 */
  typeEnum?: number;
}

export interface PageGoodsReceiptsResponse {
  total?: number;
  records: GoodsReceiptListRowDto[];
}

export interface GetGoodsReceiptDetailRequest {
  busNo: string;
}

export interface GoodsReceiptDetailLineDto {
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
  remake?: string;
  productName?: string;
}

export interface GetGoodsReceiptDetailResponse {
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
  /** 乐观锁，保存修订时原样回传 */
  rowVersion?: number;
  lines: GoodsReceiptDetailLineDto[];
}

export interface GoodsReceiptLineDto {
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  remake?: string;
}

export interface CreateGoodsReceiptRequest {
  originalBusNo?: string;
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum: number;
  purposeTypeEnum: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: GoodsReceiptLineDto[];
}

export interface UpdateGoodsReceiptRequest {
  busNo: string;
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum: number;
  purposeTypeEnum: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: GoodsReceiptLineDto[];
  /** 必填：与 getGoodsReceiptDetail 返回的 rowVersion 一致 */
  rowVersion: number;
}

export interface CreateGoodsReceiptResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdateGoodsReceiptResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitGoodsReceiptApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitGoodsReceiptApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface WithdrawGoodsReceiptApprovalRequest {
  busNo: string;
}

export interface WithdrawGoodsReceiptApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface AgreeGoodsReceiptRequest {
  busNo: string;
}

export interface RejectGoodsReceiptRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveGoodsReceiptRequest {
  busNo: string;
}

export interface PageProductsForGoodsReceiptRequest {
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

export interface PageProductsForGoodsReceiptResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export interface PageLinkablePurchaseOrdersRequest {
  /** 订货目的药库，与订货单 purpose_location_id 一致 */
  purposeLocationId: number;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageLinkablePurchaseOrdersResponse {
  total?: number;
  records: GoodsReceiptListRowDto[];
}

export async function loadGoodsReceiptFormInitApi(body?: LoadGoodsReceiptFormInitRequest) {
  return requestClient.post<ApiResponse<LoadGoodsReceiptFormInitResponse>>(
    `${BASE}/loadGoodsReceiptFormInit`,
    body ?? {},
  );
}

export async function pageGoodsReceiptsApi(body: PageGoodsReceiptsRequest) {
  return requestClient.post<ApiResponse<PageGoodsReceiptsResponse>>(
    `${BASE}/pageGoodsReceipts`,
    body,
  );
}

export async function getGoodsReceiptDetailApi(body: GetGoodsReceiptDetailRequest) {
  return requestClient.post<ApiResponse<GetGoodsReceiptDetailResponse>>(
    `${BASE}/getGoodsReceiptDetail`,
    body,
  );
}

export async function createGoodsReceiptApi(body: CreateGoodsReceiptRequest) {
  return requestClient.post<ApiResponse<CreateGoodsReceiptResponse>>(
    `${BASE}/createGoodsReceipt`,
    body,
  );
}

export async function updateGoodsReceiptApi(body: UpdateGoodsReceiptRequest) {
  return requestClient.post<ApiResponse<UpdateGoodsReceiptResponse>>(
    `${BASE}/updateGoodsReceipt`,
    body,
  );
}

export async function submitGoodsReceiptApprovalApi(body: SubmitGoodsReceiptApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitGoodsReceiptApprovalResponse>>(
    `${BASE}/submitGoodsReceiptApproval`,
    body,
  );
}

export async function withdrawGoodsReceiptApprovalApi(body: WithdrawGoodsReceiptApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawGoodsReceiptApprovalResponse>>(
    `${BASE}/withdrawGoodsReceiptApproval`,
    body,
  );
}

export async function agreeGoodsReceiptApi(body: AgreeGoodsReceiptRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeGoodsReceipt`, body);
}

export async function rejectGoodsReceiptApi(body: RejectGoodsReceiptRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectGoodsReceipt`, body);
}

export async function removeGoodsReceiptApi(body: RemoveGoodsReceiptRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeGoodsReceipt`, body);
}

export async function pageProductsForGoodsReceiptApi(body: PageProductsForGoodsReceiptRequest) {
  return requestClient.post<ApiResponse<PageProductsForGoodsReceiptResponse>>(
    `${BASE}/pageProductsForGoodsReceipt`,
    body,
  );
}

export async function pageLinkablePurchaseOrdersApi(body: PageLinkablePurchaseOrdersRequest) {
  return requestClient.post<ApiResponse<PageLinkablePurchaseOrdersResponse>>(
    `${BASE}/pageLinkablePurchaseOrders`,
    body,
  );
}

/** 药库进货单 typeEnum 排序号 = 2；位置类型 11；类别 1=库存供应（与后端 SupplyRequestType / SupplyCategory 一致） */
export const WHS_GR_TYPE_ENUM = 2;
export const WHS_GR_LOCATION_TYPE_CABINET = 11;
export const WHS_GR_CATEGORY_STOCK = 1;
