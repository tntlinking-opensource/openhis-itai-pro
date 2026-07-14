<script setup lang="ts">
import type {
  DailySettlementHandoverChannelRowDto,
  DailySettlementHandoverPaymentItemDto,
  DailySettlementHandoverSummaryDto,
} from '#/api/charge/daily-settlement-handover';

import { computed, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElButton, ElCheckbox, ElDialog, ElTable, ElTableColumn } from 'element-plus';

import { formatMoneyDisplay } from '#/utils/format/money-scale';

defineOptions({ name: 'DailySettlementHandoverDialog' });

interface SummaryDisplayItem {
  key: string;
  label: string;
  unit: string;
}

interface Props {
  visible?: boolean;
  items?: DailySettlementHandoverPaymentItemDto[];
  handoverRows?: DailySettlementHandoverChannelRowDto[];
  handoverSummary?: DailySettlementHandoverSummaryDto;
  summaryDisplay?: readonly SummaryDisplayItem[];
  /** 0 元/收支抵净提示（BR-CS-011）。 */
  zeroNetIncomeHint?: boolean;
  loading?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  items: () => [],
  handoverRows: () => [],
  handoverSummary: undefined,
  summaryDisplay: () => [],
  zeroNetIncomeHint: false,
  loading: false,
});

const emit = defineEmits<{
  'update:visible': [value: boolean];
  confirm: [paymentIds: string[]];
}>();

/** 当前勾选的 payment 主键（string 保精度）。 */
const selectedPaymentIds = ref<string[]>([]);

/** 是否全选。 */
const allSelected = computed(() => {
  const ids = props.items
    .map((item) => item.paymentId?.trim())
    .filter((id): id is string => Boolean(id));
  if (ids.length === 0) {
    return false;
  }
  return ids.every((id) => selectedPaymentIds.value.includes(id));
});

/** 是否半选（表头 checkbox indeterminate）。 */
const isIndeterminate = computed(() => {
  const ids = props.items
    .map((item) => item.paymentId?.trim())
    .filter((id): id is string => Boolean(id));
  if (ids.length === 0) {
    return false;
  }
  const selectedCount = ids.filter((id) => selectedPaymentIds.value.includes(id)).length;
  return selectedCount > 0 && selectedCount < ids.length;
});

/** 弹窗可见性双向绑定。 */
const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

/** 下半区汇总展示列（按传入 summaryDisplay 顺序）。 */
const summaryColumns = computed(() => {
  const summary = props.handoverSummary;
  if (!summary) {
    return [];
  }
  return props.summaryDisplay.map(({ key, label, unit }) => {
    const raw = summary[key as keyof DailySettlementHandoverSummaryDto];
    let value = '—';
    if (raw !== undefined && raw !== null) {
      value = unit === '人' ? `${raw} ${unit}` : `${formatMoneyDisplay(raw)} ${unit}`;
    } else if (unit === '人') {
      value = `0 ${unit}`;
    } else {
      value = `0.00 ${unit}`;
    }
    return { label, value };
  });
});

/** 弹窗打开时默认全选全部候选 payment。 */
watch(
  () => props.visible,
  (visible) => {
    if (!visible) {
      return;
    }
    selectedPaymentIds.value = props.items
      .map((item) => item.paymentId?.trim())
      .filter((id): id is string => Boolean(id));
  },
  { immediate: true },
);

/** 候选集变化时同步勾选（保留仍存在的 id）。 */
watch(
  () => props.items,
  (items) => {
    if (!props.visible) {
      return;
    }
    const candidateIds = items
      .map((item) => item.paymentId?.trim())
      .filter((id): id is string => Boolean(id));
    if (selectedPaymentIds.value.length === 0) {
      selectedPaymentIds.value = [...candidateIds];
      return;
    }
    selectedPaymentIds.value = selectedPaymentIds.value.filter((id) => candidateIds.includes(id));
  },
  { deep: true },
);

// 格式化结算时间展示。
function formatBillDate(value?: string): string {
  if (!value?.trim()) {
    return '—';
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value;
}

// 合并支付方式名为顿号分隔串。
function formatPayMethods(item: DailySettlementHandoverPaymentItemDto): string {
  const names = (item.payMethods ?? [])
    .map((method) => method.payMethodName?.trim())
    .filter((name): name is string => Boolean(name) && name !== '收银台');
  if (names.length > 0) {
    return names.join('、');
  }
  return '—';
}

// 合并项目名：去重后展示，多项同名时汇总为「项目名（N项）」。
function formatChargeItemNames(item: DailySettlementHandoverPaymentItemDto): string {
  const rawNames = (item.chargeLines ?? [])
    .map((line) => line.itemName?.trim())
    .filter((name): name is string => Boolean(name) && name !== '收费项目');
  if (rawNames.length === 0) {
    const categoryLabel = item.categoryLabel?.trim();
    if (categoryLabel) {
      return categoryLabel;
    }
    return '—';
  }
  const uniqueNames: string[] = [];
  for (const name of rawNames) {
    if (!uniqueNames.includes(name)) {
      uniqueNames.push(name);
    }
  }
  const total = rawNames.length;
  if (uniqueNames.length === 1 && total > 1) {
    return `${uniqueNames[0]}（${total}项）`;
  }
  if (uniqueNames.length <= 3) {
    return uniqueNames.join('、');
  }
  return `${uniqueNames.slice(0, 3).join('、')}等${total}项`;
}

// 格式化净额展示（结算刻度 2 位 HALF_UP）。
function formatNetAmount(value?: string): string {
  return formatMoneyDisplay(value);
}

// 切换单行勾选。
function toggleRowSelection(paymentId: string | undefined, checked: boolean) {
  const id = paymentId?.trim();
  if (!id) {
    return;
  }
  if (checked) {
    if (!selectedPaymentIds.value.includes(id)) {
      selectedPaymentIds.value = [...selectedPaymentIds.value, id];
    }
    return;
  }
  selectedPaymentIds.value = selectedPaymentIds.value.filter((item) => item !== id);
}

// 表头全选/取消全选。
function toggleSelectAll(checked: boolean) {
  if (!checked) {
    selectedPaymentIds.value = [];
    return;
  }
  selectedPaymentIds.value = props.items
    .map((item) => item.paymentId?.trim())
    .filter((id): id is string => Boolean(id));
}

// 关闭弹窗。
function handleClose() {
  dialogVisible.value = false;
}

// 确认交账：至少勾选一笔。
function handleConfirm() {
  if (selectedPaymentIds.value.length === 0) {
    return;
  }
  emit('confirm', [...selectedPaymentIds.value]);
}
</script>

<template>
  <ElDialog
    v-model="dialogVisible"
    title="确认交账明细"
    width="960px"
    destroy-on-close
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="handover-dialog">
      <div v-if="zeroNetIncomeHint" class="handover-dialog__hint">
        本次含 0 元或收支抵净流水，无实收金额亦须交账归档。
      </div>
      <div class="handover-dialog__toolbar">
        <ElCheckbox
          :model-value="allSelected"
          :indeterminate="isIndeterminate"
          @change="(value) => toggleSelectAll(Boolean(value))"
        >
          全选（{{ selectedPaymentIds.length }}/{{ items.length }}）
        </ElCheckbox>
      </div>

      <ElTable :data="items" border stripe max-height="360" row-key="paymentId">
        <ElTableColumn width="52" align="center">
          <template #default="{ row }">
            <ElCheckbox
              :model-value="selectedPaymentIds.includes(row.paymentId ?? '')"
              @change="(value) => toggleRowSelection(row.paymentId, Boolean(value))"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="流水号" prop="paymentNo" min-width="140" show-overflow-tooltip />
        <ElTableColumn label="结算时间" min-width="168">
          <template #default="{ row }">
            {{ formatBillDate(row.billDate) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="项目" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatChargeItemNames(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="净额(元)" min-width="100" align="right">
          <template #default="{ row }">
            {{ formatNetAmount(row.netAmount) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="支付方式" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatPayMethods(row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn type="expand" width="48">
          <template #default="{ row }">
            <div class="handover-dialog__expand">
              <div v-if="row.chargeLines?.length" class="handover-dialog__expand-block">
                <div class="handover-dialog__expand-title">收费项目</div>
                <table class="handover-dialog__detail-table">
                  <thead>
                    <tr>
                      <th>项目</th>
                      <th>单价</th>
                      <th>数量</th>
                      <th>金额</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="(line, index) in row.chargeLines"
                      :key="`${row.paymentId}-line-${index}`"
                    >
                      <td>
                        {{
                          line.itemName?.trim() && line.itemName.trim() !== '收费项目'
                            ? line.itemName
                            : '—'
                        }}
                      </td>
                      <td>{{ formatMoneyDisplay(line.unitPrice) }}</td>
                      <td>{{ line.quantity ?? '—' }}</td>
                      <td>{{ formatMoneyDisplay(line.lineAmount) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-if="row.payMethods?.length" class="handover-dialog__expand-block">
                <div class="handover-dialog__expand-title">支付方式明细</div>
                <table class="handover-dialog__detail-table">
                  <thead>
                    <tr>
                      <th>方式</th>
                      <th>金额</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="(method, index) in row.payMethods"
                      :key="`${row.paymentId}-pay-${index}`"
                    >
                      <td>{{ method.payMethodName ?? method.payMethodCode ?? '—' }}</td>
                      <td>{{ formatMoneyDisplay(method.amount) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div
                v-if="!row.chargeLines?.length && !row.payMethods?.length"
                class="handover-dialog__expand-empty"
              >
                暂无明细
              </div>
            </div>
          </template>
        </ElTableColumn>
      </ElTable>

      <div v-if="handoverRows?.length || handoverSummary" class="handover-dialog__summary">
        <div class="handover-dialog__summary-title">本次交账合计（全选时与下方一致）</div>
        <div v-if="handoverRows?.length" class="handover-dialog__fin-type">
          <span
            v-for="(row, index) in handoverRows"
            :key="`${row.categoryCode ?? row.category}-${index}`"
            class="handover-dialog__fin-type-item"
          >
            {{ row.category ?? '—' }}：{{ formatMoneyDisplay(row.amount) }} {{ row.unit ?? '元' }}
          </span>
        </div>
        <div v-if="summaryColumns.length" class="handover-dialog__summary-grid">
          <div
            v-for="item in summaryColumns"
            :key="item.label"
            class="handover-dialog__summary-item"
          >
            <span class="handover-dialog__summary-label">{{ item.label }}</span>
            <span class="handover-dialog__summary-value">{{ item.value }}</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <ElButton @click="handleClose">取消</ElButton>
      <ElButton
        type="primary"
        :loading="loading"
        :disabled="selectedPaymentIds.length === 0"
        @click="handleConfirm"
      >
        确认交账（{{ selectedPaymentIds.length }} 笔）
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.handover-dialog__toolbar {
  margin-bottom: 12px;
}

.handover-dialog__hint {
  padding: 8px 12px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border-radius: 4px;
}

.handover-dialog__expand {
  padding: 8px 16px 12px 48px;
}

.handover-dialog__expand-block + .handover-dialog__expand-block {
  margin-top: 12px;
}

.handover-dialog__expand-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.handover-dialog__detail-table {
  width: 100%;
  font-size: 13px;
  border-collapse: collapse;
}

.handover-dialog__detail-table th,
.handover-dialog__detail-table td {
  padding: 4px 8px;
  text-align: left;
  border: 1px solid var(--el-border-color-lighter);
}

.handover-dialog__detail-table th {
  font-weight: 600;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light);
}

.handover-dialog__expand-empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.handover-dialog__summary {
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.handover-dialog__summary-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.handover-dialog__fin-type {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.handover-dialog__summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 12px;
}

.handover-dialog__summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}

.handover-dialog__summary-label {
  color: var(--el-text-color-regular);
}

.handover-dialog__summary-value {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
</style>
