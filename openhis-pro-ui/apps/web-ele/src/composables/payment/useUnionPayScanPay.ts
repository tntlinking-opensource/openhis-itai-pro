import type { PayChannelOption } from '#/utils/payment/payment-channel-options';

import { computed, ref } from 'vue';

import { isUnionPayWinReady } from '#/api/local/openhisWin';

const SCAN_PAY_OPTION: PayChannelOption = { label: '银联扫码支付', value: 'SCAN_PAY' };

/** 银联扫码：始终展示入口；Win native 就绪才允许真正收单 */
export function useUnionPayScanPay(basePayChannelOptions: () => PayChannelOption[]) {
  const unionPayWinReady = ref(false);

  async function refreshUnionPayCapabilities() {
    unionPayWinReady.value = await isUnionPayWinReady();
  }

  /** 渠道列表始终含银联扫码（不依赖 Win 探测）；展示名固定为「银联扫码支付」 */
  const effectivePayChannelOptions = computed(() => {
    const mapped = basePayChannelOptions().map((opt) =>
      opt.value === 'SCAN_PAY' ? { ...opt, label: SCAN_PAY_OPTION.label } : opt,
    );
    if (!mapped.some((opt) => opt.value === 'SCAN_PAY')) {
      return [...mapped, SCAN_PAY_OPTION];
    }
    return mapped;
  });

  const hasScanPayChannel = computed(() =>
    effectivePayChannelOptions.value.some((opt) => opt.value === 'SCAN_PAY'),
  );

  /** Win 就绪才允许真正发起银联被扫 */
  const scanPayEnabled = computed(() => unionPayWinReady.value && hasScanPayChannel.value);

  return {
    unionPayWinReady,
    scanPayEnabled,
    hasScanPayChannel,
    effectivePayChannelOptions,
    refreshUnionPayCapabilities,
  };
}

export function isScanPayChannel(payChannelCode: string) {
  return payChannelCode === 'SCAN_PAY';
}
