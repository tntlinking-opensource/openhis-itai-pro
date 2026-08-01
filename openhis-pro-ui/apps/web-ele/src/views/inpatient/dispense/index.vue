<script setup lang="ts">
import type { InpatientDispenseQueuePatient } from './components/types';
import type { SummaryMergedLineDisplayRow } from './services/map-summary-dispense-ui';

import type { ChargeType } from '#/api/core/common';
import type {
  EncounterMedicationDispenseSlipRowDto,
  PendingDispenseInpatientEncounterQueueRowDto,
  PractitionerOptionDto,
} from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-dispense.types';
import type {
  InpatientPharmacySummaryDispenseItemDto,
  InpatientPharmacySummarySlipHeaderDto,
} from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-summary-dispense.types';
import type {
  DispenseQueuePatient,
  DispenseRxTableRow,
} from '#/views/outpatient/dispense/components/types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import { ElMessage, ElMessageBox } from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  httpDispenseInpatientMedications,
  httpLoadInpatientPharmacyDispensePageInit,
  httpPageEncounterMedicationDispenseSlips,
  httpPagePendingDispenseInpatientEncounterQueue,
  httpSavePrepareInpatientMedicationDispense,
} from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-dispense.http';
import {
  httpDispenseSummaryMedications,
  httpGetMedicationSummaryDispenseDetail,
  httpPagePendingMedicationSummarySlips,
  httpRejectSummaryDispense,
} from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-summary-dispense.http';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  resolvePreparerSelection,
  writePreparerPreference,
} from '#/utils/medication/preparer-preference';
import {
  isDispenseScannedTraceInsufficient,
  loadRemainingSplitByTraceNos,
  splitTraceNoCommaString,
} from '#/utils/trace';
import DispenseRxCenter from '#/views/outpatient/dispense/components/dispense-rx-center.vue';
import { flattenPrescriptionBlocks } from '#/views/outpatient/dispense/components/dispense-rx-helpers';
import TraceScanDialog from '#/views/outpatient/dispense/components/trace-scan-dialog.vue';
import {
  isAlreadyPreparedDispenseStatus,
  isBatchDispenseActionableStatus,
  PENDING_DISPENSE_STATUS_FILTER,
} from '#/views/outpatient/dispense/services/dispense-batch-workflow';
import {
  resolveDispenseOperationSplitQty,
  resolveDispenseRequiredTraceCount,
} from '#/views/outpatient/dispense/services/resolve-dispense-trace-scan-inventory';

import InpatientDispenseModeSwitch from './components/inpatient-dispense-mode-switch.vue';
import InpatientDispenseQueuePanel from './components/inpatient-dispense-queue-panel.vue';
import InpatientDispenseSummaryCenter from './components/inpatient-dispense-summary-center.vue';
import InpatientDispenseSummaryQueuePanel from './components/inpatient-dispense-summary-queue-panel.vue';
import {
  mapInpatientPendingQueueRowToPatient,
  mapInpatientSlipRowsToPrescriptionBlocks,
} from './services/map-api-to-inpatient-dispense-ui';
import {
  buildSummaryDispenseTraceLines,
  buildSummaryMergedLineDisplayRows,
  buildSummarySlipListDateRange,
  groupSummaryDispenseItemsByPatient,
  mapSummaryMergedLinesToTraceLines,
  normalizeSummaryDispenseItems,
} from './services/map-summary-dispense-ui';

defineOptions({
  name: 'DispenseIp',
});

type DispenseMode = 'patient' | 'summary';

const SUMMARY_PAGE_SIZE = 100;

const dispenseMode = ref<DispenseMode>('summary');

const keyword = ref('');
const today = new Date().toISOString().slice(0, 10);
const queueDateRange = ref<[string, string] | null>([today, today]);
const selectedPatientId = ref<null | string>(null);
const activeRxKind = ref<'herbal' | 'western'>('western');
const selectedPrescriptionId = ref<null | string>(null);
const selectedDispenserId = ref<string | undefined>(undefined);
const selectedReceiverId = ref<string | undefined>(undefined);
const selectedWardId = ref<string | undefined>(undefined);

const rxCenterRef = ref<InstanceType<typeof DispenseRxCenter> | null>(null);

const traceScanVisible = ref(false);
const traceCodeByLineId = ref<Record<string, string>>({});
/** 住院侧无库房改选，保持与 DispenseRxCenter v-model 兼容 */
const inventoryPickByLineId = ref<Record<string, string>>({});
const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

const dispenserOptions = ref<{ label: string; value: string }[]>([]);
const receiverCandidateOptions = ref<{ label: string; value: string }[]>([]);
const wardOptions = ref<{ label: string; value: string }[]>([]);

const queueScope = ref<'completed' | 'pending'>('pending');

const queuePatients = ref<InpatientDispenseQueuePatient[]>([]);
const slipRows = ref<EncounterMedicationDispenseSlipRowDto[]>([]);

const summaryKeyword = ref('');
const summaryDateRange = ref<[string, string] | null>([today, today]);
const summaryScope = ref<'completed' | 'pending'>('pending');
const summarySlipRows = ref<InpatientPharmacySummarySlipHeaderDto[]>([]);
const summaryTotalCount = ref(0);
const selectedSummaryNo = ref<null | string>(null);
const summaryDetailHeader = ref<InpatientPharmacySummarySlipHeaderDto | null>(null);
const summaryMergedLines = ref<SummaryMergedLineDisplayRow[]>([]);
const summaryDispenseItems = ref<InpatientPharmacySummaryDispenseItemDto[]>([]);
const summaryDispenseCompleted = ref(false);
const summaryTraceCodeByLineId = ref<Record<string, string>>({});

const queueLoading = ref(false);
const slipLoading = ref(false);
const batchSubmitting = ref(false);
const summaryBatchSubmitting = ref(false);
const initLoading = ref(false);

const summaryListRequest = useRequest(httpPagePendingMedicationSummarySlips, { manual: true });
const summaryDetailRequest = useRequest(httpGetMedicationSummaryDispenseDetail, { manual: true });
const summaryDispenseRequest = useRequest(httpDispenseSummaryMedications, {
  manual: true,
  refreshOnStale: () => reloadSummarySlips(true),
});
const summaryRejectRequest = useRequest(httpRejectSummaryDispense, {
  manual: true,
  refreshOnStale: () => reloadSummarySlips(true),
});

/** 复用门诊处方区组件：映射为 DispenseQueuePatient */
const selectedPatientForRxCenter = computed<DispenseQueuePatient | null>(() => {
  const id = selectedPatientId.value;
  if (!id) return null;
  const p = queuePatients.value.find((row) => row.id === id);
  if (!p) return null;
  return {
    id: p.id,
    patientId: p.patientId,
    patientName: p.patientName,
    genderLabel: p.genderText,
    ageLabel: p.ageLabel,
    visitNo: p.hospitalizationNo,
    visitTimeLabel: p.admissionTimeLabel,
    // 费用性质：透传队列 contractDisplay，供发药单与左侧卡片同口径
    feeNatureLabel: p.feeNatureLabel,
    rxCount: 0,
  };
});

const filteredQueuePatients = computed(() => queuePatients.value);

/** 汇总发放明细按患者分组 */
const summaryPatientGroups = computed(() =>
  groupSummaryDispenseItemsByPatient(summaryDispenseItems.value),
);

/** 追溯码弹窗数据源（当前汇总单合并 Supply 行，不关联患者发放行） */
const summaryTraceDialogLines = computed(() =>
  mapSummaryMergedLinesToTraceLines(summaryMergedLines.value),
);

/** 领药人候选（护士列表；申请人不在列表时补入选项以便回显） */
const receiverOptions = computed(() => {
  const options = [...receiverCandidateOptions.value];
  const mergeReceiverOption = (id?: null | string, name?: null | string) => {
    const value = String(id ?? '').trim();
    if (!value || options.some((opt) => opt.value === value)) {
      return;
    }
    const label = String(name ?? '').trim();
    options.push({ value, label: label || value });
  };
  const header = summaryDetailHeader.value;
  mergeReceiverOption(header?.applicantId, header?.applicantName);
  const summaryNo = selectedSummaryNo.value;
  if (summaryNo) {
    const slipRow = summarySlipRows.value.find(
      (row) => String(row.busNo ?? '').trim() === summaryNo,
    );
    mergeReceiverOption(slipRow?.applicantId, slipRow?.applicantName);
  }
  return options;
});

const prescriptionBlocks = computed(() => {
  if (!selectedPatientId.value) return [];
  return mapInpatientSlipRowsToPrescriptionBlocks(slipRows.value);
});

const prescriptionList = computed(() => flattenPrescriptionBlocks(prescriptionBlocks.value));

const prescriptionListWithTrace = computed(() =>
  prescriptionList.value.map((row) => ({
    ...row,
    traceCode: traceCodeByLineId.value[row.id] ?? row.traceCode,
  })),
);

const displayTableRows = computed<DispenseRxTableRow[]>(() => {
  const rows = prescriptionListWithTrace.value;
  const out: DispenseRxTableRow[] = [];
  if (rows.length > 0) {
    out.push({ id: 'rx-gap-after-header', rowKind: 'spacer' });
  }
  rows.forEach((r, idx) => {
    out.push(r);
    if (r.isLastInRx && idx < rows.length - 1) {
      out.push({ id: `rx-gap-${r.rxId}-${idx}`, rowKind: 'spacer' });
    }
  });
  return out;
});

/** 进入页时拉初始化数据（调配药师、病区、发药药房等） */
async function loadPageInit() {
  initLoading.value = true;
  try {
    const res = await httpLoadInpatientPharmacyDispensePageInit({
      pageNo: 1,
      pageSize: 100,
    });
    const page = res.preparerCandidatePage;
    const { content: list } = parsePagedRowsFromApiData<PractitionerOptionDto>(page ?? {});
    dispenserOptions.value = list.map((p) => ({
      value: String(p.practitionerId),
      label: p.displayName?.trim() || String(p.practitionerId),
    }));
    const receiverPage = res.receiverCandidatePage;
    const { content: receiverList } = parsePagedRowsFromApiData<PractitionerOptionDto>(
      receiverPage ?? {},
    );
    receiverCandidateOptions.value = receiverList.map((p) => ({
      value: String(p.practitionerId),
      label: p.displayName?.trim() || String(p.practitionerId),
    }));
    // 优先回填当前用户上次选择的调配药师，否则默认首项
    if (!selectedDispenserId.value) {
      selectedDispenserId.value = resolvePreparerSelection(dispenserOptions.value);
    }
    wardOptions.value = (res.wardOptions ?? []).map((o) => ({
      value: String(o.value),
      label: o.label?.trim() || String(o.value),
    }));
  } catch {
    dispenserOptions.value = [];
    receiverCandidateOptions.value = [];
    wardOptions.value = [];
  } finally {
    initLoading.value = false;
  }
}

/** 用户变更调配药师时写入本地偏好，供下次进入页回填 */
watch(selectedDispenserId, (id) => {
  writePreparerPreference(id);
});

/** 按时间范围、病区、队列类型与关键字拉取在院患者队列 */
async function reloadQueue() {
  queueLoading.value = true;
  try {
    const range = queueDateRange.value;
    if (!range?.[0] || !range?.[1]) {
      queuePatients.value = [];
      return;
    }
    const [startDay, endDay] = range;
    const dispenseStatusEnumIn =
      queueScope.value === 'pending' ? [...PENDING_DISPENSE_STATUS_FILTER] : ['COMPLETED'];
    const res = await httpPagePendingDispenseInpatientEncounterQueue({
      startDate: `${startDay} 00:00:00`,
      endDate: `${endDay} 23:59:59`,
      keyword: keyword.value.trim() || undefined,
      wardId: selectedWardId.value ? preserveApiIdForRequest(selectedWardId.value) : undefined,
      dispenseStatusEnumIn,
      pageNo: 1,
      pageSize: 50,
    });
    const { content: rows } =
      parsePagedRowsFromApiData<PendingDispenseInpatientEncounterQueueRowDto>(res);
    queuePatients.value = rows.map((r) => mapInpatientPendingQueueRowToPatient(r));
  } catch {
    queuePatients.value = [];
  } finally {
    queueLoading.value = false;
  }
}

const debouncedReloadQueue = useDebounceFn(() => {
  void reloadQueue();
}, 400);

/** 当前就诊下的住院药品发放明细 */
async function reloadSlips() {
  const enc = selectedPatientId.value;
  if (!enc) {
    slipRows.value = [];
    return;
  }
  slipLoading.value = true;
  try {
    const dispenseStatusEnumIn =
      queueScope.value === 'pending' ? [...PENDING_DISPENSE_STATUS_FILTER] : ['COMPLETED'];
    const res = await httpPageEncounterMedicationDispenseSlips({
      encounterId: enc,
      tcmOnly: activeRxKind.value === 'herbal' ? true : undefined,
      westernOnly: activeRxKind.value === 'western' ? true : undefined,
      dispenseStatusEnumIn,
      pageNo: 1,
      pageSize: 200,
    });
    const { content: slipList } =
      parsePagedRowsFromApiData<EncounterMedicationDispenseSlipRowDto>(res);
    slipRows.value = slipList;
  } catch {
    slipRows.value = [];
  } finally {
    slipLoading.value = false;
  }
}

watch(filteredQueuePatients, (list) => {
  const sid = selectedPatientId.value;
  if (!sid) return;
  if (!list.some((p) => p.id === sid)) {
    selectedPatientId.value = null;
  }
});

function clearRxWorkbenchTransientState() {
  selectedPrescriptionId.value = null;
  traceCodeByLineId.value = {};
  inventoryPickByLineId.value = {};
  void nextTick(() => {
    rxCenterRef.value?.clearRxTableSelection();
  });
}

/**
 * 发药成功后仅清除本批已核发行的临时扫码。
 * 单独发药时保留其余待发行的批量扫码结果，避免一发全清。
 *
 * @param lineIds 本批已成功核发的发放行 id
 */
function clearTransientStateForDispensedLines(lineIds: string[]) {
  if (lineIds.length === 0) return;
  const idSet = new Set(lineIds);
  const nextTrace: Record<string, string> = {};
  // 仅保留未核发行的扫码回显
  for (const [id, code] of Object.entries(traceCodeByLineId.value)) {
    if (!idSet.has(id)) nextTrace[id] = code;
  }
  traceCodeByLineId.value = nextTrace;
}

watch(selectedPatientId, () => {
  // 换患者：清空扫码/勾选等临时态
  clearRxWorkbenchTransientState();
});

watch(activeRxKind, () => {
  // 切中西药 tab：只清勾选，保留另一 tab 已扫追溯码
  selectedPrescriptionId.value = null;
  void nextTick(() => {
    rxCenterRef.value?.clearRxTableSelection();
  });
});

watch(
  prescriptionList,
  (list) => {
    if (list.length === 0) {
      selectedPrescriptionId.value = null;
      return;
    }
    const cur = selectedPrescriptionId.value;
    if (!cur || !list.some((r) => r.rxId === cur)) {
      selectedPrescriptionId.value = list[0]?.rxId ?? null;
    }
  },
  { immediate: true },
);

watch(
  () => [selectedPatientId.value, activeRxKind.value, queueScope.value] as const,
  () => {
    void reloadSlips();
  },
);

watch(
  () => [queueDateRange.value, queueScope.value, selectedWardId.value] as const,
  () => {
    void reloadQueue();
  },
);

watch(keyword, () => {
  debouncedReloadQueue();
});

const debouncedReloadSummarySlips = useDebounceFn(() => {
  void reloadSummarySlips(true);
}, 400);

watch(summaryKeyword, () => {
  debouncedReloadSummarySlips();
});

watch(summaryScope, () => {
  selectedSummaryNo.value = null;
  summaryDetailHeader.value = null;
  summaryMergedLines.value = [];
  summaryDispenseItems.value = [];
  summaryDispenseCompleted.value = false;
  void reloadSummarySlips(true);
});

watch(
  () => summaryDateRange.value,
  () => {
    if (dispenseMode.value === 'summary') {
      void reloadSummarySlips(true);
    }
  },
);

watch(queueScope, () => {
  selectedPatientId.value = null;
  slipRows.value = [];
});

/** 切换发药模式时清空对侧选中态 */
watch(dispenseMode, (mode) => {
  if (mode === 'patient') {
    selectedSummaryNo.value = null;
    summaryDetailHeader.value = null;
    summaryMergedLines.value = [];
    summaryDispenseItems.value = [];
    summaryDispenseCompleted.value = false;
    summaryTraceCodeByLineId.value = {};
    return;
  }
  selectedPatientId.value = null;
  slipRows.value = [];
  traceCodeByLineId.value = {};
  void reloadSummarySlips(true);
});

watch(selectedSummaryNo, (summaryNo) => {
  if (!summaryNo) {
    summaryDetailHeader.value = null;
    summaryMergedLines.value = [];
    summaryDispenseItems.value = [];
    summaryDispenseCompleted.value = false;
    return;
  }
  void loadSummaryDetail(summaryNo);
});

/** 加载汇总单列表（IF-IPS-01）：待发 SUMMARIZED / 已发 COMPLETED（发放表） */
async function reloadSummarySlips(clearSelection = false) {
  const prevSelected = clearSelection ? null : selectedSummaryNo.value;
  const res = await summaryListRequest.run({
    pageNo: 1,
    pageSize: SUMMARY_PAGE_SIZE,
    keyword: summaryKeyword.value.trim() || undefined,
    summaryDispenseCompletedOnly: summaryScope.value === 'completed',
    ...buildSummarySlipListDateRange(summaryDateRange.value),
  });
  const { content: rows, totalCount } =
    parsePagedRowsFromApiData<InpatientPharmacySummarySlipHeaderDto>(res);
  summarySlipRows.value = rows;
  summaryTotalCount.value = totalCount;

  if (rows.length === 0) {
    selectedSummaryNo.value = null;
    summaryDetailHeader.value = null;
    summaryMergedLines.value = [];
    summaryDispenseItems.value = [];
    summaryDispenseCompleted.value = false;
    return;
  }

  const stillVisible =
    prevSelected && rows.some((r) => String(r.busNo ?? '').trim() === prevSelected);
  selectedSummaryNo.value = stillVisible
    ? prevSelected
    : String(rows[0]?.busNo ?? '').trim() || null;
}

/** 领药人默认取当前汇总单申请人 */
function applyReceiverDefaultFromApplicant(header: InpatientPharmacySummarySlipHeaderDto | null) {
  const applicantId = String(header?.applicantId ?? '').trim();
  if (!applicantId) {
    return;
  }
  selectedReceiverId.value = applicantId;
}

/** 加载选中汇总单详情（IF-IPS-02） */
async function loadSummaryDetail(summaryNo: string) {
  const res = await summaryDetailRequest.run({ summaryNo });
  summaryDetailHeader.value = res.header ?? null;
  const dispenseItems = normalizeSummaryDispenseItems(res.dispenseItems);
  summaryDispenseItems.value = dispenseItems;
  summaryDispenseCompleted.value = res.summaryDispenseCompleted === true;
  // 合并行批号由底层发放行聚合展示（合并 DTO 本身不含 lotNumber）
  summaryMergedLines.value = buildSummaryMergedLineDisplayRows(res.mergedLines, dispenseItems);
  applyReceiverDefaultFromApplicant(summaryDetailHeader.value);
}

/** 左侧汇总单卡片单击（已选中再次点击仍刷新详情） */
function handleSummarySelect(summaryNo: string) {
  if (selectedSummaryNo.value === summaryNo) {
    void loadSummaryDetail(summaryNo);
    return;
  }
  selectedSummaryNo.value = summaryNo;
}

/** 汇总模式打开追溯码弹窗 */
function handleSummaryBatchTraceScan() {
  if (summaryDispenseItems.value.length === 0) {
    ElMessage.warning('当前汇总单无发放明细');
    return;
  }
  traceScanVisible.value = true;
}

/** 汇总核对发药（当前选中单张汇总单） */
async function handleSummaryBatchDispense() {
  const summaryNo = selectedSummaryNo.value?.trim();
  if (!summaryNo) {
    ElMessage.warning('请先选择汇总单');
    return;
  }
  const summaryNos = [summaryNo];
  const preparerId = selectedDispenserId.value;
  const receiverId = selectedReceiverId.value;
  if (!preparerId) {
    ElMessage.warning('请选择调配药师');
    return;
  }
  if (!receiverId) {
    ElMessage.warning('请选择领药人');
    return;
  }

  try {
    await ElMessageBox.confirm(`确认对汇总单 ${summaryNo} 核对发药？`, '核对发药', {
      type: 'warning',
    });
  } catch {
    return;
  }

  summaryBatchSubmitting.value = true;
  try {
    if (!insuranceContractBusNo.value?.trim()) {
      ElMessage.warning('请选择医保契约');
      return;
    }
    const traceLines = buildSummaryDispenseTraceLines(summaryTraceCodeByLineId.value);

    const res = await summaryDispenseRequest.run({
      summaryNos,
      receiverId: preserveApiIdForRequest(receiverId) ?? receiverId,
      preparerPractitionerId: preserveApiIdForRequest(preparerId) ?? preparerId,
      traceLines: traceLines.length > 0 ? traceLines : undefined,
      contractBusNo: insuranceContractBusNo.value,
    });
    const message = res.message?.trim() || '核对发药成功';
    if (message.includes('EX-OPP-207')) {
      ElMessage.warning(message);
    } else {
      ElMessage.success(message);
    }
    summaryTraceCodeByLineId.value = {};
    await reloadSummarySlips(true);
  } catch {
    /* 拦截器提示 */
  } finally {
    summaryBatchSubmitting.value = false;
  }
}

/** 汇总驳回（当前选中单张汇总单） */
async function handleSummaryBatchReject() {
  const summaryNo = selectedSummaryNo.value?.trim();
  if (!summaryNo) {
    ElMessage.warning('请先选择汇总单');
    return;
  }
  const summaryNos = [summaryNo];

  try {
    await ElMessageBox.confirm(
      `确认驳回汇总单 ${summaryNo}？驳回后护士站可重新汇总。`,
      '驳回汇总单',
      { type: 'warning' },
    );
  } catch {
    return;
  }

  summaryBatchSubmitting.value = true;
  try {
    const res = await summaryRejectRequest.run({ summaryNos });
    ElMessage.success(res.message?.trim() || '驳回成功');
    await reloadSummarySlips(true);
  } catch {
    /* 拦截器提示 */
  } finally {
    summaryBatchSubmitting.value = false;
  }
}

/** 选中卡片切换当前就诊；再次点击已选患者仍刷新处方明细（避免 ref 不变导致 watch 不触发） */
function handleQueueCardSelect(patient: { id: string }) {
  const id = patient.id;
  if (selectedPatientId.value === id) {
    clearRxWorkbenchTransientState();
    void reloadSlips();
    return;
  }
  selectedPatientId.value = id;
}

/** 时间范围变更：清空时回退为当天 */
function handleQueueDateRangeChange(range: [string, string] | null) {
  if (range && range[0] && range[1]) {
    queueDateRange.value = range;
    return;
  }
  queueDateRange.value = [today, today];
}

function handleBatchTraceScan() {
  traceScanVisible.value = true;
}

/**
 * 发药前追溯码：可不扫；已扫但不够须确认后清码再提交（DD §7 / BR-OPP-015 同构）。
 *
 * @param lineIds 待发发放行 id
 */
async function confirmTraceCodesBeforeDispense(lineIds: string[]): Promise<boolean> {
  const rowById = new Map(prescriptionList.value.map((row) => [row.id, row]));
  // 先汇总本批已扫码，一次反查主档剩余（DD §3 / §7 盖住口径）
  const allCodes: string[] = [];
  for (const id of lineIds) {
    const row = rowById.get(id);
    if (!row) continue;
    const traceRaw = String(traceCodeByLineId.value[id] ?? row.traceCode ?? '').trim();
    if (!traceRaw || traceRaw === '—') continue;
    allCodes.push(...splitTraceNoCommaString(traceRaw));
  }
  const remainingByTraceNo = await loadRemainingSplitByTraceNos(allCodes);

  const insufficient: string[] = [];
  const insufficientIds: string[] = [];
  for (const id of lineIds) {
    const row = rowById.get(id);
    if (!row) continue;
    const needCount = resolveDispenseRequiredTraceCount(row);
    const opSplit = resolveDispenseOperationSplitQty(row);
    const traceRaw = String(traceCodeByLineId.value[id] ?? row.traceCode ?? '').trim();
    // 优先剩余盖住；缺数据回落个数
    if (
      !isDispenseScannedTraceInsufficient({
        traceRaw,
        operationSplitQty: opSplit,
        needCount,
        remainingByTraceNo,
      })
    ) {
      continue;
    }
    const boundCount = splitTraceNoCommaString(traceRaw).length;
    insufficientIds.push(id);
    insufficient.push(
      `「${row.itemName}」已扫码剩余未盖住发药拆零（建议约 ${needCount} 码，当前 ${boundCount} 个）`,
    );
  }
  if (insufficient.length === 0) {
    return true;
  }
  try {
    await ElMessageBox.confirm(
      `${insufficient.join('\n')}\n\n确认后将按「未扫够」继续发药（不足行的追溯码不提交）。`,
      '追溯码覆盖不足',
      {
        type: 'warning',
        confirmButtonText: '仍继续发药',
        cancelButtonText: '返回补扫',
      },
    );
    // 确认继续：清掉不足行已绑码，禁止带着盖不住码串写 OUT
    const dropIds = new Set(insufficientIds);
    const next: Record<string, string> = {};
    for (const [id, code] of Object.entries(traceCodeByLineId.value)) {
      if (!dropIds.has(id)) next[id] = code;
    }
    traceCodeByLineId.value = next;
    return true;
  } catch {
    return false;
  }
}

/** 保存配药并发药：住院侧仅提交 dispenseId 与追溯码，不改库房/批号/数量 */
async function handleBatchDispense() {
  const enc = selectedPatientId.value;
  const preparerId = selectedDispenserId.value;
  if (!enc) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!preparerId) {
    ElMessage.warning('请选择调配药师');
    return;
  }
  const selectedIds = rxCenterRef.value?.getSelectedDispenseLineIds() ?? [];
  if (selectedIds.length === 0) {
    ElMessage.warning('请先勾选处方');
    return;
  }
  const slipById = new Map(slipRows.value.map((r) => [r.dispenseId, r]));
  const actionable = selectedIds.filter((id) =>
    isBatchDispenseActionableStatus(slipById.get(id)?.statusEnum),
  );
  if (actionable.length === 0) {
    ElMessage.warning('所选处方无可发药明细');
    return;
  }
  // 半扫确认：不足行清码后再走主流程
  if (!(await confirmTraceCodesBeforeDispense(actionable))) {
    return;
  }
  try {
    await ElMessageBox.confirm('确认对所选处方执行保存配药并发药？', '批量发药', {
      type: 'warning',
    });
  } catch {
    return;
  }
  batchSubmitting.value = true;
  try {
    if (!insuranceContractBusNo.value?.trim()) {
      ElMessage.warning('请选择医保契约');
      return;
    }
    const prepLines: { dispenseId: string; traceNo?: string }[] = [];
    const alreadyPreparedIds: string[] = [];
    for (const id of actionable) {
      const row = slipById.get(id);
      if (!row) continue;
      if (isAlreadyPreparedDispenseStatus(row.statusEnum)) {
        alreadyPreparedIds.push(id);
        continue;
      }
      prepLines.push({
        dispenseId: id,
        traceNo: traceCodeByLineId.value[id] || row.traceNo || undefined,
      });
    }
    if (prepLines.length > 0) {
      await httpSavePrepareInpatientMedicationDispense({
        encounterId: enc,
        preparerPractitionerId: preserveApiIdForRequest(preparerId) ?? preparerId,
        lines: prepLines,
      });
      await reloadSlips();
    }
    const dispenseIds = [
      ...new Set([...alreadyPreparedIds, ...prepLines.map((l) => l.dispenseId)]),
    ];
    const res = await httpDispenseInpatientMedications({
      encounterId: enc,
      contractBusNo: insuranceContractBusNo.value,
      lines: dispenseIds.map((dispenseId) => ({ dispenseId })),
    });
    const message = res.message?.trim() || '发药提交成功';
    if (message.includes('EX-OPP-207')) {
      ElMessage.warning(message);
    } else {
      ElMessage.success(message);
    }
    // 只清本批已发，保留同患者其余待发行的批量扫码
    clearTransientStateForDispensedLines(dispenseIds);
    await reloadSlips();
    await reloadQueue();
    await nextTick(() => {
      rxCenterRef.value?.clearRxTableSelection();
    });
  } catch {
    await reloadSlips();
    await reloadQueue();
  } finally {
    batchSubmitting.value = false;
  }
}

onMounted(async () => {
  try {
    const chargeTypes = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(chargeTypes);
    insuranceChargeTypeOptions.value = eligible;
    insuranceContractBusNo.value = resolveDefaultInsuranceBusNo(eligible);
  } catch {
    insuranceContractBusNo.value = undefined;
    insuranceChargeTypeOptions.value = [];
  }
  await loadPageInit();
  await (dispenseMode.value === 'summary' ? reloadSummarySlips(true) : reloadQueue());
});
</script>

<template>
  <div
    v-loading="initLoading"
    class="pharmacy-dispense-inpatient-root flex h-[calc(100vh-120px)] min-h-[480px] w-full min-w-0 flex-col overflow-hidden"
  >
    <StationTriPaneLayout
      :show-right="false"
      :show-footer="false"
      class="pharmacy-dispense-inpatient min-h-0 min-w-0 flex-1"
      left-track="minmax(240px, 264px)"
      left-track-lg="minmax(240px, 264px)"
      center-track="minmax(0, 1fr)"
    >
      <template #left>
        <div class="flex h-full min-h-0 flex-col">
          <InpatientDispenseModeSwitch v-model:dispense-mode="dispenseMode" />
          <div
            v-if="insuranceChargeTypeOptions.length > 0"
            class="border-b border-solid border-[var(--el-border-color-lighter)] px-2 pb-2"
          >
            <div class="flex min-w-0 items-center gap-1.5">
              <span class="shrink-0 text-xs text-[var(--el-text-color-secondary)]">医保契约</span>
              <ElSelect
                v-model="insuranceContractBusNo"
                class="!min-w-0 flex-1"
                placeholder="请选择医保契约"
                filterable
                clearable
              >
                <ElOption
                  v-for="item in insuranceChargeTypeOptions"
                  :key="item.busNo"
                  :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
                  :value="item.busNo"
                />
              </ElSelect>
            </div>
          </div>
          <div class="min-h-0 flex-1 overflow-hidden">
            <InpatientDispenseQueuePanel
              v-if="dispenseMode === 'patient'"
              v-model:keyword="keyword"
              v-model:queue-date-range="queueDateRange"
              v-model:queue-scope="queueScope"
              v-model:selected-ward-id="selectedWardId"
              :filtered-patients="filteredQueuePatients"
              :loading-queue="queueLoading"
              :selected-patient-id="selectedPatientId"
              :ward-options="wardOptions"
              @queue-date-range-change="handleQueueDateRangeChange"
              @refresh="reloadQueue"
              @select-patient="handleQueueCardSelect"
            />
            <InpatientDispenseSummaryQueuePanel
              v-else
              v-model:keyword="summaryKeyword"
              v-model:summary-date-range="summaryDateRange"
              v-model:summary-scope="summaryScope"
              :slip-rows="summarySlipRows"
              :total-count="summaryTotalCount"
              :loading="summaryListRequest.loading.value"
              :selected-summary-no="selectedSummaryNo"
              @refresh="reloadSummarySlips(false)"
              @select-summary="handleSummarySelect"
            />
          </div>
        </div>
      </template>

      <template #center>
        <DispenseRxCenter
          v-if="dispenseMode === 'patient'"
          ref="rxCenterRef"
          v-model:active-rx-kind="activeRxKind"
          v-model:inventory-pick-by-line-id="inventoryPickByLineId"
          v-model:selected-dispenser-id="selectedDispenserId"
          v-model:selected-prescription-id="selectedPrescriptionId"
          :batch-dispense-disabled="queueScope === 'completed' || batchSubmitting || slipLoading"
          :enable-sheet-view="false"
          :selected-patient="selectedPatientForRxCenter"
          :prescription-blocks="prescriptionBlocks"
          :display-table-rows="displayTableRows"
          :prescription-row-count="prescriptionList.length"
          :dispenser-options="dispenserOptions"
          @batch-dispense="handleBatchDispense"
          @batch-trace-scan="handleBatchTraceScan"
        />
        <InpatientDispenseSummaryCenter
          v-else
          v-model:selected-dispenser-id="selectedDispenserId"
          v-model:selected-receiver-id="selectedReceiverId"
          :batch-submitting="summaryBatchSubmitting"
          :detail-loading="summaryDetailRequest.loading.value"
          :dispenser-options="dispenserOptions"
          :read-only="summaryDispenseCompleted"
          :receiver-options="receiverOptions"
          :header="summaryDetailHeader"
          :merged-lines="summaryMergedLines"
          :patient-groups="summaryPatientGroups"
          :selected-summary-no="selectedSummaryNo"
          :trace-code-by-line-id="summaryTraceCodeByLineId"
          @batch-dispense="handleSummaryBatchDispense"
          @batch-reject="handleSummaryBatchReject"
          @batch-trace-scan="handleSummaryBatchTraceScan"
        />
      </template>
    </StationTriPaneLayout>

    <TraceScanDialog
      v-if="dispenseMode === 'patient'"
      v-model="traceScanVisible"
      v-model:trace-by-line-id="traceCodeByLineId"
      :lines="prescriptionList"
      :inventory-pick-by-line-id="inventoryPickByLineId"
      :patient-name="selectedPatientForRxCenter?.patientName ?? ''"
    />
    <TraceScanDialog
      v-else
      v-model="traceScanVisible"
      v-model:trace-by-line-id="summaryTraceCodeByLineId"
      :lines="summaryTraceDialogLines"
      :default-location-id="summaryDetailHeader?.purposeLocationId ?? ''"
      :patient-name="summaryDetailHeader?.applicantName ?? ''"
    />
  </div>
</template>
