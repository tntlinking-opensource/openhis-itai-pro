<script setup lang="ts">
import type { ServiceApplyKind } from './service-apply.types';

import type { ServiceApplyPrintMeta } from '#/utils/print/print-service-apply';

import type { ServiceApplyPatientSummary } from './service-apply.types';

import { ref, watch } from 'vue';

import { ElButton, ElEmpty } from 'element-plus';

import { useEnsureClinicalReception } from '#/components/doctor-station';

import ServiceApplyDialog from './service-apply-dialog.vue';
import ServiceApplyFormDocument from './service-apply-form-document.vue';
import AdviceDraftBindDialog from '../advice/components/advice-draft-bind-dialog.vue';
import { useServiceApplyTab } from './composables/use-service-apply-tab';
import type { ServiceApplyDocumentGroup } from './service-apply-doc-groups';
import { inferServiceApplyKindFromAdviceRow } from './service-apply-doc-groups';
import DetailAuditRemindDialog from '#/chs/components/detail-audit-remind-dialog.vue';
import type { DetailAuditRemindDialogExpose } from '#/chs/composables/use-detail-audit-gate';

defineOptions({ name: 'ServiceApplyWorkspace' });

const props = withDefaults(
  defineProps<{
    encounterId?: string;
    patientId?: string;
    serviceApplyPatient?: ServiceApplyPatientSummary;
    serviceApplyPrintMeta?: ServiceApplyPrintMeta;
  }>(),
  { encounterId: '', patientId: '' },
);

const emit = defineEmits<{
  'open-apply': [kind: ServiceApplyKind];
}>();

const { ensureReceptionBeforeClinicalWork } = useEnsureClinicalReception();

const detailAuditDialogRef = ref<DetailAuditRemindDialogExpose | null>(null);

const {
  documentGroups,
  getAdviceChargeStatusTagType,
  getAdviceStatusTagType,
  handleCopy,
  handleDelete,
  handlePrint,
  handleSign,
  handleWithdraw,
  isDocumentGroupSelected,
  mutationLoading,
  printLoading,
  reload,
  tableLoading,
  toggleDocumentGroupSelection,
} = useServiceApplyTab(props, { detailAuditDialogRef });

const editDialogVisible = ref(false);
const editTargetGroup = ref<ServiceApplyDocumentGroup | null>(null);
const editApplyKind = ref<ServiceApplyKind>('lab');

/** 打开检验/检查/治疗申请弹窗 */
function handleOpenApply(kind: ServiceApplyKind) {
  emit('open-apply', kind);
}

/** 打开修改申请单弹窗（仅草稿；已签发须先撤回。打开前自动接诊） */
async function handleEditDocument(group: ServiceApplyDocumentGroup) {
  const receptionOk = await ensureReceptionBeforeClinicalWork();
  if (!receptionOk) return;
  const head = group.lines[0];
  const kind = head ? (inferServiceApplyKindFromAdviceRow(head) ?? 'lab') : 'lab';
  editApplyKind.value = kind;
  // 先写入回显组，再打开，避免 watch 打开瞬间拿不到 editDocumentGroup
  editTargetGroup.value = group;
  editDialogVisible.value = true;
}

/** 编辑弹窗关闭后清空目标，避免下次打开残留回显 */
watch(editDialogVisible, (open) => {
  if (!open) {
    editTargetGroup.value = null;
  }
});

/** 编辑暂存成功后刷新列表 */
async function handleEditSaved() {
  await reload();
}

/** 编辑签发成功后关闭弹窗并刷新列表 */
async function handleEditSigned() {
  editDialogVisible.value = false;
  await reload();
}

defineExpose({ reload });
</script>

<template>
  <div class="service-apply-workspace flex h-full min-h-0 flex-col">
    <div class="workspace-toolbar">
      <div class="toolbar-actions">
        <ElButton type="primary" @click="handleOpenApply('lab')">检验申请</ElButton>
        <ElButton type="primary" @click="handleOpenApply('exam')">检查申请</ElButton>
        <ElButton type="primary" @click="handleOpenApply('treatment')">治疗申请</ElButton>
        <span class="toolbar-divider" />
        <ElButton type="primary" :loading="mutationLoading" @click="handleSign">签发</ElButton>
        <ElButton type="warning" plain :loading="mutationLoading" @click="handleWithdraw">
          撤回
        </ElButton>
        <ElButton type="danger" plain :loading="mutationLoading" @click="handleDelete">
          删除
        </ElButton>
        <ElButton type="success" plain :loading="mutationLoading" @click="handleCopy">
          复制
        </ElButton>
        <ElButton plain :loading="printLoading" @click="handlePrint">打印申请单</ElButton>
      </div>
    </div>

    <div v-loading="tableLoading" class="workspace-body min-h-0 flex-1 overflow-y-auto">
      <ElEmpty
        v-if="!tableLoading && documentGroups.length === 0"
        description="暂无检验/检查/治疗申请单"
        class="workspace-empty"
      />
      <div v-else class="doc-grid">
        <ServiceApplyFormDocument
          v-for="group in documentGroups"
          :key="group.applyFormNo"
          :group="group"
          :selected="isDocumentGroupSelected(group)"
          :get-status-tag-type="getAdviceStatusTagType"
          :get-charge-status-tag-type="getAdviceChargeStatusTagType"
          @toggle-select="toggleDocumentGroupSelection(group)"
          @edit="handleEditDocument(group)"
        />
      </div>
    </div>

    <ServiceApplyDialog
      v-model="editDialogVisible"
      shell="dialog"
      :apply-kind="editApplyKind"
      :edit-document-group="editTargetGroup"
      :encounter-id="encounterId"
      :patient-id="patientId"
      :patient="serviceApplyPatient"
      :print-meta="serviceApplyPrintMeta"
      @saved="handleEditSaved"
      @signed="handleEditSigned"
    />

    <DetailAuditRemindDialog ref="detailAuditDialogRef" warn-type="1" />

    <AdviceDraftBindDialog />
  </div>
</template>

<style scoped>
.service-apply-workspace {
  background: var(--el-bg-color);
}

.workspace-toolbar {
  flex-shrink: 0;
  padding: 10px 12px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  margin: 0 2px;
  background: var(--el-border-color);
}

.workspace-body {
  padding: 16px;
  background: var(--el-fill-color-blank);
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 640px), 1fr));
  gap: 16px;
  align-items: stretch;
  max-width: 1480px;
  margin: 0 auto;
}

.workspace-empty {
  padding: 48px 0;
}
</style>
