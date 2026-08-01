<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type { PreSettleChargeLinePreviewDto } from '#/api/outpatient-charge/outpatient-charge.types';
import type {
  OutpatientRefundPaymentGroupDto,
  OutpatientRefundQueueRowDto,
} from '#/api/outpatient-refund/outpatient-refund.types';
import type { BillingPatientCardPatient } from '#/components/charge/billing-patient-card.types';
import type { PayChannelOption } from '#/utils/payment/payment-channel-options';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { RotateCw } from '@vben/icons';

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
  httpExecuteOutpatientBillRefundByPayment,
  httpLoadOutpatientRefundExecutionDetail,
  httpLoadOutpatientRefundPageInit,
  httpPageOutpatientRefundQueue,
  httpVerifyRefundableBeforeExecution,
} from '#/api/outpatient-refund/outpatient-refund.http';
import { runWorkInjuryRefundReverse } from '#/chs/work-injury/use-work-injury-settle';
import BillingPatientCard from '#/components/charge/billing-patient-card.vue';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
import SectionTitle from '#/components/layouts/section-title.vue';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import SelectFuzzyInput from '#/components/select-fuzzy-input.vue';
import { useUnionPayScanPay } from '#/composables/payment/useUnionPayScanPay';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { buildDefaultDateRangeShortcuts } from '#/utils/format/date-picker-shortcuts';
import { sumMoneyThenSettle } from '#/utils/format/money-scale';
import { optionDtosToPayChannelOptions } from '#/utils/payment/payment-channel-options';
import {
  clearCachedClientRefundResult,
  submitScanRefund,
} from '#/utils/payment/submit-scan-refund';

import { OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS } from '../charge/outpatient-charge.constants';
import {
  formatPatientFeeMoney,
  summarizeOutpatientRefundFeeAmounts,
} from '../services/patient-fee-summary.service';
import '../charge-workbench-panel.css';
import ChargeSettlementDialog from '../charge/settlement-dialog.vue';
import {
  REFUND_LINE_STATUS_LABEL_TAG,
  REFUND_REJECT_REASON_MESSAGES,
} from './outpatient-refund.constants';

defineOptions({
  name: 'ChargeOpRefund',
});

/** 表格展平后的退费明细行（拟退 + 半退保留） */
interface RefundDetailTableRow {
  chargeItemId: string;
  chargeItemStatusEnum: string;
  halfRefundOnPayment?: boolean;
  hasRefundingLinesInPaymentGroup?: boolean;
  isFirstLineInPaymentGroup?: boolean;
  itemDisplayName?: null | string;
  lineTotalAmount?: null | number | string;
  paymentBusNo?: null | string;
  paymentGroupLineCount?: number;
  paymentId: string;
}

/** 同一原支付分组内需纵向合并的列 */
const PAYMENT_GROUP_MERGE_FIELDS = new Set(['paymentBusNo']);

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
const queueRecords = ref<OutpatientRefundQueueRowDto[]>([]);
const queueTotal = ref(0);
const queueLoading = ref(false);

const selectedEncounterId = ref('');
const paymentGroups = ref<OutpatientRefundPaymentGroupDto[]>([]);
const detailTableRows = ref<RefundDetailTableRow[]>([]);
const selectedDetailRows = ref<RefundDetailTableRow[]>([]);

const settlementVisible = ref(false);
const openSettlementLoading = ref(false);
const confirmRefundLoading = ref(false);

/** 确认退费弹窗快照（锁定原支付与拟退行，避免弹窗打开后勾选变化） */
const settlementSnapshot = ref<null | {
  displayAmount: number | string;
  originalPaymentId: string;
  originalPayChannelCodes?: string[];
  preservedNewChargeItemIds: string[];
  previews: PreSettleChargeLinePreviewDto[];
  refundingChargeItemIds: string[];
  selfPayAmount: number;
  ybAcctPay: number;
  ybFundPaySumamt: number;
}>(null);

const payChannelOptions = ref<PayChannelOption[]>([...OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS]);

const { refreshUnionPayCapabilities } = useUnionPayScanPay(() => payChannelOptions.value);

const settlementInsuranceBreakdown = computed(() => {
  const snap = settlementSnapshot.value;
  if (!snap) {
    return [];
  }
  const fundPay = snap.ybFundPaySumamt + snap.ybAcctPay;
  return [
    { key: 'fundPay', label: '基金支出', value: fundPay },
    { key: 'poolingPay', label: '统筹基金支付', value: snap.ybFundPaySumamt },
    { key: 'accountPay', label: '个人账户支付', value: snap.ybAcctPay },
    { key: 'cashPay', label: '现金补缴', value: snap.selfPayAmount },
    { key: 'finalReceivable', label: '最终应收', value: Number(snap.displayAmount ?? 0) },
  ];
});

const settlementSummaryInsurance = computed(
  () =>
    Number(settlementSnapshot.value?.ybFundPaySumamt ?? 0) +
    Number(settlementSnapshot.value?.ybAcctPay ?? 0),
);

const settlementSummarySelfPay = computed(() =>
  Number(settlementSnapshot.value?.selfPayAmount ?? 0),
);

const detailTableQueryParams = reactive({
  pageNo: 1,
  pageSize: 20,
});

const detailTableLayoutRef = ref<InstanceType<typeof RegisterStyleTableLayout> | null>(null);
/** 程序同步勾选时跳过 selection-change 的整组展开，避免递归 */
const syncingDetailSelection = ref(false);

/** 明细表底层 VxeGrid（RegisterStyleTableLayout → gridApi，可能多层 ref） */
function getDetailVxeGrid(): null | Record<string, unknown> {
  let cur: unknown = (detailTableLayoutRef.value as null | { gridApi?: unknown })?.gridApi;
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

/** 拟退行（REFUNDING）才可勾选执行退费 */
function isRefundingLineSelectable(row: RefundDetailTableRow) {
  return (row.chargeItemStatusEnum ?? '').toString().trim().toUpperCase() === 'REFUNDING';
}

/**
 * vxe 列 `checkMethod` 签名为 `({ row })`，TableColumn 的 `selectable` 类型为 `(row, index)`，二者兼容处理。
 * 同一原支付须整单退：仅每组首行可勾选。
 */
function detailRowSelectionSelectable(arg0: Record<string, unknown>, _index: number) {
  const row = (
    arg0 && typeof arg0 === 'object' && 'row' in arg0
      ? (arg0 as { row: RefundDetailTableRow }).row
      : arg0
  ) as RefundDetailTableRow;
  return (
    row.isFirstLineInPaymentGroup === true &&
    row.hasRefundingLinesInPaymentGroup === true &&
    isRefundingLineSelectable(row)
  );
}

/** 按组首行勾选结果展开为整单拟退行 */
function expandSelectionToWholePaymentGroups(selection: RefundDetailTableRow[]) {
  const selectedGroupIds = new Set<string>();
  for (const row of detailTableRows.value) {
    if (!row.isFirstLineInPaymentGroup || !row.hasRefundingLinesInPaymentGroup) {
      continue;
    }
    const checked = selection.some((item) => item.chargeItemId === row.chargeItemId);
    if (checked) {
      selectedGroupIds.add(row.paymentId);
    }
  }
  return detailTableRows.value.filter(
    (row) => selectedGroupIds.has(row.paymentId) && isRefundingLineSelectable(row),
  );
}

/** 将勾选状态同步到底层 VxeGrid（整单拟退行一并勾选） */
function applyDetailGridCheckboxSelection(
  rows: RefundDetailTableRow[],
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
            fullData?: RefundDetailTableRow[];
            tableData?: RefundDetailTableRow[];
          })
        : null;
    const tableRows =
      tableDataResult?.fullData ?? tableDataResult?.tableData ?? detailTableRows.value;
    for (const row of tableRows) {
      if (!isRefundingLineSelectable(row)) {
        continue;
      }
      targetGrid.setCheckboxRow(row, selectedIds.has(row.chargeItemId));
    }
  };
  if (grid) {
    doApply(grid);
    return;
  }
  void nextTick(() => {
    const targetGrid = getDetailVxeGrid();
    if (targetGrid) {
      doApply(targetGrid);
    }
  });
}

/** 将按支付分组的明细展平为表格行（先拟退后保留，便于同组相邻展示） */
function flattenPaymentGroupsToTableRows(
  groups: OutpatientRefundPaymentGroupDto[],
): RefundDetailTableRow[] {
  const rows: RefundDetailTableRow[] = [];
  for (const group of groups) {
    const paymentId = group.paymentId;
    const paymentBusNo = group.paymentBusNo;
    const halfRefundOnPayment = group.halfRefundOnPayment;
    const groupLines = [...(group.refundingLines ?? []), ...(group.preservedLines ?? [])];
    const lineCount = groupLines.length;
    const hasRefundingLinesInPaymentGroup = (group.refundingLines?.length ?? 0) > 0;
    groupLines.forEach((line, lineIndex) => {
      rows.push({
        chargeItemId: line.chargeItemId,
        chargeItemStatusEnum: line.chargeItemStatusEnum,
        itemDisplayName: line.itemDisplayName,
        lineTotalAmount: line.lineTotalAmount,
        paymentId,
        paymentBusNo,
        halfRefundOnPayment,
        hasRefundingLinesInPaymentGroup,
        isFirstLineInPaymentGroup: lineIndex === 0,
        paymentGroupLineCount: lineCount,
      });
    });
  }
  return rows;
}

/** VxeGrid spanMethod：勾选列与原支付单号列按 paymentId 分组纵向合并 */
function handleRefundDetailSpanMethod(params: {
  column: Record<string, unknown>;
  row: Record<string, unknown>;
}) {
  const row = params.row as RefundDetailTableRow;
  const column = params.column as { field?: string; type?: string };
  const field = column.field;
  const shouldMerge =
    (field && PAYMENT_GROUP_MERGE_FIELDS.has(field)) || column.type === 'checkbox';
  if (!shouldMerge) {
    return undefined;
  }
  if (row.isFirstLineInPaymentGroup) {
    return { rowspan: row.paymentGroupLineCount ?? 1, colspan: 1 };
  }
  return { rowspan: 0, colspan: 0 };
}

/** 加载明细后默认勾选全部可退原支付整单。
 * Table.vue 在 tableData 变更后会 post-flush → clearSelection，须等清空后再勾。
 */
async function selectAllRefundingRowsInGrid(items: RefundDetailTableRow[]) {
  const selectable = items.filter((r) => isRefundingLineSelectable(r));
  syncingDetailSelection.value = true;
  selectedDetailRows.value = selectable.length > 0 ? [...selectable] : [];

  await nextTick();
  await nextTick();
  await new Promise<void>((resolve) => {
    setTimeout(resolve, 0);
  });

  let grid = getDetailVxeGrid();
  if (!grid) {
    await nextTick();
    grid = getDetailVxeGrid();
  }

  if (grid) {
    if (typeof grid.clearCheckboxRow === 'function') {
      grid.clearCheckboxRow();
    }
    if (selectable.length > 0) {
      applyDetailGridCheckboxSelection(selectable, grid);
    }
  }

  await nextTick();
  selectedDetailRows.value = selectable.length > 0 ? [...selectable] : [];
  syncingDetailSelection.value = false;
}

/** 按原支付 id 查找分组 */
function findPaymentGroupById(paymentId: string) {
  return paymentGroups.value.find((g) => g.paymentId === paymentId) ?? null;
}

/** 门禁拒绝码转用户可读文案 */
function resolveRejectMessage(code: null | string | undefined) {
  const key = (code ?? '').trim();
  if (key && REFUND_REJECT_REASON_MESSAGES[key]) {
    return REFUND_REJECT_REASON_MESSAGES[key];
  }
  return key ? `无法退费：${key}` : '当前所选项目暂不可退费';
}

/** 将金额安全格式化为两位小数字符串 */
const formatMoney = formatPatientFeeMoney;

/** 表格文本列：空串/空白显示为「—」 */
const detailDashFormatter: NonNullable<TableColumn['formatter']> = (_row, _col, cellValue) => {
  if (cellValue === null || cellValue === undefined || cellValue === '') {
    return '—';
  }
  const s = String(cellValue).trim();
  return s.length > 0 ? s : '—';
};

/** 当前选中队列行 */
const currentQueueRow = computed(
  () => queueRecords.value.find((r) => r.encounterId === selectedEncounterId.value) ?? null,
);

/** 性别展示：优先接口文本字段 */
function queueGenderDisplay(row: null | OutpatientRefundQueueRowDto | undefined): string {
  if (!row) return '—';
  const text = (row.genderEnumText ?? '').trim();
  if (text) return text;
  const code = (row.genderCode ?? '').trim();
  return code || '—';
}

/** 已选原支付单数 */
const selectedPaymentGroupCount = computed(() => {
  const ids = new Set<string>();
  for (const row of selectedDetailRows.value) {
    ids.add(row.paymentId);
  }
  return ids.size;
});

/** 已选拟退行金额合计（展示用，实退以支付组 refundableAmount 为准） */
const selectedRefundingSum = computed(() =>
  sumMoneyThenSettle(selectedDetailRows.value.map((row) => row.lineTotalAmount ?? 0)),
);

/** 患者信息栏费用摘要：与明细表同源，避免队列行空金额 */
const refundPatientFeeSummary = computed(() => {
  if (!selectedEncounterId.value) {
    return null;
  }
  return summarizeOutpatientRefundFeeAmounts(paymentGroups.value);
});

const { run: runLoadExecutionDetail, loading: tableLoading } = useRequest(
  httpLoadOutpatientRefundExecutionDetail,
  {
    manual: true,
    onSuccess: (data) => {
      const groups = data.paymentGroups ?? [];
      paymentGroups.value = groups;
      const rows = flattenPaymentGroupsToTableRows(groups);
      detailTableRows.value = rows;
      selectAllRefundingRowsInGrid(rows);
    },
  },
);

/** 拉取待退费患者队列 */
const loadRefundQueue = async () => {
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
    const res = await httpPageOutpatientRefundQueue(body);
    const { content, totalCount } = parsePagedRowsFromApiData<OutpatientRefundQueueRowDto>(res);
    queueRecords.value = content;
    queueTotal.value = totalCount;
  } finally {
    queueLoading.value = false;
  }
};

/** 明细查询口径：页签为 ALL 时跟随队列行标签，否则与左侧页签一致 */
function resolveDetailViewCode(override?: string) {
  if (override) {
    return override;
  }
  if (queueTabCode.value === QUEUE_TAB.SETTLED) {
    return QUEUE_TAB.SETTLED;
  }
  if (queueTabCode.value === QUEUE_TAB.PENDING) {
    return QUEUE_TAB.PENDING;
  }
  const label = (currentQueueRow.value?.queueLabelCode ?? '').trim().toUpperCase();
  if (label === QUEUE_TAB.SETTLED || label === QUEUE_TAB.PENDING) {
    return label;
  }
  return QUEUE_TAB.PENDING;
}

/** 当前是否为已退费只读明细（不展示执行退费动作） */
const isSettledDetailView = computed(() => resolveDetailViewCode() === QUEUE_TAB.SETTLED);

/** 按当前选中就诊刷新退费明细 */
const loadExecutionDetailForSelectedEncounter = async (detailViewCodeOverride?: string) => {
  const eid = selectedEncounterId.value;
  if (!eid) {
    paymentGroups.value = [];
    detailTableRows.value = [];
    selectedDetailRows.value = [];
    void nextTick(() => {
      const grid = getDetailVxeGrid();
      if (grid && typeof grid.clearCheckboxRow === 'function') {
        grid.clearCheckboxRow();
      }
    });
    return;
  }
  await runLoadExecutionDetail({
    encounterId: eid,
    detailViewCode: resolveDetailViewCode(detailViewCodeOverride),
  });
};

/** 关键字/日期变更后回到第一页再查队列 */
const resetQueuePageAndLoad = () => {
  queuePager.pageNo = 1;
  void loadRefundQueue();
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
  settlementSnapshot.value = null;
  selectedDetailRows.value = [];
  void loadExecutionDetailForSelectedEncounter();
});

/** 队列行映射为患者卡片 */
const mapToBillingPatientCard = (row: OutpatientRefundQueueRowDto): BillingPatientCardPatient => {
  const settled = row.queueLabelCode === QUEUE_TAB.SETTLED;
  return {
    id: row.encounterId,
    patientName: row.patientName,
    gender: queueGenderDisplay(row),
    ageText: row.ageText ?? undefined,
    chargeStatusCode: settled ? 'SETTLED' : 'REFUNDING',
    chargeStatus: settled ? '已退费' : '待退费',
    visitDepartment: row.organizationName ?? '—',
    visitTime: row.startTime ?? '—',
    visitType: 'outpatient',
    doctorName: row.doctorName ?? undefined,
  };
};

/** 选中卡片切换当前就诊；再次点击已选患者仍刷新明细 */
const handleChoosePatient = (patient: BillingPatientCardPatient) => {
  const id = patient.id;
  if (selectedEncounterId.value === id) {
    settlementSnapshot.value = null;
    selectedDetailRows.value = [];
    void loadExecutionDetailForSelectedEncounter();
    return;
  }
  selectedEncounterId.value = id;
};

/** 手工刷新队列 */
const handlePatientQueueRefresh = () => {
  void loadRefundQueue();
};

/** 分页切换 */
const handleQueuePageChange = () => {
  void loadRefundQueue();
};

/** 每页条数变化时回到第一页 */
const handleQueueSizeChange = () => {
  queuePager.pageNo = 1;
  void loadRefundQueue();
};

/** 表格勾选变更：按原支付整单展开，不支持单条拟退行单独勾选 */
const handleDetailSelectionChange = (selection: RefundDetailTableRow[]) => {
  if (syncingDetailSelection.value) {
    return;
  }
  const expanded = expandSelectionToWholePaymentGroups(selection);
  selectedDetailRows.value = expanded;

  const expandedIds = new Set(expanded.map((row) => row.chargeItemId));
  const rawRefundingIds = new Set(
    selection.filter((row) => isRefundingLineSelectable(row)).map((row) => row.chargeItemId),
  );
  const needsSync =
    expandedIds.size !== rawRefundingIds.size ||
    [...expandedIds].some((id) => !rawRefundingIds.has(id));
  if (needsSync) {
    syncingDetailSelection.value = true;
    applyDetailGridCheckboxSelection(expanded, getDetailVxeGrid());
    void nextTick(() => {
      syncingDetailSelection.value = false;
    });
  }
};

/** 明细状态标签 */
const refundLineStatusTag = (row: RefundDetailTableRow) => {
  const code = (row.chargeItemStatusEnum ?? '').trim().toUpperCase();
  const mapped = code ? REFUND_LINE_STATUS_LABEL_TAG[code] : undefined;
  if (mapped) {
    return mapped;
  }
  if (code) {
    return { label: code, tagType: 'info' as const };
  }
  return { label: '—', tagType: 'info' as const };
};

const detailTableColumns: TableColumn[] = [
  {
    type: 'selection',
    width: 52,
    align: 'center',
    headerAlign: 'center',
    selectable: detailRowSelectionSelectable,
  },
  {
    prop: 'paymentBusNo',
    label: '原支付单号',
    minWidth: 140,
    align: 'center',
    headerAlign: 'center',
    formatter: detailDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'itemDisplayName',
    label: '收费项目',
    minWidth: 160,
    align: 'left',
    headerAlign: 'center',
    formatter: detailDashFormatter,
    showOverflowTooltip: true,
  },
  {
    prop: 'chargeItemStatusEnum',
    label: '状态',
    minWidth: 100,
    align: 'center',
    headerAlign: 'center',
    slot: 'statusCell',
    showOverflowTooltip: true,
  },
  {
    prop: 'lineTotalAmount',
    label: '金额',
    minWidth: 100,
    slot: 'lineTotalAmount',
    align: 'right',
    headerAlign: 'center',
  },
  {
    prop: 'halfRefundOnPayment',
    label: '半退',
    minWidth: 72,
    align: 'center',
    headerAlign: 'center',
    slot: 'halfRefundCell',
  },
];

/** 打开退费确认窗：校验同笔原支付并做执行前门禁 */
const handleOpenSettlement = async () => {
  const eid = selectedEncounterId.value;
  if (!eid) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  const selected = selectedDetailRows.value;
  if (selected.length === 0) {
    ElMessage.warning('请选择拟退原支付单');
    return;
  }
  const paymentIds = [...new Set(selected.map((r) => r.paymentId))];
  if (paymentIds.length > 1) {
    ElMessage.warning('请选择同一笔原支付单，多笔支付请分次退费');
    return;
  }
  const originalPaymentId = paymentIds[0] ?? '';
  const group = findPaymentGroupById(originalPaymentId);
  if (!group) {
    ElMessage.warning('未找到对应原支付分组，请刷新后重试');
    return;
  }
  const groupRefundingLines = group.refundingLines ?? [];
  const selectedIds = new Set(selected.map((row) => row.chargeItemId));
  const wholeGroupSelected = groupRefundingLines.every((line) =>
    selectedIds.has(line.chargeItemId),
  );
  if (!wholeGroupSelected) {
    ElMessage.warning('同一笔原支付须整单退费，不支持单独勾选某一项');
    return;
  }

  openSettlementLoading.value = true;
  try {
    const refundingChargeItemIds = groupRefundingLines.map((line) => line.chargeItemId);
    const verifyRes = await httpVerifyRefundableBeforeExecution({
      encounterId: eid,
      refundingChargeItemIds,
    });
    if (!verifyRes.allowed) {
      ElMessage.warning(resolveRejectMessage(verifyRes.rejectReasonCode));
      return;
    }

    const previews: PreSettleChargeLinePreviewDto[] = groupRefundingLines.map((line) => ({
      chargeItemId: line.chargeItemId,
      itemLabel: line.itemDisplayName ?? undefined,
      lineAmount: line.lineTotalAmount ?? 0,
    }));

    settlementSnapshot.value = {
      displayAmount: group.refundableAmount,
      originalPaymentId,
      originalPayChannelCodes: group.originalPayChannelCodes,
      preservedNewChargeItemIds: (group.preservedLines ?? []).map((l) => l.chargeItemId),
      previews,
      refundingChargeItemIds,
      selfPayAmount: Number(group.selfPayAmount ?? 0),
      ybAcctPay: Number(group.ybAcctPay ?? 0),
      ybFundPaySumamt: Number(group.ybFundPaySumamt ?? 0),
    };
    settlementVisible.value = true;
  } finally {
    openSettlementLoading.value = false;
  }
};

/** 按原支付执行退费（渠道原路返回由后端装配；含半退保留行再结算由后端编排） */
const buildRefundRequest = (snap: NonNullable<typeof settlementSnapshot.value>, eid: string) => ({
  billDate: dayjs().format('YYYY-MM-DD HH:mm:ss'),
  encounterId: eid,
  originalPaymentId: snap.originalPaymentId,
  refundingChargeItemIds: snap.refundingChargeItemIds,
  preservedNewChargeItemIds:
    snap.preservedNewChargeItemIds.length > 0 ? snap.preservedNewChargeItemIds : undefined,
});

/** 退费成功后的列表刷新 */
const finalizeRefundSuccess = async (
  res: Awaited<ReturnType<typeof httpExecuteOutpatientBillRefundByPayment>>,
  originalPaymentId?: string,
) => {
  if (res.success) {
    ElMessage.success(res.halfRefundExecuted ? '退费已完成（含保留项再结算）' : '退费已完成');
  }
  if (originalPaymentId) {
    clearCachedClientRefundResult(originalPaymentId);
  }
  settlementVisible.value = false;
  settlementSnapshot.value = null;
  if (queueTabCode.value === QUEUE_TAB.PENDING) {
    queueTabCode.value = QUEUE_TAB.SETTLED;
  }
  await loadRefundQueue();
  await loadExecutionDetailForSelectedEncounter(QUEUE_TAB.SETTLED);
};

const handleConfirmRefund = async () => {
  const snap = settlementSnapshot.value;
  const eid = selectedEncounterId.value;
  if (!snap || !eid) {
    return;
  }
  confirmRefundLoading.value = true;
  let clientRefundResult: Awaited<ReturnType<typeof submitScanRefund>> | undefined;
  try {
    await runWorkInjuryRefundReverse({
      encounterId: eid,
      originalPaymentId: snap.originalPaymentId,
    });

    try {
      // 银联只退扫码实付段：金额以 BPC txnAmtFen 为准；此处 selfPayAmount 仅作无 BPC 金额时的兜底提示
      clientRefundResult =
        (await submitScanRefund({
          originalPaymentId: snap.originalPaymentId,
          originalPayChannelCodes: snap.originalPayChannelCodes,
          refundAmountYuan: snap.selfPayAmount,
        })) ?? undefined;
    } catch (error) {
      const message = error instanceof Error ? error.message : '退费前银联校验失败，请稍后重试';
      ElMessage.error(message);
      return;
    }

    const refundBody = {
      ...buildRefundRequest(snap, eid),
      ...(clientRefundResult ? { clientRefundResult } : {}),
    };
    const res = await httpExecuteOutpatientBillRefundByPayment(refundBody);
    await finalizeRefundSuccess(res, snap.originalPaymentId);
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '退费失败';
    if (clientRefundResult) {
      ElMessage.error(
        `${message}。银联扫码段可能已退成功，请勿重复人工退货；请直接再次点「确认退费」完成本院收口。`,
      );
    } else {
      ElMessage.error(message);
    }
  } finally {
    confirmRefundLoading.value = false;
  }
};

onMounted(() => {
  void (async () => {
    try {
      const init = await httpLoadOutpatientRefundPageInit({});
      payChannelOptions.value = optionDtosToPayChannelOptions(init.paymentChannelOptions);
    } catch {
      payChannelOptions.value = [...OUTPATIENT_CHARGE_PAY_CHANNEL_OPTIONS];
    }
    await refreshUnionPayCapabilities();
    await loadRefundQueue();
  })();
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
            <SectionTitle title="待退费患者" />
            <div class="charge-filter-toolbar mt-2">
              <div class="charge-filter-toolbar__main-col flex flex-col gap-2">
                <SelectFuzzyInput v-model="keyword" placeholder="姓名 / 业务号" full-width />
                <ElRadioGroup v-model="queueTabCode" class="charge-filter-toolbar__status">
                  <ElRadio :value="QUEUE_TAB.ALL">全部</ElRadio>
                  <ElRadio :value="QUEUE_TAB.PENDING">待退费</ElRadio>
                  <ElRadio :value="QUEUE_TAB.SETTLED">已退费</ElRadio>
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
          <div class="flex h-full min-h-0 flex-col gap-3 overflow-auto">
            <div class="patient-info-bar">
              <div class="patient-info-grid">
                <div class="patient-info-item">
                  <span class="patient-info-label">患者姓名</span>
                  <span class="patient-info-value">{{ currentQueueRow?.patientName || '—' }}</span>
                </div>
                <div class="patient-info-item">
                  <span class="patient-info-label">性别</span>
                  <span class="patient-info-value">{{ queueGenderDisplay(currentQueueRow) }}</span>
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
                    <template v-if="refundPatientFeeSummary">
                      待退 ¥{{ formatMoney(refundPatientFeeSummary.pendingRefundAmount) }} · 已退
                      ¥{{ formatMoney(refundPatientFeeSummary.refundedAmount) }}
                    </template>
                    <template v-else-if="tableLoading && selectedEncounterId">加载中…</template>
                    <template v-else>—</template>
                  </span>
                </div>
              </div>
            </div>
            <div class="flex-1">
              <div class="mb-2 flex flex-wrap items-center gap-2">
                <SectionTitle title="退费项目列表" />
                <div class="flex-1"></div>
                <ElButton
                  type="primary"
                  class="mr-1"
                  :disabled="
                    isSettledDetailView || !selectedEncounterId || selectedDetailRows.length === 0
                  "
                  :loading="openSettlementLoading"
                  @click="handleOpenSettlement"
                >
                  确认退费
                </ElButton>
              </div>
              <div class="charge-detail-wrapper">
                <RegisterStyleTableLayout
                  ref="detailTableLayoutRef"
                  :key="selectedEncounterId || 'no-encounter'"
                  class="!p-0"
                  :table-data="detailTableRows"
                  :loading="tableLoading"
                  :total="detailTableRows.length"
                  :query-params="detailTableQueryParams"
                  :table-columns="detailTableColumns"
                  :show-pagination="false"
                  :show-top-query="false"
                  :max-height="600"
                  row-key="chargeItemId"
                  :span-method="handleRefundDetailSpanMethod"
                  @selection-change="handleDetailSelectionChange"
                >
                  <template #statusCell="{ row }">
                    <template
                      v-for="st in [refundLineStatusTag(row)]"
                      :key="`${row.chargeItemId}-${st.label}`"
                    >
                      <ElTag :type="st.tagType" size="small" effect="plain">{{ st.label }}</ElTag>
                    </template>
                  </template>
                  <template #lineTotalAmount="{ row }">
                    ¥{{ formatMoney(row.lineTotalAmount) }}
                  </template>
                  <template #halfRefundCell="{ row }">
                    <ElTag
                      v-if="row.halfRefundOnPayment"
                      type="warning"
                      size="small"
                      effect="plain"
                    >
                      是
                    </ElTag>
                    <span v-else class="text-[var(--el-text-color-placeholder)]">—</span>
                  </template>
                </RegisterStyleTableLayout>
                <div class="ml-4 mt-2 text-sm text-[var(--el-text-color-regular)]">
                  已选 {{ selectedPaymentGroupCount }} 笔原支付、{{
                    selectedDetailRows.length
                  }}
                  项拟退，合计 ¥{{ formatMoney(selectedRefundingSum) }}
                  <span
                    v-if="selectedDetailRows.length > 0"
                    class="text-[var(--el-text-color-secondary)]"
                  >
                    （须整单退费，实退以原支付应退金额为准）
                  </span>
                </div>
              </div>
            </div>
          </div>
        </ElCard>
      </template>
    </StationTriPaneLayout>

    <ChargeSettlementDialog
      v-model="settlementVisible"
      dialog-title="确认退费"
      primary-button-text="确认退费"
      project-table-title="退费项目"
      receivable-amount-label="应退金额"
      :show-pay-channel="false"
      :loading="confirmRefundLoading"
      :display-amount="settlementSnapshot?.displayAmount ?? 0"
      :insurance-breakdown-items="settlementInsuranceBreakdown"
      :summary-insurance-amount="settlementSummaryInsurance"
      :summary-self-pay-amount="settlementSummarySelfPay"
      :previews="settlementSnapshot?.previews ?? []"
      :selected-charge-count="settlementSnapshot?.refundingChargeItemIds.length ?? 0"
      @confirm-charge="handleConfirmRefund"
    />
  </div>
</template>
