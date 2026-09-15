/**
 * 药品结余留用报表 — AS-RPT-015
 * POST /api/app/surplusRetentionReport/{methodName}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { ApiOptionItem, PageParams } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

const BASE = '/surplusRetentionReport';

/** 页初始化响应。 */
export interface LoadSurplusRetentionReportPageInitResponse {
  defaultAuthoredDateFrom?: string;
  defaultAuthoredDateTo?: string;
  defaultPageSize?: number;
  surplusRetentionBatchCodeOptions?: ApiOptionItem[];
}

/** 明细分页行（对齐《患者明细主表》19 列）。 */
export interface SurplusRetentionReportRowDto {
  outpatientInpatientNo?: string;
  patientName?: string;
  insuredRegion?: string;
  insuredType?: string;
  admissionTime?: string;
  dischargeTime?: string;
  prescriptionDate?: string;
  drugGenericName?: string;
  productName?: string;
  drugCategoryText?: string;
  doseFormText?: string;
  specification?: string;
  packageUnitText?: string;
  pricingUnitText?: string;
  selectedProductText?: string;
  manufacturerName?: string;
  unitPrice?: number | string;
  usageQuantity?: number | string;
  usageAmount?: number | string;
}

/** 查询筛选（批次 + 签发时间窗）。 */
export interface SurplusRetentionQueryFilter {
  surplusRetentionBatchCode: string;
  authoredDateFrom: string;
  authoredDateTo: string;
}

/** 明细分页请求。 */
export interface PageSurplusRetentionReportRequest
  extends SurplusRetentionQueryFilter,
    PageParams {}

/** 明细分页响应。 */
export interface PageSurplusRetentionReportResponse {
  content?: SurplusRetentionReportRowDto[];
  totalCount?: number;
  pageNo?: number;
  pageSize?: number;
}

/** 导出请求。 */
export interface ExportSurplusRetentionReportRequest extends SurplusRetentionQueryFilter {
  pageNo?: number;
  pageSize?: number;
  exportColumns?: ExcelExportColumnPayload[];
}

/** 导出响应（二进制 JSON 包装）。 */
export type ExportSurplusRetentionReportResponse = BinaryExportResult;

/**
 * 页初始化：默认当月签发窗、批次下拉。
 */
export async function loadSurplusRetentionReportPageInitApi() {
  return reportPost<LoadSurplusRetentionReportPageInitResponse>(
    `${BASE}/loadSurplusRetentionReportPageInit`,
    {},
  );
}

/**
 * 明细分页。
 */
export async function pageSurplusRetentionReportApi(params: PageSurplusRetentionReportRequest) {
  return reportPost<PageSurplusRetentionReportResponse>(
    `${BASE}/pageSurplusRetentionReport`,
    params,
  );
}

/**
 * 导出当前筛选 Excel。
 */
export async function exportSurplusRetentionReportApi(params: ExportSurplusRetentionReportRequest) {
  return reportPost<ExportSurplusRetentionReportResponse>(
    `${BASE}/exportSurplusRetentionReport`,
    params,
    { responseType: 'json' },
  );
}
