/**
 * 药库调拨入库（药房入库）— 对接 Whale {@code POST /api/app/inventoryWarehouseTransferIn/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/inventoryWarehouseTransferIn';

export const WHS_TRANSFER_IN_TYPE_ENUM = 'CABINET_TRANSFER_IN';
export const WHS_TRANSFER_IN_CATEGORY_ENUM = 'STOCK_SUPPLY';

export interface TransferInOptionDto {
  sort?: string;
  value?: string;
  label?: string;
}

export interface LoadTransferInFormInitRequest {
  organizationId?: number;
}

export interface LoadTransferInFormInitResponse {
  pharmacyLocationOptions?: TransferInOptionDto[];
  warehouseLocationOptions?: TransferInOptionDto[];
  inboundItemTableTypeOptions?: TransferInOptionDto[];
  statusOptions?: TransferInOptionDto[];
  pharmacistOptions?: TransferInOptionDto[];
  warehouseKeeperOptions?: TransferInOptionDto[];
}

export interface TransferInListRowDto {
  busNo?: string;
  originalBusNo?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  applyTime?: string;
  approvalTime?: string;
}

export interface PageTransferInsRequest {
  purposeLocationId: number | string;
  sourceLocationId?: number | string;
  statusEnum?: string;
  keyword?: string;
  applyTimeFrom?: string;
  applyTimeTo?: string;
  pageNo: number;
  pageSize: number;
  typeEnum?: string;
}

export interface PageTransferInsResponse {
  total?: number;
  records: TransferInListRowDto[];
}

export interface GetTransferInDetailRequest {
  busNo: string;
}

export interface TransferInDetailLineDto {
  id?: number | string;
  itemTable?: string;
  itemId?: number | string;
  productName?: string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  remake?: string;
  occurrenceTime?: string;
}

export interface GetTransferInDetailResponse {
  busNo?: string;
  originalBusNo?: string;
  statusEnum?: string | number;
  sourceLocationId?: number | string;
  purposeLocationId?: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum?: string;
  practitionerId?: number | string;
  lines: TransferInDetailLineDto[];
}

export interface TransferInLineDto {
  itemTable?: string;
  itemId?: number | string;
  inventoryItemId?: number | string;
  itemQuantity?: number;
  unitCode?: string;
  lotNumber?: string;
  traceNo?: string;
  price?: number;
  totalPrice?: number;
  remake?: string;
  occurrenceTime?: string;
}

export interface CreateTransferInRequest {
  purposeLocationId: number | string;
  sourceLocationId: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum: string;
  originalBusNo?: string;
  practitionerId: number | string;
  lines: TransferInLineDto[];
}

export interface UpdateTransferInRequest {
  busNo: string;
  purposeLocationId: number | string;
  sourceLocationId: number | string;
  sourceTypeEnum?: string;
  purposeTypeEnum?: string;
  categoryEnum: string;
  originalBusNo?: string;
  practitionerId: number | string;
  lines: TransferInLineDto[];
}

export interface CreateTransferInResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface UpdateTransferInResponse {
  busNo?: string;
  statusEnum?: string | number;
}

export interface SubmitTransferInApprovalRequest {
  busNo: string;
}

export interface SubmitTransferInApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface WithdrawTransferInApprovalRequest {
  busNo: string;
}

export interface WithdrawTransferInApprovalResponse {
  success?: boolean;
  statusEnum?: string | number;
}

export interface AgreeTransferInRequest {
  busNo: string;
  contractBusNo?: string;
}

export interface RejectTransferInRequest {
  busNo: string;
  rejectReason?: string;
}

export interface RemoveTransferInRequest {
  busNo: string;
}

export interface PageProductsForTransferInRequest {
  productTableName?: string;
  keyword?: string;
  sourceLocationId?: number | string;
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
  inventoryItemId?: number | string;
}

export interface PageProductsForTransferInResponse {
  total?: number;
  records: ProductDefinitionItemDto[];
}

export async function loadTransferInFormInitApi(body?: LoadTransferInFormInitRequest) {
  return requestClient.post<ApiResponse<LoadTransferInFormInitResponse>>(
    `${BASE}/loadTransferInFormInit`,
    body ?? {},
  );
}

export async function pageTransferInsApi(body: PageTransferInsRequest) {
  return requestClient.post<ApiResponse<PageTransferInsResponse>>(`${BASE}/pageTransferIns`, body);
}

export async function getTransferInDetailApi(body: GetTransferInDetailRequest) {
  return requestClient.post<ApiResponse<GetTransferInDetailResponse>>(
    `${BASE}/getTransferInDetail`,
    body,
  );
}

export async function createTransferInApi(body: CreateTransferInRequest) {
  return requestClient.post<ApiResponse<CreateTransferInResponse>>(
    `${BASE}/createTransferIn`,
    body,
  );
}

export async function updateTransferInApi(body: UpdateTransferInRequest) {
  return requestClient.post<ApiResponse<UpdateTransferInResponse>>(
    `${BASE}/updateTransferIn`,
    body,
  );
}

export async function submitTransferInApprovalApi(body: SubmitTransferInApprovalRequest) {
  return requestClient.post<ApiResponse<SubmitTransferInApprovalResponse>>(
    `${BASE}/submitTransferInApproval`,
    body,
  );
}

export async function withdrawTransferInApprovalApi(body: WithdrawTransferInApprovalRequest) {
  return requestClient.post<ApiResponse<WithdrawTransferInApprovalResponse>>(
    `${BASE}/withdrawTransferInApproval`,
    body,
  );
}

export async function agreeTransferInApi(body: AgreeTransferInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/agreeTransferIn`, body);
}

export async function rejectTransferInApi(body: RejectTransferInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/rejectTransferIn`, body);
}

export async function removeTransferInApi(body: RemoveTransferInRequest) {
  return requestClient.post<ApiResponse<{ success?: boolean }>>(`${BASE}/removeTransferIn`, body);
}

export async function pageProductsForTransferInApi(body: PageProductsForTransferInRequest) {
  return requestClient.post<ApiResponse<PageProductsForTransferInResponse>>(
    `${BASE}/pageProductsForTransferIn`,
    body,
  );
}
