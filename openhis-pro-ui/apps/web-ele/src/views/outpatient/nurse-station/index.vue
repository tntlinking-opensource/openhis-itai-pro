<script setup lang="ts">
import type {
  ActionRecord,
  NurseAdviceExecuteRow,
  NursePatient,
  SupplyItem,
  SupplyRoomOption,
} from './components/types';
import type { NurseSkinTestRowDraft } from './services/map-nurse-skin-test';

import type {
  NetRemainingProcedureRecordItemDto,
  NurseStationPatientQueueItemDto,
  NurseStationTreatmentLineDto,
  OrderExecutionSheetItemDto,
  OutpatientSkinTestEnumOptionDto,
  OutpatientSkinTestRowDto,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import { useDebounceFn } from '@vueuse/core';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  httpBatchCancelDisposition,
  httpBatchPerformDisposition,
  httpDeleteOutpatientSkinTestRecord,
  httpListManagedSupplyRoomOptions,
  httpListNetRemainingProcedureRecordsForNurseStation,
  httpListOutpatientSkinTestRows,
  httpListProcedureRecordsForNurseStation,
  httpLoadOutpatientSkinTestInit,
  httpPageNurseStationPatientQueue,
  httpQueryEncounterTreatmentSheet,
  httpSaveOutpatientSkinTestRecord,
} from '#/api/outpatient-nurse-station/outpatient-nurse-station.http';
import { usePrintClientWarmup } from '#/composables/use-print-client-warmup';
import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { getLocalDayRangeDateTimeStrings } from '#/utils/format/date-range-quick';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  printInfusionLabel,
  printNursePrescription,
  printTreatmentSheet,
} from '#/utils/print/print-nurse-station';
import {
  groupMedicationExecuteRequestIds,
  groupMedicationLabelPrintTargets,
  groupProcedureApplyPrintTargets,
  groupSpecimenBarcodePrintTargets,
} from '#/utils/print/nurse-station-print-targets';

import NurseActionRecordDrawer from './components/nurse-action-record-drawer.vue';
import NurseCancelProcedureDialog from './components/nurse-cancel-procedure-dialog.vue';
import NurseFooterBar from './components/nurse-footer-bar.vue';
import NurseQueuePanel from './components/nurse-queue-panel.vue';
import NurseRightSidebar from './components/nurse-right-sidebar.vue';
import NurseWorkstationCenter from './components/nurse-workstation-center.vue';
import { mapSkinTestDraftToSaveRequest } from './services/map-nurse-skin-test';
import {
  isNurseExecuteMedicationRow,
  mapDeviceLinesToSupplyItems,
  mapOrderExecutionToActionRecord,
  mapQueueItemToNursePatient,
  mapTreatmentSheetToExecuteRows,
  normalizeNurseStationRequestTableForApi,
  parseExecuteKeysToBatchIds,
  parseTreatmentSheetFromApiData,
  resolveSupplyDispenseIdForApi,
} from './services/map-nurse-station-api';

defineOptions({
  name: 'OutpatientNurseStation',
});

usePrintClientWarmup();

/** IF-NUR-05 必填取消原因，前端不再弹窗采集 */
const DEFAULT_CANCEL_STATUS_REASON = '护士站取消执行';

/** 就诊 id 写入请求体：禁止 Number 转换导致雪花 Long 丢精度 */
function resolveEncounterIdForApi(raw: string): string {
  return preserveApiIdForRequest(raw) ?? '';
}

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

const supplyRoomOptions = ref<SupplyRoomOption[]>([]);

const showRecordDrawer = ref(false);
const recordTypeFilter = ref<'全部' | '打印' | '执行'>('全部');
const workstationCenterRef = ref<InstanceType<typeof NurseWorkstationCenter> | null>(null);
const activeWorkbenchTab = ref<'execute' | 'skintest'>('execute');
const executeTableLoading = ref(false);
const batchPerforming = ref(false);

const cancelExecuting = ref(false);
const cancelProcedureDialogOpen = ref(false);
const cancelProcedureRecords = ref<NetRemainingProcedureRecordItemDto[]>([]);
const cancelProcedureItemLabel = ref('');
const queueLoading = ref(false);

const patients = ref<NursePatient[]>([]);
const executeItems = ref<NurseAdviceExecuteRow[]>([]);
const supplyItems = ref<SupplyItem[]>([]);
/** 当前就诊处置单原始读模型（打印 mapper 入参） */
const treatmentSheetRaw = ref<{
  deviceLines: NurseStationTreatmentLineDto[];
  medicationLines: NurseStationTreatmentLineDto[];
  serviceLines: NurseStationTreatmentLineDto[];
}>({
  serviceLines: [],
  medicationLines: [],
  deviceLines: [],
});

const printSubmitting = ref(false);

const skinTestRows = ref<OutpatientSkinTestRowDto[]>([]);
const skinTestTableLoading = ref(false);
const skinTestInitLoading = ref(false);
const savingSkinTestServiceRequestId = ref('');
const deletingSkinTestAllergyRecordId = ref('');
const clinicalStatusOptions = ref<OutpatientSkinTestEnumOptionDto[]>([]);
const verificationStatusOptions = ref<OutpatientSkinTestEnumOptionDto[]>([]);
const criticalityOptions = ref<OutpatientSkinTestEnumOptionDto[]>([]);
const severityOptions = ref<OutpatientSkinTestEnumOptionDto[]>([]);
const practitionerOptions = ref<{ label: string; value: string }[]>([]);
/** 列表行带回的 patientId，用于保存时患者一致 Gate */
const skinTestPatientIdByEncounter = ref<Record<string, string>>({});

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
  const n = Date.parse(t.replaceAll('-', '/'));
  return Number.isNaN(n) ? 0 : n;
}

const mergedActionRecords = computed(() => {
  // 批量执行/取消已由 IF-NUR-03 服务端记录覆盖，过滤历史本机汇总行避免重复展示
  const localRows = localClientRecords.value.filter(
    (item) => item.action !== '执行并记录' && item.action !== '取消执行',
  );
  const list = [...serverActionRecords.value, ...localRows];
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

const defaultPractitionerId = computed(() => {
  return resolveLoginPractitionerIdForRequest(userStore.userInfo) ?? '';
});

const showExecuteFooter = computed(() => activeWorkbenchTab.value === 'execute');

const showSupplySection = computed(() => activeWorkbenchTab.value === 'execute');

const clearExecuteTableCheckbox = async () => {
  await workstationCenterRef.value?.clearTableSelection();
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

function resolvePrintHospitalName(): string {
  const userInfo = userStore.userInfo as null | Record<string, unknown> | undefined;
  if (!userInfo) {
    return '';
  }
  const candidates = [userInfo.hospitalName, userInfo.tenantName, userInfo.orgName];
  for (const item of candidates) {
    const text = String(item ?? '').trim();
    if (text) {
      return text;
    }
  }
  return '';
}

function buildNursePrintContext() {
  const patient = currentPatient.value;
  const info = nurseCurrentPatientInfo.value;
  return {
    hospitalName: resolvePrintHospitalName(),
    patientName: patient?.name ?? info.patientName ?? '',
    ageText: patient?.age ?? info.ageText ?? '',
    genderEnumText: patient?.genderEnumText ?? info.genderEnumText ?? '',
    feeNatureText: patient?.paymentType ?? info.serviceTypeName ?? '',
    orderDateText: (patient?.registrationTime ?? info.startTime ?? '').trim(),
    encounterBusNo: (patient?.encounterBusNo ?? '').trim(),
    orderingPractitionerName:
      nursePatientDoctorName.value === '—' ? '' : nursePatientDoctorName.value,
    registerTimeText: (patient?.registrationTime ?? info.startTime ?? '').trim(),
    executorName: currentNurseName.value === '—' ? '' : currentNurseName.value,
    executeTimeText: createTimeText(),
  };
}

const appendRecord = (payload: Omit<ActionRecord, 'id' | 'operator' | 'result' | 'time'>) => {
  const operatorLabel =
    String(payload.operatorLabel ?? '').trim() ||
    (payload.action === '取消执行' ? '取消执行人' : '执行人');
  localClientRecords.value.unshift({
    id: `local-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
    time: createTimeText(),
    operator: currentNurseName.value,
    result: '成功',
    ...payload,
    operatorLabel,
  });
};

/** 拉取护士站患者队列（按科室与就诊日） */
async function loadPatientQueue() {
  const organizationId = preserveApiIdForRequest(authStore.getSelectedOrganization()) ?? '';
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
    if (patients.value.length > 0) {
      const still = patients.value.some((p) => p.id === selectedPatientId.value);
      if (still) {
        const encounterId = resolveEncounterIdForApi(selectedPatientId.value);
        if (encounterId) {
          void loadEncounterTreatmentSheet(encounterId);
        }
      } else {
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
    await workstationCenterRef.value?.setTableSelectionForKeys(keys);
    selectedExecuteKeys.value = [...keys];
  } finally {
    isApplyingDefaultExecuteSelection.value = false;
  }
}

/** 按就诊加载处置单与耗材侧栏；加载完成后默认勾选全部可执行诊疗与待发放耗材 */
async function loadEncounterTreatmentSheet(encounterId: string) {
  const normalizedEncounterId = resolveEncounterIdForApi(encounterId);
  if (!normalizedEncounterId) {
    executeItems.value = [];
    supplyItems.value = [];
    treatmentSheetRaw.value = { serviceLines: [], medicationLines: [], deviceLines: [] };
    return;
  }
  executeTableLoading.value = true;
  try {
    const res = await httpQueryEncounterTreatmentSheet({
      encounterId: normalizedEncounterId,
    });
    const sheet = parseTreatmentSheetFromApiData(res);
    treatmentSheetRaw.value = {
      serviceLines: sheet.serviceLines,
      medicationLines: sheet.medicationLines,
      deviceLines: sheet.deviceLines,
    };
    executeItems.value = mapTreatmentSheetToExecuteRows(sheet.serviceLines, sheet.medicationLines);
    supplyItems.value = mapDeviceLinesToSupplyItems(sheet.deviceLines);
  } catch {
    executeItems.value = [];
    supplyItems.value = [];
    treatmentSheetRaw.value = { serviceLines: [], medicationLines: [], deviceLines: [] };
  } finally {
    executeTableLoading.value = false;
  }
  await nextTick();
  selectedSupplyIds.value = [...defaultSelectableSupplyIds()];
  const keys = defaultSelectableExecuteKeys();
  await applyDefaultExecuteTableSelection(keys);
}

/** 加载登录者管辖药房作为耗材房下拉 */
async function loadSupplyRoomOptions() {
  try {
    const res = await httpListManagedSupplyRoomOptions();
    supplyRoomOptions.value = (res.supplyRoomOptions ?? [])
      .map((opt) => {
        const value = preserveApiIdForRequest(opt.value) ?? '';
        return {
          value,
          label: (opt.label ?? '').trim() || value,
        };
      })
      .filter((opt) => opt.value.length > 0);
  } catch {
    supplyRoomOptions.value = [];
  }
}

/** 加载皮试表单枚举与护士候选 */
async function loadSkinTestPageInit() {
  skinTestInitLoading.value = true;
  try {
    const initRes = await httpLoadOutpatientSkinTestInit();
    clinicalStatusOptions.value = initRes.clinicalStatusOptions ?? [];
    verificationStatusOptions.value = initRes.verificationStatusOptions ?? [];
    criticalityOptions.value = initRes.criticalityOptions ?? [];
    severityOptions.value = initRes.severityOptions ?? [];
    practitionerOptions.value = (initRes.nurseOptions ?? [])
      .map((opt) => {
        const value = preserveApiIdForRequest(opt.value) ?? '';
        return {
          value,
          label: (opt.label ?? '').trim() || value,
        };
      })
      .filter((opt) => opt.value.length > 0);
  } catch {
    clinicalStatusOptions.value = [];
    verificationStatusOptions.value = [];
    criticalityOptions.value = [];
    severityOptions.value = [];
    practitionerOptions.value = [];
  } finally {
    skinTestInitLoading.value = false;
  }
}

/** 按就诊加载皮试待登记/已登记列表 */
async function loadSkinTestRows(encounterId: string) {
  const normalizedEncounterId = resolveEncounterIdForApi(encounterId);
  if (!normalizedEncounterId) {
    skinTestRows.value = [];
    return;
  }
  skinTestTableLoading.value = true;
  try {
    const cachedPatientId = skinTestPatientIdByEncounter.value[normalizedEncounterId];
    const res = await httpListOutpatientSkinTestRows({
      encounterId: normalizedEncounterId,
      patientId: cachedPatientId || undefined,
    });
    const rows = res.rows ?? [];
    skinTestRows.value = rows;
    const patientIdFromRow = String(rows[0]?.patientId ?? '').trim();
    if (patientIdFromRow) {
      skinTestPatientIdByEncounter.value = {
        ...skinTestPatientIdByEncounter.value,
        [normalizedEncounterId]: patientIdFromRow,
      };
    }
  } catch {
    skinTestRows.value = [];
  } finally {
    skinTestTableLoading.value = false;
  }
}

/** 保存单行皮试登记 */
async function handleSaveSkinTestRow(draft: NurseSkinTestRowDraft) {
  const encounterId = resolveEncounterIdForApi(selectedPatientId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const patientId =
    preserveApiIdForRequest(draft.patientId) ??
    skinTestPatientIdByEncounter.value[encounterId] ??
    '';
  if (!patientId) {
    ElMessage.warning('患者信息无效，请刷新列表');
    return;
  }
  const body = mapSkinTestDraftToSaveRequest({ ...draft, patientId }, encounterId);
  savingSkinTestServiceRequestId.value = body.serviceRequestId;
  try {
    await httpSaveOutpatientSkinTestRecord(body);
    ElMessage.success('皮试结果已保存');
    appendRecord({
      type: '执行',
      action: '登记皮试',
      target: draft.medicationName || '皮试药品',
    });
    await loadSkinTestRows(encounterId);
  } finally {
    savingSkinTestServiceRequestId.value = '';
  }
}

/** 删除误录皮试记录 */
async function handleDeleteSkinTestRow(allergyRecordId: string) {
  const encounterId = resolveEncounterIdForApi(selectedPatientId.value);
  const recordId = preserveApiIdForRequest(allergyRecordId) ?? '';
  if (!encounterId || !recordId) {
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该误录的皮试结果？删除后不影响已收费用。', '删除皮试记录', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  deletingSkinTestAllergyRecordId.value = recordId;
  try {
    await httpDeleteOutpatientSkinTestRecord({ encounterId, allergyRecordId: recordId });
    ElMessage.success('皮试记录已删除');
    appendRecord({
      type: '执行',
      action: '删除皮试记录',
      target: recordId,
    });
    await loadSkinTestRows(encounterId);
  } finally {
    deletingSkinTestAllergyRecordId.value = '';
  }
}

/** 加载执行记录抽屉数据源 */
async function loadServerExecutionLog(encounterId: string) {
  const normalizedEncounterId = resolveEncounterIdForApi(encounterId);
  if (!normalizedEncounterId) {
    serverActionRecords.value = [];
    return;
  }
  try {
    const res = await httpListProcedureRecordsForNurseStation({
      encounterId: normalizedEncounterId,
      pageNo: 1,
      pageSize: 100,
    });
    const { content: execRows } = parsePagedRowsFromApiData<OrderExecutionSheetItemDto>(res);
    serverActionRecords.value = execRows.map((row) => mapOrderExecutionToActionRecord(row));
  } catch {
    serverActionRecords.value = [];
  }
}

watch(
  () => selectedPatientId.value,
  (id) => {
    void reloadSelectedPatientWorkbench(id);
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
  void loadSupplyRoomOptions();
  void loadSkinTestPageInit();
});

/** 提交所选诊疗/药品与耗材发放 */
async function executeSelectedItems() {
  const encounterId = resolveEncounterIdForApi(selectedPatientId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
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
      serviceRequestIds: serviceRequestIds.length > 0 ? serviceRequestIds : undefined,
      medicationRequestIds: medicationRequestIds.length > 0 ? medicationRequestIds : undefined,
      deviceDispenseIds: deviceDispenseIds.length > 0 ? deviceDispenseIds : undefined,
      ...(inventoryLocationId ? { inventoryLocationId } : {}),
    });
    ElMessage.success('执行成功');
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

/** 选中患者后加载处置单、执行记录与皮试（watch 与重复点击共用） */
async function reloadSelectedPatientWorkbench(encounterId: string) {
  await clearSelectedItems();
  await loadEncounterTreatmentSheet(encounterId);
  void loadServerExecutionLog(encounterId);
  void loadSkinTestRows(encounterId);
}

/** 选中卡片切换当前就诊；再次点击已选患者仍刷新处置单（避免 ref 不变导致 watch 不触发） */
const handleSelectPatient = (patient: { id: string }) => {
  const id = patient.id;
  if (selectedPatientId.value === id) {
    void reloadSelectedPatientWorkbench(id);
    return;
  }
  selectedPatientId.value = id;
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
  const encounterId = resolveEncounterIdForApi(selectedPatientId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
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
      statusReasonText: DEFAULT_CANCEL_STATUS_REASON,
      procedureIds: procedureIds.length > 0 ? procedureIds : undefined,
      deviceDispenseIds: deviceDispenseIds.length > 0 ? deviceDispenseIds : undefined,
    });
    ElMessage.success('取消执行成功');
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
  const encounterId = resolveEncounterIdForApi(selectedPatientId.value);
  const requestId = preserveApiIdForRequest(row.requestId) ?? '';
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

/** 侧栏打印按钮统一入口 */
async function handlePrint(label: string) {
  if (!currentPatient.value) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const encounterId = resolveEncounterIdForApi(selectedPatientId.value);
  if (!encounterId) {
    ElMessage.warning('请先选择患者');
    return;
  }
  const selectedCount = totalSelectedCount.value;
  const patientName = currentPatient.value.name || '—';
  const targetText =
    selectedCount > 0
      ? `${patientName}（所选${selectedCount}项）`
      : `${patientName}（全部待执行项）`;
  const selectedKeys = selectedExecuteKeys.value.length > 0 ? selectedExecuteKeys.value : undefined;

  if (label === '打印瓶贴') {
    printSubmitting.value = true;
    try {
      const ctx = buildNursePrintContext();
      const result = await printInfusionLabel({
        encounterId,
        source: {
          patientName: ctx.patientName,
          genderEnumText: ctx.genderEnumText,
          ageText: ctx.ageText,
          registerTimeText: ctx.registerTimeText,
          medicationLines: treatmentSheetRaw.value.medicationLines,
          executorName: ctx.executorName,
          executeTimeText: ctx.executeTimeText,
        },
      });
      if (result.ok) {
        appendRecord({
          type: '打印',
          action: label,
          target: `${targetText} · ${result.count} 张`,
        });
      }
    } finally {
      printSubmitting.value = false;
    }
    return;
  }

  if (label === '打印处置单') {
    printSubmitting.value = true;
    try {
      const ctx = buildNursePrintContext();
      const result = await printTreatmentSheet({
        encounterId,
        source: {
          ...ctx,
          serviceLines: treatmentSheetRaw.value.serviceLines,
          deviceLines: treatmentSheetRaw.value.deviceLines,
        },
      });
      if (result.ok) {
        appendRecord({
          type: '打印',
          action: label,
          target: targetText,
        });
      }
    } finally {
      printSubmitting.value = false;
    }
    return;
  }

  if (label === '打印处方单') {
    const medicationRequestIds = groupMedicationExecuteRequestIds(executeItems.value, selectedKeys);
    if (!medicationRequestIds.length) {
      ElMessage.warning('没有可打印的药品医嘱');
      return;
    }
    printSubmitting.value = true;
    try {
      const result = await printNursePrescription({
        encounterId,
        medicationRequestIds,
        executeRows: executeItems.value,
      });
      if (result.ok) {
        appendRecord({
          type: '打印',
          action: label,
          target: `${targetText} · ${result.count} 张`,
        });
        ElMessage.success(`已发送 ${result.count} 张处方单`);
      }
    } finally {
      printSubmitting.value = false;
    }
    return;
  }

  if (label === '打印采血码') {
    const targets = groupSpecimenBarcodePrintTargets(executeItems.value, selectedKeys);
    if (!targets.length) {
      ElMessage.warning('没有可打印的采血/检验医嘱');
      return;
    }
    ElMessage.warning('采血码打印模板暂未接入，请联系管理员配置');
    return;
  }

  if (label === '打印皮试标签') {
    const groups = groupMedicationLabelPrintTargets(executeItems.value, selectedKeys, 'skin_test');
    if (!groups.length) {
      ElMessage.warning('没有可打印的皮试医嘱');
      return;
    }
    ElMessage.warning('皮试标签打印模板暂未接入，请联系管理员配置');
    return;
  }

  if (label === '打印雾化标签') {
    const groups = groupMedicationLabelPrintTargets(
      executeItems.value,
      selectedKeys,
      'nebulization',
    );
    if (!groups.length) {
      ElMessage.warning('没有可打印的雾化/吸入医嘱');
      return;
    }
    ElMessage.warning('雾化标签打印模板暂未接入，请联系管理员配置');
    return;
  }

  if (label === '打印处置申请单') {
    const target = groupProcedureApplyPrintTargets(executeItems.value, selectedKeys);
    if (!target) {
      ElMessage.warning('没有可打印的治疗/处置医嘱');
      return;
    }
    ElMessage.warning('处置申请单打印模板暂未接入，请联系管理员配置');
    return;
  }

  ElMessage.warning('未知打印类型');
}

/** 行内「取消执行」：仅 1 条记录直接取消，多条打开勾选弹窗 */
async function handleCancelExecute(row: NurseAdviceExecuteRow) {
  if (!row.canCancelExecute) {
    return;
  }
  const encounterId = resolveEncounterIdForApi(selectedPatientId.value);
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
    const onlyId = preserveApiIdForRequest(records[0]?.procedureId) ?? '';
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
      <NurseWorkstationCenter
        ref="workstationCenterRef"
        v-model:active-tab="activeWorkbenchTab"
        :execute-items="executeItems"
        :execute-table-loading="executeTableLoading"
        :current-patient="currentPatient"
        :patient="nurseCurrentPatientInfo"
        :doctor-name="nursePatientDoctorName"
        :can-execute-selected="canExecuteSelected"
        :skin-test-rows="skinTestRows"
        :skin-test-table-loading="skinTestTableLoading || skinTestInitLoading"
        :clinical-status-options="clinicalStatusOptions"
        :verification-status-options="verificationStatusOptions"
        :criticality-options="criticalityOptions"
        :severity-options="severityOptions"
        :practitioner-options="practitionerOptions"
        :default-practitioner-id="defaultPractitionerId"
        :saving-skin-test-service-request-id="savingSkinTestServiceRequestId"
        :deleting-skin-test-allergy-record-id="deletingSkinTestAllergyRecordId"
        @selection-change="handleExecuteSelectionChange"
        @execute="executeSelectedItems"
        @cancel-execute="handleCancelExecute"
        @refresh-skin-test="loadSkinTestRows(selectedPatientId)"
        @save-skin-test-row="handleSaveSkinTestRow"
        @delete-skin-test-row="handleDeleteSkinTestRow"
      />
    </template>

    <template #right>
      <NurseRightSidebar
        v-model:supply-room-id="selectedSupplyRoomId"
        :show-supply-section="showSupplySection"
        :can-print="Boolean(currentPatient)"
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

    <template v-if="showExecuteFooter" #footer>
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
