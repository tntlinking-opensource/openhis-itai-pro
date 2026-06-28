import type { PayChannelOption } from '#/utils/payment/payment-channel-options';

import { ref } from 'vue';

import { createSharedComposable } from '@vueuse/core';

import { useUnionPayScanPay } from '#/composables/payment/useUnionPayScanPay';
import {
  FALLBACK_PAY_CHANNEL_OPTIONS,
  optionDtosToPayChannelOptions,
} from '#/utils/payment/payment-channel-options';

import { loadRegisterFormInit } from '../services/register.service';

/** 挂号台支付渠道（就诊记录 / 确认弹窗共用，只拉一次 init；银联探测后台进行） */
export const useRegisterPayChannels = createSharedComposable(() => {
  const payChannelOptions = ref<PayChannelOption[]>([...FALLBACK_PAY_CHANNEL_OPTIONS]);
  let loadPromise: null | Promise<void> = null;

  const { scanPayEnabled, effectivePayChannelOptions, refreshUnionPayCapabilities } =
    useUnionPayScanPay(() => payChannelOptions.value);

  function ensurePayChannelsLoaded(): Promise<void> {
    if (loadPromise) {
      return loadPromise;
    }
    loadPromise = (async () => {
      try {
        const init = await loadRegisterFormInit({});
        payChannelOptions.value = optionDtosToPayChannelOptions(init.paymentChannelOptions);
      } catch {
        payChannelOptions.value = [...FALLBACK_PAY_CHANNEL_OPTIONS];
      }
      void refreshUnionPayCapabilities();
    })();
    return loadPromise;
  }

  return {
    payChannelOptions,
    scanPayEnabled,
    effectivePayChannelOptions,
    refreshUnionPayCapabilities,
    ensurePayChannelsLoaded,
  };
});
