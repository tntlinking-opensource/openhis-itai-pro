<script setup lang="ts">
import type { NurseConsumableItemChip, NurseExecutionItemChip } from './nurse-workload-card-data';

import { computed } from 'vue';

import { IconifyIcon } from '@zxplat/icons';

import { fmtReportNumber } from '#/views/report/shared/report-format';

import { formatChipQuantity } from './nurse-workload-card-data';

defineOptions({ name: 'NurseWorkloadNurseCard' });

interface Props {
  consumableDiscountAmountTotal?: number | string;
  consumableItemChips?: NurseConsumableItemChip[];
  consumableItemCount?: number;
  consumableSaleAmountTotal?: number | string;
  discountFeatureEnabled?: boolean;
  executionChips?: NurseExecutionItemChip[];
  executionDiscountAmountTotal?: number | string;
  executionSaleAmountTotal?: number | string;
  executionTotalCount?: number;
  loading?: boolean;
  practitionerName: string;
}

const props = withDefaults(defineProps<Props>(), {
  consumableDiscountAmountTotal: 0,
  consumableItemChips: () => [],
  consumableItemCount: 0,
  consumableSaleAmountTotal: 0,
  discountFeatureEnabled: false,
  executionChips: () => [],
  executionDiscountAmountTotal: 0,
  executionSaleAmountTotal: 0,
  executionTotalCount: 0,
  loading: false,
});

const emit = defineEmits<{
  viewDetail: [];
}>();

const hasExecution = computed(() => props.executionChips.length > 0);
const hasConsumable = computed(() => props.consumableItemChips.length > 0);

const saleTotal = computed(
  () => Number(props.executionSaleAmountTotal ?? 0) + Number(props.consumableSaleAmountTotal ?? 0),
);

const discountTotal = computed(
  () =>
    Number(props.executionDiscountAmountTotal ?? 0) +
    Number(props.consumableDiscountAmountTotal ?? 0),
);

const showDiscount = computed(() => Boolean(props.discountFeatureEnabled));
</script>

<template>
  <div class="nurse-workload-card" :class="{ 'nurse-workload-card--loading': loading }">
    <div class="nurse-workload-card__header">
      <div class="nurse-workload-card__meta">
        <span class="nurse-workload-card__name">{{ practitionerName }}</span>
        <span class="nurse-workload-card__summary">
          处置 <strong>{{ executionTotalCount }}</strong> 次
          <span class="nurse-workload-card__summary-dot">·</span>
          耗材 <strong>{{ consumableItemCount }}</strong> 项
          <span class="nurse-workload-card__summary-dot">·</span>
          售价合计 <strong>{{ fmtReportNumber(saleTotal, 2) }}</strong>
          <template v-if="showDiscount">
            <span class="nurse-workload-card__summary-dot">·</span>
            优惠 <strong>{{ fmtReportNumber(discountTotal, 2) }}</strong>
          </template>
        </span>
      </div>

      <button type="button" class="nurse-workload-card__detail-btn" @click="emit('viewDetail')">
        查看明细
        <IconifyIcon icon="lucide:chevron-right" />
      </button>
    </div>

    <div class="nurse-workload-card__body">
      <div class="nurse-workload-card__section">
        <span class="nurse-workload-card__section-label">处置执行</span>
        <div class="nurse-workload-card__chips">
          <template v-if="hasExecution">
            <span
              v-for="item in executionChips"
              :key="item.key"
              class="nurse-workload-card__chip nurse-workload-card__chip--execution"
            >
              <span class="nurse-workload-card__chip-label">{{ item.label }}</span>
              <span class="nurse-workload-card__chip-value">×{{ item.count }}</span>
            </span>
          </template>
          <span v-else class="nurse-workload-card__empty">暂无处置记录</span>
        </div>
      </div>

      <div class="nurse-workload-card__section">
        <span class="nurse-workload-card__section-label">耗材使用</span>
        <div class="nurse-workload-card__chips">
          <template v-if="hasConsumable">
            <span
              v-for="item in consumableItemChips"
              :key="item.key"
              class="nurse-workload-card__chip nurse-workload-card__chip--consumable"
            >
              <span class="nurse-workload-card__chip-label">{{ item.label }}</span>
              <span class="nurse-workload-card__chip-value">
                ×{{ formatChipQuantity(item.quantity) }}
              </span>
            </span>
          </template>
          <span v-else class="nurse-workload-card__empty">暂无耗材记录</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nurse-workload-card {
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: #fff;
}

.nurse-workload-card--loading {
  opacity: 0.7;
}

.nurse-workload-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.nurse-workload-card__meta {
  display: flex;
  min-width: 0;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px 14px;
}

.nurse-workload-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.nurse-workload-card__summary {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.nurse-workload-card__summary strong {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.nurse-workload-card__summary-dot {
  margin: 0 4px;
  color: var(--el-text-color-placeholder);
}

.nurse-workload-card__detail-btn {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 2px;
  padding: 0;
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
}

.nurse-workload-card__detail-btn:hover {
  color: var(--el-color-primary-dark-2);
}

.nurse-workload-card__body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.nurse-workload-card__section {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.nurse-workload-card__section-label {
  flex-shrink: 0;
  width: 56px;
  padding-top: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.nurse-workload-card__chips {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.nurse-workload-card__chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.5;
}

.nurse-workload-card__chip--execution {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.nurse-workload-card__chip--consumable {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.nurse-workload-card__chip-label {
  word-break: break-all;
}

.nurse-workload-card__chip-value {
  flex-shrink: 0;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.nurse-workload-card__empty {
  padding-top: 2px;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
</style>
