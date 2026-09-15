/**
 * 重点药品使用统计 — POST /api/app/keyMedicationUsageReport/{methodName}
 *
 * 汇总按就诊类型分组：各重点药类型行 + 组内合计；
 * 人次占比=类型人次/合计人次，金额占比=类型金额/合计金额。
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
  /** 重点药类型多选；结果按类型拆行 */
  keyDrugTypeCodes?: string[];
}

/** 页初始化响应。 */
export interface LoadKeyMedicationUsageReportPageInitResponse {
  defaultDateFrom?: string;
  defaultDateTo?: string;
  encounterClassOptions?: ApiOptionItem[];
  keyDrugTypeOptions?: ApiOptionItem[];
}

/** 汇总行（就诊类型 × 药品类型；合计行 keyDrugTypeCode=TOTAL）。 */
export interface KeyMedicationUsageSummaryRowDto {
  encounterClassCode?: string;
  encounterClassDisplay?: string;
  keyDrugTypeCode?: string;
  keyDrugTypeDisplay?: string;
  encounterNetCount?: number;
  saleAmountTotal?: number | string;
  /** 人次占比：类型人次/组内合计人次 */
  usageRatioPercent?: number | string | null;
  /** 金额占比：类型金额/组内合计金额 */
  saleAmountRatioPercent?: number | string | null;
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
