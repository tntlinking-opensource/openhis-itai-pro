import type { ReportPrintOrientation } from './report-print-column-pref';

/** 报表打印列定义（页面声明；用户勾选子集后注入模板 table.columns） */
export interface ReportPrintColumnDef {
  field: string;
  title: string;
  /** 单元格对齐；费用/数量建议 right */
  align?: 'center' | 'left' | 'right';
  /**
   * @deprecated 首次无偏好时一律默认全选；保留字段仅兼容旧声明
   */
  defaultSelected?: boolean;
  /** 相对列宽；打印时按总和等比分配到表格可用宽度 */
  widthWeight?: number;
}

/** hiprint table 单列配置 */
export interface ReportPrintTableColumn {
  title: string;
  field: string;
  width: number;
  align: 'center' | 'left' | 'right';
  halign: 'center';
  colspan: 1;
  rowspan: 1;
  /** hiprint 渲染时只输出 checked 列 */
  checked: true;
}

/** 报表打印末尾合计（页面有才传；无标签或无金额则不印） */
export interface ReportPrintSummaryItem {
  label: string;
  value: string;
}

/** 通用报表列表 PrintData */
export interface ReportListPrintData {
  hospitalName: string;
  reportTitle: string;
  rows: Array<Record<string, unknown>>;
  /** 全量合计；缺省或空数组则模板不渲染 */
  summaryItems?: ReportPrintSummaryItem[];
}

/** 去掉无标签或无金额的合计项，页面没有则不进打印。 */
export function normalizeReportPrintSummaryItems(
  items: ReportPrintSummaryItem[] | undefined,
): ReportPrintSummaryItem[] {
  if (!items || items.length === 0) {
    return [];
  }
  const result: ReportPrintSummaryItem[] = [];
  for (const item of items) {
    const label = String(item.label ?? '').trim();
    const value = String(item.value ?? '').trim();
    if (!label || !value) {
      continue;
    }
    result.push({ label, value });
  }
  return result;
}

/** hiprint 表尾合计：仅最后一页，空项跳过。 */
function buildReportListSummaryFooterFormatter(colCount: number): string {
  const span = Math.max(1, colCount);
  return [
    'function(options,rows,data){',
    'var items=data&&data.summaryItems?data.summaryItems:[];',
    'var parts=[];',
    'for(var i=0;i<items.length;i++){',
    'var it=items[i];if(!it)continue;',
    'var label=String(it.label||"").trim();',
    'var val=String(it.value||"").trim();',
    'if(!label||!val)continue;',
    'parts.push(label+"  "+val);',
    '}',
    'if(!parts.length)return "";',
    `return '<tr><td colspan="${span}" style="text-align:right;padding:6px 8px;font-size:10.5pt;font-weight:bold;">'+parts.join('&nbsp;&nbsp;&nbsp;&nbsp;')+'</td></tr>';`,
    '}',
  ].join('');
}

/**
 * A4 打印方向版心（纸张始终是 A4；横向/纵向仅改内容方向）。
 * panel 宽>高 → Electron landscape=true；宽<高 → 纵向。
 */
export interface ReportListPaperLayout {
  orientation: ReportPrintOrientation;
  /** 设计宽（mm）：横向 297 / 纵向 210 */
  panelWidthMm: number;
  /** 设计高（mm）：横向 210 / 纵向 297 */
  panelHeightMm: number;
  /** 固定 A4，禁止 other，避免被当成自定义纸 */
  paperType: 'A4';
  /**
   * 页眉线（pt）：之上的院名/单据名每页重复。
   * 须大于标题元素底边，且 ≤ 表格 top。
   */
  paperHeaderPt: number;
  paperFooter: number;
  contentLeftPt: number;
  contentWidthPt: number;
  /** 明细表 top（pt），与 paperHeader 对齐或略下 */
  tableTopPt: number;
}

/** 序号列固定宽度（pt），不参与用户勾选 */
export const REPORT_LIST_SEQ_COLUMN_WIDTH_PT = 40;

/** 横向打印：内容沿 A4 长边展开（297×210 设计框） */
export const REPORT_LIST_PAPER_LANDSCAPE: ReportListPaperLayout = {
  orientation: 'landscape',
  panelWidthMm: 297,
  panelHeightMm: 210,
  paperType: 'A4',
  paperHeaderPt: 54,
  paperFooter: 580,
  contentLeftPt: 15,
  contentWidthPt: 810,
  tableTopPt: 54,
};

/** 纵向打印：内容沿 A4 短边展开（210×297 设计框） */
export const REPORT_LIST_PAPER_PORTRAIT: ReportListPaperLayout = {
  orientation: 'portrait',
  panelWidthMm: 210,
  panelHeightMm: 297,
  paperType: 'A4',
  paperHeaderPt: 54,
  paperFooter: 848,
  contentLeftPt: 15,
  contentWidthPt: 550,
  tableTopPt: 54,
};

/** 按方向取版心 */
export function resolveReportListPaperLayout(
  orientation: ReportPrintOrientation,
): ReportListPaperLayout {
  return orientation === 'portrait' ? REPORT_LIST_PAPER_PORTRAIT : REPORT_LIST_PAPER_LANDSCAPE;
}

/** 表格可用宽度（默认横向） */
export const REPORT_LIST_TABLE_WIDTH_PT = REPORT_LIST_PAPER_LANDSCAPE.contentWidthPt;

/** 按用户勾选字段解析列定义；无效字段忽略，全空则默认全选 */
export function resolveSelectedReportColumns(
  catalog: ReportPrintColumnDef[],
  selectedFields: string[] | null | undefined,
): ReportPrintColumnDef[] {
  const byField = new Map(catalog.map((col) => [col.field, col]));
  const picked: ReportPrintColumnDef[] = [];
  for (const field of selectedFields ?? []) {
    const col = byField.get(field);
    if (col) {
      picked.push(col);
    }
  }
  if (picked.length > 0) {
    return picked;
  }
  // 无有效偏好：全部列默认需要
  return [...catalog];
}

/** 列定义 → hiprint columns（自动前置序号列）；数据列按 weight 均分剩余宽度 */
export function buildReportTableColumns(
  columns: ReportPrintColumnDef[],
  tableWidthPt: number = REPORT_LIST_TABLE_WIDTH_PT,
): ReportPrintTableColumn[] {
  if (columns.length === 0) {
    return [];
  }
  const seqWidth = REPORT_LIST_SEQ_COLUMN_WIDTH_PT;
  const dataWidth = Math.max(seqWidth, tableWidthPt - seqWidth);
  // 相对权重求和，再按比例换算 pt
  let weightSum = 0;
  for (const col of columns) {
    weightSum += col.widthWeight && col.widthWeight > 0 ? col.widthWeight : 1;
  }
  const result: ReportPrintTableColumn[] = [
    {
      title: '序号',
      field: 'seqNo',
      width: seqWidth,
      align: 'center',
      halign: 'center',
      colspan: 1,
      rowspan: 1,
      checked: true,
    },
  ];
  let assigned = 0;
  for (let i = 0; i < columns.length; i += 1) {
    const col = columns[i]!;
    const weight = col.widthWeight && col.widthWeight > 0 ? col.widthWeight : 1;
    const isLast = i === columns.length - 1;
    const width = isLast
      ? Math.max(24, dataWidth - assigned)
      : Math.max(24, Math.round((dataWidth * weight) / weightSum));
    assigned += width;
    const align = col.align ?? 'center';
    result.push({
      title: col.title,
      field: col.field,
      width,
      align,
      halign: 'center',
      colspan: 1,
      rowspan: 1,
      checked: true,
    });
  }
  return result;
}

/** 按方向改写面板尺寸、页眉线与抬头/表格元素宽度（页眉内元素每页重复） */
export function applyReportListPaperOrientation(
  printJson: Record<string, unknown>,
  orientation: ReportPrintOrientation,
): ReportListPaperLayout {
  const layout = resolveReportListPaperLayout(orientation);
  const panels = printJson.panels;
  if (!Array.isArray(panels)) {
    throw new Error('打印模板缺少 panels');
  }
  for (const panel of panels) {
    if (!panel || typeof panel !== 'object') {
      continue;
    }
    const p = panel as Record<string, unknown>;
    p.width = layout.panelWidthMm;
    p.height = layout.panelHeightMm;
    p.paperType = layout.paperType;
    p.paperFooter = layout.paperFooter;
    // 院名/单据名须落在 paperHeader 之上，分页才会每页重复
    p.paperHeader = layout.paperHeaderPt;

    const elements = p.printElements;
    if (!Array.isArray(elements)) {
      continue;
    }
    for (const el of elements) {
      if (!el || typeof el !== 'object') {
        continue;
      }
      const options = (el as Record<string, unknown>).options as
        | Record<string, unknown>
        | undefined;
      if (!options) {
        continue;
      }
      const field = String(options.field ?? '');
      if (field === 'hospitalName' || field === 'reportTitle') {
        options.left = layout.contentLeftPt;
        options.width = layout.contentWidthPt;
        continue;
      }
      if (field === 'rows') {
        options.left = layout.contentLeftPt;
        options.width = layout.contentWidthPt;
        options.top = layout.tableTopPt;
        options.tableHeaderRepeat = 'page';
      }
    }
  }
  return layout;
}

export interface PatchReportListPrintTemplateOptions {
  /** 有全量合计时在表尾注入；没有则清掉模板里残留的页脚 */
  injectSummary?: boolean;
}

/**
 * 深拷贝后的 PrintJson：套方向 + 注入 field=rows 的动态列。
 */
export function patchReportListPrintTemplate(
  printJson: Record<string, unknown>,
  columns: ReportPrintTableColumn[],
  orientation: ReportPrintOrientation,
  patchOptions?: PatchReportListPrintTemplateOptions,
): Record<string, unknown> {
  if (columns.length === 0) {
    throw new Error('打印字段不能为空');
  }
  applyReportListPaperOrientation(printJson, orientation);

  const panels = printJson.panels;
  if (!Array.isArray(panels)) {
    throw new Error('打印模板缺少 panels');
  }
  const injectSummary = patchOptions?.injectSummary === true;
  let patched = false;
  for (const panel of panels) {
    if (!panel || typeof panel !== 'object') {
      continue;
    }
    const elements = (panel as Record<string, unknown>).printElements;
    if (!Array.isArray(elements)) {
      continue;
    }
    for (const el of elements) {
      if (!el || typeof el !== 'object') {
        continue;
      }
      const element = el as Record<string, unknown>;
      const options = element.options as Record<string, unknown> | undefined;
      const typeInfo = element.printElementType as Record<string, unknown> | undefined;
      const type = String(typeInfo?.type ?? '');
      if (!options || options.field !== 'rows') {
        continue;
      }
      if (type !== 'table' && type !== 'tableCustom') {
        continue;
      }
      options.columns = [columns];
      // 不启用 hiprint 单元格合并（跨页/列宽易撑破版心）
      delete options.rowsColumnsMerge;
      if (injectSummary) {
        // 仅最后一页表尾打全量合计，避免每页重复
        options.tableFooterRepeat = 'last';
        options.footerFormatter = buildReportListSummaryFooterFormatter(columns.length);
      } else {
        delete options.footerFormatter;
        delete options.tableFooterRepeat;
      }
      patched = true;
    }
  }
  if (!patched) {
    throw new Error('打印模板未找到可注入的明细表（field=rows）');
  }
  return printJson;
}
