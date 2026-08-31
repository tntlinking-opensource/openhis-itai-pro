import type { ReportPrintColumnDef, ReportPrintSummaryItem } from '#/utils/print/print-report-list';

import type { Ref } from 'vue';

import { ref } from 'vue';

import { ElMessage } from 'element-plus';

import { printReportList } from '#/utils/print/print-report-list';

import type ReportPrintFieldDialog from './components/report-print-field-dialog.vue';

export interface UseReportListPrintOptions {
  /** 本机记住字段偏好的稳定键 */
  getReportCode: () => string;
  /** 当前视图可打印列（随汇总/明细切换可变） */
  getColumnCatalog: () => ReportPrintColumnDef[];
  /** 打印标题（医院抬头下单据名） */
  getReportTitle: () => string;
  /** 当前页已格式化为展示串的行 */
  getPrintRows: () => Record<string, string>[];
  /**
   * 页面有进价/售价等全量合计时再提供；不传或空则不打印表尾合计。
   */
  getSummaryItems?: () =>
    | Promise<ReportPrintSummaryItem[] | undefined>
    | ReportPrintSummaryItem[]
    | undefined;
}

/**
 * 报表列表通用打印：打印设置弹窗 + 打当前页。
 * 各报表页只提供列目录与行映射即可。
 */
export function useReportListPrint(options: UseReportListPrintOptions): {
  printFieldDialogRef: Ref<InstanceType<typeof ReportPrintFieldDialog> | null>;
  printSubmitting: Ref<boolean>;
  handleOpenPrintFields: () => void;
  handlePrintCurrentPage: () => Promise<void>;
} {
  const printFieldDialogRef = ref<InstanceType<typeof ReportPrintFieldDialog> | null>(null);
  const printSubmitting = ref(false);

  /** 打开打印字段/方向设置 */
  function handleOpenPrintFields() {
    printFieldDialogRef.value?.open();
  }

  /** 按当前勾选字段打印本页数据 */
  async function handlePrintCurrentPage() {
    const rows = options.getPrintRows();
    if (rows.length === 0) {
      ElMessage.warning('当前无可打印数据');
      return;
    }
    const columnCatalog = options.getColumnCatalog();
    if (columnCatalog.length === 0) {
      ElMessage.error('未配置可打印字段');
      return;
    }
    const reportTitle = options.getReportTitle().trim() || '报表';
    printSubmitting.value = true;
    try {
      const summaryItems = options.getSummaryItems ? await options.getSummaryItems() : undefined;
      const result = await printReportList({
        reportCode: options.getReportCode(),
        columnCatalog,
        reportTitle,
        rows,
        jobName: reportTitle,
        summaryItems,
      });
      if (result.ok) {
        ElMessage.success(`已提交打印（本页 ${rows.length} 行）`);
      }
    } catch {
      // 合计拉取失败时已由 useRequest 提示，不再提交打印
    } finally {
      printSubmitting.value = false;
    }
  }

  return {
    printFieldDialogRef,
    printSubmitting,
    handleOpenPrintFields,
    handlePrintCurrentPage,
  };
}

/** 将行对象按字段清单转成打印单元格文案（缺省空串） */
export function mapReportPrintRow(
  row: Record<string, unknown>,
  fields: string[],
  formatters?: Partial<Record<string, (value: unknown, row: Record<string, unknown>) => string>>,
): Record<string, string> {
  const out: Record<string, string> = {};
  for (const field of fields) {
    const formatter = formatters?.[field];
    if (formatter) {
      out[field] = formatter(row[field], row);
      continue;
    }
    const raw = row[field];
    if (raw === null || raw === undefined) {
      out[field] = '';
      continue;
    }
    out[field] = String(raw);
  }
  return out;
}

/** 从列目录取 field 列表 */
export function reportPrintFieldsOf(catalog: ReportPrintColumnDef[]): string[] {
  return catalog.map((col) => col.field);
}
