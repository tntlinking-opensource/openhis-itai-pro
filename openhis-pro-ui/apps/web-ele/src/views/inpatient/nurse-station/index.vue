<script setup lang="ts">
import type { NurseStationPatientListPanelContext } from './components/patient-list-panel.context';
import type { PatientItem } from './types';
import type { AdmissionBedOperationIntent } from './services/admission-bed-operation';
import type { OperationTab } from './workbench-tab.config';

import { computed, onMounted, provide, ref, watch } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import { submitDirectBedOperation } from './services/execute-bed-operation';

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
  operationTabs,
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
  regionNameByIdRecord,
  selectedRegionIds,
  selectedWardIds,
  wardNameByIdRecord,
  wardOptions,
} = useNurseStationPatientData();
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
    activeOperationTab.value === '护理记录' ||
    activeOperationTab.value === '住院日结' ||
    activeOperationTab.value === '体温单' ||
    activeOperationTab.value === '住院记账' ||
    activeOperationTab.value === '费用明细' ||
    activeOperationTab.value === '病历调阅'
  ) {
    return { patients: singlePanelPatients.value };
  }
  if (activeOperationTab.value === '入出转管理') {
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
    if (nextTab === '入出转管理') {
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

/** 双击患者卡片：打开入科选床弹窗。 */
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
  if (activeOperationTab.value === '入出转管理') {
    await loadBedBoard();
  }
}

/** 出院 / 清床后刷新患者队列与当前 panel 床位图（选中态由 selection watch 恢复） */
async function handleInOutTransferRefresh() {
  await loadPatientQueue();
  if (activeOperationTab.value === '入出转管理') {
    await loadBedBoard(bedBoardPanel.value);
  }
}

const { onPatientDragStart, patientListPanelDragContext } = useNurseStationBedDrag();

watch(admissionBedDialogVisible, (open) => {
  if (!open) {
    admissionBedDialogOperation.value = null;
  }
});
/** 向患者列表子组件提供工作站上下文。 */
const patientListPanelContext: NurseStationPatientListPanelContext = {
  ...patientListPanelDataContext,
  ...patientListPanelSelectionContext,
  ...patientListPanelDragContext,
  onPatientCardDblClick: handlePatientCardDblClick,
};
provide(nurseStationPatientListPanelContextKey, patientListPanelContext);
setSelectionPolicy(getSelectionPolicyByTab(activeOperationTab.value), () => {});

/** 左侧病区/病房筛选变化时，同步刷新入出转床位图 */
watch([selectedRegionIds, selectedWardIds], () => {
  if (activeOperationTab.value === '入出转管理' && loadedOperationTabs.value.has('入出转管理')) {
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
          v-for="tab in operationTabs"
          :key="tab"
          type="button"
          class="nurse-operation-tabs__item"
          :class="{ 'is-active': activeOperationTab === tab }"
          @click="handleOperationTabClick(tab)"
        >
          {{ tab }}
        </button>
      </div>
      <div class="nurse-operation-content">
        <div
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
.nurse-operation-content {
  background: #f3f5f8;
  height: calc(100% - 46px);
  padding: 0;
}
.nurse-operation-content__inner {
  background: #f3f5f8;
  height: 100%;
}
</style>
