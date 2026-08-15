/**
 * 住院日结交账 — POST /api/app/inpatientDailySettlement/<method>
 */
import type {
  DailySettlementHandoverChannelRowDto,
  DailySettlementHandoverPaymentItemDto,
  DailySettlementHandoverSummaryDto,
} from '#/api/charge/daily-settlement-handover';
import type {
  DailySettlementExpenseNatureSummaryDto,
  DailySettlementSummaryRowDto,
} from '#/api/outpatient/daily-settlement';
import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { dailySettlementLongRequestConfig } from '#/constants/daily-settlement-request-timeout';

export type {
  DailySettlementHandoverChannelRowDto,
  DailySettlementHandoverPaymentItemDto,
  DailySettlementHandoverSummaryDto,
} from '#/api/charge/daily-settlement-handover';

const BASE = '/inpatientDailySettlement';

export interface LoadInpatientDailySettlementPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  expensePropertyOptions?: ApiOptionItem[];
  financialDelegateEnabled?: boolean;
  cashierOptions?: ApiOptionItem[];
  currentCashierId?: string;
}

export interface PreviewInpatientDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  /** 交账科室（收款日结范围键） */
  orgId: string;
  /** 多选收款人（展示软筛；空表示不按人过滤） */
  cashierIds?: string[];
  expenseProperty?: string;
}

/** 上半区：fin_type_code 字典驱动的财务类别行。 */
export interface InpatientDailySettlementChannelRowDto {
  categoryCode?: string;
  category?: string;
  amount?: string;
  unit?: string;
}

export interface InpatientDailySettlementSummaryDto {
  dateRange?: string;
  totalIncome?: string;
  receivableAmount?: string;
  discountAmount?: string;
  actualAmount?: string;
  wechatAmount?: string;
  alipayAmount?: string;
  onlineBankAmount?: string;
  cashAmount?: string;
  /** 应交现金（与现金渠道同口径） */
  cashDueAmount?: string;
  /** 医保统筹合计（各医保费用性质统筹代数和） */
  insuranceCompensationAmount?: string;
  /** 个人账户合计（各医保费用性质个人账户代数和） */
  insuranceAccountAmount?: string;
  /** 医保上传差额合计（零售−上传；勾稽项，不入总收入） */
  chsRetailUploadDiffAmount?: string;
  depositAmount?: string;
  refundTotal?: string;
  expenseNatureSummaries?: DailySettlementExpenseNatureSummaryDto[];
  summaryRows?: DailySettlementSummaryRowDto[];
}

export interface PreviewInpatientDailySettlementResponse {
  /** 待归档 payment 流水条数（含 0 元与收支抵净，不因净额排除）。 */
  paymentCount?: number;
  /** 待交账候选 payment 明细（弹窗勾选）。 */
  items?: DailySettlementHandoverPaymentItemDto[];
  rows?: InpatientDailySettlementChannelRowDto[];
  summary?: InpatientDailySettlementSummaryDto;
  /** 本次待交账合计上半区（仅未交账）。 */
  handoverRows?: DailySettlementHandoverChannelRowDto[];
  /** 本次待交账合计下半区（仅未交账）。 */
  handoverSummary?: DailySettlementHandoverSummaryDto;
  gapHardBlockEarliestAt?: string;
  gapHardBlockMessage?: string;
  gapSoftWarnCount?: number;
  practitionerIdsInWindow?: string[];
  practitionerOptions?: ApiOptionItem[];
}

/**
 * 预览响应透传（仅认嵌套 summary）。
 */
export function normalizePreviewInpatientDailySettlementResponse(
  raw: PreviewInpatientDailySettlementResponse,
): PreviewInpatientDailySettlementResponse {
  return {
    paymentCount: raw.paymentCount,
    items: raw.items,
    rows: raw.rows,
    summary: raw.summary,
    handoverRows: raw.handoverRows,
    handoverSummary: raw.handoverSummary,
    gapHardBlockEarliestAt: raw.gapHardBlockEarliestAt,
    gapHardBlockMessage: raw.gapHardBlockMessage,
    gapSoftWarnCount: raw.gapSoftWarnCount,
    practitionerIdsInWindow: raw.practitionerIdsInWindow,
    practitionerOptions: raw.practitionerOptions,
  };
}

export interface SubmitInpatientDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  cashierIds?: string[];
  orgId: string;
  locationId?: string;
  /** 勾选挂载的 payment 主键（string 保精度）。 */
  paymentIds: string[];
}

export interface SubmitInpatientDailySettlementResponse {
  settlementId?: string;
  busNo?: string;
  paymentCount?: number;
}

/** 加载住院日结页筛选项与默认结账日。 */
export async function loadInpatientDailySettlementPageInitApi() {
  return publicRequestClient.post<LoadInpatientDailySettlementPageInitResponse>(
    `${BASE}/loadInpatientDailySettlementPageInit`,
    {},
  );
}

/** 按日期窗与经办人查询日结汇总（含已交账）；paymentCount 仅统计未交账流水。 */
export async function previewInpatientDailySettlementApi(
  body: PreviewInpatientDailySettlementRequest,
) {
  return publicRequestClient.post<PreviewInpatientDailySettlementResponse>(
    `${BASE}/previewInpatientDailySettlement`,
    body,
    dailySettlementLongRequestConfig(),
  );
}

/** 确认住院交账并挂载未交账流水。 */
export async function submitInpatientDailySettlementApi(
  body: SubmitInpatientDailySettlementRequest,
) {
  return publicRequestClient.post<SubmitInpatientDailySettlementResponse>(
    `${BASE}/submitInpatientDailySettlement`,
    body,
    dailySettlementLongRequestConfig(),
  );
}

export interface PageInpatientCashierSettlementHistoryRequest {
  submittedFrom: string;
  submittedTo: string;
  pageIndex?: number;
  pageSize?: number;
}

export interface InpatientCashierSettlementHistoryItemDto {
  settlementId?: string;
  busNo?: string;
  submittedAt?: string;
  dateFrom?: string;
  dateTo?: string;
  practitionerIds?: string;
  submitterId?: string;
  submitterName?: string;
  approverId?: string;
  approverName?: string;
  approvedAt?: string;
  orgId?: string;
  orgName?: string;
  locationId?: string;
  locationName?: string;
  paymentCount?: number;
  totalIncome?: string | number;
  /** 是否可按挂载现算重打（非库存快照） */
  canReprintFromMounts?: boolean;
  statusEnum?: string;
  statusEnumText?: string;
}

export interface PageInpatientCashierSettlementHistoryResponse {
  items?: InpatientCashierSettlementHistoryItemDto[];
  total?: number;
  pageIndex?: number;
  pageSize?: number;
}

export interface LoadInpatientCashierSettlementVoucherResponse {
  settlementId?: string;
  busNo?: string;
  /** 按挂载 payment 现算的打印体 */
  mountedVoucher?: {
    schemaVersion?: number;
    cashierName?: string;
    settlementPeriodText?: string;
    rows?: InpatientDailySettlementChannelRowDto[];
    summary?: InpatientDailySettlementSummaryDto;
    paymentIds?: string[];
  };
}

export interface WithdrawInpatientDailySettlementRequest {
  settlementId: string;
}

export interface WithdrawInpatientDailySettlementResponse {
  settlementId?: string;
  busNo?: string;
  statusEnum?: string;
}

/** 住院交账历史分页。 */
export async function pageInpatientCashierSettlementHistoryApi(
  body: PageInpatientCashierSettlementHistoryRequest,
) {
  return publicRequestClient.post<PageInpatientCashierSettlementHistoryResponse>(
    `${BASE}/pageInpatientCashierSettlementHistory`,
    body,
  );
}

/** 加载住院交账原单（历史重打：挂载现算）。 */
export async function loadInpatientCashierSettlementVoucherApi(settlementId: string) {
  return publicRequestClient.post<LoadInpatientCashierSettlementVoucherResponse>(
    `${BASE}/loadInpatientCashierSettlementVoucher`,
    { settlementId },
    dailySettlementLongRequestConfig(),
  );
}

/** 撤回待审住院交账（仅 SUBMITTED）。 */
export async function withdrawInpatientDailySettlementApi(
  body: WithdrawInpatientDailySettlementRequest,
) {
  return publicRequestClient.post<WithdrawInpatientDailySettlementResponse>(
    `${BASE}/withdrawInpatientDailySettlement`,
    body,
  );
}
