<script setup lang="ts">
import type { UseAdviceTabProps } from '../composables/use-advice-tab';
import type { AdviceRowItem } from '../services/advice.types';
import type { HistoryAdviceRowItem } from '../services/history-advice.types';

import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';

import { computed, ref } from 'vue';

import { ElButton, ElMessage, ElTable, ElTableColumn, ElTag } from 'element-plus';

import {
  ORDER_TEMPLATE_APPLY_CONTEXT_DOCTOR,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_WORKSTATION_OUTPATIENT,
  previewOrderTemplateApplyApi,
} from '#/api/basic/order-template';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import { useAuthStore } from '#/store';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  buildServiceApplyFormCellClassName,
  buildServiceApplyFormRowClassName,
  isServiceApplyFormGroupStart,
} from '#/utils/service-apply-form-group-display';

import { useAdviceTab } from '../composables/use-advice-tab';
import { getSkinTestDisplayTagType } from '../services/advice-list';
import {
  applyHistoryAdviceToAdviceDraft,
  HistoryAdviceApplyError,
} from '../services/history-advice-apply.service';
import {
  applyOrderTemplateToAdviceDraft,
  OrderTemplateApplyError,
  prepareOrderTemplateApplyConfirm,
} from '../services/order-template-apply.service';
import { openOrderTemplateApplyConfirmDialog } from '../services/order-template-apply-confirm-dialog.controller';
import AdviceDraftBindDialog from './advice-draft-bind-dialog.vue';
import AdviceEditCard from './advice-edit-card.vue';
import HistoryAdviceDrawer from './history-advice-drawer.vue';
import OrderTemplateApplyConfirmDialog from './order-template-apply-confirm-dialog.vue';
import OrderTemplateSidePanel from './order-template-side-panel.vue';
import SaveAsOrderTemplateDialog from './save-as-order-template-dialog.vue';
import WesternAdviceCollapsed from './western-advice-collapsed.vue';
import WesternAdviceDetailForm from './western-advice-detail-form.vue';

defineOptions({ name: 'AdviceWorkspace' });

const props = withDefaults(defineProps<UseAdviceTabProps>(), {
  encounterId: '',
  patientId: '',
});

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
  handlePrintServiceApply,
  handleRowDblclick,
  handleSaveGroup,
  handleSign,
  handleSpanMethod,
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
  serviceApplyPrintLoading,
  validateWesternRow,
} = useAdviceTab(props);

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

const authStore = useAuthStore();

const historyAdviceDrawerVisible = ref(false);
const historyAdviceApplyLoading = ref(false);
const saveAsOrderTemplateDialogVisible = ref(false);
const orderTemplateSidePanelRef = ref<InstanceType<typeof OrderTemplateSidePanel> | null>(null);
const orderTemplateApplyLoading = ref(false);

/** 另存组套：当前列表非编辑行快照 */
const saveOrderTemplateAdviceRows = computed(() => getTableRows().filter((row) => !row.isEditor));

/** 侧栏点击组套：预览明细与带出行，确认后套用至当前就诊草稿 */
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
      workstationKindCode: ORDER_TEMPLATE_WORKSTATION_OUTPATIENT,
    });
    const lines = preview?.lines ?? [];
    const templateName = preview?.header?.name ?? header.name ?? '';
    const applyParams = {
      templateKindEnum: preview?.header?.templateKindEnum ?? header.templateKindEnum,
      templateName,
      lines,
      encounterId: encounter,
      patientId: patient,
      organizationUnitId,
      medicationRateOptions: medicationRateOptions.value,
      medicationRouteOptions: medicationRouteOptions.value,
      feeNatureOptions: feeNatureOptions.value,
      existingAdviceRows: getTableRows().filter((row) => !row.isEditor),
    };
    const confirmPreview = await prepareOrderTemplateApplyConfirm(applyParams);
    orderTemplateApplyLoading.value = false;
    const dialogResult = await openOrderTemplateApplyConfirmDialog({
      templateName: confirmPreview.templateName || templateName,
      templateRows: confirmPreview.templateRows,
      bindRows: confirmPreview.bindRows,
      bindSourceGroups: confirmPreview.bindSourceGroups,
    });
    if (!dialogResult) {
      orderTemplateSidePanelRef.value?.clearSelection();
      return;
    }
    orderTemplateApplyLoading.value = true;
    await applyOrderTemplateToAdviceDraft({
      ...applyParams,
      preparedDraftLines: confirmPreview.draftLines,
      preConfirmedTemplateRows: dialogResult.templateRows,
      preConfirmedBindDialogRows: dialogResult.bindRows,
      preConfirmedBindSkipSourceKeys: dialogResult.skipSourceKeys,
    });
    const appliedLineCount = dialogResult.templateRows.length + dialogResult.bindRows.length;
    ElMessage.success(`已套用组套「${templateName}」（${appliedLineCount} 项）`);
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

/** 打开历史医嘱抽屉 */
function handleOpenHistoryAdviceDrawer() {
  if (!(patientId.value ?? '').trim()) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!(encounterId.value ?? '').trim()) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  historyAdviceDrawerVisible.value = true;
}

/** 另存成功后刷新侧栏（维护组套 + 另存模板合并列表） */
function handleSaveAsOrderTemplateSaved() {
  void orderTemplateSidePanelRef.value?.reloadTemplateList();
}

/** 打开另存为组套弹窗 */
function handleOpenSaveAsOrderTemplateDialog() {
  if (!(patientId.value ?? '').trim()) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!(encounterId.value ?? '').trim()) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  saveAsOrderTemplateDialogVisible.value = true;
}

/** 历史医嘱复用：拉目录、校验库存、静默写入草稿 */
async function handleHistoryAdviceReuse(rows: HistoryAdviceRowItem[]) {
  if (rows.length === 0) {
    return;
  }
  const encounter = preserveApiIdForRequest(encounterId.value);
  const patient = preserveApiIdForRequest(patientId.value);
  if (!encounter || !patient) {
    ElMessage.warning('请先选择患者与就诊');
    return;
  }
  const orgRaw = authStore.getSelectedOrganization();
  const organizationUnitId =
    orgRaw !== null && orgRaw !== undefined && String(orgRaw).trim() !== ''
      ? String(orgRaw)
      : undefined;
  historyAdviceApplyLoading.value = true;
  try {
    const result = await applyHistoryAdviceToAdviceDraft({
      rows,
      encounterId: encounter,
      patientId: patient,
      organizationUnitId,
      medicationRateOptions: medicationRateOptions.value,
      medicationRouteOptions: medicationRouteOptions.value,
      feeNatureOptions: feeNatureOptions.value,
      existingAdviceRows: getTableRows().filter((row) => !row.isEditor),
    });
    ElMessage.success(`已复用 ${result.appliedLineCount} 条历史医嘱`);
    historyAdviceDrawerVisible.value = false;
    await reload();
  } catch (error) {
    if (error instanceof HistoryAdviceApplyError) {
      ElMessage.error(error.message);
      return;
    }
    ElMessage.error('历史医嘱复用失败，请稍后重试');
  } finally {
    historyAdviceApplyLoading.value = false;
  }
}

defineExpose({
  loadAdviceInit,
  reload,
});
</script>

<template>
  <div class="advice-workspace-root h-full min-h-0">
    <OrderTemplateSidePanel
      ref="orderTemplateSidePanelRef"
      v-loading="orderTemplateApplyLoading"
      class="advice-workspace-root__template-panel"
      :encounter-id="encounterId"
      :patient-id="patientId"
      @select="handleOrderTemplateSelect"
    />

    <div class="advice-workspace-root__main flex min-h-0 flex-col p-2">
      <div class="mb-2 flex w-full flex-wrap items-center gap-1">
        <ElButton type="primary" @click="handleAdd()">新增</ElButton>
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
        <ElButton plain @click="handleOpenHistoryAdviceDrawer">历史医嘱</ElButton>
        <ElButton plain :loading="serviceApplyPrintLoading" @click="handlePrintServiceApply">
          打印申请单
        </ElButton>
        <ElButton plain @click="handleOpenSaveAsOrderTemplateDialog">另存为组套</ElButton>
      </div>

      <div
        v-loading="adviceWorkspaceTableLoading || historyAdviceApplyLoading"
        class="border-border-base bg-background-card min-h-0 flex-1 overflow-hidden rounded border"
      >
        <ElTable
          ref="adviceTableRef"
          :data="getTableRows()"
          :span-method="handleSpanMethod"
          :row-class-name="resolveAdviceRowClassName"
          :cell-class-name="getCellClassName"
          border
          stripe
          class="advice-table"
          height="100%"
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
          <ElTableColumn prop="type" label="类型" width="108" align="center" />
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
          <ElTableColumn prop="skinTestText" label="皮试" width="80" align="center">
            <template #default="{ row }">
              <ElTag
                v-if="getSkinTestDisplayTagType(row)"
                :type="getSkinTestDisplayTagType(row)!"
                size="small"
                effect="light"
              >
                {{ row.skinTestText }}
              </ElTag>
              <span v-else>{{ row.skinTestText }}</span>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
    </div>

    <HistoryAdviceDrawer
      v-model:open="historyAdviceDrawerVisible"
      :patient-id="patientId"
      :encounter-id="encounterId"
      @reuse="handleHistoryAdviceReuse"
    />

    <SaveAsOrderTemplateDialog
      v-model:open="saveAsOrderTemplateDialogVisible"
      :patient-id="patientId"
      :encounter-id="encounterId"
      :advice-rows="saveOrderTemplateAdviceRows"
      @saved="handleSaveAsOrderTemplateSaved"
    />

    <AdviceDraftBindDialog />

    <OrderTemplateApplyConfirmDialog />
  </div>
</template>

<style scoped>
.advice-workspace-root {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: 0;
}

.advice-workspace-root__template-panel {
  min-height: 0;
}

.advice-workspace-root__main {
  min-width: 0;
  min-height: 0;
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

/* 表体纵向滚动由 ElTable max-height 控制；横向允许成组单元格略伸出时仍保留 y 向滚动 */
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
</style>
