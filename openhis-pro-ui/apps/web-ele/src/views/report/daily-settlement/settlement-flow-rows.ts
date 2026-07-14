import type {
  DailySettlementColumnDto,
  DailySettlementListRowDto,
} from '#/api/report/daily-settlement';

import { fmtReportNumber } from '#/views/report/shared/report-format';

/** 展开区单行期间流水（统一表行，固定列头）。 */
export interface SettlementFlowRow {
  key: string;
  /** 业务名称（发药、入库、盘点盈…） */
  label: string;
  quantityText: string;
  costPackageUnitPriceText: string;
  costAmountText: string;
}

const GAIN_LOSS_GROUP_CODES = new Set(['stocktake', 'gainLoss']);

/** 从列标题提取业务组名（如「入库数量」→「入库」）。 */
function extractGroupLabel(columnTitle: string): string {
  return columnTitle
    .replace(/数量$/, '')
    .replace(/进价额$/, '')
    .trim();
}

/** 按库存明细「库存(最小)」口径格式化拆零数量（整数，无小数）。 */
export function formatMinUnitQuantity(
  quantity: unknown,
  unitLabel?: string | null,
  fractionDigits = 0,
): string {
  if (quantity === null || quantity === undefined || quantity === '') {
    return '—';
  }
  const numText = fmtReportNumber(quantity, fractionDigits);
  if (!numText || numText === '—') {
    return '—';
  }
  const unit = String(unitLabel ?? '').trim();
  return unit ? `${numText}${unit}` : numText;
}

/** 展开区数量：无数据按 0 展示，便于与公式逐项核对。 */
function formatFlowQuantity(value: unknown, unitLabel?: string | null): string {
  if (value === null || value === undefined || value === '') {
    return formatMinUnitQuantity(0, unitLabel);
  }
  return formatMinUnitQuantity(value, unitLabel);
}

/** 展开区金额：无数据按 0 展示。 */
function formatFlowAmount(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return formatFlowCell(0);
  }
  return formatFlowCell(value);
}

/** 格式化流水单元格：数量/金额空值占位。 */
function formatFlowCell(value: unknown, fractionDigits = 2): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const text = fmtReportNumber(value, fractionDigits);
  return text || '—';
}

/** 主表左侧信息区宽度（展开列 + 编码 + 名称 + 商品名 + 规格），子表与期初列对齐。 */
export const DAILY_SETTLEMENT_MAIN_INFO_WIDTH_PX = 48 + 80 + 110 + 96 + 80;

/** 解析展示文案中的数值（支持 30片 等带单位格式）。 */
function parseDisplayNumber(text: string): number | null {
  if (!text || text === '—') {
    return null;
  }
  const matched = text.replace(/,/g, '').match(/-?\d+(?:\.\d+)?/);
  if (!matched) {
    return null;
  }
  const num = Number(matched[0]);
  return Number.isFinite(num) ? num : null;
}

/** 判断流水行是否全为空或零。 */
function isFlowRowEmpty(row: SettlementFlowRow): boolean {
  const texts = [row.quantityText, row.costPackageUnitPriceText, row.costAmountText];
  return texts.every((text) => {
    const num = parseDisplayNumber(text);
    return num === null || num === 0;
  });
}

/** 按 groupCode 顺序分组动态列元数据。 */
function groupColumnsByCode(columns: DailySettlementColumnDto[]) {
  const groupOrder: string[] = [];
  const groupMap = new Map<string, DailySettlementColumnDto[]>();
  for (const column of columns) {
    const groupCode = String(column.groupCode ?? column.columnCode ?? '').trim();
    if (!groupCode) {
      continue;
    }
    if (!groupMap.has(groupCode)) {
      groupOrder.push(groupCode);
      groupMap.set(groupCode, []);
    }
    groupMap.get(groupCode)?.push(column);
  }
  return { groupOrder, groupMap };
}

/** 盘点/损益组：盈、亏各一行。 */
function buildGainLossFlowRows(
  row: DailySettlementListRowDto,
  columns: DailySettlementColumnDto[],
  groupCode: string,
): SettlementFlowRow[] {
  const flows: SettlementFlowRow[] = [];
  const gainQtyCol = columns.find(
    (c) => String(c.columnTitle ?? '').includes('盈') && c.valueType?.includes('QUANTITY'),
  );
  const lossQtyCol = columns.find(
    (c) => String(c.columnTitle ?? '').includes('亏') && c.valueType?.includes('QUANTITY'),
  );
  const gainCostCol = columns.find(
    (c) => String(c.columnTitle ?? '').includes('盈') && c.valueType?.includes('AMOUNT'),
  );
  const lossCostCol = columns.find(
    (c) => String(c.columnTitle ?? '').includes('亏') && c.valueType?.includes('AMOUNT'),
  );
  const groupPrefix = groupCode === 'stocktake' ? '盘点' : '损益';

  if (gainQtyCol || gainCostCol) {
    flows.push({
      key: `${groupCode}-gain`,
      label: `${groupPrefix}盈`,
      quantityText: formatFlowQuantity(row[gainQtyCol?.columnCode ?? ''], row.minUnitCodeLabel),
      costPackageUnitPriceText: '—',
      costAmountText: formatFlowAmount(row[gainCostCol?.columnCode ?? '']),
    });
  }
  if (lossQtyCol || lossCostCol) {
    flows.push({
      key: `${groupCode}-loss`,
      label: `${groupPrefix}亏`,
      quantityText: formatFlowQuantity(row[lossQtyCol?.columnCode ?? ''], row.minUnitCodeLabel),
      costPackageUnitPriceText: '—',
      costAmountText: formatFlowAmount(row[lossCostCol?.columnCode ?? '']),
    });
  }
  return flows;
}

/** 标准业务组：数量 + 包装进价 + 进价额一行。 */
function buildStandardFlowRow(
  row: DailySettlementListRowDto,
  columns: DailySettlementColumnDto[],
  groupCode: string,
): SettlementFlowRow {
  const qtyCol = columns.find((c) => c.valueType?.includes('QUANTITY'));
  const costPkgCol = columns.find((c) => c.valueType?.includes('PACKAGE_UNIT_PRICE_COST'));
  const costCol = columns.find((c) => c.valueType?.includes('AMOUNT_COST'));
  const labelSource = qtyCol?.columnTitle ?? costCol?.columnTitle ?? groupCode;
  const groupLabel = extractGroupLabel(String(labelSource));
  return {
    key: groupCode,
    label: groupLabel,
    quantityText: formatFlowQuantity(row[qtyCol?.columnCode ?? ''], row.minUnitCodeLabel),
    costPackageUnitPriceText: formatFlowAmount(row[costPkgCol?.columnCode ?? '']),
    costAmountText: formatFlowAmount(row[costCol?.columnCode ?? '']),
  };
}

/** 将品种行 + 动态列元数据转为展开区流水列表。 */
export function buildSettlementFlowRows(
  row: DailySettlementListRowDto,
  columns: DailySettlementColumnDto[],
  options?: { hideZeroRows?: boolean },
): SettlementFlowRow[] {
  const hideZeroRows = options?.hideZeroRows ?? true;
  const { groupOrder, groupMap } = groupColumnsByCode(columns);
  const flows: SettlementFlowRow[] = [];

  for (const groupCode of groupOrder) {
    const groupColumns = groupMap.get(groupCode) ?? [];
    if (groupColumns.length === 0) {
      continue;
    }
    if (GAIN_LOSS_GROUP_CODES.has(groupCode)) {
      flows.push(...buildGainLossFlowRows(row, groupColumns, groupCode));
      continue;
    }
    flows.push(buildStandardFlowRow(row, groupColumns, groupCode));
  }

  if (!hideZeroRows) {
    return flows;
  }
  return flows.filter((flow) => !isFlowRowEmpty(flow));
}
