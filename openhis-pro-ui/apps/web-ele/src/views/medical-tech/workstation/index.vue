<script setup lang="ts">
import type {
  MedicalTechEncounterScope,
  MedicalTechPatientQueueItemDto,
} from '#/api/medical-tech/medical-tech-execution.types';
import type { PatientInfoBarPayload } from '#/components/outpatient/patient-card.types';
import type { MedicalTechTab } from './workbench-tab.config';

import { computed, onMounted, provide, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';

import { ElTabPane, ElTabs } from 'element-plus';

import StationTriPaneLayout from '#/components/layouts/station-tri-pane-layout.vue';
import PatientInfoBar from '#/components/outpatient/patient-info-bar.vue';

import MedicalTechChargingPanel from './charging/charging-panel.vue';
import MedicalTechFooterBar from './components/medical-tech-footer-bar.vue';
import MedicalTechQueuePanel from './components/medical-tech-queue-panel.vue';
import MedicalTechExecutionPanel from './execution/execution-panel.vue';
import MedicalTechFeeListPanel from './fee-list/fee-list-panel.vue';
import { toMedicalTechPatientInfoBarPayload } from './medical-tech-patient-map';
import { useMedicalTechExecutionSheet } from './use-medical-tech-execution-sheet';
import { useMedicalTechQueue } from './use-medical-tech-queue';
import {
  MEDICAL_TECH_TAB,
  MEDICAL_TECH_TAB_LABEL,
  medicalTechTabs,
  medicalTechWorkstationContextKey,
} from './workbench-tab.config';

import './medical-tech-workstation.css';

defineOptions({ name: 'MedicalTechWorkstation' });

/** 唯一上下文：就诊类别（默认门诊） */
const encounterScope = ref<MedicalTechEncounterScope>('OUTPATIENT');
/** 唯一上下文：当前选中就诊 */
const selectedEncounterId = ref('');
/** 当前选中队列行摘要（执行/费用清单/计费信息条共用） */
const selectedPatientSummary = ref<MedicalTechPatientQueueItemDto | null>(null);
const activeTab = ref<MedicalTechTab>(MEDICAL_TECH_TAB.EXECUTION);

const {
  queueKeyword,
  queueDateRange,
  pendingDeviceOnly,
  inpatientStayFilter,
  queueRows,
  queueLoading,
  loadPatientQueue,
  handleSelectPatient,
  handleQueueKeywordSearch,
  handleQueueDateRangeChange,
  handlePendingDeviceOnlyChange,
  handleInpatientStayFilterChange,
  handleQueueRefresh,
} = useMedicalTechQueue({
  encounterScope,
  selectedEncounterId,
  selectedPatientSummary,
});

provide(medicalTechWorkstationContextKey, {
  encounterScope,
  selectedEncounterId,
  selectedPatientSummary,
  reloadPatientQueue: loadPatientQueue,
});

const {
  serviceLines,
  selectedDeviceKeys,
  supplyItems,
  sheetLoading,
  selectedServiceCount,
  selectedDeviceCount,
  totalSelectedCount,
  canExecuteSelected,
  canCancelSelected,
  performLoading,
  cancelLoading,
  registerClearTableSelection,
  handleServiceSelectionChange,
  handleSupplyChange,
  handleClearSelection,
  handlePerform,
  handleCancel,
  handleCancelSupply,
} = useMedicalTechExecutionSheet({
  encounterScope,
  selectedEncounterId,
  selectedPatientSummary,
  reloadPatientQueue: loadPatientQueue,
});

/** 页签上方信息条：门诊挂号时间，住院入院时间 */
const patientInfoBar = computed<PatientInfoBarPayload>(() =>
  toMedicalTechPatientInfoBarPayload(selectedPatientSummary.value),
);

/** 住院信息条用「入院时间」 */
const startTimeLabel = computed(() =>
  encounterScope.value === 'INPATIENT' ? '入院时间' : '挂号时间',
);

/** 可见页签：门诊隐藏医技计费 */
const visibleTabs = computed(() => {
  if (encounterScope.value === 'OUTPATIENT') {
    return medicalTechTabs.filter((tab) => tab !== MEDICAL_TECH_TAB.CHARGING);
  }
  return medicalTechTabs;
});

/** 医技执行且已选患者：展示底栏 */
const showExecuteFooter = computed(
  () => activeTab.value === MEDICAL_TECH_TAB.EXECUTION && Boolean(selectedPatientSummary.value),
);

/** 切换就诊类别：门诊时若停在计费 Tab 则回执行；住院清空待发耗材筛 */
function handleEncounterScopeChange(scope: MedicalTechEncounterScope) {
  encounterScope.value = scope;
  if (scope === 'INPATIENT') {
    pendingDeviceOnly.value = false;
  }
  if (scope === 'OUTPATIENT' && activeTab.value === MEDICAL_TECH_TAB.CHARGING) {
    activeTab.value = MEDICAL_TECH_TAB.EXECUTION;
  }
}

/** 可见页签变化时兜底纠正 activeTab */
watch(visibleTabs, (tabs) => {
  if (!tabs.includes(activeTab.value)) {
    activeTab.value = MEDICAL_TECH_TAB.EXECUTION;
  }
});

onMounted(() => {
  void loadPatientQueue();
});
</script>

<template>
  <Page auto-content-height content-class="flex min-h-0 flex-col p-0">
    <StationTriPaneLayout
      class="mt-workstation h-full min-h-0"
      left-track="288px"
      left-track-lg="288px"
      :show-right="false"
      :show-footer="showExecuteFooter"
    >
      <template #left>
        <MedicalTechQueuePanel
          v-model:keyword="queueKeyword"
          v-model:queue-date-range="queueDateRange"
          v-model:pending-device-only="pendingDeviceOnly"
          :encounter-scope="encounterScope"
          :inpatient-stay-filter="inpatientStayFilter"
          :patients="queueRows"
          :queue-loading="queueLoading"
          :selected-patient-id="selectedEncounterId"
          @encounter-scope-change="handleEncounterScopeChange"
          @inpatient-stay-filter-change="handleInpatientStayFilterChange"
          @pending-device-only-change="handlePendingDeviceOnlyChange"
          @queue-date-range-change="handleQueueDateRangeChange"
          @refresh="handleQueueRefresh"
          @search="handleQueueKeywordSearch"
          @select-patient="handleSelectPatient"
        />
      </template>

      <template #center>
        <div class="flex h-full min-h-0 min-w-0 flex-col">
          <div
            class="bg-background shrink-0 border-b border-[var(--el-border-color-lighter)] px-3 pb-3 pt-2"
          >
            <PatientInfoBar
              v-if="selectedPatientSummary"
              :patient="patientInfoBar"
              :start-time-label="startTimeLabel"
            />
            <div v-else class="text-text-sub py-1 text-sm">请选择患者</div>
          </div>
          <div class="bg-background shrink-0 px-2 pt-1.5">
            <ElTabs v-model="activeTab" class="mt-workstation-tabs">
              <ElTabPane
                v-for="tab in visibleTabs"
                :key="tab"
                :label="MEDICAL_TECH_TAB_LABEL[tab]"
                :name="tab"
              />
            </ElTabs>
          </div>
          <div class="relative min-h-0 min-w-0 flex-1 overflow-hidden">
            <MedicalTechExecutionPanel
              v-show="activeTab === MEDICAL_TECH_TAB.EXECUTION"
              class="absolute inset-0 flex min-h-0 min-w-0 flex-col"
              :service-lines="serviceLines"
              :sheet-loading="sheetLoading"
              :supply-items="supplyItems"
              :selected-supply-ids="selectedDeviceKeys"
              :register-clear-table-selection="registerClearTableSelection"
              @selection-change="handleServiceSelectionChange"
              @supply-change="handleSupplyChange"
              @cancel-supply="handleCancelSupply"
            />
            <MedicalTechFeeListPanel
              v-show="activeTab === MEDICAL_TECH_TAB.FEE_LIST"
              class="absolute inset-0 flex min-h-0 min-w-0 flex-col"
            />
            <!-- 计费仅住院且仅当前页签挂载，避免执行页选门诊人误打计费接口弹错 -->
            <MedicalTechChargingPanel
              v-if="activeTab === MEDICAL_TECH_TAB.CHARGING"
              class="absolute inset-0 flex min-h-0 min-w-0 flex-col"
            />
          </div>
        </div>
      </template>

      <template v-if="showExecuteFooter" #footer>
        <MedicalTechFooterBar
          :selected-service-count="selectedServiceCount"
          :selected-device-count="selectedDeviceCount"
          :total-selected-count="totalSelectedCount"
          :can-execute-selected="canExecuteSelected"
          :can-cancel-selected="canCancelSelected"
          :perform-loading="performLoading"
          :cancel-loading="cancelLoading"
          :hide-device-count="encounterScope === 'INPATIENT'"
          @clear="handleClearSelection"
          @execute="handlePerform"
          @cancel="handleCancel"
        />
      </template>
    </StationTriPaneLayout>
  </Page>
</template>

<style scoped>
.mt-workstation-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.mt-workstation-tabs :deep(.el-tabs__content) {
  display: none;
}
</style>
