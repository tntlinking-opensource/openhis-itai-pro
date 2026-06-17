/**
 * 药库损益单 — 对接 Whale {@code POST /api/app/inventoryWarehouseGainLoss/<method>}
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryWarehouseGainLoss';

export const WHS_GAIN_LOSS_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface GainLossOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadGainLossFormInitRequest {
  organizationId?: number;
}

export interface LoadGainLossFormInitResponse {
  warehouseLocationOptions?: GainLossOptionDto[];
  inboundItemTableTypeOptions?: GainLossOptionDto[];
  statusOptions?: GainLossOptionDto[];
  pharmacistOptions?: GainLossOptionDto[];
  warehouseKeeperOptions?: GainLossOptionDto[];
}

export interface GainLossListRowDto {
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

export interface PageGainLosssRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PageGainLosssResponse {
  total?: number;
  records: GainLossListRowDto[];
}

export interface GetGainLossDetailRequest {
  busNo: string;
}

export interface GainLossDetailLineDto {
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

export interface GetGainLossDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
  lines: GainLossDetailLineDto[];
}

export interface GainLossLineDto {
  itemTable?: string;
  itemId?: number | string;
  gainLossQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  reason?: string;
  price?: number;
  occurrenceTime?: string;
}

export interface CreateGainLossRequest {
  purposeLocationId: number | string;
  practitionerId: number | string;
  categoryEnum?: string;
  lines: GainLossLineDto[];
}

export interface CreateGainLossResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitGainLossApprovalRequest {
  busNo: string;
}

export interface SubmitGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawGainLossApprovalRequest {
  busNo: string;
}

export interface WithdrawGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeGainLossRequest {
  busNo: string;
  contractBusNo?: string;
}

export interface RejectGainLossRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveGainLossRequest {
  busNo: string;
}

export interface PageProductsForGainLossRequest {
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

export interface PageProductsForGainLossResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export async function loadGainLossFormInitApi(body?: LoadGainLossFormInitRequest) {
  return requestClient.post<ApiResponse<LoadGainLossFormInitResponse>>(
    `${BASE}/loadGainLossFormInit`,
    body ?? {},
  );
}

export async function pageGainLosssApi(body: PageGainLosssRequest) {
  return requestClient.post<ApiResponse<PageGainLosssResponse>>(`${BASE}/pageGainLosss`, body);
}

export async function getGainLossDetailApi(body: GetGainLossDetailRequest) {
  return requestClient.post<ApiResponse<GetGainLossDetailResponse>>(
    `${BASE}/getGainLossDetail`,
    body,
  );
}

export async function createGainLossApi(body: CreateGainLossRequest) {
  return requestClient.post<ApiResponse<CreateGainLossResponse>>(`${BASE}/createGainLoss`, body);
}

export async function submitGainLossApprovalApi(body: SubmitGainLossApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitGainLossApprovalResponse>>(
    `${BASE}/submitGainLossApproval`,
    body,
  );
}

export async function withdrawGainLossApprovalApi(body: WithdrawGainLossApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawGainLossApprovalResponse>>(
    `${BASE}/withdrawGainLossApproval`,
    body,
  );
}

export async function agreeGainLossApi(body: AgreeGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeGainLoss`, body);
}

export async function rejectGainLossApi(body: RejectGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectGainLoss`, body);
}

export async function removeGainLossApi(body: RemoveGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeGainLoss`, body);
}

export async function pageProductsForGainLossApi(body: PageProductsForGainLossRequest) {
  return requestClient.post<ApiResponse<PageProductsForGainLossResponse>>(
    `${BASE}/pageProductsForGainLoss`,
    body,
  );
}

export function mapGainLossInitOptionsToApiItems(list?: GainLossOptionDto[]): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}
