import type { Ref } from 'vue';

import { onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue';

/** 须 `#/` 导入，院点 overrides 才能替换配置 */
import { OUTPATIENT_DISPENSE_QUEUE_POLL } from '#/views/outpatient/dispense/outpatient-dispense-queue-poll.config';

interface UseOutpatientDispenseQueuePollOptions {
  /** 静默重拉当前筛选下的患者队列（无 loading、失败吞掉） */
  silentReloadQueue: () => Promise<void>;
  /** 手动刷新 loading 中时跳过轮询，避免抢写 */
  queueLoading: Ref<boolean>;
  /** 是否已完成首次队列加载 */
  queueBootstrapped: Ref<boolean>;
}

/**
 * 国基等院点：进入门诊发药即静默轮询患者队列（与是否选中患者无关）；页隐藏或离开页签时暂停。
 */
export function useOutpatientDispenseQueuePoll(options: UseOutpatientDispenseQueuePollOptions) {
  const { queueBootstrapped, queueLoading, silentReloadQueue } = options;
  const pollInFlight = ref(false);
  let timer: null | ReturnType<typeof setInterval> = null;

  async function tick() {
    if (!OUTPATIENT_DISPENSE_QUEUE_POLL.enabled) {
      return;
    }
    if (!queueBootstrapped.value) {
      return;
    }
    if (typeof document !== 'undefined' && document.hidden) {
      return;
    }
    if (queueLoading.value || pollInFlight.value) {
      return;
    }
    pollInFlight.value = true;
    try {
      await silentReloadQueue();
    } finally {
      pollInFlight.value = false;
    }
  }

  function startTimer() {
    stopTimer();
    if (!OUTPATIENT_DISPENSE_QUEUE_POLL.enabled) {
      return;
    }
    const intervalMs = Math.max(5_000, OUTPATIENT_DISPENSE_QUEUE_POLL.intervalMs || 15_000);
    timer = setInterval(() => {
      void tick();
    }, intervalMs);
  }

  function stopTimer() {
    if (timer != null) {
      clearInterval(timer);
      timer = null;
    }
  }

  function onVisibilityChange() {
    if (typeof document === 'undefined' || document.hidden) {
      return;
    }
    void tick();
  }

  onMounted(() => {
    if (!OUTPATIENT_DISPENSE_QUEUE_POLL.enabled) {
      return;
    }
    startTimer();
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', onVisibilityChange);
    }
    if (queueBootstrapped.value) {
      void tick();
    }
  });

  onActivated(() => {
    if (!OUTPATIENT_DISPENSE_QUEUE_POLL.enabled) {
      return;
    }
    startTimer();
    if (queueBootstrapped.value) {
      void tick();
    }
  });

  onDeactivated(() => {
    stopTimer();
  });

  onUnmounted(() => {
    stopTimer();
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibilityChange);
    }
  });

  watch(queueBootstrapped, (ready) => {
    if (ready && OUTPATIENT_DISPENSE_QUEUE_POLL.enabled) {
      void tick();
    }
  });

  return {
    pollInFlight,
  };
}
