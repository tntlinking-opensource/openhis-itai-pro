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
  ElLoading,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElRadio,
  ElRadioGroup,
  ElSelect,
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
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { decimalAdd } from '#/utils/decimal-util';

import {
  CHARGE_ITEM_STATUS_LABEL_TAG,
  DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE,
  OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS,
  OUTPATIENT_CHARGE_SETTLE_OUTCOME_COMPLETED,
} from './outpatient-charge.constants';
import ChargeSettlementDialog from './settlement-dialog.vue';
import {
  isOutpatientInsuranceAsyncConfirmResponse,
  pollOutpatientInsuranceSettleStatus,
  resolveInsuranceContractId,
  resolveOutpatientChargeInsuranceContext,
  selectionIncludesInsuranceItems,
} from './use-outpatient-charge-insurance';
import { useChargeReceiptPrint } from '#/composables/use-charge-receipt-print';
import { useOutpatientChargeListPrint } from '#/composables/use-outpatient-charge-list-print';
import { useOutpatientInvoicePrint } from '#/composables/use-outpatient-invoice-print';
import {
  ensureOutpatientChargePrintBindingLoaded,
  getOutpatientChargePrintTemplateCode,
  isOutpatientInvoiceOverlayTemplate,
  OUTPATIENT_CHARGE_PRINT_OPTIONS,
  setOutpatientChargePrintTemplateCode,
} from '#/utils/report/outpatient-charge-print-config';

defineOptions({
  name: 'ChargeOpCharge',
});

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

const {
  printChargeReceipt,
  isPrinting: receiptPrinting,
  previewLoading: receiptPreviewLoading,
} = useChargeReceiptPrint();
const {
  printOutpatientInvoice,
  isPrinting: invoicePrinting,
  previewLoading: invoicePreviewLoading,
} = useOutpatientInvoicePrint();
const {
  printOutpatientChargeList,
  isPrinting: chargeListPrinting,
  previewLoading: chargeListPreviewLoading,
} = useOutpatientChargeListPrint();

const chargePrintTemplateCode = ref(getOutpatientChargePrintTemplateCode());
const lastSettlePrintByEncounter = ref<
  Record<string, { paymentId: string; chargeItemIds: string[] }>
>({});

const chargePrintBusy = computed(
  () =>
    receiptPrinting.value ||
    receiptPreviewLoading.value ||
    invoicePrinting.value ||
    invoicePreviewLoading.value ||
    chargeListPrinting.value ||
    chargeListPreviewLoading.value,
);

async function handleChargePrintTemplateChange(code: string) {
  await setOutpatientChargePrintTemplateCode(code);
}

async function printAfterChargeSettle(params: {
  paymentId: string;
  encounterId: string;
  chargeItemIds: string[];
}) {
  // TODO: 打印插件就绪后恢复收费成功后自动打印
  void params;
  return;

  /*
  try {
    const templateCode = getOutpatientChargePrintTemplateCode();
    if (isOutpatientInvoiceOverlayTemplate(templateCode)) {
      await printOutpatientInvoice({
        ...params,
        templateCode,
        confirmBeforePrint: false,
      });
      return;
    }
    await printChargeReceipt({
      ...params,
      templateCode,
      confirmBeforePrint: false,
    });
  } catch {
    // 静默失败，不打扰收费主流程
  }
  */
}

async function handleReprintChargeSlip() {
  const eid = selectedEncounterId.value;
  if (!eid) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  const ctx = lastSettlePrintByEncounter.value[eid];
  if (!ctx) {
    ElMessage.warning('暂无本次会话内的结算记录，请完成收费后打印');
    return;
  }
  await printAfterChargeSettle({
    paymentId: ctx.paymentId,
    encounterId: eid,
    chargeItemIds: ctx.chargeItemIds,
  });
}

async function handlePrintChargeList() {
  const eid = selectedEncounterId.value;
  if (!eid) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  const ctx = lastSettlePrintByEncounter.value[eid];
  if (!ctx) {
    ElMessage.warning('暂无本次会话内的结算记录，请完成收费后打印费用清单');
    return;
  }
  await printOutpatientChargeList({
    paymentId: ctx.paymentId,
    encounterId: eid,
    chargeItemIds: ctx.chargeItemIds,
  });
}

const paymentForm = reactive({
  payChannelCode: DEFAULT_OUTPATIENT_CHARGE_PAY_CHANNEL_CODE,
});

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
const formatMoney = (value: null | number | string | undefined) => {
  const n = Number(value ?? 0);
  if (Number.isNaN(n)) {
    return '0.00';
  }
  return n.toFixed(2);
};

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
    // TODO: 医保读卡插件就绪后恢复
    const insuranceCtx = null;
    /*
    let insuranceCtx = null;
    try {
      insuranceCtx = await resolveOutpatientChargeInsuranceContext(rows);
    } catch (error) {
      const message = error instanceof Error ? error.message : '医保读卡失败';
      ElMessage.error(message);
      return;
    }
    if (insuranceCtx === null && selectionIncludesInsuranceItems(rows)) {
      if (!resolveInsuranceContractId(rows)) {
        return;
      }
      ElMessage.warning(
        '本机 OpenHIS.Win 未启动，已跳过医保读卡；自费收费可继续，医保结算请先启动插件',
      );
    }
    */
    const res = await httpPreSettleOutpatientCharges({
      chargeItemIds: rows.map((r) => r.chargeItemId),
      encounterId: eid,
      ...(insuranceCtx
        ? {
            contractId: insuranceCtx.contractId,
            insuplcAdmdvs: insuranceCtx.insuplcAdmdvs,
            insutype: insuranceCtx.insutype,
            psnNo: insuranceCtx.psnNo,
            ybCardInfo: insuranceCtx.ybCardInfo,
            ybMediumType: insuranceCtx.ybMediumType,
          }
        : {}),
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
  confirmSettleLoading.value = true;
  let asyncLoading: ReturnType<typeof ElLoading.service> | null = null;
  try {
    const insuranceGroupBchnos =
      snap.insuranceGroups
        ?.map((group) => String(group.chrgBchno ?? '').trim())
        .filter((value) => value.length > 0) ?? [];
    let settleResult = await httpConfirmOutpatientChargeSettlement({
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
    });
    if (isOutpatientInsuranceAsyncConfirmResponse(settleResult)) {
      asyncLoading = ElLoading.service({
        lock: true,
        text: '医保协同单结算处理中，请稍候…',
        background: 'rgba(0, 0, 0, 0.35)',
      });
      const polled = await pollOutpatientInsuranceSettleStatus(String(settleResult.paymentId));
      settleResult = {
        ...settleResult,
        paymentId: polled.paymentId ?? settleResult.paymentId,
        invoiceId: polled.invoiceId ?? settleResult.invoiceId,
        ybSetlId: polled.ybSetlId ?? settleResult.ybSetlId,
        paymentStatus: polled.paymentStatus ?? settleResult.paymentStatus,
      };
    }
    ElMessage.success('收费成功');
    settlementVisible.value = false;
    const printedChargeItemIds = [...lockedChargeItemIds.value];
    preSettleSnapshot.value = null;
    lockedChargeItemIds.value = [];
    if (settleResult.paymentId) {
      lastSettlePrintByEncounter.value = {
        ...lastSettlePrintByEncounter.value,
        [eid]: {
          paymentId: settleResult.paymentId,
          chargeItemIds: printedChargeItemIds,
        },
      };
      // TODO: 打印插件就绪后恢复收费成功后自动打印
      // void printAfterChargeSettle({
      //   paymentId: settleResult.paymentId,
      //   encounterId: eid,
      //   chargeItemIds: printedChargeItemIds,
      // });
    }
    void loadChargeQueue().then(() => loadPendingForSelectedEncounter());
  } catch (error) {
    const message = error instanceof Error ? error.message : '门诊收费失败';
    ElMessage.error(message);
  } finally {
    asyncLoading?.close();
    confirmSettleLoading.value = false;
  }
};

onMounted(() => {
  void (async () => {
    await ensureOutpatientChargePrintBindingLoaded();
    chargePrintTemplateCode.value = getOutpatientChargePrintTemplateCode();
  })();
  void httpLoadChargePageInit({});
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
                    <template v-if="currentQueueRow">
                      应收 ¥{{ formatMoney(currentQueueRow.totalAmount) }} · 已付 ¥{{
                        formatMoney(currentQueueRow.paidAmount)
                      }}
                    </template>
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
                <ElSelect
                  v-model="chargePrintTemplateCode"
                  class="!w-36 shrink-0"
                  :disabled="!selectedEncounterId || chargePrintBusy"
                  @change="handleChargePrintTemplateChange"
                >
                  <ElOption
                    v-for="opt in OUTPATIENT_CHARGE_PRINT_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </ElSelect>
                <ElButton
                  plain
                  :disabled="!selectedEncounterId"
                  :loading="chargePrintBusy"
                  @click="handlePrintChargeList"
                >
                  费用清单
                </ElButton>
                <ElButton
                  plain
                  :disabled="!selectedEncounterId"
                  :loading="chargePrintBusy"
                  @click="handleReprintChargeSlip"
                >
                  补打
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
      :loading="confirmSettleLoading"
      :display-amount="preSettleSnapshot?.displayAmount ?? 0"
      :insurance-breakdown-items="settlementInsuranceBreakdown"
      :insurance-groups="settlementInsuranceGroups"
      :previews="settlementPreviews"
      :selected-charge-count="lockedChargeItemIds.length"
      :summary-insurance-amount="settlementSummaryInsurance"
      :summary-self-pay-amount="settlementSummarySelfPay"
      :pay-channel-options="OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS"
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
