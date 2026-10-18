<script setup lang="ts">
import type { TableColumn } from '@zxplat-core/table-layout-ui';

import type {
  OpInsurancePreSettleGroupDto,
  PreSettleChargeLinePreviewDto,
} from '#/api/outpatient-charge/outpatient-charge.types';
import type { RefundChannelDisplayItem } from '#/utils/payment/payment-channel-options';

import { computed, ref, watch } from 'vue';

import { getPaymentTypeTagStyles } from '@zxplat/utils';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElInput,
  ElRadioButton,
  ElRadioGroup,
  ElTag,
} from 'element-plus';

import { formatDiscountRateDisplay } from '#/api/basic/charge-discount-config';
import AppDialog from '#/components/app-dialog.vue';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { formatMoneyDisplay, scaleMoneySettle, sumMoneyThenCalc } from '#/utils/format/money-scale';
import { resolveDefaultPayChannelCode } from '#/utils/payment/payment-channel-options';

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
    /** 本批优惠合计（原价−折后） */
    discountAmountTotal?: number | string;
    /** 医保个人账户支付前余额（1101 balc；无医保时不传） */
    insuranceAccountBalance?: null | number | string;
    /**
     * 公务员标志（2206 cvlserv_flag；0/1；仅展示）
     */
    insuranceCvlservFlag?: null | string;
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
    /** 是否展示银联被扫区（ZXPlat 模块 + Win native 就绪） */
    scanPayEnabled?: boolean;
    /** 半成功锁定：禁止改付款码/支付方式，仅允许补结算 */
    scanInputLocked?: boolean;
    /** 扫码状态提示（父组件写入） */
    scanStatusText?: string;
    /** 退费原路渠道行（showPayChannel=false 时展示） */
    refundChannelItems?: RefundChannelDisplayItem[];
    selectedChargeCount?: number;
    /** 是否展示支付方式选择（退费原路返回时为 false） */
    showPayChannel?: boolean;
    summaryInsuranceAmount?: number | string;
    summarySelfPayAmount?: number | string;
  }>(),
  {
    dialogTitle: '确认收费',
    displayAmount: 0,
    discountAmountTotal: 0,
    insuranceAccountBalance: undefined,
    insuranceCvlservFlag: undefined,
    insuranceBreakdownItems: () => [],
    insuranceGroups: () => [],
    loading: false,
    payChannelOptions: () => [],
    previews: () => [],
    primaryButtonText: '确认收费',
    projectTableTitle: '收费项目',
    receivableAmountLabel: '个人现金（待收）',
    refundChannelItems: () => [],
    scanPayEnabled: false,
    scanInputLocked: false,
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
    ? '医保已全额覆盖，无需个人现金实收'
    : '本单金额为 0，无需支付';
});

watch(visible, (open) => {
  // 半成功补结算时保留状态，勿清空付款码区提示所需上下文
  if (open && !props.scanInputLocked) {
    scanCode.value = '';
    snapPayChannelToVisibleOptions();
  }
});

/** 当前选中渠道不在可见列表（如院点关银联）时回落到现金 */
function snapPayChannelToVisibleOptions() {
  const options = props.payChannelOptions ?? [];
  if (options.length === 0) {
    return;
  }
  if (options.some((opt) => opt.value === payChannelCode.value)) {
    return;
  }
  payChannelCode.value = resolveDefaultPayChannelCode(options);
}

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

/** 预览行单价：后端原价口径；缺字段时回落行金额以免空列 */
function resolvePreviewUnitPrice(row: PreSettleChargeLinePreviewDto) {
  if (row.unitPrice !== null && row.unitPrice !== undefined && row.unitPrice !== '') {
    return row.unitPrice;
  }
  return row.originalAmount ?? row.lineAmount;
}

/** 预览行原价金额：勿用折后应收冒充「金额」列 */
function resolvePreviewOriginalAmount(row: PreSettleChargeLinePreviewDto) {
  if (row.originalAmount !== null && row.originalAmount !== undefined && row.originalAmount !== '') {
    return row.originalAmount;
  }
  return row.lineAmount;
}

/** 预览行折后价：打折后为折后应收，未打折与原价相同 */
function resolvePreviewDiscountedAmount(row: PreSettleChargeLinePreviewDto) {
  if (row.discountedAmount !== null && row.discountedAmount !== undefined && row.discountedAmount !== '') {
    return row.discountedAmount;
  }
  return row.lineAmount;
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
    label: '基金',
    width: 88,
    align: 'right',
    headerAlign: 'center',
    slot: 'fund',
  },
  {
    prop: 'acctPay',
    label: '医保个账',
    width: 88,
    align: 'right',
    headerAlign: 'center',
    slot: 'acct',
  },
  {
    prop: 'psnCashPay',
    label: '个人现金',
    width: 88,
    align: 'right',
    headerAlign: 'center',
    slot: 'cash',
  },
];

const displayAmountNum = computed(() => Number(props.displayAmount ?? 0));

const discountAmountNum = computed(() => Number(props.discountAmountTotal ?? 0));

const showDiscountAmount = computed(
  () => Number.isFinite(discountAmountNum.value) && discountAmountNum.value > 0.000001,
);

const showPreviewDiscountColumn = computed(() =>
  props.previews.some((row) => {
    const rate = Number(row.discountRate ?? 0);
    return Number.isFinite(rate) && rate > 0 && rate <= 1;
  }),
);

/** 预览行折扣展示 */
function formatPreviewDiscount(row: PreSettleChargeLinePreviewDto) {
  if (row.discountRate === null || row.discountRate === undefined || row.discountRate === '') {
    return '—';
  }
  const display = formatDiscountRateDisplay(row.discountRate);
  return display === '款员定' ? '—' : display;
}

const previewColumns = computed<TableColumn[]>(() => {
  const columns: TableColumn[] = [
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
      minWidth: 140,
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
  ];
  // 本批任一行有折扣率时展示折扣列与折后价；金额列仍为原价
  if (showPreviewDiscountColumn.value) {
    columns.push({
      prop: 'discountRate',
      label: '折扣',
      width: 72,
      align: 'center',
      headerAlign: 'center',
      slot: 'discountRate',
    });
    columns.push({
      prop: 'discountedAmount',
      label: '折后价',
      width: 105,
      align: 'right',
      headerAlign: 'center',
      slot: 'discountedAmount',
    });
  }
  columns.push({
    prop: 'feeNatureDisplay',
    label: '类型',
    width: 88,
    align: 'center',
    headerAlign: 'center',
    slot: 'feeNature',
  });
  return columns;
});

const previewsTotal = computed(() =>
  sumMoneyThenCalc(props.previews.map((row) => Number(row.lineAmount ?? 0))),
);

/** 打折后预览行金额已是折后；原价合计 = 折后 + 优惠 */
const originalAmountTotal = computed(() => {
  // 无优惠时预览合计即原价
  if (!showDiscountAmount.value) {
    return previewsTotal.value;
  }
  return sumMoneyThenCalc([previewsTotal.value, discountAmountNum.value]);
});

const receivableAmount = computed(() => scaleMoneySettle(displayAmountNum.value));

const feeDetailDisplayRows = computed(() => {
  const valueMap = new Map<string, number>();
  const labelMap = new Map<string, string>();
  for (const item of props.insuranceBreakdownItems) {
    valueMap.set(item.key, item.value);
    // 父组件可覆盖文案（如「医保个人账户（含家庭共济）」）
    if (item.label) {
      labelMap.set(item.key, item.label);
    }
  }
  const rows: { key: string; label: string; value: number }[] = [];
  for (const def of OUTPATIENT_CHARGE_FEE_DETAIL_FIELD_DEFS) {
    const value = valueMap.get(def.key) ?? 0;
    // 常显：基金 / 个账 / 个人现金 / 最终应收，便于个账不足时对照
    const alwaysShow =
      def.key === 'fundPay' ||
      def.key === 'accountPay' ||
      def.key === 'cashPay' ||
      def.key === 'finalReceivable';
    if (!alwaysShow && Math.abs(Number(value)) < 0.000001) {
      continue;
    }
    rows.push({
      key: def.key,
      label: labelMap.get(def.key) ?? def.label,
      value,
    });
  }
  return rows;
});

/** 费用明细里的个账 / 个人现金（个账不足催收对照） */
const accountPayAmount = computed(() => {
  const item = props.insuranceBreakdownItems.find((row) => row.key === 'accountPay');
  const value = Number(item?.value ?? 0);
  return Number.isFinite(value) ? value : 0;
});
/** 家庭共济分项（含于个账；>0 时费用明细与账户区明示） */
const mulaidPayAmount = computed(() => {
  const item = props.insuranceBreakdownItems.find((row) => row.key === 'mulaidPay');
  const value = Number(item?.value ?? 0);
  return Number.isFinite(value) ? value : 0;
});
/**
 * 「个人现金待收」提示金额：必须与弹窗底部应收 / props.displayAmount 一致。
 * 禁止优先用费用明细里的 cashPay（医保口径 ybPsnCashPay）——挂医保户但未进 medfee 的院内差额
 * 只体现在 displayAmount，若用医保口径会少提示、少扫码（如茶碱 0.75）。
 */
const personalCashPayAmount = computed(() => displayAmountNum.value);
/** 有个人现金应付时展示对照提示（医保个账 ≠ 微信/现金实收） */
const showPersonalCashHint = computed(
  () => props.insuranceBreakdownItems.length > 0 && personalCashPayAmount.value > 0.001,
);

/** 所选实收渠道说明，避免与医保个账混淆 */
function resolveChannelCollectHint(channelCode: string) {
  const code = String(channelCode ?? '').trim();
  if (code === 'SCAN_PAY' || code === 'WECHAT') {
    return '（将记为微信/扫码实收，不是医保个账）';
  }
  if (code === 'ALIPAY') {
    return '（将记为支付宝实收，不是医保个账）';
  }
  if (code === 'CASH') {
    return '（将记为现金实收，不是医保个账）';
  }
  if (code === 'BANK_CARD') {
    return '（将记为银行卡实收，不是医保个账）';
  }
  return '（实收渠道与医保个账不是同一笔）';
}

const personalCashHintText = computed(() => {
  const cashText = formatMoney(personalCashPayAmount.value);
  const channelHint = resolveChannelCollectHint(payChannelCode.value);
  if (accountPayAmount.value > 0.001) {
    // 有共济时把共济金额写进提示，避免只见「个账」不见来源
    if (mulaidPayAmount.value > 0.001) {
      return `医保个账 ¥${formatMoney(accountPayAmount.value)}（含家庭共济 ¥${formatMoney(mulaidPayAmount.value)}），个人现金待收 ¥${cashText}${channelHint}`;
    }
    return `医保个账 ¥${formatMoney(accountPayAmount.value)}，个人现金待收 ¥${cashText}${channelHint}`;
  }
  return `个人现金待收 ¥${cashText}${channelHint}`;
});

/** 支付前个账余额旁：有共济时补充说明，避免「余额0却扣个账」误判 */
const insuranceMulaidHintText = computed(() => {
  if (mulaidPayAmount.value <= 0.001) {
    return '';
  }
  return `本次个账支出含家庭共济 ¥${formatMoney(mulaidPayAmount.value)}`;
});

const showInsuranceGroups = computed(() => (props.insuranceGroups?.length ?? 0) > 0);

const showInsuranceAccountBalance = computed(() => {
  const value = props.insuranceAccountBalance;
  if (value === undefined || value === null || value === '') {
    return false;
  }
  return Number.isFinite(Number(value));
});

/** 仅公务员（平台 cvlserv_flag=1）才回显公务员待遇 */
const showInsuranceCvlservFlag = computed(() => {
  return String(props.insuranceCvlservFlag ?? '').trim() === '1';
});

/** 账户信息区是否展示（余额或公务员任一有值） */
const showAccountInfoCard = computed(
  () => showInsuranceAccountBalance.value || showInsuranceCvlservFlag.value,
);

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
  <AppDialog
    v-model="visible"
    class="charge-settlement-dialog"
    :title="props.dialogTitle"
    width="920px"
  >
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
          <p v-if="showPersonalCashHint" class="copy-personal-cash-hint">
            {{ personalCashHintText }}
          </p>
        </div>

        <!-- 右上：账户信息 + 结算操作 -->
        <div class="copy-right-stack">
          <div v-if="showAccountInfoCard" class="copy-card copy-account-card">
            <SectionTitle class="mb-2" title="账户信息" />
            <div class="copy-summary">
              <div v-if="showInsuranceAccountBalance" class="copy-summary-item is-balc">
                <span>医保个账余额（支付前）</span>
                <strong>{{ insuranceAccountBalanceDisplay }}</strong>
              </div>
              <p v-if="insuranceMulaidHintText" class="copy-mulaid-hint">
                {{ insuranceMulaidHintText }}
              </p>
              <div v-if="showInsuranceCvlservFlag" class="copy-summary-item">
                <span>公务员待遇</span>
                <strong>是</strong>
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
                  <strong>¥{{ formatMoney(originalAmountTotal) }}</strong>
                </div>
                <div v-if="showDiscountAmount" class="copy-summary-item">
                  <span>折后总价</span>
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
                <div v-if="showDiscountAmount" class="copy-summary-item">
                  <span>优惠金额</span>
                  <strong>¥{{ formatMoney(discountAmountNum) }}</strong>
                </div>
                <div class="copy-summary-item is-highlight">
                  <span>{{ props.receivableAmountLabel }}</span>
                  <strong>¥{{ formatMoney(receivableAmount) }}</strong>
                </div>
                <p v-if="showPersonalCashHint" class="copy-personal-cash-hint is-compact">
                  {{ personalCashHintText }}
                </p>
              </div>
            </div>

            <div v-if="showPayChannelSection" class="copy-section">
              <div class="copy-section-title">支付方式</div>
              <ElRadioGroup
                v-model="payChannelCode"
                class="copy-pay-group"
                :disabled="props.scanInputLocked"
              >
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
              <div class="copy-section-title">退费确认</div>
              <div class="copy-refund-confirm">
                <div class="copy-refund-confirm__row is-amount">
                  <span>退费金额</span>
                  <strong>¥{{ formatMoney(receivableAmount) }}</strong>
                </div>
                <div class="copy-refund-confirm__channels">
                  <div class="copy-refund-confirm__channels-title">退费渠道（原路返回）</div>
                  <template v-if="props.refundChannelItems.length > 0">
                    <div
                      v-for="(item, idx) in props.refundChannelItems"
                      :key="`${item.label}-${idx}`"
                      class="copy-refund-confirm__channel-row"
                    >
                      <span>{{ item.label }}</span>
                      <strong v-if="item.amount != null">¥{{ formatMoney(item.amount) }}</strong>
                      <strong v-else>原路返回</strong>
                    </div>
                  </template>
                  <p v-else class="copy-refund-hint">按原支付渠道原路返回，无需选择支付方式。</p>
                </div>
              </div>
            </div>

            <div v-if="showScanPaySection" class="copy-section">
              <div class="copy-section-title">银联扫码支付</div>
              <div class="copy-discount-row">
                <span>付款码</span>
                <ElInput
                  v-model="scanCode"
                  class="copy-input-number"
                  :placeholder="
                    props.scanInputLocked
                      ? '银联已扣款，请直接确认收费补结算'
                      : '请扫码或输入付款码'
                  "
                  :disabled="props.scanInputLocked"
                  :clearable="!props.scanInputLocked"
                  @keyup.enter="emit('confirmCharge')"
                />
              </div>
              <p v-if="props.scanInputLocked" class="copy-scan-status">
                银联已扣款成功：请点「确认收费」补结算，勿重复扫码
              </p>
              <p v-else-if="props.scanStatusText" class="copy-scan-status">
                {{ props.scanStatusText }}
              </p>
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
          <template #acct="{ row }">
            <span>¥{{ formatMoney(row.acctPay) }}</span>
            <span
              v-if="Number(row.acctMulaidPay ?? 0) > 0.001"
              class="copy-group-mulaid"
            >
              （含共济 ¥{{ formatMoney(row.acctMulaidPay) }}）
            </span>
          </template>
          <template #cash="{ row }">¥{{ formatMoney(row.psnCashPay) }}</template>
        </RegisterStyleTableLayout>
      </div>

      <!-- 中部：收费项目；折扣列加宽后在卡片内横向滚动，避免撑破弹窗把金额小结挤出去 -->
      <div class="copy-card copy-table-card">
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
          <template #category="{ row }">{{ row.chargeCategoryDisplay || '—' }}</template>
          <template #unitPrice="{ row }">¥{{ formatMoney(resolvePreviewUnitPrice(row)) }}</template>
          <template #quantity="{ row }">{{ formatQuantityWithUnit(row) }}</template>
          <template #lineAmount="{ row }">¥{{ formatMoney(resolvePreviewOriginalAmount(row)) }}</template>
          <template #discountedAmount="{ row }">¥{{ formatMoney(resolvePreviewDiscountedAmount(row)) }}</template>
          <template #discountRate="{ row }">{{ formatPreviewDiscount(row) }}</template>
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
  width: 100%;
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
}

.copy-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 300px);
  gap: 12px;
  align-items: start;
  min-width: 0;
}

.copy-right-stack {
  display: grid;
  gap: 12px;
  align-content: start;
  min-width: 0;
}

.copy-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px;
  min-width: 0;
}

.copy-table-card {
  overflow-x: auto;
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
  gap: 8px;
  min-width: 0;
  color: #64748b;
  font-size: 13px;
}

.copy-summary-item span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.copy-summary-item strong {
  flex: 0 0 auto;
  color: #0f172a;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
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

.copy-refund-confirm {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.copy-refund-confirm__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  color: #475569;
  font-size: 13px;
}

.copy-refund-confirm__row.is-amount strong {
  color: #ea580c;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.copy-refund-confirm__channels {
  border-radius: 6px;
  background: #f8fafc;
  padding: 10px 12px;
}

.copy-refund-confirm__channels-title {
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
}

.copy-refund-confirm__channel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0;
  color: #0f172a;
  font-size: 13px;
}

.copy-refund-confirm__channel-row strong {
  font-weight: 600;
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

.copy-mulaid-hint {
  margin: 6px 0 0;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
}

.copy-group-mulaid {
  display: block;
  margin-top: 2px;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
}

.copy-personal-cash-hint {
  margin: 10px 0 0;
  border-radius: 6px;
  background: #fff7ed;
  border: 1px solid #fdba74;
  padding: 8px 10px;
  color: #c2410c;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.45;
}

.copy-personal-cash-hint.is-compact {
  margin-top: 4px;
  padding: 6px 8px;
  font-size: 13px;
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

<!-- 弹层挂 body，需非 scoped 才能限制 el-dialog 被表格 min-content 撑破 -->
<style>
.charge-settlement-dialog.el-dialog {
  overflow: hidden;
}

.charge-settlement-dialog .el-dialog__body {
  max-width: 100%;
  overflow-x: hidden;
}
</style>
