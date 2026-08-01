/**
 * 价格调整 — 对接 Whale {@code POST /api/app/priceAdjustment/<methodName>}
 * baseURL 含 /api/app 时使用短路径 /priceAdjustment/...
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';

const BASE = '/priceAdjustment';

export interface OptionDto {
  value?: string;
  label?: string;
}

export interface InitPriceAdjustmentResponse {
  adjustmentReasonOptions?: OptionDto[];
  statusFilterOptions?: OptionDto[];
  itemTableTabOptions?: OptionDto[];
  orderPricingSource?: string;
}

/** 操作科室由服务端从当前登录会话解析，不在请求体上传。 */
export interface PagePriceAdjustmentDocumentRequest {
  statusEnum?: string;
  keyword?: string;
  submitTimeFrom?: string;
  submitTimeTo?: string;
  startDate?: string;
  endDate?: string;
  pageNo: number;
  pageSize: number;
}

export interface PriceAdjustmentDocumentListRowDto {
  busNo?: string;
  statusEnum?: string;
  statusEnumText?: string;
  itemTable?: string;
  adjustmentStyle?: string;
  orgId?: number | string;
  operatorId?: number | string;
  operatorName?: string;
  submitterId?: number | string;
  submitterName?: string;
  approverId?: number | string;
  approverName?: string;
  adjustmentTime?: string;
  submitTime?: string;
  approveTime?: string;
  totalPurchaseImpact?: number;
  totalSellingImpact?: number;
  lineCount?: number;
}

export interface PagePriceAdjustmentDocumentResponse {
  content?: PriceAdjustmentDocumentListRowDto[];
  records?: PriceAdjustmentDocumentListRowDto[];
  totalCount?: number;
  total?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface GetPriceAdjustmentDocumentRequest {
  busNo: string;
}

export interface PriceAdjustmentDetailDto {
  id?: number | string;
  lotNumber?: string;
  quantity?: number;
  oldPurchaseAmount?: number;
  oldSellingAmount?: number;
  purchaseImpact?: number;
  sellingImpact?: number;
}

export interface PriceAdjustmentLineDto {
  id?: number | string;
  busNo?: string;
  itemTable?: string;
  itemTableText?: string;
  itemId?: number | string;
  itemName?: string;
  itemCode?: string;
  specification?: string;
  manufacturer?: string;
  /** 项目种类展示名（详情 enrich） */
  categoryCodeDictText?: string;
  /** 单位展示名（详情 enrich） */
  unitName?: string;
  newPurchaseAmount?: number;
  newSellingAmount?: number;
  adjustmentReason?: string;
  adjustmentReasonText?: string;
  adjustmentTime?: string;
  statusEnum?: string;
  statusEnumText?: string;
  operatorId?: number | string;
  orgId?: number | string;
  adjustmentStyle?: string;
  adjustmentStyleText?: string;
  details?: PriceAdjustmentDetailDto[];
}

export interface PriceAdjustmentDocumentResponse {
  busNo?: string;
  statusEnum?: string;
  statusEnumText?: string;
  submitterId?: number | string;
  submitterName?: string;
  submitTime?: string;
  approverId?: number | string;
  approverName?: string;
  approveTime?: string;
  approveComment?: string;
  totalPurchaseImpact?: number;
  totalSellingImpact?: number;
  lines?: PriceAdjustmentLineDto[];
}

export interface SearchAdjustableItemRequest {
  itemTable: string;
  orgId?: number | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

export interface AdjustableItemRowDto {
  itemTable?: string;
  itemId?: number | string;
  itemCode?: string;
  itemName?: string;
  specification?: string;
  manufacturer?: string;
  categoryCodeDictText?: string;
  unitName?: string;
  adjustmentStyle?: string;
  currentPurchasePrice?: number;
  currentSellingPrice?: number;
}

export interface SearchAdjustableItemsResponse {
  content?: AdjustableItemRowDto[];
  records?: AdjustableItemRowDto[];
  totalCount?: number;
  total?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface LoadLogisticsAdjustmentSnapshotRequest {
  itemTable: string;
  itemIds: (number | string)[];
}

export interface LogisticsSnapshotRowDto {
  itemId?: number | string;
  lotNumber?: string;
  quantity?: number;
  oldPurchaseAmount?: number;
  oldSellingAmount?: number;
}

export interface LoadLogisticsAdjustmentSnapshotResponse {
  rows?: LogisticsSnapshotRowDto[];
}

export interface LoadClinicalOldPriceRequest {
  itemTable: string;
  itemId: number | string;
}

export interface LoadClinicalOldPriceResponse {
  oldSellingAmount?: number;
}

export interface SavePriceAdjustmentDraftDetailLineRequest {
  lotNumber?: string;
  quantity?: number;
  oldPurchaseAmount?: number;
  oldSellingAmount?: number;
}

export interface SavePriceAdjustmentDraftLineRequest {
  itemTable: string;
  itemId: number | string;
  newPurchaseAmount?: number;
  newSellingAmount?: number;
  oldSellingAmount?: number;
  details?: SavePriceAdjustmentDraftDetailLineRequest[];
}

export interface SavePriceAdjustmentDraftRequest {
  busNo?: string;
  orgId?: number | string;
  adjustmentReason: string;
  adjustmentTime?: string;
  lines: SavePriceAdjustmentDraftLineRequest[];
}

export interface SavePriceAdjustmentDraftResponse {
  busNo?: string;
  statusEnum?: string;
}

export interface SubmitPriceAdjustmentRequest {
  busNo: string;
}

export interface SubmitPriceAdjustmentResponse {
  busNo?: string;
  statusEnum?: string;
}

export interface WithdrawPriceAdjustmentDraftRequest {
  busNo: string;
}

export interface WithdrawPriceAdjustmentDraftResponse {
  busNo?: string;
  statusEnum?: string;
}

export interface ApprovePriceAdjustmentRequest {
  busNo: string;
  approveComment?: string;
}

export interface ApprovePriceAdjustmentResponse {
  busNo?: string;
  statusEnum?: string;
}

export interface RejectPriceAdjustmentRequest {
  busNo: string;
  approveComment: string;
}

export interface RejectPriceAdjustmentResponse {
  busNo?: string;
  statusEnum?: string;
}

export interface CheckInTransitForSubmitRequest {
  busNo: string;
}

export interface InTransitCheckDto {
  blocked?: boolean;
  reasonCode?: string;
  reasonMessage?: string;
}

export interface CheckInTransitForSubmitResponse {
  check?: InTransitCheckDto;
}

export async function initPriceAdjustmentApi(body?: Record<string, never>) {
  return requestClient.post<ApiResponse<InitPriceAdjustmentResponse>>(`${BASE}/init`, body ?? {});
}

export async function pagePriceAdjustmentDocumentsApi(body: PagePriceAdjustmentDocumentRequest) {
  return requestClient.post<ApiResponse<PagePriceAdjustmentDocumentResponse>>(
    `${BASE}/pageDocuments`,
    body,
  );
}

export async function getPriceAdjustmentDocumentApi(body: GetPriceAdjustmentDocumentRequest) {
  return requestClient.post<ApiResponse<PriceAdjustmentDocumentResponse>>(
    `${BASE}/getDocument`,
    body,
  );
}

export async function searchAdjustableItemsApi(body: SearchAdjustableItemRequest) {
  return requestClient.post<ApiResponse<SearchAdjustableItemsResponse>>(
    `${BASE}/searchAdjustableItems`,
    body,
  );
}

export async function loadLogisticsAdjustmentSnapshotApi(
  body: LoadLogisticsAdjustmentSnapshotRequest,
) {
  return requestClient.post<ApiResponse<LoadLogisticsAdjustmentSnapshotResponse>>(
    `${BASE}/loadLogisticsSnapshot`,
    body,
  );
}

export async function loadClinicalOldPriceApi(body: LoadClinicalOldPriceRequest) {
  return requestClient.post<ApiResponse<LoadClinicalOldPriceResponse>>(
    `${BASE}/loadClinicalOldPrice`,
    body,
  );
}

export async function savePriceAdjustmentDraftApi(body: SavePriceAdjustmentDraftRequest) {
  return requestClient.post<ApiResponse<SavePriceAdjustmentDraftResponse>>(
    `${BASE}/saveDraft`,
    body,
  );
}

export async function submitPriceAdjustmentForApprovalApi(body: SubmitPriceAdjustmentRequest) {
  return requestClient.post<ApiResponse<SubmitPriceAdjustmentResponse>>(
    `${BASE}/submitForApproval`,
    body,
  );
}

export async function withdrawPriceAdjustmentDraftApi(body: WithdrawPriceAdjustmentDraftRequest) {
  return requestClient.post<ApiResponse<WithdrawPriceAdjustmentDraftResponse>>(
    `${BASE}/withdrawDraft`,
    body,
  );
}

export async function approvePriceAdjustmentApi(body: ApprovePriceAdjustmentRequest) {
  return requestClient.post<ApiResponse<ApprovePriceAdjustmentResponse>>(`${BASE}/approve`, body);
}

export async function rejectPriceAdjustmentApi(body: RejectPriceAdjustmentRequest) {
  return requestClient.post<ApiResponse<RejectPriceAdjustmentResponse>>(`${BASE}/reject`, body);
}

export async function checkInTransitForSubmitApi(body: CheckInTransitForSubmitRequest) {
  return requestClient.post<ApiResponse<CheckInTransitForSubmitResponse>>(
    `${BASE}/checkInTransitForSubmit`,
    body,
  );
}
