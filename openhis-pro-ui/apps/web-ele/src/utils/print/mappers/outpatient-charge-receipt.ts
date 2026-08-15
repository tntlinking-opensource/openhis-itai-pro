import { formatMoneyDisplay, sumMoneyThenSettle } from '#/utils/format/money-scale';

/** 收费小票收费项目行（与模板 chargeItemLines 列 field 一致） */
export interface OutpatientChargeReceiptChargeLine {
  itemName: string;
  quantityText: string;
  amountText: string;
  /** 医保类别展示（甲类/乙类/自费等） */
  chargeCategoryText: string;
}

/** 门诊收费小票 PrintData（与 op-charge-print 模板 field 一致） */
export interface OutpatientChargeReceiptPrintData {
  hospitalName: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  feeNatureText: string;
  chargeItemLines: OutpatientChargeReceiptChargeLine[];
  totalAmountText: string;
  poolingPayText: string;
  fundPayText: string;
  accountPayText: string;
  deductibleLineText: string;
  /** 公务员待遇（是/否） */
  cvlservFlagText: string;
  /** 公务员医疗补助金额 */
  cvlservPayText: string;
  /** 其他支出（含电子钱包等） */
  othPayText: string;
  cashPayText: string;
  wechatPayText: string;
  alipayPayText: string;
  accountBalanceText: string;
  cashierName: string;
  chargeTimeText: string;
}

/** printChargeReceipt.slip（后端 ChargeReceiptSlipDto） */
export interface ChargeReceiptSlipPrintSource {
  hospitalName?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  feeNatureText?: string;
  chargeItemLines?: OutpatientChargeReceiptChargeLine[];
  totalAmountText?: string;
  poolingPayText?: string;
  fundPayText?: string;
  accountPayText?: string;
  deductibleLineText?: string;
  /** 公务员待遇（是/否） */
  cvlservFlagText?: string;
  /** 公务员医疗补助金额 */
  cvlservPayText?: string;
  /** 其他支出（含电子钱包等） */
  othPayText?: string;
  cashPayText?: string;
  wechatPayText?: string;
  alipayPayText?: string;
  accountBalanceText?: string;
  cashierName?: string;
  chargeTimeText?: string;
  encounterId?: null | number | string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapChargeLines(
  lines: OutpatientChargeReceiptChargeLine[] | undefined,
): OutpatientChargeReceiptChargeLine[] {
  if (!Array.isArray(lines)) {
    return [];
  }
  return lines.map((line) => ({
    itemName: str(line.itemName),
    quantityText: str(line.quantityText),
    amountText: str(line.amountText),
    chargeCategoryText: str(line.chargeCategoryText),
  }));
}

/**
 * 合计金额 = 收费项目行金额之和（医疗费总额）。
 * 后端 totalAmountText 可能误用 displayAmount（现金自付），有明细时以前端行合计为准。
 */
function resolveTotalAmountText(
  lines: OutpatientChargeReceiptChargeLine[],
  fallback: string,
): string {
  if (lines.length === 0) {
    return fallback;
  }
  const amounts = lines.map((line) => line.amountText).filter((text) => text.length > 0);
  if (amounts.length === 0) {
    return fallback;
  }
  return formatMoneyDisplay(sumMoneyThenSettle(amounts));
}

/** 后端收费小票 slip → PrintData */
export function buildOutpatientChargeReceiptPrintData(
  source: ChargeReceiptSlipPrintSource,
): OutpatientChargeReceiptPrintData {
  const chargeItemLines = mapChargeLines(source.chargeItemLines);
  return {
    hospitalName: str(source.hospitalName),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    feeNatureText: str(source.feeNatureText),
    chargeItemLines,
    totalAmountText: resolveTotalAmountText(chargeItemLines, str(source.totalAmountText)),
    poolingPayText: str(source.poolingPayText),
    fundPayText: str(source.fundPayText),
    accountPayText: str(source.accountPayText),
    deductibleLineText: str(source.deductibleLineText),
    cvlservFlagText: str(source.cvlservFlagText),
    cvlservPayText: str(source.cvlservPayText),
    othPayText: str(source.othPayText),
    cashPayText: str(source.cashPayText),
    wechatPayText: str(source.wechatPayText),
    alipayPayText: str(source.alipayPayText),
    accountBalanceText: str(source.accountBalanceText),
    cashierName: str(source.cashierName),
    chargeTimeText: str(source.chargeTimeText),
  };
}
