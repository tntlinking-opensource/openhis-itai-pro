import { ref } from 'vue';

import type { UnionPayWinBaseResponse } from '#/api/local/unionPayWin';
import {
  pollUnionPayOrder,
  type PollUnionPayOrderOptions,
} from '#/utils/payment/poll-union-pay-order';

export type UnionPayPollStatus = 'FAILED' | 'IDLE' | 'POLLING' | 'SUCCESS' | 'TIMEOUT';

export type UseUnionPayPollOptions = PollUnionPayOrderOptions;

/** 被扫超时后轮询 Win query-order（组件内可绑定 status） */
export function useUnionPayPoll() {
  const status = ref<UnionPayPollStatus>('IDLE');
  const lastResult = ref<UnionPayWinBaseResponse | null>(null);
  const errorMessage = ref('');
  let abortFlag = false;

  async function pollUntilDone(
    options: UseUnionPayPollOptions,
  ): Promise<UnionPayWinBaseResponse | null> {
    abortFlag = false;
    status.value = 'POLLING';
    errorMessage.value = '';
    lastResult.value = null;

    const result = await pollUnionPayOrder({
      ...options,
      isAborted: () => abortFlag,
    });

    if (abortFlag) {
      status.value = 'IDLE';
      return null;
    }
    if (result?.success) {
      status.value = 'SUCCESS';
      lastResult.value = result;
      return result;
    }

    status.value = 'TIMEOUT';
    errorMessage.value = '查询支付结果超时，请改现金或联系信息科查单';
    return null;
  }

  function cancelPoll() {
    abortFlag = true;
    status.value = 'IDLE';
  }

  function resetPoll() {
    abortFlag = true;
    status.value = 'IDLE';
    lastResult.value = null;
    errorMessage.value = '';
  }

  return {
    status,
    lastResult,
    errorMessage,
    pollUntilDone,
    cancelPoll,
    resetPoll,
  };
}
