/**
 * 退货入库单 — 对接 Whale {@code POST /api/app/inventoryWarehouseReturnStockIn/<method>}
 * type_enum=5 (CABINET_RETURN_STOCK_IN)
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

export interface LoadReturnStockInFormInitRequest {
  organizationId?: number;
}

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
  totalPrice?: number;
  totalAmount?: number;
  applicantId?: number | string;
  creatorName?: string;
  approverId?: number | string;
  approverName?: string;
  applyTime?: string;
  approvalTime?: string;
}

export type WarehouseDocumentDetailRowDto = ReturnStockInListRowDto;

export interface PageReturnStockInsRequest {
  purposeLocationId: number | string;
  sourceLocationId?: number | string;
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
  records?: ReturnStockInListRowDto[];
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
  productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown };
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

export interface ReturnStockInLineDto {
  itemTable?: string;
  itemId?: number | string;
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

export interface CreateReturnStockInRequest {
  /** 可选：关联发放单号（退库可关联发放单） */
  originalBusNo?: string;
  /** 退库来源位置（科室/药房） */
  sourceLocationId: number | string;
  /** 退库目的药库 */
  purposeLocationId: number | string;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number | string;
  lines: ReturnStockInLineDto[];
}

export interface UpdateReturnStockInRequest {
  busNo: string;
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number | string;
  lines: ReturnStockInLineDto[];
  /** 禁止传入；后端 SDD 约定 */
  rowVersion?: number;
}

export interface CreateReturnStockInResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdateReturnStockInResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitReturnStockInApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitReturnStockInApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface WithdrawReturnStockInApprovalRequest {
  busNo: string;
}

export interface WithdrawReturnStockInApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface AgreeReturnStockInRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectReturnStockInRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason: string;
}

export interface RemoveReturnStockInRequest {
  busNo: string;
}

export interface PageLinkableDeliveriesRequest {
  /** 退库来源科室/药房 */
  sourceLocationId: number | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageLinkableDeliveriesResponse {
  total?: number;
  records: WarehouseDocumentDetailRowDto[];
}

export interface PageProductsForReturnStockInRequest {
  productTableName?: string;
  keyword?: string;
  categoryCode?: string;
  activeFlag?: boolean;
  supplierId?: number | string;
  manufacturer?: string;
  includeInventory?: boolean;
  pageNo: number;
  pageSize: number;
  sortField?: string;
  sortOrder?: string;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number | string;
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

export interface PageProductsForReturnStockInResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
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

export async function createReturnStockInApi(body: CreateReturnStockInRequest) {
  return requestClient.post<ApiResponse<CreateReturnStockInResponse>>(
    `${BASE}/createReturnStockIn`,
    body,
  );
}

export async function updateReturnStockInApi(body: UpdateReturnStockInRequest) {
  return requestClient.post<ApiResponse<UpdateReturnStockInResponse>>(
    `${BASE}/updateReturnStockIn`,
    body,
  );
}

export async function submitReturnStockInApprovalApi(body: SubmitReturnStockInApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitReturnStockInApprovalResponse>>(
    `${BASE}/submitReturnStockInApproval`,
    body,
  );
}

export async function withdrawReturnStockInApprovalApi(body: WithdrawReturnStockInApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawReturnStockInApprovalResponse>>(
    `${BASE}/withdrawReturnStockInApproval`,
    body,
  );
}

export async function agreeReturnStockInApi(body: AgreeReturnStockInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeReturnStockIn`, body);
}

export async function rejectReturnStockInApi(body: RejectReturnStockInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectReturnStockIn`,
    body,
  );
}

export async function removeReturnStockInApi(body: RemoveReturnStockInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeReturnStockIn`,
    body,
  );
}

export async function pageLinkableDeliveriesApi(body: PageLinkableDeliveriesRequest) {
  return requestClient.post<ApiResponse<PageLinkableDeliveriesResponse>>(
    `${BASE}/pageLinkableDeliveries`,
    body,
  );
}

export async function pageProductsForReturnStockInApi(body: PageProductsForReturnStockInRequest) {
  return requestClient.post<ApiResponse<PageProductsForReturnStockInResponse>>(
    `${BASE}/pageProductsForReturnStockIn`,
    body,
  );
}

/** typeEnum=5；退货入库单 CABINET_RETURN_STOCK_IN */
export const WHS_RSI_TYPE_ENUM = 5;
/** @deprecated 使用 {@link WHS_RSI_TYPE_ENUM} */
export const WHS_RETURN_STOCK_IN_TYPE_ENUM = WHS_RSI_TYPE_ENUM;
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

/** @deprecated 使用 {@link loadReturnStockInFormInitApi} */
export const loadReturnFromDownstreamFormInitApi = loadReturnStockInFormInitApi;

/** @deprecated 使用 {@link pageReturnStockInsApi} */
export async function pageReturnFromDownstreamsApi(body: PageReturnStockInsRequest) {
  return pageReturnStockInsApi(body);
}

/** @deprecated 使用 {@link getReturnStockInDetailApi} */
export async function getReturnFromDownstreamDetailApi(body: GetReturnStockInDetailRequest) {
  return getReturnStockInDetailApi(body);
}

/** @deprecated 使用 {@link WHS_RSI_TYPE_ENUM} */
export const WHS_RFD_TYPE_ENUM = WHS_RSI_TYPE_ENUM;

export type ReturnFromDownstreamDetailLineDto = ReturnStockInDetailLineDto;
export type GetReturnFromDownstreamDetailResponse = GetReturnStockInDetailResponse;
