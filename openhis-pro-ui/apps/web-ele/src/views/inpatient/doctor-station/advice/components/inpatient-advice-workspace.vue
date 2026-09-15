<script setup lang="ts">
import type { UseInpatientAdviceTabProps } from '../composables/use-inpatient-advice-tab';
import type { AdviceRowItem, InpatientAdviceTherapyType } from '../services/advice.types';
import type { InpatientAdviceQuickAction } from './inpatient-advice-quick-action-bar.vue';

import type { OrderTemplateHeaderItem } from '#/api/basic/order-template';

import { computed, h, ref } from 'vue';

import { CaretBottom, CaretTop } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDatePicker,
  ElIcon,
  ElMessage,
  ElMessageBox,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  ORDER_TEMPLATE_APPLY_CONTEXT_DOCTOR,
  ORDER_TEMPLATE_KIND_TCM,
  ORDER_TEMPLATE_WORKSTATION_INPATIENT,
  previewOrderTemplateApplyApi,
} from '#/api/basic/order-template';
import AdviceOrderDisplayCell from '#/components/outpatient/advice-order-display-cell.vue';
import { useAuthStore } from '#/store';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { isServiceApplyFormGroupStart } from '#/utils/service-apply-form-group-display';
import type { AdviceRowItem as OutpatientAdviceRowItem } from '#/views/outpatient/doctor-station/advice/services/advice.types';
import OrderTemplateSidePanel from '#/views/outpatient/doctor-station/advice/components/order-template-side-panel.vue';
import AdviceDraftBindDialog from '#/views/outpatient/doctor-station/advice/components/advice-draft-bind-dialog.vue';
import type { ServiceApplyDocumentGroup } from '#/views/outpatient/doctor-station/service-apply/service-apply-doc-groups';

import { useInpatientAdviceTab } from '../composables/use-inpatient-advice-tab';
import {
  adviceTherapyTypeClass,
  getSkinTestDisplayTagType,
  resolveAdviceTherapyTypeText,
} from '../services/advice-list';
import { resolveDefaultInpatientAdviceTherapyType, rowLooksDraft } from '../services/advice-row';
import {
  applyOrderTemplateToAdviceDraft,
  OrderTemplateApplyError,
} from '../services/order-template-apply.service';
import AdviceEditCard from './advice-edit-card.vue';
import InpatientAdviceQuickActionBar from './inpatient-advice-quick-action-bar.vue';
import SaveAsOrderTemplateDialog from './save-as-order-template-dialog.vue';
import WesternAdviceCollapsed from './western-advice-collapsed.vue';
import WesternAdviceDetailForm from './western-advice-detail-form.vue';

defineOptions({ name: 'InpatientDoctorStationAdviceWorkspace' });

const props = withDefaults(defineProps<UseInpatientAdviceTabProps>(), {
  encounterId: '',
  forceWriteDisabled: false,
  patientId: '',
  defaultTimeSortOrder: undefined,
  includeTcmPrescriptions: false,
});

const emit = defineEmits<{
  editServiceApply: [group: ServiceApplyDocumentGroup];
  quickAction: [action: InpatientAdviceQuickAction];
}>();

const authStore = useAuthStore();
const orderTemplateSidePanelRef = ref<InstanceType<typeof OrderTemplateSidePanel> | null>(null);
const orderTemplateApplyLoading = ref(false);
const saveAsOrderTemplateDialogVisible = ref(false);

const {
  adviceMutationLoading,
  adviceTableRef,
  adviceWorkspaceTableLoading,
  adviceWriteDisabled,
  buildServiceApplyEditGroup,
  calcDrugLineTotalFee,
  feeNatureOptions,
  editorForm,
  editorInitialGroup,
  editorOpening,
  editorSessionNonce,
  encounterId,
  getAdviceStatusTagType,
  getCellClassName,
  getTableRows,
  handleAdd,
  handleAddEntrust,
  handleCloseEditor,
  handleDelete,
  handleEditorDirtyChange,
  handleMedicationAssignGroup,
  handleMedicationUngroup,
  handleRowDblclick: handleAdviceRowDblclick,
  handleSaveGroup,
  handleSign,
  handleSpanMethod,
  handleWithdraw,
  handleStopLongTerm,
  handleCancelStopLongTerm,
  handleAppendSkinTest,
  handleChangeDose,
  changeDoseMode,
  changeDoseStopTime,
  changeDoseStopTimeEditable,
  canEditOpenedBusinessTime,
  canEditStopBusinessTime,
  beginInlineBusinessTimeEdit,
  bindInlineBusinessTimePickerRef,
  confirmInlineBusinessTime,
  handleBatchChangeOpenedBusinessTime,
  resolveBatchOpenedBusinessTimeTargets,
  handleBatchChangeStopBusinessTime,
  resolveBatchStopBusinessTimeTargets,
  handleInlineBusinessTimeVisible,
  inlineBusinessTimeEdit,
  isInlineBusinessTimeEditing,
  listTherapyFilter,
  loadAdviceInit,
  medicationRateOptions,
  medicationRouteOptions,
  medicationSpeedOptions,
  onAdviceSelectionChange,
  orderSelectExpandNonce,
  orderSelectFocusNonce,
  patientId,
  productColumns,
  queryProductOptions,
  reload,
  setListTherapyFilter,
  validateWesternRow,
  timeSortOrder,
  toggleTimeSortOrder,
} = useInpatientAdviceTab(props);

/** 开立编辑卡默认治疗类型：与列表右上角筛选一致；「全部」回落长期 */
const defaultEditorTherapyType = computed(() =>
  resolveDefaultInpatientAdviceTherapyType(listTherapyFilter.value),
);

/** 另存组套：当前列表非编辑行快照 */
const saveOrderTemplateAdviceRows = computed(() => getTableRows().filter((row) => !row.isEditor));

/** 另存成功后刷新侧栏（维护组套 + 另存模板合并列表） */
function handleSaveAsOrderTemplateSaved() {
  void orderTemplateSidePanelRef.value?.reloadTemplateList();
}

/** 打开另存为组套弹窗 */
function handleOpenSaveAsOrderTemplateDialog() {
  if (adviceWriteDisabled.value) {
    return;
  }
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

/** 双击：申请单草稿弹窗编辑；其余走医嘱编辑卡 */
async function handleRowDblclick(row: AdviceRowItem) {
  if (adviceWriteDisabled.value) return;
  if (row.isEditor) return;
  const applyGroup = buildServiceApplyEditGroup(row);
  if (applyGroup) {
    if (!rowLooksDraft(row)) {
      ElMessage.warning('已签发申请单请先撤回后再修改');
      return;
    }
    emit('editServiceApply', {
      applyFormNo: applyGroup.applyFormNo,
      lines: applyGroup.lines as unknown as OutpatientAdviceRowItem[],
    });
    return;
  }
  await handleAdviceRowDblclick(row);
}

defineExpose({
  loadAdviceInit,
  reload,
  handleAddEntrust,
});
/** 组套套用前选择长临/出院带药类型（默认与列表右上角筛选一致） */
async function promptInpatientTemplateTherapyType(): Promise<InpatientAdviceTherapyType | null> {
  const initial = defaultEditorTherapyType.value;
  let picked: InpatientAdviceTherapyType = initial;
  try {
    await ElMessageBox({
      title: '选择医嘱类型',
      message: h({
        setup() {
          const selected = ref<InpatientAdviceTherapyType>(initial);
          return () =>
            h('div', { class: 'ip-advice-template-therapy-prompt' }, [
              h('p', { style: 'margin: 0 0 12px' }, '组套套用后将以所选类型写入医嘱草稿，请确认。'),
              h(
                ElRadioGroup,
                {
                  modelValue: selected.value,
                  'onUpdate:modelValue': (v: boolean | number | string | undefined) => {
                    selected.value = String(v ?? '') as InpatientAdviceTherapyType;
                    picked = selected.value;
                  },
                },
                () => [
                  h(ElRadio, { label: 'LONG_TERM' }, () => '长期医嘱'),
                  h(ElRadio, { label: 'TEMPORARY' }, () => '临时医嘱'),
                  h(ElRadio, { label: 'DISCHARGE_MEDICATION' }, () => '出院带药'),
                ],
              ),
            ]);
        },
      }),
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      distinguishCancelAndClose: true,
      type: 'info',
    });
    return picked;
  } catch {
    return null;
  }
}

/** 勾选批量改开立时间：弹窗选同一时刻 */
async function promptBatchOpenedBusinessTime(): Promise<null | string> {
  let picked = dayjs().format('YYYY-MM-DD HH:mm');
  try {
    await ElMessageBox({
      title: '批量改开立时间',
      message: h({
        setup() {
          const value = ref(picked);
          return () =>
            h('div', { class: 'ip-advice-batch-opened-time-prompt' }, [
              h(
                'p',
                { style: 'margin: 0 0 12px; color: var(--el-text-color-regular)' },
                '所选医嘱将统一改为以下开立时间（不得早于入院、不得晚于出院）。',
              ),
              h(ElDatePicker, {
                modelValue: value.value,
                'onUpdate:modelValue': (v: unknown) => {
                  const next = String(v ?? '').trim();
                  value.value = next;
                  picked = next;
                },
                type: 'datetime',
                valueFormat: 'YYYY-MM-DD HH:mm',
                format: 'YYYY-MM-DD HH:mm',
                timeFormat: 'HH:mm',
                clearable: false,
                teleported: true,
                style: 'width: 100%',
                popperClass: 'ip-advice-time-picker-popper',
              }),
            ]);
        },
      }),
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      distinguishCancelAndClose: true,
    });
    return picked.trim() || null;
  } catch {
    return null;
  }
}

/** 工具栏：批量改开立时间为同一时刻 */
async function handleToolbarBatchChangeOpenedTime() {
  if (adviceWriteDisabled.value) {
    return;
  }
  if (resolveBatchOpenedBusinessTimeTargets().length === 0) {
    ElMessage.warning('请勾选至少一条可改开立时间的医嘱');
    return;
  }
  const picked = await promptBatchOpenedBusinessTime();
  if (!picked) {
    return;
  }
  await handleBatchChangeOpenedBusinessTime(picked);
}

/** 勾选批量改停嘱时间：弹窗选同一时刻 */
async function promptBatchStopBusinessTime(): Promise<null | string> {
  let picked = dayjs().format('YYYY-MM-DD HH:mm');
  try {
    await ElMessageBox({
      title: '批量改停嘱时间',
      message: h({
        setup() {
          const value = ref(picked);
          return () =>
            h('div', { class: 'ip-advice-batch-stop-time-prompt' }, [
              h(
                'p',
                { style: 'margin: 0 0 12px; color: var(--el-text-color-regular)' },
                '所选已停嘱长期医嘱将统一改为以下停嘱时间（须晚于开立，且不得早于入院、不得晚于出院）。',
              ),
              h(ElDatePicker, {
                modelValue: value.value,
                'onUpdate:modelValue': (v: unknown) => {
                  const next = String(v ?? '').trim();
                  value.value = next;
                  picked = next;
                },
                type: 'datetime',
                valueFormat: 'YYYY-MM-DD HH:mm',
                format: 'YYYY-MM-DD HH:mm',
                timeFormat: 'HH:mm',
                clearable: false,
                teleported: true,
                style: 'width: 100%',
                popperClass: 'ip-advice-time-picker-popper',
              }),
            ]);
        },
      }),
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      distinguishCancelAndClose: true,
    });
    return picked.trim() || null;
  } catch {
    return null;
  }
}

/** 工具栏：批量改停嘱时间为同一时刻 */
async function handleToolbarBatchChangeStopTime() {
  if (adviceWriteDisabled.value) {
    return;
  }
  if (resolveBatchStopBusinessTimeTargets().length === 0) {
    ElMessage.warning('请勾选至少一条可改停嘱时间的医嘱');
    return;
  }
  const picked = await promptBatchStopBusinessTime();
  if (!picked) {
    return;
  }
  await handleBatchChangeStopBusinessTime(picked);
}

/** 侧栏点击组套：确认长临后预览并套用至当前就诊草稿 */
async function handleOrderTemplateSelect(header: null | OrderTemplateHeaderItem) {
  if (!header || orderTemplateApplyLoading.value || adviceWriteDisabled.value) {
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
    const therapyLabelMap: Record<InpatientAdviceTherapyType, string> = {
      LONG_TERM: '长期',
      TEMPORARY: '临时',
      DISCHARGE_MEDICATION: '出院带药',
    };
    const therapyLabel = therapyLabelMap[therapyTypeCode] ?? '临时';
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

/** 底部快捷申请：嘱托在本页直接开立，其余上抛主壳 */
async function handleQuickAction(action: InpatientAdviceQuickAction) {
  if (action === 'entrust') {
    await handleAddEntrust();
    return;
  }
  emit('quickAction', action);
}

/** 临时医嘱：起止列不展示「开/停」两行，仅开立时间+开立人 */
function isTemporaryTherapyRow(row: AdviceRowItem): boolean {
  return (
    String(row.therapyTypeCode ?? '')
      .trim()
      .toUpperCase() === 'TEMPORARY'
  );
}

/** 开立医生展示名（占位「—」不展示） */
function adviceOrderingDoctorLabel(row: AdviceRowItem): string {
  const name = String(row.orderingDoctor ?? '').trim();
  if (!name || name === '—') {
    return '';
  }
  return name;
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
      <div
        class="ip-advice-toolbar mb-2 flex w-full flex-shrink-0 flex-wrap items-center gap-x-3 gap-y-2"
      >
        <div class="ip-advice-toolbar__actions flex min-w-0 flex-1 flex-wrap items-center gap-1">
          <ElButton type="primary" :disabled="adviceWriteDisabled" @click="handleAdd()">
            新增
          </ElButton>
          <ElButton
            type="primary"
            :disabled="adviceWriteDisabled"
            :loading="adviceMutationLoading"
            @click="handleSign()"
          >
            签发
          </ElButton>
          <ElButton
            type="danger"
            plain
            :disabled="adviceWriteDisabled"
            :loading="adviceMutationLoading"
            @click="handleWithdraw()"
          >
            撤回
          </ElButton>
          <ElButton
            type="danger"
            plain
            :disabled="adviceWriteDisabled"
            :loading="adviceMutationLoading"
            @click="handleStopLongTerm()"
          >
            停嘱
          </ElButton>
          <ElButton
            plain
            :disabled="adviceWriteDisabled"
            :loading="adviceMutationLoading"
            @click="handleCancelStopLongTerm()"
          >
            取消停嘱
          </ElButton>
          <ElButton
            type="danger"
            plain
            :disabled="adviceWriteDisabled"
            :loading="adviceMutationLoading"
            @click="handleDelete()"
          >
            删除
          </ElButton>
          <ElButton
            plain
            :disabled="adviceWriteDisabled"
            :loading="adviceMutationLoading"
            @click="handleToolbarBatchChangeOpenedTime()"
          >
            改开立时间
          </ElButton>
          <ElButton
            plain
            :disabled="adviceWriteDisabled"
            :loading="adviceMutationLoading"
            @click="handleToolbarBatchChangeStopTime()"
          >
            改停嘱时间
          </ElButton>
          <ElButton
            plain
            :disabled="adviceWriteDisabled"
            :loading="adviceMutationLoading"
            @click="handleAppendSkinTest()"
          >
            追加皮试
          </ElButton>
          <ElButton
            plain
            :disabled="adviceWriteDisabled"
            :loading="adviceMutationLoading"
            @click="handleChangeDose()"
          >
            改量
          </ElButton>
          <ElButton plain :disabled="adviceWriteDisabled" @click="handleMedicationUngroup">
            拆组
          </ElButton>
          <ElButton plain :disabled="adviceWriteDisabled" @click="handleMedicationAssignGroup">
            并组
          </ElButton>
          <ElButton
            plain
            :disabled="adviceWriteDisabled"
            @click="handleOpenSaveAsOrderTemplateDialog"
          >
            另存为组套
          </ElButton>
        </div>
        <ElRadioGroup
          class="ip-advice-list-therapy-filter shrink-0"
          size="small"
          :model-value="listTherapyFilter"
          @change="(v) => setListTherapyFilter(String(v ?? '') as '' | InpatientAdviceTherapyType)"
        >
          <ElRadioButton value="">全部</ElRadioButton>
          <ElRadioButton value="LONG_TERM">长期</ElRadioButton>
          <ElRadioButton value="TEMPORARY">临时</ElRadioButton>
          <ElRadioButton value="DISCHARGE_MEDICATION">出院带药</ElRadioButton>
        </ElRadioGroup>
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
          class="advice-table h-full"
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
                :key="`${editorSessionNonce}-${defaultEditorTherapyType}`"
                v-loading="editorOpening"
                v-model="editorForm"
                v-model:dose-change-stop-time="changeDoseStopTime"
                :change-dose-mode="changeDoseMode"
                :default-therapy-type="defaultEditorTherapyType"
                :dose-change-stop-time-editable="changeDoseStopTimeEditable"
                :fee-nature-options="feeNatureOptions"
                :encounter-id="encounterId"
                :medication-rate-options="medicationRateOptions"
                :medication-route-options="medicationRouteOptions"
                :medication-speed-options="medicationSpeedOptions"
                :order-select-expand-nonce="orderSelectExpandNonce"
                :order-select-focus-nonce="orderSelectFocusNonce"
                :patient-id="patientId"
                :initial-group="editorInitialGroup"
                :product-columns="productColumns"
                :query-product-options="queryProductOptions"
                :validate-row="validateWesternRow"
                :save-loading="adviceMutationLoading"
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
                    :medication-speed-options="medicationSpeedOptions"
                    :calc-total-fee="calcDrugLineTotalFee"
                    @patch="scope.patch"
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
              <AdviceOrderDisplayCell
                :row-key="row.key"
                :group-items="row.groupItems"
                :content="row.content"
                :dosage-text="row.dosageText"
                :extension="row.extension"
                :tcm-flag="row.tcmFlag === true"
                :tcm-prescription-title="row.tcmPrescriptionTitle"
                :tcm-herb-lines="row.tcmHerbLines"
                :tcm-closing-meta="row.tcmClosingMeta"
                :tcm-decoction-text="row.tcmDecoctionText"
                :apply-form-no="
                  isServiceApplyFormGroupStart(row, $index, getTableRows())
                    ? row.applyFormNo
                    : undefined
                "
              />
            </template>
          </ElTableColumn>
          <ElTableColumn prop="chrgitmLvText" label="项目类别" width="88" align="center">
            <template #default="{ row }">
              <span v-if="!row.isEditor">{{ row.chrgitmLvText || '—' }}</span>
            </template>
          </ElTableColumn>
          <ElTableColumn width="240" align="center">
            <template #header>
              <button
                type="button"
                class="ip-advice-time-sort-header"
                :title="
                  timeSortOrder === 'desc'
                    ? '当前：倒序（后开在上），点击切换为升序'
                    : '当前：升序（先开在上），点击切换为倒序'
                "
                @click.stop="toggleTimeSortOrder"
              >
                <span>起止时间</span>
                <span class="ip-advice-time-sort-header__carets" aria-hidden="true">
                  <ElIcon
                    class="ip-advice-time-sort-header__caret"
                    :class="{ 'is-active': timeSortOrder === 'asc' }"
                  >
                    <CaretTop />
                  </ElIcon>
                  <ElIcon
                    class="ip-advice-time-sort-header__caret"
                    :class="{ 'is-active': timeSortOrder === 'desc' }"
                  >
                    <CaretBottom />
                  </ElIcon>
                </span>
              </button>
            </template>
            <template #default="{ row }">
              <div v-if="!row.isEditor" class="ip-advice-time-range">
                <!-- 临时：单行开立时间 + 开立人，无开/停前缀 -->
                <div
                  v-if="isTemporaryTherapyRow(row)"
                  class="ip-advice-time-range__line"
                  :class="{
                    'is-editable': canEditOpenedBusinessTime(row),
                    'is-editing': isInlineBusinessTimeEditing(row, 'OPENED'),
                  }"
                  :title="canEditOpenedBusinessTime(row) ? '点击修改开立时间' : undefined"
                  @click.stop="
                    canEditOpenedBusinessTime(row) && beginInlineBusinessTimeEdit('OPENED', row)
                  "
                >
                  <ElDatePicker
                    v-if="isInlineBusinessTimeEditing(row, 'OPENED')"
                    :ref="bindInlineBusinessTimePickerRef"
                    v-model="inlineBusinessTimeEdit.value"
                    type="datetime"
                    size="small"
                    value-format="YYYY-MM-DD HH:mm"
                    format="YYYY-MM-DD HH:mm"
                    time-format="HH:mm"
                    :clearable="false"
                    :editable="false"
                    teleported
                    placement="bottom-start"
                    class="ip-advice-time-inline-picker"
                    popper-class="ip-advice-time-picker-popper"
                    @click.stop
                    @change="confirmInlineBusinessTime"
                    @visible-change="handleInlineBusinessTimeVisible"
                  />
                  <template v-else>
                    <span class="ip-advice-time-range__time">{{ row.startTime || '—' }}</span>
                  </template>
                  <span v-if="adviceOrderingDoctorLabel(row)" class="ip-advice-time-range__doctor">
                    {{ adviceOrderingDoctorLabel(row) }}
                  </span>
                </div>
                <!-- 长期/出院带药：开一行、停一行；无停嘱时间显示「-」 -->
                <template v-else>
                  <div
                    class="ip-advice-time-range__line"
                    :class="{
                      'is-editable': canEditOpenedBusinessTime(row),
                      'is-editing': isInlineBusinessTimeEditing(row, 'OPENED'),
                    }"
                    :title="canEditOpenedBusinessTime(row) ? '点击修改开立时间' : undefined"
                    @click.stop="
                      canEditOpenedBusinessTime(row) && beginInlineBusinessTimeEdit('OPENED', row)
                    "
                  >
                    <span class="ip-advice-time-range__tag">开</span>
                    <ElDatePicker
                      v-if="isInlineBusinessTimeEditing(row, 'OPENED')"
                      :ref="bindInlineBusinessTimePickerRef"
                      v-model="inlineBusinessTimeEdit.value"
                      type="datetime"
                      size="small"
                      value-format="YYYY-MM-DD HH:mm"
                      format="YYYY-MM-DD HH:mm"
                      time-format="HH:mm"
                      :clearable="false"
                      :editable="false"
                      teleported
                      placement="bottom-start"
                      class="ip-advice-time-inline-picker"
                      popper-class="ip-advice-time-picker-popper"
                      @click.stop
                      @change="confirmInlineBusinessTime"
                      @visible-change="handleInlineBusinessTimeVisible"
                    />
                    <template v-else>
                      <span class="ip-advice-time-range__time">{{ row.startTime || '—' }}</span>
                    </template>
                    <span
                      v-if="adviceOrderingDoctorLabel(row)"
                      class="ip-advice-time-range__doctor"
                    >
                      {{ adviceOrderingDoctorLabel(row) }}
                    </span>
                  </div>
                  <div
                    class="ip-advice-time-range__line ip-advice-time-range__end"
                    :class="{
                      'is-editable': canEditStopBusinessTime(row),
                      'is-editing': isInlineBusinessTimeEditing(row, 'STOP'),
                    }"
                    :title="canEditStopBusinessTime(row) ? '点击修改停嘱时间' : undefined"
                    @click.stop="
                      canEditStopBusinessTime(row) && beginInlineBusinessTimeEdit('STOP', row)
                    "
                  >
                    <span class="ip-advice-time-range__tag">停</span>
                    <ElDatePicker
                      v-if="isInlineBusinessTimeEditing(row, 'STOP')"
                      :ref="bindInlineBusinessTimePickerRef"
                      v-model="inlineBusinessTimeEdit.value"
                      type="datetime"
                      size="small"
                      value-format="YYYY-MM-DD HH:mm"
                      format="YYYY-MM-DD HH:mm"
                      time-format="HH:mm"
                      :clearable="false"
                      :editable="false"
                      teleported
                      placement="bottom-start"
                      class="ip-advice-time-inline-picker"
                      popper-class="ip-advice-time-picker-popper"
                      @click.stop
                      @change="confirmInlineBusinessTime"
                      @visible-change="handleInlineBusinessTimeVisible"
                    />
                    <template v-else>
                      <span class="ip-advice-time-range__time">{{ row.endTime || '-' }}</span>
                    </template>
                    <span
                      v-if="row.endTime && row.stopperName"
                      class="ip-advice-time-range__doctor"
                    >
                      {{ row.stopperName }}
                    </span>
                  </div>
                </template>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="lineTotalAmount" label="总金额" width="100" align="center" />
          <ElTableColumn prop="skinTestText" label="皮试" width="96" align="center">
            <template #default="{ row }">
              <div class="ip-advice-skin-test-cell">
                <ElTag
                  v-if="getSkinTestDisplayTagType(row)"
                  :type="getSkinTestDisplayTagType(row)!"
                  size="small"
                  effect="light"
                >
                  {{ row.skinTestText }}
                </ElTag>
                <span v-else>{{ row.skinTestText }}</span>
                <ElTag
                  v-if="row.needAppendSkinTestFlag"
                  type="warning"
                  size="small"
                  effect="plain"
                  title="发放批号与上次皮试批号不一致，建议追加皮试检查"
                >
                  换批
                </ElTag>
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>

      <InpatientAdviceQuickActionBar
        class="flex-shrink-0"
        :disabled="adviceWriteDisabled"
        @action="handleQuickAction"
      />
    </div>
  </div>

  <SaveAsOrderTemplateDialog
    v-model:open="saveAsOrderTemplateDialogVisible"
    :patient-id="patientId"
    :encounter-id="encounterId"
    :advice-rows="saveOrderTemplateAdviceRows"
    @saved="handleSaveAsOrderTemplateSaved"
  />

  <AdviceDraftBindDialog />
</template>

<style scoped>
.ip-advice-workspace-root {
  display: grid;
  /* 组套侧栏自带宽度并可收起，列宽随内容 */
  grid-template-columns: auto minmax(0, 1fr);
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

.ip-advice-toolbar {
  padding: 6px 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}

.ip-advice-toolbar__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.ip-advice-list-therapy-filter :deep(.el-radio-button__inner) {
  padding: 5px 10px;
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

.ip-advice-skin-test-cell {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
  justify-content: center;
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

.ip-advice-time-range {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  max-width: 100%;
  line-height: 1.3;
  text-align: left;
  white-space: nowrap;
}

.ip-advice-time-sort-header {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
  user-select: none;
}

.ip-advice-time-sort-header:hover {
  color: var(--el-color-primary);
}

.ip-advice-time-sort-header__carets {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  width: 14px;
  height: 16px;
  line-height: 1;
}

.ip-advice-time-sort-header__caret {
  width: 12px;
  height: 8px;
  color: var(--el-text-color-placeholder);
}

.ip-advice-time-sort-header__caret.is-active {
  color: var(--el-color-primary);
}

.ip-advice-time-sort-header__caret :deep(svg) {
  width: 12px;
  height: 12px;
}

.ip-advice-time-range__line {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 22px;
  padding: 1px 4px;
  border-radius: 3px;
  transition: background-color 0.15s ease;
}

.ip-advice-time-range__line.is-editable:not(.is-editing) {
  cursor: pointer;
}

.ip-advice-time-range__line.is-editable:not(.is-editing):hover {
  background: var(--el-fill-color-light);
}

.ip-advice-time-range__line.is-editable:not(.is-editing):hover .ip-advice-time-range__time {
  color: var(--el-color-primary);
}

.ip-advice-time-range__line.is-editing {
  cursor: default;
  background: transparent;
  padding-left: 2px;
  padding-right: 2px;
}

.ip-advice-time-range__tag {
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  border-radius: 2px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  font-size: 11px;
  font-weight: 600;
  line-height: 14px;
  text-align: center;
}

.ip-advice-time-range__end .ip-advice-time-range__tag {
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-regular);
}

.ip-advice-time-range__time {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
  letter-spacing: 0;
}

.ip-advice-time-range__end .ip-advice-time-range__time {
  color: var(--el-text-color-regular);
}

.ip-advice-time-range__doctor {
  max-width: 4.5em;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-color-primary);
  opacity: 0.85;
}

/* 就地选择器：贴近纯文本，去掉粗边框与时钟图标 */
.ip-advice-time-inline-picker {
  width: 148px;
}

.ip-advice-time-inline-picker :deep(.el-input__wrapper) {
  padding: 0 4px;
  background: transparent;
  box-shadow: none !important;
  border-radius: 0;
  border-bottom: 1px solid var(--el-color-primary);
}

.ip-advice-time-inline-picker :deep(.el-input__wrapper.is-focus),
.ip-advice-time-inline-picker :deep(.el-input__wrapper:hover) {
  box-shadow: none !important;
  border-bottom-color: var(--el-color-primary);
}

.ip-advice-time-inline-picker :deep(.el-input__prefix),
.ip-advice-time-inline-picker :deep(.el-input__suffix) {
  display: none;
}

.ip-advice-time-inline-picker :deep(.el-input__inner) {
  height: 20px;
  padding: 0;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  color: var(--el-color-primary);
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

<!-- 面板 teleported 到 body，需非 scoped 覆盖 -->
<style>
.ip-advice-time-picker-popper.el-picker__popper {
  z-index: 3200 !important;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  box-shadow: 0 6px 18px rgb(15 23 42 / 10%);
}

.ip-advice-time-picker-popper .el-picker-panel {
  border: none;
  box-shadow: none;
}

/* 顶部「日期 + 时间」输入条：时间输入点击后展开时分面板，勿隐藏 */
.ip-advice-time-picker-popper .el-date-picker__time-header {
  padding: 8px 12px 4px;
}

.ip-advice-time-picker-popper .el-date-picker__editor-wrap .el-input__wrapper {
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

.ip-advice-time-picker-popper .el-picker-panel__footer {
  padding: 6px 10px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.ip-advice-time-picker-popper .el-picker-panel__link-btn {
  font-size: 12px;
}
</style>
