import type { OptionDto } from '#/api/outpatient-registration';

export type PayChannelOption = { label: string; value: string };

/** 初始化接口不可用时的兜底（不含 SCAN_PAY） */
export const FALLBACK_PAY_CHANNEL_OPTIONS: PayChannelOption[] = [
  { label: '现金', value: 'CASH' },
  { label: '微信', value: 'WECHAT' },
  { label: '支付宝', value: 'ALIPAY' },
  { label: '银行卡', value: 'BANK_CARD' },
];

export const FALLBACK_DEFAULT_PAY_CHANNEL = 'CASH';

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

export function buildPayChannelLabelMap(options: PayChannelOption[]): Record<string, string> {
  const map: Record<string, string> = {};
  for (const opt of options) {
    map[opt.label] = opt.value;
  }
  return map;
}
