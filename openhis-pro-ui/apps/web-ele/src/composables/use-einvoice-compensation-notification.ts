import type { NotificationItem } from '@vben/layouts';

import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { preferences } from '@vben/preferences';

import { fetchPendingCompensationStatsApi } from '#/api/einvoice-boss/compensation';
import { refreshEinvoiceCompensationMenuBadge } from '#/composables/use-einvoice-compensation-badge';
import { isEinvoiceUiEnabled } from '#/utils/project-optional-modules';

export const EINVOICE_COMPENSATION_NOTIFICATION_ID = 'einvoice-compensation-pending';

const DISMISS_KEY = 'einvoice-compensation-alert-dismiss-date';
const POLL_MS = 60_000;

export function isEinvoiceCompensationNotificationId(id: number | string) {
  return id === EINVOICE_COMPENSATION_NOTIFICATION_ID;
}

/**
 * 电子发票待补偿：写入右上角通知铃铛，不再占用顶栏布局高度。
 */
export function useEinvoiceCompensationNotification() {
  const router = useRouter();
  const { hasAccessByCodes } = useAccess();
  const einvoiceUiEnabled = isEinvoiceUiEnabled();

  const pendingCount = ref(0);
  const dismissedToday = ref(false);
  let timer: ReturnType<typeof setInterval> | null = null;

  const canView = computed(
    () => einvoiceUiEnabled && hasAccessByCodes(['einvoice-boss.compensation.view']),
  );

  function readDismissedToday() {
    const saved = localStorage.getItem(DISMISS_KEY);
    const today = new Date().toISOString().slice(0, 10);
    dismissedToday.value = saved === today;
  }

  async function refreshStats() {
    if (!canView.value) {
      pendingCount.value = 0;
      return;
    }
    try {
      const stats = await fetchPendingCompensationStatsApi();
      pendingCount.value = Number(stats?.pendingCount ?? 0);
      await refreshEinvoiceCompensationMenuBadge(pendingCount.value);
    } catch {
      // 模块未装配时静默
    }
  }

  function dismissToday() {
    const today = new Date().toISOString().slice(0, 10);
    localStorage.setItem(DISMISS_KEY, today);
    dismissedToday.value = true;
  }

  const notifications = computed<NotificationItem[]>(() => {
    if (!canView.value || pendingCount.value <= 0 || dismissedToday.value) {
      return [];
    }
    return [
      {
        avatar: preferences.logo.source,
        date: '待处理',
        id: EINVOICE_COMPENSATION_NOTIFICATION_ID,
        isRead: false,
        link: '/finance/einvoice/compensation',
        message: `有 ${pendingCount.value} 笔电子发票待补偿开票，可能影响患者取票，请尽快处理。`,
        title: '电子发票待补偿',
      },
    ];
  });

  onMounted(() => {
    readDismissedToday();
    void refreshStats();
    timer = setInterval(() => {
      void refreshStats();
    }, POLL_MS);
  });

  onUnmounted(() => {
    if (timer) {
      clearInterval(timer);
    }
  });

  watch(
    () => router.currentRoute.value.path,
    (path) => {
      if (path.startsWith('/finance/einvoice')) {
        void refreshStats();
      }
    },
  );

  return {
    dismissToday,
    einvoiceNotifications: notifications,
    refreshStats,
  };
}
