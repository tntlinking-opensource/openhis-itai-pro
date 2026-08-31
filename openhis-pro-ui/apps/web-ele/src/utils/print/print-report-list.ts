import type { PrintResult } from './runtime/types';
import type {
  ReportListPrintData,
  ReportPrintColumnDef,
  ReportPrintSummaryItem,
} from './report/report-list-print-utils';
import type { ReportPrintOrientation } from './report/report-print-column-pref';

import { ElMessage } from 'element-plus';

import { PRINT_SCENARIO } from './constants/print-scenario';
import {
  buildReportTableColumns,
  normalizeReportPrintSummaryItems,
  patchReportListPrintTemplate,
  resolveReportListPaperLayout,
  resolveSelectedReportColumns,
} from './report/report-list-print-utils';
import {
  getReportPrintColumnPreference,
  normalizeReportPrintOrientation,
  saveReportPrintColumnPreference,
} from './report/report-print-column-pref';
import { resolvePrintHospitalName } from './resolve-print-hospital-name';
import { print } from './runtime';
import { PRINT_ERROR_CODE } from './runtime/types';

export interface PrintReportListOptions {
  /** 报表稳定编码，用于本机记住字段勾选与方向 */
  reportCode: string;
  /** 本报表可选列目录 */
  columnCatalog: ReportPrintColumnDef[];
  /** 覆盖已存偏好；不传则读 localStorage / 默认全选 */
  selectedFields?: string[];
  /** 覆盖已存方向；不传则读 localStorage / 默认横向 */
  orientation?: ReportPrintOrientation;
  reportTitle: string;
  /** 已格式化为展示串的行（键=字段名） */
  rows: Record<string, string>[];
  hospitalName?: string;
  jobName?: string;
  /** 保存本次勾选与方向（默认 true） */
  persistFields?: boolean;
  /**
   * 打印末尾全量合计（进价合计/售价合计等）。
   * 页面没有或值为空则不传；工具层会再过滤空项，不注入表尾。
   */
  summaryItems?: ReportPrintSummaryItem[];
}

/** 组装通用报表 PrintData */
export function buildReportListPrintData(input: {
  hospitalName?: string;
  reportTitle: string;
  rows: Array<Record<string, unknown>>;
  summaryItems?: ReportPrintSummaryItem[];
}): ReportListPrintData {
  const summaryItems = normalizeReportPrintSummaryItems(input.summaryItems);
  const printData: ReportListPrintData = {
    hospitalName: (input.hospitalName ?? resolvePrintHospitalName()).trim() || '医院',
    reportTitle: input.reportTitle.trim() || '报表',
    rows: input.rows,
  };
  if (summaryItems.length > 0) {
    printData.summaryItems = summaryItems;
  }
  return printData;
}

/** 通用报表列表打印：读偏好 → 方向/动态列 → prepare/hiprint */
export async function printReportList(options: PrintReportListOptions): Promise<PrintResult> {
  if (!options.reportCode.trim()) {
    ElMessage.error('reportCode 不能为空');
    return {
      ok: false,
      errorCode: PRINT_ERROR_CODE.INVALID_OPTIONS,
      errorMessage: 'reportCode 不能为空',
    };
  }
  if (!options.columnCatalog.length) {
    ElMessage.error('未配置可打印字段');
    return {
      ok: false,
      errorCode: PRINT_ERROR_CODE.INVALID_OPTIONS,
      errorMessage: '未配置可打印字段',
    };
  }
  if (!options.rows.length) {
    ElMessage.warning('当前无可打印数据');
    return {
      ok: false,
      errorCode: PRINT_ERROR_CODE.INVALID_OPTIONS,
      errorMessage: '当前无可打印数据',
    };
  }

  const pref = getReportPrintColumnPreference(options.reportCode);
  const prefFields = options.selectedFields ?? pref?.fields ?? null;
  const orientation = normalizeReportPrintOrientation(
    options.orientation ?? pref?.orientation ?? 'landscape',
  );
  const selectedColumns = resolveSelectedReportColumns(options.columnCatalog, prefFields);
  const selectedFields = selectedColumns.map((col) => col.field);
  if (options.persistFields !== false) {
    saveReportPrintColumnPreference(options.reportCode, {
      fields: selectedFields,
      orientation,
      printScope: pref?.printScope,
    });
  }

  const layout = resolveReportListPaperLayout(orientation);
  const tableColumns = buildReportTableColumns(selectedColumns, layout.contentWidthPt);
  const rowsWithSeq: Array<Record<string, unknown>> = options.rows.map((row, index) => ({
    ...row,
    seqNo: String(index + 1),
  }));
  const printData = buildReportListPrintData({
    hospitalName: options.hospitalName,
    reportTitle: options.reportTitle,
    rows: rowsWithSeq,
    summaryItems: options.summaryItems,
  });
  const hasSummary = (printData.summaryItems?.length ?? 0) > 0;

  return print({
    scenarioCode: PRINT_SCENARIO.reportListPrint,
    scenarioLabel: options.reportTitle,
    printData,
    jobName: options.jobName ?? options.reportTitle,
    bizRef: {
      reportCode: options.reportCode,
      orientation,
      fieldCount: selectedFields.length,
      rowCount: options.rows.length,
    },
    transformPrintJson: (json) =>
      patchReportListPrintTemplate(json, tableColumns, orientation, {
        injectSummary: hasSummary,
      }),
  });
}

export type {
  ReportListPrintData,
  ReportPrintColumnDef,
  ReportPrintSummaryItem,
} from './report/report-list-print-utils';
export type { ReportPrintOrientation, ReportPrintScope } from './report/report-print-column-pref';
export {
  getReportPrintColumnPreference,
  normalizeReportPrintOrientation,
  normalizeReportPrintScope,
  saveReportPrintColumnPreference,
} from './report/report-print-column-pref';
