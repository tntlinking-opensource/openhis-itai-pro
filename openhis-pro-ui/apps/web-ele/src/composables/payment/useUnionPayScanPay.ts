import type { PayChannelOption } from '#/utils/payment/payment-channel-options';

import { computed, ref } from 'vue';

import { isUnionPayWinReady } from '#/api/local/openhisWin';

/** 银联扫码能力：Win native 就绪 + 渠道含 SCAN_PAY */
export function useUnionPayScanPay(basePayChannelOptions: () => PayChannelOption[]) {
  const unionPayWinReady = ref(false);

  async function refreshUnionPayCapabilities() {
    unionPayWinReady.value = await isUnionPayWinReady();
  }

  const scanPayEnabled = computed(
    () => unionPayWinReady.value && basePayChannelOptions().some((opt) => opt.value === 'SCAN_PAY'),
  );

  const effectivePayChannelOptions = computed(() => {
    if (unionPayWinReady.value) {
      return basePayChannelOptions();
    }
    return basePayChannelOptions().filter((opt) => opt.value !== 'SCAN_PAY');
  });

  return {
    unionPayWinReady,
    scanPayEnabled,
    effectivePayChannelOptions,
    refreshUnionPayCapabilities,
  };
}

export function isScanPayChannel(payChannelCode: string) {
  return payChannelCode === 'SCAN_PAY';
}
