<script setup lang="ts">
import type { InpatientSettleChargeLineDto } from '#/api/inpatient-settle';

import { computed } from 'vue';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElInput,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { formatMoneyDisplay, formatMoneyYuan } from '#/utils/format/money-scale';

type PayChannelOption = { label: string; value: string };

interface InsuranceBreakdownItem {
  key: string;
  label: string;
  value: number | string;
}

defineOptions({
  name: 'InpatientSettleDialog',
});

const props = withDefaults(
  defineProps<{
    chargeLines?: InpatientSettleChargeLineDto[];
    confirmLoading?: boolean;
    displayAmount?: number | string;
    insuranceBreakdownItems?: InsuranceBreakdownItem[];
    insurancePreSettled?: boolean;
    loading?: boolean;
    payChannelOptions?: PayChannelOption[];
    previewText?: string;
    receivableTotal?: number | string;
    scanPayEnabled?: boolean;
    scanStatusText?: string;
    settleModeLabel?: string;
  }>(),
  {
    chargeLines: () => [],
    confirmLoading: false,
    displayAmount: 0,
    insuranceBreakdownItems: () => [],
    insurancePreSettled: false,
    loading: false,
    payChannelOptions: () => [],
    previewText: '',
    receivableTotal: 0,
    scanPayEnabled: false,
    scanStatusText: '',
    settleModeLabel: '',
  },
);

const emit = defineEmits<{
  confirm: [];
}>();

const visible = defineModel<boolean>({ required: true });
const payChannelCode = defineModel<string>('payChannelCode', { required: true });
const scanCode = defineModel<string>('scanCode', { default: '' });

function formatMoney(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? formatMoneyDisplay(n) : '0.00';
}

const payableDisplay = computed(() => {
  if (props.insurancePreSettled && props.displayAmount != null) {
    return formatMoney(props.displayAmount);
  }
  return formatMoney(props.receivableTotal);
});

/** 预结算说明文案：后端 previewText 可能带 6 位小数，展示统一为 2 位 */
const formattedPreviewText = computed(() => {
  const text = props.previewText?.trim() ?? '';
  if (!text) {
    return '';
  }
  if (text.startsWith('预交金冲抵')) {
    return `预交金冲抵，应收合计：${formatMoneyYuan(props.receivableTotal)}`;
  }
  if (text.startsWith('患者实付')) {
    return `患者实付：${formatMoneyYuan(props.displayAmount)}`;
  }
  if (text.startsWith('应收合计')) {
    return `应收合计：${formatMoneyYuan(props.receivableTotal)}`;
  }
  return text;
});

const payableAmountNum = computed(() => {
  if (props.insurancePreSettled && props.displayAmount != null) {
    return Number(props.displayAmount);
  }
  return Number(props.receivableTotal ?? 0);
});

const showScanPaySection = computed(
  () => props.scanPayEnabled && payChannelCode.value === 'SCAN_PAY' && payableAmountNum.value > 0,
);

function handleCancel() {
  visible.value = false;
}
</script>

<template>
  <AppDialog v-model="visible" title="确认住院结算" width="760px" destroy-on-close>
    <div class="settle-dialog-body">
      <div v-if="settleModeLabel" class="settle-dialog-tags">
        <ElTag type="warning" effect="plain">{{ settleModeLabel }}</ElTag>
        <ElTag v-if="insurancePreSettled" type="success" effect="plain">医保预结算</ElTag>
      </div>
      <p
        v-if="formattedPreviewText"
        class="settle-dialog-preview text-sm text-[var(--el-text-color-regular)]"
      >
        {{ formattedPreviewText }}
      </p>
      <div class="settle-dialog-amount">
        <span class="text-[var(--el-text-color-regular)]">患者实付</span>
        <strong class="text-[var(--el-color-primary)]">¥{{ payableDisplay }}</strong>
        <span v-if="insurancePreSettled" class="text-xs text-[var(--el-text-color-secondary)]">
          原价合计 ¥{{ formatMoney(receivableTotal) }}
        </span>
      </div>
      <div v-if="insuranceBreakdownItems.length > 0" class="settle-dialog-section">
        <div class="settle-dialog-section__title">医保分项</div>
        <ElDescriptions :column="2" border size="small">
          <ElDescriptionsItem
            v-for="item in insuranceBreakdownItems"
            :key="item.key"
            :label="item.label"
          >
            ¥{{ formatMoney(item.value) }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </div>
      <div class="settle-dialog-section">
        <div class="settle-dialog-section__title">支付方式</div>
        <ElRadioGroup v-model="payChannelCode" class="settle-dialog-pay-group">
          <ElRadioButton v-for="opt in payChannelOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </ElRadioButton>
        </ElRadioGroup>
      </div>
      <div v-if="showScanPaySection" class="settle-dialog-section">
        <div class="settle-dialog-section__title">扫码支付</div>
        <ElInput
          v-model="scanCode"
          placeholder="请扫码或输入付款码"
          clearable
          @keyup.enter="emit('confirm')"
        />
        <p v-if="scanStatusText" class="settle-dialog-scan-status">{{ scanStatusText }}</p>
      </div>
      <div class="settle-dialog-section">
        <div class="settle-dialog-section__title">结算费用（{{ chargeLines.length }} 项）</div>
        <ElTable :data="chargeLines" border size="small" max-height="280" row-key="chargeItemId">
          <ElTableColumn prop="itemName" label="项目" min-width="160" show-overflow-tooltip />
          <ElTableColumn
            prop="categoryDisplay"
            label="类别"
            min-width="100"
            show-overflow-tooltip
          />
          <ElTableColumn label="金额" width="110" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.lineAmount) }}</template>
          </ElTableColumn>
        </ElTable>
      </div>
    </div>
    <template #footer>
      <ElButton @click="handleCancel">取消</ElButton>
      <ElButton type="primary" :loading="confirmLoading || loading" @click="emit('confirm')">
        确认结算
      </ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.settle-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settle-dialog-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.settle-dialog-preview {
  margin: 0;
}

.settle-dialog-amount {
  display: flex;
  align-items: baseline;
  gap: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  padding: 10px 12px;
}

.settle-dialog-amount strong {
  font-size: 24px;
  font-weight: 700;
}

.settle-dialog-section__title {
  margin-bottom: 8px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}

.settle-dialog-pay-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.settle-dialog-scan-status {
  margin: 8px 0 0;
  color: #2563eb;
  font-size: 13px;
}
</style>
