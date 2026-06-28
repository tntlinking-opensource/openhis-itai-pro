<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';

import { ElAlert, ElButton } from 'element-plus';

import { fetchPendingCompensationStatsApi } from '#/api/einvoice-boss/compensation';
import { refreshEinvoiceCompensationMenuBadge } from '#/composables/use-einvoice-compensation-badge';

const DISMISS_KEY = 'einvoice-compensation-alert-dismiss-date';
const POLL_MS = 60_000;

const router = useRouter();
const { hasAccessByCodes } = useAccess();

const pendingCount = ref(0);
const dismissedToday = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;

const canView = computed(() => hasAccessByCodes(['einvoice-boss.compensation.view']));

const visible = computed(() => canView.value && pendingCount.value > 0 && !dismissedToday.value);

const alertText = computed(
  () => `有 ${pendingCount.value} 笔电子发票待补偿开票，可能影响患者取票，请尽快处理。`,
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

function goCompensation() {
  router.push('/finance/einvoice/compensation');
}

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

defineExpose({ refreshStats });
</script>

<template>
  <div v-if="visible" class="px-3 pt-2">
    <ElAlert type="warning" show-icon :closable="false">
      <template #title>
        <div class="flex flex-wrap items-center gap-3">
          <span>{{ alertText }}</span>
          <ElButton size="small" type="warning" plain @click="goCompensation">去处理</ElButton>
          <ElButton size="small" link @click="dismissToday">今日不再提示</ElButton>
        </div>
      </template>
    </ElAlert>
  </div>
</template>
