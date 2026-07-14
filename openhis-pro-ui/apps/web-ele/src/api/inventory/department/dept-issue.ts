/**
 * 科室领用单 — 对接 Whale {@code POST /api/app/inventoryDepartmentDeptIssue/<method>}
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

const BASE = '/inventoryDepartmentDeptIssue';

/** {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_ISSUE} */
export const DEPT_ISSUE_TYPE_ENUM = 'DEPT_ISSUE';

export interface DeptPharmacyOptionDto {
  value?: string;
  label?: string;
  organizationId?: string;
  organizationName?: string;
}

export interface DeptIssueOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadDeptIssueFormInitRequest {
  organizationId?: number;
}

export interface LoadDeptIssueFormInitResponse {
  departmentPharmacyOptions?: DeptPharmacyOptionDto[];
  productTableOptions?: DeptIssueOptionDto[];
  statusOptions?: DeptIssueOptionDto[];
  pharmacistOptions?: DeptIssueOptionDto[];
}

export interface DeptIssueListRowDto {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  consumingOrganizationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applicantName?: string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
}

export interface PageDeptIssuesRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageDeptIssuesResponse {
  total?: number;
  totalCount?: number;
  records?: DeptIssueListRowDto[];
  content?: DeptIssueListRowDto[];
}

export interface GetDeptIssueDetailRequest {
  busNo: string;
}

export interface DeptIssueDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  traceNo?: string;
  itemQuantity?: number;
  unitCode?: string;
  price?: number;
  totalPrice?: number;
}

export interface GetDeptIssueDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  consumingOrganizationId?: string;
  consumingOrganizationName?: string;
  practitionerId?: number | string;
  reason?: string;
  remake?: string;
  applicantId?: number | string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
  lines: DeptIssueDetailLineDto[];
}

export interface DeptIssueLineDto {
  itemTable?: string;
  itemId?: number | string;
  lotNumber?: string;
  traceNo?: string;
  itemQuantity?: number;
  unitCode?: string;
  price?: number;
}

export interface CreateDeptIssueRequest {
  purposeLocationId: number | string;
  practitionerId?: number | string;
  reason?: string;
  remake?: string;
  lines: DeptIssueLineDto[];
}

export interface UpdateDeptIssueRequest {
  busNo: string;
  purposeLocationId: number | string;
  practitionerId?: number | string;
  reason?: string;
  remake?: string;
  lines: DeptIssueLineDto[];
}

export interface CreateDeptIssueResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateDeptIssueResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitDeptIssueApprovalRequest {
  busNo: string;
}

export interface SubmitDeptIssueApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawDeptIssueApprovalRequest {
  busNo: string;
}

export interface WithdrawDeptIssueApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeDeptIssueRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectDeptIssueRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason?: string;
}

export interface RemoveDeptIssueRequest {
  busNo: string;
}

export interface PageProductsForDeptIssueRequest {
  productTableName?: string;
  keyword?: string;
  purposeLocationId?: number | string;
  pageNo: number;
  pageSize: number;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number | string;
  productTableName?: string;
  productName?: string;
  merchandiseName?: string;
  unitCode?: string;
  minUnitCode?: string;
  lotNumber?: string;
  price?: number;
  availableQuantity?: number | string;
  inventoryItemId?: number | string;
}

export interface PageProductsForDeptIssueResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export async function loadDeptIssueFormInitApi(body?: LoadDeptIssueFormInitRequest) {
  return requestClient.post<ApiResponse<LoadDeptIssueFormInitResponse>>(
    `${BASE}/loadDeptIssueFormInit`,
    body ?? {},
  );
}

export async function pageDeptIssuesApi(body: PageDeptIssuesRequest) {
  return requestClient.post<ApiResponse<PageDeptIssuesResponse>>(`${BASE}/pageDeptIssues`, body);
}

export async function getDeptIssueDetailApi(body: GetDeptIssueDetailRequest) {
  return requestClient.post<ApiResponse<GetDeptIssueDetailResponse>>(
    `${BASE}/getDeptIssueDetail`,
    body,
  );
}

export async function createDeptIssueApi(body: CreateDeptIssueRequest) {
  return requestClient.post<ApiResponse<CreateDeptIssueResponse>>(`${BASE}/createDeptIssue`, body);
}

export async function updateDeptIssueApi(body: UpdateDeptIssueRequest) {
  return requestClient.post<ApiResponse<UpdateDeptIssueResponse>>(`${BASE}/updateDeptIssue`, body);
}

export async function submitDeptIssueApprovalApi(body: SubmitDeptIssueApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitDeptIssueApprovalResponse>>(
    `${BASE}/submitDeptIssueApproval`,
    body,
  );
}

export async function withdrawDeptIssueApprovalApi(body: WithdrawDeptIssueApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawDeptIssueApprovalResponse>>(
    `${BASE}/withdrawDeptIssueApproval`,
    body,
  );
}

export async function agreeDeptIssueApi(body: AgreeDeptIssueRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeDeptIssue`, body);
}

export async function rejectDeptIssueApi(body: RejectDeptIssueRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectDeptIssue`, body);
}

export async function removeDeptIssueApi(body: RemoveDeptIssueRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeDeptIssue`, body);
}

export async function pageProductsForDeptIssueApi(body: PageProductsForDeptIssueRequest) {
  return requestClient.post<ApiResponse<PageProductsForDeptIssueResponse>>(
    `${BASE}/pageProductsForDeptIssue`,
    body,
  );
}

export function mapDeptIssueInitOptionsToApiItems(list?: DeptIssueOptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
    sort:
      o.sort !== undefined && o.sort !== '' && Number.isFinite(Number(o.sort))
        ? Number(o.sort)
        : undefined,
  }));
}

/** init 科室药房下拉 → 侧栏 cabinet 行（含「全部科室药房」）。 */
export function mapDeptIssueInitToCabinetRows(
  list?: DeptPharmacyOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(
    (list ?? []).map((o) => ({ value: o.value, label: o.label })),
    INV_ALL_DEPT_PHARMACIES_LABEL,
  );
}

/** 工作站左侧列表：purposeLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageDeptIssueWorkbenchListApi(params: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: DeptIssueListRowDto[]; total: number }> {
  const res = await pageDeptIssuesApi({
    purposeLocationId: resolveWorkbenchListPurposeLocationId(params.purposeLocationId),
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<DeptIssueListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params.applyTimeBegin,
    params.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params.keyword,
  ) as DeptIssueListRowDto[];
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
