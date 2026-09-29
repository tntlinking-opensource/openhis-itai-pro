import type { ReportPrintColumnDef, ReportPrintSummaryItem } from '#/utils/print/print-report-list';

import type { Ref } from 'vue';

import { ref } from 'vue';

import { ElMessage } from 'element-plus';

import {
  getReportPrintColumnPreference,
  normalizeReportPrintScope,
  printReportList,
} from '#/utils/print/print-report-list';

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
  /**
   * 跨页打印全部：直调分页接口拉全量并映射为打印行，不改当前列表。
   * 汇总已一次返回全量时，直接映射当前行即可。
   */
  fetchAllPrintRows?: () => Promise<ReportPrintAllFetchResult>;
  /** 跨页打印前是否已有数据（通常看 total 或当前行）。不传则用当前页行数。 */
  hasPrintableData?: () => boolean;
}

/** 跨页打印每次分页条数；直调分页接口，不改列表。 */
export const REPORT_PRINT_ALL_PAGE_SIZE = 1000;

/** 跨页打印行数上限，与库存明细打印 cap 对齐。 */
export const REPORT_PRINT_ALL_MAX_ROWS = 30000;

export interface ReportPrintAllFetchResult {
  rows: Record<string, string>[];
  truncated: boolean;
}

/**
 * 按当前筛选分页拉全量源行，触达上限则截断。
 * total 为 0 时只按本页是否还有数据续拉，避免接口未回总数时提前停。
 */
export async function collectPagedReportRows<T>(options: {
  fetchPage: (pageNo: number, pageSize: number) => Promise<{ rows: T[]; total: number }>;
}): Promise<{ rows: T[]; truncated: boolean }> {
  const collected: T[] = [];
  let pageNo = 1;
  let truncated = false;
  const maxPages = Math.ceil(REPORT_PRINT_ALL_MAX_ROWS / REPORT_PRINT_ALL_PAGE_SIZE);
  while (pageNo <= maxPages && collected.length < REPORT_PRINT_ALL_MAX_ROWS) {
    const remaining = REPORT_PRINT_ALL_MAX_ROWS - collected.length;
    const pageSize = Math.min(REPORT_PRINT_ALL_PAGE_SIZE, remaining);
    const page = await options.fetchPage(pageNo, pageSize);
    const batch = page.rows ?? [];
    if (batch.length === 0) {
      break;
    }
    const room = REPORT_PRINT_ALL_MAX_ROWS - collected.length;
    const take = batch.slice(0, room);
    collected.push(...take);
    const total = Number(page.total ?? 0);
    if (take.length < batch.length || collected.length >= REPORT_PRINT_ALL_MAX_ROWS) {
      truncated = total <= 0 || total > collected.length;
      break;
    }
    if (batch.length < pageSize || (total > 0 && collected.length >= total)) {
      truncated = total > collected.length;
      break;
    }
    pageNo += 1;
  }
  return { rows: collected, truncated };
}

/**
 * 报表列表通用打印：打印设置（跨页全部 / 仅本页）+ 按范围提交。
 * 各报表页提供列目录、当前页映射，以及跨页全量拉取。
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

  /** 按打印设置范围提交：跨页全部直拉全量，仅本页用当前列表行。 */
  async function handlePrintCurrentPage() {
    const printScope = normalizeReportPrintScope(
      getReportPrintColumnPreference(options.getReportCode())?.printScope,
    );
    const printAll = printScope === 'all' && Boolean(options.fetchAllPrintRows);
    if (printAll) {
      const hasData = options.hasPrintableData
        ? options.hasPrintableData()
        : options.getPrintRows().length > 0;
      if (!hasData) {
        ElMessage.warning('当前无可打印数据');
        return;
      }
    } else if (options.getPrintRows().length === 0) {
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
      const fetched = printAll
        ? await options.fetchAllPrintRows!()
        : { rows: options.getPrintRows(), truncated: false };
      if (fetched.rows.length === 0) {
        ElMessage.warning('当前无可打印数据');
        return;
      }
      const summaryItems = options.getSummaryItems ? await options.getSummaryItems() : undefined;
      const result = await printReportList({
        reportCode: options.getReportCode(),
        columnCatalog,
        reportTitle,
        rows: fetched.rows,
        jobName: reportTitle,
        summaryItems,
      });
      if (result.ok) {
        if (fetched.truncated) {
          ElMessage.warning(`打印数据已触达 ${REPORT_PRINT_ALL_MAX_ROWS} 行上限，部分记录未包含`);
        }
        const scopeText = printAll
          ? `共 ${fetched.rows.length} 行`
          : `本页 ${fetched.rows.length} 行`;
        ElMessage.success(`已提交打印（${scopeText}）`);
      }
    } catch {
      // 合计或分页拉取失败时已由拦截器提示，不再提交打印
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
