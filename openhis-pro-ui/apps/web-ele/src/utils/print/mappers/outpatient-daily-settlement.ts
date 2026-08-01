import type {
  DailySettlementChannelRowDto,
  DailySettlementExpenseNatureSummaryDto,
  DailySettlementInsuranceTypeSummaryDto,
  DailySettlementSummaryDto,
} from '#/api/outpatient/daily-settlement';

/** 标签+金额单元格（模板四列表格列绑定）。 */
export interface OutpatientDailySettlementPrintMetric {
  labelText: string;
  valueText: string;
}

/** 四列一行（上半区 fin_type / 渠道汇总）。 */
export interface OutpatientDailySettlementPrintGridRow {
  cell1Text: string;
  cell2Text: string;
  cell3Text: string;
  cell4Text: string;
}

/** 费用性质下险种子行。 */
export interface OutpatientDailySettlementPrintNatureType {
  titleText: string;
  personCountText: string;
  metrics: OutpatientDailySettlementPrintMetric[];
}

/** 费用性质块（与页面 DailySettlementSummaryPanel 同构）。 */
export interface OutpatientDailySettlementPrintNatureBlock {
  titleText: string;
  personCountText: string;
  metrics: OutpatientDailySettlementPrintMetric[];
  types: OutpatientDailySettlementPrintNatureType[];
}

/**
 * 门诊/住院费用日结单 PrintData（动态：页面有什么打什么）。
 * SOURCE: hospitalName=C；其余均为 P（预览/凭证 rows + summary）
 */
export interface OutpatientDailySettlementPrintData {
  hospitalName: string;
  cashierName: string;
  settlementPeriodText: string;
  /** 上半区 fin_type 四列表（由 rows 动态切行）。 */
  finTypeRows: OutpatientDailySettlementPrintGridRow[];
  /** 渠道汇总四列表（总收入/微信/应交现金等）。 */
  channelRows: OutpatientDailySettlementPrintGridRow[];
  /** 费用性质块（含险种子行；模板 longText 循环）。 */
  natureBlocks: OutpatientDailySettlementPrintNatureBlock[];
}

/** 日结 mapper 入参（P：日结预览/凭证；C：机构名） */
export interface OutpatientDailySettlementPrintSource {
  hospitalName?: string;
  cashierName?: string;
  settlementPeriodText?: string;
  rows?: DailySettlementChannelRowDto[];
  summary?: DailySettlementSummaryDto & { depositAmount?: string };
}

const UNKNOWN_CONTRACT_NAME = '未标注费用性质';
const UNKNOWN_INSUTYPE_NAME = '未分险种';
const GRID_COLS = 4;

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function formatMoneyText(value: null | number | string | undefined, unit = '元'): string {
  const raw = str(value);
  if (!raw) {
    return `0.00 ${unit}`;
  }
  if (raw.includes(unit)) {
    return raw;
  }
  return `${raw} ${unit}`;
}

function formatPersonCountText(count: number | undefined): string {
  return `${count ?? 0} 人`;
}

function toCellText(label: string, valueText: string): string {
  const name = str(label) || '—';
  return `${name}：${valueText}`;
}

function chunkToGridRows(
  items: OutpatientDailySettlementPrintMetric[],
): OutpatientDailySettlementPrintGridRow[] {
  const rows: OutpatientDailySettlementPrintGridRow[] = [];
  for (let i = 0; i < items.length; i += GRID_COLS) {
    const slice = items.slice(i, i + GRID_COLS);
    const cells = [0, 1, 2, 3].map((offset) => {
      const item = slice[offset];
      if (!item) {
        return '';
      }
      return toCellText(item.labelText, item.valueText);
    });
    rows.push({
      cell1Text: cells[0] ?? '',
      cell2Text: cells[1] ?? '',
      cell3Text: cells[2] ?? '',
      cell4Text: cells[3] ?? '',
    });
  }
  return rows;
}

function mapFinTypeItems(
  rows: DailySettlementChannelRowDto[] | undefined,
): OutpatientDailySettlementPrintMetric[] {
  return (rows ?? []).map((row) => {
    const unit = str(row.unit) || '元';
    return {
      labelText: str(row.category) || '—',
      valueText: formatMoneyText(row.amount, unit),
    };
  });
}

function mapChannelItems(
  summary: OutpatientDailySettlementPrintSource['summary'] | undefined,
): OutpatientDailySettlementPrintMetric[] {
  if (!summary) {
    return [];
  }
  const items: OutpatientDailySettlementPrintMetric[] = [
    { labelText: '总收入', valueText: formatMoneyText(summary.totalIncome) },
    { labelText: '微信', valueText: formatMoneyText(summary.wechatAmount) },
    { labelText: '支付宝', valueText: formatMoneyText(summary.alipayAmount) },
    { labelText: '银行卡', valueText: formatMoneyText(summary.onlineBankAmount) },
    {
      labelText: '应交现金',
      valueText: formatMoneyText(summary.cashDueAmount ?? summary.cashAmount),
    },
    {
      labelText: '医保补偿',
      valueText: formatMoneyText(summary.insuranceCompensationAmount),
    },
  ];
  if (summary.depositAmount !== undefined) {
    items.push({
      labelText: '预交金',
      valueText: formatMoneyText(summary.depositAmount),
    });
  }
  return items;
}

function moneyMetrics(
  parts: Array<{ label: string; value?: string }>,
): OutpatientDailySettlementPrintMetric[] {
  return parts.map((part) => ({
    labelText: part.label,
    valueText: formatMoneyText(part.value),
  }));
}

function mapInsutypeTypes(
  types: DailySettlementInsuranceTypeSummaryDto[] | undefined,
): OutpatientDailySettlementPrintNatureType[] {
  const result: OutpatientDailySettlementPrintNatureType[] = [];
  (types ?? []).forEach((type) => {
    if (!type) {
      return;
    }
    const title = str(type.insutypeName) || UNKNOWN_INSUTYPE_NAME;
    // 与页面一致：「未分险种」与费用性质汇总重复，不单独打印
    if (title === UNKNOWN_INSUTYPE_NAME) {
      return;
    }
    result.push({
      titleText: title,
      personCountText: formatPersonCountText(type.personCount),
      metrics: moneyMetrics([
        { label: '现金', value: type.cashAmount },
        { label: '统筹', value: type.fundAmount },
        { label: '账户', value: type.accountAmount },
        { label: '总额', value: type.totalAmount },
      ]),
    });
  });
  return result;
}

function mapNatureBlocks(
  natures: DailySettlementExpenseNatureSummaryDto[] | undefined,
): OutpatientDailySettlementPrintNatureBlock[] {
  const blocks: OutpatientDailySettlementPrintNatureBlock[] = [];
  (natures ?? []).forEach((nature) => {
    if (!nature) {
      return;
    }
    const insurance = Boolean(nature.insurance);
    const metrics = insurance
      ? moneyMetrics([
          { label: '现金', value: nature.cashAmount },
          { label: '统筹', value: nature.fundAmount },
          { label: '账户', value: nature.accountAmount },
          { label: '总额', value: nature.totalAmount },
        ])
      : moneyMetrics([{ label: '总额', value: nature.totalAmount }]);
    blocks.push({
      titleText: str(nature.contractName) || UNKNOWN_CONTRACT_NAME,
      personCountText: formatPersonCountText(nature.personCount),
      metrics,
      types: insurance ? mapInsutypeTypes(nature.insuranceTypeSummaries) : [],
    });
  });
  return blocks;
}

/** 日结预览/凭证 → PrintData（字段与页面展示同源） */
export function buildOutpatientDailySettlementPrintData(
  source: OutpatientDailySettlementPrintSource,
): OutpatientDailySettlementPrintData {
  const summary = source.summary;
  return {
    hospitalName: str(source.hospitalName),
    cashierName: str(source.cashierName),
    settlementPeriodText: str(source.settlementPeriodText) || str(summary?.dateRange),
    finTypeRows: chunkToGridRows(mapFinTypeItems(source.rows)),
    channelRows: chunkToGridRows(mapChannelItems(summary)),
    natureBlocks: mapNatureBlocks(summary?.expenseNatureSummaries),
  };
}
