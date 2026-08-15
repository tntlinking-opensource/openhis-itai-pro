<script setup lang="ts">
import type { PricingConfirmationTableRow } from '../services/map-nurse-station-inpatient-billing';

import { computed, nextTick, ref } from 'vue';

import dayjs from 'dayjs';

import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { ENCOUNTER_CLINICAL_ORDER_MUTATION_BLOCKED_MESSAGE } from '#/utils/inpatient/encounter-clinical-order-gate';

defineOptions({
  name: 'InpatientBillingPricingConfirmationPanel',
});

interface Props {
  canViewBilling?: boolean;
  canOperateBilling?: boolean;
  chargeMutationBlocked?: boolean;
  cancelLoading?: boolean;
  changePricingTimeLoading?: boolean;
  loading?: boolean;
  pricingRows?: PricingConfirmationTableRow[];
  pricingTimeRange?: string[];
  selectedRowIds?: string[];
  tableKey?: number;
}

const props = withDefaults(defineProps<Props>(), {
  canViewBilling: false,
  canOperateBilling: false,
  chargeMutationBlocked: false,
  cancelLoading: false,
  changePricingTimeLoading: false,
  loading: false,
  pricingRows: () => [],
  pricingTimeRange: () => [],
  selectedRowIds: () => [],
  tableKey: 0,
});

const emit = defineEmits<{
  onBatchPricingClick: [];
  onCancelPricingClick: [];
  onChangePricingTimeClick: [];
  onInlineChangePricingTime: [payload: { row: PricingConfirmationTableRow; newTime: string }];
  onPricingQuery: [];
  selectionChange: [ids: string[]];
  'update:pricingTimeRange': [value: string[]];
}>();

/** 就地改划价时间：当前行 id */
const inlineEditRowId = ref('');
/** 就地 DatePicker 值（YYYY-MM-DD HH:mm） */
const inlineEditValue = ref('');
/** 就地 DatePicker 实例（进入编辑后自动展开） */
const inlinePickerRef = ref<null | { handleOpen?: () => void }>(null);

const pricingTimeRangeModel = computed({
  get: () => props.pricingTimeRange,
  set: (value: string[]) => emit('update:pricingTimeRange', value),
});

/** 当前列表可冲销/可改时的行（待结算） */
const cancellableRows = computed(() => props.pricingRows.filter((row) => row.canCancel));

/** 是否已勾选至少一条可冲销/可改时行 */
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

/** 仅待结算行允许勾选（冲销 / 改划价时间） */
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

/** 展示时间 → DatePicker 用 YYYY-MM-DD HH:mm */
function toPricingTimePickerValue(display: string): string {
  const source = String(display ?? '').trim();
  if (!source || source === '—') {
    return dayjs().format('YYYY-MM-DD HH:mm');
  }
  const matched = source.match(/^(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2})/);
  if (matched?.[1]) {
    return matched[1].replace('T', ' ');
  }
  return dayjs().format('YYYY-MM-DD HH:mm');
}

/** 绑定就地 DatePicker 实例 */
function bindInlinePickerRef(el: unknown) {
  inlinePickerRef.value = el ? (el as { handleOpen?: () => void }) : null;
}

/** 退出就地改划价时间 */
function cancelInlinePricingTimeEdit() {
  inlineEditRowId.value = '';
  inlineEditValue.value = '';
  inlinePickerRef.value = null;
}

/** 当前行是否处于就地改划价时间 */
function isInlinePricingTimeEditing(row: PricingConfirmationTableRow) {
  return Boolean(inlineEditRowId.value) && inlineEditRowId.value === row.id;
}

/** 点「划价时间」进入就地改时（仅待结算且可写） */
async function beginInlinePricingTimeEdit(row: PricingConfirmationTableRow) {
  if (!props.canOperateBilling || !row.canCancel || props.changePricingTimeLoading) {
    return;
  }
  inlineEditRowId.value = row.id;
  inlineEditValue.value = toPricingTimePickerValue(row.pricingTime);
  await nextTick();
  inlinePickerRef.value?.handleOpen?.();
}

/** 确认就地改划价时间：规范化后交给父页提交 */
function confirmInlinePricingTime(row: PricingConfirmationTableRow) {
  if (!inlineEditRowId.value || inlineEditRowId.value !== row.id) {
    return;
  }
  const raw = String(inlineEditValue.value ?? '').trim();
  if (!raw) {
    cancelInlinePricingTimeEdit();
    return;
  }
  // DatePicker 可能只到分钟，补秒后交给父页
  const matched = raw.match(/^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})(?::(\d{2}))?$/);
  const newTime = matched
    ? `${matched[1]} ${matched[2]}:${matched[3] || '00'}`
    : raw.replace('T', ' ');
  cancelInlinePricingTimeEdit();
  emit('onInlineChangePricingTime', { row, newTime });
}

/** 面板关闭时退出就地编辑（change 已提交则 rowKey 已空，此处为取消关闭） */
function handleInlinePricingTimeVisible(visible: boolean) {
  if (visible) {
    return;
  }
  void nextTick(() => {
    if (!inlineEditRowId.value) {
      return;
    }
    cancelInlinePricingTimeEdit();
  });
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
          冲销
        </ElButton>
        <ElButton
          plain
          :disabled="!canCancelSelected"
          :loading="changePricingTimeLoading"
          @click="emit('onChangePricingTimeClick')"
        >
          改划价时间
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
      当前列表无待结算划价记录，已结算或退费中的项目不可冲销或改划价时间
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
      <ElTableColumn label="划价时间" min-width="170">
        <template #default="{ row }">
          <div
            class="billing-pricing-time-cell"
            :class="{
              'is-editable': canOperateBilling && row.canCancel,
              'is-editing': isInlinePricingTimeEditing(row),
            }"
            :title="canOperateBilling && row.canCancel ? '点击修改划价时间' : undefined"
            @click.stop="beginInlinePricingTimeEdit(row)"
          >
            <ElDatePicker
              v-if="isInlinePricingTimeEditing(row)"
              :ref="bindInlinePickerRef"
              v-model="inlineEditValue"
              type="datetime"
              size="small"
              value-format="YYYY-MM-DD HH:mm"
              format="YYYY-MM-DD HH:mm"
              time-format="HH:mm"
              :clearable="false"
              :editable="false"
              teleported
              placement="bottom-start"
              class="billing-pricing-time-inline-picker"
              @click.stop
              @change="confirmInlinePricingTime(row)"
              @visible-change="handleInlinePricingTimeVisible"
            />
            <span v-else class="billing-pricing-time-cell__text">{{ row.pricingTime }}</span>
          </div>
        </template>
      </ElTableColumn>
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

.billing-pricing-time-cell {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 1px 4px;
  border-radius: 3px;
  transition: background-color 0.15s ease;
}

.billing-pricing-time-cell.is-editable:not(.is-editing) {
  cursor: pointer;
}

.billing-pricing-time-cell.is-editable:not(.is-editing):hover {
  background: var(--el-fill-color-light);
}

.billing-pricing-time-cell.is-editable:not(.is-editing):hover .billing-pricing-time-cell__text {
  color: var(--el-color-primary);
}

.billing-pricing-time-cell__text {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}

.billing-pricing-time-inline-picker {
  width: 148px;
}

.billing-pricing-time-inline-picker :deep(.el-input__wrapper) {
  padding: 0 4px;
  background: transparent;
  box-shadow: none !important;
  border-radius: 0;
  border-bottom: 1px solid var(--el-color-primary);
}

.billing-pricing-time-inline-picker :deep(.el-input__wrapper.is-focus),
.billing-pricing-time-inline-picker :deep(.el-input__wrapper:hover) {
  box-shadow: none !important;
  border-bottom-color: var(--el-color-primary);
}

.billing-pricing-time-inline-picker :deep(.el-input__prefix),
.billing-pricing-time-inline-picker :deep(.el-input__suffix) {
  display: none;
}

.billing-pricing-time-inline-picker :deep(.el-input__inner) {
  height: 20px;
  padding: 0;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  color: var(--el-color-primary);
}
</style>
