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
  DispensePrescriptionBlock,
  DispensePrescriptionItemRow,
  DispenseRxKindBadgeCounts,
  DispenseRxTableRow,
} from '#/views/outpatient/dispense/components/types';

import { computed, nextTick, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue';

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
import { getDateRangeQuick } from '#/utils/format/date-range-quick';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  DISPENSE_TRACE_CACHE_SCENE,
  readDispenseTraceCache,
  removeDispenseTraceCacheLines,
  writeDispenseTraceCache,
} from '#/utils/medication/dispense-trace-cache';
import {
  resolvePreparerSelection,
  writePreparerPreference,
} from '#/utils/medication/preparer-preference';
import {
  isDispenseScannedTraceInsufficient,
  loadRemainingSplitByTraceNos,
  splitTraceNoCommaString,
} from '#/utils/trace';
import {
  INPATIENT_DISPENSE_DEFAULT_MODE,
  type InpatientDispenseMode,
} from '#/views/inpatient/dispense/constants/inpatient-dispense-mode.config';
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
  computeInpatientRxKindSummaryFromSlips,
  filterInpatientSlipRowsByRxKind,
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

type DispenseMode = InpatientDispenseMode;

const SUMMARY_PAGE_SIZE = 100;

const dispenseMode = ref<DispenseMode>(INPATIENT_DISPENSE_DEFAULT_MODE);

const keyword = ref('');
/** 默认近半个月（含今天） */
const defaultDispenseDateRange = (): [string, string] =>
  getDateRangeQuick({ strategy: 'rolling-days', spanDays: 15 });
const queueDateRange = ref<[string, string] | null>(defaultDispenseDateRange());
const selectedPatientId = ref<null | string>(null);
const activeRxKind = ref<'herbal' | 'western'>('western');
const selectedPrescriptionId = ref<null | string>(null);
const selectedDispenserId = ref<string | undefined>(undefined);
const selectedReceiverId = ref<string | undefined>(undefined);
const selectedWardId = ref<string | undefined>(undefined);

const rxCenterRef = ref<InstanceType<typeof DispenseRxCenter> | null>(null);

const traceScanVisible = ref(false);
/** 批量扫码弹窗明细：打开时按当前勾选发药单截取，避免弹窗内仍展示未选笺 */
const traceScanDialogLines = ref<DispensePrescriptionItemRow[]>([]);
const traceCodeByLineId = ref<Record<string, string>>({});
/** 住院侧无库房改选，保持与 DispenseRxCenter v-model 兼容 */
const inventoryPickByLineId = ref<Record<string, string>>({});
const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

/** 当前就诊全量发放行（含中西药；笺面与角标用） */
const allSlipRowsForEncounter = ref<EncounterMedicationDispenseSlipRowDto[]>([]);
/** 发药单笺面块（按日虚拟；与门诊 sheetViewBlocks 同构） */
const sheetViewBlocks = ref<DispensePrescriptionBlock[]>([]);
const sheetLoading = ref(false);
const rxKindBadgeCounts = ref<DispenseRxKindBadgeCounts>({ western: 0, herbal: 0 });

const INPATIENT_TRACE_CACHE_SCENE = DISPENSE_TRACE_CACHE_SCENE.inpatient;

const dispenserOptions = ref<{ label: string; value: string }[]>([]);
const receiverCandidateOptions = ref<{ label: string; value: string }[]>([]);
const wardOptions = ref<{ label: string; value: string }[]>([]);

const queueScope = ref<'completed' | 'pending'>('pending');

const queuePatients = ref<InpatientDispenseQueuePatient[]>([]);
const slipRows = ref<EncounterMedicationDispenseSlipRowDto[]>([]);

const summaryKeyword = ref('');
const summaryDateRange = ref<[string, string] | null>(defaultDispenseDateRange());
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

/** 汇总扫码弹窗默认发放库房（单头 purposeLocationId，防 Long 精度丢失） */
const summaryTraceDefaultLocationId = computed(
  () => preserveApiIdForRequest(summaryDetailHeader.value?.purposeLocationId) ?? '',
);

/** 追溯码弹窗数据源（当前汇总单合并 Supply 行，不关联患者发放行） */
const summaryTraceDialogLines = computed(() =>
  mapSummaryMergedLinesToTraceLines(summaryMergedLines.value, summaryTraceDefaultLocationId.value),
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

/** 当前就诊下的住院药品发放明细（单次拉全量，再按中西药 tab 过滤列表） */
async function reloadEncounterSlipsFromServer(resetRxKind = false) {
  const enc = selectedPatientId.value;
  if (!enc) {
    allSlipRowsForEncounter.value = [];
    slipRows.value = [];
    rxKindBadgeCounts.value = { western: 0, herbal: 0 };
    return;
  }
  slipLoading.value = true;
  try {
    const dispenseStatusEnumIn =
      queueScope.value === 'pending' ? [...PENDING_DISPENSE_STATUS_FILTER] : ['COMPLETED'];
    const res = await httpPageEncounterMedicationDispenseSlips({
      encounterId: enc,
      dispenseStatusEnumIn,
      pageNo: 1,
      pageSize: 200,
    });
    const { content: slipList } =
      parsePagedRowsFromApiData<EncounterMedicationDispenseSlipRowDto>(res);
    allSlipRowsForEncounter.value = slipList;
    const summary = computeInpatientRxKindSummaryFromSlips(slipList);
    rxKindBadgeCounts.value = summary.badgeCounts;
    // 换患者时按数据推断默认中西药 tab
    if (resetRxKind && summary.defaultKind !== activeRxKind.value) {
      activeRxKind.value = summary.defaultKind;
    } else {
      slipRows.value = filterInpatientSlipRowsByRxKind(slipList, activeRxKind.value);
    }
  } catch {
    allSlipRowsForEncounter.value = [];
    slipRows.value = [];
    rxKindBadgeCounts.value = { western: 0, herbal: 0 };
  } finally {
    slipLoading.value = false;
  }
}

/**
 * 装配发药单笺面：复用已拉全量 slip，按日虚拟分单（与门诊处方笺同构）。
 */
function loadDaySheetBlocksForSheet(): boolean {
  const enc = selectedPatientId.value;
  if (!enc) {
    sheetViewBlocks.value = [];
    return false;
  }
  sheetLoading.value = true;
  try {
    sheetViewBlocks.value = mapInpatientSlipRowsToPrescriptionBlocks(allSlipRowsForEncounter.value);
    // 队列性别若空：用 slip 回填的 genderEnumText 补偿笺面
    const genderText = allSlipRowsForEncounter.value
      .map((r) => r.genderEnumText?.trim() ?? '')
      .find(Boolean);
    if (genderText) {
      const queueRow = queuePatients.value.find((p) => p.id === enc);
      if (queueRow && !queueRow.genderText?.trim()) {
        queueRow.genderText = genderText;
      }
    }
    return sheetViewBlocks.value.length > 0;
  } catch {
    sheetViewBlocks.value = [];
    ElMessage.error('加载发药单失败');
    return false;
  } finally {
    sheetLoading.value = false;
  }
}

/** @deprecated 兼容旧调用名：等价于 reloadEncounterSlipsFromServer(false) */
async function reloadSlips() {
  await reloadEncounterSlipsFromServer(false);
  loadDaySheetBlocksForSheet();
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

/** 将当前内存扫码写入指定就诊的 session 缓存 */
function persistDispenseTraceCacheForEncounter(encounterId: string): void {
  const enc = String(encounterId ?? '').trim();
  if (!enc) return;
  writeDispenseTraceCache({
    scene: INPATIENT_TRACE_CACHE_SCENE,
    encounterId: enc,
    snap: {
      traceCodeByLineId: { ...traceCodeByLineId.value },
    },
  });
}

/** 从 session 恢复某就诊的扫码到内存 */
function restoreDispenseTraceCacheForEncounter(encounterId: string): void {
  const enc = String(encounterId ?? '').trim();
  if (!enc) return;
  const snap = readDispenseTraceCache(INPATIENT_TRACE_CACHE_SCENE, enc);
  if (!snap) return;
  traceCodeByLineId.value = { ...snap.traceCodeByLineId };
}

/** 当前就诊扫码变更时防抖落盘 */
const debouncedPersistCurrentDispenseTrace = useDebounceFn(() => {
  const enc = String(selectedPatientId.value ?? '').trim();
  if (!enc) return;
  // 空态不落盘：换患者清空内存时勿误删新就诊缓存
  if (Object.keys(traceCodeByLineId.value).length === 0) return;
  persistDispenseTraceCacheForEncounter(enc);
}, 300);

/**
 * 发药成功后仅清除本批已核发行的临时扫码。
 * 单独发药时保留其余待发行的批量扫码结果，避免一发全清。
 *
 * @param lineIds 本批已成功核发的发放行 id
 */
function clearTransientStateForDispensedLines(lineIds: string[]) {
  if (lineIds.length === 0) return;
  const idSet = new Set(lineIds);
  // 合并展示行会话键为首条 dispenseId，已含在展开后的 lineIds 中
  const nextTrace: Record<string, string> = {};
  // 仅保留未核发行的扫码回显
  for (const [id, code] of Object.entries(traceCodeByLineId.value)) {
    if (!idSet.has(id)) nextTrace[id] = code;
  }
  traceCodeByLineId.value = nextTrace;
  const enc = String(selectedPatientId.value ?? '').trim();
  if (enc) {
    removeDispenseTraceCacheLines({
      scene: INPATIENT_TRACE_CACHE_SCENE,
      encounterId: enc,
      lineIds,
    });
  }
}

watch(selectedPatientId, (enc, prevEnc) => {
  const prev = String(prevEnc ?? '').trim();
  if (prev) {
    persistDispenseTraceCacheForEncounter(prev);
  }
  // 换患者：清空扫码/勾选等临时态
  clearRxWorkbenchTransientState();
  const next = String(enc ?? '').trim();
  if (next) {
    restoreDispenseTraceCacheForEncounter(next);
  }
});

watch(
  traceCodeByLineId,
  () => {
    debouncedPersistCurrentDispenseTrace();
  },
  { deep: true },
);

watch(activeRxKind, () => {
  // 切中西药 tab：只清勾选，保留另一 tab 已扫追溯码；列表从全量缓存过滤，不重打接口
  selectedPrescriptionId.value = null;
  if (selectedPatientId.value) {
    slipRows.value = filterInpatientSlipRowsByRxKind(
      allSlipRowsForEncounter.value,
      activeRxKind.value,
    );
  }
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
  () => [selectedPatientId.value, queueScope.value] as const,
  async ([enc, scope], old) => {
    if (!enc) {
      allSlipRowsForEncounter.value = [];
      slipRows.value = [];
      rxKindBadgeCounts.value = { western: 0, herbal: 0 };
      sheetViewBlocks.value = [];
      return;
    }
    const [oldEnc, oldScope] = old ?? [null, scope];
    const encounterChanged = enc !== oldEnc || scope !== oldScope;
    await reloadEncounterSlipsFromServer(encounterChanged);
    // 发药单为默认视图：换患者/队列范围后同步笺面
    loadDaySheetBlocksForSheet();
  },
);

watch(
  () => [queueDateRange.value, selectedWardId.value] as const,
  () => {
    // 日期/病区变更：刷新左侧患者队列（待发/已发由 queueScope watch 单独拉）
    if (dispenseMode.value !== 'patient') return;
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

/** 待发药 / 已发药 Tab：清空选中并立刻重拉患者队列 */
watch(queueScope, () => {
  selectedPatientId.value = null;
  allSlipRowsForEncounter.value = [];
  slipRows.value = [];
  sheetViewBlocks.value = [];
  activeRxKind.value = 'western';
  rxKindBadgeCounts.value = { western: 0, herbal: 0 };
  if (dispenseMode.value === 'patient') {
    void reloadQueue();
  }
});

/** 切换发药模式时清空对侧选中态，并拉对应侧列表 */
watch(dispenseMode, (mode) => {
  if (mode === 'patient') {
    selectedSummaryNo.value = null;
    summaryDetailHeader.value = null;
    summaryMergedLines.value = [];
    summaryDispenseItems.value = [];
    summaryDispenseCompleted.value = false;
    summaryTraceCodeByLineId.value = {};
    // 从汇总切回按患者：队列可能从未拉过或已过期，必须刷新
    void reloadQueue();
    return;
  }
  selectedPatientId.value = null;
  allSlipRowsForEncounter.value = [];
  slipRows.value = [];
  sheetViewBlocks.value = [];
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

/** 选中卡片切换当前就诊；再次点击已选患者仍刷新发药单明细（避免 ref 不变导致 watch 不触发） */
function handleQueueCardSelect(patient: { id: string }) {
  const id = patient.id;
  if (selectedPatientId.value === id) {
    // 再点同一人：刷新明细，扫码先落盘再恢复，避免误触清空白扫
    persistDispenseTraceCacheForEncounter(id);
    clearRxWorkbenchTransientState();
    restoreDispenseTraceCacheForEncounter(id);
    void (async () => {
      await reloadEncounterSlipsFromServer(false);
      loadDaySheetBlocksForSheet();
    })();
    return;
  }
  selectedPatientId.value = id;
}

/** 切菜单 / 停用前落盘当前就诊扫码 */
function flushCurrentDispenseTraceCache(): void {
  const enc = String(selectedPatientId.value ?? '').trim();
  if (!enc) return;
  persistDispenseTraceCacheForEncounter(enc);
}

onBeforeUnmount(() => {
  flushCurrentDispenseTraceCache();
});

onDeactivated(() => {
  flushCurrentDispenseTraceCache();
});

/** 时间范围变更：清空时回退为近半个月 */
function handleQueueDateRangeChange(range: [string, string] | null) {
  if (range && range[0] && range[1]) {
    queueDateRange.value = range;
    return;
  }
  queueDateRange.value = defaultDispenseDateRange();
}

/**
 * 批量扫描追溯码：药品明细随当前勾选发药单走（笺面勾选 / 列表明细勾选同构）。
 */
function handleBatchTraceScan() {
  const selectedIds = rxCenterRef.value?.getSelectedDispenseLineIds() ?? [];
  if (selectedIds.length === 0) {
    ElMessage.warning('请先勾选发药单');
    return;
  }
  const selected = new Set(selectedIds);
  const lines = prescriptionList.value.filter((row) => {
    const sourceIds =
      row.sourceDispenseIds && row.sourceDispenseIds.length > 0 ? row.sourceDispenseIds : [row.id];
    return sourceIds.some((id) => selected.has(id));
  });
  if (lines.length === 0) {
    ElMessage.warning('所选发药单暂无药品明细');
    return;
  }
  traceScanDialogLines.value = lines;
  traceScanVisible.value = true;
}

/**
 * 发药前追溯码：可不扫；已扫但不够须确认后清码再提交（DD §7 / BR-OPP-015 同构）。
 * 住院同医嘱合并展示行：按展示行校验一次应扫量，不足时清展示行会话码。
 *
 * @param lineIds 待发发放行 id（已展开 sourceDispenseIds）
 */
async function confirmTraceCodesBeforeDispense(lineIds: string[]): Promise<boolean> {
  // 展开后的 dispenseId → 展示行（合并行用首条 id 存扫码会话）
  const displayRowByDispenseId = new Map<string, (typeof prescriptionList.value)[number]>();
  for (const row of prescriptionList.value) {
    const sourceIds =
      row.sourceDispenseIds && row.sourceDispenseIds.length > 0 ? row.sourceDispenseIds : [row.id];
    for (const sid of sourceIds) {
      displayRowByDispenseId.set(sid, row);
    }
  }

  // 同一展示行只校验一次（合并数量对应一次应扫量）
  const uniqueDisplayRows: (typeof prescriptionList.value)[number][] = [];
  const seenDisplayIds = new Set<string>();
  for (const id of lineIds) {
    const row = displayRowByDispenseId.get(id);
    if (!row || seenDisplayIds.has(row.id)) continue;
    seenDisplayIds.add(row.id);
    uniqueDisplayRows.push(row);
  }

  // 先汇总本批已扫码，一次反查主档剩余（DD §3 / §7 盖住口径）
  const allCodes: string[] = [];
  for (const row of uniqueDisplayRows) {
    const traceRaw = String(traceCodeByLineId.value[row.id] ?? row.traceCode ?? '').trim();
    if (!traceRaw || traceRaw === '—') continue;
    allCodes.push(...splitTraceNoCommaString(traceRaw));
  }
  const remainingByTraceNo = await loadRemainingSplitByTraceNos(allCodes);

  const insufficient: string[] = [];
  const insufficientDisplayIds: string[] = [];
  for (const row of uniqueDisplayRows) {
    const needCount = resolveDispenseRequiredTraceCount(row);
    const opSplit = resolveDispenseOperationSplitQty(row);
    const traceRaw = String(traceCodeByLineId.value[row.id] ?? row.traceCode ?? '').trim();
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
    insufficientDisplayIds.push(row.id);
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
    // 确认继续：清掉不足展示行已绑码，禁止带着盖不住码串写 OUT
    const dropIds = new Set(insufficientDisplayIds);
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

/**
 * 合并展示行上的逗号追溯码按序拆到各底层 dispenseId（一码一行；码不足则后续行不带码）。
 *
 * @param displayLineId 展示行 id（扫码会话键，通常为首条 dispenseId）
 * @param sourceDispenseIds 同医嘱同日合并的发放主键（有序）
 * @param targetDispenseId 当前提交的发放主键
 */
function resolveDistributedTraceNoForDispense(
  displayLineId: string,
  sourceDispenseIds: string[],
  targetDispenseId: string,
): string | undefined {
  const raw = String(traceCodeByLineId.value[displayLineId] ?? '').trim();
  if (!raw || raw === '—') return undefined;
  const codes = splitTraceNoCommaString(raw);
  if (codes.length === 0) return undefined;
  const idx = sourceDispenseIds.indexOf(targetDispenseId);
  if (idx < 0 || idx >= codes.length) return undefined;
  return codes[idx];
}

/**
 * 先保存配药再发药：PREPARATION 先配药；IN_PROGRESS/PREPARED 可直接重试发药。
 * 住院侧仅提交 dispenseId 与追溯码，不改库房/批号/数量。
 *
 * @param lineIds 待发发放行（已展开 sourceDispenseIds）；空则提示勾选
 * @param confirmTitle 确认框标题
 */
async function executeDispenseForLineIds(
  lineIds: string[],
  confirmTitle = '批量发药',
): Promise<void> {
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
  if (lineIds.length === 0) {
    ElMessage.warning('请先勾选发药单');
    return;
  }
  // 状态门禁用全量缓存，避免笺面勾选跨 tab 时漏查
  const slipById = new Map(allSlipRowsForEncounter.value.map((r) => [r.dispenseId, r]));
  const actionable = lineIds.filter((id) =>
    isBatchDispenseActionableStatus(slipById.get(id)?.statusEnum),
  );
  if (actionable.length === 0) {
    ElMessage.warning('所选发药单无可发药明细');
    return;
  }
  // 半扫确认：不足行清码后再走主流程
  if (!(await confirmTraceCodesBeforeDispense(actionable))) {
    return;
  }
  try {
    await ElMessageBox.confirm(`确认对所选发药单执行保存配药并发药？`, confirmTitle, {
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
    // 展示行 → 有序 sourceDispenseIds：合并行追溯码按序拆分（笺面+明细同源）
    const sourceIdsByDisplayId = new Map<string, string[]>();
    const displayIdByDispenseId = new Map<string, string>();
    for (const row of prescriptionList.value) {
      const sourceIds =
        row.sourceDispenseIds && row.sourceDispenseIds.length > 0
          ? row.sourceDispenseIds
          : [row.id];
      sourceIdsByDisplayId.set(row.id, sourceIds);
      for (const sid of sourceIds) {
        displayIdByDispenseId.set(sid, row.id);
      }
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
      const displayId = displayIdByDispenseId.get(id) ?? id;
      const sourceIds = sourceIdsByDisplayId.get(displayId) ?? [id];
      // 合并行：展示会话码按序落到各底层发放行
      const distributed = resolveDistributedTraceNoForDispense(displayId, sourceIds, id);
      prepLines.push({
        dispenseId: id,
        traceNo: distributed || row.traceNo || undefined,
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

/** 批量发药：取当前勾选（笺面多选或列表明细） */
async function handleBatchDispense() {
  const selectedIds = rxCenterRef.value?.getSelectedDispenseLineIds() ?? [];
  await executeDispenseForLineIds(selectedIds, '批量发药');
}

/** 发药单视图：按单张日发药单发药 */
async function handleSingleDaySheetDispense(rxId: string) {
  // 当前 tab 下列表明细与笺面同源；合并行展开 sourceDispenseIds
  const lineIds = prescriptionList.value
    .filter((row) => row.rxId === rxId)
    .flatMap((row) =>
      row.sourceDispenseIds && row.sourceDispenseIds.length > 0 ? row.sourceDispenseIds : [row.id],
    );
  if (lineIds.length === 0) {
    ElMessage.warning('当前发药单无可发药明细');
    return;
  }
  await executeDispenseForLineIds(lineIds, '单独发药');
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
          :enable-sheet-view="true"
          group-no-column-label="发药单"
          sheet-no-label="发药单"
          :print-prescriptions-disabled="true"
          :selected-patient="selectedPatientForRxCenter"
          :prescription-blocks="prescriptionBlocks"
          :prescription-sheet-blocks="sheetViewBlocks"
          :sheet-loading="sheetLoading"
          :trace-code-by-line-id="traceCodeByLineId"
          :display-table-rows="displayTableRows"
          :prescription-row-count="prescriptionList.length"
          :dispenser-options="dispenserOptions"
          :rx-kind-badge-counts="rxKindBadgeCounts"
          @batch-dispense="handleBatchDispense"
          @batch-trace-scan="handleBatchTraceScan"
          @dispense-prescription="handleSingleDaySheetDispense"
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
      :lines="traceScanDialogLines"
      :inventory-pick-by-line-id="inventoryPickByLineId"
      :patient-name="selectedPatientForRxCenter?.patientName ?? ''"
    />
    <TraceScanDialog
      v-else
      v-model="traceScanVisible"
      v-model:trace-by-line-id="summaryTraceCodeByLineId"
      :lines="summaryTraceDialogLines"
      :default-location-id="summaryTraceDefaultLocationId"
      :patient-name="summaryDetailHeader?.applicantName ?? ''"
    />
  </div>
</template>
