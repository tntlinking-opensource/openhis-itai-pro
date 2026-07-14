<script setup lang="ts">
import type { PractitionerWorkloadSummaryTotalsDto } from '#/api/report/practitioner-workload';

import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { fmtReportNumber } from '#/views/report/shared/report-format';

defineOptions({ name: 'PractitionerWorkloadDoctorCard' });

interface Props {
  loading?: boolean;
  practitionerName: string;
  summaryTotals: PractitionerWorkloadSummaryTotalsDto;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
});

const emit = defineEmits<{
  click: [];
}>();

const metrics = computed(() => [
  { label: '接诊人数', value: String(props.summaryTotals.encounterCountTotal ?? 0) },
  { label: '处方数量', value: String(props.summaryTotals.prescriptionCountTotal ?? 0) },
  { label: '诊疗项目', value: String(props.summaryTotals.activityItemCountTotal ?? 0) },
  {
    label: '开单总金额',
    value: fmtReportNumber(props.summaryTotals.receivableAmountTotal),
    highlight: true,
  },
]);
</script>

<template>
  <div
    role="button"
    tabindex="0"
    class="practitioner-workload-doctor-card"
    :class="{ 'practitioner-workload-doctor-card--loading': loading }"
    @click="emit('click')"
    @keydown.enter="emit('click')"
  >
    <div class="practitioner-workload-doctor-card__head">
      <div class="practitioner-workload-doctor-card__name">{{ practitionerName }}</div>
      <span class="practitioner-workload-doctor-card__link">
        明细
        <IconifyIcon icon="lucide:chevron-right" class="practitioner-workload-doctor-card__arrow" />
      </span>
    </div>

    <div class="practitioner-workload-doctor-card__metrics">
      <div
        v-for="metric in metrics"
        :key="metric.label"
        class="practitioner-workload-doctor-card__metric"
      >
        <span class="practitioner-workload-doctor-card__metric-label">{{ metric.label }}</span>
        <span
          class="practitioner-workload-doctor-card__metric-value"
          :class="{
            'practitioner-workload-doctor-card__metric-value--highlight': metric.highlight,
          }"
        >
          {{ metric.value }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.practitioner-workload-doctor-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px 16px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.practitioner-workload-doctor-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 2px 8px rgb(15 23 42 / 6%);
}

.practitioner-workload-doctor-card:hover .practitioner-workload-doctor-card__link {
  color: var(--el-color-primary);
}

.practitioner-workload-doctor-card--loading {
  opacity: 0.72;
  pointer-events: none;
}

.practitioner-workload-doctor-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.practitioner-workload-doctor-card__name {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.practitioner-workload-doctor-card__link {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  transition: color 0.15s ease;
}

.practitioner-workload-doctor-card__arrow {
  width: 14px;
  height: 14px;
}

.practitioner-workload-doctor-card__metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}

.practitioner-workload-doctor-card__metric {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.practitioner-workload-doctor-card__metric-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.practitioner-workload-doctor-card__metric-value {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.practitioner-workload-doctor-card__metric-value--highlight {
  color: var(--el-color-primary);
}
</style>
