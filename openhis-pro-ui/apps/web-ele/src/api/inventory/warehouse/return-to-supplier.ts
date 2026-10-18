/**
 * 药库退货单（向供应商退货）— 对接 ZXPlat {@code POST /api/app/inventoryWarehouseReturnToSupplier/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  coerceWorkbenchListLocationIdForApi,
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_WAREHOUSES_LABEL,
  mapInitOptionsToCabinetRowsWithAll,
} from '#/utils/inventory/inventory-workbench-list';

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

export interface LoadReturnToSupplierFormInitRequest {}

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
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  supplierId?: number | string;
  /** 供应商展示名（列表富化） */
  supplierName?: string;
  itemId?: number;
  itemTable?: string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  /** 单据总金额（聚合列表侧栏） */
  totalAmount?: number;
  applicantId?: number | string;
  /** 制单人展示名（列表富化） */
  creatorName?: string;
  /** 审批人展示名（列表富化） */
  approverName?: string;
  applyTime?: string;
}

export interface PageReturnToSuppliersRequest {
  sourceLocationId: number | string;
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
  totalCount?: number;
  content?: ReturnToSupplierListRowDto[];
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
  /** 计量单位展示名（详情富化） */
  unitCodeDictText?: string;
  lotNumber?: string;
  traceNo?: string;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  productName?: string;
  /** 单价（包装锚进价快照；优先进货落库价，缺省批号进价） */
  price?: number;
  /** 行合计金额（落库 total_price） */
  totalPrice?: number;
  /** 产品目录计量带，详情回显 / 换算用 */
  productCatalog?: { unitBands?: unknown; unitBandsJson?: unknown };
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
  /** 整单退货金额合计 */
  totalAmount?: number;
  lines: ReturnToSupplierDetailLineDto[];
}

export interface ReturnToSupplierLineDto {
  itemTable?: string;
  itemId?: number | string;
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
  sourceLocationId: number | string;
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: ReturnToSupplierLineDto[];
}

export interface UpdateReturnToSupplierRequest {
  busNo: string;
  sourceLocationId: number | string;
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
  /** 医保契约 busNo；上传进销存时使用 */
  contractBusNo?: string;
}

export interface RejectReturnToSupplierRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveReturnToSupplierRequest {
  busNo: string;
}

export interface PageLinkableGoodsReceiptsRequest {
  /** 药库（源位置）；`0` = 全部可见药库，与 IF-WHS-RT-12 / 侧栏约定一致 */
  sourceLocationId: number | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

/** 可关联进货单列表行（IF-WHS-RT-12） */
export interface LinkableGoodsReceiptListRowDto {
  busNo?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  supplierId?: number | string;
  supplierName?: string;
  creatorName?: string;
  applicantId?: number | string;
  totalAmount?: number;
  applyTime?: string;
  returnable?: boolean;
}

export interface PageLinkableGoodsReceiptsResponse {
  total?: number;
  records: LinkableGoodsReceiptListRowDto[];
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

export async function pageLinkableGoodsReceiptsApi(body: PageLinkableGoodsReceiptsRequest) {
  return requestClient.post<ApiResponse<PageLinkableGoodsReceiptsResponse>>(
    `${BASE}/pageLinkableGoodsReceipts`,
    body,
  );
}

export interface GetLinkableGoodsReceiptDetailRequest {
  busNo: string;
  sourceLocationId?: number | string;
  excludeReturnBusNo?: string;
}

export interface ReturnableInboundLineDto {
  itemTable?: string;
  itemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  inboundQuantity?: number;
  returnedQuantity?: number;
  returnableQuantity?: number;
  sourceAvailableQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  occurrenceTime?: string;
  remake?: string;
  productCatalog?: unknown;
}

export interface GetLinkableGoodsReceiptDetailResponse {
  originalBusNo?: string;
  goodsReceiptBusNo?: string;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  supplierId?: number | string;
  supplierName?: string;
  categoryEnum?: string;
  practitionerId?: number | string;
  lines: ReturnableInboundLineDto[];
}

export async function getLinkableGoodsReceiptDetailApi(body: GetLinkableGoodsReceiptDetailRequest) {
  return requestClient.post<ApiResponse<GetLinkableGoodsReceiptDetailResponse>>(
    `${BASE}/getLinkableGoodsReceiptDetail`,
    body,
  );
}

/** 药库 init → 侧栏 cabinet 行（含「全部库房」）。 */
export function mapReturnToSupplierInitToCabinetRows(
  opts?: WarehouseOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(
    (opts ?? []).map((o) => ({
      value: o.locationId,
      label: o.displayName,
    })),
    INV_ALL_WAREHOUSES_LABEL,
  );
}

/** 工作站左侧列表：sourceLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageReturnToSupplierWorkbenchListApi(params: {
  sourceLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: ReturnToSupplierListRowDto[]; total: number }> {
  const sid = coerceWorkbenchListLocationIdForApi(params.sourceLocationId);
  const res = await pageReturnToSuppliersApi({
    sourceLocationId: sid,
    typeEnum: WHS_RT_TYPE_ENUM,
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<ReturnToSupplierListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params.applyTimeBegin,
    params.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params.keyword,
  ) as ReturnToSupplierListRowDto[];
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}

/** typeEnum=3；SupplyRequestType CABINET=11；SupplyCategory STOCK_SUPPLY=1 */
export const WHS_RT_TYPE_ENUM = 3;
export const WHS_RT_LOCATION_TYPE_CABINET = 11;
export const WHS_RT_CATEGORY_STOCK = 1;
