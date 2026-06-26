<script setup lang="ts">
import type { InpatientAdviceQuickAction } from './inpatient-advice-quick-action-bar.vue';
import type { UseInpatientAdviceTabProps } from '../composables/use-inpatient-advice-tab';
import type { AdviceRowItem, InpatientAdviceTherapyType } from '../services/advice.types';

import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';

import { ref } from 'vue';

import { ElButton, ElMessage, ElMessageBox, ElTable, ElTableColumn, ElTag } from 'element-plus';

import {
  ORDER_TEMPLATE_APPLY_CONTEXT_DOCTOR,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_WORKSTATION_INPATIENT,
  previewOrderTemplateApplyApi,
} from '#/api/basic/order-template';
import AppDialog from '#/components/app-dialog.vue';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import TcmAdvice from '#/components/doctor-station/tcm-advice/tcm-advice.vue';
import OrderTemplateSidePanel from '#/views/outpatient/doctor-station/advice/components/order-template-side-panel.vue';
import { useAuthStore } from '#/store';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  buildServiceApplyFormCellClassName,
  buildServiceApplyFormRowClassName,
  isServiceApplyFormGroupStart,
} from '#/utils/service-apply-form-group-display';

import { useInpatientAdviceTab } from '../composables/use-inpatient-advice-tab';
import { adviceTherapyTypeClass, resolveAdviceTherapyTypeText } from '../services/advice-list';
import {
  applyOrderTemplateToAdviceDraft,
  OrderTemplateApplyError,
} from '../services/order-template-apply.service';
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

const authStore = useAuthStore();
const orderTemplateSidePanelRef = ref<InstanceType<typeof OrderTemplateSidePanel> | null>(null);
const orderTemplateApplyLoading = ref(false);

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
  getCellClassName: getBaseCellClassName,
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
  handleWithdraw,
  handleStopLongTerm,
  handleCancelStopLongTerm,
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

/** 医嘱表最右列索引（申请单聚组右边框） */
const ADVICE_APPLY_FORM_TABLE_EDGE_RIGHT_INDEX = 9;

/** 申请单聚组：表格行 class-name */
const resolveAdviceRowClassName = ({ row, rowIndex }: { row: AdviceRowItem; rowIndex: number }) => {
  const tableRows = getTableRows();
  return buildServiceApplyFormRowClassName({ row, rowIndex, rows: tableRows });
};

/** 合并编辑行与申请单聚组的 cell-class-name */
const getCellClassName = ({
  columnIndex,
  row,
  rowIndex,
  column,
}: {
  columnIndex: number;
  row: AdviceRowItem;
  rowIndex: number;
  column?: { fixed?: boolean | string };
}) => {
  const base = getBaseCellClassName({ columnIndex, row });
  const applyForm = buildServiceApplyFormCellClassName({
    row,
    rowIndex,
    rows: getTableRows(),
    columnIndex,
    edgeRightIndex: ADVICE_APPLY_FORM_TABLE_EDGE_RIGHT_INDEX,
    columnFixed: column?.fixed,
  });
  return [base, applyForm].filter((part) => part.length > 0).join(' ');
};

defineExpose({
  loadAdviceInit,
  reload,
});

/** 组套套用前选择长临医嘱类型 */
async function promptInpatientTemplateTherapyType(): Promise<InpatientAdviceTherapyType | null> {
  try {
    await ElMessageBox.confirm('组套套用后将以所选类型写入医嘱草稿，请确认。', '选择医嘱类型', {
      confirmButtonText: '长期医嘱',
      cancelButtonText: '临时医嘱',
      distinguishCancelAndClose: true,
      type: 'info',
    });
    return 'LONG_TERM';
  } catch (action) {
    if (action === 'cancel') {
      return 'TEMPORARY';
    }
    return null;
  }
}

/** 侧栏点击组套：确认长临后预览并套用至当前就诊草稿 */
async function handleOrderTemplateSelect(header: null | OrderTemplateHeaderItem) {
  if (!header || orderTemplateApplyLoading.value) {
    return;
  }
  const orderTemplateId = preserveApiIdForRequest(header.orderTemplateId);
  const encounter = preserveApiIdForRequest(encounterId.value);
  const patient = preserveApiIdForRequest(patientId.value);
  if (!orderTemplateId || !encounter || !patient) {
    return;
  }
  const templateKind = String(header.templateKindEnum ?? '')
    .trim()
    .toUpperCase();
  if (templateKind === ORDER_TEMPLATE_KIND_TCM) {
    ElMessage.warning('中药组套暂不支持套用');
    orderTemplateSidePanelRef.value?.clearSelection();
    return;
  }
  const therapyTypeCode = await promptInpatientTemplateTherapyType();
  if (!therapyTypeCode) {
    orderTemplateSidePanelRef.value?.clearSelection();
    return;
  }
  const orgRaw = authStore.getSelectedOrganization();
  const organizationUnitId =
    orgRaw !== null && orgRaw !== undefined && String(orgRaw).trim() !== ''
      ? String(orgRaw)
      : undefined;
  orderTemplateApplyLoading.value = true;
  try {
    const preview = await previewOrderTemplateApplyApi({
      orderTemplateId,
      encounterId: encounter,
      patientId: patient,
      applyContextCode: ORDER_TEMPLATE_APPLY_CONTEXT_DOCTOR,
      workstationKindCode: ORDER_TEMPLATE_WORKSTATION_INPATIENT,
    });
    const lines = preview?.lines ?? [];
    const result = await applyOrderTemplateToAdviceDraft({
      templateKindEnum: preview?.header?.templateKindEnum ?? header.templateKindEnum,
      lines,
      encounterId: encounter,
      patientId: patient,
      organizationUnitId,
      therapyTypeCode,
      medicationRateOptions: medicationRateOptions.value,
      medicationRouteOptions: medicationRouteOptions.value,
      feeNatureOptions: feeNatureOptions.value,
      existingAdviceRows: getTableRows().filter((row) => !row.isEditor),
    });
    const therapyLabel = therapyTypeCode === 'LONG_TERM' ? '长期' : '临时';
    ElMessage.success(
      `已套用组套「${preview?.header?.name ?? header.name ?? ''}」为${therapyLabel}医嘱（${result.appliedLineCount} 项）`,
    );
    orderTemplateSidePanelRef.value?.clearSelection();
    await reload();
  } catch (error) {
    orderTemplateSidePanelRef.value?.clearSelection();
    if (error instanceof OrderTemplateApplyError) {
      ElMessage.error(error.message);
      return;
    }
    ElMessage.error('组套套用失败，请稍后重试');
  } finally {
    orderTemplateApplyLoading.value = false;
  }
}

/** 底部快捷申请条上抛主壳 */
function handleQuickAction(action: InpatientAdviceQuickAction) {
  emit('quickAction', action);
}
</script>

<template>
  <div class="ip-advice-workspace-root h-full min-h-0">
    <OrderTemplateSidePanel
      ref="orderTemplateSidePanelRef"
      v-loading="orderTemplateApplyLoading"
      class="ip-advice-workspace-root__template-panel"
      :encounter-id="encounterId"
      :patient-id="patientId"
      @select="handleOrderTemplateSelect"
    />

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
        <ElButton
          type="warning"
          plain
          :loading="adviceMutationLoading"
          @click="handleCancelStopLongTerm()"
        >
          取消停嘱
        </ElButton>
        <ElButton type="danger" plain :loading="adviceMutationLoading" @click="handleDelete()">
          删除
        </ElButton>
        <ElButton plain @click="handleMedicationUngroup">拆组</ElButton>
        <ElButton plain @click="handleMedicationAssignGroup">并组</ElButton>
      </div>

      <div
        class="border-border-base bg-background-card min-h-0 flex-1 overflow-hidden rounded border"
      >
        <ElTable
          ref="adviceTableRef"
          v-loading="adviceWorkspaceTableLoading"
          :data="getTableRows()"
          :span-method="handleSpanMethod"
          :row-class-name="resolveAdviceRowClassName"
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
            <template #default="{ row, $index }">
              <div
                class="advice-order-content-wrap"
                :class="{ 'is-apply-form-block': Boolean(row.applyFormNo?.trim()) }"
              >
                <div
                  v-if="isServiceApplyFormGroupStart(row, $index, getTableRows())"
                  class="doctor-apply-form-group-label"
                >
                  <ElTag size="small" effect="plain" type="primary">
                    申请单 {{ row.applyFormNo }}
                  </ElTag>
                </div>
                <AdviceOrderDisplayCell
                  :row-key="row.key"
                  :group-items="row.groupItems"
                  :content="row.content"
                  :dosage-text="row.dosageText"
                  :extension="row.extension"
                />
              </div>
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
          <TcmAdvice
            :encounter-id="encounterId"
            :patient-id="patientId"
            station-scope="inpatient"
          />
        </div>
      </AppDialog>
    </div>
  </div>
</template>

<style scoped>
.ip-advice-workspace-root {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: 0;
}

.ip-advice-workspace-root__template-panel {
  min-height: 0;
}

.ip-advice-workspace {
  box-sizing: border-box;
  min-width: 0;
  min-height: 0;
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

/* 同一申请单多行：浅底 + 四边框成一组；组间顶部分隔；组内无横线 */
.advice-table :deep(td.doctor-apply-form-cell) {
  background-color: var(--el-color-primary-light-9) !important;
}

.advice-table :deep(td.doctor-apply-form-cell.is-group-edge-left) {
  border-left: 2px solid var(--el-color-primary-light-5) !important;
}

.advice-table :deep(td.doctor-apply-form-cell.is-group-edge-right) {
  border-right: 2px solid var(--el-color-primary-light-5) !important;
}

.advice-table :deep(td.doctor-apply-form-cell.is-group-start) {
  border-top: 2px solid var(--el-color-primary-light-5) !important;
}

.advice-table :deep(td.doctor-apply-form-cell.is-group-end) {
  border-bottom: 2px solid var(--el-color-primary-light-5) !important;
}

.advice-table :deep(td.doctor-apply-form-cell.is-group-continue) {
  border-top: none !important;
}

.advice-table :deep(td.doctor-apply-form-cell:not(.is-group-end)) {
  border-bottom: none !important;
}

.advice-table :deep(td.doctor-apply-form-cell.is-group-start.is-group-edge-left) {
  border-top-left-radius: 4px;
}

.advice-table :deep(td.doctor-apply-form-cell.is-group-start.is-group-edge-right) {
  border-top-right-radius: 4px;
}

.advice-table :deep(td.doctor-apply-form-cell.is-group-end.is-group-edge-left) {
  border-bottom-left-radius: 4px;
}

.advice-table :deep(td.doctor-apply-form-cell.is-group-end.is-group-edge-right) {
  border-bottom-right-radius: 4px;
}

.advice-order-content-wrap.is-apply-form-block {
  padding-left: 8px;
}

.doctor-apply-form-group-label {
  margin-bottom: 4px;
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
