import type { ChargeReceiptSlipDto } from '#/api/outpatient-charge/outpatient-charge.types';

/** 门诊收费发票 PrintData（吉林通用机打发票套打，与 op-invoice-print 模板 field 一致） */
export interface OutpatientChargeInvoicePrintData {
  hospitalName: string;
  patientName: string;
  feeNatureText: string;
  paymentNoText: string;
  departmentName: string;
  encounterBusNo: string;
  insuranceNoText: string;
  doctorName: string;
  cashierName: string;
  chargeDateText: string;
  /** 右侧第三栏收费时间 */
  chargeTimeText: string;
  westernMedicineFeeText: string;
  examFeeText: string;
  chinesePatentFeeText: string;
  surgeryFeeText: string;
  herbalFeeText: string;
  oxygenFeeText: string;
  labFeeText: string;
  bloodFeeText: string;
  xrayFeeText: string;
  disposeFeeText: string;
  electroFeeText: string;
  injectionFeeText: string;
  /** CT费：系统暂无独立分栏，固定 ￥0.00 */
  ctFeeText: string;
  /** 治疗费（右列，与 CT 费同行） */
  therapyFeeText: string;
  /** 磁共振：系统暂无独立分栏，固定 ￥0.00 */
  mriFeeText: string;
  /** 耗材费（右下空白格） */
  materialFeeText: string;
  originalAccountBalanceText: string;
  accountPayText: string;
  invoiceCashPayText: string;
  insurancePayText: string;
  workInjuryPayText: string;
  maternityPayText: string;
  accountBalanceText: string;
  totalAmountChineseText: string;
  totalAmountText: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function stripMoneyPrefix(value: string): string {
  return value.replace(/^[¥￥]\s*/, '');
}

function parseAmount(value: null | number | string | undefined): number | null {
  const text = stripMoneyPrefix(str(value));
  if (!text || text === '0' || text === '0.0' || text === '0.00') {
    return null;
  }
  const num = Number(text);
  if (!Number.isFinite(num)) {
    return null;
  }
  return num;
}

function formatYen(num: number): string {
  return `￥${num.toFixed(2)}`;
}

/** 费用分栏：无金额留空，有金额 ￥0.00 */
function feeAmount(value: null | number | string | undefined): string {
  const num = parseAmount(value);
  if (num === null) {
    return '';
  }
  return formatYen(num);
}

/** 固定栏（CT/磁共振等）：无独立分栏时打 ￥0.00 */
function feeAmountOrZero(value: null | number | string | undefined): string {
  const num = parseAmount(value);
  if (num === null) {
    return '￥0.00';
  }
  return formatYen(num);
}

/** 支付栏：无值 ￥0.00 */
function payAmount(value: null | number | string | undefined): string {
  const text = stripMoneyPrefix(str(value));
  if (!text) {
    return '￥0.00';
  }
  const num = Number(text);
  if (!Number.isFinite(num)) {
    return text.startsWith('￥') || text.startsWith('¥') ? text : `￥${text}`;
  }
  return formatYen(num);
}

/** 后端收费小票 slip → 吉林机打发票套打 PrintData */
export function buildOutpatientChargeInvoicePrintData(
  source: ChargeReceiptSlipDto,
): OutpatientChargeInvoicePrintData {
  return {
    hospitalName: str(source.hospitalName),
    patientName: str(source.patientName),
    feeNatureText: str(source.feeNatureText),
    paymentNoText: str(source.paymentNoText),
    departmentName: str(source.departmentName),
    encounterBusNo: str(source.encounterBusNo),
    insuranceNoText: str(source.insuranceNoText),
    doctorName: str(source.doctorName),
    cashierName: str(source.cashierName),
    chargeDateText: str(source.chargeDateText),
    chargeTimeText: str(source.chargeTimeText) || str(source.chargeDateText),
    westernMedicineFeeText: feeAmount(source.westernMedicineFeeText),
    examFeeText: feeAmount(source.examFeeText),
    chinesePatentFeeText: feeAmount(source.chinesePatentFeeText),
    surgeryFeeText: feeAmount(source.surgeryFeeText),
    herbalFeeText: feeAmount(source.herbalFeeText),
    oxygenFeeText: feeAmount(source.oxygenFeeText),
    labFeeText: feeAmount(source.labFeeText),
    bloodFeeText: feeAmount(source.bloodFeeText),
    xrayFeeText: feeAmount(source.xrayFeeText),
    disposeFeeText: feeAmount(source.disposeFeeText),
    electroFeeText: feeAmount(source.electroFeeText),
    injectionFeeText: feeAmount(source.injectionFeeText),
    ctFeeText: feeAmountOrZero(undefined),
    therapyFeeText: feeAmount(source.therapyFeeText),
    mriFeeText: feeAmountOrZero(undefined),
    materialFeeText: feeAmount(source.materialFeeText),
    originalAccountBalanceText: payAmount(source.originalAccountBalanceText),
    accountPayText: payAmount(source.accountPayText),
    invoiceCashPayText: payAmount(source.invoiceCashPayText),
    insurancePayText: payAmount(source.insurancePayText),
    workInjuryPayText: '',
    maternityPayText: '',
    accountBalanceText: payAmount(source.accountBalanceText),
    totalAmountChineseText: str(source.totalAmountChineseText),
    totalAmountText: feeAmount(source.totalAmountText) || payAmount(source.totalAmountText),
  };
}
