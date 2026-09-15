/**
 * 科室采购申请单 — 对接 Whale
 * {@code POST /api/app/inventoryDepartmentPurchaseRequest/<method>}
 * 契约见 AS-INV-DEP-007-SDD；选品同构订货（产品定义级，无批号/无供应商）。
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_DEPT_PHARMACIES_LABEL,
  mapInitOptionsToCabinetRowsWithAll,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryDepartmentPurchaseRequest';

/** {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_PURCHASE_REQUEST} */
export const DEPT_PURCHASE_REQUEST_TYPE_ENUM = 'DEPT_PURCHASE_REQUEST';

/** {@link com.openhis.health.domain.share.enums.SupplyCategory#STOCK_SUPPLY} */
export const DEPT_CPR_CATEGORY_STOCK = 'STOCK_SUPPLY';

/** 列表「全部科室药房」占位 purposeLocationId（与 init OptionDto.value 一致，字符串） */
export const DEP_CPR_ALL_DEPARTMENTS_PURPOSE_LOCATION_ID = '0';

export interface DeptPurchaseRequestOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadDeptPurchaseRequestFormInitRequest {}

export interface LoadDeptPurchaseRequestFormInitResponse {
  departmentPharmacyLocationOptions?: DeptPurchaseRequestOptionDto[];
  statusOptions?: DeptPurchaseRequestOptionDto[];
  purchaseItemTableTypeOptions?: DeptPurchaseRequestOptionDto[];
  pharmacistOptions?: DeptPurchaseRequestOptionDto[];
  warehouseKeeperOptions?: DeptPurchaseRequestOptionDto[];
}

export interface PageDeptPurchaseRequestsRequest {
  /** 目的科室药房；{@link DEP_CPR_ALL_DEPARTMENTS_PURPOSE_LOCATION_ID}（0）表示全部 */
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface DeptPurchaseRequestListRowDto {
  busNo?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  statusEnum?: string;
  statusEnumText?: string;
  rejectReason?: string;
  creatorName?: string;
  approverName?: string;
  totalAmount?: number;
  applyTime?: string;
  occurrenceTime?: string;
}

export interface PageDeptPurchaseRequestsResponse {
  totalCount?: number;
  total?: number;
  pageNo?: number;
  pageSize?: number;
  content?: DeptPurchaseRequestListRowDto[];
}

export interface GetDeptPurchaseRequestDetailRequest {
  busNo: string;
}

export interface DeptPurchaseRequestDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  price?: number;
  totalPrice?: number;
  remake?: string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  productCatalog?: unknown;
}

export interface GetDeptPurchaseRequestDetailResponse {
  busNo?: string;
  typeEnum?: string;
  statusEnum?: string;
  purposeLocationId?: number | string;
  purposeTypeEnum?: string;
  categoryEnum?: string;
  practitionerId?: number | string;
  occurrenceTime?: string;
  remake?: string;
  rowVersion?: number | string;
  lines: DeptPurchaseRequestDetailLineDto[];
}

export interface DeptPurchaseRequestLineDto {
  itemTable: string;
  itemId: number | string;
  itemQuantity: number;
  unitCode: string;
  price?: number;
  totalPrice?: number;
  remake?: string;
}

export interface CreateDeptPurchaseRequestRequest {
  purposeLocationId: number | string;
  occurrenceTime: string;
  practitionerId?: number | string;
  categoryEnum?: string;
  remake?: string;
  lines: DeptPurchaseRequestLineDto[];
}

export interface UpdateDeptPurchaseRequestRequest {
  busNo: string;
  purposeLocationId: number | string;
  occurrenceTime: string;
  practitionerId?: number | string;
  categoryEnum?: string;
  remake?: string;
  lines: DeptPurchaseRequestLineDto[];
  rowVersion?: number | string;
}

export interface CreateDeptPurchaseRequestResponse {
  busNo?: string;
  statusEnum?: string;
}

export interface UpdateDeptPurchaseRequestResponse {
  busNo?: string;
  statusEnum?: string;
}

export interface SubmitDeptPurchaseRequestApprovalRequest {
  busNo: string;
  applyClock?: string;
}

export interface SubmitDeptPurchaseRequestApprovalResponse {
  success?: boolean;
  statusEnum?: string;
}

export interface WithdrawDeptPurchaseRequestApprovalRequest {
  busNo: string;
}

export interface WithdrawDeptPurchaseRequestApprovalResponse {
  success?: boolean;
  statusEnum?: string;
}

export interface AgreeDeptPurchaseRequestRequest {
  busNo: string;
}

export interface RejectDeptPurchaseRequestRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveDeptPurchaseRequestRequest {
  busNo: string;
}

export interface PageProductsForDeptPurchaseRequestRequest {
  purposeLocationId: number | string;
  productTableName?: string;
  keyword?: string;
  categoryCode?: string;
  activeFlag?: boolean;
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

export interface PageProductsForDeptPurchaseRequestResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
  content?: ProductDefinitionItemDto[];
}

export async function loadDeptPurchaseRequestFormInitApi(
  body?: LoadDeptPurchaseRequestFormInitRequest,
) {
  return requestClient.post<ApiResponse<LoadDeptPurchaseRequestFormInitResponse>>(
    `${BASE}/loadDeptPurchaseRequestFormInit`,
    body ?? {},
  );
}

export async function pageDeptPurchaseRequestsApi(body: PageDeptPurchaseRequestsRequest) {
  return requestClient.post<ApiResponse<PageDeptPurchaseRequestsResponse>>(
    `${BASE}/pageDeptPurchaseRequests`,
    body,
  );
}

/** 工作站左侧列表：未选科室药房 → purposeLocationId=0；日期在前端按 occurrence/apply 筛。 */
export async function pageDeptPurchaseRequestWorkbenchListApi(params?: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: DeptPurchaseRequestListRowDto[]; total: number }> {
  const pid = resolveWorkbenchListPurposeLocationId(params?.purposeLocationId);
  const res = await pageDeptPurchaseRequestsApi({
    purposeLocationId: pid,
    keyword: params?.keyword?.trim() || undefined,
    pageNo: params?.pageNo ?? 1,
    pageSize: params?.pageSize ?? 100,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<DeptPurchaseRequestListRowDto>(
    res.data,
  );
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params?.applyTimeBegin,
    params?.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params?.keyword,
  ) as DeptPurchaseRequestListRowDto[];
  const filtered = (params?.applyTimeBegin && params?.applyTimeEnd) || params?.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}

export async function getDeptPurchaseRequestDetailApi(body: GetDeptPurchaseRequestDetailRequest) {
  return requestClient.post<ApiResponse<GetDeptPurchaseRequestDetailResponse>>(
    `${BASE}/getDeptPurchaseRequestDetail`,
    body,
  );
}

export async function createDeptPurchaseRequestApi(body: CreateDeptPurchaseRequestRequest) {
  return requestClient.post<ApiResponse<CreateDeptPurchaseRequestResponse>>(
    `${BASE}/createDeptPurchaseRequest`,
    body,
  );
}

export async function updateDeptPurchaseRequestApi(body: UpdateDeptPurchaseRequestRequest) {
  return requestClient.post<ApiResponse<UpdateDeptPurchaseRequestResponse>>(
    `${BASE}/updateDeptPurchaseRequest`,
    body,
  );
}

export async function submitDeptPurchaseRequestApprovalApi(
  body: SubmitDeptPurchaseRequestApprovalRequest,
) {
  return requestClient.post<ApiResponse<SubmitDeptPurchaseRequestApprovalResponse>>(
    `${BASE}/submitDeptPurchaseRequestApproval`,
    body,
  );
}

export async function withdrawDeptPurchaseRequestApprovalApi(
  body: WithdrawDeptPurchaseRequestApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawDeptPurchaseRequestApprovalResponse>>(
    `${BASE}/withdrawDeptPurchaseRequestApproval`,
    body,
  );
}

export async function agreeDeptPurchaseRequestApi(body: AgreeDeptPurchaseRequestRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreeDeptPurchaseRequest`,
    body,
  );
}

export async function rejectDeptPurchaseRequestApi(body: RejectDeptPurchaseRequestRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectDeptPurchaseRequest`,
    body,
  );
}

export async function removeDeptPurchaseRequestApi(body: RemoveDeptPurchaseRequestRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeDeptPurchaseRequest`,
    body,
  );
}

export async function pageProductsForDeptPurchaseRequestApi(
  body: PageProductsForDeptPurchaseRequestRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForDeptPurchaseRequestResponse>>(
    `${BASE}/pageProductsForDeptPurchaseRequest`,
    body,
  );
}

/** init 科室药房 → 侧栏下拉行（含「全部科室药房」首项 value=0） */
export function mapDeptPurchaseRequestInitToCabinetRows(
  opts?: DeptPurchaseRequestOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(opts ?? [], INV_ALL_DEPT_PHARMACIES_LABEL);
}

/** init OptionDto → ApiOptionItem（项目类型等） */
export function mapDeptPurchaseRequestInitToApiOptions(
  opts?: DeptPurchaseRequestOptionDto[],
): ApiOptionItem[] {
  return (opts ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
    sort:
      o.sort !== undefined && o.sort !== '' && Number.isFinite(Number(o.sort))
        ? Number(o.sort)
        : undefined,
  }));
}

/** 排除「全部」占位后的真实科室药房选项（单头用） */
export function filterDeptPurchaseRequestPharmacyOptions(
  opts?: DeptPurchaseRequestOptionDto[],
): DeptPurchaseRequestOptionDto[] {
  return (opts ?? []).filter(
    (o) => String(o.value ?? '') !== DEP_CPR_ALL_DEPARTMENTS_PURPOSE_LOCATION_ID,
  );
}
