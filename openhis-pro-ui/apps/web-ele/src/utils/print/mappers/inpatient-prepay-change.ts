import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { resolvePrepayReceiptChannelBucket } from './inpatient-prepay-receipt';

/** 找补方向：收取为补，退费为退 */
export type InpatientPrepayChangeKind = 'collect' | 'refund';

/** 住院预交金找补收据 PrintData（与 ip-prepayChange-print 模板 field 一致） */
export interface InpatientPrepayChangePrintData {
  hospitalName: string;
  documentTitle: string;
  dateText: string;
  patientName: string;
  departmentName: string;
  inpatientNo: string;
  /** 补 / 退 */
  changeKindText: string;
  amountText: string;
  cashPayText: string;
  wechatPayText: string;
  alipayPayText: string;
  bankPayText: string;
  cashierName: string;
}

/** 住院预交金找补收据 mapper 入参 */
export interface InpatientPrepayChangePrintSource {
  hospitalName?: string;
  documentTitle?: string;
  /** 已格式化日期；缺省从 paymentTimeText 取年月日 */
  dateText?: string;
  paymentTimeText?: string;
  departmentName?: string;
  inpatientNo?: string;
  patientName?: string;
  changeKind?: InpatientPrepayChangeKind;
  amountText?: string;
  amount?: null | number | string;
  payChannelCode?: string;
  payMethodText?: string;
  scanPayType?: string;
  cashPayText?: string;
  cashPayAmount?: null | number | string;
  wechatPayText?: string;
  wechatPayAmount?: null | number | string;
  alipayPayText?: string;
  alipayPayAmount?: null | number | string;
  bankPayText?: string;
  bankPayAmount?: null | number | string;
  cashierName?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function formatYuan(amount: null | number | string | undefined): string {
  if (amount === null || amount === undefined || amount === '') {
    return '¥0.00';
  }
  const num = Number(String(amount).replaceAll(',', '').trim());
  if (!Number.isFinite(num)) {
    return '¥0.00';
  }
  return `¥${formatMoneyDisplay(Math.abs(num))}`;
}

/** 退费金额带负号，与找补收据票面一致 */
function formatSignedYuan(
  amount: null | number | string | undefined,
  refund: boolean,
): string {
  const text = formatYuan(amount);
  if (!refund) {
    return text;
  }
  return text.replace('¥', '¥-');
}

function resolveDateText(source: InpatientPrepayChangePrintSource): string {
  const ready = str(source.dateText);
  if (ready) {
    return ready;
  }
  const raw = str(source.paymentTimeText);
  const matched = raw.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!matched) {
    return raw;
  }
  return `${matched[1]}年${matched[2]}月${matched[3]}日`;
}

function buildChannelAmounts(
  source: InpatientPrepayChangePrintSource,
  amountValue: null | number | string | undefined,
): Pick<
  InpatientPrepayChangePrintData,
  'alipayPayText' | 'bankPayText' | 'cashPayText' | 'wechatPayText'
> {
  const hasExplicit =
    str(source.cashPayText) ||
    source.cashPayAmount != null ||
    str(source.wechatPayText) ||
    source.wechatPayAmount != null ||
    str(source.alipayPayText) ||
    source.alipayPayAmount != null ||
    str(source.bankPayText) ||
    source.bankPayAmount != null;

  if (hasExplicit) {
    return {
      cashPayText: str(source.cashPayText) || formatYuan(source.cashPayAmount),
      wechatPayText: str(source.wechatPayText) || formatYuan(source.wechatPayAmount),
      alipayPayText: str(source.alipayPayText) || formatYuan(source.alipayPayAmount),
      bankPayText: str(source.bankPayText) || formatYuan(source.bankPayAmount),
    };
  }

  const zero = '¥0.00';
  const bucket = resolvePrepayReceiptChannelBucket(
    source.payChannelCode,
    source.scanPayType,
    source.payMethodText,
  );
  const paid = formatYuan(amountValue);
  return {
    cashPayText: bucket === 'cash' ? paid : zero,
    wechatPayText: bucket === 'wechat' ? paid : zero,
    alipayPayText: bucket === 'alipay' ? paid : zero,
    bankPayText: bucket === 'bank' ? paid : zero,
  };
}

/** 住院预交金找补收据 → PrintData */
export function buildInpatientPrepayChangePrintData(
  source: InpatientPrepayChangePrintSource,
): InpatientPrepayChangePrintData {
  const refund = source.changeKind === 'refund';
  const amountText = str(source.amountText) || formatSignedYuan(source.amount, refund);
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '住院预交金找补收据',
    dateText: resolveDateText(source),
    patientName: str(source.patientName),
    departmentName: str(source.departmentName),
    inpatientNo: str(source.inpatientNo),
    changeKindText: refund ? '退' : '补',
    amountText,
    ...buildChannelAmounts(source, source.amount),
    cashierName: str(source.cashierName),
  };
}
