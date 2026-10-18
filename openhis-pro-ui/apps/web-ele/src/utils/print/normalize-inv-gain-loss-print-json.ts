/**
 * 损益单复用盘点盈亏模版时，打印前去掉账面/实盘列，仅保留损益数量与金额。
 */

interface PrintElementLike {
  options?: Record<string, unknown>;
  printElementType?: {
    type?: string;
    title?: string;
    columns?: unknown;
    formatter?: unknown;
  };
}

/** 损益明细列（无账面/实盘；field 仍对齐盘点 PrintData 的 diff*） */
const GAIN_LOSS_TABLE_COLUMNS = [
  [
    {
      align: 'center',
      field: 'seqNo',
      title: '序号',
      width: 36,
      checked: true,
      columnId: 'seqNo',
    },
    {
      align: 'left',
      field: 'productName',
      title: '品名',
      width: 100,
      checked: true,
      columnId: 'productName',
    },
    {
      align: 'left',
      field: 'specText',
      title: '规格',
      width: 56,
      checked: true,
      columnId: 'specText',
    },
    {
      align: 'left',
      field: 'manufacturerText',
      title: '厂家',
      width: 60,
      checked: true,
      columnId: 'manufacturerText',
    },
    {
      align: 'center',
      field: 'lotNumber',
      title: '批号',
      width: 56,
      checked: true,
      columnId: 'lotNumber',
    },
    {
      align: 'center',
      field: 'diffQtyText',
      title: '损益数量',
      width: 56,
      checked: true,
      columnId: 'diffQtyText',
    },
    {
      align: 'right',
      field: 'unitPriceText',
      title: '单价',
      width: 44,
      checked: true,
      columnId: 'unitPriceText',
      // 覆盖库模板按 columnId 合并回来的 formatter（避免把金额×100 印进单价）
      formatter: '',
      formatter2: '',
      renderFormatter: '',
    },
    {
      align: 'right',
      field: 'diffAmountText',
      title: '金额',
      width: 52,
      checked: true,
      columnId: 'diffAmountText',
      formatter: '',
      formatter2: '',
      renderFormatter: '',
    },
    {
      align: 'left',
      field: 'reasonText',
      title: '变更原因',
      width: 86,
      checked: true,
      columnId: 'reasonText',
    },
  ],
];

function isTableElement(element: PrintElementLike): boolean {
  return String(element.printElementType?.type ?? '') === 'table';
}

function ensureOptions(element: PrintElementLike): Record<string, unknown> {
  if (!element.options || typeof element.options !== 'object') {
    element.options = {};
  }
  return element.options;
}

/**
 * 将盘点盈亏 PrintJson 裁成损益单版式：去掉账面/实盘/实盘额，页脚「盈亏合计」改为「损益合计」。
 */
export function normalizeInvGainLossPrintJson(
  printJson: Record<string, unknown>,
): Record<string, unknown> {
  const panels = printJson.panels;
  if (!Array.isArray(panels) || panels.length === 0) {
    return printJson;
  }
  const panel = panels[0] as { printElements?: unknown[] };
  const elements = panel.printElements;
  if (!Array.isArray(elements)) {
    return printJson;
  }

  for (const raw of elements) {
    if (!raw || typeof raw !== 'object') {
      continue;
    }
    const element = raw as PrintElementLike;
    const options = ensureOptions(element);

    if (isTableElement(element)) {
      options.columns = structuredClone(GAIN_LOSS_TABLE_COLUMNS);
      // hiprint 会按 columnId 把 printElementType.columns 合并进 options.columns，
      // 旧列上的 formatter 会留下来，把单价印成金额的百分制。
      if (element.printElementType) {
        element.printElementType.title = '损益明细';
        delete element.printElementType.columns;
        delete element.printElementType.formatter;
      }
      continue;
    }

    // 页脚合计文案：盘点「盈亏合计」→ 损益「损益合计」
    const field = String(options.field ?? '');
    const title = String(options.title ?? element.printElementType?.title ?? '');
    if (field === 'totalAmountText' || title === '盈亏合计') {
      options.title = '损益合计';
      if (element.printElementType) {
        element.printElementType.title = '损益合计';
      }
    }
  }

  return printJson;
}
