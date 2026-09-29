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
import { useEnsureClinicalReception } from '#/components/doctor-station';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import { useCompactViewport } from '#/composables/use-compact-viewport';
import { ADVICE_TABLE_COL_WIDTH } from '#/constants/layout/doctor-station-compact-presets';
import { useAuthStore } from '#/store';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { isServiceApplyFormGroupStart } from '#/utils/service-apply-form-group-display';

import { useAdviceTab } from '../composables/use-advice-tab';
import DetailAuditRemindDialog from '#/chs/components/detail-audit-remind-dialog.vue';
import type { DetailAuditRemindDialogExpose } from '#/chs/composables/use-detail-audit-gate';
import {
  getAdviceChargeStatusTagType,
  getSkinTestDisplayTagType,
  sumAdviceListTotalAmount,
} from '../services/advice-list';
import {
  applyHistoryAdviceToAdviceDraft,
  HistoryAdviceApplyError,
} from '../services/history-advice-apply.service';
import { openOrderTemplateApplyConfirmDialog } from '../services/order-template-apply-confirm-dialog.controller';
import {
  applyOrderTemplateToAdviceDraft,
  OrderTemplateApplyError,
  prepareOrderTemplateApplyConfirm,
} from '../services/order-template-apply.service';
import AdviceDraftBindDialog from './advice-draft-bind-dialog.vue';
import AdviceDiagnosisSummaryCell from './advice-diagnosis-summary-cell.vue';
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

const detailAuditDialogRef = ref<DetailAuditRemindDialogExpose | null>(null);

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
  diagnosisItemsForList,
  encounterId,
  getAdviceStatusTagType,
  getCellClassName,
  getTableRows,
  handleAdd,
  handleCloseEditor,
  handleDelete,
  handleDeleteDrugInEditor,
  handleEditorDirtyChange,
  handleMedicationAssignGroup,
  handleMedicationUngroup,
  handleRowDblclick,
  handleSaveGroup,
  handleSign,
  handleSpanMethod,
  handleWithdraw,
  lastSelectedEncounterDiagnosisIds,
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
  tableSelection,
  validateWesternRow,
} = useAdviceTab(props, { detailAuditDialogRef });

const { pickPreset } = useCompactViewport();

/** 表格列宽：<1680 使用 compact，≥1680（含 1920）与现网 default 一致 */
const adviceTableColWidth = pickPreset(ADVICE_TABLE_COL_WIDTH);

const authStore = useAuthStore();
const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();

const historyAdviceDrawerVisible = ref(false);
const historyAdviceApplyLoading = ref(false);
const saveAsOrderTemplateDialogVisible = ref(false);
const orderTemplateSidePanelRef = ref<InstanceType<typeof OrderTemplateSidePanel> | null>(null);
const orderTemplateApplyLoading = ref(false);

/** 另存组套：当前列表非编辑行快照 */
const saveOrderTemplateAdviceRows = computed(() => getTableRows().filter((row) => !row.isEditor));

/** 列表各明细行总金额合计（6 位加总后 2 位展示；入账以后端为准） */
const adviceListTotalAmountText = computed(() => sumAdviceListTotalAmount(getTableRows()));

/** 侧栏点击组套：预览明细，确认后套用至当前就诊草稿 */
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
  const receptionOk = await ensureReceptionBeforeClinicalWork();
  if (!receptionOk) {
    orderTemplateSidePanelRef.value?.clearSelection();
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
    });
    const appliedLineCount = dialogResult.templateRows.length;
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

/** 打开历史医嘱抽屉（纯查看；复用时再接诊） */
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
async function handleOpenSaveAsOrderTemplateDialog() {
  if (!(patientId.value ?? '').trim()) {
    ElMessage.warning('请先选择患者');
    return;
  }
  if (!(encounterId.value ?? '').trim()) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  const receptionOk = await ensureReceptionBeforeClinicalWork();
  if (!receptionOk) return;
  saveAsOrderTemplateDialogVisible.value = true;
}

/** 将勾选/历史行按「新开草稿」写入当前就诊（复用与复制共用） */
async function applyAdviceRowsAsNewDraft(
  rows: HistoryAdviceRowItem[],
  options?: { successMessage?: string; closeHistoryDrawer?: boolean },
) {
  if (rows.length === 0) {
    return;
  }
  const encounter = preserveApiIdForRequest(encounterId.value);
  const patient = preserveApiIdForRequest(patientId.value);
  if (!encounter || !patient) {
    ElMessage.warning('请先选择患者与就诊');
    return;
  }
  const receptionOk = await ensureReceptionBeforeClinicalWork();
  if (!receptionOk) {
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
    if (options?.successMessage) {
      ElMessage.success(options.successMessage);
    } else if (result.importedDiagnosisCount > 0) {
      ElMessage.success(
        `已复用 ${result.appliedLineCount} 条历史医嘱，并同步 ${result.importedDiagnosisCount} 条诊断`,
      );
    } else {
      ElMessage.success(`已复用 ${result.appliedLineCount} 条历史医嘱`);
    }
    // 慢病备案未过检：诊断仍带入为普通，单独提示原因
    if (result.chronicSkippedHints && result.chronicSkippedHints.length > 0) {
      ElMessage.warning(result.chronicSkippedHints.join('；'));
    }
    if (options?.closeHistoryDrawer) {
      historyAdviceDrawerVisible.value = false;
    }
    await reload();
  } catch (error) {
    if (error instanceof HistoryAdviceApplyError) {
      ElMessage.error(error.message);
      return;
    }
    // 后端业务异常通常已由请求拦截器提示，此处不再弹泛化失败避免重复
  } finally {
    historyAdviceApplyLoading.value = false;
  }
}

/** 历史医嘱复用：拉目录、校验库存、导入对应诊断、静默写入草稿 */
async function handleHistoryAdviceReuse(rows: HistoryAdviceRowItem[]) {
  await applyAdviceRowsAsNewDraft(rows, { closeHistoryDrawer: true });
}

/** 复制勾选医嘱（含退费）：与历史复用同一落库；成组行按 mergedAdviceRows 整组展开 */
async function handleCopySelectedAdvice() {
  const selected = tableSelection.value.filter((row) => !row.isEditor);
  if (selected.length === 0) {
    ElMessage.warning('请先勾选要复制的医嘱');
    return;
  }
  const encounter = preserveApiIdForRequest(encounterId.value);
  if (!encounter) {
    ElMessage.warning('请先选择就诊');
    return;
  }
  // 勾选行补 encounterId，成组展示行保留 mergedAdviceRows 供 apply 整组展开
  const rows: HistoryAdviceRowItem[] = selected.map((row) => ({
    ...row,
    encounterId: encounter,
  }));
  await applyAdviceRowsAsNewDraft(rows, { successMessage: '已复制' });
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
      <div
        class="advice-workspace-toolbar mb-2 flex w-full flex-wrap items-center justify-between gap-2"
      >
        <div class="flex flex-wrap items-center gap-1">
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
          <ElButton
            type="success"
            plain
            :loading="historyAdviceApplyLoading"
            @click="handleCopySelectedAdvice"
          >
            复制
          </ElButton>
          <ElButton plain @click="handleMedicationUngroup">拆组</ElButton>
          <ElButton plain @click="handleMedicationAssignGroup">并组</ElButton>
          <ElButton plain @click="handleOpenHistoryAdviceDrawer">历史医嘱</ElButton>
          <ElButton plain @click="handleOpenSaveAsOrderTemplateDialog">另存为组套</ElButton>
        </div>
        <div class="advice-list-total shrink-0">
          <span class="advice-list-total__label">合计金额</span>
          <strong class="advice-list-total__value">{{ adviceListTotalAmountText }}</strong>
        </div>
      </div>

      <div
        v-loading="adviceWorkspaceTableLoading || historyAdviceApplyLoading"
        class="border-border-base bg-background-card min-h-0 flex-1 overflow-hidden rounded border"
      >
        <ElTable
          ref="adviceTableRef"
          :data="getTableRows()"
          :span-method="handleSpanMethod"
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
                v-model:last-selected-encounter-diagnosis-ids="lastSelectedEncounterDiagnosisIds"
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
                @delete-drug="handleDeleteDrugInEditor"
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
                    :encounter-id="encounterId"
                    :patient-id="patientId"
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
                    :patient-diagnoses="scope.patientDiagnoses"
                  />
                </template>
              </AdviceEditCard>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="status"
            label="签发状态"
            :width="adviceTableColWidth.status"
            align="center"
          >
            <template #default="{ row }">
              <ElTag :type="getAdviceStatusTagType(row)" size="small" effect="light">
                {{ row.status }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="type"
            label="类型"
            :width="adviceTableColWidth.type"
            align="center"
          />
          <ElTableColumn
            prop="content"
            label="医嘱内容"
            :min-width="adviceTableColWidth.contentMin"
          >
            <template #default="{ row, $index }">
              <AdviceOrderDisplayCell
                :row-key="row.key"
                :group-items="row.groupItems"
                :content="row.content"
                :dosage-text="row.dosageText"
                :extension="row.extension"
                :apply-form-no="
                  isServiceApplyFormGroupStart(row, $index, getTableRows())
                    ? row.applyFormNo
                    : undefined
                "
              />
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="startTime"
            label="签发时间"
            :width="adviceTableColWidth.startTime"
            align="center"
          />
          <ElTableColumn
            prop="lineTotalAmount"
            label="总金额"
            :width="adviceTableColWidth.amount"
            align="center"
          />
          <ElTableColumn
            prop="feeNatureText"
            label="费用性质"
            :width="adviceTableColWidth.feeNature"
            align="center"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span v-if="!row.isEditor">{{ row.feeNatureText || '—' }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="chargeStatus"
            label="费用状态"
            :width="adviceTableColWidth.chargeStatus"
            align="center"
          >
            <template #default="{ row }">
              <ElTag :type="getAdviceChargeStatusTagType(row)" size="small" effect="light">
                {{ row.chargeStatus }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="diagnosisSummary"
            label="诊断"
            :min-width="adviceTableColWidth.diagnosisMin"
            align="center"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <AdviceDiagnosisSummaryCell
                v-if="!row.isEditor"
                :diagnosis-summary="row.diagnosisSummary"
                :encounter-diagnosis-ids="row.encounterDiagnosisIds"
                :diagnosis-items="diagnosisItemsForList"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn
            prop="orderingDoctor"
            label="开立医生"
            :width="adviceTableColWidth.doctor"
            align="center"
            show-overflow-tooltip
          />
          <ElTableColumn
            prop="skinTestText"
            label="皮试"
            :width="adviceTableColWidth.skinTest"
            align="center"
          >
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

    <!-- 签发前绑定确认弹窗挂载点（resolveAndPersistAdviceDraftBindsAtSign） -->
    <AdviceDraftBindDialog />

    <OrderTemplateApplyConfirmDialog />

    <DetailAuditRemindDialog ref="detailAuditDialogRef" warn-type="1" />
  </div>
</template>

<style scoped>
.advice-workspace-root {
  display: grid;
  /* 组套侧栏自带宽度并可收起，列宽随内容 */
  grid-template-columns: auto minmax(0, 1fr);
  min-height: 0;
}

.advice-workspace-root__template-panel {
  min-height: 0;
}

.advice-workspace-root__main {
  min-width: 0;
  min-height: 0;
}

.advice-list-total {
  display: inline-flex;
  gap: 8px;
  align-items: baseline;
  font-size: 13px;
  line-height: 20px;
}

.advice-list-total__label {
  color: var(--el-text-color-regular);
}

.advice-list-total__value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
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
</style>
