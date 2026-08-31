/**
 * 退货/退库打印模板种子是「迷你版」：panelPageRule=none、表高约 40、仅 5 列。
 * 针式联打时观感明显小于流转单。打印前对齐到流转单同级版式（列+表高+纸型）。
 */

interface PrintElementLike {
  options?: Record<string, unknown>;
  printElementType?: { type?: string };
}

/** 与流转单同宽 546pt；含退货原因 */
const RETURN_TABLE_COLUMNS = [
  [
    {
      align: 'center',
      field: 'seqNo',
      fixed: false,
      title: '序号',
      width: 32,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'seqNo',
    },
    {
      align: 'left',
      field: 'productName',
      fixed: false,
      title: '品名',
      width: 100,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'productName',
    },
    {
      align: 'left',
      field: 'specText',
      fixed: false,
      title: '规格',
      width: 50,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'specText',
    },
    {
      align: 'left',
      field: 'manufacturerText',
      fixed: false,
      title: '厂家',
      width: 46,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'manufacturerText',
    },
    {
      align: 'center',
      field: 'qtyUnitText',
      fixed: false,
      title: '数量',
      width: 46,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'qtyUnitText',
    },
    {
      align: 'right',
      field: 'unitPriceText',
      fixed: false,
      title: '单价',
      width: 42,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'unitPriceText',
    },
    {
      align: 'right',
      field: 'amountText',
      fixed: false,
      title: '金额',
      width: 48,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'amountText',
    },
    {
      align: 'center',
      field: 'lotNumber',
      fixed: false,
      title: '批号',
      width: 50,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'lotNumber',
    },
    {
      align: 'center',
      field: 'expiryText',
      fixed: false,
      title: '有效期',
      width: 58,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'expiryText',
    },
    {
      align: 'left',
      field: 'reasonText',
      fixed: false,
      title: '退货原因',
      width: 74,
      checked: true,
      colspan: 1,
      rowspan: 1,
      columnId: 'reasonText',
    },
  ],
];

/** 流转单设计稿中的明细表高度与签批 top（pt） */
const MOVEMENT_TABLE_HEIGHT_PT = 580;
const MOVEMENT_FOOTER_TOP_PT = 720;

function readNumber(value: unknown, fallback = 0): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function ensureOptions(element: PrintElementLike): Record<string, unknown> {
  if (!element.options || typeof element.options !== 'object') {
    element.options = {};
  }
  return element.options;
}

function isTableElement(element: PrintElementLike): boolean {
  return element.printElementType?.type === 'table';
}

/**
 * 把退货迷你模板抬到与流转单一致的设计框，再交给 applyInvFormPaperLayout 锁一折纸高。
 * 可就地改 prepare 后的深拷贝。
 */
export function normalizeInvReturnPrintJson(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  const panels = printJson.panels;
  if (!Array.isArray(panels) || panels.length === 0) {
    return printJson;
  }
  const panel = panels[0];
  if (!panel || typeof panel !== 'object') {
    return printJson;
  }
  const panelObj = panel as Record<string, unknown>;

  // 去掉「按内容出纸」，与流转单一样先按整页设计框排
  delete panelObj.panelPageRule;
  if (panelObj.paperType == null || panelObj.paperType === '') {
    panelObj.paperType = 'roll';
  }
  if (typeof panelObj.paperFooter !== 'number') {
    panelObj.paperFooter = 841.889_763_779_527_7;
  }
  panelObj.paperHeader = 0;

  const elements = panelObj.printElements;
  if (!Array.isArray(elements)) {
    return printJson;
  }

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
    const bottom = readNumber(options.top) + readNumber(options.height);
    if (!tableElement || bottom > tableBottomPt) {
      tableElement = element;
      tableBottomPt = bottom;
    }
  }

  if (!tableElement) {
    return printJson;
  }

  const tableOptions = ensureOptions(tableElement);
  tableOptions.height = MOVEMENT_TABLE_HEIGHT_PT;
  tableOptions.columns = structuredClone(RETURN_TABLE_COLUMNS);
  const newTableBottom = readNumber(tableOptions.top) + MOVEMENT_TABLE_HEIGHT_PT;

  // 原迷你稿签批紧贴矮表（约 top=170）；抬到流转单签批位置
  for (const raw of elements) {
    if (!raw || typeof raw !== 'object' || raw === tableElement) {
      continue;
    }
    const element = raw as PrintElementLike;
    const options = ensureOptions(element);
    const top = readNumber(options.top);
    if (top >= tableBottomPt - 8 && top < newTableBottom) {
      options.top = MOVEMENT_FOOTER_TOP_PT;
    }
  }

  return printJson;
}
