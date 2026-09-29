/**
 * 科室退库单 — 对接 {@code POST /api/app/inventoryDepartmentReturnToWarehouse/<method>}
 * 方法名与 {@code InventoryDepartmentReturnToWarehouseAppService} 一致。
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  coerceWorkbenchListLocationIdForApi,
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_DEPT_PHARMACIES_LABEL,
  mapInitOptionsToCabinetRowsWithAll,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryDepartmentReturnToWarehouse';

export interface LocationOptionDto {
  locationId?: number | string;
  displayName?: string;
  active?: boolean;
}

export interface LoadDeptReturnFormInitRequest {}

export interface PractitionerOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadDeptReturnFormInitResponse {
  /** 源科室药房（与后端 departmentPharmacyLocationOptions 对齐） */
  departmentPharmacyLocationOptions?: LocationOptionDto[];
  /** 目的药库（与后端 warehouseLocationOptions 对齐） */
  warehouseLocationOptions?: LocationOptionDto[];
  pharmacistOptions?: PractitionerOptionDto[];
  warehouseKeeperOptions?: PractitionerOptionDto[];
  supplierOptions?: { supplierId?: number | string; displayName?: string }[];
}

export interface DeptReturnListRowDto {
  id?: number;
  busNo?: string;
  originalBusNo?: string;
  typeEnum?: number;
  statusEnum?: number;
  sourceLocationId?: number;
  purposeLocationId?: number;
  supplierId?: number;
  supplierName?: string;
  itemId?: number;
  itemTable?: string;
  itemQuantity?: number;
  unitCode?: string;
  totalAmount?: number;
  applicantId?: number;
  creatorName?: string;
  approverName?: string;
  applyTime?: string;
}

export interface PageDeptReturnsRequest {
  sourceLocationId: number | string;
  statusEnum?: number;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  typeEnum?: number;
}

export interface PageDeptReturnsResponse {
  total?: number;
  totalCount?: number;
  content?: DeptReturnListRowDto[];
}

export interface GetDeptReturnDetailRequest {
  busNo: string;
}

export interface DeptReturnDetailLineDto {
  id?: number;
  itemTable?: string;
  itemId?: number;
  itemQuantity?: number;
  unitCode?: string;
  /** 计量单位展示名；无则勿用 unitCode 冒充 */
  unitCodeDictText?: string;
  lotNumber?: string;
  traceNo?: string;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  /** 行单价（供应请求落库价） */
  price?: number;
  /** 行金额 */
  totalPrice?: number;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  productCatalog?: {
    unitBands?: unknown;
    unitBandsJson?: unknown;
    [key: string]: unknown;
  };
}

export interface GetDeptReturnDetailResponse {
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
  practitionerId?: number | string;
  applicantId?: number | string;
  applicantName?: string;
  lines: DeptReturnDetailLineDto[];
}

export interface DeptReturnLineDto {
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

export interface CreateDeptReturnRequest {
  /** 可选：关联已同意科室入库单；空则按源科室药房库存自由选品 */
  originalBusNo?: string;
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  practitionerId?: number | string;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: DeptReturnLineDto[];
}

export interface UpdateDeptReturnRequest {
  busNo: string;
  sourceLocationId: number | string;
  purposeLocationId: number | string;
  practitionerId?: number | string;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: DeptReturnLineDto[];
  rowVersion?: number;
}

export interface CreateDeptReturnResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface UpdateDeptReturnResponse {
  busNo?: string;
  statusEnum?: number;
}

export interface SubmitDeptReturnApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitDeptReturnApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface WithdrawDeptReturnApprovalRequest {
  busNo: string;
}

export interface WithdrawDeptReturnApprovalResponse {
  success?: boolean;
  statusEnum?: number;
}

export interface AgreeDeptReturnRequest {
  busNo: string;
}

export interface RejectDeptReturnRequest {
  busNo: string;
  rejectReason: string;
}

export interface RemoveDeptReturnRequest {
  busNo: string;
}

export interface PageProductsForDeptReturnRequest {
  /** 源科室药房位置（库存选品门禁） */
  sourceLocationId: number | string;
  productTableName?: string;
  keyword?: string;
  /** true=仅返回账面量>0 的批号行 */
  positiveQuantityOnly?: boolean;
  pageNo: number;
  pageSize: number;
  sortField?: string;
  sortOrder?: string;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number | string;
  productTableName?: string;
  productBusNo?: string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  expirationDate?: string;
  availableQuantity?: number;
  unitBands?: unknown;
  purchaseUnitPrice?: number;
  inventoryItemId?: number | string;
}

export interface PageProductsForDeptReturnResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export interface PageLinkableReturnSourceDocumentsRequest {
  sourceLocationId: number | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export async function loadDeptReturnFormInitApi(body?: LoadDeptReturnFormInitRequest) {
  return requestClient.post<ApiResponse<LoadDeptReturnFormInitResponse>>(
    `${BASE}/loadReturnToWarehouseFormInit`,
    body ?? {},
  );
}

export async function pageDeptReturnsApi(body: PageDeptReturnsRequest) {
  return requestClient.post<ApiResponse<PageDeptReturnsResponse>>(
    `${BASE}/pageReturnToWarehouses`,
    body,
  );
}

export async function getDeptReturnDetailApi(body: GetDeptReturnDetailRequest) {
  return requestClient.post<ApiResponse<GetDeptReturnDetailResponse>>(
    `${BASE}/getReturnToWarehouseDetail`,
    body,
  );
}

export async function createDeptReturnApi(body: CreateDeptReturnRequest) {
  return requestClient.post<ApiResponse<CreateDeptReturnResponse>>(
    `${BASE}/createReturnToWarehouse`,
    body,
  );
}

export async function updateDeptReturnApi(body: UpdateDeptReturnRequest) {
  return requestClient.post<ApiResponse<UpdateDeptReturnResponse>>(
    `${BASE}/updateReturnToWarehouse`,
    body,
  );
}

export async function submitDeptReturnApprovalApi(body: SubmitDeptReturnApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitDeptReturnApprovalResponse>>(
    `${BASE}/submitReturnToWarehouseApproval`,
    body,
  );
}

export async function withdrawDeptReturnApprovalApi(body: WithdrawDeptReturnApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawDeptReturnApprovalResponse>>(
    `${BASE}/withdrawReturnToWarehouseApproval`,
    body,
  );
}

export async function agreeDeptReturnApi(body: AgreeDeptReturnRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreeReturnToWarehouse`,
    body,
  );
}

export async function rejectDeptReturnApi(body: RejectDeptReturnRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectReturnToWarehouse`,
    body,
  );
}

export async function removeDeptReturnApi(body: RemoveDeptReturnRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeReturnToWarehouse`,
    body,
  );
}

export async function pageProductsForDeptReturnApi(body: PageProductsForDeptReturnRequest) {
  return requestClient.post<ApiResponse<PageProductsForDeptReturnResponse>>(
    `${BASE}/pageProductsForReturnToWarehouse`,
    body,
  );
}

export async function pageLinkableReturnSourceDocumentsApi(
  body: PageLinkableReturnSourceDocumentsRequest,
) {
  return requestClient.post<ApiResponse<PageDeptReturnsResponse>>(
    `${BASE}/pageLinkableReturnSourceDocuments`,
    body,
  );
}

export interface GetLinkableStockInDetailRequest {
  busNo: string;
  sourceLocationId?: number | string;
  excludeReturnBusNo?: string;
}

export interface ReturnableStockInLineDto {
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

export interface GetLinkableStockInDetailResponse {
  originalBusNo?: string;
  stockInBusNo?: string;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  supplierId?: number | string;
  categoryEnum?: string;
  practitionerId?: number | string;
  lines: ReturnableStockInLineDto[];
}

export async function getLinkableStockInDetailApi(body: GetLinkableStockInDetailRequest) {
  return requestClient.post<ApiResponse<GetLinkableStockInDetailResponse>>(
    `${BASE}/getLinkableStockInDetail`,
    body,
  );
}

/** 科室 init → 侧栏 cabinet 行（含「全部科室药房」）。 */
export function mapDeptReturnInitToCabinetRows(
  opts?: LocationOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(
    (opts ?? []).map((o) => ({
      value: o.locationId,
      label: o.displayName,
    })),
    INV_ALL_DEPT_PHARMACIES_LABEL,
  );
}

/** 工作站左侧列表：sourceLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageDeptReturnWorkbenchListApi(params: {
  sourceLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: DeptReturnListRowDto[]; total: number }> {
  const sid = coerceWorkbenchListLocationIdForApi(params.sourceLocationId);
  const res = await pageDeptReturnsApi({
    sourceLocationId: sid,
    typeEnum: DEP_RT_TYPE_ENUM,
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<DeptReturnListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params.applyTimeBegin,
    params.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params.keyword,
  ) as DeptReturnListRowDto[];
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}

/** typeEnum=22；SupplyRequestType DEPARTMENT=2；CABINET=11；SupplyCategory STOCK=1 */
export const DEP_RT_TYPE_ENUM = 22;
export const DEP_RT_SOURCE_TYPE_DEPARTMENT = 2;
export const DEP_RT_PURPOSE_TYPE_CABINET = 11;
export const DEP_RT_CATEGORY_STOCK = 1;
