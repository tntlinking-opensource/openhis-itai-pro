/**
 * 科室退领单 — 对接 Whale {@code POST /api/app/inventoryDepartmentDeptIssueReturn/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  filterWorkbenchListRowsByDateRange,
  filterWorkbenchListRowsByKeyword,
  INV_ALL_DEPT_PHARMACIES_LABEL,
  mapInitOptionsToCabinetRowsWithAll,
  resolveWorkbenchListPurposeLocationId,
} from '#/utils/inventory/inventory-workbench-list';

const BASE = '/inventoryDepartmentDeptIssueReturn';

/** {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_ISSUE_RETURN} */
export const DEPT_ISSUE_RETURN_TYPE_ENUM = 'DEPT_ISSUE_RETURN';

export interface DeptIssueReturnOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadDeptIssueReturnFormInitRequest {
  organizationId?: number;
}

export interface LoadDeptIssueReturnFormInitResponse {
  departmentPharmacies?: DeptIssueReturnOptionDto[];
  supplyStatuses?: DeptIssueReturnOptionDto[];
  pharmacists?: DeptIssueReturnOptionDto[];
}

export interface DeptIssueReturnListRowDto {
  busNo?: string;
  originalBusNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  consumingOrganizationName?: string;
  applicantId?: number | string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
}

export interface PageDeptIssueReturnsRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageDeptIssueReturnsResponse {
  total?: number;
  totalCount?: number;
  records?: DeptIssueReturnListRowDto[];
  content?: DeptIssueReturnListRowDto[];
}

export interface GetDeptIssueReturnDetailRequest {
  busNo: string;
}

export interface DeptIssueReturnDetailLineDto {
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

export interface GetDeptIssueReturnDetailResponse {
  busNo?: string;
  originalBusNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  consumingOrganizationName?: string;
  applicantId?: number | string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
  lines: DeptIssueReturnDetailLineDto[];
}

export interface DeptIssueReturnLineDto {
  itemTable?: string;
  itemId?: number | string;
  lotNumber?: string;
  traceNo?: string;
  itemQuantity?: number;
  unitCode?: string;
  price?: number;
}

export interface CreateDeptIssueReturnRequest {
  originalBusNo: string;
  purposeLocationId: number | string;
  lines: DeptIssueReturnLineDto[];
}

export interface UpdateDeptIssueReturnRequest {
  busNo: string;
  purposeLocationId: number | string;
  lines: DeptIssueReturnLineDto[];
}

export interface CreateDeptIssueReturnResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateDeptIssueReturnResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitDeptIssueReturnApprovalRequest {
  busNo: string;
}

export interface SubmitDeptIssueReturnApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawDeptIssueReturnApprovalRequest {
  busNo: string;
}

export interface WithdrawDeptIssueReturnApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeDeptIssueReturnRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
}

export interface RejectDeptIssueReturnRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason?: string;
}

export interface RemoveDeptIssueReturnRequest {
  busNo: string;
}

export interface PageLinkableIssueDocumentsRequest {
  purposeLocationId: number | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface LinkableIssueListRowDto {
  busNo?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  consumingOrganizationName?: string;
  creatorName?: string;
  applicantId?: number | string;
  totalAmount?: number;
  approvalTime?: string;
  returnable?: boolean;
}

export interface PageLinkableIssueDocumentsResponse {
  total?: number;
  totalCount?: number;
  records?: LinkableIssueListRowDto[];
  content?: LinkableIssueListRowDto[];
}

export interface GetLinkableIssueDetailRequest {
  originalBusNo: string;
  purposeLocationId: number | string;
  excludeReturnBusNo?: string;
}

export interface LinkableIssueReturnLineDto {
  itemTable?: string;
  itemId?: number | string;
  lotNumber?: string;
  traceNo?: string;
  issueQuantity?: number;
  returnableQuantity?: number;
  unitCode?: string;
  unitCodeDictText?: string;
  productName?: string;
  specification?: string;
  price?: number;
}

export interface GetLinkableIssueDetailResponse {
  originalBusNo?: string;
  purposeLocationId?: string;
  purposeLocationName?: string;
  consumingOrganizationName?: string;
  lines: LinkableIssueReturnLineDto[];
}

export async function loadDeptIssueReturnFormInitApi(body?: LoadDeptIssueReturnFormInitRequest) {
  return requestClient.post<ApiResponse<LoadDeptIssueReturnFormInitResponse>>(
    `${BASE}/loadDeptIssueReturnFormInit`,
    body ?? {},
  );
}

export async function pageDeptIssueReturnsApi(body: PageDeptIssueReturnsRequest) {
  return requestClient.post<ApiResponse<PageDeptIssueReturnsResponse>>(
    `${BASE}/pageDeptIssueReturns`,
    body,
  );
}

export async function getDeptIssueReturnDetailApi(body: GetDeptIssueReturnDetailRequest) {
  return requestClient.post<ApiResponse<GetDeptIssueReturnDetailResponse>>(
    `${BASE}/getDeptIssueReturnDetail`,
    body,
  );
}

export async function createDeptIssueReturnApi(body: CreateDeptIssueReturnRequest) {
  return requestClient.post<ApiResponse<CreateDeptIssueReturnResponse>>(
    `${BASE}/createDeptIssueReturn`,
    body,
  );
}

export async function updateDeptIssueReturnApi(body: UpdateDeptIssueReturnRequest) {
  return requestClient.post<ApiResponse<UpdateDeptIssueReturnResponse>>(
    `${BASE}/updateDeptIssueReturn`,
    body,
  );
}

export async function submitDeptIssueReturnApprovalApi(body: SubmitDeptIssueReturnApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitDeptIssueReturnApprovalResponse>>(
    `${BASE}/submitDeptIssueReturnApproval`,
    body,
  );
}

export async function withdrawDeptIssueReturnApprovalApi(
  body: WithdrawDeptIssueReturnApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawDeptIssueReturnApprovalResponse>>(
    `${BASE}/withdrawDeptIssueReturnApproval`,
    body,
  );
}

export async function agreeDeptIssueReturnApi(body: AgreeDeptIssueReturnRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreeDeptIssueReturn`,
    body,
  );
}

export async function rejectDeptIssueReturnApi(body: RejectDeptIssueReturnRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectDeptIssueReturn`,
    body,
  );
}

export async function removeDeptIssueReturnApi(body: RemoveDeptIssueReturnRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removeDeptIssueReturn`,
    body,
  );
}

export async function pageLinkableIssueDocumentsApi(body: PageLinkableIssueDocumentsRequest) {
  return requestClient.post<ApiResponse<PageLinkableIssueDocumentsResponse>>(
    `${BASE}/pageLinkableIssueDocuments`,
    body,
  );
}

export async function getLinkableIssueDetailApi(body: GetLinkableIssueDetailRequest) {
  return requestClient.post<ApiResponse<GetLinkableIssueDetailResponse>>(
    `${BASE}/getLinkableIssueDetail`,
    body,
  );
}

/** init 科室药房下拉 → 侧栏 cabinet 行（含「全部科室药房」）。 */
export function mapDeptIssueReturnInitToCabinetRows(
  list?: DeptIssueReturnOptionDto[],
): Record<string, unknown>[] {
  return mapInitOptionsToCabinetRowsWithAll(list ?? [], INV_ALL_DEPT_PHARMACIES_LABEL);
}

/** 工作站左侧列表：purposeLocationId=0 表示全部；日期/关键字在前端筛。 */
export async function pageDeptIssueReturnWorkbenchListApi(params: {
  purposeLocationId?: number | string;
  keyword?: string;
  applyTimeBegin?: string;
  applyTimeEnd?: string;
  pageNo?: number;
  pageSize?: number;
}): Promise<{ records: DeptIssueReturnListRowDto[]; total: number }> {
  const res = await pageDeptIssueReturnsApi({
    purposeLocationId: resolveWorkbenchListPurposeLocationId(params.purposeLocationId),
    keyword: params.keyword?.trim() || undefined,
    pageNo: params.pageNo ?? 1,
    pageSize: params.pageSize ?? 200,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<DeptIssueReturnListRowDto>(res.data);
  const dateFiltered = filterWorkbenchListRowsByDateRange(
    content,
    params.applyTimeBegin,
    params.applyTimeEnd,
  );
  const records = filterWorkbenchListRowsByKeyword(
    dateFiltered as Record<string, unknown>[],
    params.keyword,
  ) as DeptIssueReturnListRowDto[];
  const filtered = (params.applyTimeBegin && params.applyTimeEnd) || params.keyword?.trim();
  return { records, total: filtered ? records.length : totalCount };
}
