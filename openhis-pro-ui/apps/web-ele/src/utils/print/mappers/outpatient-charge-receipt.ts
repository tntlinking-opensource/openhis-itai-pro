/** 收费小票收费项目行（与模板 chargeItemLines 列 field 一致） */
export interface OutpatientChargeReceiptChargeLine {
  itemName: string;
  quantityText: string;
  amountText: string;
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

/** 后端收费小票 slip → PrintData */
export function buildOutpatientChargeReceiptPrintData(
  source: ChargeReceiptSlipPrintSource,
): OutpatientChargeReceiptPrintData {
  return {
    hospitalName: str(source.hospitalName),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    feeNatureText: str(source.feeNatureText),
    chargeItemLines: mapChargeLines(source.chargeItemLines),
    totalAmountText: str(source.totalAmountText),
    poolingPayText: str(source.poolingPayText),
    fundPayText: str(source.fundPayText),
    accountPayText: str(source.accountPayText),
    deductibleLineText: str(source.deductibleLineText),
    cashPayText: str(source.cashPayText),
    wechatPayText: str(source.wechatPayText),
    alipayPayText: str(source.alipayPayText),
    accountBalanceText: str(source.accountBalanceText),
    cashierName: str(source.cashierName),
    chargeTimeText: str(source.chargeTimeText),
  };
}
