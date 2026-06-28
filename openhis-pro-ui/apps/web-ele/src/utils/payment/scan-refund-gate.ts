/** 可能被银联被扫落库的渠道（须再查 BPC SUCCESS 才能确认 Win 退货）。 */
const UNION_PAY_POSSIBLE_PAY_CHANNEL_CODES = new Set(['ALIPAY', 'BANK_CARD', 'WECHAT']);

/**
 * 原支付是否可能存在银联被扫段。
 *
 * 真源为 {@code fin_payment_rec_detail.pay_enum}；仅现金/医保等时返回 false，不调 BPC。
 */
export function mayRequireUnionPayBpcRefund(payChannelCodes?: null | string[]): boolean {
  if (!payChannelCodes?.length) {
    return false;
  }
  return payChannelCodes.some((code) => {
    const normalized = String(code ?? '')
      .trim()
      .toUpperCase();
    return normalized.length > 0 && UNION_PAY_POSSIBLE_PAY_CHANNEL_CODES.has(normalized);
  });
}
