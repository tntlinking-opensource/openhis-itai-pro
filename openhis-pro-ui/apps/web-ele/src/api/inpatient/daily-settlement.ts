/**
 * 住院日结交账 — POST /api/app/inpatientDailySettlement/<method>
 */
import type {
  DailySettlementHandoverChannelRowDto,
  DailySettlementHandoverPaymentItemDto,
  DailySettlementHandoverSummaryDto,
} from '#/api/charge/daily-settlement-handover';
import type { ApiOptionItem } from '#/types/api-response';

import { publicRequestClient } from '#/api/request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

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
  cashierId?: string;
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
  depositAmount?: string;
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
  insuranceCount?: number;
  refundTotal?: string;
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
}

/** 下半区固定展示项（对齐老日结单 + 住院特有预交金，顺序不可删减）。 */
export const INPATIENT_DAILY_SETTLEMENT_SUMMARY_DISPLAY = [
  { key: 'totalIncome', label: '总收入', unit: '元' },
  { key: 'wechatAmount', label: '微信', unit: '元' },
  { key: 'alipayAmount', label: '支付宝', unit: '元' },
  { key: 'onlineBankAmount', label: '网银', unit: '元' },
  { key: 'depositAmount', label: '预交金', unit: '元' },
  { key: 'selfPayCashTotal', label: '自费现金总额', unit: '元' },
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
  { key: 'totalCashIncome', label: '现金收入总额', unit: '元' },
] as const satisfies ReadonlyArray<{
  key: keyof Omit<
    InpatientDailySettlementSummaryDto,
    | 'dateRange'
    | 'receivableAmount'
    | 'discountAmount'
    | 'actualAmount'
    | 'cashAmount'
    | 'insuranceCount'
    | 'refundTotal'
  >;
  label: string;
  unit: string;
}>;

/**
 * 从页初始化数据解析默认收款人 ID（OptionDto.value 为权威 string，避免 currentCashierId 经 JSON number 丢精度）。
 */
export function resolveDefaultCashierIdFromPageInit(
  data: LoadInpatientDailySettlementPageInitResponse,
  sessionPractitionerId?: string,
): string | undefined {
  const options = data.cashierOptions ?? [];
  const optionValues = options
    .map((item) => preserveApiIdForRequest(item.value))
    .filter((value): value is string => Boolean(value));
  // 非代交账仅本人：唯一下拉项 value 即正确 Practitioner 主键
  if (optionValues.length === 1) {
    return optionValues[0];
  }
  const sessionId = preserveApiIdForRequest(sessionPractitionerId);
  if (sessionId && optionValues.includes(sessionId)) {
    return sessionId;
  }
  const currentId = preserveApiIdForRequest(data.currentCashierId);
  if (currentId && optionValues.includes(currentId)) {
    return currentId;
  }
  return optionValues[0] ?? currentId;
}

/** 兼容 summary 嵌套或平铺在响应根上的两种形态。 */
export function normalizePreviewInpatientDailySettlementResponse(
  raw: PreviewInpatientDailySettlementResponse & InpatientDailySettlementSummaryDto,
): PreviewInpatientDailySettlementResponse {
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
  const summary: InpatientDailySettlementSummaryDto = {
    dateRange: raw.dateRange,
    totalIncome: raw.totalIncome,
    receivableAmount: raw.receivableAmount,
    discountAmount: raw.discountAmount,
    actualAmount: raw.actualAmount,
    wechatAmount: raw.wechatAmount,
    alipayAmount: raw.alipayAmount,
    onlineBankAmount: raw.onlineBankAmount,
    cashAmount: raw.cashAmount,
    depositAmount: raw.depositAmount,
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
    insuranceCount: raw.insuranceCount,
    refundTotal: raw.refundTotal,
  };
  const hasSummaryField = INPATIENT_DAILY_SETTLEMENT_SUMMARY_DISPLAY.some(
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

export interface SubmitInpatientDailySettlementRequest {
  dateFrom: string;
  dateTo: string;
  cashierId?: string;
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
  );
}

/** 确认住院交账并挂载未交账流水。 */
export async function submitInpatientDailySettlementApi(
  body: SubmitInpatientDailySettlementRequest,
) {
  return publicRequestClient.post<SubmitInpatientDailySettlementResponse>(
    `${BASE}/submitInpatientDailySettlement`,
    body,
  );
}
