import type {
  DailySettlementChannelRowDto,
  DailySettlementExpenseNatureSummaryDto,
  DailySettlementInsuranceTypeSummaryDto,
  DailySettlementSummaryDto,
} from '#/api/outpatient/daily-settlement';

/** 标签+金额。 */
interface DailySettlementPrintMetric {
  labelText: string;
  valueText: string;
}

/**
 * 四组「标签左 + 金额右」一行（模板 8 列无边框表，财务分类/渠道区）。
 */
export interface OutpatientDailySettlementPrintGridRow {
  rowType: 'grid';
  label1: string;
  amount1: string;
  label2: string;
  amount2: string;
  label3: string;
  amount3: string;
  label4: string;
  amount4: string;
}

/**
 * 费用性质明细表一行（模板有线表：名称/人数/医保现金/医保统筹/个人账户/总额）。
 * nature=费用性质合计行；type=险种子行（名称缩进）。
 */
export interface OutpatientDailySettlementPrintNatureRow {
  rowType: 'nature' | 'type';
  name: string;
  personCount: string;
  cashAmount: string;
  fundAmount: string;
  accountAmount: string;
  totalAmount: string;
}

/**
 * 门诊/住院费用日结单 PrintData（动态：页面有什么打什么）。
 *
 * SOURCE: hospitalName=C；其余均为 P（预览/凭证 rows + summary）
 */
export interface OutpatientDailySettlementPrintData {
  hospitalName: string;
  cashierName: string;
  settlementPeriodText: string;
  finTypeRows: OutpatientDailySettlementPrintGridRow[];
  channelRows: OutpatientDailySettlementPrintGridRow[];
  natureRows: OutpatientDailySettlementPrintNatureRow[];
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
const TYPE_NAME_INDENT = '　';

/** 按费用性质名称关键字取总额（渠道区市/省医保合计、自费合计） */
function findNatureTotalAmount(
  natures: DailySettlementExpenseNatureSummaryDto[] | undefined,
  keyword: string,
): string | undefined {
  const hit = (natures ?? []).find((nature) => str(nature?.contractName).includes(keyword));
  return hit?.totalAmount;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function formatMoneyText(value: null | number | string | undefined, unit = '元'): string {
  const raw = str(value);
  if (!raw) {
    return `0.00${unit}`;
  }
  if (raw.includes(unit)) {
    // 去掉「元」前空格，压缩四列金额列宽度避免折行
    return raw.replace(new RegExp(String.raw`\s*${unit}$`), unit);
  }
  return `${raw}${unit}`;
}

/** 金额是否为 0（空、0、0.00、0元） */
function isZeroMoneyText(value: null | number | string | undefined): boolean {
  const raw = str(value)
    .replace(/\s*元$/, '')
    .replace(/,/g, '');
  if (!raw) {
    return true;
  }
  const num = Number(raw);
  return Number.isFinite(num) && num === 0;
}

/** 表格金额：不加「元」（单位在表头） */
function formatTableAmount(value: null | number | string | undefined): string {
  const raw = str(value).replace(/\s*元$/, '');
  if (!raw) {
    return '0.00';
  }
  return raw;
}

function emptyGridRow(): OutpatientDailySettlementPrintGridRow {
  return {
    rowType: 'grid',
    label1: '',
    amount1: '',
    label2: '',
    amount2: '',
    label3: '',
    amount3: '',
    label4: '',
    amount4: '',
  };
}

function metricsToGridRow(
  metrics: DailySettlementPrintMetric[],
  cols: number = GRID_COLS,
): OutpatientDailySettlementPrintGridRow {
  const row = emptyGridRow();
  const keys = [
    ['label1', 'amount1'],
    ['label2', 'amount2'],
    ['label3', 'amount3'],
    ['label4', 'amount4'],
  ] as const;
  metrics.slice(0, cols).forEach((item, index) => {
    const pair = keys[index];
    if (!pair) {
      return;
    }
    const [labelKey, amountKey] = pair;
    row[labelKey] = `${str(item.labelText) || '—'}：`;
    row[amountKey] = item.valueText;
  });
  return row;
}

function chunkToGridRows(
  items: DailySettlementPrintMetric[],
  cols: number = GRID_COLS,
): OutpatientDailySettlementPrintGridRow[] {
  const rows: OutpatientDailySettlementPrintGridRow[] = [];
  for (let i = 0; i < items.length; i += cols) {
    rows.push(metricsToGridRow(items.slice(i, i + cols), cols));
  }
  return rows;
}

function mapFinTypeItems(
  rows: DailySettlementChannelRowDto[] | undefined,
): DailySettlementPrintMetric[] {
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
): DailySettlementPrintMetric[] {
  if (!summary) {
    return [];
  }
  const items: DailySettlementPrintMetric[] = [
    { labelText: '总收入', valueText: formatMoneyText(summary.totalIncome) },
    { labelText: '微信', valueText: formatMoneyText(summary.wechatAmount) },
    { labelText: '支付宝', valueText: formatMoneyText(summary.alipayAmount) },
    { labelText: '银行卡', valueText: formatMoneyText(summary.onlineBankAmount) },
    {
      labelText: '应交现金',
      valueText: formatMoneyText(summary.cashDueAmount ?? summary.cashAmount),
    },
    {
      labelText: '医保统筹',
      valueText: formatMoneyText(summary.insuranceCompensationAmount),
    },
    {
      labelText: '个人账户',
      valueText: formatMoneyText(summary.insuranceAccountAmount),
    },
  ];
  const retailDiffRaw =
    summary.chsRetailUploadDiffAmount?.trim() ||
    (summary.summaryRows ?? []).find((row) => row?.key === 'chsRetailUploadDiffAmount')?.value ||
    '';
  if (!isZeroMoneyText(retailDiffRaw)) {
    items.push({
      labelText: '医保上传差额',
      valueText: formatMoneyText(retailDiffRaw),
    });
  }
  const natures = summary.expenseNatureSummaries;
  items.push(
    {
      labelText: '市医保合计',
      valueText: formatMoneyText(findNatureTotalAmount(natures, '市医保') ?? '0.00'),
    },
    {
      labelText: '省医保合计',
      valueText: formatMoneyText(findNatureTotalAmount(natures, '省医保') ?? '0.00'),
    },
    {
      labelText: '自费合计',
      valueText: formatMoneyText(findNatureTotalAmount(natures, '自费') ?? '0.00'),
    },
  );
  if (summary.depositAmount !== undefined) {
    items.push({
      labelText: '预交金',
      valueText: formatMoneyText(summary.depositAmount),
    });
  }
  return items;
}

function natureTableRow(
  rowType: 'nature' | 'type',
  name: string,
  personCount: number | undefined,
  amounts: {
    account?: string;
    cash?: string;
    fund?: string;
    total?: string;
    /** 自费等：仅总额，其余留空 */
    totalOnly?: boolean;
  },
): OutpatientDailySettlementPrintNatureRow {
  if (amounts.totalOnly) {
    return {
      rowType,
      name,
      personCount: String(personCount ?? 0),
      cashAmount: '',
      fundAmount: '',
      accountAmount: '',
      totalAmount: formatTableAmount(amounts.total),
    };
  }
  return {
    rowType,
    name,
    personCount: String(personCount ?? 0),
    cashAmount: formatTableAmount(amounts.cash),
    fundAmount: formatTableAmount(amounts.fund),
    accountAmount: formatTableAmount(amounts.account),
    totalAmount: formatTableAmount(amounts.total),
  };
}

function mapInsutypeNatureRows(
  types: DailySettlementInsuranceTypeSummaryDto[] | undefined,
): OutpatientDailySettlementPrintNatureRow[] {
  const rows: OutpatientDailySettlementPrintNatureRow[] = [];
  (types ?? []).forEach((type) => {
    if (!type) {
      return;
    }
    const title = str(type.insutypeName) || UNKNOWN_INSUTYPE_NAME;
    if (title === UNKNOWN_INSUTYPE_NAME) {
      return;
    }
    rows.push(
      natureTableRow('type', `${TYPE_NAME_INDENT}${title}`, type.personCount, {
        cash: type.cashAmount,
        fund: type.fundAmount,
        account: type.accountAmount,
        total: type.totalAmount,
      }),
    );
  });
  return rows;
}

/** 费用性质 → 有线明细表行（性质分组行 + 险种子行；合计已上提到渠道区） */
function mapNatureRows(
  natures: DailySettlementExpenseNatureSummaryDto[] | undefined,
): OutpatientDailySettlementPrintNatureRow[] {
  const rows: OutpatientDailySettlementPrintNatureRow[] = [];
  (natures ?? []).forEach((nature) => {
    if (!nature) {
      return;
    }
    const name = str(nature.contractName) || UNKNOWN_CONTRACT_NAME;
    const insurance = Boolean(nature.insurance);
    if (insurance) {
      rows.push(
        natureTableRow('nature', name, nature.personCount, {
          cash: nature.cashAmount,
          fund: nature.fundAmount,
          account: nature.accountAmount,
          total: nature.totalAmount,
        }),
        ...mapInsutypeNatureRows(nature.insuranceTypeSummaries),
      );
      return;
    }
    rows.push(
      natureTableRow('nature', name, nature.personCount, {
        total: nature.totalAmount,
        totalOnly: true,
      }),
    );
  });
  return rows;
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
    // 渠道区保持四列一行（与财务分类同构）
    channelRows: chunkToGridRows(mapChannelItems(summary), 4),
    natureRows: mapNatureRows(summary?.expenseNatureSummaries),
  };
}
