/**
 * 药房损益单 — 对接 Whale {@code POST /api/app/inventoryPharmacyGainLoss/<method>}
 */
import type { ApiOptionItem, ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryPharmacyGainLoss';

export const PHA_GAIN_LOSS_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface PharmacyGainLossOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadPharmacyGainLossFormInitRequest {
  organizationId?: number;
}

export interface LoadPharmacyGainLossFormInitResponse {
  pharmacyLocationOptions?: PharmacyGainLossOptionDto[];
  inboundItemTableTypeOptions?: PharmacyGainLossOptionDto[];
  statusOptions?: PharmacyGainLossOptionDto[];
  pharmacistOptions?: PharmacyGainLossOptionDto[];
  warehouseKeeperOptions?: PharmacyGainLossOptionDto[];
}

export interface PharmacyGainLossListRowDto {
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

export interface PagePharmacyGainLosssRequest {
  purposeLocationId: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
}

export interface PagePharmacyGainLosssResponse {
  total?: number;
  records: PharmacyGainLossListRowDto[];
}

export interface GetPharmacyGainLossDetailRequest {
  busNo: string;
}

export interface PharmacyGainLossDetailLineDto {
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

export interface GetPharmacyGainLossDetailResponse {
  busNo?: string;
  statusEnum?: string | number;
  purposeLocationId?: number | string;
  purposeLocationName?: string;
  practitionerId?: number | string;
  applicantId?: number | string;
  applyTime?: string;
  approverId?: number | string;
  approvalTime?: string;
  lines: PharmacyGainLossDetailLineDto[];
}

export interface PharmacyGainLossLineDto {
  itemTable?: string;
  itemId?: number | string;
  gainLossQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  reason?: string;
  price?: number;
  occurrenceTime?: string;
}

export interface CreatePharmacyGainLossRequest {
  purposeLocationId: number | string;
  practitionerId: number | string;
  categoryEnum?: string;
  lines: PharmacyGainLossLineDto[];
}

export interface CreatePharmacyGainLossResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitPharmacyGainLossApprovalRequest {
  busNo: string;
}

export interface SubmitPharmacyGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawPharmacyGainLossApprovalRequest {
  busNo: string;
}

export interface WithdrawPharmacyGainLossApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreePharmacyGainLossRequest {
  busNo: string;
  contractBusNo?: string;
}

export interface RejectPharmacyGainLossRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemovePharmacyGainLossRequest {
  busNo: string;
}

export interface PageProductsForPharmacyGainLossRequest {
  productTableName?: string;
  keyword?: string;
  purposeLocationId?: number | string;
  pageNo: number;
  pageSize: number;
}

export interface PageProductsForPharmacyGainLossResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
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

export async function loadPharmacyGainLossFormInitApi(body?: LoadPharmacyGainLossFormInitRequest) {
  return requestClient.post<ApiResponse<LoadPharmacyGainLossFormInitResponse>>(
    `${BASE}/loadPharmacyGainLossFormInit`,
    body ?? {},
  );
}

export async function pagePharmacyGainLosssApi(body: PagePharmacyGainLosssRequest) {
  return requestClient.post<ApiResponse<PagePharmacyGainLosssResponse>>(
    `${BASE}/pagePharmacyGainLosss`,
    body,
  );
}

export async function getPharmacyGainLossDetailApi(body: GetPharmacyGainLossDetailRequest) {
  return requestClient.post<ApiResponse<GetPharmacyGainLossDetailResponse>>(
    `${BASE}/getPharmacyGainLossDetail`,
    body,
  );
}

export async function createPharmacyGainLossApi(body: CreatePharmacyGainLossRequest) {
  return requestClient.post<ApiResponse<CreatePharmacyGainLossResponse>>(
    `${BASE}/createPharmacyGainLoss`,
    body,
  );
}

export async function submitPharmacyGainLossApprovalApi(
  body: SubmitPharmacyGainLossApprovalRequest,
) {
  return requestClient.post<ApiResponse<SubmitPharmacyGainLossApprovalResponse>>(
    `${BASE}/submitPharmacyGainLossApproval`,
    body,
  );
}

export async function withdrawPharmacyGainLossApprovalApi(
  body: WithdrawPharmacyGainLossApprovalRequest,
) {
  return requestClient.post<ApiResponse<WithdrawPharmacyGainLossApprovalResponse>>(
    `${BASE}/withdrawPharmacyGainLossApproval`,
    body,
  );
}

export async function agreePharmacyGainLossApi(body: AgreePharmacyGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/agreePharmacyGainLoss`,
    body,
  );
}

export async function rejectPharmacyGainLossApi(body: RejectPharmacyGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/rejectPharmacyGainLoss`,
    body,
  );
}

export async function removePharmacyGainLossApi(body: RemovePharmacyGainLossRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(
    `${BASE}/removePharmacyGainLoss`,
    body,
  );
}

export async function pageProductsForPharmacyGainLossApi(
  body: PageProductsForPharmacyGainLossRequest,
) {
  return requestClient.post<ApiResponse<PageProductsForPharmacyGainLossResponse>>(
    `${BASE}/pageProductsForPharmacyGainLoss`,
    body,
  );
}

export function mapPharmacyGainLossInitOptionsToApiItems(
  list?: PharmacyGainLossOptionDto[],
): ApiOptionItem[] {
  return (list ?? []).map((o) => ({
    label: String(o.label ?? ''),
    value: String(o.value ?? ''),
  }));
}
