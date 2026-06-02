<script setup lang="ts">
import type { PricingConfirmationTableRow } from '../services/map-nurse-station-inpatient-billing';

import { computed } from 'vue';

defineOptions({
  name: 'InpatientBillingPricingConfirmationPanel',
});

const props = withDefaults(defineProps<Props>(), {
  canOperateBilling: false,
  loading: false,
  pricingRows: () => [],
  pricingTimeRange: () => [],
});

const emit = defineEmits<{
  onBatchPricingClick: [];
  onPricingQuery: [];
  'update:pricingTimeRange': [value: string[]];
}>();

interface Props {
  canOperateBilling?: boolean;
  loading?: boolean;
  pricingRows?: PricingConfirmationTableRow[];
  pricingTimeRange?: string[];
}

const pricingTimeRangeModel = computed({
  get: () => props.pricingTimeRange,
  set: (value: string[]) => emit('update:pricingTimeRange', value),
});

/** 将金额格式化为两位小数金额文案。 */
function formatCurrency(value: number) {
  return `￥${value.toFixed(2)}`;
}
</script>

<template>
  <div class="billing-list-card flex min-h-0 flex-1 flex-col">
    <div class="billing-list-card__toolbar">
      <div class="billing-list-card__query-actions">
        <ElDatePicker
          v-model="pricingTimeRangeModel"
          type="daterange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          range-separator="至"
          clearable
          style="width: 260px; max-width: 260px; flex: 0 0 260px"
        />
        <ElButton type="primary" :loading="loading" @click="emit('onPricingQuery')">查询</ElButton>
      </div>
      <ElButton type="primary" :disabled="!canOperateBilling" @click="emit('onBatchPricingClick')">
        批量划价
      </ElButton>
    </div>
    <ElAlert
      v-if="!canOperateBilling"
      title="住院记账仅支持单选患者，请在左侧只勾选 1 位患者后查看划价列表"
      type="warning"
      :closable="false"
    />
    <ElTable v-loading="loading" :data="pricingRows" border stripe height="100%" row-key="id">
      <ElTableColumn prop="pricingContent" label="划价内容" min-width="200" show-overflow-tooltip />
      <ElTableColumn prop="pricingModeDisplay" label="划价模式" width="100" align="center" />
      <ElTableColumn prop="quantityText" label="数量" min-width="90" align="center" />
      <ElTableColumn label="单价" min-width="100" align="right">
        <template #default="{ row }">
          {{ formatCurrency(row.unitPrice) }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="总价" min-width="110" align="right">
        <template #default="{ row }">
          {{ formatCurrency(row.totalPrice) }}
        </template>
      </ElTableColumn>
      <ElTableColumn prop="pricingTime" label="划价时间" min-width="170" />
      <ElTableColumn prop="statusText" label="状态" min-width="120" align="center">
        <template #default="{ row }">
          <ElTag effect="light">{{ row.statusText }}</ElTag>
        </template>
      </ElTableColumn>
    </ElTable>
  </div>
</template>

<style scoped>
.billing-list-card {
  gap: 10px;
}

.billing-list-card__toolbar {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
  min-height: 32px;
}

.billing-list-card__query-actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

.billing-list-card :deep(.el-table__header th.el-table__cell) {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.billing-list-card :deep(.el-table__header-wrapper) {
  border-bottom: 1px solid var(--el-border-color-light);
}
</style>
