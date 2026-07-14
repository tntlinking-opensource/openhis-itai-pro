<script setup lang="ts">
import type { PricingConfirmationTableRow } from '../services/map-nurse-station-inpatient-billing';

import { computed } from 'vue';

import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE } from '#/utils/inpatient/encounter-clinical-order-gate';

defineOptions({
  name: 'InpatientBillingPricingConfirmationPanel',
});

const props = withDefaults(defineProps<Props>(), {
  canViewBilling: false,
  canOperateBilling: false,
  chargeMutationBlocked: false,
  cancelLoading: false,
  loading: false,
  pricingRows: () => [],
  pricingTimeRange: () => [],
  selectedRowIds: () => [],
  tableKey: 0,
});

const emit = defineEmits<{
  onBatchPricingClick: [];
  onCancelPricingClick: [];
  onPricingQuery: [];
  selectionChange: [ids: string[]];
  'update:pricingTimeRange': [value: string[]];
}>();

interface Props {
  canViewBilling?: boolean;
  canOperateBilling?: boolean;
  chargeMutationBlocked?: boolean;
  cancelLoading?: boolean;
  loading?: boolean;
  pricingRows?: PricingConfirmationTableRow[];
  pricingTimeRange?: string[];
  selectedRowIds?: string[];
  tableKey?: number;
}

const pricingTimeRangeModel = computed({
  get: () => props.pricingTimeRange,
  set: (value: string[]) => emit('update:pricingTimeRange', value),
});

/** 当前列表可取消划价的行 */
const cancellableRows = computed(() => props.pricingRows.filter((row) => row.canCancel));

/** 是否已勾选至少一条可取消行 */
const canCancelSelected = computed(
  () =>
    props.canOperateBilling &&
    props.selectedRowIds.some((id) => {
      const row = props.pricingRows.find((item) => item.id === id);
      return row?.canCancel;
    }),
);

/** 将金额格式化为两位小数金额文案。 */
function formatCurrency(value: number) {
  return `￥${formatMoneyDisplay(value)}`;
}

/** 仅待结算行允许勾选（用于取消划价） */
function pricingRowSelectable(row: PricingConfirmationTableRow) {
  return row.canCancel;
}

/** 表格勾选变化时回传稳定行 id 列表 */
function handleSelectionChange(selection: PricingConfirmationTableRow[]) {
  emit(
    'selectionChange',
    selection.map((row) => row.id),
  );
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
      <div class="billing-list-card__actions">
        <ElButton
          type="danger"
          plain
          :disabled="!canCancelSelected"
          :loading="cancelLoading"
          @click="emit('onCancelPricingClick')"
        >
          取消划价
        </ElButton>
        <ElButton
          type="primary"
          :disabled="!canOperateBilling"
          @click="emit('onBatchPricingClick')"
        >
          批量划价
        </ElButton>
      </div>
    </div>
    <ElAlert
      v-if="chargeMutationBlocked"
      :title="`${ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE}（仅可查看列表）`"
      type="warning"
      :closable="false"
    />
    <ElAlert
      v-else-if="!canViewBilling"
      title="住院记账仅支持单选患者，请在左侧只勾选 1 位患者后查看划价列表"
      type="warning"
      :closable="false"
    />
    <div
      v-else-if="cancellableRows.length === 0 && pricingRows.length > 0"
      class="billing-list-card__hint"
    >
      当前列表无待结算划价记录，已结算或退费中的项目不可取消划价
    </div>
    <ElTable
      :key="tableKey"
      v-loading="loading"
      :data="pricingRows"
      border
      stripe
      height="100%"
      row-key="id"
      @selection-change="handleSelectionChange"
    >
      <ElTableColumn
        type="selection"
        width="42"
        align="center"
        :selectable="pricingRowSelectable"
        :reserve-selection="false"
      />
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

.billing-list-card__actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

.billing-list-card__hint {
  color: var(--el-text-color-secondary);
  font-size: 13px;
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
