/**
 * 科室损益单 — 对接 Whale {@code POST /api/app/inventoryDeptGainLoss/<method>}
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryDeptGainLoss';

export const DEP_GAIN_LOSS_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface DeptGainLossOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadDeptGainLossFormInitRequest {
  organizationId?: number;
}

export interface LoadDeptGainLossFormInitResponse {
  deptLocationOptions?: DeptGainLossOptionDto[];
  inboundItemTableTypeOptions?: DeptGainLossOptionDto[];
  statusOptions?: DeptGainLossOptionDto[];
  nurseOptions?: DeptGainLossOptionDto[];
  warehouseKeeperOptions?: DeptGainLossOptionDto[];
}

export interface DeptGainLossListRowDto {
  busNo?: string;
  statusEnum?: string | number;
  statusEnumText?: string;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  itemCount?: number;
  gainLossQuantitySum?: number;
  applicantId?: number | string;
  applicantName?: string;
  applyTime?: string;
  approverId?: number | string;
  approverName?: string;
  approvalTime?: string;
}

export interface PageDeptGainLosssRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageDeptGainLosssResponse {
  total?: number;
  records: DeptGainLossListRowDto[];
}

export interface GetDeptGainLossDetailRequest {
  busNo: string;
}

export interface DeptGainLossDetailLineDto {
  lineId?: number | string;
  itemTable?: string;
  itemId?: number | string;
  productName?: string;
  specification?: string;
  manufacturer?: string;
  lotNumber?: string;
  unitCode?: string;
  bookQuantity?: number;
  gainLossQuantity?: number;
  actualQuantity?: number;
  actualAmount?: number;
  gainLossAmount?: number;
  price?: number;
  reason?: string;
  remake?: string;
  occurrenceTime?: string;
}

export interface GetDeptGainLossDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
  lines: DeptGainLossDetailLineDto[];
}

export interface DeptGainLossLineDto {
  itemTable?: string;
  itemId?: number | string;
  gainLossQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  reason?: string;
  price?: number;
  occurrenceTime?: string;
}

export interface CreateDeptGainLossRequest {
  purposeLocationId: number | string;
  practitionerId: number | string;
  categoryEnum?: string;
  lines: DeptGainLossLineDto[];
}

export interface CreateDeptGainLossResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitDeptGainLossApprovalRequest {
  busNo: string;
}

export interface SubmitDeptGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawDeptGainLossApprovalRequest {
  busNo: string;
}

export interface WithdrawDeptGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeDeptGainLossRequest {
  busNo: string;
  contractBusNo?: string;
}

export interface RejectDeptGainLossRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveDeptGainLossRequest {
  busNo: string;
}

export interface PageProductsForDeptGainLossRequest {
  productTableName?: string;
  keyword?: string;
  purposeLocationId?: number | string;
  pageNo: number;
  pageSize: number;
}

export interface ProductDefinitionItemDto {
  productDefinitionId?: number;
  productTableName?: string;
  productName?: string;
  merchandiseName?: string;
  unitCode?: string;
  minUnitCode?: string;
  lotNumber?: string;
  price?: number;
}

export interface PageProductsForDeptGainLossResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export async function loadDeptGainLossFormInitApi(body?: LoadDeptGainLossFormInitRequest) {
  return requestClient.post<ApiResponse<LoadDeptGainLossFormInitResponse>>(
    `${BASE}/loadDeptGainLossFormInit`,
    body ?? {},
  );
}

export async function pageDeptGainLosssApi(body: PageDeptGainLosssRequest) {
  return requestClient.post<ApiResponse<PageDeptGainLosssResponse>>(
    `${BASE}/pageDeptGainLosss`,
    body,
  );
}

export async function getDeptGainLossDetailApi(body: GetDeptGainLossDetailRequest) {
  return requestClient.post<ApiResponse<GetDeptGainLossDetailResponse>>(
    `${BASE}/getDeptGainLossDetail`,
    body,
  );
}

export async function createDeptGainLossApi(body: CreateDeptGainLossRequest) {
  return requestClient.post<ApiResponse<CreateDeptGainLossResponse>>(
    `${BASE}/createDeptGainLoss`,
    body,
  );
}

export async function submitDeptGainLossApprovalApi(body: SubmitDeptGainLossApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitDeptGainLossApprovalResponse>>(
    `${BASE}/submitDeptGainLossApproval`,
    body,
  );
}

export async function withdrawDeptGainLossApprovalApi(body: WithdrawDeptGainLossApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawDeptGainLossApprovalResponse>>(
    `${BASE}/withdrawDeptGainLossApproval`,
    body,
  );
}

export async function agreeDeptGainLossApi(body: AgreeDeptGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeDeptGainLoss`, body);
}

export async function rejectDeptGainLossApi(body: RejectDeptGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectDeptGainLoss`, body);
}

export async function removeDeptGainLossApi(body: RemoveDeptGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeDeptGainLoss`, body);
}

export async function pageProductsForDeptGainLossApi(body: PageProductsForDeptGainLossRequest) {
  return requestClient.post<ApiResponse<PageProductsForDeptGainLossResponse>>(
    `${BASE}/pageProductsForDeptGainLoss`,
    body,
  );
}

export function mapDeptGainLossInitOptionsToApiItems(
  list?: DeptGainLossOptionDto[],
): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}
