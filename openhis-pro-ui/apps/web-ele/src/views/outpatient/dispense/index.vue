<script setup lang="ts">
import type {
  DispensePrescriptionBlock,
  DispensePrescriptionItemRow,
  DispenseQueuePatient,
  DispenseRxKindBadgeCounts,
  DispenseRxTableRow,
} from './components/types';

import type {
  EncounterMedicationDispenseSlipRowDto,
  PendingDispenseEncounterQueueRowDto,
  PractitionerOptionDto,
} from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';
import type { ChargeType } from '#/chs/constants/insurance-contract';

import { computed, nextTick, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import { ElMessage, ElMessageBox, ElOption, ElSelect } from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  httpDispenseOutpatientMedications,
  httpLoadPharmacyDispensePageInit,
  httpPageEncounterMedicationDispenseSlips,
  httpPagePendingDispenseEncounterQueue,
  httpSavePrepareOutpatientMedicationDispense,
} from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.http';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { fetchEncounterTcmDiagnosisDisplayText } from '#/components/doctor-station/diagnosis/services/fetch-encounter-tcm-diagnosis-text';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { usePrintClientWarmup } from '#/composables/use-print-client-warmup';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
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
import { PRINT_SCENARIO } from '#/utils/print/constants/print-scenario';
import { printOutpatientTcmPrescription } from '#/utils/print/print-outpatient-tcm-prescription';
import { resolveOutpatientPrescriptionPrintScenario } from '#/utils/print/resolve-outpatient-prescription-scenario';
import { groupSlipRowsByPrescriptionNo, print } from '#/utils/print/runtime';
import {
  isDispenseScannedTraceInsufficient,
  loadRemainingSplitByTraceNos,
  splitTraceNoCommaString,
} from '#/utils/trace';

import DispenseQueuePanel from './components/dispense-queue-panel.vue';
import DispenseRxCenter from './components/dispense-rx-center.vue';
import { flattenPrescriptionBlocks } from './components/dispense-rx-helpers';
import TraceScanDialog from './components/trace-scan-dialog.vue';
import {
  isAlreadyPreparedDispenseStatus,
  isBatchDispenseActionableStatus,
  PENDING_DISPENSE_STATUS_FILTER,
} from './services/dispense-batch-workflow';
import { decodeInventoryPick, encodeInventoryPick } from './services/inventory-pick-codec';
import {
  mapPendingQueueRowToPatient,
  mapSlipRowsToPrescriptionBlocks,
} from './services/map-api-to-dispense-ui';
import {
  computeRxKindSummaryFromSlips,
  filterSlipRowsByRxKind,
} from './services/resolve-default-rx-kind';
import {
  resolveDispenseOperationSplitQty,
  resolveDispenseRequiredTraceCount,
} from './services/resolve-dispense-trace-scan-inventory';

defineOptions({
  name: 'PharmacyDispenseOutpatient',
});

usePrintClientWarmup();

const keyword = ref('');
const today = new Date().toISOString().slice(0, 10);
const queueDateRange = ref<[string, string] | null>([today, today]);
const selectedPatientId = ref<null | string>(null);
const activeRxKind = ref<'herbal' | 'western'>('western');
const rxKindBadgeCounts = ref<DispenseRxKindBadgeCounts>({ western: 0, herbal: 0 });
const selectedPrescriptionId = ref<null | string>(null);
const selectedDispenserId = ref<string | undefined>(undefined);

const rxCenterRef = ref<InstanceType<typeof DispenseRxCenter> | null>(null);

/** 处方单视图：就诊下全部中西药处方块（与表格当前 tab 的 slip 列表无关） */
const sheetViewBlocks = ref<DispensePrescriptionBlock[]>([]);
/** 处方单视图：中医主诊断展示（病证合并） */
const sheetTcmDiagnosisText = ref('');
const sheetLoading = ref(false);

const traceScanVisible = ref(false);
/** 批量扫码弹窗明细：打开时按当前勾选处方截取 */
const traceScanDialogLines = ref<DispensePrescriptionItemRow[]>([]);
const traceCodeByLineId = ref<Record<string, string>>({});
/** 表格每行选中的库房×批号（仅待发且有 inventory 候选时出现下拉） */
const inventoryPickByLineId = ref<Record<string, string>>({});

const OUTPATIENT_TRACE_CACHE_SCENE = DISPENSE_TRACE_CACHE_SCENE.outpatient;

const dispenserOptions = ref<{ label: string; value: string }[]>([]);
const queueScope = ref<'completed' | 'pending'>('pending');

const queuePatients = ref<DispenseQueuePatient[]>([]);
/** 当前就诊下全量发放行（未按中西药 tab 过滤，供角标与 tab 切换复用） */
const allSlipRowsForEncounter = ref<EncounterMedicationDispenseSlipRowDto[]>([]);
const slipRows = ref<EncounterMedicationDispenseSlipRowDto[]>([]);

const queueLoading = ref(false);
const slipLoading = ref(false);
const batchSubmitting = ref(false);
const printSubmitting = ref(false);
const initLoading = ref(false);
const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

const selectedPatient = computed(() => {
  const id = selectedPatientId.value;
  if (!id) return null;
  return queuePatients.value.find((p) => p.id === id) ?? null;
});

const filteredQueuePatients = computed(() => queuePatients.value);

const prescriptionBlocks = computed(() => {
  if (!selectedPatientId.value) return [];
  return mapSlipRowsToPrescriptionBlocks(slipRows.value);
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

/** 进入页时拉初始化数据（调配药师候选等） */
async function loadPageInit() {
  initLoading.value = true;
  try {
    const res = await httpLoadPharmacyDispensePageInit({
      pageNo: 1,
      pageSize: 100,
    });
    const page = res.preparerCandidatePage;
    const { content: list } = parsePagedRowsFromApiData<PractitionerOptionDto>(page ?? {});
    dispenserOptions.value = list.map((p) => ({
      value: String(p.practitionerId),
      label: p.displayName?.trim() || String(p.practitionerId),
    }));
    // 优先回填当前用户上次选择的调配药师，否则默认首项
    if (!selectedDispenserId.value) {
      selectedDispenserId.value = resolvePreparerSelection(dispenserOptions.value);
    }
  } catch {
    dispenserOptions.value = [];
  } finally {
    initLoading.value = false;
  }
}

/** 用户变更调配药师时写入本地偏好，供下次进入页回填 */
watch(selectedDispenserId, (id) => {
  writePreparerPreference(id);
});

/** 按时间范围、队列类型与关键字拉取就诊队列 */
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
    const res = await httpPagePendingDispenseEncounterQueue({
      startTime: `${startDay} 00:00:00`,
      endTime: `${endDay} 23:59:59`,
      keyword: keyword.value.trim() || undefined,
      dispenseStatusEnumIn,
      pageNo: 1,
      pageSize: 50,
    });
    const { content: rows } = parsePagedRowsFromApiData<PendingDispenseEncounterQueueRowDto>(res);
    queuePatients.value = rows.map((r) => mapPendingQueueRowToPatient(r));
  } catch {
    queuePatients.value = [];
  } finally {
    queueLoading.value = false;
  }
}

const debouncedReloadQueue = useDebounceFn(() => {
  void reloadQueue();
}, 400);

function applySlipRowsForActiveKind(all: EncounterMedicationDispenseSlipRowDto[]) {
  slipRows.value = filterSlipRowsByRxKind(all, activeRxKind.value);
}

/**
 * 拉取当前就诊发放明细（单次请求）：更新角标缓存，并按需切换默认 tab 或刷新当前 tab 列表。
 * @param resetRxKind 切换就诊/队列范围时为 true，按数据推断默认中西药 tab
 */
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
    const summary = computeRxKindSummaryFromSlips(slipList);
    rxKindBadgeCounts.value = summary.badgeCounts;
    if (resetRxKind && summary.defaultKind !== activeRxKind.value) {
      activeRxKind.value = summary.defaultKind;
    } else {
      applySlipRowsForActiveKind(slipList);
    }
  } catch {
    allSlipRowsForEncounter.value = [];
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

/** 清空表格勾选/库房/追溯等临时态（不碰处方单笺面，笺面随就诊另拉） */
function clearRxWorkbenchTransientState() {
  selectedPrescriptionId.value = null;
  traceCodeByLineId.value = {};
  inventoryPickByLineId.value = {};
  void nextTick(() => {
    rxCenterRef.value?.clearRxTableSelection();
  });
}

/** 将当前内存扫码/库房选择写入指定就诊的 session 缓存 */
function persistDispenseTraceCacheForEncounter(encounterId: string): void {
  const enc = String(encounterId ?? '').trim();
  if (!enc) return;
  writeDispenseTraceCache({
    scene: OUTPATIENT_TRACE_CACHE_SCENE,
    encounterId: enc,
    snap: {
      traceCodeByLineId: { ...traceCodeByLineId.value },
      inventoryPickByLineId: { ...inventoryPickByLineId.value },
    },
  });
}

/** 从 session 恢复某就诊的扫码/库房选择到内存 */
function restoreDispenseTraceCacheForEncounter(encounterId: string): void {
  const enc = String(encounterId ?? '').trim();
  if (!enc) return;
  const snap = readDispenseTraceCache(OUTPATIENT_TRACE_CACHE_SCENE, enc);
  if (!snap) return;
  traceCodeByLineId.value = { ...snap.traceCodeByLineId };
  if (snap.inventoryPickByLineId) {
    inventoryPickByLineId.value = { ...snap.inventoryPickByLineId };
  }
}

/** 当前就诊有变更时防抖落盘（扫码/选库房后误切菜单可恢复） */
const debouncedPersistCurrentDispenseTrace = useDebounceFn(() => {
  const enc = String(selectedPatientId.value ?? '').trim();
  if (!enc) return;
  // 空态不落盘：换患者先清空内存时，避免误删新就诊已有 session 缓存
  if (
    Object.keys(traceCodeByLineId.value).length === 0 &&
    Object.keys(inventoryPickByLineId.value).length === 0
  ) {
    return;
  }
  persistDispenseTraceCacheForEncounter(enc);
}, 300);

/**
 * 发药成功后仅清除本批已核发行的临时扫码/库房选择。
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
  const nextPick: Record<string, string> = {};
  // 仅保留未核发行的库房×批号选择
  for (const [id, pick] of Object.entries(inventoryPickByLineId.value)) {
    if (!idSet.has(id)) nextPick[id] = pick;
  }
  inventoryPickByLineId.value = nextPick;
  // 同步 session：已发行从缓存剔除
  const enc = String(selectedPatientId.value ?? '').trim();
  if (enc) {
    removeDispenseTraceCacheLines({
      scene: OUTPATIENT_TRACE_CACHE_SCENE,
      encounterId: enc,
      lineIds,
    });
  }
}

/**
 * 库存下拉默认值：优先对齐发放行库位+批号，避免默认到模板其它候选药房。
 *
 * @param row 展开后的发药明细行
 * @returns 选项 value；无候选时 undefined
 */
function resolveDefaultInventoryPickValue(row: DispensePrescriptionItemRow): string | undefined {
  const opts = row.inventoryPickOptions;
  if (!opts?.length) return undefined;
  const locId = (row.dispenseLocationId ?? '').trim();
  const lotRaw = (row.batchNo ?? '').trim();
  const lot = lotRaw && lotRaw !== '—' ? lotRaw : '';
  // 先库位+批号精确对齐发放行
  if (locId) {
    for (const opt of opts) {
      try {
        const decoded = decodeInventoryPick(opt.value);
        if (String(decoded.pharmacyOrganizationId) !== locId) continue;
        if (lot && (decoded.lotNumber ?? '') !== lot) continue;
        return opt.value;
      } catch {
        /* 非法编码跳过 */
      }
    }
    // 再仅按发放库位对齐（批号候选不完全一致时）
    for (const opt of opts) {
      try {
        const decoded = decodeInventoryPick(opt.value);
        if (String(decoded.pharmacyOrganizationId) === locId) return opt.value;
      } catch {
        /* 非法编码跳过 */
      }
    }
  }
  return opts[0]?.value;
}

watch(selectedPatientId, (enc, prevEnc) => {
  // 换患者前：先把上一位的扫码落盘，避免误切白扫
  const prev = String(prevEnc ?? '').trim();
  if (prev) {
    persistDispenseTraceCacheForEncounter(prev);
  }
  // 换患者：清空扫码/勾选等临时态，并先清笺面避免闪上一位
  clearRxWorkbenchTransientState();
  sheetViewBlocks.value = [];
  sheetTcmDiagnosisText.value = '';
  // 再恢复新就诊已缓存的扫码（切走再回来）
  const next = String(enc ?? '').trim();
  if (next) {
    restoreDispenseTraceCacheForEncounter(next);
  }
});

watch(
  [traceCodeByLineId, inventoryPickByLineId],
  () => {
    debouncedPersistCurrentDispenseTrace();
  },
  { deep: true },
);

watch(activeRxKind, () => {
  // 切中西药 tab：只清勾选，保留另一 tab 已扫追溯码（批量扫码后可分批发药）
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
    // 合并当前 tab 库房选择；保留另一 tab 行上的已选，避免切 tab 冲掉
    const currentIds = new Set(list.map((row) => row.id));
    const next: Record<string, string> = {};
    // 先保留非当前 tab 行（另一侧中西药）的库房选择
    for (const [id, pick] of Object.entries(inventoryPickByLineId.value)) {
      if (!currentIds.has(id)) next[id] = pick;
    }
    for (const row of list) {
      const opts = row.inventoryPickOptions;
      if (!opts?.length) continue;
      const prev = inventoryPickByLineId.value[row.id];
      // 已选手动选择且仍在候选内则保留；否则优先对齐发放行库位/批号
      const keep =
        prev && opts.some((o) => o.value === prev) ? prev : resolveDefaultInventoryPickValue(row);
      if (!keep) continue;
      next[row.id] = keep;
    }
    inventoryPickByLineId.value = next;
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
      sheetTcmDiagnosisText.value = '';
      return;
    }
    const [oldEnc, oldScope] = old ?? [null, scope];
    const encounterChanged = enc !== oldEnc || scope !== oldScope;
    await reloadEncounterSlipsFromServer(encounterChanged);
    // 处方单为默认视图：切换就诊/队列范围后同步拉笺面数据
    await loadAllPrescriptionBlocksForSheet();
  },
);

watch(activeRxKind, () => {
  if (!selectedPatientId.value) return;
  applySlipRowsForActiveKind(allSlipRowsForEncounter.value);
});

watch(
  () => [queueDateRange.value, queueScope.value] as const,
  () => {
    void reloadQueue();
  },
);

watch(keyword, () => {
  debouncedReloadQueue();
});

watch(queueScope, () => {
  selectedPatientId.value = null;
  allSlipRowsForEncounter.value = [];
  slipRows.value = [];
  activeRxKind.value = 'western';
  rxKindBadgeCounts.value = { western: 0, herbal: 0 };
});

/** 选中卡片切换当前就诊；再次点击已选患者仍刷新处方明细（避免 ref 不变导致 watch 不触发） */
function handleQueueCardSelect(patient: { id: string }) {
  const id = patient.id;
  if (selectedPatientId.value === id) {
    // 再点同一人：刷新明细，但扫码先落盘再恢复，避免误触清空白扫
    persistDispenseTraceCacheForEncounter(id);
    clearRxWorkbenchTransientState();
    restoreDispenseTraceCacheForEncounter(id);
    void reloadEncounterSlipsFromServer(false);
    void loadAllPrescriptionBlocksForSheet();
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

/** 时间范围变更：清空时回退为当天 */
function handleQueueDateRangeChange(range: [string, string] | null) {
  if (range && range[0] && range[1]) {
    queueDateRange.value = range;
    return;
  }
  queueDateRange.value = [today, today];
}

/**
 * 批量扫描追溯码：药品明细随当前勾选处方走（笺面勾选 / 列表明细勾选同构）。
 */
function handleBatchTraceScan() {
  const selectedIds = rxCenterRef.value?.getSelectedDispenseLineIds() ?? [];
  if (selectedIds.length === 0) {
    ElMessage.warning('请先勾选处方');
    return;
  }
  const selected = new Set(selectedIds);
  const lines = prescriptionList.value.filter((row) => {
    const sourceIds =
      row.sourceDispenseIds && row.sourceDispenseIds.length > 0 ? row.sourceDispenseIds : [row.id];
    return sourceIds.some((id) => selected.has(id));
  });
  if (lines.length === 0) {
    ElMessage.warning('所选处方暂无药品明细');
    return;
  }
  traceScanDialogLines.value = lines;
  traceScanVisible.value = true;
}

/**
 * 发药前追溯码：可不扫；已扫但剩余盖不住须弹窗确认后方可继续（BR-OPP-015 / DD §7）。
 *
 * <p>用户确认「仍继续」后：不足行清空内存中的追溯码再提交，避免带着盖不住码串写 OUT，
 * 与「不扫只影响追溯账、不挡主流程」一致。</p>
 *
 * @param lineIds 待发发放行 id
 * @returns 用户确认继续或无需确认时为 true；取消则为 false
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
      `${insufficient.join('\n')}\n\n确认后将按「未扫够」继续发药（不足行的追溯码不提交），追溯账可能不完整。`,
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

/**
 * 装配处方单视图：复用当前就诊已拉好的全量 slip，再补中医诊断文案。
 * 不按中西药 tab 过滤，与原「处方查看」弹窗一致。
 */
async function loadAllPrescriptionBlocksForSheet(): Promise<boolean> {
  const enc = selectedPatientId.value;
  if (!enc) {
    sheetViewBlocks.value = [];
    sheetTcmDiagnosisText.value = '';
    return false;
  }
  const patientId = selectedPatient.value?.patientId ?? '';
  sheetLoading.value = true;
  try {
    sheetViewBlocks.value = mapSlipRowsToPrescriptionBlocks(allSlipRowsForEncounter.value);
    // 队列若尚未带 genderEnumText，回落用明细行后端文案（禁止前端按 code 翻译）
    const genderText = allSlipRowsForEncounter.value
      .map((r) => r.genderEnumText?.trim() ?? '')
      .find(Boolean);
    if (genderText) {
      const queueRow = queuePatients.value.find((p) => p.id === enc);
      if (queueRow && !queueRow.genderLabel) {
        queueRow.genderLabel = genderText;
      }
    }
    sheetTcmDiagnosisText.value = patientId
      ? await fetchEncounterTcmDiagnosisDisplayText(enc, patientId)
      : '';
    return sheetViewBlocks.value.length > 0;
  } catch {
    sheetViewBlocks.value = [];
    sheetTcmDiagnosisText.value = '';
    ElMessage.error('加载处方单失败');
    return false;
  } finally {
    sheetLoading.value = false;
  }
}

/** 多选处方：每张处方号静默打印一次 */
async function handlePrintPrescriptions() {
  const selectedLineIds = rxCenterRef.value?.getSelectedDispenseLineIds() ?? [];
  if (selectedLineIds.length === 0) {
    ElMessage.warning('请先勾选要打印的处方');
    return;
  }
  const selectedSet = new Set(selectedLineIds);
  const selectedSlips = slipRows.value.filter((row) => selectedSet.has(row.dispenseId));
  await printSlipGroups(selectedSlips);
}

/**
 * 打印前补偿：slip 未返回费用性质时，回填患者队列合同展示名。
 */
function withPatientFeeNatureFallback(
  slips: EncounterMedicationDispenseSlipRowDto[],
): EncounterMedicationDispenseSlipRowDto[] {
  const fallback = selectedPatient.value?.feeNatureLabel?.trim() || '';
  if (!fallback) return slips;
  return slips.map((row) => {
    if ((row.feeNatureText ?? '').trim()) return row;
    return { ...row, feeNatureText: fallback };
  });
}

/**
 * 按 slip 组分中西药场景逐张静默打印。
 * @returns 是否至少找到可打印分组
 */
async function printSlipGroups(
  selectedSlips: EncounterMedicationDispenseSlipRowDto[],
): Promise<boolean> {
  const groups = groupSlipRowsByPrescriptionNo(withPatientFeeNatureFallback(selectedSlips));
  if (groups.length === 0) {
    ElMessage.warning('未找到可打印的处方数据');
    return false;
  }
  const encounterId = selectedPatientId.value;
  const patientId = selectedPatient.value?.patientId ?? '';
  printSubmitting.value = true;
  try {
    for (const group of groups) {
      const prescriptionNo = group[0]?.prescriptionNo?.trim() ?? '';
      const scenarioCode = resolveOutpatientPrescriptionPrintScenario(group);
      if (scenarioCode === PRINT_SCENARIO.outpatientTcmRx) {
        if (!encounterId || !patientId) {
          ElMessage.warning('缺少就诊或患者信息，无法打印中药处方诊断');
          continue;
        }
        await printOutpatientTcmPrescription({
          slipRows: group,
          encounterId,
          patientId,
          prescriptionNo: prescriptionNo || undefined,
        });
        continue;
      }
      await print({
        scenarioCode,
        source: group,
        bizRef: {
          encounterId: encounterId ?? undefined,
          prescriptionNo: prescriptionNo || undefined,
        },
      });
    }
    return true;
  } finally {
    printSubmitting.value = false;
  }
}

/** 发药单视图：按单张处方块打印 */
async function handleSinglePrescriptionPrint(rxId: string) {
  const block = sheetViewBlocks.value.find((b) => b.id === rxId);
  if (!block) {
    ElMessage.warning('未找到可打印的发药单');
    return;
  }
  const lineIds = new Set(block.items.map((line) => line.id));
  const slips = allSlipRowsForEncounter.value.filter((row) => lineIds.has(row.dispenseId));
  await printSlipGroups(slips);
}

/**
 * 先保存配药再发药：PREPARATION 先配药；IN_PROGRESS/PREPARED 可直接重试发药。
 * @param lineIds 待发发放行；空则按表格勾选
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
    ElMessage.warning('请先勾选处方');
    return;
  }
  const slipById = new Map(slipRows.value.map((r) => [r.dispenseId, r]));
  // 仅保留可发药状态明细
  const actionable = lineIds.filter((id) =>
    isBatchDispenseActionableStatus(slipById.get(id)?.statusEnum),
  );
  if (actionable.length === 0) {
    ElMessage.warning('所选处方无可发药明细');
    return;
  }
  if (!(await confirmTraceCodesBeforeDispense(actionable))) {
    return;
  }
  try {
    await ElMessageBox.confirm(`确认对所选处方执行保存配药并发药？`, confirmTitle, {
      type: 'warning',
    });
  } catch {
    return;
  }
  batchSubmitting.value = true;
  try {
    const prepLines: {
      dispenseId: string;
      dispenseLocationId: string;
      lotNumber?: null | string;
      quantity: string;
      traceNo?: null | string;
    }[] = [];
    const alreadyPreparedIds: string[] = [];
    for (const id of actionable) {
      const row = slipById.get(id);
      if (!row) continue;
      if (isAlreadyPreparedDispenseStatus(row.statusEnum)) {
        alreadyPreparedIds.push(id);
        continue;
      }
      let pickRaw = inventoryPickByLineId.value[id];
      const inv0 = row.inventoryDetailList?.[0];
      if (!pickRaw && inv0) {
        pickRaw = encodeInventoryPick({
          pharmacyOrganizationId: String(inv0.pharmacyOrganizationId),
          lotNumber: inv0.lotNumber,
        });
      }
      if (!pickRaw) {
        ElMessage.error(`请为「${row.medicationName ?? '药品'}」选择发放库房`);
        return;
      }
      let dispenseLocationId: string;
      let lotNumber: string | undefined;
      try {
        const decoded = decodeInventoryPick(pickRaw);
        dispenseLocationId = decoded.pharmacyOrganizationId;
        lotNumber = decoded.lotNumber;
      } catch {
        ElMessage.error(`「${row.medicationName ?? '药品'}」库房选择数据无效，请重新选择`);
        return;
      }
      prepLines.push({
        dispenseId: id,
        dispenseLocationId,
        lotNumber,
        quantity: String(row.quantity),
        traceNo: traceCodeByLineId.value[id] || row.traceNo || undefined,
      });
    }
    if (prepLines.length > 0) {
      await httpSavePrepareOutpatientMedicationDispense({
        encounterId: enc,
        preparerPractitionerId: preparerId,
        lines: prepLines,
      });
      // 配药成功后同步服务端状态，避免发药失败重试时仍按 PREPARATION 重复 savePrepare
      await reloadEncounterSlipsFromServer(false);
    }
    const dispenseIds = [
      ...new Set([...alreadyPreparedIds, ...prepLines.map((l) => l.dispenseId)]),
    ];
    const res = await httpDispenseOutpatientMedications({
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
    await reloadEncounterSlipsFromServer(false);
    await loadAllPrescriptionBlocksForSheet();
    await reloadQueue();
    await nextTick(() => {
      rxCenterRef.value?.clearRxTableSelection();
    });
  } catch {
    /* 错误提示由请求拦截器处理；同步已配药态以便用户直接重试发药 */
    await reloadEncounterSlipsFromServer(false);
    await loadAllPrescriptionBlocksForSheet();
    await reloadQueue();
  } finally {
    batchSubmitting.value = false;
  }
}

/** 工具栏批量发药：按表格勾选处方 */
async function handleBatchDispense() {
  const selectedIds = rxCenterRef.value?.getSelectedDispenseLineIds() ?? [];
  await executeDispenseForLineIds(selectedIds, '批量发药');
}

/** 处方单视图：按单张处方块发药 */
async function handleSinglePrescriptionDispense(rxId: string) {
  // 取该处方块在当前 tab 下列表中的全部发放行
  const lineIds = prescriptionList.value.filter((row) => row.rxId === rxId).map((row) => row.id);
  if (lineIds.length === 0) {
    ElMessage.warning('当前处方无可发药明细');
    return;
  }
  await executeDispenseForLineIds(lineIds, '单独发药');
}

onMounted(async () => {
  await loadPageInit();
  try {
    const chargeTypes = await getChargeTypeListApi();
    const eligible = filterInsuranceChargeTypes(chargeTypes);
    insuranceChargeTypeOptions.value = eligible;
    insuranceContractBusNo.value = resolveDefaultInsuranceBusNo(eligible);
  } catch {
    insuranceContractBusNo.value = undefined;
    insuranceChargeTypeOptions.value = [];
  }
  await reloadQueue();
});
</script>

<template>
  <div
    v-loading="initLoading"
    class="pharmacy-dispense-outpatient-root flex h-[calc(100vh-120px)] min-h-[480px] w-full min-w-0 flex-col overflow-hidden"
  >
    <StationTriPaneLayout
      :show-right="false"
      :show-footer="false"
      class="pharmacy-dispense-outpatient min-h-0 min-w-0 flex-1"
      left-track="minmax(240px, 264px)"
      left-track-lg="minmax(240px, 264px)"
      center-track="minmax(0, 1fr)"
    >
      <template #left>
        <div class="flex h-full min-h-0 flex-col">
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
            <DispenseQueuePanel
              v-model:keyword="keyword"
              v-model:queue-date-range="queueDateRange"
              v-model:queue-scope="queueScope"
              :filtered-patients="filteredQueuePatients"
              :loading-queue="queueLoading"
              :selected-patient-id="selectedPatientId"
              @queue-date-range-change="handleQueueDateRangeChange"
              @refresh="reloadQueue"
              @select-patient="handleQueueCardSelect"
            />
          </div>
        </div>
      </template>

      <template #center>
        <DispenseRxCenter
          ref="rxCenterRef"
          v-model:active-rx-kind="activeRxKind"
          v-model:inventory-pick-by-line-id="inventoryPickByLineId"
          v-model:selected-dispenser-id="selectedDispenserId"
          v-model:selected-prescription-id="selectedPrescriptionId"
          :batch-dispense-disabled="queueScope === 'completed' || batchSubmitting || slipLoading"
          :print-prescriptions-disabled="printSubmitting || slipLoading"
          :selected-patient="selectedPatient"
          :prescription-blocks="prescriptionBlocks"
          :prescription-sheet-blocks="sheetViewBlocks"
          :sheet-loading="sheetLoading"
          :sheet-tcm-diagnosis-text="sheetTcmDiagnosisText"
          :trace-code-by-line-id="traceCodeByLineId"
          :display-table-rows="displayTableRows"
          :prescription-row-count="prescriptionList.length"
          :dispenser-options="dispenserOptions"
          :rx-kind-badge-counts="rxKindBadgeCounts"
          @batch-dispense="handleBatchDispense"
          @batch-trace-scan="handleBatchTraceScan"
          @dispense-prescription="handleSinglePrescriptionDispense"
          @print-prescription="handleSinglePrescriptionPrint"
          @print-prescriptions="handlePrintPrescriptions"
        />
      </template>
    </StationTriPaneLayout>

    <TraceScanDialog
      v-model="traceScanVisible"
      v-model:trace-by-line-id="traceCodeByLineId"
      :lines="traceScanDialogLines"
      :inventory-pick-by-line-id="inventoryPickByLineId"
      :patient-name="selectedPatient?.patientName ?? ''"
    />
  </div>
</template>
