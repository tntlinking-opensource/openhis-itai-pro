/**
 * 财务审批 — POST /api/app/financeApproval/<methodName>
 */
import type { ApiOptionItem, PageApiResponse, PageParams } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

const BASE = '/financeApproval';

export interface InitFinanceApprovalResponse {
  typeOptions?: ApiOptionItem[];
  statusOptions?: ApiOptionItem[];
  locationOptions?: ApiOptionItem[];
  supplierOptions?: ApiOptionItem[];
  itemTableOptions?: ApiOptionItem[];
  applicantOptions?: ApiOptionItem[];
  approverOptions?: ApiOptionItem[];
}

export interface PageFinanceApprovalDocumentParams extends PageParams {
  typeEnum?: string;
  statusEnum?: string;
  busNo?: string;
  /** 写请求 Long：string，禁止 Number() */
  locationId?: string;
  supplierId?: string;
  itemTable?: string;
  applicantId?: string;
  approverId?: string;
  occurrenceTimeFrom?: string;
  occurrenceTimeTo?: string;
  approvalTimeFrom?: string;
  approvalTimeTo?: string;
}

export interface FinanceApprovalDocumentRowDto {
  documentKind?: string;
  busNo?: string;
  typeEnum?: string;
  typeEnumText?: string;
  statusEnum?: string;
  statusEnumText?: string;
  locationId?: number | string;
  locationName?: string;
  supplierId?: number | string;
  supplierName?: string;
  itemTable?: string;
  itemTableText?: string;
  applicantId?: number | string;
  applicantName?: string;
  approverId?: number | string;
  approverName?: string;
  occurrenceTime?: string;
  applyTime?: string;
  submitTime?: string;
  approvalTime?: string;
  approveTime?: string;
  inboundAmount?: number | string;
  returnAmount?: number | string;
  stocktakeActualAmount?: number | string;
  profitLossAmount?: number | string;
  /** 调价进价盈亏（与售价分列，禁止前端加总） */
  priceAdjustmentPurchaseImpact?: number | string;
  /** 调价售价盈亏（与进价分列，禁止前端加总） */
  priceAdjustmentSellingImpact?: number | string;
  rejectReason?: string;
}

export interface AgreeFinanceApprovalParams {
  busNo: string;
  documentKind: string;
  typeEnum?: string;
}

export interface RejectFinanceApprovalParams {
  busNo: string;
  documentKind: string;
  typeEnum?: string;
  rejectReason?: string;
}

export interface FinanceApprovalWriteResponse {
  busNo?: string;
  documentKind?: string;
  statusEnum?: string;
}

/** 初始化财务审批页下拉选项 */
export async function initFinanceApprovalApi() {
  return publicRequestClient.post<InitFinanceApprovalResponse>(`${BASE}/init`, {});
}

/** 分页查询财务审批单据 */
export async function pageFinanceApprovalDocumentsApi(params: PageFinanceApprovalDocumentParams) {
  return publicRequestClient.post<PageApiResponse<FinanceApprovalDocumentRowDto>>(
    `${BASE}/pageDocuments`,
    params,
  );
}

/** 财务审批同意 */
export async function agreeFinanceApprovalApi(params: AgreeFinanceApprovalParams) {
  return publicRequestClient.post<FinanceApprovalWriteResponse>(`${BASE}/agree`, params);
}

/** 财务审批驳回 */
export async function rejectFinanceApprovalApi(params: RejectFinanceApprovalParams) {
  return publicRequestClient.post<FinanceApprovalWriteResponse>(`${BASE}/reject`, params);
}
