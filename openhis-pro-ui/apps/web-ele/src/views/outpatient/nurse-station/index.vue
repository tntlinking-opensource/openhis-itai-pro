<script setup lang="ts">
import type {
  ActionRecord,
  NurseAdviceExecuteRow,
  NursePatient,
  SupplyItem,
  SupplyRoomOption,
} from './components/types';

import type {
  NetRemainingProcedureRecordItemDto,
  NurseStationPatientQueueItemDto,
  OrderExecutionSheetItemDto,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useDebounceFn } from '@vueuse/core';
import { useUserStore } from '@vben/stores';

import { ElMessage } from 'element-plus';

import {
  httpBatchCancelDisposition,
  httpBatchPerformDisposition,
  httpListNetRemainingProcedureRecordsForNurseStation,
  httpListProcedureRecordsForNurseStation,
  httpPageNurseStationPatientQueue,
  httpQueryEncounterTreatmentSheet,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.http';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { useAuthStore } from '#/store';
import { getLocalDayRangeDateTimeStrings } from '#/utils/date-range-quick';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import NurseActionRecordDrawer from './components/nurse-action-record-drawer.vue';
import NurseCancelProcedureDialog from './components/nurse-cancel-procedure-dialog.vue';
import NurseExecuteCenter from './components/nurse-execute-center.vue';
import NurseFooterBar from './components/nurse-footer-bar.vue';
import NurseQueuePanel from './components/nurse-queue-panel.vue';
import NurseRightSidebar from './components/nurse-right-sidebar.vue';
import {
  mapDeviceLinesToSupplyItems,
  mapOrderExecutionToActionRecord,
  mapQueueItemToNursePatient,
  isNurseExecuteMedicationRow,
  mapTreatmentSheetToExecuteRows,
  normalizeNurseStationRequestTableForApi,
  parseExecuteKeysToBatchIds,
  resolveSupplyDispenseIdForApi,
} from './services/map-nurse-station-api';
import { useBedsideSpecimenBarcodePrint } from '#/composables/use-bedside-specimen-barcode-print';
import { useIvInfusionLabelPrint } from '#/composables/use-iv-infusion-label-print';
import {
  useNebulizationLabelPrint,
  useSkinTestLabelPrint,
} from '#/composables/use-medication-label-print';
import { useNursePrescriptionPrint } from '#/composables/use-nurse-prescription-print';
import { useProcedureApplyFormPrint } from '#/composables/use-service-apply-form-print';
import { useTreatmentExecuteSheetPrint } from '#/composables/use-treatment-execute-sheet-print';
import { groupInfusionLabelPrintTargets } from '#/utils/report/iv-infusion-label-print';
import {
  groupMedicationExecuteRequestIds,
  groupMedicationLabelPrintTargets,
  groupProcedureApplyPrintTargets,
  groupSpecimenBarcodePrintTargets,
  groupTreatmentExecutePrintTargets,
} from '#/utils/report/nurse-station-print-targets';

defineOptions({
  name: 'OutpatientNurseStation',
});

/** IF-NUR-05 必填取消原因，前端不再弹窗采集 */
const DEFAULT_CANCEL_STATUS_REASON = '护士站取消执行';

const authStore = useAuthStore();
const userStore = useUserStore();

const keyword = ref('');
const defaultQueueListDate = new Date().toISOString().slice(0, 10);
const selectedQueueListDate = ref(defaultQueueListDate);
const selectedPatientId = ref('');
const selectedExecuteKeys = ref<string[]>([]);
const selectedSupplyIds = ref<string[]>([]);
/** 程序化全选表格时忽略 selection-change，避免先被 clear 成空数组 */
const isApplyingDefaultExecuteSelection = ref(false);
const selectedSupplyRoomId = ref<string | undefined>(undefined);

/** 耗材房下拉：库房主键需与基础数据一致后再改为接口加载 */
const supplyRoomOptions = ref<SupplyRoomOption[]>([]);

const selectedSupplyRoomLabel = computed(() => {
  const id = selectedSupplyRoomId.value;
  if (!id) return '';
  return supplyRoomOptions.value.find((o) => o.value === id)?.label ?? id;
});

const showRecordDrawer = ref(false);
const recordTypeFilter = ref<'全部' | '打印' | '执行'>('全部');
const executeCenterRef = ref<InstanceType<typeof NurseExecuteCenter> | null>(null);
const executeTableLoading = ref(false);
const batchPerforming = ref(false);
const { printIvInfusionLabels } = useIvInfusionLabelPrint();
const { printBedsideSpecimenBarcodes } = useBedsideSpecimenBarcodePrint();
const { printTreatmentExecuteSheet } = useTreatmentExecuteSheetPrint();
const { printNursePrescriptions } = useNursePrescriptionPrint();
const { printMedicationLabels: printSkinTestLabels } = useSkinTestLabelPrint();
const { printMedicationLabels: printNebulizationLabels } = useNebulizationLabelPrint();
const { printServiceApplyForm: printProcedureApplyForm } = useProcedureApplyFormPrint();

const cancelExecuting = ref(false);
const cancelProcedureDialogOpen = ref(false);
const cancelProcedureRecords = ref<NetRemainingProcedureRecordItemDto[]>([]);
const cancelProcedureItemLabel = ref('');
const queueLoading = ref(false);

const patients = ref<NursePatient[]>([]);
const executeItems = ref<NurseAdviceExecuteRow[]>([]);
const supplyItems = ref<SupplyItem[]>([]);

/** 服务端执行单读模型映射后的记录 */
const serverActionRecords = ref<ActionRecord[]>([]);
/** 本机打印与批量成功等补充记录 */
const localClientRecords = ref<ActionRecord[]>([]);

const currentNurseName = computed(() => {
  const u = userStore.userInfo;
  if (!u) return '—';
  return u.realName || u.name || u.userName || u.username || '—';
});

/** 解析记录时间字符串用于合并排序 */
function actionRecordTimeKey(t: string): number {
  const n = Date.parse(t.replace(/-/g, '/'));
  return Number.isNaN(n) ? 0 : n;
}

const mergedActionRecords = computed(() => {
  const list = [...serverActionRecords.value, ...localClientRecords.value];
  list.sort((a, b) => actionRecordTimeKey(b.time) - actionRecordTimeKey(a.time));
  return list;
});

const currentPatient = computed(() => {
  if (!selectedPatientId.value) return null;
  return patients.value.find((item) => item.id === selectedPatientId.value) ?? null;
});

const nurseCurrentPatientInfo = computed((): PatientInfoBarPayload => {
  const p = currentPatient.value;
  if (!p) {
    return {
      patientName: '',
      genderEnum: undefined,
      ageText: '',
      phone: '',
      medicalRecordNo: '',
      serviceTypeName: '',
      startTime: '',
    };
  }
  return {
    encounterId: p.id,
    patientName: p.name ?? '',
    genderEnumText: p.genderEnumText ?? null,
    genderEnum: undefined,
    ageText: p.age,
    phone: p.phone ?? '',
    medicalRecordNo: p.medicalRecordNo ?? '',
    serviceTypeName: p.paymentType ?? '',
    startTime: (p.registrationTime || p.admissionTime || '').trim(),
  };
});

const nursePatientDoctorName = computed(() => {
  const p = currentPatient.value;
  if (!p) return '';
  return (p.doctor ?? '').trim() || '—';
});

const recentActionRecords = computed(() => mergedActionRecords.value.slice(0, 5));

const handleSelectPatient = (patient: { id: string }) => {
  selectedPatientId.value = patient.id;
};

const clearExecuteTableCheckbox = async () => {
  await executeCenterRef.value?.clearTableSelection();
};

const handleExecuteSelectionChange = (keys: string[]) => {
  if (isApplyingDefaultExecuteSelection.value) {
    return;
  }
  selectedExecuteKeys.value = keys;
};

const totalSelectedCount = computed(() => {
  return selectedExecuteKeys.value.length + selectedSupplyIds.value.length;
});

const totalPendingCount = computed(() => {
  const pendingExecute = executeItems.value.filter(
    (item) => !isNurseExecuteMedicationRow(item) && item.status !== '已完成',
  ).length;
  const pendingSupply = supplyItems.value.filter((item) => item.status === '待发放').length;
  return pendingExecute + pendingSupply;
});

const canExecuteSelected = computed(() => {
  return (
    (selectedExecuteKeys.value.length > 0 || selectedSupplyIds.value.length > 0) &&
    !batchPerforming.value
  );
});

const filteredActionRecords = computed(() => {
  if (recordTypeFilter.value === '全部') {
    return mergedActionRecords.value;
  }
  return mergedActionRecords.value.filter((item) => item.type === recordTypeFilter.value);
});

const createTimeText = () => {
  const now = new Date();
  const year = now.getFullYear();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  const hour = `${now.getHours()}`.padStart(2, '0');
  const minute = `${now.getMinutes()}`.padStart(2, '0');
  const second = `${now.getSeconds()}`.padStart(2, '0');
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
};

const appendRecord = (payload: Omit<ActionRecord, 'id' | 'operator' | 'result' | 'time'>) => {
  localClientRecords.value.unshift({
    id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    time: createTimeText(),
    operator: currentNurseName.value,
    result: '成功',
    ...payload,
  });
};

/** 拉取护士站患者队列（按科室与就诊日） */
async function loadPatientQueue() {
  const organizationId = (authStore.getSelectedOrganization() ?? '').trim();
  if (!organizationId) {
    ElMessage.warning('请先选择科室');
    patients.value = [];
    selectedPatientId.value = '';
    return;
  }
  queueLoading.value = true;
  try {
    const day = selectedQueueListDate.value.trim();
    const { startDate, endDate } = getLocalDayRangeDateTimeStrings(day);
    if (!startDate || !endDate) {
      ElMessage.warning('请选择有效的就诊日期');
      patients.value = [];
      selectedPatientId.value = '';
      return;
    }
    const res = await httpPageNurseStationPatientQueue({
      organizationId,
      keyword: keyword.value.trim() || undefined,
      startDate,
      endDate,
      pageNo: 1,
      pageSize: 50,
    });
    const { content: queueRows } = parsePagedRowsFromApiData<NurseStationPatientQueueItemDto>(res);
    patients.value = queueRows.map((r, i) => mapQueueItemToNursePatient(r, i));
    if (patients.value.length) {
      const still = patients.value.some((p) => p.id === selectedPatientId.value);
      if (!still) {
        selectedPatientId.value = patients.value[0]?.id ?? '';
      }
    } else {
      selectedPatientId.value = '';
    }
  } finally {
    queueLoading.value = false;
  }
}

const debouncedReloadQueueByKeyword = useDebounceFn(() => {
  void loadPatientQueue();
}, 350);

function defaultSelectableExecuteKeys(): string[] {
  return executeItems.value
    .filter((row) => !isNurseExecuteMedicationRow(row) && row.status !== '已完成')
    .map((r) => r.key);
}

function defaultSelectableSupplyIds(): string[] {
  return supplyItems.value.filter((s) => s.status === '待发放').map((s) => s.id);
}

/** 同步表格勾选状态（会触发子表 clear/toggle，期间忽略 selection-change） */
async function applyDefaultExecuteTableSelection(keys: string[]) {
  isApplyingDefaultExecuteSelection.value = true;
  try {
    await executeCenterRef.value?.setTableSelectionForKeys(keys);
    selectedExecuteKeys.value = [...keys];
  } finally {
    isApplyingDefaultExecuteSelection.value = false;
  }
}

/** 按就诊加载处置单与耗材侧栏；加载完成后默认勾选全部可执行诊疗与待发放耗材 */
async function loadEncounterTreatmentSheet(encounterId: string) {
  if (!encounterId.trim()) {
    executeItems.value = [];
    supplyItems.value = [];
    return;
  }
  executeTableLoading.value = true;
  try {
    const res = await httpQueryEncounterTreatmentSheet({ encounterId });
    executeItems.value = mapTreatmentSheetToExecuteRows(
      res.serviceLines ?? [],
      res.medicationLines ?? [],
    );
    supplyItems.value = mapDeviceLinesToSupplyItems(res.deviceLines ?? []);
  } finally {
    executeTableLoading.value = false;
  }
  await nextTick();
  selectedSupplyIds.value = [...defaultSelectableSupplyIds()];
  const keys = defaultSelectableExecuteKeys();
  await applyDefaultExecuteTableSelection(keys);
}

/** 加载执行记录抽屉数据源 */
async function loadServerExecutionLog(encounterId: string) {
  if (!encounterId.trim()) {
    serverActionRecords.value = [];
    return;
  }
  try {
    const res = await httpListProcedureRecordsForNurseStation({
      encounterId,
      pageNo: 1,
      pageSize: 100,
    });
    const { content: execRows } = parsePagedRowsFromApiData<OrderExecutionSheetItemDto>(res);
    serverActionRecords.value = execRows.map(mapOrderExecutionToActionRecord);
  } catch {
    serverActionRecords.value = [];
  }
}

watch(
  () => selectedPatientId.value,
  async (id) => {
    await clearSelectedItems();
    await loadEncounterTreatmentSheet(id);
    void loadServerExecutionLog(id);
  },
);

watch(selectedQueueListDate, () => {
  void loadPatientQueue();
});

watch(keyword, () => {
  void debouncedReloadQueueByKeyword();
});

onMounted(() => {
  void loadPatientQueue();
});

/** 提交所选诊疗/药品与耗材发放 */
async function executeSelectedItems() {
  const encounterId = selectedPatientId.value.trim();
  const operatorId = String(userStore.userInfo?.id ?? '').trim();
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!operatorId) {
    ElMessage.warning('当前登录用户无效，请重新登录');
    return;
  }
  const {
    serviceRequestIds,
    medicationRequestIds,
    deviceDispenseIds: fromExecute,
  } = parseExecuteKeysToBatchIds(selectedExecuteKeys.value);
  const fromSupply = [...selectedSupplyIds.value];
  const deviceDispenseIds = [...new Set([...fromExecute, ...fromSupply])];
  const hasAny =
    serviceRequestIds.length > 0 || medicationRequestIds.length > 0 || deviceDispenseIds.length > 0;
  if (!hasAny) {
    ElMessage.warning('请至少选择一项再执行');
    return;
  }

  const roomRaw = (selectedSupplyRoomId.value ?? '').trim();
  const inventoryLocationId = /^\d+$/.test(roomRaw) ? roomRaw : undefined;

  batchPerforming.value = true;
  try {
    await httpBatchPerformDisposition({
      encounterId,
      operatorId,
      serviceRequestIds: serviceRequestIds.length ? serviceRequestIds : undefined,
      medicationRequestIds: medicationRequestIds.length ? medicationRequestIds : undefined,
      deviceDispenseIds: deviceDispenseIds.length ? deviceDispenseIds : undefined,
      ...(inventoryLocationId ? { inventoryLocationId } : {}),
    });
    ElMessage.success('执行成功');
    const roomNote = selectedSupplyRoomLabel.value
      ? `；耗材房：${selectedSupplyRoomLabel.value}`
      : '';
    appendRecord({
      type: '执行',
      action: '执行并记录',
      target: `处置${serviceRequestIds.length + medicationRequestIds.length}项，耗材${deviceDispenseIds.length}项${roomNote}`,
    });
    selectedExecuteKeys.value = [];
    selectedSupplyIds.value = [];
    await clearExecuteTableCheckbox();
    await loadEncounterTreatmentSheet(encounterId);
    await loadServerExecutionLog(encounterId);
    await loadPatientQueue();
  } finally {
    batchPerforming.value = false;
  }
}

const clearSelectedItems = async () => {
  selectedExecuteKeys.value = [];
  selectedSupplyIds.value = [];
  await clearExecuteTableCheckbox();
};

const handleQueueListDateChange = (date: string | undefined) => {
  selectedQueueListDate.value = date || defaultQueueListDate;
};

const toggleSupplySelection = (id: string, checked: boolean) => {
  const target = supplyItems.value.find((item) => item.id === id);
  if (!target || target.status !== '待发放') {
    return;
  }
  if (checked) {
    if (!selectedSupplyIds.value.includes(id)) {
      selectedSupplyIds.value.push(id);
    }
    return;
  }
  selectedSupplyIds.value = selectedSupplyIds.value.filter((itemId) => itemId !== id);
};

/** 提交 IF-NUR-05：诊疗 procedure 与/或耗材发放回退 */
async function submitBatchCancel(params: {
  deviceDispenseIds?: string[];
  procedureIds?: string[];
  targetLabel: string;
}) {
  const encounterId = selectedPatientId.value.trim();
  const operatorRaw = userStore.userInfo?.id;
  const operatorId = (preserveApiIdForRequest(operatorRaw) ?? String(operatorRaw ?? '')).trim();
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!operatorId) {
    ElMessage.warning('当前登录用户无效，请重新登录');
    return;
  }
  const procedureIds = params.procedureIds ?? [];
  const deviceDispenseIds = params.deviceDispenseIds ?? [];
  if (procedureIds.length === 0 && deviceDispenseIds.length === 0) {
    ElMessage.warning('请至少选择一项可取消内容');
    return;
  }
  cancelExecuting.value = true;
  try {
    await httpBatchCancelDisposition({
      encounterId,
      operatorId,
      statusReasonText: DEFAULT_CANCEL_STATUS_REASON,
      procedureIds: procedureIds.length ? procedureIds : undefined,
      deviceDispenseIds: deviceDispenseIds.length ? deviceDispenseIds : undefined,
    });
    ElMessage.success('取消执行成功');
    appendRecord({
      type: '执行',
      action: '取消执行',
      target: params.targetLabel,
    });
    cancelProcedureDialogOpen.value = false;
    await loadEncounterTreatmentSheet(encounterId);
    await loadServerExecutionLog(encounterId);
    await loadPatientQueue();
  } finally {
    cancelExecuting.value = false;
  }
}

/** 提交 procedureIds 批量取消执行 */
async function submitBatchCancelProcedure(procedureIds: string[]) {
  await submitBatchCancel({
    procedureIds,
    targetLabel: cancelProcedureItemLabel.value || `共 ${procedureIds.length} 条`,
  });
}

/** 侧栏耗材取消执行（PREPARED / COMPLETED / PART_COMPLETED） */
async function handleCancelSupplyExecute(supplyId: string) {
  const item = supplyItems.value.find((row) => row.id === supplyId);
  if (!item?.canCancelExecute) {
    return;
  }
  const dispenseId = resolveSupplyDispenseIdForApi(item);
  if (!dispenseId) {
    ElMessage.warning('发放单无效，无法取消');
    return;
  }
  await submitBatchCancel({
    deviceDispenseIds: [dispenseId],
    targetLabel: item.name?.trim() || '耗材',
  });
}

/** 拉取当前诊疗项目净剩可取消执行记录 */
async function loadNetRemainingProcedureRecords(
  row: NurseAdviceExecuteRow,
): Promise<NetRemainingProcedureRecordItemDto[]> {
  const encounterId = selectedPatientId.value.trim();
  const requestId = (row.requestId ?? '').trim();
  const requestTable = normalizeNurseStationRequestTableForApi(row.requestTable ?? '');
  if (!encounterId || !requestId || !requestTable) {
    return [];
  }
  const res = await httpListNetRemainingProcedureRecordsForNurseStation({
    encounterId,
    requestId,
    requestTable,
  });
  return res.records ?? [];
}

/** 行内「取消执行」：仅 1 条记录直接取消，多条打开勾选弹窗 */
async function handleCancelExecute(row: NurseAdviceExecuteRow) {
  if (!row.canCancelExecute) {
    return;
  }
  const encounterId = selectedPatientId.value.trim();
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  cancelProcedureItemLabel.value = row.content?.trim() || row.name?.trim() || '诊疗项目';
  cancelExecuting.value = true;
  let records: NetRemainingProcedureRecordItemDto[] = [];
  try {
    records = await loadNetRemainingProcedureRecords(row);
  } catch {
    return;
  } finally {
    cancelExecuting.value = false;
  }
  if (records.length === 0) {
    ElMessage.warning('暂无可取消的执行记录');
    return;
  }
  if (records.length === 1) {
    const onlyId = String(records[0]?.procedureId ?? '').trim();
    if (!onlyId) {
      ElMessage.warning('执行记录无效');
      return;
    }
    await submitBatchCancelProcedure([onlyId]);
    return;
  }
  cancelProcedureRecords.value = records;
  cancelProcedureDialogOpen.value = true;
}

/** 弹窗内多选后直接取消 */
async function handleCancelProcedureDialogConfirm(procedureIds: string[]) {
  await submitBatchCancelProcedure(procedureIds);
}

const handlePrint = async (label: string) => {
  if (!currentPatient.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const selectedCount = totalSelectedCount.value;
  const patientName = currentPatient.value.name || '-';
  const targetText =
    selectedCount > 0
      ? `${patientName}（所选${selectedCount}项）`
      : `${patientName}（全部待执行项）`;

  if (label === '打印瓶贴') {
    const groups = groupInfusionLabelPrintTargets(
      executeItems.value,
      selectedExecuteKeys.value.length > 0 ? selectedExecuteKeys.value : undefined,
    );
    if (!groups.length) {
      ElMessage.warning('没有可打印的药品医嘱');
      return;
    }
    try {
      await printIvInfusionLabels({
        encounterId: currentPatient.value.id,
        groups,
      });
      appendRecord({
        type: '打印',
        action: label,
        target: `${targetText} · ${groups.length} 组`,
      });
      ElMessage.success(`已发送 ${groups.length} 张输液瓶贴`);
    } catch {
      // composable 已提示
    }
    return;
  }

  if (label === '打印采血码') {
    const targets = groupSpecimenBarcodePrintTargets(
      executeItems.value,
      selectedExecuteKeys.value.length > 0 ? selectedExecuteKeys.value : undefined,
    );
    if (!targets.length) {
      ElMessage.warning('没有可打印的采血/检验医嘱');
      return;
    }
    try {
      await printBedsideSpecimenBarcodes({
        encounterId: currentPatient.value.id,
        targets,
      });
      appendRecord({
        type: '打印',
        action: label,
        target: `${targetText} · ${targets.length} 张`,
      });
      ElMessage.success(`已发送 ${targets.length} 张采血条码`);
    } catch {
      // composable 已提示
    }
    return;
  }

  if (label === '打印处置单') {
    const target = groupTreatmentExecutePrintTargets(
      executeItems.value,
      selectedExecuteKeys.value.length > 0 ? selectedExecuteKeys.value : undefined,
    );
    if (!target) {
      ElMessage.warning('没有可打印的处置医嘱');
      return;
    }
    try {
      await printTreatmentExecuteSheet({
        encounterId: currentPatient.value.id,
        target,
      });
      appendRecord({
        type: '打印',
        action: label,
        target: `${targetText} · ${target.serviceRequestIds.length} 项`,
      });
      ElMessage.success('处置执行单已发送打印');
    } catch {
      // composable 已提示
    }
    return;
  }

  if (label === '打印处方单') {
    const medicationRequestIds = groupMedicationExecuteRequestIds(
      executeItems.value,
      selectedExecuteKeys.value.length > 0 ? selectedExecuteKeys.value : undefined,
    );
    if (!medicationRequestIds.length) {
      ElMessage.warning('没有可打印的药品医嘱');
      return;
    }
    try {
      const printedCount = await printNursePrescriptions({
        encounterId: currentPatient.value.id,
        medicationRequestIds,
        executeRows: executeItems.value,
      });
      if (printedCount <= 0) {
        return;
      }
      appendRecord({
        type: '打印',
        action: label,
        target: `${targetText} · ${printedCount} 张`,
      });
      ElMessage.success(`已发送 ${printedCount} 张处方单`);
    } catch {
      // composable 已提示
    }
    return;
  }

  if (label === '打印皮试标签') {
    const groups = groupMedicationLabelPrintTargets(
      executeItems.value,
      selectedExecuteKeys.value.length > 0 ? selectedExecuteKeys.value : undefined,
      'skin_test',
    );
    if (!groups.length) {
      ElMessage.warning('没有可打印的皮试医嘱');
      return;
    }
    try {
      await printSkinTestLabels({
        encounterId: currentPatient.value.id,
        groups,
      });
      appendRecord({
        type: '打印',
        action: label,
        target: `${targetText} · ${groups.length} 张`,
      });
      ElMessage.success(`已发送 ${groups.length} 张皮试标签`);
    } catch {
      // composable 已提示
    }
    return;
  }

  if (label === '打印雾化标签') {
    const groups = groupMedicationLabelPrintTargets(
      executeItems.value,
      selectedExecuteKeys.value.length > 0 ? selectedExecuteKeys.value : undefined,
      'nebulization',
    );
    if (!groups.length) {
      ElMessage.warning('没有可打印的雾化/吸入医嘱');
      return;
    }
    try {
      await printNebulizationLabels({
        encounterId: currentPatient.value.id,
        groups,
      });
      appendRecord({
        type: '打印',
        action: label,
        target: `${targetText} · ${groups.length} 张`,
      });
      ElMessage.success(`已发送 ${groups.length} 张雾化标签`);
    } catch {
      // composable 已提示
    }
    return;
  }

  if (label === '打印处置申请单') {
    const target = groupProcedureApplyPrintTargets(
      executeItems.value,
      selectedExecuteKeys.value.length > 0 ? selectedExecuteKeys.value : undefined,
    );
    if (!target) {
      ElMessage.warning('没有可打印的治疗/处置医嘱');
      return;
    }
    try {
      await printProcedureApplyForm({
        encounterId: currentPatient.value.id,
        target,
      });
      appendRecord({
        type: '打印',
        action: label,
        target: `${targetText} · ${target.serviceRequestIds.length} 项`,
      });
      ElMessage.success('治疗/处置申请单已发送打印');
    } catch {
      // composable 已提示
    }
    return;
  }

  appendRecord({
    type: '打印',
    action: label,
    target: targetText,
  });
  ElMessage.success(`${label}任务已发送`);
};
</script>

<template>
  <StationTriPaneLayout class="nurse-station-page">
    <template #left>
      <NurseQueuePanel
        v-model:keyword="keyword"
        v-model:queue-date="selectedQueueListDate"
        :filtered-patients="patients"
        :queue-loading="queueLoading"
        :selected-patient-id="selectedPatientId"
        @queue-date-change="handleQueueListDateChange"
        @refresh="loadPatientQueue"
        @select-patient="handleSelectPatient"
      />
    </template>

    <template #center>
      <NurseExecuteCenter
        ref="executeCenterRef"
        :execute-items="executeItems"
        :execute-table-loading="executeTableLoading"
        :current-patient="currentPatient"
        :patient="nurseCurrentPatientInfo"
        :doctor-name="nursePatientDoctorName"
        :can-execute-selected="canExecuteSelected"
        @selection-change="handleExecuteSelectionChange"
        @execute="executeSelectedItems"
        @cancel-execute="handleCancelExecute"
      />
    </template>

    <template #right>
      <NurseRightSidebar
        v-model:supply-room-id="selectedSupplyRoomId"
        :supply-items="supplyItems"
        :selected-supply-ids="selectedSupplyIds"
        :supply-room-options="supplyRoomOptions"
        :recent-action-records="recentActionRecords"
        @supply-change="toggleSupplySelection"
        @cancel-supply="handleCancelSupplyExecute"
        @print="handlePrint"
        @show-all-records="showRecordDrawer = true"
      />
    </template>

    <template #footer>
      <NurseFooterBar
        :selected-execute-count="selectedExecuteKeys.length"
        :selected-supply-count="selectedSupplyIds.length"
        :total-pending-count="totalPendingCount"
        :total-selected-count="totalSelectedCount"
        :can-execute-selected="canExecuteSelected"
        @clear="clearSelectedItems"
        @execute="executeSelectedItems"
      />
    </template>

    <template #drawer>
      <NurseActionRecordDrawer
        v-model:open="showRecordDrawer"
        v-model:record-type-filter="recordTypeFilter"
        :records="filteredActionRecords"
      />
      <NurseCancelProcedureDialog
        v-model:open="cancelProcedureDialogOpen"
        :records="cancelProcedureRecords"
        :item-label="cancelProcedureItemLabel"
        :submitting="cancelExecuting"
        @confirm="handleCancelProcedureDialogConfirm"
      />
    </template>
  </StationTriPaneLayout>
</template>

<style scoped>
.nurse-station-page :deep(.execute-primary-btn) {
  min-width: 7.5rem;
  font-weight: 600;
}
</style>
