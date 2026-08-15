import type { OptionDto } from '#/api/outpatient-registration';

export type PayChannelOption = { label: string; value: string };

/** 退费确认弹窗：渠道行（原路返回展示） */
export type RefundChannelDisplayItem = {
  amount?: number;
  label: string;
};

/** 初始化接口不可用时的兜底（不含 SCAN_PAY） */
export const FALLBACK_PAY_CHANNEL_OPTIONS: PayChannelOption[] = [
  { label: '现金', value: 'CASH' },
  { label: '微信', value: 'WECHAT' },
  { label: '支付宝', value: 'ALIPAY' },
  { label: '银行卡', value: 'BANK_CARD' },
];

export const FALLBACK_DEFAULT_PAY_CHANNEL = 'CASH';

/** 非收银员可选、但退费原路展示需要的落库渠道 */
const PERSISTED_REFUND_CHANNEL_LABELS: Record<string, string> = {
  ALIPAY: '支付宝',
  BANK_CARD: '银行卡',
  CASH: '现金',
  CASHIER: '收银台',
  INSURANCE_ACCT: '医保个账',
  INSURANCE_FUND: '医保统筹',
  SCAN_PAY: '扫码支付',
  WECHAT: '微信',
};

const INSURANCE_CHANNEL_CODES = new Set(['INSURANCE_ACCT', 'INSURANCE_FUND']);

export function optionDtosToPayChannelOptions(
  options: OptionDto[] | null | undefined,
): PayChannelOption[] {
  const merged: PayChannelOption[] = [];
  const seen = new Set<string>();
  for (const item of FALLBACK_PAY_CHANNEL_OPTIONS) {
    merged.push(item);
    seen.add(item.value);
  }
  for (const opt of options ?? []) {
    const value = opt.value?.trim();
    const label = opt.label?.trim();
    if (!value || !label || seen.has(value)) {
      continue;
    }
    merged.push({ label, value });
    seen.add(value);
  }
  return merged;
}

/** 渠道码 → 展示名（优先下拉选项，其次落库枚举文案） */
export function resolvePayChannelDisplayLabel(
  code: string,
  options: PayChannelOption[] = [],
): string {
  const normalized = code.trim();
  if (!normalized) {
    return '';
  }
  const fromOptions = options.find((opt) => opt.value === normalized)?.label?.trim();
  if (fromOptions) {
    return fromOptions;
  }
  return PERSISTED_REFUND_CHANNEL_LABELS[normalized] ?? normalized;
}

/**
 * 组装退费确认弹窗渠道行：医保统筹/个账按金额拆分，自付渠道合并展示并标注原路返回。
 */
export function buildRefundChannelDisplayItems(params: {
  originalPayChannelCodes?: string[] | null;
  payChannelOptions?: PayChannelOption[];
  selfPayAmount?: null | number;
  ybAcctPay?: null | number;
  ybFundPaySumamt?: null | number;
}): RefundChannelDisplayItem[] {
  const options = params.payChannelOptions ?? [];
  const items: RefundChannelDisplayItem[] = [];
  const fund = Number(params.ybFundPaySumamt ?? 0);
  const acct = Number(params.ybAcctPay ?? 0);
  const selfPay = Number(params.selfPayAmount ?? 0);
  const codes = (params.originalPayChannelCodes ?? [])
    .map((code) => String(code ?? '').trim())
    .filter(Boolean);

  if (Number.isFinite(fund) && fund > 0.001) {
    items.push({ label: '医保统筹', amount: fund });
  }
  if (Number.isFinite(acct) && acct > 0.001) {
    items.push({ label: '医保个账', amount: acct });
  }

  const selfPayCodes = codes.filter((code) => !INSURANCE_CHANNEL_CODES.has(code));
  const selfPayLabels = [
    ...new Set(
      selfPayCodes.map((code) => resolvePayChannelDisplayLabel(code, options)).filter(Boolean),
    ),
  ];
  if (Number.isFinite(selfPay) && selfPay > 0.001) {
    items.push({
      label: selfPayLabels.length > 0 ? selfPayLabels.join('、') : '原支付渠道',
      amount: selfPay,
    });
  } else if (selfPayLabels.length > 0 && items.length === 0) {
    items.push({
      label: selfPayLabels.join('、'),
    });
  }

  if (items.length === 0) {
    const labels = [
      ...new Set(codes.map((code) => resolvePayChannelDisplayLabel(code, options)).filter(Boolean)),
    ];
    items.push({
      label: labels.length > 0 ? labels.join('、') : '原支付渠道',
    });
  }
  return items;
}

/** @deprecated 业务逻辑勿用 label→code；请直接使用 payChannelCode */
/** @deprecated 结算/收费应直接绑定 payChannelCode，勿用 label 反查 code */
export function buildPayChannelLabelMap(options: PayChannelOption[]): Record<string, string> {
  const map: Record<string, string> = {};
  for (const opt of options) {
    map[opt.label] = opt.value;
  }
  return map;
}
