/**
 * 门诊/住院费用明细报表 — outpatientFeeDetailReport / inpatientFeeDetailReport
 */
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

import { publicRequestClient } from '#/api/request';

/** 报表场景：门诊或住院。 */
export type FeeDetailReportScope = 'inpatient' | 'outpatient';

/** 呈现模式：汇总。 */
export const PRESENTATION_MODE_SUMMARY = 'SUMMARY' as const;
/** 呈现模式：明细。 */
export const PRESENTATION_MODE_DETAIL = 'DETAIL' as const;
export type PresentationModeCode =
  | typeof PRESENTATION_MODE_DETAIL
  | typeof PRESENTATION_MODE_SUMMARY;

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
  /** 门诊=挂号科室；住院=入院科室（字段名与后端契约一致）。 */
  registrationDepartmentOptions?: ApiOptionItem[];
  cashierOptions?: ApiOptionItem[];
  feeNatureOptions?: ApiOptionItem[];
  presentationModeOptions?: ApiOptionItem[];
}

export interface FeeDetailReportQueryFilter {
  dateFrom: string;
  dateTo: string;
  registrationDepartmentId?: string;
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
}

export interface FeeDetailReportSummaryRowDto {
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
  cashierId?: string;
  cashierName?: string;
  billDate?: string;
  tradeDirection?: string;
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
  return publicRequestClient.post<SummarizeFeeDetailReportResponse>(`${base}/${method}`, body);
}

/** 就诊块明细分页。 */
export async function pageFeeDetailReportApi(
  scope: FeeDetailReportScope,
  body: PageFeeDetailReportParams,
) {
  const base = resolveReportBase(scope);
  const method =
    scope === 'inpatient' ? 'pageInpatientFeeDetailReport' : 'pageOutpatientFeeDetailReport';
  return publicRequestClient.post<PageFeeDetailReportResponse>(`${base}/${method}`, body);
}

/** 按当前 Tab 导出。 */
export async function exportFeeDetailReportApi(
  scope: FeeDetailReportScope,
  body: ExportFeeDetailReportParams,
) {
  const base = resolveReportBase(scope);
  const method =
    scope === 'inpatient' ? 'exportInpatientFeeDetailReport' : 'exportOutpatientFeeDetailReport';
  return publicRequestClient.post<ExportFeeDetailReportResponse>(`${base}/${method}`, body);
}
