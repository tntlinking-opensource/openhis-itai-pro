/**
 * 门诊处方抽查报表 — AS-RPT-014
 * POST /api/app/prescriptionSpotCheckReport/{methodName}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

const BASE = '/prescriptionSpotCheckReport';

/** 西药及中成药。 */
export const REPORT_TYPE_WESTERN_PATENT = 'WESTERN_PATENT' as const;
/** 中药饮片。 */
export const REPORT_TYPE_HERBAL = 'HERBAL' as const;

/** 报表类型码。 */
export type PrescriptionSpotCheckReportType =
  | typeof REPORT_TYPE_WESTERN_PATENT
  | typeof REPORT_TYPE_HERBAL;

/** 页初始化响应。 */
export interface LoadPrescriptionSpotCheckReportPageInitResponse {
  defaultReportType?: string;
  defaultOpenedDateFrom?: string;
  defaultOpenedDateTo?: string;
  defaultPageSize?: number;
  reportTypeOptions?: ApiOptionItem[];
}

/** 西药及中成药行。 */
export interface PrescriptionSpotCheckWesternRowDto {
  orgName?: string;
  orgLicenseNo?: string;
  prescriptionNo?: string;
  prescriptionAmount?: number | string;
  patientBusNo?: string;
  genderText?: string;
  birthDate?: string;
  openedAt?: string;
  visitTypeText?: string;
  deptName?: string;
  deptYbNo?: string;
  practitionerYbNo?: string;
  diagnosisName?: string;
  diagnosisCode?: string;
  drugBusNo?: string;
  drugYbNo?: string;
  drugYbName?: string;
  doseFormText?: string;
  specification?: string;
  packagingText?: string;
  methodText?: string;
  rateText?: string;
  doseQuantity?: number | string;
  doseUnitText?: string;
  dispenseQuantity?: number | string;
  dispenseUnitText?: string;
  drugOrder?: number;
}

/** 中药饮片行。 */
export interface PrescriptionSpotCheckHerbalRowDto {
  orgName?: string;
  orgLicenseNo?: string;
  prescriptionNo?: string;
  prescriptionAmount?: number | string;
  patientBusNo?: string;
  genderText?: string;
  birthDate?: string;
  openedAt?: string;
  visitTypeText?: string;
  deptName?: string;
  deptYbNo?: string;
  practitionerYbNo?: string;
  tcmDiseaseName?: string;
  tcmDiseaseCode?: string;
  tcmSyndromeName?: string;
  tcmSyndromeCode?: string;
  drugName?: string;
  drugBusNo?: string;
  drugYbNo?: string;
  doseCount?: number | string;
  prepareMethodText?: string;
  methodText?: string;
  dosePerUnit?: number | string;
  doseUnitText?: string;
  footnoteText?: string;
  drugOrder?: number;
}

/** 查询筛选（开方时间窗 + 类型）。 */
export interface PrescriptionSpotCheckQueryFilter {
  reportType: string;
  openedDateFrom: string;
  openedDateTo: string;
}

/** 明细分页请求。 */
export interface PagePrescriptionSpotCheckReportRequest
  extends PrescriptionSpotCheckQueryFilter,
    PageParams {}

/** 明细分页响应（content 留空；按类型填 western/herbal）。 */
export interface PagePrescriptionSpotCheckReportResponse {
  reportType?: string;
  westernContent?: PrescriptionSpotCheckWesternRowDto[];
  herbalContent?: PrescriptionSpotCheckHerbalRowDto[];
  content?: unknown[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

/** 导出请求。 */
export interface ExportPrescriptionSpotCheckReportRequest extends PrescriptionSpotCheckQueryFilter {
  pageNo?: number;
  pageSize?: number;
  exportColumns?: ExcelExportColumnPayload[];
}

/** 导出响应（二进制 JSON 包装）。 */
export type ExportPrescriptionSpotCheckReportResponse = BinaryExportResult;

/**
 * 页初始化：默认类型、当月开方窗、类型下拉。
 */
export async function loadPrescriptionSpotCheckReportPageInitApi() {
  return reportPost<LoadPrescriptionSpotCheckReportPageInitResponse>(
    `${BASE}/loadPrescriptionSpotCheckReportPageInit`,
    {},
  );
}

/**
 * 明细分页：按报表类型返回 westernContent 或 herbalContent。
 */
export async function pagePrescriptionSpotCheckReportApi(
  params: PagePrescriptionSpotCheckReportRequest,
) {
  return reportPost<PagePrescriptionSpotCheckReportResponse>(
    `${BASE}/pagePrescriptionSpotCheckReport`,
    params,
  );
}

/**
 * 导出当前筛选 Excel。
 */
export async function exportPrescriptionSpotCheckReportApi(
  params: ExportPrescriptionSpotCheckReportRequest,
) {
  return reportPost<ExportPrescriptionSpotCheckReportResponse>(
    `${BASE}/exportPrescriptionSpotCheckReport`,
    params,
    { responseType: 'json' },
  );
}
