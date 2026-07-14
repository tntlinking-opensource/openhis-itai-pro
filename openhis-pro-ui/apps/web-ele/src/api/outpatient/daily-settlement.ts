/**
 * 门诊日结交账 — 对接 {@code POST /api/app/outpatientDailySettlement/<method>}
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
  cashierId?: string;
  expenseProperty?: string;
}

/** 上半区：fin_type_code 字典驱动的财务类别行。 */
export interface DailySettlementChannelRowDto {
  categoryCode?: string;
  category?: string;
  amount?: string;
  unit?: string;
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
  selfPayCashTotal?: string;
  selfPayCount?: number;
  provincialInsuranceCount?: number;
  municipalInsuranceCount?: number;
  provincialInsuranceCash?: string;
  provincialInsuranceFund?: string;
  provincialInsuranceAccount?: string;
  provincialInsuranceFundTotal?: string;
  provincialInsuranceTotal?: string;
  municipalInsuranceCash?: string;
  municipalInsuranceFund?: string;
  municipalInsuranceAccount?: string;
  municipalInsuranceFundTotal?: string;
  municipalInsuranceTotal?: string;
  totalCashIncome?: string;
  physicalExamCount?: number;
  insuranceCount?: number;
  refundTotal?: string;
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
}

/** 下半区固定展示项（对齐老门诊日结单，顺序不可删减）。 */
export const DAILY_SETTLEMENT_SUMMARY_DISPLAY = [
  { key: 'totalIncome', label: '总收入', unit: '元' },
  { key: 'wechatAmount', label: '微信', unit: '元' },
  { key: 'alipayAmount', label: '支付宝', unit: '元' },
  { key: 'onlineBankAmount', label: '网银', unit: '元' },
  { key: 'selfPayCashTotal', label: '现金', unit: '元' },
  { key: 'selfPayCount', label: '自费人次', unit: '人' },
  { key: 'provincialInsuranceCount', label: '省医保人次', unit: '人' },
  { key: 'municipalInsuranceCount', label: '市医保人次', unit: '人' },
  { key: 'provincialInsuranceCash', label: '省医保现金', unit: '元' },
  { key: 'provincialInsuranceFund', label: '省医保统筹', unit: '元' },
  { key: 'provincialInsuranceAccount', label: '省医保账户', unit: '元' },
  { key: 'provincialInsuranceFundTotal', label: '省医保基金', unit: '元' },
  { key: 'municipalInsuranceCash', label: '市医保现金', unit: '元' },
  { key: 'municipalInsuranceFund', label: '市医保统筹', unit: '元' },
  { key: 'municipalInsuranceAccount', label: '市医保账户', unit: '元' },
  { key: 'municipalInsuranceFundTotal', label: '市医保基金', unit: '元' },
  { key: 'provincialInsuranceTotal', label: '省医保总额', unit: '元' },
  { key: 'municipalInsuranceTotal', label: '市医保总额', unit: '元' },
  { key: 'totalCashIncome', label: '自费总额', unit: '元' },
  { key: 'physicalExamCount', label: '体检人次', unit: '人' },
] as const satisfies ReadonlyArray<{
  key: keyof Omit<
    DailySettlementSummaryDto,
    | 'actualAmount'
    | 'cashAmount'
    | 'dateRange'
    | 'discountAmount'
    | 'insuranceCount'
    | 'receivableAmount'
    | 'refundTotal'
  >;
  label: string;
  unit: string;
}>;

/** 兼容 summary 嵌套或平铺在响应根上的两种形态。 */
export function normalizePreviewOutpatientDailySettlementResponse(
  raw: DailySettlementSummaryDto & PreviewOutpatientDailySettlementResponse,
): PreviewOutpatientDailySettlementResponse {
  if (raw.summary) {
    return {
      paymentCount: raw.paymentCount,
      items: raw.items,
      rows: raw.rows,
      summary: raw.summary,
      handoverRows: raw.handoverRows,
      handoverSummary: raw.handoverSummary,
    };
  }
  const summary: DailySettlementSummaryDto = {
    dateRange: raw.dateRange,
    totalIncome: raw.totalIncome,
    receivableAmount: raw.receivableAmount,
    discountAmount: raw.discountAmount,
    actualAmount: raw.actualAmount,
    wechatAmount: raw.wechatAmount,
    alipayAmount: raw.alipayAmount,
    onlineBankAmount: raw.onlineBankAmount,
    cashAmount: raw.cashAmount,
    selfPayCashTotal: raw.selfPayCashTotal,
    selfPayCount: raw.selfPayCount,
    provincialInsuranceCount: raw.provincialInsuranceCount,
    municipalInsuranceCount: raw.municipalInsuranceCount,
    provincialInsuranceCash: raw.provincialInsuranceCash,
    provincialInsuranceFund: raw.provincialInsuranceFund,
    provincialInsuranceAccount: raw.provincialInsuranceAccount,
    provincialInsuranceFundTotal: raw.provincialInsuranceFundTotal,
    provincialInsuranceTotal: raw.provincialInsuranceTotal,
    municipalInsuranceCash: raw.municipalInsuranceCash,
    municipalInsuranceFund: raw.municipalInsuranceFund,
    municipalInsuranceAccount: raw.municipalInsuranceAccount,
    municipalInsuranceFundTotal: raw.municipalInsuranceFundTotal,
    municipalInsuranceTotal: raw.municipalInsuranceTotal,
    totalCashIncome: raw.totalCashIncome,
    physicalExamCount: raw.physicalExamCount,
    insuranceCount: raw.insuranceCount,
    refundTotal: raw.refundTotal,
  };
  const hasSummaryField = DAILY_SETTLEMENT_SUMMARY_DISPLAY.some(
    ({ key }) => summary[key] !== undefined && summary[key] !== null,
  );
  if (!hasSummaryField && summary.dateRange === undefined) {
    return {
      paymentCount: raw.paymentCount,
      items: raw.items,
      rows: raw.rows,
      handoverRows: raw.handoverRows,
      handoverSummary: raw.handoverSummary,
    };
  }
  return {
    paymentCount: raw.paymentCount,
    items: raw.items,
    rows: raw.rows,
    summary,
    handoverRows: raw.handoverRows,
    handoverSummary: raw.handoverSummary,
  };
}

export interface SubmitOutpatientDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  cashierId?: string;
  orgId: string;
  locationId?: string;
  /** 勾选挂载的 payment 主键（string 保精度）。 */
  paymentIds: string[];
}

export interface SubmitOutpatientDailySettlementResponse {
  settlementId?: string;
  busNo?: string;
  paymentCount?: number;
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
