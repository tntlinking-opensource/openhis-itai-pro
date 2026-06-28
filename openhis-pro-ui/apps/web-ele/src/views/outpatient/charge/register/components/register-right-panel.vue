<script setup lang="ts">
import type { RegisterHistoryExpose, RegisterSlotSelectionPayload } from '../services';

import type { BookingEntityId } from '#/api/booking/booking-id';

import { ref } from 'vue';

import { Refresh } from '@element-plus/icons-vue';
import { ElButton, ElCard, ElTabPane, ElTabs, ElTag } from 'element-plus';

import { useRegisterBookingMode } from '../composables/use-register-booking-mode';
import { loadTodayRegisterSummary } from '../services/register-stats.service';
import DailySlotPanel from './daily-slot-panel.vue';
import History from './history.vue';

defineOptions({
  name: 'RegisterRightPanel',
});

const props = withDefaults(
  defineProps<{
    healthcareServiceId?: BookingEntityId;
    /** 左侧挂号表单当前科室，优先于顶部登录科室查询号源 */
    organizationId?: BookingEntityId;
    practitionerId?: BookingEntityId;
  }>(),
  {
    organizationId: undefined,
    practitionerId: undefined,
    healthcareServiceId: undefined,
  },
);

const emit = defineEmits<{
  slotSelected: [payload: RegisterSlotSelectionPayload];
}>();

const { bookingEnabled, modeLoading } = useRegisterBookingMode();

const activeTab = ref('history');
const statsLoading = ref(false);
const totalCount = ref(0);
const cancelledCount = ref(0);
const activeCount = ref(0);

const historyRef = ref<null | RegisterHistoryExpose>(null);
const slotPanelRef = ref<null | { refresh: () => Promise<void> }>(null);

async function refreshStats() {
  statsLoading.value = true;
  try {
    const res = await loadTodayRegisterSummary();
    totalCount.value = Number(res.totalCount ?? 0);
    cancelledCount.value = Number(res.cancelledCount ?? 0);
    activeCount.value = Number(res.activeCount ?? 0);
  } finally {
    statsLoading.value = false;
  }
}

async function refreshActiveTab() {
  if (activeTab.value === 'history') {
    await historyRef.value?.refresh();
    return;
  }
  if (activeTab.value === 'slots') {
    await slotPanelRef.value?.refresh();
  }
}

async function refresh() {
  await refreshStats();
  await historyRef.value?.refresh();
  if (activeTab.value === 'slots') {
    await slotPanelRef.value?.refresh();
  }
}

function handleTabChange(name: number | string) {
  activeTab.value = String(name);
  void refreshActiveTab();
}

defineExpose({
  refresh,
});

void refreshStats();
</script>

<template>
  <div class="flex h-full min-h-0 min-w-0 flex-1 flex-col overflow-hidden">
    <ElCard
      shadow="never"
      class="flex h-full min-h-0 flex-col overflow-hidden rounded-lg"
      body-class="flex flex-1 min-h-0 flex-col overflow-hidden !p-2 !pt-0"
    >
      <template #header>
        <div class="flex flex-wrap items-center gap-3">
          <div v-loading="statsLoading" class="flex flex-wrap items-center gap-2">
            <span class="text-sm text-[var(--el-text-color-regular)]">当日挂号</span>
            <ElTag type="primary" effect="plain">{{ totalCount }}</ElTag>
            <span class="text-sm text-[var(--el-text-color-regular)]">有效</span>
            <ElTag type="success" effect="plain">{{ activeCount }}</ElTag>
            <span class="text-sm text-[var(--el-text-color-regular)]">已退</span>
            <ElTag type="info" effect="plain">{{ cancelledCount }}</ElTag>
          </div>
          <ElButton
            class="ml-auto"
            :icon="Refresh"
            circle
            :loading="statsLoading"
            @click="refresh"
          />
        </div>
      </template>

      <div
        v-loading="modeLoading"
        class="register-right-panel__body flex min-h-0 flex-1 flex-col overflow-hidden"
      >
        <template v-if="!modeLoading">
          <div v-if="bookingEnabled" class="flex h-full min-h-0 flex-1 flex-col overflow-hidden">
            <ElTabs
              v-model="activeTab"
              class="register-right-panel__tabs flex-shrink-0"
              @tab-change="handleTabChange"
            >
              <ElTabPane label="就诊记录" name="history" />
              <ElTabPane label="当日号源" name="slots" />
            </ElTabs>
            <div class="register-right-panel__content relative min-h-0 flex-1 overflow-hidden">
              <History
                v-show="activeTab === 'history'"
                ref="historyRef"
                class="absolute inset-0 h-full"
                @records-changed="refreshStats"
              />
              <DailySlotPanel
                v-if="activeTab === 'slots'"
                ref="slotPanelRef"
                class="absolute inset-0 h-full"
                :organization-id="props.organizationId"
                :practitioner-id="props.practitionerId"
                :healthcare-service-id="props.healthcareServiceId"
                @slot-selected="(payload) => emit('slotSelected', payload)"
              />
            </div>
          </div>

          <div v-else class="h-full min-h-0 flex-1 overflow-hidden">
            <History ref="historyRef" class="h-full" @records-changed="refreshStats" />
          </div>
        </template>
      </div>
    </ElCard>
  </div>
</template>

<style scoped>
.register-right-panel__tabs :deep(.el-tabs__header) {
  margin-bottom: 8px;
}

.register-right-panel__tabs :deep(.el-tabs__content) {
  display: none;
}

.register-right-panel__body :deep(.el-loading-mask) {
  min-height: 160px;
}
</style>
