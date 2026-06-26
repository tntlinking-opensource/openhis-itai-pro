<script setup lang="ts">
import type { NurseStationPatientListPanelContext } from './components/patient-list-panel.context';
import type { PatientItem } from './types';
import type { AdmissionBedOperationIntent } from './services/admission-bed-operation';
import type { OperationTab } from './workbench-tab.config';

import { computed, onMounted, provide, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import { submitDirectBedOperation } from './services/execute-bed-operation';
import { submitRecallInpatientDischarge } from './services/recall-inpatient-discharge';

import PatientAdmissionBedDialog from './components/patient-admission-bed-dialog.vue';
import { nurseStationPatientListPanelContextKey } from './components/patient-list-panel.context';
import PatientListPanel from './components/patient-list-panel.vue';
import { useNurseStationBedBoard } from './use-nurse-station-bed-board';
import { useNurseStationBedDrag } from './use-nurse-station-bed-drag';
import { useNurseStationPatientData } from './use-nurse-station-patient-data';
import { useNurseStationPatientProjections } from './use-nurse-station-patient-projections';
import { useNurseStationSelection } from './use-nurse-station-selection';
import {
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
const activeOperationTab = ref<OperationTab>('医嘱执行');
const {
  bootstrapPatientWorkbench,
  filteredPatients,
  loadPatientQueue,
  patients,
  patientListPanelDataContext,
  queueScopeFilter,
  regionNameByIdRecord,
  selectedRegionIds,
  selectedWardIds,
  wardNameByIdRecord,
  wardOptions,
} = useNurseStationPatientData();
const IN_OUT_TRANSFER_TAB: OperationTab = '入出转管理';
/** 已出院结算列表：仅召回可用，禁止选中患者与右侧业务操作 */
const isRecallListMode = computed(() => queueScopeFilter.value === 'settled-discharged');
const { bedBoardGroups, bedBoardLoading, bedBoardPanel, loadBedBoard } = useNurseStationBedBoard({
  selectedHouseIds: () => selectedWardIds.value,
  selectedWardIds: () => selectedRegionIds.value,
  wardOptions: () => wardOptions.value,
});
const {
  activePatientId,
  clearSelection,
  initSelectionState,
  patientListPanelSelectionContext,
  selectedPatientIds,
  setActivePatient,
  setSelectionPolicy,
  syncPatientSelectedFlags,
} = useNurseStationSelection({
  filteredPatients,
  patients,
  selectionDisabled: isRecallListMode,
});
const patientQueueBootstrapped = ref(false);
const admissionBedDialogVisible = ref(false);
const admissionBedDialogPatient = ref<null | PatientItem>(null);
const admissionBedDialogOperation = ref<AdmissionBedOperationIntent | null>(null);
const operationTabLoading = ref(false);
const loadedOperationTabs = ref<Set<OperationTab>>(new Set(['医嘱执行']));
const { selectedPatients, singlePanelPatients } = useNurseStationPatientProjections({
  activePatientId,
  patients,
  regionNameByIdRecord,
  selectedPatientIds,
  wardNameByIdRecord,
});
/** 根据当前导航获取主工作区组件（未映射项显示占位态）。 */
const currentOperationComponent = computed(() => operationComponentByTab[activeOperationTab.value]);
/** 根据当前导航拼装组件入参，保证 KeepAlive 缓存切换不丢状态。 */
const currentOperationProps = computed<Record<string, unknown>>(() => {
  if (
    activeOperationTab.value === '医嘱执行' ||
    activeOperationTab.value === '医嘱校对' ||
    activeOperationTab.value === '汇总领药'
  ) {
    return { patients: selectedPatients.value };
  }
  if (
    activeOperationTab.value === '医嘱查看' ||
    activeOperationTab.value === '皮试管理' ||
    activeOperationTab.value === '护理记录' ||
    activeOperationTab.value === '体温单' ||
    activeOperationTab.value === '住院记账' ||
    activeOperationTab.value === '费用明细' ||
    activeOperationTab.value === '病历调阅'
  ) {
    return { patients: singlePanelPatients.value };
  }
  if (activeOperationTab.value === IN_OUT_TRANSFER_TAB) {
    return {
      activePatientId: activePatientId.value,
      bedBoardGroups: bedBoardGroups.value,
      bedBoardLoading: bedBoardLoading.value,
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
/** 统一处理 tab 切换引发的选择策略变化。 */
function onWorkbenchTabChange(tabId: OperationTab, prevTabId: OperationTab) {
  if (tabId === prevTabId) return;
  const policy = getSelectionPolicyByTab(tabId);
  setSelectionPolicy(policy, (msg) => ElMessage.warning(msg));
}
/** 切换右侧业务导航：首次进入模块时显示 loading，加载完成后再切换。 */
async function handleOperationTabClick(tab: string) {
  if (isRecallListMode.value) {
    return;
  }
  const nextTab = tab as OperationTab;
  const prevTab = activeOperationTab.value;
  if (tab === activeOperationTab.value) return;
  const loader = operationLoaderByTab[nextTab];
  if (!loader || loadedOperationTabs.value.has(nextTab)) {
    activeOperationTab.value = nextTab;
    onWorkbenchTabChange(nextTab, prevTab);
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

/** 双击患者卡片：在院列表打开入科选床弹窗。 */
function handlePatientCardDblClick(patient: PatientItem) {
  if (queueScopeFilter.value === 'settled-discharged') {
    return;
  }
  openAdmissionBedDialog(patient, null);
}

/** 切换至入出转管理并加载入科床位图（召回成功后引导二次分床）。 */
async function switchToInOutTransferTab() {
  const prevTab = activeOperationTab.value;
  if (activeOperationTab.value === IN_OUT_TRANSFER_TAB) {
    await loadBedBoard('admission');
    return;
  }
  const loader = operationLoaderByTab[IN_OUT_TRANSFER_TAB];
  if (loader && !loadedOperationTabs.value.has(IN_OUT_TRANSFER_TAB)) {
    operationTabLoading.value = true;
    try {
      await loader();
      loadedOperationTabs.value.add(IN_OUT_TRANSFER_TAB);
    } finally {
      operationTabLoading.value = false;
    }
  }
  await loadBedBoard('admission');
  activeOperationTab.value = IN_OUT_TRANSFER_TAB;
  onWorkbenchTabChange(IN_OUT_TRANSFER_TAB, prevTab);
}

/** 召回成功后切至入出转管理、选中患者并提示重新分床。 */
async function navigateAfterRecallSuccess(patient: PatientItem) {
  queueScopeFilter.value = 'in-hospital';
  await loadPatientQueue();
  await switchToInOutTransferTab();
  setActivePatient(patient.id);
  try {
    await ElMessageBox.alert(
      `患者「${patient.name}」已召回至待入科，请在右侧入科床位图中为该患者重新分床。`,
      '请重新分床',
      { type: 'info', confirmButtonText: '知道了' },
    );
  } catch {
    // 用户关闭提示框，不影响后续操作
  }
}

/** 出院召回：确认后提交 ATD-07 并刷新队列（仅已清床离院患者）。 */
async function handleRecallPatient(patient: PatientItem) {
  try {
    await ElMessageBox.confirm(
      `确认将患者「${patient.name}」出院召回？将撤回出院医嘱并恢复待入科，须至入出转管理二次分床。`,
      '出院召回',
      { type: 'warning', confirmButtonText: '确认召回', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  try {
    await submitRecallInpatientDischarge(patient.id);
    await navigateAfterRecallSuccess(patient);
  } catch {
    // 业务错误由 request 拦截器展示后端 message，此处不再重复弹窗
  }
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
  const message = await submitDirectBedOperation(patient, intent);
  if (!message) {
    ElMessage.warning('换床提交失败，请稍后重试');
    return;
  }
  ElMessage.success(message);
  await handleAdmissionBedDialogSaved();
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
  await loadPatientQueue();
  if (activeOperationTab.value === IN_OUT_TRANSFER_TAB) {
    await loadBedBoard(bedBoardPanel.value);
  }
}

const { onPatientDragStart, patientListPanelDragContext } = useNurseStationBedDrag();

watch(admissionBedDialogVisible, (open) => {
  if (!open) {
    admissionBedDialogOperation.value = null;
  }
});

/** 切换至已出院结算时清空选中，避免右侧模块沿用旧患者自动拉数 */
watch(isRecallListMode, (recallMode) => {
  if (recallMode) {
    clearSelection();
  }
});
/** 向患者列表子组件提供工作站上下文。 */
const patientListPanelContext: NurseStationPatientListPanelContext = {
  ...patientListPanelDataContext,
  ...patientListPanelSelectionContext,
  ...patientListPanelDragContext,
  onPatientCardDblClick: handlePatientCardDblClick,
  onRecallPatient: handleRecallPatient,
};
provide(nurseStationPatientListPanelContextKey, patientListPanelContext);
setSelectionPolicy(getSelectionPolicyByTab(activeOperationTab.value), () => {});

/** 左侧病区/病房筛选变化时，同步刷新入出转床位图 */
watch([selectedRegionIds, selectedWardIds], () => {
  if (isRecallListMode.value) {
    return;
  }
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

    <div class="nurse-workbench__operation">
      <div class="nurse-operation-tabs">
        <button
          v-for="tab in visibleOperationTabs"
          :key="tab"
          type="button"
          class="nurse-operation-tabs__item"
          :class="{ 'is-active': activeOperationTab === tab, 'is-disabled': isRecallListMode }"
          :disabled="isRecallListMode"
          @click="handleOperationTabClick(tab)"
        >
          {{ tab }}
        </button>
      </div>
      <div class="nurse-operation-content">
        <div
          v-if="isRecallListMode"
          class="nurse-operation-content__recall-placeholder flex min-h-80 items-center justify-center"
        >
          <ElEmpty description="已出院结算模式下仅支持出院召回，请从左侧患者列表点击「召回」" />
        </div>
        <div
          v-else
          v-loading="operationTabLoading"
          element-loading-text="模块加载中..."
          class="nurse-operation-content__inner"
        >
          <KeepAlive :max="7">
            <component
              :is="currentOperationComponent"
              v-if="currentOperationComponent"
              :key="activeOperationTab"
              v-bind="currentOperationProps"
            />
          </KeepAlive>
          <ElEmpty v-if="!currentOperationComponent" description="该模块正在建设中" />
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
  padding: 0;
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

.nurse-operation-tabs__item.is-disabled,
.nurse-operation-tabs__item:disabled {
  color: var(--el-text-color-placeholder);
  cursor: not-allowed;
}

.nurse-operation-tabs__item.is-disabled:hover,
.nurse-operation-tabs__item:disabled:hover {
  color: var(--el-text-color-placeholder);
}

.nurse-operation-content {
  background: #f3f5f8;
  height: calc(100% - 46px);
  padding: 0;
}
.nurse-operation-content__inner,
.nurse-operation-content__recall-placeholder {
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
</style>
