import type {
  DailySettlementChannelRowDto,
  DailySettlementExpenseNatureSummaryDto,
  DailySettlementInsuranceTypeSummaryDto,
  DailySettlementSummaryDto,
  DailySettlementWorkInjurySummaryDto,
} from '#/api/outpatient/daily-settlement';

import { isWorkInjuryUiEnabled } from '#/utils/project-optional-modules';

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
  documentTitle: string;
  cashierName: string;
  settlementPeriodText: string;
  finTypeRows: OutpatientDailySettlementPrintGridRow[];
  channelRows: OutpatientDailySettlementPrintGridRow[];
  natureRows: OutpatientDailySettlementPrintNatureRow[];
  /** 工伤分类四列网格（无工伤或院点关闭时为空） */
  workInjuryCategoryRows: OutpatientDailySettlementPrintGridRow[];
  /** 工伤统筹/个人自付行（复用费用性质表结构） */
  workInjuryNatureRows: OutpatientDailySettlementPrintNatureRow[];
}

/** 日结 mapper 入参（P：日结预览/凭证；C：机构名） */
export interface OutpatientDailySettlementPrintSource {
  hospitalName?: string;
  /** 单据标题：门诊收费日结单 / 住院收费日结单 / 住院收费日结单（患者分项） */
  documentTitle?: string;
  cashierName?: string;
  settlementPeriodText?: string;
  rows?: DailySettlementChannelRowDto[];
  summary?: DailySettlementSummaryDto & {
    depositAmount?: string;
    depositRefundAmount?: string;
    settleSupplementAmount?: string;
    medicationTotalAmount?: string;
  };
  /**
   * 门/住院患者分项打印源；有值且 {@link printMode}=`patient` 时按患者块拼装。
   */
  patientSummaries?: Array<{
    seqNo?: number;
    patientName?: string;
    encounterBusNo?: string;
    wardName?: string;
    bedName?: string;
    /** 门诊收费日期 yyyy-MM-dd */
    chargeDate?: string;
    hospitalizedText?: string;
    summary?: OutpatientDailySettlementPrintSource['summary'];
  }>;
  /** 合计打印（默认）或患者分项打印 */
  printMode?: 'patient' | 'summary';
  /** 已配置有效折扣时渠道区打印折扣合计 */
  discountFeatureEnabled?: boolean;
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

/** 是否打印折扣合计：仅已配置有效折扣时打印。 */
function shouldPrintDiscountAmount(
  _discountAmount: string | undefined,
  discountFeatureEnabled: boolean | undefined,
): boolean {
  return Boolean(discountFeatureEnabled);
}

/** 下半区渠道网格项。 */
function mapChannelItems(
  summary: OutpatientDailySettlementPrintSource['summary'] | undefined,
  options?: { discountFeatureEnabled?: boolean; omitRetailUploadDiff?: boolean },
): DailySettlementPrintMetric[] {
  if (!summary) {
    return [];
  }
  const medicationTotal =
    summary.medicationTotalAmount?.trim() ||
    (summary.summaryRows ?? []).find((row) => row?.key === 'medicationTotalAmount')?.value ||
    '0.00';
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
      labelText: '药品合计金额',
      valueText: formatMoneyText(medicationTotal),
    },
  ];
  if (shouldPrintDiscountAmount(summary.discountAmount, options?.discountFeatureEnabled)) {
    // 与页面渠道区同序：应交现金后插入折扣合计；应收=折后总收入+折扣
    items.push({
      labelText: '折扣合计',
      valueText: formatMoneyText(summary.discountAmount),
    });
    items.push({
      labelText: '应收',
      valueText: formatMoneyText(summary.receivableAmount),
    });
  }
  items.push(
    {
      labelText: '医保统筹',
      valueText: formatMoneyText(summary.insuranceCompensationAmount),
    },
    {
      labelText: '个人账户',
      valueText: formatMoneyText(summary.insuranceAccountAmount),
    },
  );
  const retailDiffRaw =
    summary.chsRetailUploadDiffAmount?.trim() ||
    (summary.summaryRows ?? []).find((row) => row?.key === 'chsRetailUploadDiffAmount')?.value ||
    '';
  if (!options?.omitRetailUploadDiff && !isZeroMoneyText(retailDiffRaw)) {
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
  // 住院多退少补拆列：与页面 summary 同字段，打印一并带出
  if (summary.depositRefundAmount !== undefined) {
    items.push({
      labelText: '退预交金',
      valueText: formatMoneyText(summary.depositRefundAmount),
    });
  }
  if (summary.settleSupplementAmount !== undefined) {
    items.push({
      labelText: '结算补收',
      valueText: formatMoneyText(summary.settleSupplementAmount),
    });
  }
  return items;
}

/**
 * 组装患者分项身份标题。
 * 住院：姓名 + 住院号 + 病房-病床；门诊：姓名 + 门诊号 + 收费日期 + 是否住院。
 */
function formatPatientBlockTitle(block: {
  bedName?: string;
  chargeDate?: string;
  encounterBusNo?: string;
  hospitalizedText?: string;
  patientName?: string;
  wardName?: string;
}): string {
  const name = str(block.patientName) || '未知患者';
  const busNo = str(block.encounterBusNo) || '—';
  const ward = str(block.wardName);
  const bed = str(block.bedName);
  const chargeDate = str(block.chargeDate) || '—';
  const hospitalizedText = str(block.hospitalizedText);
  // 门诊特有：有「是否住院」文案时走门诊标题（不含预交金/病区）
  if (hospitalizedText) {
    return `${name} 门诊号:${busNo} 收费日期:${chargeDate} 是否住院:${hospitalizedText}`;
  }
  let location = '';
  if (ward && bed) {
    location = `${ward}-${bed}`;
  } else if (ward) {
    location = ward;
  } else if (bed) {
    location = bed;
  }
  if (location) {
    return `${name} 住院号:${busNo} ${location}`;
  }
  return `${name} 住院号:${busNo}`;
}

/**
 * 患者分项 → 渠道四列网格（每位患者先标题行，再跟合计同构金额行）。
 *
 * @param patients 患者分项块
 * @param discountFeatureEnabled 已配置有效折扣时打印折扣合计
 */
function mapPatientChannelRows(
  patients: OutpatientDailySettlementPrintSource['patientSummaries'],
  discountFeatureEnabled?: boolean,
): OutpatientDailySettlementPrintGridRow[] {
  const rows: OutpatientDailySettlementPrintGridRow[] = [];
  for (const block of patients ?? []) {
    if (!block) {
      continue;
    }
    rows.push({
      rowType: 'grid',
      label1: formatPatientBlockTitle(block),
      amount1: '',
      label2: '',
      amount2: '',
      label3: '',
      amount3: '',
      label4: '',
      amount4: '',
    });
    rows.push(
      ...chunkToGridRows(
        mapChannelItems(block.summary, {
          omitRetailUploadDiff: true,
          discountFeatureEnabled,
        }),
        4,
      ),
    );
  }
  return rows;
}

/**
 * 患者分项 → 费用性质明细（名称前加患者序号，便于分块辨认）。
 */
function mapPatientNatureRows(
  patients: OutpatientDailySettlementPrintSource['patientSummaries'],
): OutpatientDailySettlementPrintNatureRow[] {
  const rows: OutpatientDailySettlementPrintNatureRow[] = [];
  for (const block of patients ?? []) {
    if (!block?.summary?.expenseNatureSummaries?.length) {
      continue;
    }
    const prefix = `【${str(block.patientName) || '未知患者'}】`;
    const natureRows = mapNatureRows(block.summary.expenseNatureSummaries);
    for (const natureRow of natureRows) {
      rows.push({
        ...natureRow,
        name: `${prefix}${natureRow.name}`,
      });
    }
  }
  return rows;
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

/** 未分险种且现金/统筹/个账/总额全为 0 时不打印；任一列非 0 必须打出 */
function isAllZeroInsutypeRow(type: DailySettlementInsuranceTypeSummaryDto): boolean {
  return (
    isZeroMoneyText(type.cashAmount) &&
    isZeroMoneyText(type.fundAmount) &&
    isZeroMoneyText(type.accountAmount) &&
    isZeroMoneyText(type.totalAmount)
  );
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
    if (title === UNKNOWN_INSUTYPE_NAME && isAllZeroInsutypeRow(type)) {
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

/** 工伤分类金额 → 打印四列网格 */
function mapWorkInjuryCategoryRows(
  wi: DailySettlementWorkInjurySummaryDto | null | undefined,
): OutpatientDailySettlementPrintGridRow[] {
  if (!isWorkInjuryUiEnabled() || !wi) {
    return [];
  }
  const items: DailySettlementPrintMetric[] = [
    {
      labelText: '工伤西药中成药',
      valueText: formatMoneyText(wi.westernPatentMedicineAmount),
    },
    { labelText: '工伤诊疗', valueText: formatMoneyText(wi.treatmentAmount) },
    { labelText: '工伤耗材', valueText: formatMoneyText(wi.consumableAmount) },
    {
      labelText: '工伤中草药',
      valueText: formatMoneyText(wi.herbalMedicineAmount),
    },
    {
      labelText: '工伤诊察费',
      valueText: formatMoneyText(wi.consultationFeeAmount),
    },
    {
      labelText: '工伤人次',
      valueText: `${String(wi.personCount ?? 0)}人次`,
    },
  ];
  return chunkToGridRows(items, 4);
}

/** 工伤统筹/个人自付 → 打印明细表行 */
function mapWorkInjuryNatureRows(
  wi: DailySettlementWorkInjurySummaryDto | null | undefined,
): OutpatientDailySettlementPrintNatureRow[] {
  if (!isWorkInjuryUiEnabled() || !wi) {
    return [];
  }
  return [
    natureTableRow('nature', str(wi.contractName) || '工伤保险', wi.personCount, {
      cash: wi.cashAmount,
      fund: wi.fundAmount,
      account: wi.accountAmount,
      total: wi.totalAmount,
    }),
  ];
}

/** 日结预览/凭证 → PrintData（字段与页面展示同源） */
export function buildOutpatientDailySettlementPrintData(
  source: OutpatientDailySettlementPrintSource,
): OutpatientDailySettlementPrintData {
  const summary = source.summary;
  const workInjury = summary?.workInjurySummary;
  // 患者分项：不上半区 fin_type，渠道/费用性质按患者块展开
  if (source.printMode === 'patient') {
    return {
      hospitalName: str(source.hospitalName),
      documentTitle: str(source.documentTitle) || '住院收费日结单（患者分项）',
      cashierName: str(source.cashierName),
      settlementPeriodText: str(source.settlementPeriodText) || str(summary?.dateRange),
      finTypeRows: [],
      channelRows: mapPatientChannelRows(
        source.patientSummaries,
        source.discountFeatureEnabled,
      ),
      natureRows: mapPatientNatureRows(source.patientSummaries),
      workInjuryCategoryRows: [],
      workInjuryNatureRows: [],
    };
  }
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '门诊收费日结单',
    cashierName: str(source.cashierName),
    settlementPeriodText: str(source.settlementPeriodText) || str(summary?.dateRange),
    finTypeRows: chunkToGridRows(mapFinTypeItems(source.rows)),
    channelRows: chunkToGridRows(
      mapChannelItems(summary, { discountFeatureEnabled: source.discountFeatureEnabled }),
      4,
    ),
    natureRows: mapNatureRows(summary?.expenseNatureSummaries),
    // 工伤独立块：模板绑定 workInjury*；无工伤或院点关闭时为空数组
    workInjuryCategoryRows: mapWorkInjuryCategoryRows(workInjury),
    workInjuryNatureRows: mapWorkInjuryNatureRows(workInjury),
  };
}
