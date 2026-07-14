<script setup lang="ts">
import type { InpatientPharmacySummarySlipHeaderDto } from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-summary-dispense.types';

import { computed, ref } from 'vue';

import { RotateCw, Search } from '@vben/icons';

import { ElButton, ElDatePicker, ElInput, ElRadio, ElRadioGroup, ElTag } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';

import { formatSummaryApplyTime } from '../services/map-summary-dispense-ui';

defineOptions({
  name: 'InpatientDispenseSummaryQueuePanel',
});

interface Props {
  loading?: boolean;
  selectedSummaryNo: null | string;
  slipRows: InpatientPharmacySummarySlipHeaderDto[];
  totalCount: number;
}

const props = defineProps<Props>();

const emit = defineEmits<{
  dateRangeChange: [range: [string, string] | null];
  refresh: [];
  selectSummary: [summaryNo: string];
}>();

const keyword = defineModel<string>('keyword', { required: true });
const summaryDateRange = defineModel<[string, string] | null>('summaryDateRange', {
  required: true,
});
const summaryScope = defineModel<'completed' | 'pending'>('summaryScope', { required: true });

const isListRefreshing = ref(false);

/** 触发刷新动画并通知父级重拉列表 */
function handleRefresh() {
  isListRefreshing.value = true;
  emit('refresh');
  window.setTimeout(() => {
    isListRefreshing.value = false;
  }, 400);
}

/** 日期范围变更：空值回退为当天 */
function handleDateRangeChange(range: [string, string] | null) {
  if (range && range[0] && range[1]) {
    summaryDateRange.value = range;
    emit('dateRangeChange', range);
    return;
  }
  const today = new Date().toISOString().slice(0, 10);
  const fallback: [string, string] = [today, today];
  summaryDateRange.value = fallback;
  emit('dateRangeChange', fallback);
}

/** 汇总单卡片状态标签类型（仅以发放表门控为准） */
function resolveSummaryStatusTagType(row: InpatientPharmacySummarySlipHeaderDto) {
  if (row.summaryDispenseCompleted === true) {
    return 'success';
  }
  return 'warning';
}

/** 左侧列表标题（随待发/已发切换） */
const listTitle = computed(() =>
  summaryScope.value === 'pending' ? '待核对汇总单' : '已发药汇总单',
);

/** 空列表提示文案 */
const emptyListText = computed(() =>
  summaryScope.value === 'pending' ? '暂无待核对汇总单' : '暂无已发药汇总单',
);

/** 单击卡片：切换当前查看的汇总单 */
function handleCardClick(row: InpatientPharmacySummarySlipHeaderDto) {
  const summaryNo = String(row.busNo ?? '').trim();
  if (!summaryNo) return;
  emit('selectSummary', summaryNo);
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="mb-1.5 flex flex-col gap-1.5 px-2 pt-1.5">
      <SectionTitle :title="listTitle" />
      <ElRadioGroup
        v-model="summaryScope"
        class="dispense-summary-queue-scope flex flex-wrap items-center gap-x-4 gap-y-1"
      >
        <ElRadio value="pending">待核对</ElRadio>
        <ElRadio value="completed">已发药</ElRadio>
      </ElRadioGroup>
      <div class="summary-daterange-row w-full min-w-0">
        <ElDatePicker
          v-model="summaryDateRange"
          class="summary-daterange w-full"
          type="daterange"
          unlink-panels
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          @change="handleDateRangeChange($event as [string, string] | null)"
        />
      </div>
      <div class="flex min-w-0 items-center gap-1.5">
        <ElInput v-model="keyword" clearable placeholder="汇总单号" class="min-w-0 flex-1">
          <template #prefix>
            <Search class="h-4 w-4" />
          </template>
        </ElInput>
        <ElButton
          type="default"
          class="shrink-0 !px-2"
          :loading="props.loading"
          @click="handleRefresh"
        >
          <RotateCw class="h-4 w-4" :class="{ 'animate-spin': isListRefreshing }" />
        </ElButton>
      </div>
      <p class="text-xs text-[var(--el-text-color-secondary)]">共 {{ props.totalCount }} 张</p>
    </div>

    <div class="queue-list min-h-0 flex-1 space-y-1.5 overflow-y-auto px-2 pb-1">
      <template v-if="props.slipRows.length === 0">
        <div class="py-8 text-center text-sm text-[var(--el-text-color-secondary)]">
          {{ emptyListText }}
        </div>
      </template>
      <template v-else>
        <div
          v-for="row in props.slipRows"
          :key="row.busNo"
          class="summary-slip-card cursor-pointer rounded-md border border-solid px-2 py-2 transition-colors"
          :class="{
            'summary-slip-card--selected': selectedSummaryNo === row.busNo,
          }"
          @click="handleCardClick(row)"
        >
          <div class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-2">
                <span class="truncate text-sm font-semibold text-[var(--el-text-color-primary)]">
                  {{ row.busNo || '—' }}
                </span>
                <ElTag size="small" :type="resolveSummaryStatusTagType(row)" effect="plain">
                  {{ row.summaryDispenseStatusText }}
                </ElTag>
              </div>
              <p class="mt-1 truncate text-xs text-[var(--el-text-color-regular)]">
                {{ formatSummaryApplyTime(row.applyTime) }}
                <span v-if="row.purposeLocationName"> · {{ row.purposeLocationName }}</span>
              </p>
              <p class="mt-0.5 truncate text-xs text-[var(--el-text-color-secondary)]">
                申请人：{{ row.applicantName || '—' }}
              </p>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.summary-daterange-row :deep(.el-date-editor.el-date-editor--daterange) {
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  --el-date-editor-daterange-width: 100%;
}

.summary-slip-card {
  border-color: var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

.summary-slip-card:hover {
  border-color: var(--el-color-primary-light-5);
}

.summary-slip-card--selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
</style>
