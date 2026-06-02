<script setup lang="ts">
import type { UseAdviceTabProps } from '../composables/use-advice-tab';
import type { AdviceRowItem } from '../services/advice.types';

import { ElButton, ElTable, ElTableColumn, ElTag } from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import { useAdviceTab } from '../composables/use-advice-tab';

import TcmAdvice from '#/components/doctor-station/tcm-advice/tcm-advice.vue';

import AdviceEditCard from './advice-edit-card.vue';
import WesternAdviceCollapsed from './western-advice-collapsed.vue';
import WesternAdviceDetailForm from './western-advice-detail-form.vue';

defineOptions({ name: 'AdviceWorkspace' });

const props = withDefaults(defineProps<UseAdviceTabProps>(), {
  encounterId: '',
  patientId: '',
  patientServiceTypeName: '',
});

const {
  adviceMutationLoading,
  adviceTableRef,
  adviceWorkspaceTableLoading,
  addDrugGuardWestern,
  calcDrugLineTotalFee,
  defaultFeeNatureFromPatient,
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
} = useAdviceTab(props);

defineExpose({
  loadAdviceInit,
  reload,
});
</script>

<template>
  <div class="flex h-full min-h-0 flex-col p-2">
    <div class="mb-2 flex w-full items-center gap-1">
      <ElButton type="primary" @click="handleAdd()">新增</ElButton>
      <ElButton type="primary" @click="handleOpenTcmDialog()">中药处方</ElButton>
      <ElButton type="primary" :loading="adviceMutationLoading" @click="handleSign()">
        签发
      </ElButton>
      <ElButton type="warning" plain :loading="adviceMutationLoading" @click="handleWithdraw()">
        撤回
      </ElButton>
      <ElButton type="danger" plain :loading="adviceMutationLoading" @click="handleDelete()">
        删除
      </ElButton>
      <ElButton plain @click="handleMedicationUngroup">拆组</ElButton>
      <ElButton plain @click="handleMedicationAssignGroup">并组</ElButton>
      <ElButton plain @click="handleToolbarPlaceholder('组套')">组套</ElButton>
    </div>

    <div
      class="border-border-base bg-background-card max-h-[calc(100vh-230px)] min-h-0 flex-1 overflow-hidden rounded border"
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
              :default-fee-nature="defaultFeeNatureFromPatient"
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
        <ElTableColumn prop="type" label="类型" width="108" align="center" />
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

    <AppDialog
      v-model="tcmDialogVisible"
      title="中药处方"
      width="1280px"
      destroy-on-close
      append-to-body
    >
      <div class="h-[78vh] min-h-[560px]">
        <TcmAdvice />
      </div>
    </AppDialog>
  </div>
</template>

<style scoped>
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

/* 表体纵向滚动由 ElTable max-height 控制；横向允许成组单元格略伸出时仍保留 y 向滚动 */
.advice-table :deep(.el-table__body-wrapper) {
  overflow-x: auto;
  overflow-y: auto;
}
</style>
