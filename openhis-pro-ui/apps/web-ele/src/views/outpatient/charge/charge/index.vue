<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  ChargeQueueRowDto,
  ChargeSettlementInsuranceSummaryDto,
  PendingChargeItemRowDto,
  PreSettleChargeLinePreviewDto,
  PreSettleOutpatientChargesResponse,
} from '#/api/outpatient-charge/outpatient-charge.types';
import type { BillingPatientCardPatient } from '#/components/charge/billing-patient-card.types';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { RotateCw } from '@vben/icons';

import { useDebounceFn } from '@vueuse/core';
import {
  runWorkInjuryCharge2207Invoke,
  runWorkInjuryChargePreSettleInvoke,
  runWorkInjuryRefundReverse,
} from '#/chs/work-injury/use-work-injury-settle';
import WorkInjuryFilingDetailDialog from '#/chs/work-injury/work-injury-filing-detail-dialog.vue';
import dayjs from 'dayjs';
import {
  ElBadge,
  ElButton,
  ElCard,
  ElDatePicker,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElRadio,
  ElRadioGroup,
  ElTag,
} from 'element-plus';

import {
  httpCompensateInsuranceSettlementsByPayment,
  httpConfirmOutpatientChargeSettlement,
  httpConfirmOutpatientInsuranceBeforeScan,
  httpLoadChargePageInit,
  httpLoadPendingChargeItems,
  httpPageOutpatientChargeQueue,
  httpPrepareUnionPayMakeupCollect,
  httpPreSettleOutpatientCharges,
  httpSwitchEncounterChargeItemsToSelfPay,
} from '#/api/outpatient-charge/outpatient-charge.http';
import BillingPatientCard from '#/components/charge/billing-patient-card.vue';
import EinvoiceBillAmountMismatchBanner from '#/components/einvoice/EinvoiceBillAmountMismatchBanner.vue';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { usePrintClientWarmup } from '#/composables/use-print-client-warmup';
import { isScanPayChannel, useUnionPayScanPay } from '#/composables/payment/useUnionPayScanPay';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { buildDefaultDateRangeShortcuts } from '#/utils/format/date-picker-shortcuts';
import {
  getDateRangeQuick,
  getLocalDayRangeDateTimeStrings,
} from '#/utils/format/date-range-quick';
import { sumMoneyThenSettle } from '#/utils/format/money-scale';
import {
  FALLBACK_DEFAULT_PAY_CHANNEL,
  optionDtosToPayChannelOptions,
  resolveCollectPayChannelCode,
} from '#/utils/payment/payment-channel-options';
import {
  cacheClientScanResult,
  clearCachedClientScanResult,
  hasCachedClientScanSuccess,
} from '#/utils/payment/scan-settle-cache';
import {
  ScanSettleIncompleteError,
  ScanSettleRefundedError,
  settleWithScanRecovery,
} from '#/utils/payment/scan-settle-recovery';
import { submitScanPayment } from '#/utils/payment/submit-scan-payment';
import { httpGetBpcOrdersByPayment } from '#/api/payment/union-pay-bpc';
import { printChargeReceipt, schedulePrintChargeReceipt } from '#/utils/print/print-charge-receipt';

import {
  formatPatientFeeMoney,
  summarizeOutpatientChargeFeeAmounts,
} from '../services/patient-fee-summary.service';
import '../charge-workbench-panel.css';
import {
  CHARGE_ITEM_STATUS_LABEL_TAG,
  OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS,
  OUTPATIENT_CHARGE_SETTLE_OUTCOME_COMPLETED,
  resolveOutpatientChargeSwitchAccountMessage,
} from './outpatient-charge.constants';
import {
  chooseOutpatientChargeInsuranceMedium,
  outpatientChargeReadCredentialFailHint,
  outpatientChargeReadCredentialHint,
  outpatientChargeReadCredentialLabel,
  resolveInsuranceContractId,
  resolveOutpatientChargeInsuranceContext,
  selectionIncludesInsuranceItems,
  selectionIncludesWorkInjuryItems,
} from './use-outpatient-charge-insurance';
import { isStaleCrossRoleError } from '#/utils/http/stale-cross-role-error';
import { isUnmappedYbNoComplianceError } from '#/utils/http/format-catalog-unmapped-yb-error';
import ChargeProgressDialog, { type ChargeProgressStep } from './charge-progress-dialog.vue';
import ChargePaymentRecordsDialog from './payment-records-dialog.vue';
import ChargeSettlementDialog from './settlement-dialog.vue';
import DetailAuditRemindDialog from '#/chs/components/detail-audit-remind-dialog.vue';
import {
  runOpDetailAuditGate,
  type DetailAuditRemindDialogExpose,
} from '#/chs/composables/use-detail-audit-gate';
import { resolveInsuranceContractById } from '#/chs/utils/resolve-insurance-contract-bus-no';

defineOptions({
  name: 'ChargeOpCharge',
});

/** 主列表展平行：同一收费单（支付单/单据号）合并勾选 */
interface ChargeDetailTableRow extends PendingChargeItemRowDto {
  billGroupKey: string;
  billGroupLineCount: number;
  isFirstLineInBillGroup: boolean;
  paymentId?: string;
  paymentNo?: string;
}

/** 同一收费单内需纵向合并的列 */
const BILL_GROUP_MERGE_FIELDS = new Set(['paymentNo', 'busNo']);

usePrintClientWarmup();

const detailAuditDialogRef = ref<DetailAuditRemindDialogExpose | null>(null);

const QUEUE_TAB = {
  ALL: 'ALL',
  PENDING: 'PENDING',
  SETTLED: 'SETTLED',
} as const;

const queueTabCode = ref<string>(QUEUE_TAB.PENDING);
const keyword = ref('');
/** 默认查当天；请求时再拼本地日 00:00:00～23:59:59 */
const dateRange = ref<[string, string] | null>(getDateRangeQuick({ strategy: 'calendar-day' }));
/** 区间查询：展开面板默认快捷时间范围 */
const dateRangeShortcuts = buildDefaultDateRangeShortcuts();
const queuePager = reactive({
  pageNo: 1,
  pageSize: 20,
});
const queueRecords = ref<ChargeQueueRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);

const selectedEncounterId = ref('');
/** 工伤收费缺类型12审批时，引导一键备案用的 contractId */
const workInjuryFilingContractId = ref<string | number | undefined>(undefined);
const workInjuryFilingDialogRef = ref<InstanceType<typeof WorkInjuryFilingDetailDialog> | null>(
  null,
);
const pendingItems = ref<PendingChargeItemRowDto[]>([]);
/** 是否允许转医保/转自费（须双账户；纯自费挂号为 false） */
const accountSwitchEnabled = ref(false);
/** 本就诊全部成功支付摘要（账单时间倒序） */
const settlementInsuranceOptions = ref<ChargeSettlementInsuranceSummaryDto[]>([]);
/** 当前选中的支付流水主键（支付记录弹窗 / 补打回落） */
const selectedSettlementPaymentId = ref('');
/** 支付记录弹窗 */
const paymentRecordsVisible = ref(false);
const selectedChargeRows = ref<PendingChargeItemRowDto[]>([]);
/** 主列表勾选的已结算支付单（补打小票） */
const selectedSettledPaymentIds = ref<string[]>([]);

const preSettleSnapshot = ref<null | PreSettleOutpatientChargesResponse>(null);
/** 预结算成功时锁定勾选 id，避免弹窗打开后表格勾选变化导致与草稿不一致 */
const lockedChargeItemIds = ref<string[]>([]);
/** 读卡 1101 返回的个账支付前余额，供确认收费弹窗「账户信息」展示 */
const settlementInsuranceBalc = ref<null | number | string>(null);

/** 结算弹窗预览行：从待收明细补齐数量与单位（预结算接口不返回） */
const settlementPreviews = computed<PreSettleChargeLinePreviewDto[]>(() => {
  const previews = preSettleSnapshot.value?.previews ?? [];
  if (previews.length === 0) {
    return previews;
  }
  const lockedIds = new Set(lockedChargeItemIds.value);
  const quantityByChargeItemId = new Map<
    string,
    Pick<PendingChargeItemRowDto, 'quantityDisplay' | 'quantityUnit'>
  >();
  for (const row of pendingItems.value) {
    if (lockedIds.has(row.chargeItemId)) {
      quantityByChargeItemId.set(row.chargeItemId, {
        quantityDisplay: row.quantityDisplay,
        quantityUnit: row.quantityUnit,
      });
    }
  }
  return previews.map((preview) => {
    const quantity = quantityByChargeItemId.get(preview.chargeItemId);
    if (!quantity) {
      return preview;
    }
    return {
      ...preview,
      quantityDisplay: quantity.quantityDisplay,
      quantityUnit: quantity.quantityUnit,
    };
  });
});
const settlementInsuranceBreakdown = computed(() => {
  const snap = preSettleSnapshot.value;
  if (!snap?.insurancePreSettled) {
    return [];
  }
  // 基金合计 ≠ 统筹：统筹用 hifp，其他支出（含电子钱包）用 oth
  return [
    { key: 'fundPay', label: '基金支付合计', value: Number(snap.ybFundPaySumamt ?? 0) },
    { key: 'poolingPay', label: '基本医保统筹', value: Number(snap.ybHifpPay ?? 0) },
    { key: 'otherPay', label: '其他支出', value: Number(snap.ybOthPay ?? 0) },
    { key: 'accountPay', label: '医保个人账户', value: Number(snap.ybAcctPay ?? 0) },
    { key: 'deductibleLine', label: '起付线', value: Number(snap.ybActPayDedc ?? 0) },
    { key: 'cvlservPay', label: '公务员医疗补助', value: Number(snap.ybCvlservPay ?? 0) },
    { key: 'cashPay', label: '个人现金（医保口径）', value: Number(snap.ybPsnCashPay ?? 0) },
    { key: 'finalReceivable', label: '个人现金（待收）', value: Number(snap.displayAmount ?? 0) },
  ];
});

/** 预结算透传的公务员标志（账户信息区展示） */
const settlementCvlservFlag = computed(() => {
  const flag = preSettleSnapshot.value?.ybCvlservFlag;
  if (flag === undefined || flag === null) {
    return undefined;
  }
  const text = String(flag).trim();
  if (!text) {
    return undefined;
  }
  return text;
});
const settlementSummaryInsurance = computed(() =>
  Number(preSettleSnapshot.value?.ybMedfeeSumamt ?? 0),
);
const settlementSummarySelfPay = computed(() =>
  Number(preSettleSnapshot.value?.selfPayRollup?.totalAmount ?? 0),
);
const settlementInsuranceGroups = computed(() => preSettleSnapshot.value?.insuranceGroups ?? []);
const settlementVisible = ref(false);
const preSettleLoading = ref(false);
/** 银联补收：复用已确认 2207，禁止扫码失败/落账失败时误冲正医保 */
const unionPayMakeupCollectMode = ref(false);
const makeupCollectLoading = ref(false);

/** 确认收费过程进度（读卡 / 预结算 / 正式结算） */
const chargeProgressVisible = ref(false);
const chargeProgressTitle = ref('收费处理中');
const chargeProgressSteps = ref<ChargeProgressStep[]>([]);
const chargeProgressActiveIndex = ref(0);
const chargeProgressDetail = ref('');

function openChargeProgress(title: string, steps: ChargeProgressStep[], detail = '') {
  chargeProgressTitle.value = title;
  chargeProgressSteps.value = steps;
  chargeProgressActiveIndex.value = 0;
  chargeProgressDetail.value = detail;
  chargeProgressVisible.value = true;
}

function setChargeProgressStep(index: number, detail?: string) {
  chargeProgressActiveIndex.value = index;
  if (detail !== undefined) {
    chargeProgressDetail.value = detail;
  }
}

function closeChargeProgress() {
  chargeProgressVisible.value = false;
  chargeProgressDetail.value = '';
}
const confirmSettleLoading = ref(false);
/** 补打收费小票 loading */
const reprintLoading = ref(false);

const paymentForm = reactive({
  payChannelCode: FALLBACK_DEFAULT_PAY_CHANNEL,
});

const payChannelOptions = ref([...OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS]);
const scanCode = ref('');
const scanStatusText = ref('');

const {
  scanPayEnabled,
  hasScanPayChannel,
  effectivePayChannelOptions,
  refreshUnionPayCapabilities,
} = useUnionPayScanPay(() => payChannelOptions.value);

/** 预结算后患者现金应付（个账/基金全额时为 0） */
const settlementCashPay = computed(() => {
  const cash = Number(preSettleSnapshot.value?.displayAmount ?? 0);
  return Number.isFinite(cash) ? cash : 0;
});

/** 零现金时禁止选扫码，避免误提交 SCAN_PAY 导致 2207 后本地回滚 */
const settlementPayChannelOptions = computed(() => {
  if (settlementCashPay.value > 0.001) {
    return effectivePayChannelOptions.value;
  }
  return effectivePayChannelOptions.value.filter((opt) => !isScanPayChannel(opt.value));
});

const settlementScanPayEnabled = computed(
  () => hasScanPayChannel.value && settlementCashPay.value > 0.001,
);

/** 半成功：本机已有银联成功凭证时锁定付款码，禁止重扫 */
const settlementScanInputLocked = computed(() => {
  const draftId = preSettleSnapshot.value?.paymentDraftId;
  if (draftId == null || draftId === '') {
    return false;
  }
  return hasCachedClientScanSuccess(draftId);
});

const chargeTableQueryParams = reactive({
  pageNo: 1,
  pageSize: 20,
});

const chargeTableLayoutRef = ref<InstanceType<typeof RegisterStyleTableLayout> | null>(null);
/** 程序同步勾选时跳过 selection-change，避免 clearCheckboxRow 把已选清空 */
const syncingChargeSelection = ref(false);

/** 待收表格底层 VxeGrid（RegisterStyleTableLayout → gridApi，可能多层 ref） */
function getChargeVxeGrid(): null | Record<string, unknown> {
  let cur: unknown = (chargeTableLayoutRef.value as null | { gridApi?: unknown })?.gridApi;
  for (let i = 0; i < 4 && cur && typeof cur === 'object' && 'value' in cur; i++) {
    const obj = cur as Record<string, unknown>;
    // 已是 Vxe 实例时勿再拆 .value（实例上也可能有 value 字段）
    if (
      typeof obj.setCheckboxRow === 'function' ||
      typeof obj.setAllCheckboxRow === 'function' ||
      typeof obj.clearCheckboxRow === 'function'
    ) {
      break;
    }
    cur = obj.value;
  }
  if (!cur || typeof cur !== 'object') {
    return null;
  }
  const g = cur as { grid?: Record<string, unknown> };
  const resolved = (g.grid ?? cur) as Record<string, unknown>;
  if (
    typeof resolved.setCheckboxRow === 'function' ||
    typeof resolved.setAllCheckboxRow === 'function' ||
    typeof resolved.clearCheckboxRow === 'function'
  ) {
    return resolved;
  }
  return (g.grid as Record<string, unknown> | undefined) ?? (cur as Record<string, unknown>);
}

/** 是否已结算行（仅认 statusCode） */
function isOutpatientChargeItemSettled(row: PendingChargeItemRowDto) {
  return (
    String(row.statusCode ?? '')
      .trim()
      .toUpperCase() === 'SETTLED'
  );
}

/** 仅非「已结算」行可勾选 */
function isPendingChargeItemSelectable(row: PendingChargeItemRowDto) {
  return !isOutpatientChargeItemSettled(row);
}

/** 正式结算失败：Stale 跨角色（如签退后旧 draft） */
function isOutpatientChargeStaleSettlementError(error: unknown) {
  return isStaleCrossRoleError(error);
}

/**
 * 预结算/工伤 Win 错误文案。
 * <p>{@code RequestClient} 失败时 throw 的是 {@code response.data}（普通对象，非 Error），
 * 只读 {@code instanceof Error} 会落到无意义兜底并盖住拦截器已提示的真实原因。</p>
 * @returns 需本地再弹的文案；空串表示拦截器已处理、勿再弹
 */
function resolveOutpatientPreSettleErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message.trim() || fallback;
  }
  if (error && typeof error === 'object') {
    const row = error as {
      message?: unknown;
      msg?: unknown;
      error?: unknown;
      errorCode?: unknown;
      data?: { message?: unknown; msg?: unknown };
      response?: { data?: { message?: unknown; msg?: unknown; error?: unknown } };
    };
    const candidates = [
      row.response?.data?.message,
      row.response?.data?.msg,
      row.response?.data?.error,
      row.message,
      row.msg,
      row.error,
      row.data?.message,
      row.data?.msg,
    ];
    let extracted = '';
    for (const candidate of candidates) {
      if (typeof candidate === 'string' && candidate.trim()) {
        extracted = candidate.trim();
        break;
      }
    }
    const errorCode = typeof row.errorCode === 'string' ? row.errorCode : '';
    // request.ts 对工伤 MODULE_NOT_AVAILABLE / NOT_FOUND 故意不弹，需本地补弹
    if (errorCode === 'MODULE_NOT_AVAILABLE' || errorCode === 'NOT_FOUND') {
      return extracted || fallback;
    }
    // 其余业务 Result：拦截器已 ElMessage，返回空避免二次兜底覆盖
    if (errorCode || row.message != null || row.msg != null) {
      return '';
    }
    return extracted || fallback;
  }
  if (typeof error === 'string' && error.trim()) {
    return error.trim();
  }
  return fallback;
}

/** 抽出预结算错误原文（含拦截器已提示的业务 message），供判断是否引导一键备案。 */
function extractOutpatientPreSettleRawMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message.trim();
  }
  if (error && typeof error === 'object') {
    const row = error as {
      message?: unknown;
      msg?: unknown;
      error?: unknown;
      data?: { message?: unknown; msg?: unknown };
      response?: { data?: { message?: unknown; msg?: unknown; error?: unknown } };
    };
    const candidates = [
      row.response?.data?.message,
      row.response?.data?.msg,
      row.response?.data?.error,
      row.message,
      row.msg,
      row.error,
      row.data?.message,
      row.data?.msg,
    ];
    for (const candidate of candidates) {
      if (typeof candidate === 'string' && candidate.trim()) {
        return candidate.trim();
      }
    }
  }
  if (typeof error === 'string' && error.trim()) {
    return error.trim();
  }
  return '';
}

function shouldGuideWorkInjuryFiling(message: string): boolean {
  return /类型\s*12|一键备案|审批类型\s*10|联网结算未开通|就医类型|缺少有效门诊工伤审批/.test(
    message,
  );
}

async function resetSettlementAfterAdviceChanged() {
  settlementVisible.value = false;
  scanCode.value = '';
  scanStatusText.value = '';
  preSettleSnapshot.value = null;
  settlementInsuranceBalc.value = null;
  lockedChargeItemIds.value = [];
  await loadPendingForSelectedEncounter();
}

/**
 * vxe 列 `checkMethod` 签名为 `({ row })`，TableColumn 的 `selectable` 类型为 `(row, index)`。
 * 同一收费单仅组首行可勾选；待收整单收费，已结算整单补打。
 */
function chargeItemSelectionSelectable(arg0: Record<string, unknown>, _index: number) {
  const row = (
    arg0 && typeof arg0 === 'object' && 'row' in arg0
      ? (arg0 as { row: ChargeDetailTableRow }).row
      : arg0
  ) as ChargeDetailTableRow;
  if (row.isFirstLineInBillGroup !== true) {
    return false;
  }
  if (isPendingChargeItemSelectable(row)) {
    return true;
  }
  return Boolean(String(row.paymentId ?? '').trim());
}

/** 按组首行勾选结果展开为整单行 */
function expandSelectionToWholeBillGroups(selection: ChargeDetailTableRow[]) {
  const selectedGroupKeys = new Set<string>();
  for (const row of displayedPendingItems.value) {
    if (!row.isFirstLineInBillGroup) {
      continue;
    }
    const checked = selection.some((item) => item.chargeItemId === row.chargeItemId);
    if (checked) {
      selectedGroupKeys.add(row.billGroupKey);
    }
  }
  return displayedPendingItems.value.filter((row) => selectedGroupKeys.has(row.billGroupKey));
}

/** 从整单勾选结果拆出待收行与可补打支付单 */
function applyExpandedChargeSelection(expanded: ChargeDetailTableRow[]) {
  selectedChargeRows.value = expanded.filter((row) => isPendingChargeItemSelectable(row));
  const paymentIds: string[] = [];
  const seen = new Set<string>();
  for (const row of expanded) {
    if (!isOutpatientChargeItemSettled(row)) {
      continue;
    }
    const paymentId = String(row.paymentId ?? '').trim();
    if (!paymentId || seen.has(paymentId)) {
      continue;
    }
    seen.add(paymentId);
    paymentIds.push(paymentId);
  }
  selectedSettledPaymentIds.value = paymentIds;
}

/** 将勾选状态同步到底层 VxeGrid（整单行一并勾选） */
function applyChargeGridCheckboxSelection(
  rows: ChargeDetailTableRow[],
  grid?: null | Record<string, unknown>,
) {
  const doApply = (targetGrid: Record<string, unknown>) => {
    if (typeof targetGrid.setCheckboxRow !== 'function') {
      return;
    }
    const selectedIds = new Set(rows.map((row) => row.chargeItemId));
    const tableDataResult =
      typeof targetGrid.getTableData === 'function'
        ? (targetGrid.getTableData() as {
            fullData?: ChargeDetailTableRow[];
            tableData?: ChargeDetailTableRow[];
          })
        : null;
    const tableRows =
      tableDataResult?.fullData ?? tableDataResult?.tableData ?? displayedPendingItems.value;
    for (const row of tableRows) {
      targetGrid.setCheckboxRow(row, selectedIds.has(row.chargeItemId));
    }
  };
  if (grid) {
    doApply(grid);
    return;
  }
  void nextTick(() => {
    const targetGrid = getChargeVxeGrid();
    if (targetGrid) {
      doApply(targetGrid);
    }
  });
}

/**
 * 选中患者并加载明细后：默认勾选全部待收整单。
 * Table.vue 在 tableData 变更后会 post-flush → clearSelection（再 await nextTick），
 * 必须等清空完成后再勾，否则会被冲掉。
 */
async function selectAllPendingRowsInGrid(items: ChargeDetailTableRow[]) {
  const selectable = items.filter(
    (r) => r.isFirstLineInBillGroup === true && isPendingChargeItemSelectable(r),
  );
  const expanded = expandSelectionToWholeBillGroups(selectable);
  syncingChargeSelection.value = true;
  applyExpandedChargeSelection(expanded);

  // 对齐 Table.clearSelection：flush post + 其内部 nextTick；再 macro task 兜底
  await nextTick();
  await nextTick();
  await new Promise<void>((resolve) => {
    setTimeout(resolve, 0);
  });

  let grid = getChargeVxeGrid();
  if (!grid) {
    await nextTick();
    grid = getChargeVxeGrid();
  }

  if (grid) {
    if (typeof grid.clearCheckboxRow === 'function') {
      grid.clearCheckboxRow();
    }
    if (expanded.length > 0) {
      applyChargeGridCheckboxSelection(expanded, grid);
    }
  }

  await nextTick();
  applyExpandedChargeSelection(expanded);
  syncingChargeSelection.value = false;
}

/** 将金额安全格式化为两位小数字符串 */
const formatMoney = formatPatientFeeMoney;

/** 表格文本列：空串/空白显示为「—」 */
const chargeItemDashFormatter: NonNullable<TableColumn['formatter']> = (_row, _col, cellValue) => {
  if (cellValue === null || cellValue === undefined || cellValue === '') {
    return '—';
  }
  const s = String(cellValue).trim();
  return s.length > 0 ? s : '—';
};

/** 数量列：展示值后拼接单位，如「1支」 */
const chargeItemQuantityFormatter: NonNullable<TableColumn['formatter']> = (
  row,
  _col,
  cellValue,
) => {
  const display = chargeItemDashFormatter(row, _col, cellValue);
  if (display === '—') {
    return '—';
  }
  const unit = String((row as PendingChargeItemRowDto).quantityUnit ?? '').trim();
  return unit ? `${display}${unit}` : display;
};

/** 就诊类型列：优先 medTypeDisplay，缺省回落 medTypeCode */
const chargeItemMedTypeFormatter: NonNullable<TableColumn['formatter']> = (
  row,
  _col,
  cellValue,
) => {
  const display = chargeItemDashFormatter(row, _col, cellValue);
  if (display !== '—') {
    return display;
  }
  return chargeItemDashFormatter(row, _col, (row as PendingChargeItemRowDto).medTypeCode);
};

/**
 * 收费项 → 成功支付摘要：paymentId / paymentNo / billDate。
 * 同一明细若出现在多笔支付中，保留 options 中先遇到的一笔（摘要按账单时间倒序）。
 */
const paymentMetaByItemId = computed(() => {
  const map = new Map<string, { billDate: string; paymentId: string; paymentNo: string }>();
  for (const payment of settlementInsuranceOptions.value) {
    const paymentId = String(payment.paymentId ?? '').trim();
    if (!paymentId) {
      continue;
    }
    const paymentNo = String(payment.paymentNo ?? '').trim() || paymentId;
    const billDate = String(payment.billDate ?? '').trim();
    const meta = { paymentId, paymentNo, billDate };
    // 优先 chargeItemIds；缺省时从 chargeLines 回落
    const ids = payment.chargeItemIds ?? [];
    if (ids.length > 0) {
      for (const rawId of ids) {
        const id = String(rawId ?? '').trim();
        if (id && !map.has(id)) {
          map.set(id, meta);
        }
      }
      continue;
    }
    for (const line of payment.chargeLines ?? []) {
      const id = String(line.chargeItemId ?? '').trim();
      if (id && !map.has(id)) {
        map.set(id, meta);
      }
    }
  }
  return map;
});

/** 收费项 → 收费时间（兼容原 formatter） */
const chargeTimeByItemId = computed(() => {
  const map = new Map<string, string>();
  for (const [id, meta] of paymentMetaByItemId.value) {
    if (meta.billDate) {
      map.set(id, meta.billDate);
    }
  }
  return map;
});

/**
 * 将收银台明细按收费单分组：已结算按 paymentId，待收按单据号；同组相邻并打合并标记。
 */
function buildChargeDetailTableRows(items: PendingChargeItemRowDto[]): ChargeDetailTableRow[] {
  const metaByItemId = paymentMetaByItemId.value;
  // 摘要未回填明细时，用当前选中/队列支付流水兜底，保证已结算行仍可整单勾选补打
  const fallbackPaymentId =
    String(selectedSettlementPaymentId.value ?? '').trim() ||
    String(currentQueueRow.value?.paymentId ?? '').trim();
  const decorated = items.map((item, originalIndex) => {
    const chargeItemId = String(item.chargeItemId ?? '').trim();
    const meta = chargeItemId ? metaByItemId.get(chargeItemId) : undefined;
    let paymentId = meta?.paymentId ?? '';
    let paymentNo = meta?.paymentNo ?? '';
    if (!paymentId && isOutpatientChargeItemSettled(item) && fallbackPaymentId) {
      paymentId = fallbackPaymentId;
      paymentNo = fallbackPaymentId;
    }
    const busNo = String(item.busNo ?? '').trim();
    // 已关联支付单用支付主键分组；待收按单据号整单勾选
    const billGroupKey = paymentId
      ? `pay:${paymentId}`
      : `bus:${busNo || chargeItemId || String(originalIndex)}`;
    return {
      item,
      originalIndex,
      billGroupKey,
      paymentId,
      paymentNo,
      settled: isOutpatientChargeItemSettled(item),
    };
  });
  decorated.sort((a, b) => {
    // 待收整单置顶，便于收费；已结算按支付单聚拢，便于补打勾选
    if (a.settled !== b.settled) {
      return a.settled ? 1 : -1;
    }
    const keyCompare = a.billGroupKey.localeCompare(b.billGroupKey);
    if (keyCompare !== 0) {
      return keyCompare;
    }
    return a.originalIndex - b.originalIndex;
  });
  const groupCounts = new Map<string, number>();
  for (const row of decorated) {
    groupCounts.set(row.billGroupKey, (groupCounts.get(row.billGroupKey) ?? 0) + 1);
  }
  const seenGroups = new Set<string>();
  return decorated.map((row) => {
    const isFirstLineInBillGroup = !seenGroups.has(row.billGroupKey);
    if (isFirstLineInBillGroup) {
      seenGroups.add(row.billGroupKey);
    }
    return {
      ...row.item,
      paymentId: row.paymentId || undefined,
      paymentNo: row.paymentNo || undefined,
      billGroupKey: row.billGroupKey,
      isFirstLineInBillGroup,
      billGroupLineCount: groupCounts.get(row.billGroupKey) ?? 1,
    };
  });
}

/** 收费时间列：已结算行按支付 billDate 格式化；待收或无支付关联显示「—」 */
const chargeItemChargeTimeFormatter: NonNullable<TableColumn['formatter']> = (row) => {
  const id = String((row as PendingChargeItemRowDto).chargeItemId ?? '').trim();
  const billDate = id ? chargeTimeByItemId.value.get(id) : undefined;
  if (!billDate) {
    return '—';
  }
  const parsed = dayjs(billDate);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : '—';
};

/** 单价列：空值显示「—」，有值按金额两位展示 */
const chargeItemUnitPriceFormatter: NonNullable<TableColumn['formatter']> = (
  row,
  _col,
  cellValue,
) => {
  const raw =
    cellValue === null || cellValue === undefined
      ? (row as PendingChargeItemRowDto).unitPrice
      : cellValue;
  if (raw === null || raw === undefined || raw === '') {
    return '—';
  }
  return `¥${formatMoney(raw)}`;
};

/** 当前选中队列行 */
const currentQueueRow = computed(
  () => queueRecords.value.find((r) => r.encounterId === selectedEncounterId.value) ?? null,
);

/** 性别：优先 `genderEnumText`，否则 `genderCode`（与医生站读模型一致） */
function chargeQueueGenderDisplay(row: ChargeQueueRowDto | null | undefined): string {
  if (!row) return '—';
  const text = (row.genderEnumText ?? '').trim();
  if (text) return text;
  const code = (row.genderCode ?? '').trim();
  return code || '—';
}

/** 已选明细金额合计（展示用，以预结算返回为准收费） */
const selectedChargeSum = computed(() =>
  sumMoneyThenSettle(selectedChargeRows.value.map((row) => row.totalPrice ?? 0)),
);

/** 患者信息栏费用摘要：与待收明细同源，避免队列行空金额 */
const chargePatientFeeSummary = computed(() => {
  if (!selectedEncounterId.value) {
    return null;
  }
  return summarizeOutpatientChargeFeeAmounts(pendingItems.value, isOutpatientChargeItemSettled);
});

/** 主区收费明细：按收费单分组后全量展示 */
const displayedPendingItems = computed(() => buildChargeDetailTableRows(pendingItems.value));

/** 当前就诊是否可补打：主列表存在已关联支付单的已结算行，或队列上回落 paymentId */
const canReprintChargeReceipt = computed(() => {
  if (displayedPendingItems.value.some((row) => Boolean(String(row.paymentId ?? '').trim()))) {
    return true;
  }
  return Boolean(String(currentQueueRow.value?.paymentId ?? '').trim());
});

const paymentRecordsCount = computed(() => settlementInsuranceOptions.value.length);

/** 打开支付记录弹窗（仅弹窗展示，主页不固定支付/单据区） */
function handleOpenPaymentRecords() {
  if (!selectedEncounterId.value) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  paymentRecordsVisible.value = true;
}

const billAmountMismatchContext = computed(() => {
  const snap = preSettleSnapshot.value;
  if (snap) {
    const lineAmounts = settlementPreviews.value.map((preview) => preview.lineAmount ?? 0);
    return {
      lineAmounts,
      paymentAmount: snap.displayAmount ?? 0,
      paymentId:
        snap.paymentDraftId === null || snap.paymentDraftId === undefined
          ? ''
          : String(snap.paymentDraftId),
    };
  }
  return {
    lineAmounts: [],
    paymentAmount: 0,
    paymentId: '',
  };
});

function handleBillBannerFocusAdjust() {
  const table = document.querySelector('.charge-outpatient-workbench .el-table__body-wrapper');
  table?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

const { run: runLoadPending, loading: tableLoading } = useRequest(httpLoadPendingChargeItems, {
  manual: true,
  onSuccess: (data) => {
    const items = data.items ?? [];
    pendingItems.value = items;
    accountSwitchEnabled.value = Boolean(data.accountSwitchEnabled);
    const options = data.settlementInsuranceOptions ?? [];
    settlementInsuranceOptions.value = options;
    selectedSettlementPaymentId.value = String(
      data.settlementInsuranceSummary?.paymentId ?? options[0]?.paymentId ?? '',
    );
    selectAllPendingRowsInGrid(displayedPendingItems.value);
  },
});

/** 拉取待收/已结队列 */
const loadChargeQueue = async () => {
  queueLoading.value = true;
  try {
    const body = {
      endDate: undefined as string | undefined,
      keyword: keyword.value.trim() || undefined,
      pageNo: queuePager.pageNo,
      pageSize: queuePager.pageSize,
      queueTabCode: queueTabCode.value,
      startDate: undefined as string | undefined,
    };
    const range = dateRange.value;
    if (range?.[0] && range[1]) {
      // 起止日各自落到本地日历日边界，避免漏当天首尾时段
      body.startDate = getLocalDayRangeDateTimeStrings(range[0]).startDate;
      body.endDate = getLocalDayRangeDateTimeStrings(range[1]).endDate;
    }
    const res = await httpPageOutpatientChargeQueue(body);
    const { content, totalCount } = parsePagedRowsFromApiData<ChargeQueueRowDto>(res);
    queueRecords.value = content;
    queueTotal.value = totalCount;
  } finally {
    queueLoading.value = false;
  }
};

/** 按当前选中就诊刷新待收明细 */
const loadPendingForSelectedEncounter = async () => {
  const eid = selectedEncounterId.value;
  if (!eid) {
    pendingItems.value = [];
    accountSwitchEnabled.value = false;
    settlementInsuranceOptions.value = [];
    selectedSettlementPaymentId.value = '';
    paymentRecordsVisible.value = false;
    selectedChargeRows.value = [];
    selectedSettledPaymentIds.value = [];
    void nextTick(() => {
      const grid = getChargeVxeGrid();
      if (grid && typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    });
    return;
  }
  const paymentId = String(currentQueueRow.value?.paymentId ?? '').trim() || undefined;
  await runLoadPending({
    encounterId: eid,
    includePriceExtension: true,
    paymentId,
  });
};

/** 关键字/日期变更后回到第一页再查队列 */
const resetQueuePageAndLoad = () => {
  queuePager.pageNo = 1;
  void loadChargeQueue();
};

const debouncedQueueReload = useDebounceFn(resetQueuePageAndLoad, 400);

watch(keyword, () => {
  debouncedQueueReload();
});

watch(dateRange, () => {
  debouncedQueueReload();
});

watch(queueTabCode, () => {
  resetQueuePageAndLoad();
});

watch(selectedEncounterId, () => {
  preSettleSnapshot.value = null;
  settlementInsuranceBalc.value = null;
  settlementInsuranceOptions.value = [];
  selectedSettlementPaymentId.value = '';
  paymentRecordsVisible.value = false;
  lockedChargeItemIds.value = [];
  selectedChargeRows.value = [];
  selectedSettledPaymentIds.value = [];
  void loadPendingForSelectedEncounter();
});

/** 队列行映射为患者卡片 */
const mapToBillingPatientCard = (row: ChargeQueueRowDto): BillingPatientCardPatient => {
  const settled = row.queueLabelCode === QUEUE_TAB.SETTLED;
  return {
    id: row.encounterId,
    patientName: row.patientName,
    gender: chargeQueueGenderDisplay(row),
    ageText: row.ageText ?? undefined,
    chargeStatusCode: settled ? 'SETTLED' : 'PLANNED',
    chargeStatus: settled ? '已结算' : '待收费',
    visitDepartment: row.organizationName ?? '—',
    visitTime: row.startTime ?? '—',
    visitType: 'outpatient',
    doctorName: row.doctorName ?? undefined,
  };
};

/** 选中卡片切换当前就诊；再次点击已选患者仍刷新待收明细（避免 ref 不变导致 watch 不触发） */
const handleChoosePatient = (patient: BillingPatientCardPatient) => {
  const id = patient.id;
  if (selectedEncounterId.value === id) {
    preSettleSnapshot.value = null;
    settlementInsuranceBalc.value = null;
    lockedChargeItemIds.value = [];
    selectedChargeRows.value = [];
    selectedSettledPaymentIds.value = [];
    void loadPendingForSelectedEncounter();
    return;
  }
  selectedEncounterId.value = id;
};

/** 手工刷新队列 */
const handlePatientQueueRefresh = () => {
  void loadChargeQueue();
};

/** 分页切换 */
const handleQueuePageChange = () => {
  void loadChargeQueue();
};

/** 每页条数变化时回到第一页 */
const handleQueueSizeChange = () => {
  queuePager.pageNo = 1;
  void loadChargeQueue();
};

/** 表格勾选变更：按收费单整单展开；待收供收费，已结算支付单供补打 */
const handleChargeSelectionChange = (selection: ChargeDetailTableRow[]) => {
  if (syncingChargeSelection.value) {
    return;
  }
  const expanded = expandSelectionToWholeBillGroups(selection);
  applyExpandedChargeSelection(expanded);

  const expandedIds = new Set(expanded.map((row) => row.chargeItemId));
  const rawIds = new Set(selection.map((row) => row.chargeItemId));
  const needsSync =
    expandedIds.size !== rawIds.size || [...expandedIds].some((id) => !rawIds.has(id));
  if (needsSync) {
    syncingChargeSelection.value = true;
    applyChargeGridCheckboxSelection(expanded, getChargeVxeGrid());
    void nextTick(() => {
      syncingChargeSelection.value = false;
    });
  }
};

/** VxeGrid spanMethod：勾选列与支付单号/单据号按收费单分组纵向合并 */
function handleChargeDetailSpanMethod(params: {
  column: Record<string, unknown>;
  row: Record<string, unknown>;
}) {
  const row = params.row as ChargeDetailTableRow;
  const column = params.column as { field?: string; type?: string };
  const field = column.field;
  const shouldMerge = (field && BILL_GROUP_MERGE_FIELDS.has(field)) || column.type === 'checkbox';
  if (!shouldMerge) {
    return undefined;
  }
  if (row.isFirstLineInBillGroup) {
    return { rowspan: row.billGroupLineCount ?? 1, colspan: 1 };
  }
  return { rowspan: 0, colspan: 0 };
}

/** 收费状态：按枚举 code 映射标签；未知 code 时回退接口 statusText */
const chargeItemStatusTag = (row: PendingChargeItemRowDto) => {
  const code = row.statusCode?.trim() ?? '';
  const mapped = code ? CHARGE_ITEM_STATUS_LABEL_TAG[code.toUpperCase()] : undefined;
  if (mapped) {
    return mapped;
  }
  const text = row.statusText?.trim();
  if (text) {
    return { label: text, tagType: 'info' as const };
  }
  if (code) {
    return { label: code, tagType: 'info' as const };
  }
  return { label: '—', tagType: 'info' as const };
};

const chargeTableColumns: TableColumn[] = [
  {
    type: 'selection',
    width: 52,
    align: 'center',
    headerAlign: 'center',
    selectable: chargeItemSelectionSelectable,
  },
  {
    prop: 'paymentNo',
    label: '支付单号',
    minWidth: 150,
    align: 'center',
    headerAlign: 'center',
    formatter: chargeItemDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'busNo',
    label: '单据号',
    minWidth: 150,
    align: 'center',
    headerAlign: 'center',
    formatter: chargeItemDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'itemName',
    label: '收费项目',
    minWidth: 160,
    align: 'left',
    headerAlign: 'center',
    formatter: chargeItemDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'quantityDisplay',
    label: '数量',
    minWidth: 88,
    align: 'center',
    headerAlign: 'center',
    formatter: chargeItemQuantityFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'chargeCategoryDisplay',
    label: '医疗类型',
    minWidth: 96,
    align: 'center',
    headerAlign: 'center',
    formatter: chargeItemDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'medicalInsuranceCode',
    label: '医保编码',
    minWidth: 120,
    align: 'left',
    headerAlign: 'center',
    formatter: chargeItemDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'chrgitmLvText',
    label: '医保类别',
    minWidth: 88,
    align: 'center',
    headerAlign: 'center',
    formatter: chargeItemDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'feeNatureDisplay',
    label: '费用性质',
    minWidth: 100,
    align: 'center',
    headerAlign: 'center',
    formatter: chargeItemDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'medTypeDisplay',
    label: '就诊类型',
    minWidth: 100,
    align: 'center',
    headerAlign: 'center',
    formatter: chargeItemMedTypeFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusCode',
    label: '收费状态',
    minWidth: 100,
    align: 'center',
    headerAlign: 'center',
    slot: 'statusCell',
    showOverflowTooltip: true,
  },
  {
    prop: 'unitPrice',
    label: '单价',
    minWidth: 88,
    align: 'right',
    headerAlign: 'center',
    formatter: chargeItemUnitPriceFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'totalPrice',
    label: '金额',
    minWidth: 100,
    slot: 'totalPrice',
    align: 'right',
    headerAlign: 'center',
  },
  {
    prop: 'cashierName',
    label: '收款人',
    minWidth: 96,
    align: 'center',
    headerAlign: 'center',
    formatter: chargeItemDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'chargeTime',
    label: '收费时间',
    minWidth: 168,
    align: 'center',
    headerAlign: 'center',
    formatter: chargeItemChargeTimeFormatter,
    showOverflowTooltip: true,
  },
];

const switchAccountLoading = ref(false);

/** 勾选明细转自费：将所选待收行 accountId 切至自费账户（IF-OPC-05） */
const handleSwitchSelfPay = async () => {
  const eid = selectedEncounterId.value;
  if (!eid) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  if (selectedChargeRows.value.length === 0) {
    ElMessage.warning('请勾选需要转自费的费用项');
    return;
  }
  await ElMessageBox.confirm('将勾选的待收费用项切换到自费记账账户，是否继续？', '转自费', {
    cancelButtonText: '取消',
    confirmButtonText: '确定',
    type: 'warning',
  });
  switchAccountLoading.value = true;
  try {
    // 勾选行主键串：保持 string，避免雪花 Long 精度丢失
    const chargeItemIds = selectedChargeRows.value
      .map((row) => String(row.chargeItemId ?? '').trim())
      .filter((id) => id !== '');
    const res = await httpSwitchEncounterChargeItemsToSelfPay({
      encounterId: eid,
      chargeItemIds,
    });
    if (res.success) {
      ElMessage.success('已切换为自费记账');
      await loadPendingForSelectedEncounter();
      return;
    }
    ElMessage.error(resolveOutpatientChargeSwitchAccountMessage(res.messageCode));
  } finally {
    switchAccountLoading.value = false;
  }
};

/** 打开结算前先做预结算，拿到草稿与应收 */
const handleOpenSettlement = async () => {
  const eid = selectedEncounterId.value;
  if (!eid) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  if (selectedChargeRows.value.length === 0) {
    ElMessage.warning('请选择待收费用项');
    return;
  }
  const rows = selectedChargeRows.value;
  // 工伤与省/市医保隔离：工伤走 Win 2201→2206，不读省医保卡、不跑 3101
  const isWorkInjury = selectionIncludesWorkInjuryItems(rows);
  const needChsInsurance = selectionIncludesInsuranceItems(rows) && !isWorkInjury;
  let insuranceMedium: Awaited<ReturnType<typeof chooseOutpatientChargeInsuranceMedium>> = null;
  if (needChsInsurance) {
    insuranceMedium = await chooseOutpatientChargeInsuranceMedium();
    if (!insuranceMedium) {
      return;
    }
  }
  const readCredentialLabel = insuranceMedium
    ? outpatientChargeReadCredentialLabel(insuranceMedium)
    : '读取医保凭证';
  const progressSteps: ChargeProgressStep[] = isWorkInjury
    ? [
        { key: 'pre-settle', label: '工伤预结算（9102→2206）' },
        { key: 'open-dialog', label: '打开结算确认' },
      ]
    : needChsInsurance
      ? [
          { key: 'read-card', label: readCredentialLabel },
          { key: 'person-1101', label: '核验参保人员信息（1101）' },
          { key: 'detail-audit', label: '医保事前提醒（3101）' },
          { key: 'pre-settle', label: '医保预结算通信（2201→2206）' },
          { key: 'open-dialog', label: '打开结算确认' },
        ]
      : [
          { key: 'pre-settle', label: '生成支付草稿与应收' },
          { key: 'open-dialog', label: '打开结算确认' },
        ];

  preSettleLoading.value = true;
  openChargeProgress('确认收费处理中', progressSteps, '正在准备…');
  await nextTick();
  let openSettlementAfterProgress = false;
  let pendingWorkInjuryFilingGuide = false;
  try {
    let insuranceCtx: Awaited<ReturnType<typeof resolveOutpatientChargeInsuranceContext>> = null;
    settlementInsuranceBalc.value = null;

    if (needChsInsurance && insuranceMedium) {
      insuranceCtx = await resolveOutpatientChargeInsuranceContext(rows, {
        medium: insuranceMedium,
        onPhase: (phase) => {
          if (phase === 'plugin' || phase === 'read-card') {
            setChargeProgressStep(0, outpatientChargeReadCredentialHint(insuranceMedium));
            return;
          }
          setChargeProgressStep(1, '正在核验参保人员信息（1101）…');
        },
      });
      if (!insuranceCtx?.psnNo) {
        ElMessage.warning(outpatientChargeReadCredentialFailHint(insuranceMedium));
        return;
      }
      settlementInsuranceBalc.value =
        insuranceCtx.balc === undefined || insuranceCtx.balc === null || insuranceCtx.balc === ''
          ? null
          : insuranceCtx.balc;
    }

    if (needChsInsurance && insuranceCtx) {
      setChargeProgressStep(2, '医保事前提醒分析中（3101），请稍候…');
      const resolved = await resolveInsuranceContractById(insuranceCtx.contractId);
      try {
        const { continued } = await runOpDetailAuditGate({
          params: {
            encounterId: eid,
            chargeItemIds: rows.map((r) => r.chargeItemId),
            contractId: insuranceCtx.contractId,
            contractBusNo: resolved?.contractBusNo,
            trigScen: '7',
            psnNo: insuranceCtx.psnNo,
            insuplcAdmdvs: insuranceCtx.insuplcAdmdvs,
            insutype: insuranceCtx.insutype,
            // 结算补慢病 2201 时用读卡原样凭证
            mdtrtCertType: insuranceCtx.ybMediumType,
            mdtrtCertNo: insuranceCtx.ybCardInfo,
          },
          dialogRef: detailAuditDialogRef,
        });
        if (!continued) {
          ElMessage.info('已按事前提醒返回修改，未继续预结算');
          return;
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : '事前提醒失败';
        ElMessage.error(message);
        return;
      }
    }

    const preSettleStepIndex = isWorkInjury ? 0 : needChsInsurance ? 3 : 0;
    const openDialogStepIndex = isWorkInjury ? 1 : needChsInsurance ? 4 : 1;
    setChargeProgressStep(
      preSettleStepIndex,
      isWorkInjury
        ? '工伤预结算通信中（9102→2206），请稍候…'
        : needChsInsurance
          ? '医保预结算通信中（2201→2206），请稍候…'
          : '正在计算应收并生成支付草稿…',
    );
    const workInjuryContractId = isWorkInjury ? resolveInsuranceContractId(rows) : undefined;
    if (isWorkInjury) {
      workInjuryFilingContractId.value = workInjuryContractId;
    }
    const res = await httpPreSettleOutpatientCharges({
      chargeItemIds: rows.map((r) => r.chargeItemId),
      encounterId: eid,
      ...(insuranceCtx
        ? {
            psnNo: insuranceCtx.psnNo,
            ybMediumType: insuranceCtx.ybMediumType,
            ybCardInfo: insuranceCtx.ybCardInfo,
            insuplcAdmdvs: insuranceCtx.insuplcAdmdvs,
            insutype: insuranceCtx.insutype,
            contractId: insuranceCtx.contractId,
          }
        : workInjuryContractId
          ? { contractId: workInjuryContractId }
          : {}),
    });
    if (res.settlementComposition === 'WORK_INJURY_INVOKE') {
      setChargeProgressStep(preSettleStepIndex, '工伤医保逐步通信中…');
    }
    preSettleSnapshot.value =
      res.settlementComposition === 'WORK_INJURY_INVOKE'
        ? await runWorkInjuryChargePreSettleInvoke(
            res,
            eid,
            rows.map((r) => r.chargeItemId),
            workInjuryContractId,
          )
        : res;
    setChargeProgressStep(openDialogStepIndex, '预结算完成，即将打开确认窗口…');
    lockedChargeItemIds.value = selectedChargeRows.value.map((r) => r.chargeItemId);
    unionPayMakeupCollectMode.value = false;
    const cashPay = Number(preSettleSnapshot.value?.displayAmount ?? 0);
    paymentForm.payChannelCode = resolveCollectPayChannelCode(
      cashPay,
      settlementPayChannelOptions.value,
    );
    // 先关进度弹窗再开结算窗，避免同 tick 双 Dialog 互相顶掉
    openSettlementAfterProgress = true;
  } catch (error: unknown) {
    // Win/本地 Error 必弹；HTTP 业务体拦截器已提示时 message 为空，勿用兜底覆盖
    const message = resolveOutpatientPreSettleErrorMessage(error, '预结算失败，请重试');
    const rawMessage = message || extractOutpatientPreSettleRawMessage(error);
    if (message) {
      ElMessage.error(message);
    }
    if (shouldGuideWorkInjuryFiling(rawMessage) && selectedEncounterId.value) {
      // contractId 可能稍后才解析到；先记下，关进度后再开备案窗（避免与进度 Dialog 互顶闪退）
      if (workInjuryFilingContractId.value == null) {
        workInjuryFilingContractId.value = resolveInsuranceContractId(selectedChargeRows.value);
      }
      pendingWorkInjuryFilingGuide = true;
    }
  } finally {
    closeChargeProgress();
    preSettleLoading.value = false;
  }
  if (pendingWorkInjuryFilingGuide) {
    await nextTick();
    // 等进度弹窗关闭动画结束，再开备案（与结算窗同样防互顶）
    await new Promise((resolve) => window.setTimeout(resolve, 200));
    if (workInjuryFilingContractId.value == null) {
      ElMessage.warning('无法打开一键备案：缺少工伤费用性质，请确认已选工伤收费项');
    } else {
      ElMessage.info('请完成门诊工伤备案（类型12）后再确认收费');
      workInjuryFilingDialogRef.value?.openDialog({ autoGuideApproval: true });
    }
  }
  if (openSettlementAfterProgress) {
    await nextTick();
    // 等进度弹窗关闭动画结束，避免两个 AppDialog 互顶导致结算窗不显示
    await new Promise((resolve) => window.setTimeout(resolve, 80));
    settlementVisible.value = true;
  }
};

/** 银联补收：复用已确认 2207，打开扫码弹窗补收现金段（如 ¥79.06） */
const handleOpenUnionPayMakeupCollect = async () => {
  const eid = selectedEncounterId.value;
  if (!eid) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  if (selectedChargeRows.value.length === 0) {
    ElMessage.warning('请勾选需要银联补收的费用项');
    return;
  }
  await refreshUnionPayCapabilities();
  if (!scanPayEnabled.value) {
    ElMessage.error('请启动本地客户端（OpenHIS.Win）并确认银联组件已安装');
    return;
  }
  makeupCollectLoading.value = true;
  openChargeProgress(
    '银联补收准备中',
    [{ key: 'prepare', label: '复用已确认医保结算' }],
    '正在准备…',
  );
  await nextTick();
  try {
    const chargeItemIds = selectedChargeRows.value
      .map((row) => String(row.chargeItemId ?? '').trim())
      .filter((id) => id !== '');
    const res = await httpPrepareUnionPayMakeupCollect({
      encounterId: eid,
      chargeItemIds,
    });
    preSettleSnapshot.value = res;
    lockedChargeItemIds.value = chargeItemIds;
    unionPayMakeupCollectMode.value = true;
    settlementInsuranceBalc.value = null;
    paymentForm.payChannelCode = 'SCAN_PAY';
    scanCode.value = '';
    scanStatusText.value = '';
    // 半成功：服务端已有 BPC SUCCESS 时预热本机凭证，弹窗锁定勿重复扫码
    let alreadyPaid = false;
    try {
      const orders = (await httpGetBpcOrdersByPayment(res.paymentDraftId)) ?? [];
      const successOrder = orders.find(
        (row) =>
          row.status === 'SUCCESS' &&
          (row.tranType === 'C' || !row.tranType) &&
          row.tradeNo &&
          row.merTradeNo,
      );
      if (successOrder) {
        cacheClientScanResult(res.paymentDraftId, {
          merTradeNo: String(successOrder.merTradeNo ?? ''),
          tradeNo: String(successOrder.tradeNo ?? ''),
          payType: String(successOrder.payType ?? ''),
          respCode: '00',
          respMsg: successOrder.respMsg || '银联已扣款，请补结算',
          txnAmtFen: successOrder.txnAmtFen,
        });
        alreadyPaid = true;
        scanStatusText.value = '银联已扣款，请直接确认补收（不会重复扣款）';
      }
    } catch {
      // 查单失败仍可打开弹窗；确认时再走 submitScanPayment
    }
    settlementVisible.value = true;
    ElMessage.success(
      alreadyPaid
        ? `银联已扣 ¥${Number(res.displayAmount ?? 0).toFixed(2)}，请直接确认补收`
        : `请扫码补收 ¥${Number(res.displayAmount ?? 0).toFixed(2)}（医保已确认，不再重新结算）`,
    );
  } catch (error) {
    if (!isStaleCrossRoleError(error) && !isUnmappedYbNoComplianceError(error)) {
      const message = error instanceof Error ? error.message : '银联补收准备失败';
      ElMessage.error(message);
    }
  } finally {
    closeChargeProgress();
    makeupCollectLoading.value = false;
  }
};

/** 正式结算：医保 2207（若需）→ 银联扫码 → 院内落账 */
const handleConfirmSettlement = async () => {
  const snap = preSettleSnapshot.value;
  const eid = selectedEncounterId.value;
  if (!snap || !eid) {
    return;
  }
  // 应收为 0（如医保全额覆盖）时无需银联扫码/付款码；有医保时扫码额以 2207 回写为准
  const cashPay = Number(snap.displayAmount ?? 0);
  let payableCash = Number.isFinite(cashPay) ? cashPay : 0;
  const needScanPay = payableCash > 0.001 && isScanPayChannel(paymentForm.payChannelCode);
  const halfSuccessLocked = hasCachedClientScanSuccess(snap.paymentDraftId);
  // 银联补收半成功：允许空付款码，由 submitScanPayment 查 BPC SUCCESS 复用凭证
  if (
    needScanPay &&
    !halfSuccessLocked &&
    !scanCode.value.trim() &&
    !unionPayMakeupCollectMode.value
  ) {
    ElMessage.warning('请输入付款码');
    return;
  }

  const hasChsInsuranceSettle = Boolean(
    snap.ybChrgBchno ||
      (snap.insuranceGroups?.some((g) => String(g.chrgBchno ?? '').trim()) ?? false),
  );
  const hasInsuranceSettle = Boolean(snap.workInjurySessionId || hasChsInsuranceSettle);
  /** 国家医保 + 扫码：先 2207 再 Win，避免串码超时仍扣银联 */
  const chsBeforeScan = hasChsInsuranceSettle && needScanPay && !snap.workInjurySessionId;

  const progressSteps: ChargeProgressStep[] = [];
  if (snap.workInjurySessionId) {
    progressSteps.push({ key: 'wi-2207', label: '工伤医保正式结算（2207）' });
  }
  if (chsBeforeScan) {
    progressSteps.push({ key: 'yb-2207', label: '医保正式结算（2207）' });
  }
  if (needScanPay) {
    progressSteps.push({ key: 'scan-pay', label: '银联扫码支付' });
  }
  if (hasInsuranceSettle && !snap.workInjurySessionId && !chsBeforeScan) {
    progressSteps.push({ key: 'yb-2207', label: '医保正式结算（2207）' });
  }
  progressSteps.push({ key: 'local-settle', label: '院内落账与出票' });

  confirmSettleLoading.value = true;
  scanStatusText.value = '';
  openChargeProgress('正在确认收费', progressSteps, '请稍候，勿重复点击…');
  await nextTick();
  let step = 0;
  let workInjury2207Done = false;
  let chs2207BeforeScanDone = false;
  try {
    if (snap.workInjurySessionId) {
      setChargeProgressStep(step, '工伤医保正式结算通信中…');
      await runWorkInjuryCharge2207Invoke(
        snap,
        eid,
        lockedChargeItemIds.value,
        resolveInsuranceContractId(selectedChargeRows.value),
      );
      workInjury2207Done = true;
      step += 1;
    }

    const insuranceGroupBchnos =
      snap.insuranceGroups
        ?.map((group) => String(group.chrgBchno ?? '').trim())
        .filter((value) => value.length > 0) ?? [];

    if (chsBeforeScan) {
      setChargeProgressStep(step, '医保正式结算通信中（2207）…');
      const expectedBatches =
        insuranceGroupBchnos.length > 0 ? insuranceGroupBchnos.length : snap.ybChrgBchno ? 1 : 0;
      const ybConfirm = await httpConfirmOutpatientInsuranceBeforeScan({
        paymentDraftId: String(snap.paymentDraftId),
        encounterId: eid,
        ...(insuranceGroupBchnos.length > 0
          ? { ybChrgBchnos: insuranceGroupBchnos }
          : snap.ybChrgBchno
            ? { ybChrgBchno: snap.ybChrgBchno }
            : {}),
      });
      if (expectedBatches > 0 && (ybConfirm?.confirmedBatchCount ?? 0) < expectedBatches) {
        ElMessage.error('医保结算批次不完整，请勿扫码，请重试确认收费');
        return;
      }
      if (ybConfirm?.patientCashAmount != null && ybConfirm.patientCashAmount !== '') {
        const ybCash = Number(ybConfirm.patientCashAmount);
        if (Number.isFinite(ybCash)) {
          payableCash = Math.max(0, ybCash);
        }
      }
      chs2207BeforeScanDone = true;
      step += 1;
    }

    let clientScanResult;
    if (needScanPay && payableCash > 0.001) {
      await refreshUnionPayCapabilities();
      if (!scanPayEnabled.value) {
        ElMessage.error('请启动本地客户端（OpenHIS.Win）并确认银联组件已安装');
        return;
      }
      setChargeProgressStep(step, halfSuccessLocked ? '使用已成功银联凭证…' : '等待银联支付结果…');
      scanStatusText.value = halfSuccessLocked ? '银联已扣款，正在补结算…' : '等待支付结果…';
      try {
        const scanOutcome = await submitScanPayment({
          paymentDraftId: snap.paymentDraftId,
          amount: payableCash,
          scanCode: scanCode.value,
        });
        clientScanResult = scanOutcome.clientScanResult;
        scanStatusText.value = `支付成功${scanOutcome.clientScanResult.tradeNo ? `，单号 ${scanOutcome.clientScanResult.tradeNo}` : ''}`;
        step += 1;
      } catch (scanError) {
        // 扫码前已 2207：Win 失败则冲正医保，避免平台已收、银联未收
        // 银联补收复用已确认结算：禁止冲正
        if (chs2207BeforeScanDone && !unionPayMakeupCollectMode.value) {
          try {
            await httpCompensateInsuranceSettlementsByPayment({
              paymentDraftId: String(snap.paymentDraftId),
            });
            ElMessage.warning('银联支付失败，已冲正医保结算；请重新确认收费');
          } catch {
            ElMessage.error('银联支付失败，且医保冲正未完成，请联系医保办，勿重复收费');
          }
        }
        throw scanError;
      }
    }

    // 零现金：占位 CASH 金额 0（禁止 SCAN_PAY）；有现金：按所选渠道提交应付额
    const paymentDetails =
      payableCash > 0.001
        ? [
            {
              amount: payableCash,
              payChannelCode: paymentForm.payChannelCode,
            },
          ]
        : [
            {
              amount: 0,
              payChannelCode: FALLBACK_DEFAULT_PAY_CHANNEL,
            },
          ];
    setChargeProgressStep(step, hasInsuranceSettle ? '医保确认与院内落账中…' : '院内落账中…');
    const settleBody = {
      billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
      chargeItemIds: lockedChargeItemIds.value,
      encounterId: eid,
      outcomeEnum: OUTPATIENT_CHARGE_SETTLE_OUTCOME_COMPLETED,
      paymentDetails,
      paymentDraftId: snap.paymentDraftId,
      ...(insuranceGroupBchnos.length > 0
        ? { ybChrgBchnos: insuranceGroupBchnos }
        : snap.ybChrgBchno
          ? { ybChrgBchno: snap.ybChrgBchno }
          : {}),
    };
    const payResult = await (clientScanResult
      ? settleWithScanRecovery(
          clientScanResult,
          () =>
            httpConfirmOutpatientChargeSettlement({
              ...settleBody,
              clientScanResult,
            }),
          {
            paymentDraftId: snap.paymentDraftId,
            refundAmountYuan: payableCash,
          },
        )
      : httpConfirmOutpatientChargeSettlement(settleBody));
    clearCachedClientScanResult(snap.paymentDraftId);
    ElMessage.success(unionPayMakeupCollectMode.value ? '银联补收成功' : '收费成功');
    settlementVisible.value = false;
    scanCode.value = '';
    scanStatusText.value = '';
    preSettleSnapshot.value = null;
    settlementInsuranceBalc.value = null;
    lockedChargeItemIds.value = [];
    unionPayMakeupCollectMode.value = false;
    void loadChargeQueue().then(() => loadPendingForSelectedEncounter());
    schedulePrintChargeReceipt({
      encounterId: eid,
      paymentId: payResult?.paymentId,
      jobName: '收费小票',
    });
  } catch (error) {
    if (error instanceof ScanSettleRefundedError) {
      ElMessage.warning(error.message);
      scanStatusText.value = '';
      return;
    }
    if (error instanceof ScanSettleIncompleteError) {
      ElMessage.error(error.message);
      scanStatusText.value = '银联已扣款，请补结算或银联退货，勿重复扫码';
      return;
    }
    if (workInjury2207Done) {
      try {
        await runWorkInjuryRefundReverse({
          encounterId: eid,
          originalPaymentId: String(snap.paymentDraftId),
        });
        ElMessage.warning('院内落账失败，已尝试冲正工伤结算；请勿重复收费');
      } catch {
        ElMessage.error('院内落账失败，且工伤冲正未完成，请联系医保办，勿重复收费');
      }
    }
    if (isOutpatientChargeStaleSettlementError(error)) {
      await resetSettlementAfterAdviceChanged();
      return;
    }
    if (!isStaleCrossRoleError(error) && !isUnmappedYbNoComplianceError(error)) {
      const message = error instanceof Error ? error.message : '门诊收费失败';
      ElMessage.error(message);
    }
  } finally {
    closeChargeProgress();
    confirmSettleLoading.value = false;
    if (!settlementVisible.value) {
      scanStatusText.value = '';
    }
  }
};

/** 补打收费小票：按主列表勾选的已结算支付单打印 */
async function handleReprintChargeReceipt() {
  const eid = selectedEncounterId.value;
  if (!eid) {
    return;
  }
  let paymentIds = selectedSettledPaymentIds.value
    .map((id) => String(id ?? '').trim())
    .filter(Boolean);
  // 未勾选已结算单时，若仅有一笔可补打支付单则直接打，避免无摘要时完全不可用
  if (paymentIds.length === 0) {
    const available = [
      ...new Set(
        displayedPendingItems.value
          .map((row) => String(row.paymentId ?? '').trim())
          .filter(Boolean),
      ),
    ];
    if (available.length === 1) {
      paymentIds = available;
    } else if (available.length === 0) {
      const fallback = String(currentQueueRow.value?.paymentId ?? '').trim();
      if (fallback) {
        paymentIds = [fallback];
      }
    }
  }
  if (paymentIds.length === 0) {
    ElMessage.warning('请勾选要补打的已结算收费单');
    return;
  }
  reprintLoading.value = true;
  try {
    // 多笔支付单按勾选顺序逐张补打
    for (const paymentId of paymentIds) {
      selectedSettlementPaymentId.value = paymentId;
      await printChargeReceipt({
        encounterId: eid,
        paymentId,
        jobName: '收费小票（补打）',
      });
    }
  } finally {
    reprintLoading.value = false;
  }
}

onMounted(() => {
  void (async () => {
    try {
      const init = await httpLoadChargePageInit({});
      payChannelOptions.value = optionDtosToPayChannelOptions(init.paymentChannelOptions);
    } catch {
      payChannelOptions.value = [...OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS];
    }
    await refreshUnionPayCapabilities();
  })();
  void loadChargeQueue();
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-col overflow-hidden">
    <StationTriPaneLayout
      :show-right="false"
      :show-footer="false"
      class="charge-outpatient-workbench min-h-0 flex-1"
      left-track="minmax(280px, 0.36fr)"
      center-track="minmax(0, 1fr)"
    >
      <template #left>
        <div class="flex h-full min-h-0 flex-col gap-2 overflow-hidden p-2">
          <div class="shrink-0 px-0.5 pt-0.5">
            <SectionTitle title="待收费患者" />
            <div class="charge-filter-toolbar mt-2">
              <div class="charge-filter-toolbar__main-col flex flex-col gap-2">
                <SelectFuzzyInput v-model="keyword" placeholder="姓名 / 业务号" full-width />
                <ElRadioGroup v-model="queueTabCode" class="charge-filter-toolbar__status">
                  <ElRadio :value="QUEUE_TAB.ALL">全部</ElRadio>
                  <ElRadio :value="QUEUE_TAB.PENDING">待收费</ElRadio>
                  <ElRadio :value="QUEUE_TAB.SETTLED">已结算</ElRadio>
                </ElRadioGroup>
              </div>
              <div class="charge-filter-toolbar__date-row flex min-w-0 items-center gap-1.5">
                <ElDatePicker
                  v-model="dateRange"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  unlink-panels
                  clearable
                  class="charge-filter-toolbar__date min-w-0 flex-1"
                  :shortcuts="dateRangeShortcuts"
                />
                <ElButton
                  type="default"
                  class="!shrink-0 !px-2"
                  title="刷新患者列表"
                  :loading="queueLoading"
                  @click="handlePatientQueueRefresh"
                >
                  <RotateCw class="h-4 w-4" />
                </ElButton>
              </div>
            </div>
          </div>
          <div class="charge-patient-scroll min-h-0 flex-1 space-y-2 overflow-y-auto px-0.5 pb-1">
            <template v-if="queueRecords.length === 0">
              <div class="py-8 text-center text-sm text-[var(--el-text-color-placeholder)]">
                暂无就诊记录
              </div>
            </template>
            <BillingPatientCard
              v-for="row in queueRecords"
              :key="row.encounterId"
              :patient="mapToBillingPatientCard(row)"
              :selected="row.encounterId === selectedEncounterId"
              @select="handleChoosePatient"
            />
          </div>
          <div class="shrink-0 pt-1">
            <ElPagination
              v-model:current-page="queuePager.pageNo"
              v-model:page-size="queuePager.pageSize"
              :disabled="queueLoading"
              :page-sizes="[10, 20, 50]"
              :total="queueTotal"
              background
              layout="total, sizes, prev, pager, next"
              small
              @current-change="handleQueuePageChange"
              @size-change="handleQueueSizeChange"
            />
          </div>
        </div>
      </template>

      <template #center>
        <ElCard
          shadow="never"
          class="flex h-full min-h-0 flex-col overflow-hidden rounded-lg"
          body-class="flex-1 min-h-0 overflow-hidden !p-2"
        >
          <SectionTitle class="mb-2" title="患者信息" />
          <EinvoiceBillAmountMismatchBanner
            :line-amounts="billAmountMismatchContext.lineAmounts"
            :payment-amount="billAmountMismatchContext.paymentAmount"
            :payment-id="billAmountMismatchContext.paymentId"
            @focus-adjust="handleBillBannerFocusAdjust"
          />
          <div class="flex h-full min-h-0 flex-col gap-3 overflow-auto">
            <div class="patient-info-bar">
              <div class="patient-info-grid">
                <div class="patient-info-item">
                  <span class="patient-info-label">患者姓名</span>
                  <span class="patient-info-value">{{ currentQueueRow?.patientName || '—' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">性别</span>
                  <span class="patient-info-value">{{
                    chargeQueueGenderDisplay(currentQueueRow)
                  }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">年龄</span>
                  <span class="patient-info-value">{{ currentQueueRow?.ageText || '—' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">就诊业务号</span>
                  <span class="patient-info-value">{{
                    currentQueueRow?.encounterBusNo || '—'
                  }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">接诊医生</span>
                  <span class="patient-info-value">{{ currentQueueRow?.doctorName || '—' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">就诊开始</span>
                  <span class="patient-info-value">{{ currentQueueRow?.startTime || '—' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">科室</span>
                  <span class="patient-info-value">
                    {{ currentQueueRow?.organizationName || '—' }}
                  </span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">费用摘要</span>
                  <span class="patient-info-value">
                    <template v-if="chargePatientFeeSummary">
                      待收 ¥{{ formatMoney(chargePatientFeeSummary.pendingCollectionAmount) }} ·
                      已收 ¥{{ formatMoney(chargePatientFeeSummary.collectedAmount) }}
                    </template>
                    <template v-else-if="tableLoading && selectedEncounterId">加载中…</template>
                    <template v-else>—</template>
                  </span>
                </div>
              </div>
            </div>
            <div class="flex-1">
              <div class="mb-2 flex flex-wrap items-center gap-2">
                <SectionTitle title="收费项目列表" />
                <div class="flex-1"></div>
                <ElButton
                  type="warning"
                  plain
                  class="mr-1"
                  :disabled="!selectedEncounterId || selectedChargeRows.length === 0"
                  :loading="makeupCollectLoading"
                  @click="handleOpenUnionPayMakeupCollect"
                >
                  银联补收
                </ElButton>
                <ElBadge
                  :hidden="paymentRecordsCount < 1"
                  :max="99"
                  :value="paymentRecordsCount"
                  class="mr-1"
                >
                  <ElButton :disabled="!selectedEncounterId" @click="handleOpenPaymentRecords">
                    支付记录
                  </ElButton>
                </ElBadge>
                <ElButton
                  :disabled="
                    !selectedEncounterId ||
                    !accountSwitchEnabled ||
                    selectedChargeRows.length === 0 ||
                    switchAccountLoading
                  "
                  :loading="switchAccountLoading"
                  class="mr-1"
                  @click="handleSwitchSelfPay"
                >
                  转自费
                </ElButton>
                <ElButton
                  type="primary"
                  class="mr-1"
                  :disabled="!selectedEncounterId || selectedChargeRows.length === 0"
                  :loading="preSettleLoading"
                  @click="handleOpenSettlement"
                >
                  确认收费
                </ElButton>
                <ElButton
                  class="mr-1"
                  :disabled="!canReprintChargeReceipt"
                  :loading="reprintLoading"
                  @click="handleReprintChargeReceipt"
                >
                  补打小票
                </ElButton>
              </div>
              <div class="charge-detail-wrapper">
                <RegisterStyleTableLayout
                  ref="chargeTableLayoutRef"
                  :key="selectedEncounterId || 'no-encounter'"
                  class="!p-0"
                  :table-data="displayedPendingItems"
                  :loading="tableLoading"
                  :total="displayedPendingItems.length"
                  :query-params="chargeTableQueryParams"
                  :table-columns="chargeTableColumns"
                  :show-pagination="false"
                  :show-top-query="false"
                  :max-height="600"
                  row-key="chargeItemId"
                  :span-method="handleChargeDetailSpanMethod"
                  @selection-change="handleChargeSelectionChange"
                >
                  <template #statusCell="{ row }">
                    <template
                      v-for="st in [chargeItemStatusTag(row)]"
                      :key="`${row.chargeItemId}-${st.label}`"
                    >
                      <ElTag :type="st.tagType" size="small" effect="plain">{{ st.label }}</ElTag>
                    </template>
                  </template>
                  <template #totalPrice="{ row }">¥{{ formatMoney(row.totalPrice) }}</template>
                </RegisterStyleTableLayout>
                <div class="ml-4 mt-2 text-sm text-[var(--el-text-color-regular)]">
                  已选待收 {{ selectedChargeRows.length }} 项，合计 ¥{{
                    formatMoney(selectedChargeSum)
                  }}
                  <template v-if="selectedSettledPaymentIds.length > 0">
                    ；已选补打 {{ selectedSettledPaymentIds.length }} 笔
                  </template>
                </div>
              </div>
            </div>
          </div>
        </ElCard>
      </template>
    </StationTriPaneLayout>

    <ChargePaymentRecordsDialog
      v-model="paymentRecordsVisible"
      v-model:payment-id="selectedSettlementPaymentId"
      :charge-items="pendingItems"
      :payments="settlementInsuranceOptions"
    />

    <ChargeSettlementDialog
      v-model="settlementVisible"
      v-model:pay-channel-code="paymentForm.payChannelCode"
      v-model:scan-code="scanCode"
      :dialog-title="unionPayMakeupCollectMode ? '银联补收' : '确认收费'"
      :primary-button-text="unionPayMakeupCollectMode ? '确认补收' : '确认收费'"
      :loading="confirmSettleLoading"
      :display-amount="preSettleSnapshot?.displayAmount ?? 0"
      :insurance-account-balance="settlementInsuranceBalc"
      :insurance-cvlserv-flag="settlementCvlservFlag"
      :insurance-breakdown-items="settlementInsuranceBreakdown"
      :insurance-groups="settlementInsuranceGroups"
      :previews="settlementPreviews"
      :selected-charge-count="lockedChargeItemIds.length"
      :summary-insurance-amount="settlementSummaryInsurance"
      :summary-self-pay-amount="settlementSummarySelfPay"
      :pay-channel-options="settlementPayChannelOptions"
      :scan-pay-enabled="settlementScanPayEnabled"
      :scan-input-locked="settlementScanInputLocked"
      :scan-status-text="scanStatusText"
      @confirm-charge="handleConfirmSettlement"
    />

    <ChargeProgressDialog
      v-model="chargeProgressVisible"
      :title="chargeProgressTitle"
      :steps="chargeProgressSteps"
      :active-index="chargeProgressActiveIndex"
      :detail="chargeProgressDetail"
    />

    <DetailAuditRemindDialog ref="detailAuditDialogRef" warn-type="1" />

    <WorkInjuryFilingDetailDialog
      ref="workInjuryFilingDialogRef"
      :encounter-id="selectedEncounterId"
      :contract-id="workInjuryFilingContractId"
      @completed="ElMessage.success('工伤备案已完成，请重新确认收费')"
    />
  </div>
</template>
