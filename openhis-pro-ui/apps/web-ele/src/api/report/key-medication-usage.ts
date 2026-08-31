/**
 * 重点药品使用统计 — POST /api/app/keyMedicationUsageReport/{methodName}
 */
import { reportPost } from '#/constants/report-request-timeout';
import type { ApiOptionItem } from '#/types/api-response';
import type { ExcelExportColumnPayload } from '#/utils/export/build-excel-export-columns';
import type { BinaryExportResult } from '#/utils/export/download-binary-export';

const BASE = '/keyMedicationUsageReport';

/** 汇总筛选。 */
export interface KeyMedicationUsageQueryFilter {
  dateFrom: string;
  dateTo: string;
  encounterClassCodes?: string[];
  keyDrugTypeCodes?: string[];
}

/** 页初始化响应。 */
export interface LoadKeyMedicationUsageReportPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  encounterClassOptions?: ApiOptionItem[];
  keyDrugTypeOptions?: ApiOptionItem[];
}

/** 汇总行。 */
export interface KeyMedicationUsageSummaryRowDto {
  encounterClassCode?: string;
  encounterClassDisplay?: string;
  keyDrugTypeCode?: string;
  keyDrugTypeDisplay?: string;
  encounterNetCount?: number;
  saleAmountTotal?: number | string;
}

/** 汇总响应。 */
export interface SummarizeKeyMedicationUsageReportResponse {
  rows?: KeyMedicationUsageSummaryRowDto[];
  totalEncounterNetCount?: number;
  totalSaleAmount?: number | string;
}

/** 加载页初始化。 */
export async function loadKeyMedicationUsageReportPageInitApi() {
  return reportPost<LoadKeyMedicationUsageReportPageInitResponse>(
    `${BASE}/loadKeyMedicationUsageReportPageInit`,
    {},
  );
}

/** 汇总查询。 */
export async function summarizeKeyMedicationUsageReportApi(params: KeyMedicationUsageQueryFilter) {
  return reportPost<SummarizeKeyMedicationUsageReportResponse>(
    `${BASE}/summarizeKeyMedicationUsageReport`,
    params,
  );
}

/** 导出请求：筛选 + 可选列清单。 */
export interface ExportKeyMedicationUsageReportRequest extends KeyMedicationUsageQueryFilter {
  /** 页面可见列 → 后端 Excel 列裁剪 */
  exportColumns?: ExcelExportColumnPayload[];
}

/** 导出 Excel。 */
export async function exportKeyMedicationUsageReportApi(
  params: ExportKeyMedicationUsageReportRequest,
) {
  return reportPost<BinaryExportResult>(`${BASE}/exportKeyMedicationUsageReport`, params);
}
