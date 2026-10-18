<script setup lang="ts">
import type { TableColumn } from '@zxplat-core/table-layout-ui';

import type { InpatientSettleChargeLineDto } from '#/api/inpatient-settle';
import type { AuditViolation } from '#/chs/api/detail-audit-try';

import { computed, ref, watch } from 'vue';

import { ElButton, ElDescriptions, ElDescriptionsItem, ElTabPane, ElTable, ElTableColumn, ElTabs, ElTag } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import { formatSevDeg } from '#/chs/constants/detail-audit-try';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { OUTPATIENT_CHARGE_FEE_DETAIL_FIELD_DEFS } from '#/views/outpatient/charge/charge/outpatient-charge.constants';

interface InsuranceBreakdownItem {
  key: string;
  label: string;
  value: number | string;
}

defineOptions({
  name: 'InpatientClinicalPreSettlePreviewDialog',
});

const props = withDefaults(
  defineProps<{
    chargeLines?: InpatientSettleChargeLineDto[];
    /** 3101 违规提醒；有内容时预结算弹窗增加页签 */
    auditViolations?: AuditViolation[];
    /** 3101 调用失败说明；与违规列表一并放入事前提醒页签 */
    auditRemindNote?: string;
    displayAmount?: number | string;
    insuranceBreakdownItems?: InsuranceBreakdownItem[];
    insurancePreSettled?: boolean;
    /** 库内预交金余额 D */
    prepayNetBalance?: number | string;
    /** 本次预交金冲抵额 O */
    prepayOffsetAmount?: number | string;
    /** 是否全额退预交金 */
    prepayFullRefund?: boolean;
    /** 应退预交金金额 */
    prepayRefundAmount?: number | string;
    previewText?: string;
    receivableTotal?: number | string;
  }>(),
  {
    chargeLines: () => [],
    auditViolations: () => [],
    auditRemindNote: '',
    displayAmount: 0,
    insuranceBreakdownItems: () => [],
    insurancePreSettled: false,
    prepayNetBalance: undefined,
    prepayOffsetAmount: undefined,
    prepayFullRefund: false,
    prepayRefundAmount: undefined,
    previewText: '',
    receivableTotal: 0,
  },
);

const visible = defineModel<boolean>({ required: true });

/** 有提醒或调用失败说明时才出现「事前提醒」页签。 */
const showAuditTab = computed(
  () => (props.auditViolations?.length ?? 0) > 0 || Boolean(props.auditRemindNote?.trim()),
);

const auditTabLabel = computed(() => {
  const count = props.auditViolations?.length ?? 0;
  if (count > 0) {
    return `事前提醒（${count}）`;
  }
  return '事前提醒';
});

const activeTab = ref('settle');

const selectedViolation = ref<AuditViolation | null>(null);

const selectedViolationDetails = computed(() => selectedViolation.value?.details ?? []);

const selectedVolaCont = computed(() => String(selectedViolation.value?.volaCont ?? '').trim());

/** 弹窗打开或提醒列表变化时，默认看预结算，并选中第一条提醒。 */
function syncAuditSelection() {
  activeTab.value = 'settle';
  selectedViolation.value = props.auditViolations?.[0] ?? null;
}

watch(visible, (open) => {
  if (open) {
    syncAuditSelection();
  }
});

/** 弹窗费用表分页占位（无服务端分页）。 */
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

/** 金额两位小数。 */
function formatMoney(value: null | number | string | undefined) {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? formatMoneyDisplay(n) : '0.00';
}

/** 渠道实付 C：预结算 displayAmount。 */
const payableDisplay = computed(() => {
  if (props.displayAmount != null && props.displayAmount !== '') {
    return formatMoney(props.displayAmount);
  }
  return formatMoney(0);
});

const showPrepayStrip = computed(() => {
  if (props.prepayNetBalance == null || props.prepayNetBalance === '') {
    return false;
  }
  return Number.isFinite(Number(props.prepayNetBalance));
});

/** 预结算说明：后端 previewText 优先原样展示。 */
const formattedPreviewText = computed(() => {
  const text = props.previewText?.trim() ?? '';
  if (!text) {
    return '';
  }
  return text;
});

/** 应退预交金展示文案。 */
const prepayRefundDisplay = computed(() => {
  if (props.prepayRefundAmount == null || props.prepayRefundAmount === '') {
    return '';
  }
  const n = Number(props.prepayRefundAmount);
  if (!Number.isFinite(n) || n <= 0) {
    return '';
  }
  const label = props.prepayFullRefund ? '全额退现金' : '退现金';
  return `${label} ¥${formatMoney(n)}`;
});

/**
 * 费用明细展示行：门诊字段 + 住院预交金冲抵项；常显基金/个人现金/预交金/需补交。
 */
const feeDetailDisplayRows = computed(() => {
  const map = new Map<string, { label: string; value: number }>();
  for (const item of props.insuranceBreakdownItems) {
    map.set(item.key, {
      label: item.label,
      value: Number(item.value ?? 0),
    });
  }
  const rows: { key: string; label: string; value: number }[] = [];
  // 个人现金之后插入预交金余额/冲抵/应退，最后才是渠道实付 C
  const orderedKeys: string[] = [];
  for (const def of OUTPATIENT_CHARGE_FEE_DETAIL_FIELD_DEFS) {
    if (def.key === 'finalReceivable') {
      orderedKeys.push('prepayBalance', 'prepayOffset', 'prepayRefund', 'finalReceivable');
      continue;
    }
    orderedKeys.push(def.key);
  }
  const seen = new Set<string>();
  for (const key of orderedKeys) {
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    const fromBreakdown = map.get(key);
    const outpatientDef = OUTPATIENT_CHARGE_FEE_DETAIL_FIELD_DEFS.find((d) => d.key === key);
    const value = fromBreakdown?.value ?? 0;
    const label = fromBreakdown?.label ?? outpatientDef?.label ?? key;
    const alwaysShow =
      key === 'fundPay' ||
      key === 'cashPay' ||
      key === 'prepayBalance' ||
      key === 'prepayOffset' ||
      key === 'prepayRefund' ||
      key === 'finalReceivable';
    if (!alwaysShow && Math.abs(Number(value)) < 0.000001) {
      continue;
    }
    rows.push({ key, label, value });
  }
  return rows;
});

/** 费用明细金额是否非零（高亮有发生额的项）。 */
function isNonZeroFeeAmount(value: number) {
  return Number(value) !== 0;
}

/** 费用明细行 class：关键行加粗；非零金额加高亮。 */
function resolveFeeDetailRowClass(item: { key: string; value: number }) {
  const classes: string[] = [];
  if (
    item.key === 'finalReceivable' ||
    item.key === 'cashPay' ||
    item.key === 'prepayBalance' ||
    item.key === 'prepayOffset' ||
    item.key === 'prepayRefund'
  ) {
    classes.push('is-key-row');
  }
  if (isNonZeroFeeAmount(item.value)) {
    classes.push('is-nonzero');
  }
  return classes.join(' ');
}
</script>

<template>
  <AppDialog v-model="visible" title="住院完整预结算" width="860px" destroy-on-close>
    <ElTabs v-if="showAuditTab" v-model="activeTab" class="clinical-pre-settle-tabs">
      <ElTabPane label="预结算" name="settle" />
      <ElTabPane :label="auditTabLabel" name="audit" />
    </ElTabs>
    <div v-show="!showAuditTab || activeTab === 'settle'" class="clinical-pre-settle-body">
      <div class="clinical-pre-settle-tags">
        <ElTag type="warning" effect="plain">仅预览 · 不落账</ElTag>
        <ElTag v-if="insurancePreSettled" type="success" effect="plain">医保预结算（2303）</ElTag>
      </div>
      <p v-if="formattedPreviewText" class="text-sm text-[var(--el-text-color-regular)]">
        {{ formattedPreviewText }}
      </p>
      <div class="clinical-pre-settle-amount">
        <span class="text-[var(--el-text-color-regular)]">应付个人现金</span>
        <strong class="text-[var(--el-color-primary)]">¥{{ payableDisplay }}</strong>
        <span v-if="insurancePreSettled" class="text-xs text-[var(--el-text-color-secondary)]">
          费用合计 ¥{{ formatMoney(receivableTotal) }}
        </span>
      </div>
      <p
        v-if="showPrepayStrip"
        class="clinical-pre-settle-prepay text-sm text-[var(--el-text-color-regular)]"
      >
        预交金余额 ¥{{ formatMoney(prepayNetBalance) }}
        <template v-if="prepayOffsetAmount != null && Number(prepayOffsetAmount) > 0">
          · 本次冲抵 ¥{{ formatMoney(prepayOffsetAmount) }}
        </template>
        <template v-if="prepayRefundDisplay"> · {{ prepayRefundDisplay }}</template>
        <span class="text-xs text-[var(--el-text-color-secondary)]">
          （冲抵后渠道实付；有余退现金）
        </span>
      </p>

      <div v-if="feeDetailDisplayRows.length > 0" class="clinical-pre-settle-section">
        <SectionTitle class="mb-2.5" title="费用明细" />
        <ElDescriptions
          :column="2"
          size="small"
          label-width="112px"
          class="clinical-pre-settle-fee-list"
        >
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

      <div v-if="chargeLines.length > 0" class="clinical-pre-settle-section">
        <SectionTitle class="mb-2.5" :title="`结算费用（${chargeLines.length} 项）`" />
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

      <p class="text-xs text-[var(--el-text-color-secondary)]">
        正式收费请到住院收费处确认结算。关闭本窗不影响收费处再次预结算。
      </p>
    </div>
    <div v-show="showAuditTab && activeTab === 'audit'" class="clinical-pre-settle-audit">
      <p v-if="auditRemindNote?.trim()" class="clinical-pre-settle-audit__note">
        {{ auditRemindNote }}
      </p>
      <p
        v-if="(auditViolations?.length ?? 0) > 0"
        class="text-sm text-[var(--el-text-color-regular)]"
      >
        发现 {{ auditViolations?.length }} 条规则提醒。本页只展示，不拦截预结算。
      </p>
      <ElTable
        v-if="(auditViolations?.length ?? 0) > 0"
        :data="auditViolations"
        highlight-current-row
        max-height="220"
        stripe
        @current-change="(row: AuditViolation | null) => (selectedViolation = row)"
      >
        <ElTableColumn label="严重程度" width="110">
          <template #default="{ row }">{{ formatSevDeg(row.sevDeg) }}</template>
        </ElTableColumn>
        <ElTableColumn label="规则名称" min-width="220" prop="ruleName" show-overflow-tooltip />
        <ElTableColumn label="违规金额" width="110" prop="volaAmt" align="right" />
      </ElTable>
      <div v-if="selectedVolaCont" class="clinical-pre-settle-audit__content">
        {{ selectedVolaCont }}
      </div>
      <ElTable
        v-if="selectedViolationDetails.length > 0"
        :data="selectedViolationDetails"
        max-height="200"
        stripe
      >
        <ElTableColumn label="药品/项目" min-width="200" prop="itemName" show-overflow-tooltip />
        <ElTableColumn label="处方/医嘱" min-width="140" prop="rxId" show-overflow-tooltip />
        <ElTableColumn label="金额" width="96" prop="volaAmt" align="right" />
      </ElTable>
    </div>
    <template #footer>
      <ElButton type="primary" @click="visible = false">关闭</ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.clinical-pre-settle-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.clinical-pre-settle-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.clinical-pre-settle-amount {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
  font-size: 16px;
}

.clinical-pre-settle-amount strong {
  font-size: 28px;
  font-weight: 700;
}

.clinical-pre-settle-prepay {
  margin: 0;
  line-height: 1.5;
}

.clinical-pre-settle-section {
  display: flex;
  flex-direction: column;
}

.clinical-pre-settle-fee-list :deep(.el-descriptions__label.is-key-row),
.clinical-pre-settle-fee-list :deep(.el-descriptions__content.is-key-row) {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.clinical-pre-settle-fee-list :deep(.el-descriptions__content.is-nonzero) {
  color: var(--el-color-primary);
}

.clinical-pre-settle-tabs :deep(.el-tabs__content) {
  display: none;
}

.clinical-pre-settle-audit {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.clinical-pre-settle-audit__note {
  margin: 0;
  color: var(--el-color-warning);
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
}

.clinical-pre-settle-audit__content {
  max-height: 160px;
  overflow: auto;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
