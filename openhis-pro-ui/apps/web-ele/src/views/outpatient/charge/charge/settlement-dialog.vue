<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  OpInsurancePreSettleGroupDto,
  PreSettleChargeLinePreviewDto,
} from '#/api/outpatient-charge/outpatient-charge.types';

import { computed, ref, watch } from 'vue';

import { getPaymentTypeTagStyles } from '@vben/utils';

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
import SectionTitle from '#/components/layouts/section-title.vue';
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
    /** 医保个人账户支付前余额（1101 balc；无医保时不传） */
    insuranceAccountBalance?: null | number | string;
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
    /** 是否展示支付方式选择（退费原路返回时为 false） */
    showPayChannel?: boolean;
    summaryInsuranceAmount?: number | string;
    summarySelfPayAmount?: number | string;
  }>(),
  {
    dialogTitle: '确认收费',
    displayAmount: 0,
    insuranceAccountBalance: undefined,
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
    showPayChannel: true,
    summaryInsuranceAmount: 0,
    summarySelfPayAmount: 0,
  },
);

const emit = defineEmits<{
  confirmCharge: [];
}>();

const visible = defineModel<boolean>({ required: true });
const payChannelCode = defineModel<string>('payChannelCode', { default: '' });
const scanCode = defineModel<string>('scanCode', { default: '' });

/** 应收 > 0 才需要选支付方式 / 扫付款码（医保全额覆盖等零元场景跳过） */
const needCashPay = computed(() => {
  const amount = Number(props.displayAmount ?? 0);
  return Number.isFinite(amount) && amount > 0.001;
});
const showPayChannelSection = computed(() => props.showPayChannel && needCashPay.value);
const showScanPaySection = computed(
  () => showPayChannelSection.value && props.scanPayEnabled && payChannelCode.value === 'SCAN_PAY',
);
const zeroPayHint = computed(() => {
  const insurance = Number(props.summaryInsuranceAmount ?? 0);
  return Number.isFinite(insurance) && insurance > 0.001
    ? '医保已全额覆盖，无需额外现金支付'
    : '本单金额为 0，无需支付';
});

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

/** 弹窗表格分页占位（无服务端分页） */
const insuranceGroupQueryParams = ref({ pageNo: 1, pageSize: 50 });
const previewQueryParams = ref({ pageNo: 1, pageSize: 100 });

const insuranceGroupColumns: TableColumn[] = [
  {
    prop: 'groupIndex',
    label: '组',
    width: 48,
    align: 'center',
    headerAlign: 'center',
    slot: 'groupIndex',
  },
  {
    prop: 'medTypeDisplay',
    label: '医疗类别',
    minWidth: 108,
    align: 'left',
    headerAlign: 'center',
    slot: 'medType',
  },
  {
    prop: 'medfeeSumamt',
    label: '医疗费',
    width: 96,
    align: 'right',
    headerAlign: 'center',
    slot: 'medfee',
  },
  {
    prop: 'fundPaySumamt',
    label: '统筹',
    width: 88,
    align: 'right',
    headerAlign: 'center',
    slot: 'fund',
  },
  {
    prop: 'acctPay',
    label: '个账',
    width: 88,
    align: 'right',
    headerAlign: 'center',
    slot: 'acct',
  },
  {
    prop: 'psnCashPay',
    label: '现金',
    width: 88,
    align: 'right',
    headerAlign: 'center',
    slot: 'cash',
  },
];

const previewColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 56,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'itemLabel',
    label: '项目',
    minWidth: 170,
    align: 'left',
    headerAlign: 'center',
    slot: 'itemLabel',
    showOverflowTooltip: true,
  },
  {
    prop: 'category',
    label: '类别',
    width: 80,
    align: 'center',
    headerAlign: 'center',
    slot: 'category',
  },
  {
    prop: 'unitPrice',
    label: '单价',
    width: 95,
    align: 'right',
    headerAlign: 'center',
    slot: 'unitPrice',
  },
  {
    prop: 'quantityDisplay',
    label: '数量',
    width: 70,
    align: 'center',
    headerAlign: 'center',
    slot: 'quantity',
  },
  {
    prop: 'lineAmount',
    label: '金额',
    width: 105,
    align: 'right',
    headerAlign: 'center',
    slot: 'lineAmount',
  },
  {
    prop: 'feeNatureDisplay',
    label: '类型',
    width: 88,
    align: 'center',
    headerAlign: 'center',
    slot: 'feeNature',
  },
];

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

const showInsuranceAccountBalance = computed(() => {
  const value = props.insuranceAccountBalance;
  if (value === undefined || value === null || value === '') {
    return false;
  }
  return Number.isFinite(Number(value));
});

const insuranceAccountBalanceDisplay = computed(() => {
  if (!showInsuranceAccountBalance.value) {
    return '—';
  }
  return `¥${formatMoney(props.insuranceAccountBalance)}`;
});

/** 费用明细金额是否非零（用于高亮有实际发生额的项） */
function isNonZeroFeeAmount(value: number) {
  return Number(value) !== 0;
}

/** 费用明细行 class：关键行加粗；非零金额加高亮色 */
function resolveFeeDetailRowClass(item: { key: string; value: number }) {
  const classes: string[] = [];
  if (item.key === 'finalReceivable' || item.key === 'cashPay') {
    classes.push('is-key-row');
  }
  if (isNonZeroFeeAmount(item.value)) {
    classes.push('is-nonzero');
  }
  return classes.join(' ');
}

/** 关闭确认收费弹窗 */
const handleCancel = () => {
  visible.value = false;
};
</script>

<template>
  <AppDialog v-model="visible" :title="props.dialogTitle" width="860px">
    <div class="copy-dialog-body">
      <div class="copy-main-grid">
        <!-- 左上：费用明细 -->
        <div class="copy-card copy-insurance-card">
          <SectionTitle class="mb-2.5" title="费用明细" />
          <ElDescriptions :column="2" size="small" label-width="112px" class="copy-insurance-list">
            <ElDescriptionsItem
              v-for="item in feeDetailDisplayRows"
              :key="item.key"
              :label="item.label"
              :label-class-name="resolveFeeDetailRowClass(item)"
              :class-name="resolveFeeDetailRowClass(item)"
            >
              ¥{{ formatMoney(item.value) }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </div>

        <!-- 右上：账户信息 + 结算操作 -->
        <div class="copy-right-stack">
          <div v-if="showInsuranceAccountBalance" class="copy-card copy-account-card">
            <SectionTitle class="mb-2" title="账户信息" />
            <div class="copy-summary">
              <div class="copy-summary-item is-balc">
                <span>医保余额（支付前）</span>
                <strong>{{ insuranceAccountBalanceDisplay }}</strong>
              </div>
            </div>
          </div>

          <div class="copy-card">
            <SectionTitle class="mb-2.5" title="结算操作" />

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

            <div v-if="showPayChannelSection" class="copy-section">
              <div class="copy-section-title">支付方式</div>
              <ElRadioGroup v-model="payChannelCode" class="copy-pay-group">
                <ElRadioButton v-for="opt in payChannelOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </ElRadioButton>
              </ElRadioGroup>
            </div>
            <div v-else-if="props.showPayChannel" class="copy-section">
              <div class="copy-section-title">支付方式</div>
              <p class="copy-refund-hint">{{ zeroPayHint }}</p>
            </div>
            <div v-else class="copy-section">
              <div class="copy-section-title">退款路径</div>
              <p class="copy-refund-hint">按原支付渠道原路返回，无需选择支付方式。</p>
            </div>

            <div v-if="showScanPaySection" class="copy-section">
              <div class="copy-section-title">银联扫码支付</div>
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
      </div>

      <div v-if="showInsuranceGroups" class="copy-card">
        <SectionTitle class="mb-2.5" title="医保分组（多账户混合）" />
        <RegisterStyleTableLayout
          class="!p-0"
          :table-data="props.insuranceGroups"
          :table-columns="insuranceGroupColumns"
          :query-params="insuranceGroupQueryParams"
          :total="props.insuranceGroups.length"
          :show-pagination="false"
          :show-top-query="false"
          row-key="groupIndex"
        >
          <template #groupIndex="{ row }">{{ row.groupIndex ?? '—' }}</template>
          <template #medType="{ row }">
            {{ row.medTypeDisplay || row.medType || '—' }}
          </template>
          <template #medfee="{ row }">¥{{ formatMoney(row.medfeeSumamt) }}</template>
          <template #fund="{ row }">¥{{ formatMoney(row.fundPaySumamt) }}</template>
          <template #acct="{ row }">¥{{ formatMoney(row.acctPay) }}</template>
          <template #cash="{ row }">¥{{ formatMoney(row.psnCashPay) }}</template>
        </RegisterStyleTableLayout>
      </div>

      <!-- 中部：收费项目 -->
      <div class="copy-card">
        <SectionTitle class="mb-2.5" :title="props.projectTableTitle" />
        <RegisterStyleTableLayout
          class="!p-0"
          :table-data="previews"
          :table-columns="previewColumns"
          :query-params="previewQueryParams"
          :total="previews.length"
          :show-pagination="false"
          :show-top-query="false"
          :max-height="previews.length > 9 ? 390 : undefined"
          row-key="chargeItemId"
        >
          <template #itemLabel="{ row }">{{ row.itemLabel || '—' }}</template>
          <template #category>—</template>
          <template #unitPrice="{ row }">¥{{ formatMoney(row.lineAmount) }}</template>
          <template #quantity="{ row }">{{ formatQuantityWithUnit(row) }}</template>
          <template #lineAmount="{ row }">¥{{ formatMoney(row.lineAmount) }}</template>
          <template #feeNature="{ row }">
            <ElTag
              v-if="resolveFeeNatureLabel(row) !== '—'"
              size="small"
              :style="resolveFeeNatureTagStyle(row)"
            >
              {{ resolveFeeNatureLabel(row) }}
            </ElTag>
            <span v-else>—</span>
          </template>
        </RegisterStyleTableLayout>
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
  grid-template-columns: minmax(0, 1fr) 350px;
  gap: 12px;
  align-items: start;
}

.copy-right-stack {
  display: grid;
  gap: 12px;
  align-content: start;
}

.copy-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px;
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

.copy-summary-item.is-balc strong {
  color: #2563eb;
  font-size: 16px;
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
  width: 100%;
}

.copy-pay-group :deep(.el-radio-button) {
  flex: 0 0 auto;
  margin: 0;
}

.copy-pay-group :deep(.el-radio-button__inner) {
  height: 28px;
  padding: 0 10px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 28px;
  text-align: center;
  white-space: nowrap;
}

.copy-scan-status {
  margin: 8px 0 0;
  color: #2563eb;
  font-size: 13px;
}

.copy-refund-hint {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.copy-insurance-list :deep(.el-descriptions__label) {
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

.copy-insurance-list :deep(.el-descriptions__label.is-key-row),
.copy-insurance-list :deep(.el-descriptions__content.is-key-row) {
  color: #0f172a;
  font-weight: 700;
}

.copy-insurance-list :deep(.el-descriptions__label.is-nonzero),
.copy-insurance-list :deep(.el-descriptions__content.is-nonzero) {
  color: #ea580c;
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
