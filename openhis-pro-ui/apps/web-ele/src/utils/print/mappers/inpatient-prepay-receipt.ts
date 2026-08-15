import { formatMoneyDisplay } from '#/utils/format/money-scale';

/** 住院预交金收据 PrintData（与 ip-prepayReceipt-print 模板 field 一致） */
export interface InpatientPrepayReceiptPrintData {
  documentTitle: string;
  departmentName: string;
  inpatientNo: string;
  patientName: string;
  patientIdNo: string;
  paymentTimeText: string;
  amountText: string;
  amountInWordsText: string;
  cashPayText: string;
  wechatPayText: string;
  alipayPayText: string;
  bankPayText: string;
  cashierName: string;
}

/** 住院预交金收据 mapper 入参 */
export interface InpatientPrepayReceiptPrintSource {
  /** 完整抬头；缺省时用 hospitalName +「预交金收据」 */
  documentTitle?: string;
  hospitalName?: string;
  departmentName?: string;
  inpatientNo?: string;
  patientName?: string;
  patientIdNo?: string;
  paymentTimeText?: string;
  amountText?: string;
  amount?: null | number | string;
  amountInWordsText?: string;
  /** 支付渠道 code：CASH / WECHAT / ALIPAY / BANK_CARD / SCAN_PAY */
  payChannelCode?: string;
  /** 支付方式展示（无 code 时兜底：微信/支付宝/现金/银行） */
  payMethodText?: string;
  /** 扫码 payType（用于 SCAN_PAY 细分微信/支付宝） */
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

/** 优先已格式化文案，否则按金额数值两位小数 */
function resolveMoneyText(
  text: string | undefined,
  amount: null | number | string | undefined,
): string {
  const formatted = str(text);
  if (formatted) {
    return formatted;
  }
  if (amount === null || amount === undefined || amount === '') {
    return '';
  }
  return formatMoneyDisplay(amount);
}

function resolveDocumentTitle(source: InpatientPrepayReceiptPrintSource): string {
  const title = str(source.documentTitle);
  if (title) {
    return title;
  }
  const hospitalName = str(source.hospitalName);
  if (hospitalName) {
    return `${hospitalName}预交金收据`;
  }
  return '预交金收据';
}

const CN_DIGITS = ['零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖'] as const;
const CN_UNITS = ['', '拾', '佰', '仟'] as const;
const CN_SECTIONS = ['', '万', '亿'] as const;

/** 将 0–9999 一段转为中文大写（不含段后缀） */
function sectionToChinese(section: number): string {
  if (section <= 0) {
    return '';
  }
  let n = section;
  let unitIndex = 0;
  let zero = false;
  let result = '';
  while (n > 0) {
    const digit = n % 10;
    if (digit === 0) {
      if (!zero && result) {
        zero = true;
        result = `${CN_DIGITS[0]}${result}`;
      }
    } else {
      zero = false;
      result = `${CN_DIGITS[digit]}${CN_UNITS[unitIndex]}${result}`;
    }
    n = Math.floor(n / 10);
    unitIndex += 1;
  }
  return result;
}

/**
 * 金额转人民币大写（如 1000 → 壹仟元整）。
 * 仅做展示格式化，供预交金收据 PrintData 使用。
 */
export function formatAmountInChineseWords(amount: null | number | string | undefined): string {
  if (amount === null || amount === undefined || amount === '') {
    return '';
  }
  const raw = String(amount).replaceAll(',', '').trim();
  const num = Number(raw);
  if (!Number.isFinite(num) || num < 0) {
    return '';
  }
  const fixed = formatMoneyDisplay(num);
  const [yuanPart = '0', fenPart = '00'] = fixed.split('.');
  let yuan = Number.parseInt(yuanPart, 10);
  const fen = Number.parseInt(fenPart.slice(0, 2).padEnd(2, '0'), 10);
  if (yuan === 0 && fen === 0) {
    return '零元整';
  }

  let yuanText = '';
  if (yuan === 0) {
    yuanText = '零元';
  } else {
    let sectionIndex = 0;
    let needZero = false;
    while (yuan > 0) {
      const section = yuan % 10_000;
      if (section === 0) {
        needZero = yuanText.length > 0;
      } else {
        const sectionText = sectionToChinese(section) + CN_SECTIONS[sectionIndex];
        yuanText = needZero ? `${sectionText}零${yuanText}` : `${sectionText}${yuanText}`;
        needZero = false;
      }
      yuan = Math.floor(yuan / 10_000);
      sectionIndex += 1;
    }
    yuanText = `${yuanText}元`;
  }

  if (fen === 0) {
    return `${yuanText}整`;
  }
  const jiao = Math.floor(fen / 10);
  const fenDigit = fen % 10;
  let fenText = '';
  if (jiao > 0) {
    fenText += `${CN_DIGITS[jiao]}角`;
  } else if (yuanPart !== '0') {
    fenText += '零';
  }
  if (fenDigit > 0) {
    fenText += `${CN_DIGITS[fenDigit]}分`;
  }
  return `${yuanText}${fenText}`;
}

/** 将支付渠道归到收据四列：现金 / 微信 / 支付宝 / 银行 */
export function resolvePrepayReceiptChannelBucket(
  payChannelCode?: string,
  scanPayType?: string,
  payMethodText?: string,
): 'alipay' | 'bank' | 'cash' | 'wechat' | null {
  const code = str(payChannelCode).toUpperCase();
  if (code === 'CASH') {
    return 'cash';
  }
  if (code === 'WECHAT') {
    return 'wechat';
  }
  if (code === 'ALIPAY') {
    return 'alipay';
  }
  if (code === 'BANK_CARD' || code === 'BANK') {
    return 'bank';
  }
  if (code === 'SCAN_PAY') {
    const payType = str(scanPayType).toUpperCase();
    if (payType.includes('ALIPAY') || payType.includes('ZFB')) {
      return 'alipay';
    }
    if (payType.includes('WECHAT') || payType.includes('WX') || payType.includes('WEIXIN')) {
      return 'wechat';
    }
    // 扫码未区分渠道时归微信列（与院内常见展示一致）
    return 'wechat';
  }
  // 无 code 时按展示文案兜底（兼容旧流水接口）
  const method = str(payMethodText);
  if (method.includes('微信')) {
    return 'wechat';
  }
  if (method.includes('支付宝')) {
    return 'alipay';
  }
  if (method.includes('现金')) {
    return 'cash';
  }
  if (method.includes('银行') || method.includes('银联') || method.includes('刷卡')) {
    return 'bank';
  }
  return null;
}

function buildChannelAmounts(
  source: InpatientPrepayReceiptPrintSource,
  amountValue: null | number | string | undefined,
): Pick<
  InpatientPrepayReceiptPrintData,
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
      cashPayText: resolveMoneyText(source.cashPayText, source.cashPayAmount) || '0.00',
      wechatPayText: resolveMoneyText(source.wechatPayText, source.wechatPayAmount) || '0.00',
      alipayPayText: resolveMoneyText(source.alipayPayText, source.alipayPayAmount) || '0.00',
      bankPayText: resolveMoneyText(source.bankPayText, source.bankPayAmount) || '0.00',
    };
  }

  const zero = '0.00';
  const bucket = resolvePrepayReceiptChannelBucket(
    source.payChannelCode,
    source.scanPayType,
    source.payMethodText,
  );
  const paid = resolveMoneyText(undefined, amountValue) || zero;
  return {
    cashPayText: bucket === 'cash' ? paid : zero,
    wechatPayText: bucket === 'wechat' ? paid : zero,
    alipayPayText: bucket === 'alipay' ? paid : zero,
    bankPayText: bucket === 'bank' ? paid : zero,
  };
}

/** 住院预交金收据 → PrintData */
export function buildInpatientPrepayReceiptPrintData(
  source: InpatientPrepayReceiptPrintSource,
): InpatientPrepayReceiptPrintData {
  const amountText = resolveMoneyText(source.amountText, source.amount);
  const amountInWordsText =
    str(source.amountInWordsText) || formatAmountInChineseWords(amountText || source.amount);
  return {
    documentTitle: resolveDocumentTitle(source),
    departmentName: str(source.departmentName),
    inpatientNo: str(source.inpatientNo),
    patientName: str(source.patientName),
    patientIdNo: str(source.patientIdNo),
    paymentTimeText: str(source.paymentTimeText),
    amountText,
    amountInWordsText,
    ...buildChannelAmounts(source, amountText || source.amount),
    cashierName: str(source.cashierName),
  };
}
