<script setup lang="ts">
import type { PractitionerWorkloadSummaryTotalsDto } from '#/api/report/practitioner-workload';

import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { fmtReportNumber } from '#/views/report/shared/report-format';

defineOptions({ name: 'PractitionerWorkloadTotalsBar' });

interface Props {
  doctorCount?: number;
  summaryTotals?: PractitionerWorkloadSummaryTotalsDto;
}

const props = withDefaults(defineProps<Props>(), {
  doctorCount: 0,
  summaryTotals: () => ({}),
});

const totals = computed(() => props.summaryTotals ?? {});
</script>

<template>
  <div class="practitioner-workload-stats">
    <div class="practitioner-workload-stats__row">
      <div class="practitioner-workload-stats__card practitioner-workload-stats__card--doctor">
        <span class="practitioner-workload-stats__icon">
          <IconifyIcon icon="lucide:users" />
        </span>
        <div class="practitioner-workload-stats__content">
          <span class="practitioner-workload-stats__label">医生人数</span>
          <span class="practitioner-workload-stats__value">{{ doctorCount }}</span>
        </div>
      </div>

      <div class="practitioner-workload-stats__card practitioner-workload-stats__card--encounter">
        <span class="practitioner-workload-stats__icon">
          <IconifyIcon icon="lucide:user-check" />
        </span>
        <div class="practitioner-workload-stats__content">
          <span class="practitioner-workload-stats__label">挂号人次</span>
          <span class="practitioner-workload-stats__value">{{
            totals.registrationNetCountTotal ?? totals.encounterCountTotal ?? 0
          }}</span>
        </div>
      </div>

      <div class="practitioner-workload-stats__card practitioner-workload-stats__card--amount">
        <span class="practitioner-workload-stats__icon">
          <IconifyIcon icon="lucide:banknote" />
        </span>
        <div class="practitioner-workload-stats__content">
          <span class="practitioner-workload-stats__label">售价合计</span>
          <span
            class="practitioner-workload-stats__value practitioner-workload-stats__value--amount"
          >
            {{ fmtReportNumber(totals.saleAmountTotal) }}
          </span>
        </div>
      </div>
    </div>

    <div class="practitioner-workload-stats__row practitioner-workload-stats__row--amount">
      <div class="practitioner-workload-stats__card practitioner-workload-stats__card--activity">
        <span class="practitioner-workload-stats__icon">
          <IconifyIcon icon="lucide:activity" />
        </span>
        <div class="practitioner-workload-stats__content">
          <span class="practitioner-workload-stats__label">诊疗项目</span>
          <span class="practitioner-workload-stats__value">{{
            totals.activityItemCountTotal ?? 0
          }}</span>
        </div>
      </div>

      <div
        class="practitioner-workload-stats__card practitioner-workload-stats__card--prescription"
      >
        <span class="practitioner-workload-stats__icon">
          <IconifyIcon icon="lucide:file-text" />
        </span>
        <div class="practitioner-workload-stats__content">
          <span class="practitioner-workload-stats__label">处方数量</span>
          <span class="practitioner-workload-stats__value">{{
            totals.prescriptionCountTotal ?? 0
          }}</span>
        </div>
      </div>

      <div
        class="practitioner-workload-stats__card practitioner-workload-stats__card--amount-total"
      >
        <span class="practitioner-workload-stats__icon">
          <IconifyIcon icon="lucide:wallet" />
        </span>
        <div class="practitioner-workload-stats__content">
          <span class="practitioner-workload-stats__label">进价合计</span>
          <span
            class="practitioner-workload-stats__value practitioner-workload-stats__value--amount-total"
          >
            {{ fmtReportNumber(totals.purchaseAmountTotal) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.practitioner-workload-stats {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.practitioner-workload-stats__row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.practitioner-workload-stats__card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: #fff;
}

.practitioner-workload-stats__icon {
  display: flex;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 18px;
}

.practitioner-workload-stats__card--doctor .practitioner-workload-stats__icon {
  background: linear-gradient(135deg, #bfdbfe 0%, #93c5fd 100%);
  color: #1d4ed8;
}

.practitioner-workload-stats__card--encounter .practitioner-workload-stats__icon {
  background: linear-gradient(135deg, #a7f3d0 0%, #6ee7b7 100%);
  color: #047857;
}

.practitioner-workload-stats__card--prescription .practitioner-workload-stats__icon {
  background: linear-gradient(135deg, #fde68a 0%, #fcd34d 100%);
  color: #b45309;
}

.practitioner-workload-stats__card--activity .practitioner-workload-stats__icon {
  background: linear-gradient(135deg, #bfdbfe 0%, #93c5fd 100%);
  color: #1d4ed8;
}

.practitioner-workload-stats__card--amount .practitioner-workload-stats__icon,
.practitioner-workload-stats__card--amount-total .practitioner-workload-stats__icon {
  background: linear-gradient(135deg, #e9d5ff 0%, #d8b4fe 100%);
  color: #7e22ce;
}

.practitioner-workload-stats__card--amount-total {
  border-color: var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}

.practitioner-workload-stats__content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.practitioner-workload-stats__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.practitioner-workload-stats__value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.practitioner-workload-stats__value--amount,
.practitioner-workload-stats__value--amount-total {
  font-size: 18px;
}

.practitioner-workload-stats__value--amount-total {
  color: var(--el-color-primary);
}
</style>
