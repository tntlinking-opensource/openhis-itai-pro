<script setup lang="ts">
import { computed } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { fmtReportNumber } from '#/views/report/shared/report-format';

defineOptions({ name: 'NurseWorkloadTotalsBar' });

interface Props {
  consumableTotalAmount?: number | string;
  consumableTotalQuantity?: number | string;
  executionTotalAmount?: number | string;
  executionTotalCount?: number;
  nurseCount?: number;
}

const props = withDefaults(defineProps<Props>(), {
  consumableTotalAmount: 0,
  consumableTotalQuantity: 0,
  executionTotalAmount: 0,
  executionTotalCount: 0,
  nurseCount: 0,
});

const amountTotal = computed(
  () => Number(props.executionTotalAmount ?? 0) + Number(props.consumableTotalAmount ?? 0),
);
</script>

<template>
  <div class="nurse-workload-stats">
    <div class="nurse-workload-stats__row">
      <div class="nurse-workload-stats__card nurse-workload-stats__card--nurse">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:users" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">护士人数</span>
          <span class="nurse-workload-stats__value">{{ nurseCount }}</span>
        </div>
      </div>

      <div class="nurse-workload-stats__card nurse-workload-stats__card--execution">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:activity" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">处置总次数</span>
          <span class="nurse-workload-stats__value">{{ executionTotalCount }}</span>
        </div>
      </div>

      <div class="nurse-workload-stats__card nurse-workload-stats__card--consumable">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:package" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">耗材总数量</span>
          <span class="nurse-workload-stats__value">
            {{ fmtReportNumber(consumableTotalQuantity, 2) }}
          </span>
        </div>
      </div>
    </div>

    <div class="nurse-workload-stats__row nurse-workload-stats__row--amount">
      <div class="nurse-workload-stats__card nurse-workload-stats__card--amount">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:banknote" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">处置金额</span>
          <span class="nurse-workload-stats__value nurse-workload-stats__value--amount">
            {{ fmtReportNumber(executionTotalAmount, 2) }}
          </span>
        </div>
      </div>

      <div class="nurse-workload-stats__card nurse-workload-stats__card--amount">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:wallet" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">耗材金额</span>
          <span class="nurse-workload-stats__value nurse-workload-stats__value--amount">
            {{ fmtReportNumber(consumableTotalAmount, 2) }}
          </span>
        </div>
      </div>

      <div class="nurse-workload-stats__card nurse-workload-stats__card--amount-total">
        <span class="nurse-workload-stats__icon">
          <IconifyIcon icon="lucide:circle-dollar-sign" />
        </span>
        <div class="nurse-workload-stats__content">
          <span class="nurse-workload-stats__label">金额合计</span>
          <span class="nurse-workload-stats__value nurse-workload-stats__value--amount-total">
            {{ fmtReportNumber(amountTotal, 2) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nurse-workload-stats {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nurse-workload-stats__row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.nurse-workload-stats__card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: #fff;
}

.nurse-workload-stats__icon {
  display: flex;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  font-size: 18px;
}

.nurse-workload-stats__card--nurse .nurse-workload-stats__icon {
  background: linear-gradient(135deg, #a7f3d0 0%, #6ee7b7 100%);
  color: #047857;
}

.nurse-workload-stats__card--execution .nurse-workload-stats__icon {
  background: linear-gradient(135deg, #bfdbfe 0%, #93c5fd 100%);
  color: #1d4ed8;
}

.nurse-workload-stats__card--consumable .nurse-workload-stats__icon {
  background: linear-gradient(135deg, #fde68a 0%, #fcd34d 100%);
  color: #b45309;
}

.nurse-workload-stats__card--amount .nurse-workload-stats__icon,
.nurse-workload-stats__card--amount-total .nurse-workload-stats__icon {
  background: linear-gradient(135deg, #e9d5ff 0%, #d8b4fe 100%);
  color: #7e22ce;
}

.nurse-workload-stats__card--amount-total {
  border-color: var(--el-color-primary-light-7);
  background: var(--el-color-primary-light-9);
}

.nurse-workload-stats__content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.nurse-workload-stats__label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.nurse-workload-stats__value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.nurse-workload-stats__value--amount,
.nurse-workload-stats__value--amount-total {
  font-size: 18px;
}

.nurse-workload-stats__value--amount-total {
  color: var(--el-color-primary);
}
</style>
