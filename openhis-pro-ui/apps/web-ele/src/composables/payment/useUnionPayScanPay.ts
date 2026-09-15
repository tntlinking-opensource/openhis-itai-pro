import type { PayChannelOption } from '#/utils/payment/payment-channel-options';

import { computed, ref } from 'vue';

import { isUnionPayWinReady } from '#/api/local/openhisWin';
import { isUnionPayUiEnabled } from '#/utils/project-optional-modules';

const SCAN_PAY_OPTION: PayChannelOption = { label: '银联扫码支付', value: 'SCAN_PAY' };

/** 银联扫码：院点开启时展示入口；Win native 就绪才允许真正收单 */
export function useUnionPayScanPay(basePayChannelOptions: () => PayChannelOption[]) {
  const unionPayWinReady = ref(false);
  const unionPayUiEnabled = isUnionPayUiEnabled();

  async function refreshUnionPayCapabilities() {
    if (!unionPayUiEnabled) {
      unionPayWinReady.value = false;
      return;
    }
    unionPayWinReady.value = await isUnionPayWinReady();
  }

  /** 院点关闭银联时剔除 SCAN_PAY；开启时保证列表含扫码项 */
  const effectivePayChannelOptions = computed(() => {
    const mapped = basePayChannelOptions().map((opt) =>
      opt.value === 'SCAN_PAY' ? { ...opt, label: SCAN_PAY_OPTION.label } : opt,
    );
    if (!unionPayUiEnabled) {
      return mapped.filter((opt) => opt.value !== 'SCAN_PAY');
    }
    if (!mapped.some((opt) => opt.value === 'SCAN_PAY')) {
      return [...mapped, SCAN_PAY_OPTION];
    }
    return mapped;
  });

  const hasScanPayChannel = computed(() =>
    effectivePayChannelOptions.value.some((opt) => opt.value === 'SCAN_PAY'),
  );

  /** 院点开启 + Win 就绪才允许真正发起银联被扫 */
  const scanPayEnabled = computed(
    () => unionPayUiEnabled && unionPayWinReady.value && hasScanPayChannel.value,
  );

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
