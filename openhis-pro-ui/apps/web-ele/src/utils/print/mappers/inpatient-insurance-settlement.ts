import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { formatMedType } from '#/chs/constants/medical-info-query';
import { formatInsutype } from '#/chs/constants/reconciliation';
import { formatPsnType } from '#/chs/constants/benefit-display';

/**
 * 住院医保结算单 PrintData（与 ip-insuranceSettlement-print 模板 field 一致）。
 *
 * <p>版式在院点库 prt_template；本文件只组数。</p>
 */
export interface InpatientInsuranceSettlementPrintData {
  documentTitle: string;
  settleDateText: string;
  printTimeText: string;
  patientName: string;
  genderText: string;
  ageText: string;
  nationText: string;
  idCardNo: string;
  psnNo: string;
  medTypeText: string;
  insuplcAdmdvs: string;
  inpatientNo: string;
  psnTypeText: string;
  admissionDiagnosisText: string;
  insutypeText: string;
  dischargeDiagnosisText: string;
  doctorName: string;
  stayDaysText: string;
  doctorCode: string;
  admissionDateText: string;
  dischargeDeptText: string;
  dischargeDateText: string;
  employerName: string;
  visitCountText: string;
  hospitalLevelText: string;
  hospitalName: string;
  hospitalCode: string;
  medfeeSumamtText: string;
  inscpScpAmtText: string;
  actPayDedcText: string;
  preselfpayAmtText: string;
  fulamtOwnpayAmtText: string;
  overlmtSelfpayText: string;
  poolPropSelfpayText: string;
  hifpPayText: string;
  mafPayText: string;
  hifobPayText: string;
  hifesPayText: string;
  hifmiPayText: string;
  acctPayText: string;
  cvlservPayText: string;
  psnCashPayText: string;
  psnPartAmtText: string;
  fundPaySumamtText: string;
  othPayText: string;
  acctMulaidPayText: string;
  balcText: string;
  remarksText: string;
  operatorName: string;
  patientSignText: string;
  processDateText: string;
  /** 模板 longText 占位；表格由库模板 formatter 读其它字段渲染 */
  settlementSheetTable: string;
}

/** 住院医保结算单 mapper 入参 */
export interface InpatientInsuranceSettlementPrintSource {
  documentTitle?: string;
  settleDateText?: string;
  printTimeText?: string;
  patientName?: string;
  genderText?: string;
  ageText?: string;
  nationText?: string;
  idCardNo?: string;
  psnNo?: string;
  medType?: string;
  medTypeText?: string;
  insuplcAdmdvs?: string;
  inpatientNo?: string;
  psnType?: string;
  psnTypeText?: string;
  admissionDiagnosisText?: string;
  insutype?: string;
  insutypeText?: string;
  dischargeDiagnosisText?: string;
  doctorName?: string;
  stayDaysText?: string;
  doctorCode?: string;
  admissionDateText?: string;
  dischargeDeptText?: string;
  dischargeDateText?: string;
  employerName?: string;
  visitCountText?: string;
  hospitalLevelText?: string;
  hospitalName?: string;
  hospitalCode?: string;
  medfeeSumamt?: null | number | string;
  inscpScpAmt?: null | number | string;
  actPayDedc?: null | number | string;
  preselfpayAmt?: null | number | string;
  fulamtOwnpayAmt?: null | number | string;
  overlmtSelfpay?: null | number | string;
  poolPropSelfpay?: null | number | string;
  hifpPay?: null | number | string;
  mafPay?: null | number | string;
  hifobPay?: null | number | string;
  hifesPay?: null | number | string;
  hifmiPay?: null | number | string;
  acctPay?: null | number | string;
  cvlservPay?: null | number | string;
  psnCashPay?: null | number | string;
  psnPartAmt?: null | number | string;
  fundPaySumamt?: null | number | string;
  othPay?: null | number | string;
  acctMulaidPay?: null | number | string;
  balc?: null | number | string;
  remarksText?: string;
  operatorName?: string;
  patientSignText?: string;
  processDateText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  const text = String(value).trim();
  if (text === '—' || text === '-') {
    return '';
  }
  return text;
}

function moneyText(value: null | number | string | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  return formatMoneyDisplay(value);
}

/** 报销比例：0.74 → 0.74；已是文案则原样 */
function ratioText(value: null | number | string | undefined): string {
  const raw = str(value);
  if (!raw) {
    return '';
  }
  const num = Number(raw);
  if (!Number.isFinite(num)) {
    return raw;
  }
  return String(num);
}

function nowText(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

const DEFAULT_REMARKS =
  '备注：总费用=符合医保费用+先行自付+全自费+超限价自费；基金支付总额=统筹+大额+大病+企补+救助+公务员等；个人支付=个人账户+个人现金；其他支出含个人账户等。';

/** 性别票面短文案（男/女） */
function resolveGenderShort(value: null | number | string | undefined): string {
  const raw = str(value);
  if (!raw) {
    return '';
  }
  if (raw.includes('男') || raw.toUpperCase() === 'MALE' || raw === '1') {
    return '男';
  }
  if (raw.includes('女') || raw.toUpperCase() === 'FEMALE' || raw === '2') {
    return '女';
  }
  return raw;
}

/** 住院医保结算单 → PrintData */
export function buildInpatientInsuranceSettlementPrintData(
  source: InpatientInsuranceSettlementPrintSource,
): InpatientInsuranceSettlementPrintData {
  const medTypeText = str(source.medTypeText) || formatMedType(source.medType);
  const insutypeText = str(source.insutypeText) || formatInsutype(source.insutype);
  const psnTypeRaw = formatPsnType(source.psnType);
  const psnTypeText =
    str(source.psnTypeText) || (psnTypeRaw === '—' ? '' : psnTypeRaw);
  const settleDate = str(source.settleDateText);
  const processDate = str(source.processDateText) || settleDate;
  const patientName = str(source.patientName);

  return {
    documentTitle: str(source.documentTitle) || '吉林省省医疗保险医疗费用结算单',
    settleDateText: settleDate,
    printTimeText: str(source.printTimeText) || nowText(),
    patientName,
    genderText: resolveGenderShort(source.genderText),
    ageText: str(source.ageText).replace(/岁$/, ''),
    nationText: str(source.nationText),
    idCardNo: str(source.idCardNo),
    psnNo: str(source.psnNo),
    medTypeText: medTypeText === '—' ? '' : medTypeText,
    insuplcAdmdvs: str(source.insuplcAdmdvs),
    inpatientNo: str(source.inpatientNo),
    psnTypeText,
    admissionDiagnosisText: str(source.admissionDiagnosisText),
    insutypeText: insutypeText === '—' ? '' : insutypeText,
    dischargeDiagnosisText: str(source.dischargeDiagnosisText),
    doctorName: str(source.doctorName),
    stayDaysText: str(source.stayDaysText),
    doctorCode: str(source.doctorCode),
    admissionDateText: str(source.admissionDateText),
    dischargeDeptText: str(source.dischargeDeptText),
    dischargeDateText: str(source.dischargeDateText),
    employerName: str(source.employerName),
    visitCountText: str(source.visitCountText),
    hospitalLevelText: str(source.hospitalLevelText),
    hospitalName: str(source.hospitalName),
    hospitalCode: str(source.hospitalCode),
    medfeeSumamtText: moneyText(source.medfeeSumamt),
    inscpScpAmtText: moneyText(source.inscpScpAmt),
    actPayDedcText: moneyText(source.actPayDedc),
    preselfpayAmtText: moneyText(source.preselfpayAmt),
    fulamtOwnpayAmtText: moneyText(source.fulamtOwnpayAmt),
    overlmtSelfpayText: moneyText(source.overlmtSelfpay),
    poolPropSelfpayText: ratioText(source.poolPropSelfpay),
    hifpPayText: moneyText(source.hifpPay),
    mafPayText: moneyText(source.mafPay),
    hifobPayText: moneyText(source.hifobPay),
    hifesPayText: moneyText(source.hifesPay),
    hifmiPayText: moneyText(source.hifmiPay),
    acctPayText: moneyText(source.acctPay),
    cvlservPayText: moneyText(source.cvlservPay),
    psnCashPayText: moneyText(source.psnCashPay),
    psnPartAmtText: moneyText(source.psnPartAmt),
    fundPaySumamtText: moneyText(source.fundPaySumamt),
    othPayText: moneyText(source.othPay),
    acctMulaidPayText: moneyText(source.acctMulaidPay),
    balcText: moneyText(source.balc),
    remarksText: str(source.remarksText) || DEFAULT_REMARKS,
    operatorName: str(source.operatorName),
    /** 患者签字留白，不预填姓名 */
    patientSignText: '',
    processDateText: processDate,
    settlementSheetTable: '',
  };
}
