import type { Ref } from 'vue';

import type { NurseStationTaskIncreaseHint } from './nurse-station-task-notify.types';

import { onMounted, onUnmounted, ref, watch } from 'vue';

/** 须 `#/` 导入，院点 overrides 才能替换配置 */
import { NURSE_STATION_QUEUE_POLL } from '#/views/inpatient/nurse-station/nurse-station-queue-poll.config';

interface UseNurseStationQueuePollOptions {
  /** 静默重拉当前筛选下的患者队列（无 loading、失败吞掉） */
  silentReloadPatientQueue: () => Promise<NurseStationTaskIncreaseHint[]>;
  /** 手动刷新 loading 中时跳过轮询，避免抢写 */
  queueLoading: Ref<boolean>;
  /** 工作台是否已完成首次队列加载 */
  patientQueueBootstrapped: Ref<boolean>;
  /** 检测到待校/待执上升时回调（浮层提示） */
  onTaskIncreases?: (hints: NurseStationTaskIncreaseHint[]) => void;
}

/**
 * 国基等院点：进入护士站即静默轮询队列（与是否勾选患者无关）；页隐藏时暂停。
 */
export function useNurseStationQueuePoll(options: UseNurseStationQueuePollOptions) {
  const { onTaskIncreases, patientQueueBootstrapped, queueLoading, silentReloadPatientQueue } =
    options;
  const pollInFlight = ref(false);
  let timer: null | ReturnType<typeof setInterval> = null;

  async function tick() {
    if (!NURSE_STATION_QUEUE_POLL.enabled) {
      return;
    }
    // 仅等首次队列就绪；不依赖选中患者
    if (!patientQueueBootstrapped.value) {
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
      const increases = await silentReloadPatientQueue();
      if (increases.length > 0) {
        onTaskIncreases?.(increases);
      }
    } finally {
      pollInFlight.value = false;
    }
  }

  function startTimer() {
    stopTimer();
    if (!NURSE_STATION_QUEUE_POLL.enabled) {
      return;
    }
    const intervalMs = Math.max(5_000, NURSE_STATION_QUEUE_POLL.intervalMs || 15_000);
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
    if (typeof document === 'undefined') {
      return;
    }
    if (document.hidden) {
      return;
    }
    void tick();
  }

  onMounted(() => {
    if (!NURSE_STATION_QUEUE_POLL.enabled) {
      return;
    }
    startTimer();
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', onVisibilityChange);
    }
    // 若挂载时队列已就绪，立刻拉一枪（不依赖选中）
    if (patientQueueBootstrapped.value) {
      void tick();
    }
  });

  onUnmounted(() => {
    stopTimer();
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibilityChange);
    }
  });

  watch(patientQueueBootstrapped, (ready) => {
    if (ready && NURSE_STATION_QUEUE_POLL.enabled) {
      void tick();
    }
  });

  return {
    pollInFlight,
  };
}
