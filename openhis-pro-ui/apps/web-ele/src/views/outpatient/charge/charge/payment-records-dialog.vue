<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  ChargeSettlementInsuranceSummaryDto,
  ChargeSettlementPaymentLineDto,
  ChargeSettlementSetlinfoDto,
  PendingChargeItemRowDto,
} from '#/api/outpatient-charge/outpatient-charge.types';

import { computed, ref, watch } from 'vue';

import dayjs from 'dayjs';
import { ElButton, ElDescriptions, ElDescriptionsItem, ElEmpty, ElTag } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import { OUTPATIENT_CHARGE_SETLINFO_FIELD_DEFS } from './outpatient-charge.constants';

defineOptions({
  name: 'ChargePaymentRecordsDialog',
});

const props = withDefaults(
  defineProps<{
    /** 用于解析支付单关联明细（收银台当前列表，作回落） */
    chargeItems?: PendingChargeItemRowDto[];
    payments?: ChargeSettlementInsuranceSummaryDto[];
  }>(),
  {
    chargeItems: () => [],
    payments: () => [],
  },
);

const visible = defineModel<boolean>({ required: true });
/** 当前选中支付流水（与父级同步，便于补打等） */
const selectedPaymentId = defineModel<string>('paymentId', { default: '' });

const dialogSelectedPaymentId = ref('');
/** 弹窗内表格分页占位（无服务端分页） */
const paymentListQueryParams = ref({ pageNo: 1, pageSize: 50 });
const paymentDetailQueryParams = ref({ pageNo: 1, pageSize: 50 });

const paymentRows = computed(() => props.payments ?? []);

const selectedPayment = computed(() => {
  const id = dialogSelectedPaymentId.value;
  if (!id) {
    return null;
  }
  return paymentRows.value.find((row) => String(row.paymentId ?? '') === id) ?? null;
});

const showInsuranceSummary = computed(() => selectedPayment.value?.insuranceSettled === true);

const selectedSetlinfo = computed(
  (): ChargeSettlementSetlinfoDto | null => selectedPayment.value?.setlinfo ?? null,
);

/** 患者可读「本次谁付的」：有 setlinfo 分项优先，否则回落摘要基金总额 */
const patientPayCompositionRows = computed(() => {
  const payment = selectedPayment.value;
  if (!payment) {
    return [];
  }
  const setl = selectedSetlinfo.value;
  const rows: { key: string; label: string; value: number }[] = [];
  const pushIf = (key: string, label: string, raw: null | number | string | undefined) => {
    const n = Number(raw ?? 0);
    if (!Number.isFinite(n) || Math.abs(n) < 0.000001) {
      return;
    }
    rows.push({ key, label, value: n });
  };
  if (setl) {
    pushIf('hifpPay', '基本医保统筹', setl.hifpPay);
    pushIf('othPay', '其他支出', setl.othPay);
    pushIf('cvlservPay', '公务员医疗补助', setl.cvlservPay);
    pushIf('hifmiPay', '居民大病保险', setl.hifmiPay);
    pushIf('hifobPay', '职工大额补助', setl.hifobPay);
    pushIf('mafPay', '医疗救助', setl.mafPay);
    pushIf('acctPay', '个人账户', setl.acctPay ?? payment.acctPay);
    pushIf('psnCashPay', '现金', setl.psnCashPay ?? payment.psnCashPay);
    pushIf('fundPaySumamt', '基金支付合计', setl.fundPaySumamt ?? payment.fundPaySumamt);
  } else {
    pushIf('fundPaySumamt', '基金支付合计', payment.fundPaySumamt);
    pushIf('acctPay', '个人账户', payment.acctPay);
    pushIf('psnCashPay', '现金', payment.psnCashPay);
  }
  return rows;
});

const showPatientPayComposition = computed(() => patientPayCompositionRows.value.length > 0);

const showSetlinfoDetail = computed(() => {
  const setlinfo = selectedSetlinfo.value;
  if (!setlinfo) {
    return false;
  }
  return OUTPATIENT_CHARGE_SETLINFO_FIELD_DEFS.some((def) => {
    const raw = setlinfo[def.key];
    if (raw === undefined || raw === null) {
      return false;
    }
    return String(raw).trim().length > 0;
  });
});

const setlinfoDisplayRows = computed(() => {
  const setlinfo = selectedSetlinfo.value;
  if (!setlinfo) {
    return [];
  }
  const rows: { key: string; label: string; kind: 'money' | 'ratio' | 'text'; value: string }[] =
    [];
  for (const def of OUTPATIENT_CHARGE_SETLINFO_FIELD_DEFS) {
    const raw = setlinfo[def.key];
    if (raw === undefined || raw === null || String(raw).trim() === '') {
      continue;
    }
    // 金额/比例为 0 时仍展示（平台回执常见）
    let display = formatSetlinfoField(raw, def.kind);
    // 公务员标志按字典 0/1 转是/否，便于收银回看
    if (def.key === 'cvlservFlag') {
      display = formatCvlservFlagText(raw);
    }
    rows.push({
      key: def.key,
      label: def.label,
      kind: def.kind,
      value: display,
    });
  }
  return rows;
});

/** 明细：优先后端 chargeLines，再回落收银台 items */
const paymentDetailItems = computed((): ChargeSettlementPaymentLineDto[] => {
  const payment = selectedPayment.value;
  if (!payment) {
    return [];
  }
  const chargeItemById = new Map<string, PendingChargeItemRowDto>();
  for (const row of props.chargeItems ?? []) {
    const id = String(row.chargeItemId ?? '').trim();
    if (id) {
      chargeItemById.set(id, row);
    }
  }
  const lines = payment.chargeLines;
  if (lines && lines.length > 0) {
    return lines.map((line) => mergePaymentLineWithCashierItem(line, chargeItemById));
  }
  const ids = payment.chargeItemIds;
  if (!ids || ids.length === 0) {
    return [];
  }
  const idSet = new Set(ids.map((id) => String(id)));
  return (props.chargeItems ?? [])
    .filter((row) => idSet.has(String(row.chargeItemId ?? '')))
    .map((row) => ({
      chargeItemId: row.chargeItemId,
      busNo: row.busNo,
      itemName: row.itemName,
      quantityDisplay: row.quantityDisplay,
      quantityUnit: row.quantityUnit,
      statusCode: row.statusCode,
      statusText: row.statusText,
      totalPrice: row.totalPrice,
    }));
});

/**
 * 支付明细与收银台同行对齐：缺名或名称被回落成单据号时，用列表项目名校正。
 */
function mergePaymentLineWithCashierItem(
  line: ChargeSettlementPaymentLineDto,
  chargeItemById: Map<string, PendingChargeItemRowDto>,
): ChargeSettlementPaymentLineDto {
  const fromList = chargeItemById.get(String(line.chargeItemId ?? '').trim());
  if (!fromList) {
    return line;
  }
  const lineName = String(line.itemName ?? '').trim();
  const busNo = String(line.busNo ?? fromList.busNo ?? '').trim();
  const listName = String(fromList.itemName ?? '').trim();
  const useListName = !!listName && (!lineName || (busNo.length > 0 && lineName === busNo));
  const listQty = String(fromList.quantityDisplay ?? '').trim();
  const listUnit = String(fromList.quantityUnit ?? '').trim();
  return {
    ...line,
    itemName: useListName ? listName : line.itemName || listName || line.itemName,
    quantityDisplay: listQty || line.quantityDisplay,
    quantityUnit: listUnit || line.quantityUnit,
  };
}

const paymentListColumns: TableColumn[] = [
  {
    prop: 'paymentNo',
    label: '支付单号',
    minWidth: 160,
    align: 'left',
    headerAlign: 'center',
    slot: 'paymentNo',
    showOverflowTooltip: true,
  },
  {
    prop: 'displayAmount',
    label: '金额',
    width: 110,
    align: 'right',
    headerAlign: 'center',
    slot: 'paymentAmount',
  },
  {
    prop: 'billDate',
    label: '账单时间',
    width: 160,
    align: 'center',
    headerAlign: 'center',
    slot: 'billDate',
  },
];

const paymentDetailColumns: TableColumn[] = [
  {
    prop: 'busNo',
    label: '单据号',
    minWidth: 120,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'itemName',
    label: '收费项目',
    minWidth: 160,
    align: 'left',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'quantityDisplay',
    label: '数量',
    width: 88,
    align: 'center',
    headerAlign: 'center',
    slot: 'quantity',
    showOverflowTooltip: true,
  },
  {
    prop: 'statusText',
    label: '状态',
    width: 88,
    align: 'center',
    headerAlign: 'center',
    slot: 'status',
  },
  {
    prop: 'totalPrice',
    label: '金额',
    width: 100,
    align: 'right',
    headerAlign: 'center',
    slot: 'totalPrice',
  },
];

function formatMoney(value: null | number | string | undefined) {
  return formatMoneyDisplay(value);
}

function formatSetlinfoField(
  value: null | number | string | undefined,
  kind: 'money' | 'ratio' | 'text',
) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return '—';
  }
  if (kind === 'text') {
    return String(value).trim();
  }
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return String(value);
  }
  if (kind === 'ratio') {
    return `${(num * 100).toFixed(2)}%`;
  }
  return `¥${formatMoney(num)}`;
}

/** 公务员标志展示：0→否 / 1→是，其余保留原文 */
function formatCvlservFlagText(value: null | number | string | undefined) {
  const text = String(value ?? '').trim();
  if (text === '1') {
    return '是';
  }
  if (text === '0') {
    return '否';
  }
  return text || '—';
}

/** 列表金额：displayAmount 为 0 时回落医保医疗费总额 */
function resolvePaymentListAmount(row: ChargeSettlementInsuranceSummaryDto) {
  const display = Number(row.displayAmount);
  if (Number.isFinite(display) && display !== 0) {
    return display;
  }
  const medfee = Number(row.medfeeSumamt);
  if (Number.isFinite(medfee) && medfee !== 0) {
    return medfee;
  }
  return display || medfee || 0;
}

function formatBillDate(billDate: null | string | undefined) {
  if (!billDate) {
    return '—';
  }
  return dayjs(billDate).format('YYYY-MM-DD HH:mm');
}

function paymentRowClassName({ row }: { row: ChargeSettlementInsuranceSummaryDto }) {
  return String(row.paymentId ?? '') === dialogSelectedPaymentId.value
    ? 'is-selected-payment-row'
    : '';
}

function applyDialogPayment(paymentId: string) {
  const id = String(paymentId ?? '').trim();
  dialogSelectedPaymentId.value = id;
  selectedPaymentId.value = id;
}

function syncSelectionOnOpen() {
  const options = paymentRows.value;
  const preferred = String(selectedPaymentId.value ?? '').trim();
  if (preferred && options.some((row) => String(row.paymentId ?? '') === preferred)) {
    applyDialogPayment(preferred);
    return;
  }
  applyDialogPayment(String(options[0]?.paymentId ?? ''));
}

watch(visible, (open) => {
  if (open) {
    syncSelectionOnOpen();
  }
});

watch(
  () => props.payments,
  () => {
    if (visible.value) {
      syncSelectionOnOpen();
    }
  },
);

function handlePaymentRowClick(row: ChargeSettlementInsuranceSummaryDto) {
  applyDialogPayment(String(row.paymentId ?? ''));
}

function handleClose() {
  visible.value = false;
}
</script>

<template>
  <AppDialog v-model="visible" title="支付记录" width="960px">
    <div class="payment-dialog">
      <section class="payment-dialog__section">
        <SectionTitle title="支付单" />
        <RegisterStyleTableLayout
          v-if="paymentRows.length > 0"
          class="mt-2 !p-0"
          :table-data="paymentRows"
          :table-columns="paymentListColumns"
          :query-params="paymentListQueryParams"
          :total="paymentRows.length"
          :show-pagination="false"
          :show-top-query="false"
          :max-height="220"
          row-key="paymentId"
          :row-class-name="paymentRowClassName"
          @row-click="handlePaymentRowClick"
        >
          <template #paymentNo="{ row }">
            {{ String(row.paymentNo ?? '').trim() || row.paymentId || '—' }}
          </template>
          <template #paymentAmount="{ row }">
            ¥{{ formatMoney(resolvePaymentListAmount(row)) }}
          </template>
          <template #billDate="{ row }">
            {{ formatBillDate(row.billDate) }}
          </template>
        </RegisterStyleTableLayout>
        <ElEmpty v-else class="py-4" description="暂无支付记录" :image-size="64" />
      </section>

      <section v-if="selectedPayment && showInsuranceSummary" class="payment-dialog__section">
        <SectionTitle title="医保费用摘要" />
        <div class="payment-dialog__summary mt-2">
          <div class="payment-dialog__summary-item">
            <span class="payment-dialog__summary-label">医疗费总额</span>
            <span>¥{{ formatMoney(selectedPayment.medfeeSumamt) }}</span>
          </div>
          <div class="payment-dialog__summary-item payment-dialog__summary-item--fund">
            <span class="payment-dialog__summary-label">基金支付合计</span>
            <span>¥{{ formatMoney(selectedPayment.fundPaySumamt) }}</span>
          </div>
          <div class="payment-dialog__summary-item payment-dialog__summary-item--fund">
            <span class="payment-dialog__summary-label">个账</span>
            <span>¥{{ formatMoney(selectedPayment.acctPay) }}</span>
          </div>
          <div class="payment-dialog__summary-item">
            <span class="payment-dialog__summary-label">自付</span>
            <span>¥{{ formatMoney(selectedPayment.psnCashPay) }}</span>
          </div>
        </div>
      </section>

      <section v-if="selectedPayment && showPatientPayComposition" class="payment-dialog__section">
        <SectionTitle title="本次谁付的" />
        <div class="payment-dialog__summary payment-dialog__summary--compose mt-2">
          <div
            v-for="row in patientPayCompositionRows"
            :key="row.key"
            class="payment-dialog__summary-item"
            :class="{ 'payment-dialog__summary-item--fund': row.key !== 'psnCashPay' }"
          >
            <span class="payment-dialog__summary-label">{{ row.label }}</span>
            <span>¥{{ formatMoney(row.value) }}</span>
          </div>
        </div>
      </section>

      <section v-if="selectedPayment && showSetlinfoDetail" class="payment-dialog__section">
        <SectionTitle title="医保分摊（国家局 2206）" />
        <ElDescriptions class="payment-dialog__setlinfo mt-2" :column="2" border size="small">
          <ElDescriptionsItem
            v-for="item in setlinfoDisplayRows"
            :key="item.key"
            :label="item.label"
            :span="item.kind === 'text' ? 2 : 1"
          >
            {{ item.value }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </section>

      <section v-if="selectedPayment" class="payment-dialog__section">
        <SectionTitle title="收费明细" />
        <RegisterStyleTableLayout
          v-if="paymentDetailItems.length > 0"
          class="mt-2 !p-0"
          :table-data="paymentDetailItems"
          :table-columns="paymentDetailColumns"
          :query-params="paymentDetailQueryParams"
          :total="paymentDetailItems.length"
          :show-pagination="false"
          :show-top-query="false"
          :max-height="280"
          row-key="chargeItemId"
        >
          <template #quantity="{ row }">
            {{ [row.quantityDisplay, row.quantityUnit].filter(Boolean).join('') || '—' }}
          </template>
          <template #status="{ row }">
            <ElTag size="small" effect="plain">
              {{ row.statusText || row.statusCode || '—' }}
            </ElTag>
          </template>
          <template #totalPrice="{ row }">¥{{ formatMoney(row.totalPrice) }}</template>
        </RegisterStyleTableLayout>
        <p v-else class="payment-dialog__hint mt-2">该支付单暂无关联收费明细</p>
      </section>
    </div>

    <template #footer>
      <ElButton @click="handleClose">关闭</ElButton>
    </template>
  </AppDialog>
</template>

<style scoped>
.payment-dialog {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.payment-dialog__section {
  min-width: 0;
}

.payment-dialog__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.payment-dialog__summary--compose {
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
}

.payment-dialog__summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.payment-dialog__summary-item--fund {
  color: #2563eb;
}

.payment-dialog__summary-label {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.payment-dialog__hint {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.payment-dialog__setlinfo :deep(.el-descriptions__label) {
  width: 160px;
  color: var(--el-text-color-secondary);
}
</style>
