/**
 * 门诊/住院耗材使用报表 — POST /api/app/{outpatient|inpatient}ConsumableUsageReport/{method}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';

export const EXPORT_TAB_SUMMARY = 'SUMMARY' as const;
export const EXPORT_TAB_DETAIL = 'DETAIL' as const;
export type ConsumableUsageExportTab = typeof EXPORT_TAB_DETAIL | typeof EXPORT_TAB_SUMMARY;

/** 明细行合并：逐条发放行（默认）。 */
export const LINE_MERGE_MODE_LINE = 'LINE' as const;
/** 明细行合并：同就诊+品规净合计（仅患者使用源）。 */
export const LINE_MERGE_MODE_BY_DEFINITION = 'BY_DEFINITION' as const;
export type ConsumableUsageLineMergeMode =
  | typeof LINE_MERGE_MODE_BY_DEFINITION
  | typeof LINE_MERGE_MODE_LINE;

export const SUMMARY_DIMENSION_LOCATION = 'LOCATION' as const;
export const SUMMARY_DIMENSION_ITEM = 'ITEM' as const;
export const SUMMARY_DIMENSION_CATEGORY = 'CATEGORY' as const;
export const SUMMARY_DIMENSION_DEPARTMENT = 'DEPARTMENT' as const;
/** @deprecated 产品已收敛；不入默认维 */
export const SUMMARY_DIMENSION_PATIENT = 'PATIENT' as const;
export type ConsumableUsageSummaryDimension =
  | typeof SUMMARY_DIMENSION_CATEGORY
  | typeof SUMMARY_DIMENSION_DEPARTMENT
  | typeof SUMMARY_DIMENSION_ITEM
  | typeof SUMMARY_DIMENSION_LOCATION
  | typeof SUMMARY_DIMENSION_PATIENT;

export const SOURCE_CODE_ALL = 'ALL' as const;
export const SOURCE_CODE_PATIENT_DISPENSE = 'PATIENT_DISPENSE' as const;
export const SOURCE_CODE_DEPT_ISSUE = 'DEPT_ISSUE' as const;
export type ConsumableUsageSourceCode =
  | typeof SOURCE_CODE_ALL
  | typeof SOURCE_CODE_DEPT_ISSUE
  | typeof SOURCE_CODE_PATIENT_DISPENSE;

export type ConsumableUsageReportScope = 'inpatient' | 'outpatient';

const OUTPATIENT_BASE = '/outpatientConsumableUsageReport';
const INPATIENT_BASE = '/inpatientConsumableUsageReport';

export interface LoadConsumableUsageReportPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  summaryDimensionOptions?: ApiOptionItem[];
  sourceCodeOptions?: ApiOptionItem[];
  organizationOptions?: ApiOptionItem[];
  locationOptions?: ApiOptionItem[];
  feeNatureOptions?: ApiOptionItem[];
  /** 耗材类型下拉 */
  categoryOptions?: ApiOptionItem[];
  /** 执行人（发耗材人）下拉 */
  practitionerOptions?: ApiOptionItem[];
  /** 记录类型下拉 ALL/DISPENSE/REFUND */
  recordTypeOptions?: ApiOptionItem[];
}

export interface ConsumableUsageReportFilter {
  dateFrom: string;
  dateTo: string;
  sourceCode?: string;
  locationId?: string;
  feeNatureContractNo?: string;
  recordTypeCode?: string;
  deviceCategoryCodes?: string[];
  performerPractitionerId?: string;
  deviceKeyword?: string;
  patientKeyword?: string;
  /** 患者侧=开立科室；领用侧=领用科室 */
  organizationId?: string;
  /**
   * 明细行合并：LINE / BY_DEFINITION；仅住院明细。
   * BY_DEFINITION = 同就诊+品规净合计（患者使用源）。
   */
  lineMergeMode?: ConsumableUsageLineMergeMode | string;
}

export interface SummarizeConsumableUsageReportParams extends ConsumableUsageReportFilter {
  summaryDimension?: string;
}

export interface ConsumableUsageSummaryRowDto {
  dimensionKey?: string;
  summaryDimension?: string;
  /** 维度展示名（耗材房/品规/类型/科室） */
  dimensionName?: string;
  patientId?: string;
  patientName?: string;
  organizationId?: string;
  organizationName?: string;
  locationId?: string;
  locationName?: string;
  deviceDefId?: string;
  deviceName?: string;
  /** 耗材类型 code（CATEGORY 维） */
  categoryCode?: string;
  netQuantity?: number | string;
  purchaseUnitPrice?: number | string;
  saleUnitPrice?: number | string;
  /** 进价净额 */
  purchaseNetAmount?: number | string;
  /** 售价净额 */
  saleNetAmount?: number | string;
}

export interface SummarizeConsumableUsageReportResponse {
  rows?: ConsumableUsageSummaryRowDto[];
  totalNetQuantity?: number | string;
  /** 进价净额合计 */
  purchaseNetTotalAmount?: number | string;
  /** 售价净额合计 */
  saleNetTotalAmount?: number | string;
}

export interface PageConsumableUsageReportDetailParams
  extends ConsumableUsageReportFilter,
    PageParams {}

export interface ConsumableUsageDetailRowDto {
  sourceCode?: string;
  sourceCode_dictText?: string;
  dispenseId?: string;
  deviceReqId?: string;
  supplyRequestId?: string;
  busNo?: string;
  deviceDefId?: string;
  deviceName?: string;
  specText?: string;
  encounterId?: string;
  encounterBusNo?: string;
  /** 住院明细：病区 */
  wardName?: string;
  /** 住院明细：床号 */
  bedName?: string;
  patientId?: string;
  patientName?: string;
  organizationId?: string;
  organizationName?: string;
  locationId?: string;
  locationName?: string;
  statusOrTypeEnum?: string;
  statusOrTypeEnum_enumText?: string;
  quantity?: number | string;
  unitCode?: string;
  unitCode_dictText?: string;
  lotNumber?: string;
  /** 收费锚点批号（账批质检） */
  chargeLotNumber?: string;
  /** 账批≠实发批（仅质检标红；金额口径不变） */
  lotMismatch?: boolean;
  businessTime?: string;
  /** 金额（售价别名） */
  amount?: number | string;
  purchaseUnitPrice?: number | string;
  purchaseAmount?: number | string;
  saleUnitPrice?: number | string;
  saleAmount?: number | string;
  feeNatureContractNo?: string;
  feeNatureDisplay?: string;
  medType?: string;
  medTypeDisplay?: string;
  /** 执行人（发耗材人）主键 */
  performerId?: string;
  /** 执行人（发耗材人）姓名 */
  performerName?: string;
}

export interface PageConsumableUsageReportDetailResponse {
  records?: ConsumableUsageDetailRowDto[];
  total?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface ExportConsumableUsageReportParams extends ConsumableUsageReportFilter {
  exportTabCode: ConsumableUsageExportTab;
  summaryDimension?: string;
  /** 页面列清单；后端按 field 白名单裁剪写出 */
  exportColumns?: ExcelExportColumnPayload[];
}

export type ExportConsumableUsageReportResponse = BinaryExportResult;

/** 解析门诊/住院 AppService 基路径。 */
function resolveBase(scope: ConsumableUsageReportScope) {
  if (scope === 'inpatient') {
    return INPATIENT_BASE;
  }
  return OUTPATIENT_BASE;
}

/** 加载耗材使用报表页筛选项与默认日期。 */
export async function loadConsumableUsageReportPageInitApi(scope: ConsumableUsageReportScope) {
  const base = resolveBase(scope);
  const method =
    scope === 'inpatient'
      ? 'loadInpatientConsumableUsageReportPageInit'
      : 'loadOutpatientConsumableUsageReportPageInit';
  return reportPost<LoadConsumableUsageReportPageInitResponse>(`${base}/${method}`, {});
}

/** 按耗材房/品规/类型/科室净汇总。 */
export async function summarizeConsumableUsageReportApi(
  scope: ConsumableUsageReportScope,
  body: SummarizeConsumableUsageReportParams,
) {
  const base = resolveBase(scope);
  const method =
    scope === 'inpatient'
      ? 'summarizeInpatientConsumableUsageReport'
      : 'summarizeOutpatientConsumableUsageReport';
  return reportPost<SummarizeConsumableUsageReportResponse>(`${base}/${method}`, body);
}

/** 明细分页（患者发放 ∪ 科室领用）。 */
export async function pageConsumableUsageReportDetailApi(
  scope: ConsumableUsageReportScope,
  body: PageConsumableUsageReportDetailParams,
) {
  const base = resolveBase(scope);
  const method =
    scope === 'inpatient'
      ? 'pageInpatientConsumableUsageReportDetail'
      : 'pageOutpatientConsumableUsageReportDetail';
  return reportPost<PageConsumableUsageReportDetailResponse>(`${base}/${method}`, body);
}

/** 导出当前 Tab（汇总 / 明细）xlsx。 */
export async function exportConsumableUsageReportApi(
  scope: ConsumableUsageReportScope,
  body: ExportConsumableUsageReportParams,
) {
  const base = resolveBase(scope);
  const method =
    scope === 'inpatient'
      ? 'exportInpatientConsumableUsageReport'
      : 'exportOutpatientConsumableUsageReport';
  return reportPost<ExportConsumableUsageReportResponse>(`${base}/${method}`, body);
}
