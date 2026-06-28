<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  ChargeQueueRowDto,
  PendingChargeItemRowDto,
  PreSettleChargeLinePreviewDto,
  PreSettleOutpatientChargesResponse,
} from '#/api/outpatient-charge/outpatient-charge.types';
import type { BillingPatientCardPatient } from '#/components/charge/billing-patient-card.types';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { RotateCw } from '@vben/icons';

import { TableLayout } from '@vben-core/table-layout-ui';

import { useDebounceFn } from '@vueuse/core';
import dayjs from 'dayjs';
import {
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
  httpSwitchEncounterChargeItemsToInsurance,
  httpSwitchEncounterChargeItemsToSelfPay,
} from '#/api/outpatient-charge/outpatient-charge.http';
import BillingPatientCard from '#/components/charge/billing-patient-card.vue';
import EinvoiceBillAmountMismatchBanner from '#/components/einvoice/EinvoiceBillAmountMismatchBanner.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { usePrintClientWarmup } from '#/composables/use-print-client-warmup';
import { useUnionPayScanPay } from '#/composables/payment/useUnionPayScanPay';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { decimalAdd } from '#/utils/format/decimal-util';
import { optionDtosToPayChannelOptions } from '#/utils/payment/payment-channel-options';
import { settleWithScanRecovery } from '#/utils/payment/scan-settle-recovery';
import { submitScanPayment } from '#/utils/payment/submit-scan-payment';
import { printChargeReceipt, schedulePrintChargeReceipt } from '#/utils/print/print-charge-receipt';

import {
  formatPatientFeeMoney,
  summarizeOutpatientChargeFeeAmounts,
} from '../services/patient-fee-summary.service';
import {
  CHARGE_ITEM_STATUS_LABEL_TAG,
  DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE,
  OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS,
  OUTPATIENT_CHARGE_SETTLE_OUTCOME_COMPLETED,
} from './outpatient-charge.constants';
import { isStaleCrossRoleError } from '#/utils/http/stale-cross-role-error';
import ChargeSettlementDialog from './settlement-dialog.vue';

defineOptions({
  name: 'ChargeOpCharge',
});

usePrintClientWarmup();

const QUEUE_TAB = {
  ALL: 'ALL',
  PENDING: 'PENDING',
  SETTLED: 'SETTLED',
} as const;

const queueTabCode = ref<string>(QUEUE_TAB.PENDING);
const keyword = ref('');
const dateRange = ref<[string, string] | null>(null);
const queuePager = reactive({
  pageNo: 1,
  pageSize: 20,
});
const queueRecords = ref<ChargeQueueRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);

const selectedEncounterId = ref('');
const pendingItems = ref<PendingChargeItemRowDto[]>([]);
const selectedChargeRows = ref<PendingChargeItemRowDto[]>([]);

const preSettleSnapshot = ref<null | PreSettleOutpatientChargesResponse>(null);
/** 预结算成功时锁定勾选 id，避免弹窗打开后表格勾选变化导致与草稿不一致 */
const lockedChargeItemIds = ref<string[]>([]);

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
const confirmSettleLoading = ref(false);
/** 补打收费小票 loading */
const reprintLoading = ref(false);

const paymentForm = reactive({
  payChannelCode: DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE,
});

const payChannelOptions = ref([...OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS]);
const scanCode = ref('');
const scanStatusText = ref('');

const { scanPayEnabled, effectivePayChannelOptions, refreshUnionPayCapabilities } =
  useUnionPayScanPay(() => payChannelOptions.value);

const chargeTableQueryParams = reactive({
  pageNo: 1,
  pageSize: 20,
});

const chargeTableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);

/** 待收表格底层 VxeGrid（TableLayout → Table 暴露的 gridApi，可能多层 ref） */
function getChargeVxeGrid(): null | Record<string, unknown> {
  let cur: unknown = (chargeTableLayoutRef.value as null | { gridApi?: unknown })?.gridApi;
  for (let i = 0; i < 4 && cur && typeof cur === 'object' && 'value' in cur; i++) {
    cur = (cur as { value: unknown }).value;
  }
  if (!cur || typeof cur !== 'object') {
    return null;
  }
  const g = cur as { grid?: Record<string, unknown> };
  return (g.grid ?? cur) as Record<string, unknown>;
}

/** 是否已结算行（与枚举 `SETTLED`、展示文案「已结算」对齐，避免接口 code 不一致时仍可勾选） */
function isOutpatientChargeItemSettled(row: PendingChargeItemRowDto) {
  const code = (row.statusCode ?? '').toString().trim().toUpperCase();
  if (code === 'SETTLED') return true;
  const mapped = code ? CHARGE_ITEM_STATUS_LABEL_TAG[code] : undefined;
  if (mapped?.label === '已结算') return true;
  return (row.statusText ?? '').toString().includes('已结算');
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

/** 选中患者并加载待收明细后：仅勾选可收费行（已结算禁用勾选） */
function selectAllPendingRowsInGrid(items: PendingChargeItemRowDto[]) {
  const selectable = items.filter((r) => isPendingChargeItemSelectable(r));
  selectedChargeRows.value = selectable.length > 0 ? [...selectable] : [];
  void nextTick(() => {
    const grid = getChargeVxeGrid();
    if (!grid) {
      return;
    }
    if (typeof grid.clearCheckboxRow === 'function') {
      grid.clearCheckboxRow();
    }
    if (items.length === 0) {
      return;
    }
    if (typeof grid.setCheckboxRow !== 'function') {
      return;
    }
    for (const row of items) {
      grid.setCheckboxRow(row, isPendingChargeItemSelectable(row));
    }
  });
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
const selectedChargeSum = computed(() => {
  let sum = 0;
  for (const row of selectedChargeRows.value) {
    sum = decimalAdd(sum, row.totalPrice ?? 0, 4);
  }
  return sum;
});

/** 患者信息栏费用摘要：与待收明细同源，避免队列行空金额 */
const chargePatientFeeSummary = computed(() => {
  if (!selectedEncounterId.value) {
    return null;
  }
  return summarizeOutpatientChargeFeeAmounts(pendingItems.value, isOutpatientChargeItemSettled);
});

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
    selectAllPendingRowsInGrid(items);
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
    selectedChargeRows.value = [];
    void nextTick(() => {
      const grid = getChargeVxeGrid();
      if (grid && typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    });
    return;
  }
  await runLoadPending({ encounterId: eid, includePriceExtension: true });
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
    prop: 'chargeItemActions',
    label: '操作',
    width: 88,
    align: 'center',
    headerAlign: 'center',
    slot: 'chargeItemActionsCell',
  },
];

/** 就诊维度转医保 */
const handleSwitchInsurance = async () => {
  const eid = selectedEncounterId.value;
  if (!eid) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  await ElMessageBox.confirm('将当前就诊下费用项批量切换到医保记账账户，是否继续？', '转医保', {
    cancelButtonText: '取消',
    confirmButtonText: '确定',
    type: 'warning',
  });
  const res = await httpSwitchEncounterChargeItemsToInsurance({ encounterId: eid });
  if (res.success) {
    ElMessage.success('已提交转医保');
    await loadPendingForSelectedEncounter();
  }
};

/** 就诊维度转自费 */
const handleSwitchSelfPay = async () => {
  const eid = selectedEncounterId.value;
  if (!eid) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  await ElMessageBox.confirm('将当前就诊下费用项批量切换到自费记账账户，是否继续？', '转自费', {
    cancelButtonText: '取消',
    confirmButtonText: '确定',
    type: 'warning',
  });
  const res = await httpSwitchEncounterChargeItemsToSelfPay({ encounterId: eid });
  if (res.success) {
    ElMessage.success('已提交转自费');
    await loadPendingForSelectedEncounter();
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
  preSettleLoading.value = true;
  try {
    const rows = selectedChargeRows.value;
    // TODO: 医保读卡插件就绪后恢复 resolveOutpatientChargeInsuranceContext(rows)
    const res = await httpPreSettleOutpatientCharges({
      chargeItemIds: rows.map((r) => r.chargeItemId),
      encounterId: eid,
    });
    preSettleSnapshot.value = res;
    lockedChargeItemIds.value = selectedChargeRows.value.map((r) => r.chargeItemId);
    paymentForm.payChannelCode = DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE;
    settlementVisible.value = true;
  } finally {
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
  const isScanPay = paymentForm.payChannelCode === 'SCAN_PAY';
  if (isScanPay && !scanCode.value.trim()) {
    ElMessage.warning('请输入付款码');
    return;
  }
  confirmSettleLoading.value = true;
  scanStatusText.value = '';
  try {
    let clientScanResult;
    if (isScanPay) {
      if (!scanPayEnabled.value) {
        ElMessage.error('请启动本地客户端（OpenHIS.Win）并确认银联组件已安装');
        return;
      }
      scanStatusText.value = '等待支付结果…';
      const scanOutcome = await submitScanPayment({
        paymentDraftId: snap.paymentDraftId,
        amount: snap.displayAmount ?? 0,
        scanCode: scanCode.value,
      });
      clientScanResult = scanOutcome.clientScanResult;
      scanStatusText.value = `支付成功${scanOutcome.clientScanResult.tradeNo ? `，单号 ${scanOutcome.clientScanResult.tradeNo}` : ''}`;
    }

    const insuranceGroupBchnos =
      snap.insuranceGroups
        ?.map((group) => String(group.chrgBchno ?? '').trim())
        .filter((value) => value.length > 0) ?? [];
    const payResult = await (clientScanResult
      ? settleWithScanRecovery(clientScanResult, () =>
          httpConfirmOutpatientChargeSettlement({
            billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
            chargeItemIds: lockedChargeItemIds.value,
            encounterId: eid,
            outcomeEnum: OUTPATIENT_CHARGE_SETTLE_OUTCOME_COMPLETED,
            paymentDetails: [
              {
                amount: snap.displayAmount,
                payChannelCode: paymentForm.payChannelCode,
              },
            ],
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
          paymentDetails: [
            {
              amount: snap.displayAmount,
              payChannelCode: paymentForm.payChannelCode,
            },
          ],
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
    lockedChargeItemIds.value = [];
    void loadChargeQueue().then(() => loadPendingForSelectedEncounter());
    schedulePrintChargeReceipt({
      encounterId: eid,
      paymentId: payResult?.paymentId,
      jobName: '收费小票',
    });
  } catch (error) {
    if (isOutpatientChargeStaleSettlementError(error)) {
      await resetSettlementAfterAdviceChanged();
      return;
    }
    if (!isStaleCrossRoleError(error)) {
      const message = error instanceof Error ? error.message : '门诊收费失败';
      ElMessage.error(message);
    }
  } finally {
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
  const paymentId = String(row.paymentId ?? '').trim();
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
                  :disabled="!selectedEncounterId"
                  class="mr-1"
                  @click="handleSwitchInsurance"
                >
                  转医保
                </ElButton>
                <ElButton
                  :disabled="!selectedEncounterId"
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
                <TableLayout
                  ref="chargeTableLayoutRef"
                  :key="selectedEncounterId || 'no-encounter'"
                  :table-data="pendingItems"
                  :loading="tableLoading"
                  :total="pendingItems.length"
                  :query-params="chargeTableQueryParams"
                  :table-columns="chargeTableColumns"
                  :show-pagination="false"
                  :show-top-query="false"
                  :max-height="600"
                  row-key="chargeItemId"
                  border
                  stripe
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
                  <template #chargeItemActionsCell>—</template>
                </TableLayout>
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

    <ChargeSettlementDialog
      v-model="settlementVisible"
      v-model:pay-channel-code="paymentForm.payChannelCode"
      v-model:scan-code="scanCode"
      :loading="confirmSettleLoading"
      :display-amount="preSettleSnapshot?.displayAmount ?? 0"
      :insurance-breakdown-items="settlementInsuranceBreakdown"
      :insurance-groups="settlementInsuranceGroups"
      :previews="settlementPreviews"
      :selected-charge-count="lockedChargeItemIds.length"
      :summary-insurance-amount="settlementSummaryInsurance"
      :summary-self-pay-amount="settlementSummarySelfPay"
      :pay-channel-options="effectivePayChannelOptions"
      :scan-pay-enabled="scanPayEnabled"
      :scan-status-text="scanStatusText"
      @confirm-charge="handleConfirmSettlement"
    />
  </div>
</template>

<style scoped>
.charge-filter-toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.charge-filter-toolbar__status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 12px;
  line-height: 1.25;
}

.charge-filter-toolbar__status :deep(.el-radio) {
  margin-right: 0;
  height: auto;
  line-height: 1.25;
}

.charge-filter-toolbar__date :deep(.el-input__wrapper) {
  width: 100%;
}

.charge-patient-scroll {
  min-height: 200px;
}

.patient-info-bar {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-light);
  padding: 10px 12px;
}

.patient-info-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 16px;
}

.patient-info-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.patient-info-label {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  white-space: nowrap;
}

.patient-info-value {
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 500;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.charge-detail-wrapper :deep(button[title='全屏']),
.charge-detail-wrapper :deep(button[aria-label='全屏']),
.charge-detail-wrapper :deep(.iconify[data-icon*='fullscreen']) {
  display: none !important;
}

.charge-detail-wrapper :deep(.vxe-grid) {
  height: auto !important;
  max-height: none !important;
}

.charge-detail-wrapper :deep(.vxe-table--body-wrapper) {
  max-height: none !important;
}
</style>
