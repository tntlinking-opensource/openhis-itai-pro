<script setup lang="ts">
import type { PreSettleChargeLinePreviewDto } from '#/api/outpatient-charge/outpatient-charge.types';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElInputNumber,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { decimalAdd, decimalMul, decimalSub } from '#/utils/decimal-util';

import { OUTPATIENT_CHARGE_FEE_DETAIL_FIELD_DEFS } from './outpatient-charge.constants';

type PayChannelOption = { label: string; value: string };

interface InsuranceBreakdownItem {
  key: string;
  label: string;
  value: number;
}

defineOptions({
  name: 'ChargeSettlementDialog',
});

const props = withDefaults(
  defineProps<{
    /** 弹窗标题（默认：确认收费） */
    dialogTitle?: string;
    displayAmount?: number | string;
    insuranceBreakdownItems?: InsuranceBreakdownItem[];
    loading?: boolean;
    payChannelOptions?: PayChannelOption[];
    previews?: PreSettleChargeLinePreviewDto[];
    /** 主操作按钮文案（默认：确认收费） */
    primaryButtonText?: string;
    /** 中部表格卡片标题（默认：收费项目） */
    projectTableTitle?: string;
    /** 小结/底部高亮金额标签（默认：应收金额） */
    receivableAmountLabel?: string;
    selectedChargeCount?: number;
    summaryInsuranceAmount?: number | string;
    summarySelfPayAmount?: number | string;
  }>(),
  {
    dialogTitle: '确认收费',
    displayAmount: 0,
    insuranceBreakdownItems: () => [],
    loading: false,
    payChannelOptions: () => [],
    previews: () => [],
    primaryButtonText: '确认收费',
    projectTableTitle: '收费项目',
    receivableAmountLabel: '应收金额',
    selectedChargeCount: 0,
    summaryInsuranceAmount: 0,
    summarySelfPayAmount: 0,
  },
);

const emit = defineEmits<{
  confirmCharge: [];
}>();

const visible = defineModel<boolean>({ required: true });
const payChannelCode = defineModel<string>('payChannelCode', { required: true });

/** 弹窗内优惠金额（仅影响展示应收；正式结算金额仍以父组件传入的 displayAmount 为准） */
const localDiscountAmount = ref(0);

watch(visible, (open) => {
  if (open) {
    localDiscountAmount.value = 0;
  }
});

function formatMoney(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n.toFixed(2) : '0.00';
}

const displayAmountNum = computed(() => Number(props.displayAmount ?? 0));

const previewsTotal = computed(() => {
  let sum = 0;
  for (const row of props.previews) {
    sum = decimalAdd(sum, Number(row.lineAmount ?? 0));
  }
  return sum;
});

const receivableAmount = computed(() =>
  Math.max(decimalSub(displayAmountNum.value, localDiscountAmount.value), 0),
);

const feeDetailDisplayRows = computed(() => {
  const map = new Map<string, number>();
  for (const item of props.insuranceBreakdownItems) {
    map.set(item.key, item.value);
  }
  const rows: { key: string; label: string; value: number }[] = [];
  for (const def of OUTPATIENT_CHARGE_FEE_DETAIL_FIELD_DEFS) {
    rows.push({
      key: def.key,
      label: def.label,
      value: map.get(def.key) ?? 0,
    });
  }
  return rows;
});

const quickDiscounts = [
  { label: '95折', value: '95' },
  { label: '9折', value: '90' },
  { label: '85折', value: '85' },
];

const applyQuickDiscount = (type: string) => {
  const base = displayAmountNum.value;
  const ratio = Number(type) / 100;
  const target = decimalMul(base, ratio);
  localDiscountAmount.value = decimalSub(base, target);
};

const handleCancel = () => {
  visible.value = false;
};
</script>

<template>
  <AppDialog v-model="visible" :title="props.dialogTitle" width="800px">
    <div class="copy-dialog-body">
      <div class="copy-main-grid">
        <!-- 左上：费用明细 -->
        <div class="copy-card copy-insurance-card">
          <div class="copy-card-title">费用明细</div>
          <ElDescriptions :column="1" size="small" class="copy-insurance-list">
            <ElDescriptionsItem
              v-for="item in feeDetailDisplayRows"
              :key="item.key"
              :label="item.label"
              :class="{ 'is-key-row': item.key === 'finalReceivable' || item.key === 'cashPay' }"
            >
              ¥{{ formatMoney(item.value) }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </div>

        <!-- 右上：结算操作 -->
        <div class="copy-card">
          <div class="copy-card-title">结算操作</div>

          <div class="copy-section">
            <div class="copy-section-title">金额小结</div>
            <div class="copy-summary">
              <div class="copy-summary-item">
                <span>已选项目</span>
                <strong>{{ selectedChargeCount }} 项</strong>
              </div>
              <div class="copy-summary-item">
                <span>总金额</span>
                <strong>¥{{ formatMoney(previewsTotal) }}</strong>
              </div>
              <div class="copy-summary-item">
                <span>医保金额</span>
                <strong>¥{{ formatMoney(summaryInsuranceAmount) }}</strong>
              </div>
              <div class="copy-summary-item">
                <span>自费金额</span>
                <strong>¥{{ formatMoney(summarySelfPayAmount) }}</strong>
              </div>
              <div class="copy-summary-item is-highlight">
                <span>{{ props.receivableAmountLabel }}</span>
                <strong>¥{{ formatMoney(receivableAmount) }}</strong>
              </div>
            </div>
          </div>

          <div class="copy-section">
            <div class="copy-section-title">优惠设置</div>
            <div class="copy-discount-row">
              <span>优惠金额</span>
              <ElInputNumber
                v-model="localDiscountAmount"
                class="copy-input-number"
                :min="0"
                :max="displayAmountNum"
                controls-position="right"
                :precision="2"
                :step="0.01"
              />
            </div>
            <div class="copy-quick-discounts">
              <ElButton
                v-for="item in quickDiscounts"
                :key="item.value"
                size="small"
                @click="applyQuickDiscount(item.value)"
              >
                {{ item.label }}
              </ElButton>
            </div>
          </div>

          <div class="copy-section">
            <div class="copy-section-title">支付方式</div>
            <ElRadioGroup v-model="payChannelCode" class="copy-pay-group">
              <ElRadioButton v-for="opt in payChannelOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </ElRadioButton>
            </ElRadioGroup>
          </div>
        </div>
      </div>

      <!-- 中部：收费项目 -->
      <div class="copy-card">
        <div class="copy-card-title">{{ props.projectTableTitle }}</div>
        <ElTable
          :data="previews"
          border
          size="small"
          :max-height="previews.length > 9 ? 390 : undefined"
          row-key="chargeItemId"
        >
          <ElTableColumn prop="itemLabel" label="项目" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ row.itemLabel || '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="类别" width="80" align="center">
            <template #default>—</template>
          </ElTableColumn>
          <ElTableColumn label="单价" width="95" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.lineAmount) }}</template>
          </ElTableColumn>
          <ElTableColumn label="数量" width="70" align="center">
            <template #default>1</template>
          </ElTableColumn>
          <ElTableColumn label="金额" width="105" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.lineAmount) }}</template>
          </ElTableColumn>
          <ElTableColumn label="医保" width="80" align="center">
            <template #default>
              <ElTag type="success" size="small">医保</ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>

      <div class="copy-bottom">
        <div class="copy-bottom-amount">
          <span>{{ props.receivableAmountLabel }}</span>
          <strong>¥{{ formatMoney(receivableAmount) }}</strong>
        </div>
        <div class="flex gap-2">
          <ElButton @click="handleCancel">取消</ElButton>
          <ElButton type="primary" :loading="loading" @click="emit('confirmCharge')">
            {{ props.primaryButtonText }}
          </ElButton>
        </div>
      </div>
    </div>
  </AppDialog>
</template>

<style scoped>
.copy-dialog-body {
  display: grid;
  gap: 12px;
}

.copy-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.86fr) minmax(0, 1.14fr);
  gap: 12px;
  align-items: start;
}

.copy-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px;
}

.copy-card-title {
  margin-bottom: 10px;
  color: #334155;
  font-size: 15px;
  font-weight: 600;
}

.copy-section + .copy-section {
  margin-top: 10px;
  border-top: 1px solid #e2e8f0;
  padding-top: 10px;
}

.copy-section-title {
  margin-bottom: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.copy-summary {
  display: grid;
  gap: 6px;
}

.copy-summary-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #64748b;
  font-size: 13px;
}

.copy-summary-item strong {
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
}

.copy-summary-item.is-highlight strong {
  color: #ea580c;
  font-size: 20px;
}

.copy-discount-row {
  display: grid;
  grid-template-columns: 60px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.copy-input-number {
  width: 100%;
}

.copy-quick-discounts {
  display: flex;
  flex-wrap: wrap;
  margin-top: 8px;
  gap: 6px;
}

.copy-quick-discounts :deep(.el-button--small) {
  height: 28px;
  min-width: 72px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 13px;
}

.copy-pay-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.copy-pay-group :deep(.el-radio-button__inner) {
  height: 28px;
  min-width: 72px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 28px;
  text-align: center;
}

.copy-insurance-list :deep(.el-descriptions__label) {
  width: 58%;
  color: #64748b;
  font-size: 14px;
  padding: 1px 0;
}

.copy-insurance-list :deep(.el-descriptions__content) {
  color: #0f172a;
  text-align: right;
  font-size: 14px;
  font-weight: 600;
  padding: 1px 0;
}

.copy-insurance-list :deep(.is-key-row .el-descriptions__label),
.copy-insurance-list :deep(.is-key-row .el-descriptions__content) {
  color: #0f172a;
  font-weight: 700;
}

.copy-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: 8px;
  background: #f8fafc;
  padding: 10px 12px;
}

.copy-bottom-amount {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: #475569;
}

.copy-bottom-amount strong {
  color: #ea580c;
  font-size: 32px;
  font-weight: 700;
}
</style>
