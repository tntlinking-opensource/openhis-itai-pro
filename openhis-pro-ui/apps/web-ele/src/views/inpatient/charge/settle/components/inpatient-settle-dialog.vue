<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { InpatientSettleChargeLineDto } from '#/api/inpatient-settle';

import { computed, ref } from 'vue';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElInput,
  ElRadioButton,
  ElRadioGroup,
  ElTag,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
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
    /** 库内预交金余额 */
    prepayNetBalance?: number | string;
    /** 本次预交金冲抵额 */
    prepayOffsetAmount?: number | string;
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
    prepayNetBalance: undefined,
    prepayOffsetAmount: undefined,
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

/** 弹窗表格分页占位 */
const chargeLineQueryParams = ref({ pageNo: 1, pageSize: 100 });

const chargeLineColumns: TableColumn[] = [
  {
    prop: 'itemName',
    label: '项目',
    minWidth: 160,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'categoryDisplay',
    label: '类别',
    minWidth: 100,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'lineAmount',
    label: '金额',
    width: 110,
    align: 'right',
    headerAlign: 'center',
    slot: 'lineAmount',
  },
];

function formatMoney(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? formatMoneyDisplay(n) : '0.00';
}

/** 需补交：预结算 displayAmount（预交金充足时为 0）。 */
const payableDisplay = computed(() => {
  if (props.displayAmount != null && props.displayAmount !== '') {
    return formatMoney(props.displayAmount);
  }
  return formatMoney(props.receivableTotal);
});

const showPrepayStrip = computed(() => {
  if (props.prepayNetBalance == null || props.prepayNetBalance === '') {
    return false;
  }
  return Number.isFinite(Number(props.prepayNetBalance));
});

/** 预结算说明文案：后端 previewText 可能带 6 位小数，展示统一为 2 位 */
const formattedPreviewText = computed(() => {
  const text = props.previewText?.trim() ?? '';
  if (!text) {
    return '';
  }
  if (text.startsWith('预交金冲抵医保个人现金')) {
    return `预交金冲抵医保个人现金：${formatMoneyYuan(props.prepayOffsetAmount)}`;
  }
  if (text.startsWith('预交金冲抵')) {
    return `预交金冲抵，应收合计：${formatMoneyYuan(props.receivableTotal)}`;
  }
  if (text.startsWith('医保已覆盖')) {
    return '医保已覆盖，无需补交';
  }
  if (text.startsWith('患者实付')) {
    return `需补交：${formatMoneyYuan(props.displayAmount)}`;
  }
  if (text.startsWith('应收合计')) {
    return `应收合计：${formatMoneyYuan(props.receivableTotal)}`;
  }
  return text;
});

const payableAmountNum = computed(() => {
  if (props.displayAmount != null && props.displayAmount !== '') {
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
        <span class="text-[var(--el-text-color-regular)]">需补交</span>
        <strong class="text-[var(--el-color-primary)]">¥{{ payableDisplay }}</strong>
        <span v-if="insurancePreSettled" class="text-xs text-[var(--el-text-color-secondary)]">
          原价合计 ¥{{ formatMoney(receivableTotal) }}
        </span>
      </div>
      <p
        v-if="showPrepayStrip"
        class="settle-dialog-prepay text-sm text-[var(--el-text-color-regular)]"
      >
        预交金余额 ¥{{ formatMoney(prepayNetBalance) }}
        <template v-if="prepayOffsetAmount != null && prepayOffsetAmount !== ''">
          · 本次冲抵 ¥{{ formatMoney(prepayOffsetAmount) }}
        </template>
        <span class="text-xs text-[var(--el-text-color-secondary)]">
          （优先从预交金扣，不足部分需补交）
        </span>
      </p>
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
        <div class="settle-dialog-section__title">银联扫码支付</div>
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
        <RegisterStyleTableLayout
          class="!p-0"
          :table-data="chargeLines"
          :table-columns="chargeLineColumns"
          :query-params="chargeLineQueryParams"
          :total="chargeLines.length"
          :show-pagination="false"
          :show-top-query="false"
          :max-height="280"
          row-key="chargeItemId"
        >
          <template #lineAmount="{ row }">¥{{ formatMoney(row.lineAmount) }}</template>
        </RegisterStyleTableLayout>
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
  flex-wrap: wrap;
  align-items: baseline;
  gap: 8px 12px;
  font-size: 14px;
}

.settle-dialog-amount strong {
  font-size: 22px;
  font-weight: 700;
}

.settle-dialog-prepay {
  margin: 0;
  line-height: 1.5;
}

.settle-dialog-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settle-dialog-section__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.settle-dialog-pay-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.settle-dialog-scan-status {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
