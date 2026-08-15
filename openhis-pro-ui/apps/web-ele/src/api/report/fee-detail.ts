/**
 * 门诊/住院费用明细报表 — outpatientFeeDetailReport / inpatientFeeDetailReport
 */
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

import { publicRequestClient } from '#/api/request';
import { reportLongRequestConfig } from '#/constants/report-request-timeout';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

/** 报表场景：门诊或住院。 */
export type FeeDetailReportScope = 'inpatient' | 'outpatient';

/** 呈现模式：汇总。 */
export const PRESENTATION_MODE_SUMMARY = 'SUMMARY' as const;
/** 呈现模式：明细。 */
export const PRESENTATION_MODE_DETAIL = 'DETAIL' as const;
export type PresentationModeCode =
  | typeof PRESENTATION_MODE_DETAIL
  | typeof PRESENTATION_MODE_SUMMARY;

/** 汇总维：按费用类型（fin_type，默认）。 */
export const SUMMARY_DIMENSION_FIN_TYPE = 'FIN_TYPE' as const;
/** 汇总维：按开立科室。 */
export const SUMMARY_DIMENSION_DEPARTMENT = 'DEPARTMENT' as const;
/** 汇总维：按费用大类。 */
export const SUMMARY_DIMENSION_FEE_CATEGORY = 'FEE_CATEGORY' as const;
/** 汇总维：按品规/项目。 */
export const SUMMARY_DIMENSION_ITEM = 'ITEM' as const;
export type FeeDetailSummaryDimension =
  | typeof SUMMARY_DIMENSION_DEPARTMENT
  | typeof SUMMARY_DIMENSION_FEE_CATEGORY
  | typeof SUMMARY_DIMENSION_FIN_TYPE
  | typeof SUMMARY_DIMENSION_ITEM;

/** 住院结算状态：全部。 */
export const SETTLEMENT_STATUS_ALL = 'ALL' as const;
/** 住院结算状态：已结算。 */
export const SETTLEMENT_STATUS_SETTLED = 'SETTLED' as const;
/** 住院结算状态：待结算。 */
export const SETTLEMENT_STATUS_PENDING = 'PENDING_SETTLEMENT' as const;

function resolveReportBase(scope: FeeDetailReportScope): string {
  if (scope === 'inpatient') {
    return '/inpatientFeeDetailReport';
  }
  return '/outpatientFeeDetailReport';
}

export interface LoadFeeDetailReportPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  defaultPageSize?: number;
  defaultActiveTab?: string;
  tradeDirectionOptions?: ApiOptionItem[];
  feeCategoryOptions?: ApiOptionItem[];
  medicationSubCategoryOptions?: ApiOptionItem[];
  activitySubCategoryOptions?: ApiOptionItem[];
  deviceSubCategoryOptions?: ApiOptionItem[];
  /** 开立科室下拉（复用后端 registrationDepartmentOptions 科室主数据）。 */
  registrationDepartmentOptions?: ApiOptionItem[];
  cashierOptions?: ApiOptionItem[];
  feeNatureOptions?: ApiOptionItem[];
  /** 住院：结算状态筛（全部/已结算/待结算）。 */
  settlementStatusOptions?: ApiOptionItem[];
  presentationModeOptions?: ApiOptionItem[];
  /** 汇总维度下拉（仅汇总 Tab）。 */
  summaryDimensionOptions?: ApiOptionItem[];
}

export interface FeeDetailReportQueryFilter {
  dateFrom: string;
  dateTo: string;
  /** @deprecated 报表中心改用 requestingOrgId */
  registrationDepartmentId?: string;
  /** 开立科室（账单 requesting_org_id） */
  requestingOrgId?: string;
  feeCategoryCodes?: string[];
  subCategoryCodes?: string[];
  patientKeyword?: string;
  productNameKeyword?: string;
  productCodeKeyword?: string;
  cashierId?: string;
  /** 明细 Tab 生效；汇总强制净视角。 */
  tradeDirection?: string;
  /** 费用性质合同 bus_no；空则不筛选。 */
  feeNatureContractNo?: string;
  /**
   * 住院结算状态筛：ALL / SETTLED / PENDING_SETTLEMENT；空则后端默认 ALL。
   * 门诊报表忽略。
   */
  settlementStatus?: string;
  /** 汇总维度；仅汇总 Tab / 汇总导出生效。 */
  summaryDimension?: string;
}

export interface FeeDetailReportSummaryRowDto {
  /** 汇总维度 code。 */
  summaryDimension?: string;
  /** 维度分桶键。 */
  dimensionKey?: string;
  /** 维度展示名。 */
  dimensionName?: string;
  /** FIN_TYPE 维兼容。 */
  finTypeCode?: string;
  finTypeDisplay?: string;
  purchaseAmountTotal?: number | string;
  saleAmountTotal?: number | string;
  actualReceivedAmountTotal?: number | string;
  receivableAmountTotal?: number | string;
  discountedAmountTotal?: number | string;
  lineAmountTotal?: number | string;
  lineCount?: number;
}

export interface FeeDetailReportSummaryFooterDto {
  purchaseAmountTotal?: number | string;
  saleAmountTotal?: number | string;
  actualReceivedAmountTotal?: number | string;
  receivableAmountTotal?: number | string;
  discountedAmountTotal?: number | string;
  lineAmountTotal?: number | string;
  lineCountTotal?: number;
}

export interface SummarizeFeeDetailReportResponse {
  rows?: FeeDetailReportSummaryRowDto[];
  footer?: FeeDetailReportSummaryFooterDto;
}

export interface FeeDetailAmountSummaryDto {
  baseAmountTotal?: number | string;
  discountAmountTotal?: number | string;
  receivableAmountTotal?: number | string;
  discountedAmountTotal?: number | string;
  lineAmountTotal?: number | string;
  purchaseAmountTotal?: number | string;
  saleAmountTotal?: number | string;
  actualReceivedAmountTotal?: number | string;
  insuranceAmountTotal?: number | string;
  selfPayAmountTotal?: number | string;
}

export interface FeeDetailLineDto {
  chargeItemId?: string;
  paymentId?: string;
  feeCategoryCode?: string;
  feeCategoryDisplay?: string;
  subCategoryCode?: string;
  subCategoryDisplay?: string;
  productName?: string;
  productCode?: string;
  specification?: string;
  quantityValue?: number | string;
  quantityUnitDisplay?: string;
  orderingDoctorId?: string;
  orderingDoctorName?: string;
  /** 执行人主键 */
  performerId?: string;
  /** 执行人姓名 */
  performerName?: string;
  cashierId?: string;
  cashierName?: string;
  billDate?: string;
  tradeDirection?: string;
  /** 费用性质合同 bus_no（账单账户）。 */
  feeNatureContractNo?: string;
  /** 费用性质展示（ChargeItem.account_id → 合同名）。 */
  feeNatureDisplay?: string;
  insuranceLevelDisplay?: string;
  baseAmount?: number | string;
  discountAmount?: number | string;
  receivableAmount?: number | string;
  discountedAmount?: number | string;
  unitPrice?: number | string;
  lineAmount?: number | string;
  purchaseUnitPrice?: number | string;
  purchaseAmount?: number | string;
  saleUnitPrice?: number | string;
  saleAmount?: number | string;
  actualReceivedAmount?: number | string;
  insuranceAmount?: number | string;
  selfPayAmount?: number | string;
  discountRateDisplay?: string;
}

export interface FeeDetailEncounterBlockDto {
  encounterId?: string;
  patientName?: string;
  busNo?: string;
  registrationDepartmentName?: string;
  /** 开立科室名（账单 requesting_org_id） */
  requestingOrgName?: string;
  encounterDate?: string;
  feeNatureContractNo?: string;
  feeNatureDisplay?: string;
  medType?: string;
  medTypeDisplay?: string;
  lines?: FeeDetailLineDto[];
  subtotal?: FeeDetailAmountSummaryDto;
}

export interface PageFeeDetailReportParams extends FeeDetailReportQueryFilter, PageParams {}

export interface PageFeeDetailReportResponse {
  content?: FeeDetailEncounterBlockDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
  summary?: FeeDetailAmountSummaryDto;
}

export interface ExportFeeDetailReportParams extends FeeDetailReportQueryFilter {
  presentationMode: PresentationModeCode | string;
}

export type ExportFeeDetailReportResponse = BinaryExportResult & {
  rowCount?: number;
};

/** 页初始化。 */
export async function loadFeeDetailReportPageInitApi(scope: FeeDetailReportScope) {
  const base = resolveReportBase(scope);
  const method =
    scope === 'inpatient'
      ? 'loadInpatientFeeDetailReportPageInit'
      : 'loadOutpatientFeeDetailReportPageInit';
  return publicRequestClient.post<LoadFeeDetailReportPageInitResponse>(`${base}/${method}`, {});
}

/** 费用类型净汇总。 */
export async function summarizeFeeDetailReportApi(
  scope: FeeDetailReportScope,
  body: FeeDetailReportQueryFilter,
) {
  const base = resolveReportBase(scope);
  const method =
    scope === 'inpatient'
      ? 'summarizeInpatientFeeDetailReport'
      : 'summarizeOutpatientFeeDetailReport';
  return publicRequestClient.post<SummarizeFeeDetailReportResponse>(
    `${base}/${method}`,
    body,
    reportLongRequestConfig(),
  );
}

/** 就诊块明细分页。 */
export async function pageFeeDetailReportApi(
  scope: FeeDetailReportScope,
  body: PageFeeDetailReportParams,
) {
  const base = resolveReportBase(scope);
  const method =
    scope === 'inpatient' ? 'pageInpatientFeeDetailReport' : 'pageOutpatientFeeDetailReport';
  const res = await publicRequestClient.post<PageFeeDetailReportResponse>(
    `${base}/${method}`,
    body,
    reportLongRequestConfig(),
  );
  // 统一剥分页包装，避免 totalCount 丢失导致「共 N 条」为 0、翻页控件不可用
  const { content, totalCount, pageNo, pageSize } =
    parsePagedRowsFromApiData<FeeDetailEncounterBlockDto>(res);
  return {
    ...res,
    content,
    totalCount: totalCount || Number(res?.totalCount ?? 0),
    pageNo: pageNo ?? res?.pageNo,
    pageSize: pageSize ?? res?.pageSize,
    summary: res?.summary,
  } satisfies PageFeeDetailReportResponse;
}

/** 按当前 Tab 导出。 */
export async function exportFeeDetailReportApi(
  scope: FeeDetailReportScope,
  body: ExportFeeDetailReportParams,
) {
  const base = resolveReportBase(scope);
  const method =
    scope === 'inpatient' ? 'exportInpatientFeeDetailReport' : 'exportOutpatientFeeDetailReport';
  return publicRequestClient.post<ExportFeeDetailReportResponse>(
    `${base}/${method}`,
    body,
    reportLongRequestConfig(),
  );
}
