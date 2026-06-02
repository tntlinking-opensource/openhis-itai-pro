/**
 * 药库出库单 — 对接 Whale {@code POST /api/app/inventoryWarehouseStockOut/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryWarehouseStockOut';

/** 主区/侧栏用的简化库位行（由 init 的 Option 列表映射而来）。 */
export interface WarehouseOptionDto {
  /** 对应 init 选项 {@code value}，常为雪花 Long 字符串 */
  locationId?: number | string;
  displayName?: string;
  active?: boolean;
}

/** 与后端 {@code loadStockOutFormInit} 返回的下拉项同构（如 OptionDto）。 */
export interface StockOutFormInitOptionDto {
  sort?: string;
  value?: string;
  label?: string;
  busNo?: null | string;
  children?: null | StockOutFormInitOptionDto[];
}

export interface LoadStockOutFormInitRequest {
  organizationId?: number;
}

export interface LoadStockOutFormInitResponse {
  warehouseLocationOptions?: StockOutFormInitOptionDto[];
  pharmacyLocationOptions?: StockOutFormInitOptionDto[];
  inboundItemTableTypeOptions?: StockOutFormInitOptionDto[];
  statusOptions?: StockOutFormInitOptionDto[];
  pharmacistOptions?: StockOutFormInitOptionDto[];
  warehouseKeeperOptions?: StockOutFormInitOptionDto[];
}

export interface StockOutListRowDto {
  id?: number;
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: number;
  statusEnum?: number | string;
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

export interface PageStockOutsRequest {
  /** 源药库 Location.id；雪花 Long 与后端一致时用 string */
  sourceLocationId: number | string;
  statusEnum?: number | string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  /**
   * 单据类型：与后端枚举第二字段（name）一致，如 {@code CABINET_STOCK_OUT}。
   * 历史实现曾传排序号 4，现已改为传枚举 name 字符串。
   */
  typeEnum?: number | string;
}

export interface PageStockOutsResponse {
  total?: number;
  records: StockOutListRowDto[];
}

export interface GetStockOutDetailRequest {
  busNo: string;
}

/** 与后端 {@code ProductDefinitionCatalogEditorHintsDto} / 详情行 {@code productCatalog} 同构 */
export interface StockOutProductCatalogUnitBandDto {
  bandKind?: string;
  unitCode?: string;
  unitName?: string;
  ratioToSplit?: number;
  unitPrice?: null | number;
}

export interface StockOutProductCatalogDto {
  unitBands?: StockOutProductCatalogUnitBandDto[];
  purchasePrice?: number;
  productBasePrice?: number;
  minUnitCode?: string;
  defaultPurchaseUnitCode?: string;
  merchandiseName?: string;
  categoryCode?: string;
  categoryCodeDictText?: string;
  productBusNo?: string;
  totalVolume?: string;
}

export interface StockOutDetailLineDto {
  /** 明细行主键（雪花 Long 字符串） */
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  /** 持久化为 SPLIT 基准数量 */
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  occurrenceTime?: string | null;
  reason?: string;
  remake?: string | null;
  productName?: string | null;
  specification?: string | null;
  manufacturer?: string | null;
  lotNumber?: string;
  traceNo?: string | null;
  price?: number;
  totalPrice?: number;
  startTime?: string | null;
  endTime?: string | null;
  productCatalog?: StockOutProductCatalogDto | null;
}

export interface GetStockOutDetailResponse {
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: number;
  statusEnum?: number | string;
  sourceLocationId?: number;
  purposeLocationId?: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  lines: StockOutDetailLineDto[];
}

export interface StockOutLineDto {
  itemTable?: string;
  itemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  /** 选品库存行主键（与 IF-WHS-SO-11 inventoryItemId 一致，草稿可用量精确比对） */
  inventoryItemId?: number | string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
}

export interface CreateStockOutRequest {
  originalBusNo?: string;
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  /** 经手人：医务人员主键，与后端 {@code CreateStockOutRequest.practitionerId} 一致 */
  practitionerId?: number | string;
  lines: StockOutLineDto[];
}

export interface UpdateStockOutRequest {
  busNo: string;
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  practitionerId?: number | string;
  lines: StockOutLineDto[];
  rowVersion?: number;
}

export interface CreateStockOutResponse {
  busNo?: string;
  statusEnum?: number | string;
}

export interface UpdateStockOutResponse {
  busNo?: string;
  statusEnum?: number | string;
}

export interface SubmitStockOutApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitStockOutApprovalResponse {
  success?: boolean;
  statusEnum?: number | string;
}

export interface WithdrawStockOutApprovalRequest {
  busNo: string;
}

export interface WithdrawStockOutApprovalResponse {
  success?: boolean;
  statusEnum?: number | string;
}

export interface AgreeStockOutRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectStockOutRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason?: string;
}

export interface RemoveStockOutRequest {
  busNo: string;
}

/** 与后端 {@code PageProductsForStockOutRequest} + {@code AbstractPageQuery} 对齐（B.10.11）。 */
export interface PageProductsForStockOutRequest {
  /** 源药库位 id，选品按库过滤时必填（与侧栏/单头所选一致） */
  sourceLocationId?: number | string;
  productTableName?: string;
  categoryCode?: string;
  activeFlag?: boolean;
  supplierId?: number;
  manufacturer?: string;
  includeInventory?: boolean;
  pageNo: number;
  pageSize: number;
  keyword?: string;
  startDate?: string;
  endDate?: string;
  sortBy?: string;
  sortDirection?: string;
  where?: string[];
  sortField?: string;
  sortOrder?: string;
}

export interface ProductDefinitionItemDto {
  /** 常为雪花 Long 字符串 */
  productDefinitionId?: number | string;
  productTableName?: string;
  productBusNo?: string;
  productName?: string;
  merchandiseName?: string;
  unitCode?: string;
  minUnitCode?: string;
  categoryCode?: string;
  price?: number;
  purchasePrice?: number;
  /** 出库选品：采购单价（与 purchasePrice 并存时优先本字段） */
  purchaseUnitPrice?: number;
  manufacturer?: string;
  pyStr?: string;
  wbStr?: string;
  /** 规格文案（如 10mg） */
  specification?: string;
  lotNumber?: string;
  expirationDate?: null | string | string[];
  /** 库存行 id（批号级选品时与 productDefinitionId 并存，下拉行唯一键） */
  inventoryItemId?: number | string;
  /** 选品扩展（与进货选品同构时由读模型返回） */
  totalVolume?: string;
  availableQuantity?: number;
  unitBands?: unknown;
}

export interface PageProductsForStockOutResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export interface PageLinkablePharmacyRequisitionsRequest {
  sourceLocationId: number | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageLinkablePharmacyRequisitionsResponse {
  total?: number;
  records: StockOutListRowDto[];
}

/** 药库出库单：与 SupplyOrderType.CABINET_STOCK_OUT 枚举第二字段（name）一致 */
export const WHS_SO_TYPE_ENUM = 'CABINET_STOCK_OUT';

export async function loadStockOutFormInitApi(body?: LoadStockOutFormInitRequest) {
  return requestClient.post<ApiResponse<LoadStockOutFormInitResponse>>(
    `${BASE}/loadStockOutFormInit`,
    body ?? {},
  );
}

export async function pageStockOutsApi(body: PageStockOutsRequest) {
  return requestClient.post<ApiResponse<PageStockOutsResponse>>(`${BASE}/pageStockOuts`, body);
}

export async function getStockOutDetailApi(body: GetStockOutDetailRequest) {
  return requestClient.post<ApiResponse<GetStockOutDetailResponse>>(
    `${BASE}/getStockOutDetail`,
    body,
  );
}

export async function createStockOutApi(body: CreateStockOutRequest) {
  return requestClient.post<ApiResponse<CreateStockOutResponse>>(`${BASE}/createStockOut`, body);
}

export async function updateStockOutApi(body: UpdateStockOutRequest) {
  return requestClient.post<ApiResponse<UpdateStockOutResponse>>(`${BASE}/updateStockOut`, body);
}

export async function submitStockOutApprovalApi(body: SubmitStockOutApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitStockOutApprovalResponse>>(
    `${BASE}/submitStockOutApproval`,
    body,
  );
}

export async function withdrawStockOutApprovalApi(body: WithdrawStockOutApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawStockOutApprovalResponse>>(
    `${BASE}/withdrawStockOutApproval`,
    body,
  );
}

export async function agreeStockOutApi(body: AgreeStockOutRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeStockOut`, body);
}

export async function rejectStockOutApi(body: RejectStockOutRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectStockOut`, body);
}

export async function removeStockOutApi(body: RemoveStockOutRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeStockOut`, body);
}

export async function pageProductsForStockOutApi(body: PageProductsForStockOutRequest) {
  return requestClient.post<ApiResponse<PageProductsForStockOutResponse>>(
    `${BASE}/pageProductsForStockOut`,
    body,
  );
}

export async function pageLinkablePharmacyRequisitionsApi(
  body: PageLinkablePharmacyRequisitionsRequest,
) {
  return requestClient.post<ApiResponse<PageLinkablePharmacyRequisitionsResponse>>(
    `${BASE}/pageLinkablePharmacyRequisitions`,
    body,
  );
}
