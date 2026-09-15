/**
 * 门诊/住院发药报表 — POST /api/app/{outpatient|inpatient}MedicationDispenseReport/{method}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';

export const PRESENTATION_MODE_SUMMARY = 'SUMMARY' as const;
export const PRESENTATION_MODE_DETAIL = 'DETAIL' as const;
export type MedicationDispensePresentationMode =
  | typeof PRESENTATION_MODE_DETAIL
  | typeof PRESENTATION_MODE_SUMMARY;

export const SUMMARY_DIMENSION_PHARMACY = 'PHARMACY' as const;
export const SUMMARY_DIMENSION_ITEM = 'ITEM' as const;
export const SUMMARY_DIMENSION_CATEGORY = 'CATEGORY' as const;
export const SUMMARY_DIMENSION_DEPARTMENT = 'DEPARTMENT' as const;

export const SOURCE_CODE_ALL = 'ALL' as const;
export const SOURCE_CODE_PATIENT_DISPENSE = 'PATIENT_DISPENSE' as const;
export const SOURCE_CODE_DEPT_ISSUE = 'DEPT_ISSUE' as const;

export type MedicationDispenseReportScope = 'inpatient' | 'outpatient';

const OUTPATIENT_BASE = '/outpatientMedicationDispenseReport';
const INPATIENT_BASE = '/inpatientMedicationDispenseReport';

export interface LoadMedicationDispenseReportPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  pharmacyOptions?: ApiOptionItem[];
  categoryOptions?: ApiOptionItem[];
  practitionerOptions?: ApiOptionItem[];
  recordTypeOptions?: ApiOptionItem[];
  summaryDimensionOptions?: ApiOptionItem[];
  sourceOptions?: ApiOptionItem[];
  feeNatureOptions?: ApiOptionItem[];
  /** 开立/领用科室下拉 */
  organizationOptions?: ApiOptionItem[];
}

export interface MedicationDispenseReportFilter {
  dispenseDateFrom: string;
  dispenseDateTo: string;
  recordTypeCode?: string;
  pharmacyLocationId?: string;
  medicationCategoryCodes?: string[];
  dispenserPractitionerId?: string;
  drugKeyword?: string;
  patientKeyword?: string;
  sourceCode?: string;
  feeNatureContractNo?: string;
  /** 患者侧=开立科室；领用侧=领用科室 */
  organizationId?: string;
}

export interface SummarizeMedicationDispenseReportParams extends MedicationDispenseReportFilter {
  summaryDimension?: string;
}

export interface MedicationDispenseSummaryRowDto {
  dimensionKey?: string;
  summaryDimension?: string;
  dimensionName?: string;
  locationId?: string;
  medicationId?: string;
  categoryCode?: string;
  organizationId?: string;
  netQuantity?: number | string;
  /** 药品编码（仅品规汇总） */
  medicationCode?: string;
  /** 最小单位净数量（仅品规汇总） */
  minUnitQuantity?: number | string;
  /** 包装单位名（仅品规汇总；数量列拼接） */
  packageUnitName?: string;
  /** 最小单位名（仅品规汇总；最小数量列拼接） */
  minUnitName?: string;
  purchaseUnitPrice?: number | string;
  saleUnitPrice?: number | string;
  /** 进价净额 */
  purchaseNetAmount?: number | string;
  /** 售价净额 */
  saleNetAmount?: number | string;
}

export interface SummarizeMedicationDispenseReportResponse {
  rows?: MedicationDispenseSummaryRowDto[];
  totalQuantity?: number | string;
  /** 进价净额合计 */
  purchaseTotalAmount?: number | string;
  /** 售价净额合计 */
  saleTotalAmount?: number | string;
}

export interface PageMedicationDispenseReportDetailParams
  extends MedicationDispenseReportFilter,
    PageParams {}

export interface MedicationDispenseDetailRowDto {
  sourceCode?: string;
  sourceText?: string;
  businessTime?: string;
  dispenseId?: string;
  supplyRequestId?: string;
  busNo?: string;
  recordTypeCode?: string;
  recordTypeText?: string;
  pharmacyLocationId?: string;
  pharmacyLocationName?: string;
  dispenserPractitionerId?: string;
  dispenserName?: string;
  patientId?: string;
  patientName?: string;
  encounterId?: string;
  /** 门诊号 */
  encounterBusNo?: string;
  /** 住院号 */
  inpatientNo?: string;
  wardName?: string;
  bedNo?: string;
  prescriptionNo?: string;
  medicationId?: string;
  medicationCode?: string;
  medicationName?: string;
  medicationSpec?: string;
  quantity?: number | string;
  unitCode?: string;
  unitText?: string;
  purchaseUnitPrice?: number | string;
  purchaseAmount?: number | string;
  saleUnitPrice?: number | string;
  saleAmount?: number | string;
  lotNumber?: string;
  /** 收费锚点批号（账批质检） */
  chargeLotNumber?: string;
  /** 账批≠实发批（仅质检标红；金额口径不变） */
  lotMismatch?: boolean;
  traceNo?: string;
  departmentId?: string;
  departmentName?: string;
  usageText?: string;
  manufacturerText?: string;
  medReqId?: string;
  feeNatureContractNo?: string;
  feeNatureDisplay?: string;
  medType?: string;
  medTypeDisplay?: string;
}

export interface PageMedicationDispenseReportDetailResponse {
  content?: MedicationDispenseDetailRowDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

export interface ExportMedicationDispenseReportParams extends MedicationDispenseReportFilter {
  presentationMode: MedicationDispensePresentationMode;
  summaryDimension?: string;
  /** 页面列清单；后端按 field 白名单裁剪写出 */
  exportColumns?: ExcelExportColumnPayload[];
}

export type ExportMedicationDispenseReportResponse = BinaryExportResult;

/** 解析门诊/住院 AppService 基路径。 */
function resolveBase(scope: MedicationDispenseReportScope) {
  if (scope === 'inpatient') {
    return INPATIENT_BASE;
  }
  return OUTPATIENT_BASE;
}

/** 加载发药报表页筛选项与默认日期。 */
export async function loadMedicationDispenseReportPageInitApi(
  scope: MedicationDispenseReportScope,
) {
  const base = resolveBase(scope);
  const method =
    scope === 'inpatient'
      ? 'loadInpatientMedicationDispenseReportPageInit'
      : 'loadOutpatientMedicationDispenseReportPageInit';
  return reportPost<LoadMedicationDispenseReportPageInitResponse>(`${base}/${method}`, {});
}

/** 按药房/品规/类型/科室净汇总（跨多日常超过 Axios 默认 10s）。 */
export async function summarizeMedicationDispenseReportApi(
  scope: MedicationDispenseReportScope,
  body: SummarizeMedicationDispenseReportParams,
) {
  const base = resolveBase(scope);
  const method =
    scope === 'inpatient'
      ? 'summarizeInpatientMedicationDispenseReport'
      : 'summarizeOutpatientMedicationDispenseReport';
  return reportPost<SummarizeMedicationDispenseReportResponse>(`${base}/${method}`, body);
}

/** 明细分页（患者发退药 ∪ 科室领用/退领）。 */
export async function pageMedicationDispenseReportDetailApi(
  scope: MedicationDispenseReportScope,
  body: PageMedicationDispenseReportDetailParams,
) {
  const base = resolveBase(scope);
  const method =
    scope === 'inpatient'
      ? 'pageInpatientMedicationDispenseReportDetail'
      : 'pageOutpatientMedicationDispenseReportDetail';
  return reportPost<PageMedicationDispenseReportDetailResponse>(`${base}/${method}`, body);
}

/** 导出当前 Tab（汇总 / 明细）xlsx。 */
export async function exportMedicationDispenseReportApi(
  scope: MedicationDispenseReportScope,
  body: ExportMedicationDispenseReportParams,
) {
  const base = resolveBase(scope);
  const method =
    scope === 'inpatient'
      ? 'exportInpatientMedicationDispenseReport'
      : 'exportOutpatientMedicationDispenseReport';
  return reportPost<ExportMedicationDispenseReportResponse>(`${base}/${method}`, body);
}
