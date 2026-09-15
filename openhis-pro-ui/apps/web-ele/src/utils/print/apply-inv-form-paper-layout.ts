import {
  A4_PAPER_HEIGHT_MM,
  A4_PAPER_WIDTH_MM,
  clampFormPaperHeightMm,
  INV_FORM_PAPER_KIND,
  type InvFormPaperKind,
} from './form-print-preference';

/** hiprint 元素 left/top/width/height 为 pt；panel.width/height 为 mm */
const PT_PER_MM = 72 / 25.4;
/** 签批与纸底、表格与签批的间距 */
const EDGE_GAP_PT = 4 * PT_PER_MM;
/** 表格过矮时仍保留的最小高度（约两行） */
const MIN_TABLE_HEIGHT_PT = 40;
/** 判定「在表格设计框下方」的容差 */
const BELOW_TABLE_SLACK_PT = 8;
/** 院名/单据名：落在 paperHeader 之上才会每页重复，续页表也从这条线起排 */
const TITLE_REPEAT_FIELDS = new Set(['hospitalName', 'documentTitle']);
/** 找不到标题时续页至少留的顶距（约 6mm），避免贴顶被打印机裁表头 */
const MIN_CONTINUATION_HEADER_PT = 18;
/** 标题底边到页眉线的间隙 */
const TITLE_HEADER_GAP_PT = 4;
/** 表头行高过矮时中文列名会裁切 */
const MIN_TABLE_HEADER_ROW_HEIGHT_PT = 24;

interface PrintElementLike {
  options?: Record<string, unknown>;
  printElementType?: { type?: string };
}

interface InvDocumentPaperLayoutOptions {
  paperHeightMm: number;
  paperWidthMm?: number;
  paperType: 'A4' | 'other';
  paperNumberDisabled: boolean;
}

/** 读取元素 options，没有则建一个以便回写 */
function ensureOptions(element: PrintElementLike): Record<string, unknown> {
  if (!element.options || typeof element.options !== 'object') {
    element.options = {};
  }
  return element.options;
}

function readNumber(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function isTableElement(element: PrintElementLike): boolean {
  return element.printElementType?.type === 'table';
}

/**
 * 续页 paperHeader：院名+单据名重复，明细表从该线起排，避免第二页表头贴顶被裁。
 * 页眉线必须低于表格 top，否则整表会被当成页眉每页重复。
 */
function resolveInvDocumentPaperHeaderPt(elements: unknown[], tableTopPt: number): number {
  let titleBottomPt = 0;
  for (const raw of elements) {
    if (!raw || typeof raw !== 'object') {
      continue;
    }
    const options = (raw as PrintElementLike).options;
    if (!options || typeof options !== 'object') {
      continue;
    }
    const field = String(options.field ?? '').trim();
    if (!TITLE_REPEAT_FIELDS.has(field)) {
      continue;
    }
    const top = readNumber(options.top);
    const height = Math.max(readNumber(options.height), 12);
    titleBottomPt = Math.max(titleBottomPt, top + height);
  }
  let headerPt =
    titleBottomPt > 0 ? titleBottomPt + TITLE_HEADER_GAP_PT : MIN_CONTINUATION_HEADER_PT;
  const maxSafeHeaderPt = Math.max(0, tableTopPt - BELOW_TABLE_SLACK_PT);
  if (maxSafeHeaderPt <= 0) {
    return 0;
  }
  if (headerPt > maxSafeHeaderPt) {
    headerPt = maxSafeHeaderPt;
  }
  return headerPt;
}

/** 续页重复表头，并保证表头行高够印中文列名 */
function patchInvDocumentTableHeader(tableOptions: Record<string, unknown>): void {
  tableOptions.tableHeaderRepeat = 'page';
  const headerRowHeight = readNumber(tableOptions.tableHeaderRowHeight);
  if (headerRowHeight < MIN_TABLE_HEADER_ROW_HEIGHT_PT) {
    tableOptions.tableHeaderRowHeight = MIN_TABLE_HEADER_ROW_HEIGHT_PT;
  }
}

/**
 * 按纸高锁定一页：签批钉在纸底，表格吃掉中间剩余高度。
 * 入参为 prepare 后的深拷贝，可就地改。
 */
function applyInvDocumentPaperLayout(
  printJson: Record<string, unknown>,
  layout: InvDocumentPaperLayoutOptions,
): Record<string, unknown> {
  const heightMm = layout.paperHeightMm;
  const panels = printJson.panels;
  if (!Array.isArray(panels) || panels.length === 0) {
    return printJson;
  }
  const panel = panels[0];
  if (!panel || typeof panel !== 'object') {
    return printJson;
  }
  const panelObj = panel as Record<string, unknown>;
  const pageHeightPt = heightMm * PT_PER_MM;

  panelObj.height = heightMm;
  if (typeof layout.paperWidthMm === 'number' && layout.paperWidthMm > 0) {
    panelObj.width = layout.paperWidthMm;
  }
  panelObj.paperType = layout.paperType;
  panelObj.paperFooter = pageHeightPt;
  panelObj.paperNumberDisabled = layout.paperNumberDisabled;
  delete panelObj.panelPageRule;

  const elements = panelObj.printElements;
  if (!Array.isArray(elements)) {
    panelObj.paperHeader = MIN_CONTINUATION_HEADER_PT;
    return printJson;
  }

  // 主表：取设计框底边最低的 table（单据只有一张明细表）
  let tableElement: PrintElementLike | undefined;
  let tableBottomPt = 0;
  for (const raw of elements) {
    if (!raw || typeof raw !== 'object') {
      continue;
    }
    const element = raw as PrintElementLike;
    if (!isTableElement(element)) {
      continue;
    }
    const options = ensureOptions(element);
    const top = readNumber(options.top);
    const height = readNumber(options.height);
    const bottom = top + height;
    if (!tableElement || bottom > tableBottomPt) {
      tableElement = element;
      tableBottomPt = bottom;
    }
  }

  if (!tableElement) {
    panelObj.paperHeader = MIN_CONTINUATION_HEADER_PT;
    return printJson;
  }

  const tableOptions = ensureOptions(tableElement);
  const tableTopPt = readNumber(tableOptions.top);
  panelObj.paperHeader = resolveInvDocumentPaperHeaderPt(elements, tableTopPt);
  patchInvDocumentTableHeader(tableOptions);
  const footerElements: PrintElementLike[] = [];
  for (const raw of elements) {
    if (!raw || typeof raw !== 'object') {
      continue;
    }
    const element = raw as PrintElementLike;
    if (element === tableElement) {
      continue;
    }
    const top = readNumber(ensureOptions(element).top);
    // 设计稿里签批/合计都在表格框下方
    if (top >= tableBottomPt - BELOW_TABLE_SLACK_PT) {
      footerElements.push(element);
    }
  }

  if (footerElements.length === 0) {
    const maxTableHeight = Math.max(MIN_TABLE_HEIGHT_PT, pageHeightPt - tableTopPt - EDGE_GAP_PT);
    tableOptions.height = maxTableHeight;
    return printJson;
  }

  let footerMinTop = Number.POSITIVE_INFINITY;
  let footerMaxBottom = 0;
  for (const element of footerElements) {
    const options = ensureOptions(element);
    const top = readNumber(options.top);
    const height = Math.max(readNumber(options.height), 12);
    footerMinTop = Math.min(footerMinTop, top);
    footerMaxBottom = Math.max(footerMaxBottom, top + height);
  }
  const footerSpanPt = Math.max(footerMaxBottom - footerMinTop, 12);

  const minFooterTop = tableTopPt + MIN_TABLE_HEIGHT_PT + EDGE_GAP_PT;
  let newFooterMinTop = pageHeightPt - EDGE_GAP_PT - footerSpanPt;
  if (newFooterMinTop < minFooterTop) {
    newFooterMinTop = minFooterTop;
  }
  const delta = newFooterMinTop - footerMinTop;

  // 整块签批平移到纸底，避免跟在表尾越过页边
  for (const element of footerElements) {
    const options = ensureOptions(element);
    options.top = readNumber(options.top) + delta;
  }

  tableOptions.height = Math.max(MIN_TABLE_HEIGHT_PT, newFooterMinTop - EDGE_GAP_PT - tableTopPt);

  return printJson;
}

/**
 * 针式联单：按用户纸高锁定一折，签批钉在纸底，表格吃掉中间剩余高度。
 * 入参为 prepare 后的深拷贝，可就地改。
 */
export function applyInvFormPaperLayout(
  printJson: Record<string, unknown>,
  paperHeightMm: number,
): Record<string, unknown> {
  return applyInvDocumentPaperLayout(printJson, {
    paperHeightMm: clampFormPaperHeightMm(paperHeightMm),
    paperType: 'other',
    paperNumberDisabled: true,
  });
}

/**
 * A4 纵向：锁 210×297，签批钉在页底，表格吃掉中间剩余高度。
 * 入参为 prepare 后的深拷贝，可就地改。
 */
export function applyInvA4PaperLayout(printJson: Record<string, unknown>): Record<string, unknown> {
  return applyInvDocumentPaperLayout(printJson, {
    paperHeightMm: A4_PAPER_HEIGHT_MM,
    paperWidthMm: A4_PAPER_WIDTH_MM,
    paperType: 'A4',
    paperNumberDisabled: false,
  });
}

/** 按纸张类型套库房单据版式（三联单一折 / A4 整页） */
export function applyInvDocumentPaperByKind(
  printJson: Record<string, unknown>,
  paperKind: InvFormPaperKind,
  paperHeightMm: number,
): Record<string, unknown> {
  if (paperKind === INV_FORM_PAPER_KIND.a4) {
    return applyInvA4PaperLayout(printJson);
  }
  return applyInvFormPaperLayout(printJson, paperHeightMm);
}
