<script setup lang="ts">
import type {
  DispensePrescriptionBlock,
  DispenseQueuePatient,
  DispenseRxKindBadgeCounts,
  DispenseRxTableRow,
} from './components/types';

import type {
  EncounterMedicationDispenseSlipRowDto,
  PendingDispenseEncounterQueueRowDto,
  PractitionerOptionDto,
} from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

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
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
  type ChargeType,
} from '#/chs/constants/insurance-contract';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import DispenseQueuePanel from './components/dispense-queue-panel.vue';
import DispenseRxCenter from './components/dispense-rx-center.vue';
import { flattenPrescriptionBlocks } from './components/dispense-rx-helpers';
import TraceScanDialog from './components/trace-scan-dialog.vue';
import { decodeInventoryPick, encodeInventoryPick } from './services/inventory-pick-codec';
import {
  mapPendingQueueRowToPatient,
  mapSlipRowsToPrescriptionBlocks,
} from './services/map-api-to-dispense-ui';
import { loadEncounterRxKindSummary } from './services/resolve-default-rx-kind';
import { useDispenseSlipPrint } from '#/composables/use-dispense-slip-print';
import { useOutpatientPrescriptionPrint } from '#/composables/use-outpatient-prescription-print';
import {
  DISPENSE_SLIP_TEMPLATE,
  resolveOutpatientPrescriptionTemplateCode,
} from '#/utils/report/outpatient-prescription-print-config';
import { isNarcoticSlipRow } from '#/utils/report/prescription-print-kind';

defineOptions({
  name: 'PharmacyDispenseOutpatient',
});

const keyword = ref('');
const today = new Date().toISOString().slice(0, 10);
const queueDateRange = ref<[string, string] | null>([today, today]);
const selectedPatientId = ref<null | string>(null);
const activeRxKind = ref<'herbal' | 'western'>('western');
const rxKindBadgeCounts = ref<DispenseRxKindBadgeCounts>({ western: 0, herbal: 0 });
const selectedPrescriptionId = ref<null | string>(null);
const selectedDispenserId = ref<string | undefined>(undefined);

const rxCenterRef = ref<InstanceType<typeof DispenseRxCenter> | null>(null);

/** 处方单弹窗：就诊下全部中西药处方块（与表格当前 tab 的 slip 列表无关） */
const sheetViewBlocks = ref<DispensePrescriptionBlock[]>([]);

const traceScanVisible = ref(false);
const traceCodeByLineId = ref<Record<string, string>>({});
/** 表格每行选中的库房×批号（仅待发且有 inventory 候选时出现下拉） */
const inventoryPickByLineId = ref<Record<string, string>>({});

const dispenserOptions = ref<{ label: string; value: string }[]>([]);
const queueScope = ref<'completed' | 'pending'>('pending');

const queuePatients = ref<DispenseQueuePatient[]>([]);
const slipRows = ref<EncounterMedicationDispenseSlipRowDto[]>([]);

const queueLoading = ref(false);
const slipLoading = ref(false);
const batchSubmitting = ref(false);
const initLoading = ref(false);
const insuranceContractBusNo = ref<string>();
const insuranceChargeTypeOptions = ref<ChargeType[]>([]);

const { isPrinting, previewLoading, printDispenseSlip } = useDispenseSlipPrint();

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
    if (!selectedDispenserId.value && dispenserOptions.value[0]) {
      selectedDispenserId.value = dispenserOptions.value[0].value;
    }
  } catch {
    dispenserOptions.value = [];
  } finally {
    initLoading.value = false;
  }
}

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
      queueScope.value === 'pending' ? ['PREPARATION', 'IN_PROGRESS'] : ['COMPLETED'];
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

/** 同步当前就诊中西药处方块角标，并返回默认 tab */
async function syncEncounterRxKindSummary(enc: string, scope: 'completed' | 'pending') {
  const dispenseStatusEnumIn = scope === 'pending' ? ['PREPARATION', 'IN_PROGRESS'] : ['COMPLETED'];
  const summary = await loadEncounterRxKindSummary({
    encounterId: enc,
    dispenseStatusEnumIn,
  });
  rxKindBadgeCounts.value = summary.badgeCounts;
  return summary;
}

/** 当前就诊下的发放明细（分页首屏） */
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
      inventoryPickByLineId.value = {};
      return;
    }
    const cur = selectedPrescriptionId.value;
    if (!cur || !list.some((r) => r.rxId === cur)) {
      selectedPrescriptionId.value = list[0]?.rxId ?? null;
    }
    const next: Record<string, string> = {};
    for (const row of list) {
      const opts = row.inventoryPickOptions;
      if (!opts?.length) continue;
      const prev = inventoryPickByLineId.value[row.id];
      const firstOpt = opts[0];
      if (!firstOpt) continue;
      const keep = prev && opts.some((o) => o.value === prev) ? prev : firstOpt.value;
      next[row.id] = keep;
    }
    inventoryPickByLineId.value = next;
  },
  { immediate: true },
);

watch(
  () => [selectedPatientId.value, activeRxKind.value, queueScope.value] as const,
  async ([enc, kind, scope], old) => {
    if (!enc) {
      slipRows.value = [];
      rxKindBadgeCounts.value = { western: 0, herbal: 0 };
      return;
    }
    const [oldEnc, , oldScope] = old ?? [null, kind, scope];
    if (enc !== oldEnc || scope !== oldScope) {
      const summary = await syncEncounterRxKindSummary(enc, scope);
      if (summary.defaultKind !== kind) {
        activeRxKind.value = summary.defaultKind;
        return;
      }
    }
    void reloadSlips();
  },
);

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
  slipRows.value = [];
  activeRxKind.value = 'western';
  rxKindBadgeCounts.value = { western: 0, herbal: 0 };
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
    sheetViewBlocks.value = mapSlipRowsToPrescriptionBlocks(slipList);
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
  const enc = selectedPatientId.value;
  const rxId = selectedPrescriptionId.value;
  if (!enc) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!rxId) {
    ElMessage.warning('请先选择处方');
    return;
  }
  const block = prescriptionBlocks.value.find((b) => b.id === rxId);
  if (!block) {
    ElMessage.warning('未找到所选处方');
    return;
  }
  const firstLineId = block.items[0]?.id;
  const slip = firstLineId
    ? slipRows.value.find((r) => String(r.dispenseId) === firstLineId)
    : undefined;
  const dispenseStatusEnumIn =
    queueScope.value === 'pending' ? ['PREPARATION', 'IN_PROGRESS'] : ['COMPLETED'];
  const narcoticOnly = slip ? isNarcoticSlipRow(slip) : false;
  const tcmOnly = !narcoticOnly && block.rxType === 'herbal';
  const templateCode = resolveOutpatientPrescriptionTemplateCode({ narcoticOnly, tcmOnly });
  const printParams = {
    encounterId: enc,
    prescriptionNo: block.rxNo,
    prescriptionGroupId:
      slip?.prescriptionGroupId != null ? String(slip.prescriptionGroupId) : undefined,
    westernOnly: block.rxType === 'western',
    tcmOnly,
    dispenseStatusEnumIn,
  };
  if (templateCode === DISPENSE_SLIP_TEMPLATE) {
    void printDispenseSlip({
      ...printParams,
      jobName: `发药单-${block.rxNo}`,
    });
    return;
  }
  void printPrescription({
    ...printParams,
    templateCode,
    jobName: narcoticOnly
      ? `麻精处方-${block.rxNo}`
      : tcmOnly
        ? `中药处方-${block.rxNo}`
        : `门诊处方-${block.rxNo}`,
  });
}

/** 先保存配药再发药：仅处理 PREPARATION / IN_PROGRESS 行 */
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
    const prepLines: {
      dispenseId: string;
      dispenseLocationId: string;
      lotNumber?: null | string;
      quantity: string;
      traceNo?: null | string;
    }[] = [];
    const alreadyProgressIds: string[] = [];
    for (const id of actionable) {
      const row = slipById.get(id);
      if (!row) continue;
      if (row.statusEnum === 'IN_PROGRESS') {
        alreadyProgressIds.push(id);
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
    }
    const dispenseIds = [
      ...new Set([...alreadyProgressIds, ...prepLines.map((l) => l.dispenseId)]),
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
    traceCodeByLineId.value = {};
    inventoryPickByLineId.value = {};
    await reloadSlips();
    await syncEncounterRxKindSummary(enc, queueScope.value);
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
          :selected-patient="selectedPatient"
          :prescription-blocks="prescriptionBlocks"
          :prescription-sheet-blocks="sheetViewBlocks"
          :display-table-rows="displayTableRows"
          :prescription-row-count="prescriptionList.length"
          :dispenser-options="dispenserOptions"
          :prescription-print-loading="isPrinting || previewLoading"
          :rx-kind-badge-counts="rxKindBadgeCounts"
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
      :patient-name="selectedPatient?.patientName ?? ''"
    />
  </div>
</template>
