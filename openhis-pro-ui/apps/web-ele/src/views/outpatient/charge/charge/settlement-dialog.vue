<script setup lang="ts">
import type {
  OpInsurancePreSettleGroupDto,
  PreSettleChargeLinePreviewDto,
} from '#/api/outpatient-charge/outpatient-charge.types';

import { computed, watch } from 'vue';

import { getPaymentTypeTagStyles } from '@vben/utils';

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
import { formatMoneyDisplay, scaleMoneySettle, sumMoneyThenCalc } from '#/utils/format/money-scale';

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
    /** 多账户混合结算时的医保分组（仅多 account 时展示） */
    insuranceGroups?: OpInsurancePreSettleGroupDto[];
    loading?: boolean;
    payChannelOptions?: PayChannelOption[];
    previews?: PreSettleChargeLinePreviewDto[];
    /** 主操作按钮文案（默认：确认收费） */
    primaryButtonText?: string;
    /** 中部表格卡片标题（默认：收费项目） */
    projectTableTitle?: string;
    /** 小结/底部高亮金额标签（默认：应收金额） */
    receivableAmountLabel?: string;
    /** 是否展示银联被扫区（Whale 模块 + Win native 就绪） */
    scanPayEnabled?: boolean;
    /** 扫码状态提示（父组件写入） */
    scanStatusText?: string;
    selectedChargeCount?: number;
    summaryInsuranceAmount?: number | string;
    summarySelfPayAmount?: number | string;
  }>(),
  {
    dialogTitle: '确认收费',
    displayAmount: 0,
    insuranceBreakdownItems: () => [],
    insuranceGroups: () => [],
    loading: false,
    payChannelOptions: () => [],
    previews: () => [],
    primaryButtonText: '确认收费',
    projectTableTitle: '收费项目',
    receivableAmountLabel: '应收金额',
    scanPayEnabled: false,
    scanStatusText: '',
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
const scanCode = defineModel<string>('scanCode', { default: '' });

const showScanPaySection = computed(
  () => props.scanPayEnabled && payChannelCode.value === 'SCAN_PAY',
);

watch(visible, (open) => {
  if (open) {
    scanCode.value = '';
  }
});

function formatMoney(value: null | number | string | undefined) {
  return formatMoneyDisplay(value);
}

/** 费用性质标签文案：与 IF-OPC-03 待收费列表 feeNatureDisplay 同口径 */
function resolveFeeNatureLabel(row: PreSettleChargeLinePreviewDto) {
  const label = String(row.feeNatureDisplay ?? '').trim();
  if (!label) {
    return '—';
  }
  return label;
}

function resolveFeeNatureTagStyle(row: PreSettleChargeLinePreviewDto) {
  const label = resolveFeeNatureLabel(row);
  if (label === '—') {
    return undefined;
  }
  const { bgColor, textColor } = getPaymentTypeTagStyles(label);
  return { backgroundColor: bgColor, color: textColor, border: 'none' };
}

/** 数量展示：拼接单位，如「1支」 */
function formatQuantityWithUnit(row: PreSettleChargeLinePreviewDto) {
  const display = String(row.quantityDisplay ?? '').trim();
  if (!display) {
    return '—';
  }
  const unit = String(row.quantityUnit ?? '').trim();
  return unit ? `${display}${unit}` : display;
}

const displayAmountNum = computed(() => Number(props.displayAmount ?? 0));

const previewsTotal = computed(() =>
  sumMoneyThenCalc(props.previews.map((row) => Number(row.lineAmount ?? 0))),
);

const receivableAmount = computed(() => scaleMoneySettle(displayAmountNum.value));

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

const showInsuranceGroups = computed(() => (props.insuranceGroups?.length ?? 0) > 0);

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
            <div class="copy-section-title">支付方式</div>
            <ElRadioGroup v-model="payChannelCode" class="copy-pay-group">
              <ElRadioButton v-for="opt in payChannelOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </ElRadioButton>
            </ElRadioGroup>
          </div>

          <div v-if="showScanPaySection" class="copy-section">
            <div class="copy-section-title">扫码支付</div>
            <div class="copy-discount-row">
              <span>付款码</span>
              <ElInput
                v-model="scanCode"
                class="copy-input-number"
                placeholder="请扫码或输入付款码"
                clearable
                @keyup.enter="emit('confirmCharge')"
              />
            </div>
            <p v-if="props.scanStatusText" class="copy-scan-status">{{ props.scanStatusText }}</p>
          </div>
        </div>
      </div>

      <div v-if="showInsuranceGroups" class="copy-card">
        <div class="copy-card-title">医保分组（多账户混合）</div>
        <ElTable :data="props.insuranceGroups" border size="small" row-key="groupIndex">
          <ElTableColumn label="组" width="48" align="center">
            <template #default="{ row }">{{ row.groupIndex ?? '—' }}</template>
          </ElTableColumn>
          <ElTableColumn label="医疗类别" min-width="108">
            <template #default="{ row }">
              {{ row.medTypeDisplay || row.medType || '—' }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="医疗费" width="96" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.medfeeSumamt) }}</template>
          </ElTableColumn>
          <ElTableColumn label="统筹" width="88" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.fundPaySumamt) }}</template>
          </ElTableColumn>
          <ElTableColumn label="个账" width="88" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.acctPay) }}</template>
          </ElTableColumn>
          <ElTableColumn label="现金" width="88" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.psnCashPay) }}</template>
          </ElTableColumn>
        </ElTable>
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
            <template #default="{ row }">{{ formatQuantityWithUnit(row) }}</template>
          </ElTableColumn>
          <ElTableColumn label="金额" width="105" align="right">
            <template #default="{ row }">¥{{ formatMoney(row.lineAmount) }}</template>
          </ElTableColumn>
          <ElTableColumn label="类型" width="88" align="center">
            <template #default="{ row }">
              <ElTag
                v-if="resolveFeeNatureLabel(row) !== '—'"
                size="small"
                :style="resolveFeeNatureTagStyle(row)"
              >
                {{ resolveFeeNatureLabel(row) }}
              </ElTag>
              <span v-else>—</span>
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

.copy-scan-status {
  margin: 8px 0 0;
  color: #2563eb;
  font-size: 13px;
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
