/**
 * 交账审批 — POST /api/app/cashierSettlementApproval/<method>
 */
import type { ApiOptionItem, PageApiResponse, PageParams } from '#/types/api-response';
import type {
  DailySettlementChannelRowDto,
  DailySettlementSummaryDto,
} from '#/api/outpatient/daily-settlement';

import { publicRequestClient } from '#/api/request';
import { dailySettlementLongRequestConfig } from '#/constants/daily-settlement-request-timeout';

const BASE = '/cashierSettlementApproval';

export interface LoadCashierSettlementApprovalPageInitResponse {
  categoryOptions?: ApiOptionItem[];
  typeOptions?: ApiOptionItem[];
  statusOptions?: ApiOptionItem[];
  /** 药房下拉（药房日结筛选） */
  pharmacyOptions?: ApiOptionItem[];
}

export interface PageCashierSettlementApprovalsParams extends PageParams {
  /** PAYMENT / PHARMACY；空=不限 */
  categoryEnum?: string;
  typeEnum?: string;
  typeEnums?: string[];
  statusEnum?: string;
  statusEnums?: string[];
  submittedFrom?: string;
  submittedTo?: string;
  orgId?: string;
  locationId?: string;
  busNoKeyword?: string;
}

export interface CashierSettlementApprovalListItemDto {
  settlementId?: string;
  busNo?: string;
  typeEnum?: string;
  typeEnumText?: string;
  statusEnum?: string;
  statusEnumText?: string;
  orgId?: string;
  orgName?: string;
  locationId?: string;
  locationName?: string;
  dateFrom?: string;
  dateTo?: string;
  paymentCount?: number;
  totalIncome?: string | number;
  submitterId?: string;
  submitterName?: string;
  submittedAt?: string;
  approverId?: string;
  approverName?: string;
  approvedAt?: string;
}

export interface CashierSettlementApprovalHeaderDto extends CashierSettlementApprovalListItemDto {
  practitionerIds?: string;
}

export interface PharmacyDailySettlementCategoryRowDto {
  categoryCode?: string;
  categoryLabel?: string;
  netQuantity?: string | number;
  purchaseAmount?: string | number;
  saleAmount?: string | number;
}

export interface PharmacyDailySettlementProductRowDto {
  medicationId?: string;
  medicationName?: string;
  busNo?: string;
  specification?: string;
  manufacturer?: string;
  netQuantity?: string | number;
  purchaseUnitPrice?: string | number;
  saleUnitPrice?: string | number;
  purchaseAmount?: string | number;
  saleAmount?: string | number;
}

export interface LoadCashierSettlementApprovalDetailResponse {
  header?: CashierSettlementApprovalHeaderDto;
  mountedSourceCount?: number;
  detailBodyRecomputeNote?: string;
  /** 收款日结上半区（挂载现算） */
  paymentRows?: DailySettlementChannelRowDto[];
  /** 收款日结下半区汇总 */
  paymentSummary?: DailySettlementSummaryDto;
  pharmacyCategoryRows?: PharmacyDailySettlementCategoryRowDto[];
  pharmacyProductRows?: PharmacyDailySettlementProductRowDto[];
}

export interface ApproveCashierSettlementParams {
  settlementId: string;
}

export interface RejectCashierSettlementParams {
  settlementId: string;
  reason?: string;
}

/** 加载交账审批页筛选项。 */
export async function loadCashierSettlementApprovalPageInitApi() {
  return publicRequestClient.post<LoadCashierSettlementApprovalPageInitResponse>(
    `${BASE}/loadCashierSettlementApprovalPageInit`,
    {},
  );
}

/** 分页查询交账审批列表。 */
export async function pageCashierSettlementApprovalsApi(
  params: PageCashierSettlementApprovalsParams,
) {
  return publicRequestClient.post<PageApiResponse<CashierSettlementApprovalListItemDto>>(
    `${BASE}/pageCashierSettlementApprovals`,
    params,
  );
}

/** 加载交账审批详情（按挂载现算，可能超过默认 10s）。 */
export async function loadCashierSettlementApprovalDetailApi(settlementId: string) {
  return publicRequestClient.post<LoadCashierSettlementApprovalDetailResponse>(
    `${BASE}/loadCashierSettlementApprovalDetail`,
    { settlementId },
    dailySettlementLongRequestConfig(),
  );
}

/** 审批通过交账单。 */
export async function approveCashierSettlementApi(params: ApproveCashierSettlementParams) {
  return publicRequestClient.post<{ settlementId?: string; statusEnum?: string }>(
    `${BASE}/approveCashierSettlement`,
    params,
  );
}

/** 驳回交账单。 */
export async function rejectCashierSettlementApi(params: RejectCashierSettlementParams) {
  return publicRequestClient.post<{ settlementId?: string; statusEnum?: string }>(
    `${BASE}/rejectCashierSettlement`,
    params,
  );
}
