/**
 * 门诊日结交账 — POST /api/app/outpatientDailySettlement/<method>
 */
import type {
  DailySettlementHandoverChannelRowDto,
  DailySettlementHandoverPaymentItemDto,
  DailySettlementHandoverSummaryDto,
} from '#/api/charge/daily-settlement-handover';
import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';

export type {
  DailySettlementHandoverChannelRowDto,
  DailySettlementHandoverPaymentItemDto,
  DailySettlementHandoverSummaryDto,
} from '#/api/charge/daily-settlement-handover';

const BASE = '/outpatientDailySettlement';

export interface LoadOutpatientDailySettlementPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  expensePropertyOptions?: ApiOptionItem[];
  financialDelegateEnabled?: boolean;
  cashierOptions?: ApiOptionItem[];
  currentCashierId?: string;
}

export interface PreviewOutpatientDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  /** 交账科室（收款日结范围键） */
  orgId: string;
  /** 多选收款人（展示软筛；空表示不按人过滤） */
  cashierIds?: string[];
  expenseProperty?: string;
}

/** 上半区：fin_type_code 字典驱动的财务类别行。 */
export interface DailySettlementChannelRowDto {
  categoryCode?: string;
  category?: string;
  amount?: string;
  unit?: string;
}

/** 日结下半区扁平展示行。 */
export interface DailySettlementSummaryRowDto {
  key?: string;
  label?: string;
  value?: string;
  unit?: string;
  /** 0=公共/费用性质，1=险种子行 */
  level?: number;
}

/** 医保合同下按险种类型汇总。 */
export interface DailySettlementInsuranceTypeSummaryDto {
  insutype?: string;
  insutypeName?: string;
  personCount?: number;
  cashAmount?: string;
  fundAmount?: string;
  accountAmount?: string;
  fundTotalAmount?: string;
  totalAmount?: string;
}

/** 按费用性质（fin_contract）动态汇总。 */
export interface DailySettlementExpenseNatureSummaryDto {
  contractNo?: string;
  contractName?: string;
  insurance?: boolean;
  personCount?: number;
  cashAmount?: string;
  fundAmount?: string;
  accountAmount?: string;
  fundTotalAmount?: string;
  totalAmount?: string;
  insuranceTypeSummaries?: DailySettlementInsuranceTypeSummaryDto[];
}

export interface DailySettlementSummaryDto {
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
  /** 医保补偿合计（各医保费用性质统筹代数和） */
  insuranceCompensationAmount?: string;
  physicalExamCount?: number;
  refundTotal?: string;
  expenseNatureSummaries?: DailySettlementExpenseNatureSummaryDto[];
  /** 下半区主渲染源 */
  summaryRows?: DailySettlementSummaryRowDto[];
}

export interface PreviewOutpatientDailySettlementResponse {
  /** 待归档 payment 流水条数（含 0 元与收支抵净，不因净额排除）。 */
  paymentCount?: number;
  /** 待交账候选 payment 明细（弹窗勾选）。 */
  items?: DailySettlementHandoverPaymentItemDto[];
  rows?: DailySettlementChannelRowDto[];
  summary?: DailySettlementSummaryDto;
  /** 本次待交账合计上半区（仅未交账；全选时弹窗底部与之对齐）。 */
  handoverRows?: DailySettlementHandoverChannelRowDto[];
  /** 本次待交账合计下半区（仅未交账）。 */
  handoverSummary?: DailySettlementHandoverSummaryDto;
  /** 漏交硬栏最早时刻；非空时禁止提交 */
  gapHardBlockEarliestAt?: string;
  gapHardBlockMessage?: string;
  /** 截止后未交账条数（仅提醒） */
  gapSoftWarnCount?: number;
  /** 窗内收费人 Practitioner 主键 */
  practitionerIdsInWindow?: string[];
  /** 窗内收费人下拉 */
  practitionerOptions?: ApiOptionItem[];
}

/**
 * 预览响应透传（仅认嵌套 summary）。
 */
export function normalizePreviewOutpatientDailySettlementResponse(
  raw: PreviewOutpatientDailySettlementResponse,
): PreviewOutpatientDailySettlementResponse {
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

export interface SubmitOutpatientDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  orgId: string;
  cashierIds?: string[];
  /** 勾选挂载的 payment 主键（string 保精度）。 */
  paymentIds: string[];
}

export interface SubmitOutpatientDailySettlementResponse {
  settlementId?: string;
  busNo?: string;
  paymentCount?: number;
  gapSoftWarnCount?: number;
}

export interface PageOutpatientCashierSettlementHistoryRequest {
  submittedFrom: string;
  submittedTo: string;
  pageIndex?: number;
  pageSize?: number;
}

export interface OutpatientCashierSettlementHistoryItemDto {
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

export interface PageOutpatientCashierSettlementHistoryResponse {
  items?: OutpatientCashierSettlementHistoryItemDto[];
  total?: number;
  pageIndex?: number;
  pageSize?: number;
}

export interface LoadOutpatientCashierSettlementVoucherResponse {
  settlementId?: string;
  busNo?: string;
  /** 按挂载 payment 现算的打印体 */
  mountedVoucher?: {
    schemaVersion?: number;
    cashierName?: string;
    settlementPeriodText?: string;
    rows?: DailySettlementChannelRowDto[];
    summary?: DailySettlementSummaryDto;
    paymentIds?: string[];
  };
}

export interface WithdrawOutpatientDailySettlementRequest {
  settlementId: string;
}

export interface WithdrawOutpatientDailySettlementResponse {
  settlementId?: string;
  busNo?: string;
  statusEnum?: string;
}

/** 加载门诊日结页筛选项与默认结账日。 */
export async function loadOutpatientDailySettlementPageInitApi() {
  return publicRequestClient.post<LoadOutpatientDailySettlementPageInitResponse>(
    `${BASE}/loadOutpatientDailySettlementPageInit`,
    {},
  );
}

/** 按日期窗与经办人查询日结汇总（含已交账）；paymentCount 仅统计未交账流水。 */
export async function previewOutpatientDailySettlementApi(
  body: PreviewOutpatientDailySettlementRequest,
) {
  return publicRequestClient.post<PreviewOutpatientDailySettlementResponse>(
    `${BASE}/previewOutpatientDailySettlement`,
    body,
  );
}

/** 确认门诊交账并挂载未交账流水。 */
export async function submitOutpatientDailySettlementApi(
  body: SubmitOutpatientDailySettlementRequest,
) {
  return publicRequestClient.post<SubmitOutpatientDailySettlementResponse>(
    `${BASE}/submitOutpatientDailySettlement`,
    body,
  );
}

/** 门诊交账历史分页。 */
export async function pageOutpatientCashierSettlementHistoryApi(
  body: PageOutpatientCashierSettlementHistoryRequest,
) {
  return publicRequestClient.post<PageOutpatientCashierSettlementHistoryResponse>(
    `${BASE}/pageOutpatientCashierSettlementHistory`,
    body,
  );
}

/** 加载门诊交账原单快照（历史重打）。 */
export async function loadOutpatientCashierSettlementVoucherApi(settlementId: string) {
  return publicRequestClient.post<LoadOutpatientCashierSettlementVoucherResponse>(
    `${BASE}/loadOutpatientCashierSettlementVoucher`,
    { settlementId },
  );
}

/** 撤回待审门诊交账（仅 SUBMITTED）。 */
export async function withdrawOutpatientDailySettlementApi(
  body: WithdrawOutpatientDailySettlementRequest,
) {
  return publicRequestClient.post<WithdrawOutpatientDailySettlementResponse>(
    `${BASE}/withdrawOutpatientDailySettlement`,
    body,
  );
}
