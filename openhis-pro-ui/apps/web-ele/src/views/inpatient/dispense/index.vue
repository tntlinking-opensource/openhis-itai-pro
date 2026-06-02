<script setup lang="ts">
import type {
  DispensePrescriptionBlock,
  DispenseQueuePatient,
  DispenseRxTableRow,
} from '#/views/outpatient/dispense/components/types';

import type {
  EncounterMedicationDispenseSlipRowDto,
  PendingDispenseInpatientEncounterQueueRowDto,
  PractitionerOptionDto,
} from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-dispense.types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  httpDispenseInpatientMedications,
  httpLoadInpatientPharmacyDispensePageInit,
  httpPageEncounterMedicationDispenseSlips,
  httpPagePendingDispenseInpatientEncounterQueue,
  httpSavePrepareInpatientMedicationDispense,
} from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-dispense.http';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import DispenseRxCenter from '#/views/outpatient/dispense/components/dispense-rx-center.vue';
import { flattenPrescriptionBlocks } from '#/views/outpatient/dispense/components/dispense-rx-helpers';
import TraceScanDialog from '#/views/outpatient/dispense/components/trace-scan-dialog.vue';

import InpatientDispenseQueuePanel from './components/inpatient-dispense-queue-panel.vue';
import type { InpatientDispenseQueuePatient } from './components/types';
import {
  mapInpatientPendingQueueRowToPatient,
  mapInpatientSlipRowsToPrescriptionBlocks,
} from './services/map-api-to-inpatient-dispense-ui';

defineOptions({
  name: 'DispenseIp',
});

const keyword = ref('');
const today = new Date().toISOString().slice(0, 10);
const queueDateRange = ref<[string, string] | null>([today, today]);
const selectedPatientId = ref<null | string>(null);
const activeRxKind = ref<'herbal' | 'western'>('western');
const selectedPrescriptionId = ref<null | string>(null);
const selectedDispenserId = ref<string | undefined>(undefined);
const selectedWardId = ref<string | undefined>(undefined);

const rxCenterRef = ref<InstanceType<typeof DispenseRxCenter> | null>(null);

const sheetViewBlocks = ref<DispensePrescriptionBlock[]>([]);

const traceScanVisible = ref(false);
const traceCodeByLineId = ref<Record<string, string>>({});
/** 住院侧无库房改选，保持与 DispenseRxCenter v-model 兼容 */
const inventoryPickByLineId = ref<Record<string, string>>({});

const dispenserOptions = ref<{ label: string; value: string }[]>([]);
const wardOptions = ref<{ label: string; value: string }[]>([]);

const queueScope = ref<'completed' | 'pending'>('pending');

const queuePatients = ref<InpatientDispenseQueuePatient[]>([]);
const slipRows = ref<EncounterMedicationDispenseSlipRowDto[]>([]);

const queueLoading = ref(false);
const slipLoading = ref(false);
const batchSubmitting = ref(false);
const initLoading = ref(false);

/** 复用门诊处方区组件：映射为 DispenseQueuePatient */
const selectedPatientForRxCenter = computed<DispenseQueuePatient | null>(() => {
  const id = selectedPatientId.value;
  if (!id) return null;
  const p = queuePatients.value.find((row) => row.id === id);
  if (!p) return null;
  return {
    id: p.id,
    patientName: p.patientName,
    genderLabel: p.genderText,
    ageLabel: p.ageLabel,
    visitNo: p.hospitalizationNo,
    visitTimeLabel: p.admissionTimeLabel,
    rxCount: 0,
  };
});

const filteredQueuePatients = computed(() => queuePatients.value);

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
    if (!selectedDispenserId.value && dispenserOptions.value[0]) {
      selectedDispenserId.value = dispenserOptions.value[0].value;
    }
    wardOptions.value = (res.wardOptions ?? []).map((o) => ({
      value: String(o.value),
      label: o.label?.trim() || String(o.value),
    }));
  } catch {
    dispenserOptions.value = [];
    wardOptions.value = [];
  } finally {
    initLoading.value = false;
  }
}

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
      queueScope.value === 'pending' ? ['PREPARATION', 'IN_PROGRESS'] : ['COMPLETED'];
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
      queueScope.value === 'pending' ? ['PREPARATION', 'IN_PROGRESS'] : ['COMPLETED'];
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

watch([selectedPatientId, activeRxKind], () => {
  sheetViewBlocks.value = [];
  selectedPrescriptionId.value = null;
  traceCodeByLineId.value = {};
  inventoryPickByLineId.value = {};
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

watch(queueScope, () => {
  selectedPatientId.value = null;
  slipRows.value = [];
});

function handleQueueCardSelect(patient: { id: string }) {
  selectedPatientId.value = patient.id;
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

async function loadAllPrescriptionBlocksForSheet(): Promise<boolean> {
  const enc = selectedPatientId.value;
  if (!enc) return false;
  const dispenseStatusEnumIn =
    queueScope.value === 'pending' ? ['PREPARATION', 'IN_PROGRESS'] : ['COMPLETED'];
  try {
    const res = await httpPageEncounterMedicationDispenseSlips({
      encounterId: enc,
      dispenseStatusEnumIn,
      pageNo: 1,
      pageSize: 200,
    });
    const { content: slipList } =
      parsePagedRowsFromApiData<EncounterMedicationDispenseSlipRowDto>(res);
    sheetViewBlocks.value = mapInpatientSlipRowsToPrescriptionBlocks(slipList);
    return sheetViewBlocks.value.length > 0;
  } catch {
    sheetViewBlocks.value = [];
    ElMessage.error('加载处方单失败');
    return false;
  }
}

async function handleViewPrescription() {
  const ok = await loadAllPrescriptionBlocksForSheet();
  if (!ok) {
    ElMessage.warning('暂无处方可展示');
    return;
  }
  rxCenterRef.value?.openSheetViewMode();
}

function handlePrintPrescription() {
  // TODO: 打印处方单
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
  const actionable = selectedIds.filter((id) => {
    const s = slipById.get(id)?.statusEnum;
    return s === 'PREPARATION' || s === 'IN_PROGRESS';
  });
  if (actionable.length === 0) {
    ElMessage.warning('所选处方无可发药明细');
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
    const prepLines: { dispenseId: string; traceNo?: string }[] = [];
    const alreadyProgressIds: string[] = [];
    for (const id of actionable) {
      const row = slipById.get(id);
      if (!row) continue;
      if (row.statusEnum === 'IN_PROGRESS') {
        alreadyProgressIds.push(id);
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
    }
    const dispenseIds = [
      ...new Set([...alreadyProgressIds, ...prepLines.map((l) => l.dispenseId)]),
    ];
    await httpDispenseInpatientMedications({
      encounterId: enc,
      lines: dispenseIds.map((dispenseId) => ({ dispenseId })),
    });
    ElMessage.success('发药提交成功');
    traceCodeByLineId.value = {};
    await reloadSlips();
    await reloadQueue();
    await nextTick(() => {
      rxCenterRef.value?.clearRxTableSelection();
    });
  } catch {
    /* 错误提示由请求拦截器处理 */
  } finally {
    batchSubmitting.value = false;
  }
}

onMounted(async () => {
  await loadPageInit();
  await reloadQueue();
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
        <InpatientDispenseQueuePanel
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
      </template>

      <template #center>
        <DispenseRxCenter
          ref="rxCenterRef"
          v-model:active-rx-kind="activeRxKind"
          v-model:inventory-pick-by-line-id="inventoryPickByLineId"
          v-model:selected-dispenser-id="selectedDispenserId"
          v-model:selected-prescription-id="selectedPrescriptionId"
          :batch-dispense-disabled="queueScope === 'completed' || batchSubmitting || slipLoading"
          :selected-patient="selectedPatientForRxCenter"
          :prescription-blocks="prescriptionBlocks"
          :prescription-sheet-blocks="sheetViewBlocks"
          :display-table-rows="displayTableRows"
          :prescription-row-count="prescriptionList.length"
          :dispenser-options="dispenserOptions"
          @batch-dispense="handleBatchDispense"
          @batch-trace-scan="handleBatchTraceScan"
          @view-prescription="handleViewPrescription"
          @print-prescription="handlePrintPrescription"
        />
      </template>
    </StationTriPaneLayout>

    <TraceScanDialog
      v-model="traceScanVisible"
      v-model:trace-by-line-id="traceCodeByLineId"
      :lines="prescriptionList"
      :patient-name="selectedPatientForRxCenter?.patientName ?? ''"
    />
  </div>
</template>
