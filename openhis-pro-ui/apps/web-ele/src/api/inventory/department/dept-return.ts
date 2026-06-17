/**
 * 科室退库单 — 对接 {@code POST /api/app/inventoryDeptReturn/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryDeptReturn';

export interface LocationOptionDto {
  locationId?: number;
  displayName?: string;
  active?: boolean;
}

export interface LoadDeptReturnFormInitRequest {
  organizationId?: number;
}

export interface LoadDeptReturnFormInitResponse {
  sourceDeptLocationOptions: LocationOptionDto[];
  purposeWarehouseOptions: LocationOptionDto[];
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
  itemId?: number;
  itemTable?: string;
  itemQuantity?: number;
  unitCode?: string;
  applyTime?: string;
  approvalTime?: string;
}

export interface PageDeptReturnsRequest {
  sourceLocationId: number;
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
  records: DeptReturnListRowDto[];
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
  lotNumber?: string;
  traceNo?: string;
  occurrenceTime?: string;
  reason?: string;
  remake?: string;
  productName?: string;
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
  lines: DeptReturnDetailLineDto[];
}

export interface DeptReturnLineDto {
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

export interface CreateDeptReturnRequest {
  originalBusNo: string;
  sourceLocationId: number;
  purposeLocationId: number;
  sourceTypeEnum?: number;
  purposeTypeEnum?: number;
  categoryEnum?: number;
  supplierId?: number;
  lines: DeptReturnLineDto[];
}

export interface UpdateDeptReturnRequest {
  busNo: string;
  sourceLocationId: number;
  purposeLocationId: number;
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
  approverId?: string;
  approvalClock?: string;
}

export interface RejectDeptReturnRequest {
  busNo: string;
  approverId?: string;
  approvalClock?: string;
  rejectReason: string;
}

export interface RemoveDeptReturnRequest {
  busNo: string;
}

export interface PageProductsForDeptReturnRequest {
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

export interface PageProductsForDeptReturnResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export interface PageLinkableReturnSourceDocumentsRequest {
  sourceLocationId: number;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export async function loadDeptReturnFormInitApi(body?: LoadDeptReturnFormInitRequest) {
  return requestClient.post<ApiResponse<LoadDeptReturnFormInitResponse>>(
    `${BASE}/loadDeptReturnFormInit`,
    body ?? {},
  );
}

export async function pageDeptReturnsApi(body: PageDeptReturnsRequest) {
  return requestClient.post<ApiResponse<PageDeptReturnsResponse>>(`${BASE}/pageDeptReturns`, body);
}

export async function getDeptReturnDetailApi(body: GetDeptReturnDetailRequest) {
  return requestClient.post<ApiResponse<GetDeptReturnDetailResponse>>(
    `${BASE}/getDeptReturnDetail`,
    body,
  );
}

export async function createDeptReturnApi(body: CreateDeptReturnRequest) {
  return requestClient.post<ApiResponse<CreateDeptReturnResponse>>(
    `${BASE}/createDeptReturn`,
    body,
  );
}

export async function updateDeptReturnApi(body: UpdateDeptReturnRequest) {
  return requestClient.post<ApiResponse<UpdateDeptReturnResponse>>(
    `${BASE}/updateDeptReturn`,
    body,
  );
}

export async function submitDeptReturnApprovalApi(body: SubmitDeptReturnApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitDeptReturnApprovalResponse>>(
    `${BASE}/submitDeptReturnApproval`,
    body,
  );
}

export async function withdrawDeptReturnApprovalApi(body: WithdrawDeptReturnApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawDeptReturnApprovalResponse>>(
    `${BASE}/withdrawDeptReturnApproval`,
    body,
  );
}

export async function agreeDeptReturnApi(body: AgreeDeptReturnRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeDeptReturn`, body);
}

export async function rejectDeptReturnApi(body: RejectDeptReturnRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectDeptReturn`, body);
}

export async function removeDeptReturnApi(body: RemoveDeptReturnRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeDeptReturn`, body);
}

export async function pageProductsForDeptReturnApi(body: PageProductsForDeptReturnRequest) {
  return requestClient.post<ApiResponse<PageProductsForDeptReturnResponse>>(
    `${BASE}/pageProductsForDeptReturn`,
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

/** typeEnum=22；SupplyRequestType DEPARTMENT=2；CABINET=11；SupplyCategory STOCK=1 */
export const DEP_RT_TYPE_ENUM = 22;
export const DEP_RT_SOURCE_TYPE_DEPARTMENT = 2;
export const DEP_RT_PURPOSE_TYPE_CABINET = 11;
export const DEP_RT_CATEGORY_STOCK = 1;
