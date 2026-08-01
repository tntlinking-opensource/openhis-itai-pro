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
  httpConfirmOutpatientChargeSettlement,
  httpLoadChargePageInit,
  httpLoadPendingChargeItems,
  httpPageOutpatientChargeQueue,
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
import { sumMoneyThenSettle } from '#/utils/format/money-scale';
import {
  FALLBACK_DEFAULT_PAY_CHANNEL,
  optionDtosToPayChannelOptions,
} from '#/utils/payment/payment-channel-options';
import { settleWithScanRecovery } from '#/utils/payment/scan-settle-recovery';
import { submitScanPayment } from '#/utils/payment/submit-scan-payment';
import { printChargeReceipt, schedulePrintChargeReceipt } from '#/utils/print/print-charge-receipt';

import {
  formatPatientFeeMoney,
  summarizeOutpatientChargeFeeAmounts,
} from '../services/patient-fee-summary.service';
import '../charge-workbench-panel.css';
import {
  CHARGE_ITEM_STATUS_LABEL_TAG,
  DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE,
  OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS,
  OUTPATIENT_CHARGE_SETTLE_OUTCOME_COMPLETED,
  resolveOutpatientChargeSwitchAccountMessage,
} from './outpatient-charge.constants';
import {
  chooseOutpatientChargeInsuranceMedium,
  outpatientChargeReadCredentialFailHint,
  outpatientChargeReadCredentialHint,
  outpatientChargeReadCredentialLabel,
  resolveOutpatientChargeInsuranceContext,
  selectionIncludesInsuranceItems,
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

usePrintClientWarmup();

const detailAuditDialogRef = ref<DetailAuditRemindDialogExpose | null>(null);

const QUEUE_TAB = {
  ALL: 'ALL',
  PENDING: 'PENDING',
  SETTLED: 'SETTLED',
} as const;

const queueTabCode = ref<string>(QUEUE_TAB.PENDING);
const keyword = ref('');
const dateRange = ref<[string, string] | null>(null);
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
const pendingItems = ref<PendingChargeItemRowDto[]>([]);
/** 本就诊全部成功支付摘要（账单时间倒序） */
const settlementInsuranceOptions = ref<ChargeSettlementInsuranceSummaryDto[]>([]);
/** 当前选中的支付流水主键（支付记录弹窗 / 补打回落） */
const selectedSettlementPaymentId = ref('');
/** 支付记录弹窗 */
const paymentRecordsVisible = ref(false);
const selectedChargeRows = ref<PendingChargeItemRowDto[]>([]);

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
  return [
    { key: 'poolingPay', label: '统筹基金支付', value: Number(snap.ybFundPaySumamt ?? 0) },
    { key: 'accountPay', label: '个人账户支付', value: Number(snap.ybAcctPay ?? 0) },
    { key: 'cashPay', label: '现金补缴', value: Number(snap.ybPsnCashPay ?? 0) },
    { key: 'finalReceivable', label: '最终应收', value: Number(snap.displayAmount ?? 0) },
  ];
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
  payChannelCode: DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE,
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
 * vxe 列 `checkMethod` 签名为 `({ row })`，TableColumn 的 `selectable` 类型为 `(row, index)`，二者兼容处理。
 */
function chargeItemSelectionSelectable(arg0: Record<string, unknown>, _index: number) {
  const row = (
    arg0 && typeof arg0 === 'object' && 'row' in arg0
      ? (arg0 as { row: PendingChargeItemRowDto }).row
      : arg0
  ) as PendingChargeItemRowDto;
  return isPendingChargeItemSelectable(row);
}

/**
 * 选中患者并加载待收明细后：仅勾选可收费行。
 * Table.vue 在 tableData 变更后会 post-flush → clearSelection（再 await nextTick），
 * 必须等清空完成后再勾，否则会被冲掉。
 */
async function selectAllPendingRowsInGrid(items: PendingChargeItemRowDto[]) {
  const selectable = items.filter((r) => isPendingChargeItemSelectable(r));
  syncingChargeSelection.value = true;
  selectedChargeRows.value = selectable.length > 0 ? [...selectable] : [];

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

  if (grid && selectable.length > 0) {
    // 优先全选（checkMethod 会跳过已结算）；避免 setCheckboxRow 行引用对不上
    if (typeof grid.setAllCheckboxRow === 'function') {
      grid.setAllCheckboxRow(true);
    } else if (typeof grid.setCheckboxRow === 'function') {
      const selectedIds = new Set(selectable.map((row) => row.chargeItemId));
      const tableDataResult =
        typeof grid.getTableData === 'function'
          ? (grid.getTableData() as {
              fullData?: PendingChargeItemRowDto[];
              tableData?: PendingChargeItemRowDto[];
            })
          : null;
      const tableRows =
        tableDataResult?.fullData ?? tableDataResult?.tableData ?? pendingItems.value;
      for (const row of tableRows) {
        if (!isPendingChargeItemSelectable(row)) {
          continue;
        }
        grid.setCheckboxRow(row, selectedIds.has(row.chargeItemId));
      }
    }
  } else if (grid && typeof grid.clearCheckboxRow === 'function') {
    grid.clearCheckboxRow();
  }

  await nextTick();
  selectedChargeRows.value = selectable.length > 0 ? [...selectable] : [];
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

/**
 * 收费项 → 收费时间：取成功支付摘要 billDate（与小票收费时间同源）。
 * 同一明细若出现在多笔支付中，保留 options 中先遇到的一笔（摘要按账单时间倒序）。
 */
const chargeTimeByItemId = computed(() => {
  const map = new Map<string, string>();
  for (const payment of settlementInsuranceOptions.value) {
    const billDate = String(payment.billDate ?? '').trim();
    if (!billDate) {
      continue;
    }
    // 优先 chargeItemIds；缺省时从 chargeLines 回落
    const ids = payment.chargeItemIds ?? [];
    if (ids.length > 0) {
      for (const rawId of ids) {
        const id = String(rawId ?? '').trim();
        if (id && !map.has(id)) {
          map.set(id, billDate);
        }
      }
      continue;
    }
    for (const line of payment.chargeLines ?? []) {
      const id = String(line.chargeItemId ?? '').trim();
      if (id && !map.has(id)) {
        map.set(id, billDate);
      }
    }
  }
  return map;
});

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

/** 当前选中队列行 */
const currentQueueRow = computed(
  () => queueRecords.value.find((r) => r.encounterId === selectedEncounterId.value) ?? null,
);

/** 当前就诊是否可补打收费小票（已有成功支付流水） */
const canReprintChargeReceipt = computed(() =>
  Boolean(String(currentQueueRow.value?.paymentId ?? '').trim()),
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

/** 主区收费明细始终全量展示 */
const displayedPendingItems = computed(() => pendingItems.value);

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
      body.startDate = `${range[0]} 00:00:00`;
      body.endDate = `${range[1]} 23:59:59`;
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
    settlementInsuranceOptions.value = [];
    selectedSettlementPaymentId.value = '';
    paymentRecordsVisible.value = false;
    selectedChargeRows.value = [];
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

/** 表格勾选变更：缓存已选收费项（全选等场景下过滤掉不可勾选的已结算行） */
const handleChargeSelectionChange = (selection: PendingChargeItemRowDto[]) => {
  if (syncingChargeSelection.value) {
    return;
  }
  selectedChargeRows.value = selection.filter((r) => isPendingChargeItemSelectable(r));
};

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
    prop: 'statusCode',
    label: '收费状态',
    minWidth: 100,
    align: 'center',
    headerAlign: 'center',
    slot: 'statusCell',
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

/** 就诊维度转自费：将待收明细 accountId 批量切至自费账户（IF-OPC-05） */
const handleSwitchSelfPay = async () => {
  const eid = selectedEncounterId.value;
  if (!eid) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  await ElMessageBox.confirm('将当前就诊下待收费用项批量切换到自费记账账户，是否继续？', '转自费', {
    cancelButtonText: '取消',
    confirmButtonText: '确定',
    type: 'warning',
  });
  switchAccountLoading.value = true;
  try {
    const res = await httpSwitchEncounterChargeItemsToSelfPay({ encounterId: eid });
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
  const needInsurance = selectionIncludesInsuranceItems(rows);
  let insuranceMedium: Awaited<ReturnType<typeof chooseOutpatientChargeInsuranceMedium>> = null;
  if (needInsurance) {
    insuranceMedium = await chooseOutpatientChargeInsuranceMedium();
    if (!insuranceMedium) {
      return;
    }
  }
  const readCredentialLabel = insuranceMedium
    ? outpatientChargeReadCredentialLabel(insuranceMedium)
    : '读取医保凭证';
  const progressSteps: ChargeProgressStep[] = needInsurance
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
  try {
    let insuranceCtx: Awaited<ReturnType<typeof resolveOutpatientChargeInsuranceContext>> = null;
    settlementInsuranceBalc.value = null;
    if (needInsurance && insuranceMedium) {
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

    if (needInsurance && insuranceCtx) {
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

    const preSettleStepIndex = needInsurance ? 3 : 0;
    const openDialogStepIndex = needInsurance ? 4 : 1;
    setChargeProgressStep(
      preSettleStepIndex,
      needInsurance ? '医保预结算通信中（2201→2206），请稍候…' : '正在计算应收并生成支付草稿…',
    );
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
          )
        : res;
    setChargeProgressStep(openDialogStepIndex, '预结算完成，即将打开确认窗口…');
    lockedChargeItemIds.value = selectedChargeRows.value.map((r) => r.chargeItemId);
    const cashPay = Number(preSettleSnapshot.value?.displayAmount ?? 0);
    paymentForm.payChannelCode =
      Number.isFinite(cashPay) && cashPay > 0.001
        ? DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE
        : FALLBACK_DEFAULT_PAY_CHANNEL;
    settlementVisible.value = true;
  } finally {
    closeChargeProgress();
    preSettleLoading.value = false;
  }
};

/** 正式结算：按草稿与渠道实付落账 */
const handleConfirmSettlement = async () => {
  const snap = preSettleSnapshot.value;
  const eid = selectedEncounterId.value;
  if (!snap || !eid) {
    return;
  }
  // 应收为 0（如医保全额覆盖）时无需银联扫码/付款码
  const cashPay = Number(snap.displayAmount ?? 0);
  const payableCash = Number.isFinite(cashPay) ? cashPay : 0;
  const needScanPay = payableCash > 0.001 && isScanPayChannel(paymentForm.payChannelCode);
  if (needScanPay && !scanCode.value.trim()) {
    ElMessage.warning('请输入付款码');
    return;
  }

  const hasInsuranceSettle = Boolean(
    snap.workInjurySessionId ||
      snap.ybChrgBchno ||
      (snap.insuranceGroups?.some((g) => String(g.chrgBchno ?? '').trim()) ?? false),
  );
  const progressSteps: ChargeProgressStep[] = [];
  if (snap.workInjurySessionId) {
    progressSteps.push({ key: 'wi-2207', label: '工伤医保正式结算（2207）' });
  }
  if (needScanPay) {
    progressSteps.push({ key: 'scan-pay', label: '银联扫码支付' });
  }
  if (hasInsuranceSettle && !snap.workInjurySessionId) {
    progressSteps.push({ key: 'yb-2207', label: '医保正式结算（2207）' });
  }
  progressSteps.push({ key: 'local-settle', label: '院内落账与出票' });

  confirmSettleLoading.value = true;
  scanStatusText.value = '';
  openChargeProgress('正在确认收费', progressSteps, '请稍候，勿重复点击…');
  await nextTick();
  let step = 0;
  let workInjury2207Done = false;
  try {
    if (snap.workInjurySessionId) {
      setChargeProgressStep(step, '工伤医保正式结算通信中…');
      await runWorkInjuryCharge2207Invoke(snap, eid, lockedChargeItemIds.value);
      workInjury2207Done = true;
      step += 1;
    }
    let clientScanResult;
    if (needScanPay) {
      await refreshUnionPayCapabilities();
      if (!scanPayEnabled.value) {
        ElMessage.error('请启动本地客户端（OpenHIS.Win）并确认银联组件已安装');
        return;
      }
      setChargeProgressStep(step, '等待银联支付结果…');
      scanStatusText.value = '等待支付结果…';
      const scanOutcome = await submitScanPayment({
        paymentDraftId: snap.paymentDraftId,
        amount: payableCash,
        scanCode: scanCode.value,
      });
      clientScanResult = scanOutcome.clientScanResult;
      scanStatusText.value = `支付成功${scanOutcome.clientScanResult.tradeNo ? `，单号 ${scanOutcome.clientScanResult.tradeNo}` : ''}`;
      step += 1;
    }

    const insuranceGroupBchnos =
      snap.insuranceGroups
        ?.map((group) => String(group.chrgBchno ?? '').trim())
        .filter((value) => value.length > 0) ?? [];
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
    setChargeProgressStep(step, hasInsuranceSettle ? '医保正式结算与院内落账中…' : '院内落账中…');
    const payResult = await (clientScanResult
      ? settleWithScanRecovery(clientScanResult, () =>
          httpConfirmOutpatientChargeSettlement({
            billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
            chargeItemIds: lockedChargeItemIds.value,
            encounterId: eid,
            outcomeEnum: OUTPATIENT_CHARGE_SETTLE_OUTCOME_COMPLETED,
            paymentDetails,
            paymentDraftId: snap.paymentDraftId,
            clientScanResult,
            ...(insuranceGroupBchnos.length > 0
              ? { ybChrgBchnos: insuranceGroupBchnos }
              : snap.ybChrgBchno
                ? { ybChrgBchno: snap.ybChrgBchno }
                : {}),
          }),
        )
      : httpConfirmOutpatientChargeSettlement({
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
        }));
    ElMessage.success('收费成功');
    settlementVisible.value = false;
    scanCode.value = '';
    scanStatusText.value = '';
    preSettleSnapshot.value = null;
    settlementInsuranceBalc.value = null;
    lockedChargeItemIds.value = [];
    void loadChargeQueue().then(() => loadPendingForSelectedEncounter());
    schedulePrintChargeReceipt({
      encounterId: eid,
      paymentId: payResult?.paymentId,
      jobName: '收费小票',
    });
  } catch (error) {
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

/** 补打收费小票（按队列行最近一次成功支付流水） */
async function handleReprintChargeReceipt() {
  const row = currentQueueRow.value;
  const eid = selectedEncounterId.value;
  if (!row || !eid) {
    return;
  }
  const paymentId =
    String(selectedSettlementPaymentId.value ?? '').trim() || String(row.paymentId ?? '').trim();
  if (!paymentId) {
    ElMessage.warning('暂无支付流水，无法补打收费小票');
    return;
  }
  reprintLoading.value = true;
  try {
    await printChargeReceipt({
      encounterId: eid,
      paymentId,
      jobName: '收费小票（补打）',
    });
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
                <!-- 临时隐藏：门诊转自费 -->
                <ElButton
                  v-if="false"
                  :disabled="!selectedEncounterId || switchAccountLoading"
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
                  已选 {{ selectedChargeRows.length }} 项，合计 ¥{{
                    formatMoney(selectedChargeSum)
                  }}
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
      :loading="confirmSettleLoading"
      :display-amount="preSettleSnapshot?.displayAmount ?? 0"
      :insurance-account-balance="settlementInsuranceBalc"
      :insurance-breakdown-items="settlementInsuranceBreakdown"
      :insurance-groups="settlementInsuranceGroups"
      :previews="settlementPreviews"
      :selected-charge-count="lockedChargeItemIds.length"
      :summary-insurance-amount="settlementSummaryInsurance"
      :summary-self-pay-amount="settlementSummarySelfPay"
      :pay-channel-options="settlementPayChannelOptions"
      :scan-pay-enabled="settlementScanPayEnabled"
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
  </div>
</template>
