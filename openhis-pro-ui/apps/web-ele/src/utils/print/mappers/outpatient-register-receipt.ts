/** 挂号小票收费项目行（与模板 chargeItemLines 列 field 一致） */
export interface OutpatientRegisterReceiptChargeLine {
  itemName: string;
  amountText: string;
  chargeCategoryText: string;
}

/** 挂号小票 PrintData（与 op-reg-print 模板 field 一致；来源 printRegisterTicket.slip） */
export interface OutpatientRegisterReceiptPrintData {
  hospitalName: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  feeNatureText: string;
  registerDepartmentName: string;
  /** 挂号医生 */
  doctorName: string;
  chargeItemLines: OutpatientRegisterReceiptChargeLine[];
  totalAmountText: string;
  poolingPayText: string;
  fundPayText: string;
  accountPayText: string;
  deductibleLineText: string;
  /** 公务员待遇（是/否） */
  cvlservFlagText: string;
  /** 公务员医疗补助金额 */
  cvlservPayText: string;
  cashPayText: string;
  wechatPayText: string;
  alipayPayText: string;
  accountBalanceText: string;
  cashierName: string;
  chargeTimeText: string;
}

/** printRegisterTicket.slip（后端 RegistrationSlipDto） */
export interface RegistrationSlipPrintSource {
  hospitalName?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  feeNatureText?: string;
  registerDepartmentName?: string;
  organizationName?: string;
  /** 挂号医生 */
  doctorName?: string;
  chargeItemLines?: OutpatientRegisterReceiptChargeLine[];
  totalAmountText?: string;
  poolingPayText?: string;
  fundPayText?: string;
  accountPayText?: string;
  deductibleLineText?: string;
  /** 公务员待遇（是/否） */
  cvlservFlagText?: string;
  /** 公务员医疗补助金额 */
  cvlservPayText?: string;
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
  lines: OutpatientRegisterReceiptChargeLine[] | undefined,
): OutpatientRegisterReceiptChargeLine[] {
  if (!Array.isArray(lines)) {
    return [];
  }
  return lines.map((line) => ({
    itemName: str(line.itemName),
    amountText: str(line.amountText),
    chargeCategoryText: str(line.chargeCategoryText),
  }));
}

/** 后端挂号小票 slip → PrintData */
export function buildOutpatientRegisterReceiptPrintData(
  source: RegistrationSlipPrintSource,
): OutpatientRegisterReceiptPrintData {
  return {
    hospitalName: str(source.hospitalName),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    feeNatureText: str(source.feeNatureText),
    registerDepartmentName: str(source.registerDepartmentName) || str(source.organizationName),
    doctorName: str(source.doctorName),
    chargeItemLines: mapChargeLines(source.chargeItemLines),
    totalAmountText: str(source.totalAmountText),
    poolingPayText: str(source.poolingPayText),
    fundPayText: str(source.fundPayText),
    accountPayText: str(source.accountPayText),
    deductibleLineText: str(source.deductibleLineText),
    cvlservFlagText: str(source.cvlservFlagText),
    cvlservPayText: str(source.cvlservPayText),
    cashPayText: str(source.cashPayText),
    wechatPayText: str(source.wechatPayText),
    alipayPayText: str(source.alipayPayText),
    accountBalanceText: str(source.accountBalanceText),
    cashierName: str(source.cashierName),
    chargeTimeText: str(source.chargeTimeText),
  };
}
