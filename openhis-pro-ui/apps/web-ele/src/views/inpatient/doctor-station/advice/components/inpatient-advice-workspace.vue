<script setup lang="ts">
import type { InpatientAdviceQuickAction } from './inpatient-advice-quick-action-bar.vue';
import type { UseInpatientAdviceTabProps } from '../composables/use-inpatient-advice-tab';
import type { AdviceRowItem } from '../services/advice.types';

import { ElButton, ElTable, ElTableColumn, ElTag } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import TcmAdvice from '#/components/doctor-station/tcm-advice/tcm-advice.vue';

import { useInpatientAdviceTab } from '../composables/use-inpatient-advice-tab';
import { adviceTherapyTypeClass, resolveAdviceTherapyTypeText } from '../services/advice-list';
import AdviceEditCard from './advice-edit-card.vue';
import InpatientAdviceQuickActionBar from './inpatient-advice-quick-action-bar.vue';
import WesternAdviceCollapsed from './western-advice-collapsed.vue';
import WesternAdviceDetailForm from './western-advice-detail-form.vue';

defineOptions({ name: 'InpatientDoctorStationAdviceWorkspace' });

const props = withDefaults(defineProps<UseInpatientAdviceTabProps>(), {
  encounterId: '',
  patientId: '',
});

const emit = defineEmits<{
  quickAction: [action: InpatientAdviceQuickAction];
}>();

const {
  adviceMutationLoading,
  adviceTableRef,
  adviceWorkspaceTableLoading,
  addDrugGuardWestern,
  calcDrugLineTotalFee,
  feeNatureOptions,
  editorForm,
  editorInitialGroup,
  editorOpening,
  editorPatientDiagnoses,
  encounterId,
  getAdviceStatusTagType,
  getCellClassName,
  getTableRows,
  handleAdd,
  handleCloseEditor,
  handleDelete,
  handleEditorDirtyChange,
  handleMedicationAssignGroup,
  handleMedicationUngroup,
  handleOpenTcmDialog,
  handleRowDblclick,
  handleSaveGroup,
  handleSign,
  handleSpanMethod,
  handleToolbarPlaceholder,
  handleWithdraw,
  handleStopLongTerm,
  loadAdviceInit,
  medicationRateOptions,
  medicationRouteOptions,
  onAdviceSelectionChange,
  orderSelectExpandNonce,
  orderSelectFocusNonce,
  patientId,
  productColumns,
  queryProductOptions,
  reload,
  tcmDialogVisible,
  validateWesternRow,
} = useInpatientAdviceTab(props);

defineExpose({
  loadAdviceInit,
  reload,
});

/** 底部快捷申请条上抛主壳 */
function handleQuickAction(action: InpatientAdviceQuickAction) {
  emit('quickAction', action);
}
</script>

<template>
  <div class="ip-advice-workspace flex h-full min-h-0 flex-col p-2">
    <div class="mb-2 flex w-full flex-shrink-0 flex-wrap items-center gap-1">
      <ElButton type="primary" @click="handleAdd()">新增</ElButton>
      <ElButton type="primary" @click="handleOpenTcmDialog()">中药处方</ElButton>
      <ElButton type="primary" :loading="adviceMutationLoading" @click="handleSign()">
        签发
      </ElButton>
      <ElButton type="warning" plain :loading="adviceMutationLoading" @click="handleWithdraw()">
        撤回
      </ElButton>
      <ElButton type="warning" :loading="adviceMutationLoading" @click="handleStopLongTerm()">
        停嘱
      </ElButton>
      <ElButton type="danger" plain :loading="adviceMutationLoading" @click="handleDelete()">
        删除
      </ElButton>
      <ElButton plain @click="handleMedicationUngroup">拆组</ElButton>
      <ElButton plain @click="handleMedicationAssignGroup">并组</ElButton>
      <ElButton plain @click="handleToolbarPlaceholder('组套')">组套</ElButton>
      <ElButton plain @click="handleToolbarPlaceholder('模板')">模板</ElButton>
    </div>

    <div
      class="border-border-base bg-background-card min-h-0 flex-1 overflow-hidden rounded border"
    >
      <ElTable
        ref="adviceTableRef"
        v-loading="adviceWorkspaceTableLoading"
        :data="getTableRows()"
        :span-method="handleSpanMethod"
        :cell-class-name="getCellClassName"
        border
        stripe
        class="advice-table"
        max-height="calc(100vh - 230px)"
        @row-dblclick="handleRowDblclick"
        @selection-change="onAdviceSelectionChange"
      >
        <ElTableColumn
          type="selection"
          width="42"
          align="center"
          :selectable="(row: AdviceRowItem) => !row.isEditor"
        />
        <ElTableColumn
          prop="editorSlot"
          label=""
          width="1"
          class-name="advice-editor-hidden-col"
          label-class-name="advice-editor-hidden-col"
        >
          <template #default="{ row }">
            <AdviceEditCard
              v-if="row.isEditor"
              v-loading="editorOpening"
              v-model="editorForm"
              v-model:patient-diagnoses="editorPatientDiagnoses"
              :fee-nature-options="feeNatureOptions"
              :encounter-id="encounterId"
              :medication-rate-options="medicationRateOptions"
              :medication-route-options="medicationRouteOptions"
              :order-select-expand-nonce="orderSelectExpandNonce"
              :order-select-focus-nonce="orderSelectFocusNonce"
              :patient-id="patientId"
              :initial-group="editorInitialGroup"
              :product-columns="productColumns"
              :query-product-options="queryProductOptions"
              :validate-row="validateWesternRow"
              :save-loading="adviceMutationLoading"
              :add-drug-guard="addDrugGuardWestern"
              @close="handleCloseEditor"
              @editor-dirty-change="handleEditorDirtyChange"
              @save-group="handleSaveGroup"
            >
              <template #detail="scope">
                <WesternAdviceDetailForm
                  :item="scope.item"
                  :fee-nature-options="feeNatureOptions"
                  :medication-rate-options="medicationRateOptions"
                  :medication-route-options="medicationRouteOptions"
                  :patient-diagnoses="scope.patientDiagnoses"
                  :calc-total-fee="calcDrugLineTotalFee"
                  @patch="scope.patch"
                  @update:patient-diagnoses="scope.updatePatientDiagnoses"
                />
              </template>
              <template #collapsed="scope">
                <WesternAdviceCollapsed
                  :item="scope.item"
                  :calc-total-fee="calcDrugLineTotalFee"
                  :medication-rate-options="medicationRateOptions"
                  :medication-route-options="medicationRouteOptions"
                />
              </template>
            </AdviceEditCard>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <ElTag :type="getAdviceStatusTagType(row)" size="small" effect="light">
              {{ row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="type" label="类型" width="128" align="center">
          <template #default="{ row }">
            <span>{{ row.type }}</span>
            <template v-if="resolveAdviceTherapyTypeText(row)">
              <span class="ip-advice-type-sep"> / </span>
              <span
                class="ip-advice-therapy-text"
                :class="adviceTherapyTypeClass(row.therapyTypeCode)"
              >
                {{ resolveAdviceTherapyTypeText(row) }}
              </span>
            </template>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="content" label="医嘱内容" min-width="350">
          <template #default="{ row }">
            <AdviceOrderDisplayCell
              :row-key="row.key"
              :group-items="row.groupItems"
              :content="row.content"
              :dosage-text="row.dosageText"
              :extension="row.extension"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="startTime" label="开立时间" width="170" align="center" />
        <ElTableColumn prop="lineTotalAmount" label="总金额" width="100" align="center" />
        <ElTableColumn prop="diagnosisSummary" label="诊断" min-width="140" align="center" />
        <ElTableColumn prop="orderingDoctor" label="开立医生" width="100" align="center" />
        <ElTableColumn prop="skinTestText" label="皮试" width="72" align="center" />
      </ElTable>
    </div>

    <InpatientAdviceQuickActionBar class="flex-shrink-0" @action="handleQuickAction" />

    <AppDialog
      v-model="tcmDialogVisible"
      title="中药处方"
      width="1280px"
      destroy-on-close
      append-to-body
    >
      <div class="h-[78vh] min-h-[560px]">
        <TcmAdvice :encounter-id="encounterId" :patient-id="patientId" />
      </div>
    </AppDialog>
  </div>
</template>

<style scoped>
.ip-advice-workspace {
  box-sizing: border-box;
  padding-bottom: 0;
}

.advice-table :deep(.el-table__header-wrapper th.el-table__cell) {
  background: #eaf4ff !important;
}

.advice-table :deep(.advice-editor-cell .cell) {
  padding: 8px 10px !important;
  overflow: visible !important;
}

.advice-table :deep(.advice-editor-hidden-col) {
  padding: 0 !important;
  width: 0 !important;
  min-width: 0 !important;
  border: 0 !important;
  overflow: visible !important;
}

.advice-table :deep(.advice-editor-cell) {
  overflow: visible !important;
}

.advice-table :deep(.el-table__body-wrapper) {
  overflow-x: auto;
  overflow-y: auto;
}

.ip-advice-type-sep {
  color: var(--el-text-color-secondary);
}

.ip-advice-therapy-text--long {
  color: #a6745c;
  font-weight: 500;
}

.ip-advice-therapy-text--temp {
  color: #3787a5;
  font-weight: 500;
}

.ip-advice-therapy-text--discharge {
  color: #5b8c3e;
  font-weight: 500;
}
</style>
