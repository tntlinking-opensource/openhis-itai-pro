/**
 * 药库订货单 — 对接 Whale {@code POST /api/app/inventoryWarehousePurchaseOrder/<method>}
 * baseURL 含 /api/app 时使用短路径 /inventoryWarehousePurchaseOrder/...
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryWarehousePurchaseOrder';

// --- 与后端 DTO 对齐的 TypeScript 类型（驼峰与 Java 一致）---

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

export interface LoadPurchaseOrderFormInitRequest {
  organizationId?: number;
}

export interface LoadPurchaseOrderFormInitResponse {
  warehouseOptions: WarehouseOptionDto[];
  supplierOptions: SupplierOptionDto[];
  /** 左侧列表「全部药库」筛选项；locationId 为占位（约定 0），与后端一致 */
  allWarehousesOption?: WarehouseOptionDto;
}

export interface PagePurchaseOrdersRequest {
  /**
   * 订货目的药库 Location.id（后端读模型 {@code purpose_location_id}）。
   * {@link WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID}（0）表示全部（机构药库 ID IN；无药库主数据时按 tenant_id 回退）。
   * 请求体请使用 JSON 数字 {@code 0}，勿使用字符串 {@code "0"}。
   */
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

export interface PagePurchaseOrdersResponse {
  total?: number;
  records: WarehouseDocumentDetailRowDto[];
}

export interface GetPurchaseOrderDetailRequest {
  busNo: string;
}

export interface PurchaseOrderDetailLineDto {
  id?: number;
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  remake?: string;
  productName?: string;
}

export interface GetPurchaseOrderDetailResponse {
  busNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  supplierId?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  /** 乐观锁：修订订货单时回传 */
  rowVersion?: number;
  lines: PurchaseOrderDetailLineDto[];
}

export interface PurchaseOrderLineDto {
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  remake?: string;
}

export interface CreatePurchaseOrderRequest {
  /** 入库目的药库 */
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: PurchaseOrderLineDto[];
  typeEnum?: number;
}

export interface UpdatePurchaseOrderRequest {
  busNo: string;
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: PurchaseOrderLineDto[];
  rowVersion?: number;
}

export interface CreatePurchaseOrderResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdatePurchaseOrderResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitPurchaseOrderApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface WithdrawPurchaseOrderApprovalRequest {
  busNo: string;
}

/** 与后端 SubmitPurchaseOrderApprovalResponse 一致 */
export interface SubmitPurchaseOrderApprovalResponse {
  success?: boolean;
  /** SupplyStatus.sortOrder，成功提交后为审核中 = 2 */
  statusEnum?: number;
}

/** 与后端 WithdrawPurchaseOrderApprovalResponse 一致 */
export interface WithdrawPurchaseOrderApprovalResponse {
  success?: boolean;
  /** SupplyStatus.sortOrder，撤回后为已撤回 = 9 */
  statusEnum?: number;
}

export interface AgreePurchaseOrderRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectPurchaseOrderRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason?: string;
}

export interface RemovePurchaseOrderRequest {
  busNo: string;
}

export interface PageProductsForPurchaseRequest {
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
  /** 项目业务编码（选品下拉「项目编码」列） */
  productBusNo?: string;
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

export interface PageProductsForPurchaseResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

// --- API ---

export async function loadPurchaseOrderFormInitApi(body?: LoadPurchaseOrderFormInitRequest) {
  return requestClient.post<ApiResponse<LoadPurchaseOrderFormInitResponse>>(
    `${BASE}/loadPurchaseOrderFormInit`,
    body ?? {},
  );
}

export async function pagePurchaseOrdersApi(body: PagePurchaseOrdersRequest) {
  return requestClient.post<ApiResponse<PagePurchaseOrdersResponse>>(
    `${BASE}/pagePurchaseOrders`,
    body,
  );
}

export async function getPurchaseOrderDetailApi(body: GetPurchaseOrderDetailRequest) {
  return requestClient.post<ApiResponse<GetPurchaseOrderDetailResponse>>(
    `${BASE}/getPurchaseOrderDetail`,
    body,
  );
}

export async function createPurchaseOrderApi(body: CreatePurchaseOrderRequest) {
  return requestClient.post<ApiResponse<CreatePurchaseOrderResponse>>(
    `${BASE}/createPurchaseOrder`,
    body,
  );
}

export async function updatePurchaseOrderApi(body: UpdatePurchaseOrderRequest) {
  return requestClient.post<ApiResponse<UpdatePurchaseOrderResponse>>(
    `${BASE}/updatePurchaseOrder`,
    body,
  );
}

export async function submitPurchaseOrderApprovalApi(body: SubmitPurchaseOrderApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitPurchaseOrderApprovalResponse>>(
    `${BASE}/submitPurchaseOrderApproval`,
    body,
  );
}

export async function withdrawPurchaseOrderApprovalApi(body: WithdrawPurchaseOrderApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawPurchaseOrderApprovalResponse>>(
    `${BASE}/withdrawPurchaseOrderApproval`,
    body,
  );
}

export async function agreePurchaseOrderApi(body: AgreePurchaseOrderRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreePurchaseOrder`, body);
}

export async function rejectPurchaseOrderApi(body: RejectPurchaseOrderRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectPurchaseOrder`,
    body,
  );
}

export async function removePurchaseOrderApi(body: RemovePurchaseOrderRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removePurchaseOrder`,
    body,
  );
}

export async function pageProductsForPurchaseApi(body: PageProductsForPurchaseRequest) {
  return requestClient.post<ApiResponse<PageProductsForPurchaseResponse>>(
    `${BASE}/pageProductsForPurchase`,
    body,
  );
}

/** 分页列表「全部药库」占位 purposeLocationId（非真实 Location）；与后端 InventoryWarehousePurchaseOrderAppService.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID 一致 */
export const WHS_PO_ALL_WAREHOUSES_PURPOSE_LOCATION_ID = 0;

/** 与后端一致：药库订货单 typeEnum=1、位置类型 11=库房、类别 1=库存供应 */
export const WHS_PO_TYPE_ENUM = 1;
export const WHS_PO_LOCATION_TYPE_CABINET = 11;
export const WHS_PO_CATEGORY_STOCK = 1;
