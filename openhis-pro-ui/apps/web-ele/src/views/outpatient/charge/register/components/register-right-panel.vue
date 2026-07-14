<script setup lang="ts">
import type { RegisterHistoryExpose } from '../services';
import type { DateRangeQuickSpec } from '#/utils/format/date-range-quick';

import { ref, watch } from 'vue';

import { Refresh } from '@element-plus/icons-vue';
import { ElButton, ElDatePicker } from 'element-plus';

import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { getDateRangeQuick } from '#/utils/format/date-range-quick';

import { loadTodayRegisterSummary } from '../services/register-stats.service';
import History from './history.vue';

defineOptions({
  name: 'RegisterRightPanel',
});

const props = withDefaults(
  defineProps<{
    dateRange: [string, string];
  }>(),
  {},
);

const emit = defineEmits<{
  dateRangeChange: [[string, string]];
  recordsChanged: [];
}>();

const statsLoading = ref(false);
const totalCount = ref(0);
const cancelledCount = ref(0);
const activeCount = ref(0);
const activeQuick = ref<'calendar-day' | 'calendar-month' | 'calendar-week'>('calendar-day');
const historyKeyword = ref('');

const historyRef = ref<null | RegisterHistoryExpose>(null);

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

async function refresh() {
  await refreshStats();
  await historyRef.value?.refresh();
}

function handleRecordsChanged() {
  void refreshStats();
  emit('recordsChanged');
}

function applyDateRangeQuick(spec: DateRangeQuickSpec) {
  activeQuick.value = spec.strategy;
  const range = getDateRangeQuick(spec) as [string, string];
  emit('dateRangeChange', range);
}

function handleDateRangeChange(value: [string, string] | null) {
  if (!value) {
    return;
  }
  activeQuick.value = 'calendar-day';
  emit('dateRangeChange', value);
}

async function handleKeywordSearch() {
  await historyRef.value?.search();
}

defineExpose({
  refresh,
});

watch(
  () => props.dateRange,
  () => {
    void historyRef.value?.refresh();
  },
);

void refreshStats();
</script>

<template>
  <section class="register-history-section flex h-full min-h-0 flex-col overflow-hidden">
    <div class="register-history-section__toolbar">
      <h3 class="register-history-section__title">历史就诊</h3>

      <SelectFuzzyInput
        v-model="historyKeyword"
        class="register-history-section__keyword"
        placeholder="患者姓名/就诊号"
        @search="handleKeywordSearch"
      />

      <div class="register-history-section__date-wrap">
        <ElDatePicker
          :model-value="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          @update:model-value="handleDateRangeChange"
        />
      </div>

      <div class="register-history-section__quick">
        <button
          type="button"
          class="register-history-section__quick-btn"
          :class="{ 'is-active': activeQuick === 'calendar-day' }"
          @click="applyDateRangeQuick({ strategy: 'calendar-day' })"
        >
          今天
        </button>
        <button
          type="button"
          class="register-history-section__quick-btn"
          :class="{ 'is-active': activeQuick === 'calendar-week' }"
          @click="applyDateRangeQuick({ strategy: 'calendar-week' })"
        >
          本周
        </button>
        <button
          type="button"
          class="register-history-section__quick-btn"
          :class="{ 'is-active': activeQuick === 'calendar-month' }"
          @click="applyDateRangeQuick({ strategy: 'calendar-month' })"
        >
          本月
        </button>
      </div>

      <div v-loading="statsLoading" class="register-history-section__stats">
        <span>当日挂号 {{ totalCount }}</span>
        <span>有效 {{ activeCount }}</span>
        <span>已退 {{ cancelledCount }}</span>
        <ElButton
          :icon="Refresh"
          circle
          text
          :loading="statsLoading"
          title="刷新"
          @click="refresh"
        />
      </div>
    </div>

    <History
      ref="historyRef"
      v-model:keyword="historyKeyword"
      class="min-h-0 flex-1"
      :date-range="dateRange"
      toolbar-mode="hidden"
      @records-changed="handleRecordsChanged"
    />
  </section>
</template>

<style scoped>
.register-history-section {
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.register-history-section__toolbar {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  overflow-x: auto;
}

.register-history-section__title {
  flex-shrink: 0;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.register-history-section__date-wrap {
  flex: 0 0 auto;
  width: 280px;
}

.register-history-section__date-wrap :deep(.el-date-editor--daterange) {
  width: 280px;
  max-width: 280px;
  padding-right: 6px;
  padding-left: 8px;
}

.register-history-section__date-wrap :deep(.el-range-input) {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
  font-size: 13px;
}

.register-history-section__date-wrap :deep(.el-range-separator) {
  flex: 0 0 auto;
  padding: 0 2px;
  font-size: 12px;
  line-height: 1;
}

.register-history-section__quick {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
}

.register-history-section__quick-btn {
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-color-primary);
  cursor: pointer;
  background: var(--el-fill-color);
  border: none;
  border-radius: 20px;
  transition: background-color 0.2s ease;
  white-space: nowrap;
}

.register-history-section__quick-btn.is-active {
  color: #fff;
  background: var(--el-color-primary);
}

.register-history-section__keyword {
  flex: 0 0 auto;
  width: 220px;
  min-width: 220px;
  max-width: 220px;
}

.register-history-section__stats {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  padding: 2px 12px;
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  background: var(--el-fill-color);
  border-radius: 20px;
}

@media (max-width: 1679px) {
  .register-history-section {
    padding-top: 4px;
  }

  .register-history-section__toolbar {
    margin-bottom: 6px;
  }
}
</style>
