<script setup lang="ts">
import type { NurseStationPatientListPanelContext } from './components/patient-list-panel.context';
import type { PatientItem } from './types';
import type { AdmissionBedOperationIntent } from './services/admission-bed-operation';
import type { OperationTab } from './workbench-tab.config';

import { computed, onMounted, provide, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import { submitDirectBedOperation } from './services/execute-bed-operation';
import { isStaleCrossRoleError } from '#/utils/http/stale-cross-role-error';

import PatientAdmissionBedDialog from './components/patient-admission-bed-dialog.vue';
import PatientLargeScreen from './components/patient-large-screen.vue';
import { nurseStationPatientListPanelContextKey } from './components/patient-list-panel.context';
import PatientListPanel from './components/patient-list-panel.vue';
import { useNurseStationBedBoard } from './use-nurse-station-bed-board';
import { useNurseStationBedDrag } from './use-nurse-station-bed-drag';
import { useNurseStationPatientData } from './use-nurse-station-patient-data';
import { useNurseStationPatientProjections } from './use-nurse-station-patient-projections';
import { useNurseStationSelection } from './use-nurse-station-selection';
import { isNurseStationBedUnassigned } from './components/nurse-station-module-patient.types';
import {
  MULTI_PATIENT_OPERATION_TABS,
  NURSE_STATION_BED_REQUIRED_MESSAGE,
  OPERATION_TAB,
  OPERATION_TAB_LABEL,
  SINGLE_PATIENT_OPERATION_TABS,
  doesNurseStationTabRequireBed,
  operationComponentByTab,
  operationLoaderByTab,
  operationSelectionPolicyByTab,
  visibleOperationTabs,
} from './workbench-tab.config';

defineOptions({
  name: 'InpatientNurseStationPatientList',
});
/** 根据页签读取患者选择策略，兜底为单选避免异常态。 */
function getSelectionPolicyByTab(tab: OperationTab) {
  return operationSelectionPolicyByTab[tab] ?? 'single';
}
const activeOperationTab = ref<OperationTab>(OPERATION_TAB.ORDER_EXECUTE);
const {
  bootstrapPatientWorkbench,
  filteredPatients,
  loadPatientQueue,
  patients,
  patientListPanelDataContext,
  pendingAuditCount,
  pendingExecuteCount,
  refreshPatientFilters,
  regionNameByIdRecord,
  selectedRegionIds,
  selectedWardIds,
  totalPatientCount,
  wardNameByIdRecord,
  wardOptions,
} = useNurseStationPatientData();
const IN_OUT_TRANSFER_TAB = OPERATION_TAB.IN_OUT_TRANSFER;
const { bedBoardGroups, bedBoardLoading, bedBoardPanel, loadBedBoard } = useNurseStationBedBoard({
  selectedHouseIds: () => selectedWardIds.value,
  selectedWardIds: () => selectedRegionIds.value,
  wardOptions: () => wardOptions.value,
});
const {
  activePatientId,
  initSelectionState,
  patientListPanelSelectionContext,
  selectedPatientIds,
  setActivePatient,
  setSelectionPolicy,
  syncPatientSelectedFlags,
} = useNurseStationSelection({
  filteredPatients,
  patients,
});
const patientQueueBootstrapped = ref(false);
/** 患者大屏默认隐藏，由左侧队列按钮展开。 */
const largeScreenVisible = ref(false);
const admissionBedDialogVisible = ref(false);
const admissionBedDialogPatient = ref<null | PatientItem>(null);
const admissionBedDialogOperation = ref<AdmissionBedOperationIntent | null>(null);
const operationTabLoading = ref(false);
/** 换床/互换直提交 loading（E4） */
const directBedSubmitting = ref(false);
const loadedOperationTabs = ref<Set<OperationTab>>(new Set([OPERATION_TAB.ORDER_EXECUTE]));
const { selectedPatients, singlePanelPatients } = useNurseStationPatientProjections({
  activePatientId,
  patients,
  regionNameByIdRecord,
  selectedPatientIds,
  wardNameByIdRecord,
});
/** 根据当前导航获取主工作区组件（未映射项显示占位态）。 */
const currentOperationComponent = computed(() => operationComponentByTab[activeOperationTab.value]);

/** 仅保留已分床患者，避免未分床就诊进入业务模块。 */
function filterBedAssignedModulePatients<T extends { bedNo: string }>(list: T[]): T[] {
  // 与左侧卡片一致：无床号展示名视为未分床
  return list.filter((item) => !isNurseStationBedUnassigned(item.bedNo));
}

/**
 * 当前页签是否因选中患者未分床而门禁。
 * 多选：有勾选但无一已分床；单选：主患者存在且未分床。
 */
const operationBlockedByUnassignedBed = computed(() => {
  if (!doesNurseStationTabRequireBed(activeOperationTab.value)) {
    return false;
  }
  if (MULTI_PATIENT_OPERATION_TABS.has(activeOperationTab.value)) {
    if (selectedPatients.value.length === 0) {
      return false;
    }
    return filterBedAssignedModulePatients(selectedPatients.value).length === 0;
  }
  if (SINGLE_PATIENT_OPERATION_TABS.has(activeOperationTab.value)) {
    const patient = singlePanelPatients.value[0];
    if (!patient) {
      return false;
    }
    return isNurseStationBedUnassigned(patient.bedNo);
  }
  return false;
});

/** 根据当前导航拼装组件入参，保证 KeepAlive 缓存切换不丢状态。 */
const currentOperationProps = computed<Record<string, unknown>>(() => {
  if (MULTI_PATIENT_OPERATION_TABS.has(activeOperationTab.value)) {
    return { patients: filterBedAssignedModulePatients(selectedPatients.value) };
  }
  if (SINGLE_PATIENT_OPERATION_TABS.has(activeOperationTab.value)) {
    return { patients: filterBedAssignedModulePatients(singlePanelPatients.value) };
  }
  if (activeOperationTab.value === IN_OUT_TRANSFER_TAB) {
    return {
      activePatientId: activePatientId.value,
      bedBoardGroups: bedBoardGroups.value,
      bedBoardLoading: bedBoardLoading.value || directBedSubmitting.value,
      patients: patients.value,
      regionNameById: regionNameByIdRecord.value,
      onSelectPatient: handleBedMapSelectPatient,
      onPatientDragStart,
      onOpenBedOperation: handleOpenBedOperationDialog,
      onExecuteDirectBed: handleExecuteDirectBed,
      onReloadBedBoard: loadBedBoard,
      onWorkbenchRefresh: handleInOutTransferRefresh,
    };
  }
  return {};
});

/** 目标页签在当前选中下是否会触发未分床门禁。 */
function isTabBlockedByUnassignedBed(tab: OperationTab): boolean {
  if (!doesNurseStationTabRequireBed(tab)) {
    return false;
  }
  if (MULTI_PATIENT_OPERATION_TABS.has(tab)) {
    if (selectedPatients.value.length === 0) {
      return false;
    }
    return filterBedAssignedModulePatients(selectedPatients.value).length === 0;
  }
  if (SINGLE_PATIENT_OPERATION_TABS.has(tab)) {
    const patient = singlePanelPatients.value[0];
    if (!patient) {
      return false;
    }
    return isNurseStationBedUnassigned(patient.bedNo);
  }
  return false;
}

/** 统一处理 tab 切换引发的选择策略变化。 */
function onWorkbenchTabChange(tabId: OperationTab, prevTabId: OperationTab) {
  if (tabId === prevTabId) return;
  const policy = getSelectionPolicyByTab(tabId);
  setSelectionPolicy(policy, (msg) => ElMessage.warning(msg));
}

/** 切换右侧业务导航：首次进入模块时显示 loading，加载完成后再切换。 */
async function handleOperationTabClick(tab: OperationTab) {
  const nextTab = tab;
  const prevTab = activeOperationTab.value;
  if (nextTab === activeOperationTab.value) return;
  // 未分床仅允许进入入出转；其余页签提示后仍切换，由门禁空态承接
  if (isTabBlockedByUnassignedBed(nextTab)) {
    ElMessage.warning(NURSE_STATION_BED_REQUIRED_MESSAGE);
  }
  const loader = operationLoaderByTab[nextTab];
  if (!loader || loadedOperationTabs.value.has(nextTab)) {
    activeOperationTab.value = nextTab;
    onWorkbenchTabChange(nextTab, prevTab);
    if (nextTab === IN_OUT_TRANSFER_TAB) {
      void loadBedBoard(bedBoardPanel.value);
    }
    return;
  }
  operationTabLoading.value = true;
  try {
    await loader();
    if (nextTab === IN_OUT_TRANSFER_TAB) {
      await loadBedBoard('admission');
    }
    loadedOperationTabs.value.add(nextTab);
    activeOperationTab.value = nextTab;
    onWorkbenchTabChange(nextTab, prevTab);
  } finally {
    operationTabLoading.value = false;
  }
}

/** 门禁空态：跳转入出转完成分床 */
function handleGoAssignBed() {
  void handleOperationTabClick(IN_OUT_TRANSFER_TAB);
}
/** 床位图点击占床：单选该患者并与左侧列表联动。 */
function handleBedMapSelectPatient(id: string) {
  setActivePatient(id);
}

/** 打开入科选床弹窗（无目标床时仅维护信息） */
function openAdmissionBedDialog(patient: PatientItem, intent: AdmissionBedOperationIntent | null) {
  admissionBedDialogPatient.value = patient;
  admissionBedDialogOperation.value = intent;
  admissionBedDialogVisible.value = true;
}

/** 双击患者卡片：打开患者体征卡（可单独保存体征）。 */
function handlePatientCardDblClick(patient: PatientItem) {
  openAdmissionBedDialog(patient, null);
}

/** 床位图拖放：仅首次分床打开入科弹窗 */
function handleOpenBedOperationDialog(payload: {
  patient: PatientItem;
  intent: AdmissionBedOperationIntent;
}) {
  if (payload.intent.type !== 'assign') {
    return;
  }
  openAdmissionBedDialog(payload.patient, payload.intent);
}

/** 换床/互换：确认后直接提交写床 */
async function handleExecuteDirectBed(payload: {
  patient: PatientItem;
  intent: AdmissionBedOperationIntent;
}) {
  const { patient, intent } = payload;
  if (directBedSubmitting.value) {
    return;
  }
  if (intent.type === 'swap' && intent.swapPartnerName) {
    try {
      await ElMessageBox.confirm(
        `是否与「${intent.swapPartnerName}」（床位 ${intent.targetBedName}）互换？`,
        '换床确认',
        { type: 'warning', confirmButtonText: '互换床位', cancelButtonText: '取消' },
      );
    } catch {
      return;
    }
  }
  directBedSubmitting.value = true;
  try {
    const message = await submitDirectBedOperation(patient, intent);
    if (!message) {
      ElMessage.warning('换床提交失败，请稍后重试');
      return;
    }
    ElMessage.success(message);
    await handleAdmissionBedDialogSaved();
  } catch (error) {
    if (isStaleCrossRoleError(error)) {
      await handleAdmissionBedDialogSaved();
    }
  } finally {
    directBedSubmitting.value = false;
  }
}

/** 弹窗保存后刷新患者队列与床位图（选中态由 selection watch 恢复） */
async function handleAdmissionBedDialogSaved() {
  await loadPatientQueue();
  if (activeOperationTab.value === IN_OUT_TRANSFER_TAB) {
    await loadBedBoard();
  }
}

/** 出院 / 清床后刷新患者队列与当前 panel 床位图（选中态由 selection watch 恢复） */
async function handleInOutTransferRefresh() {
  await refreshWorkbenchAfterEncounterFlowChange();
}

/** 出院医嘱校对等写操作后：刷新队列，并保证出 Tab 床位图与就诊态一致 */
async function refreshWorkbenchAfterEncounterFlowChange() {
  await loadPatientQueue();
  if (activeOperationTab.value === IN_OUT_TRANSFER_TAB) {
    await loadBedBoard(bedBoardPanel.value);
    return;
  }
  if (loadedOperationTabs.value.has(IN_OUT_TRANSFER_TAB)) {
    await loadBedBoard('discharge');
  }
}

const { onPatientDragStart, patientListPanelDragContext } = useNurseStationBedDrag();

watch(admissionBedDialogVisible, (open) => {
  if (!open) {
    admissionBedDialogOperation.value = null;
  }
});

/** 展开患者一览大屏（铺满护士站工作区）。 */
function openPatientLargeScreen() {
  largeScreenVisible.value = true;
}

/** 大屏选中患者：收敛为当前患者并收起大屏回到业务区。 */
function handleLargeScreenSelect(payload: { patientId: string }) {
  const patient = patients.value.find((row) => row.id === payload.patientId);
  selectedPatientIds.value = new Set([payload.patientId]);
  setActivePatient(payload.patientId);
  largeScreenVisible.value = false;
  // 未分床且当前页签需床位时，引导至入出转分床
  if (
    patient &&
    isNurseStationBedUnassigned(patient.bedNo) &&
    doesNurseStationTabRequireBed(activeOperationTab.value)
  ) {
    ElMessage.warning(NURSE_STATION_BED_REQUIRED_MESSAGE);
    void handleOperationTabClick(IN_OUT_TRANSFER_TAB);
  }
}

/** 大屏快捷入口：收敛为当前患者、切换业务页签并收起大屏。 */
function handleLargeScreenShortcut(payload: { patientId: string; tab: OperationTab }) {
  const patient = patients.value.find((row) => row.id === payload.patientId);
  // 未分床仅允许跳转入出转分床
  if (
    patient &&
    isNurseStationBedUnassigned(patient.bedNo) &&
    doesNurseStationTabRequireBed(payload.tab)
  ) {
    ElMessage.warning(NURSE_STATION_BED_REQUIRED_MESSAGE);
    selectedPatientIds.value = new Set([payload.patientId]);
    setActivePatient(payload.patientId);
    largeScreenVisible.value = false;
    void handleOperationTabClick(IN_OUT_TRANSFER_TAB);
    return;
  }
  // 先收敛为单患者，避免多选态切到单选页签时被策略收束清空
  selectedPatientIds.value = new Set([payload.patientId]);
  setActivePatient(payload.patientId);
  largeScreenVisible.value = false;
  void handleOperationTabClick(payload.tab);
}

/** 大屏内刷新：复用队列筛选刷新。 */
function handleLargeScreenRefresh() {
  void refreshPatientFilters();
}

/** 向患者列表子组件提供工作站上下文。 */
const patientListPanelContext: NurseStationPatientListPanelContext = {
  ...patientListPanelDataContext,
  ...patientListPanelSelectionContext,
  ...patientListPanelDragContext,
  refreshWorkbenchAfterEncounterFlowChange,
  onPatientCardDblClick: handlePatientCardDblClick,
  openPatientLargeScreen,
};
provide(nurseStationPatientListPanelContextKey, patientListPanelContext);
setSelectionPolicy(getSelectionPolicyByTab(activeOperationTab.value), () => {});

/** 左侧病区/病房筛选变化时，同步刷新入出转床位图 */
watch([selectedRegionIds, selectedWardIds], () => {
  if (
    activeOperationTab.value === IN_OUT_TRANSFER_TAB &&
    loadedOperationTabs.value.has(IN_OUT_TRANSFER_TAB)
  ) {
    void loadBedBoard(bedBoardPanel.value);
  }
});

/** 工作台挂载后拉取病区树与患者队列，并初始化默认选中。 */
onMounted(async () => {
  await bootstrapPatientWorkbench();
  if (!patientQueueBootstrapped.value) {
    initSelectionState();
    patientQueueBootstrapped.value = true;
    return;
  }
  syncPatientSelectedFlags();
});
</script>

<template>
  <div class="nurse-workbench">
    <PatientListPanel />

    <PatientAdmissionBedDialog
      v-model="admissionBedDialogVisible"
      :patient="admissionBedDialogPatient"
      :bed-operation="admissionBedDialogOperation"
      :region-name-by-id="regionNameByIdRecord"
      :ward-name-by-id="wardNameByIdRecord"
      @saved="handleAdmissionBedDialogSaved"
    />

    <PatientLargeScreen
      v-model="largeScreenVisible"
      :patients="filteredPatients"
      :total-patient-count="totalPatientCount"
      :pending-audit-count="pendingAuditCount"
      :pending-execute-count="pendingExecuteCount"
      :region-name-by-id="regionNameByIdRecord"
      :ward-name-by-id="wardNameByIdRecord"
      @select="handleLargeScreenSelect"
      @shortcut="handleLargeScreenShortcut"
      @refresh="handleLargeScreenRefresh"
    />

    <div class="nurse-workbench__operation">
      <div class="nurse-operation-tabs">
        <button
          v-for="tab in visibleOperationTabs"
          :key="tab"
          type="button"
          class="nurse-operation-tabs__item"
          :class="{ 'is-active': activeOperationTab === tab }"
          @click="handleOperationTabClick(tab)"
        >
          {{ OPERATION_TAB_LABEL[tab] }}
        </button>
      </div>
      <div class="nurse-operation-content">
        <div
          v-loading="operationTabLoading"
          element-loading-text="模块加载中..."
          class="nurse-operation-content__inner"
        >
          <div v-if="operationBlockedByUnassignedBed" class="nurse-operation-bed-gate">
            <ElEmpty :description="NURSE_STATION_BED_REQUIRED_MESSAGE">
              <ElButton type="primary" @click="handleGoAssignBed">去入出转分床</ElButton>
            </ElEmpty>
          </div>
          <template v-else>
            <KeepAlive :max="7">
              <component
                :is="currentOperationComponent"
                v-if="currentOperationComponent"
                :key="activeOperationTab"
                v-bind="currentOperationProps"
              />
            </KeepAlive>
            <ElEmpty v-if="!currentOperationComponent" description="该模块正在建设中" />
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nurse-workbench {
  display: flex;
  gap: 0;
  height: 100%;
  margin: 0;
  min-height: 0;
  overflow: hidden;
  padding: 0;
  position: relative;
  width: 100%;
}
.nurse-workbench__operation {
  background: #fff;
  height: 100%;
  min-height: 0;
  width: 78%;
}
.nurse-operation-tabs {
  align-items: stretch;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color);
  display: flex;
  flex-wrap: nowrap;
  min-height: 46px;
  overflow-x: auto;
  padding: 0 8px;
}
.nurse-operation-tabs__item {
  align-items: center;
  background: transparent;
  border: 0;
  border-bottom: 0;
  color: #4f5969;
  cursor: pointer;
  display: inline-flex;
  flex: 1 0 auto;
  font-size: 15px;
  font-weight: 500;
  height: 46px;
  justify-content: center;
  line-height: 1;
  margin: 0;
  min-width: 104px;
  padding: 0 10px;
  position: relative;
  white-space: nowrap;
}

.nurse-operation-tabs__item:hover {
  color: var(--el-color-primary);
}

.nurse-operation-tabs__item.is-active {
  color: var(--el-color-primary);
}

.nurse-operation-tabs__item.is-active::after {
  background: var(--el-color-primary);
  border-radius: 2px;
  bottom: 0;
  content: '';
  height: 2px;
  left: 50%;
  position: absolute;
  transform: translateX(-50%);
  width: 80px;
}

.nurse-operation-content {
  background: #f3f5f8;
  height: calc(100% - 46px);
  padding: 0;
}
.nurse-operation-content__inner {
  background: #f3f5f8;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.nurse-operation-content__inner > * {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.nurse-operation-bed-gate {
  align-items: center;
  display: flex;
  flex: 1;
  justify-content: center;
  min-height: 0;
}
</style>
