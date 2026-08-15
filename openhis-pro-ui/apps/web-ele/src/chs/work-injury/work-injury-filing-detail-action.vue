<script setup lang="ts">
import { computed, ref } from 'vue';

import { ElButton, ElMessage } from 'element-plus';

import WorkInjury2301ApprovalDialog from './work-injury-2301-approval-dialog.vue';
import WorkInjuryFilingDetailDialog from './work-injury-filing-detail-dialog.vue';
import { httpWorkInjuryLoadEncounterContext } from './work-injury.http';

defineOptions({ name: 'WorkInjuryFilingDetailAction' });

const props = defineProps<{
  encounterId?: string | number;
  contractId?: string | number;
  injureAffirmIdHint?: string;
  /** 标题旁紧凑场景使用 link。 */
  link?: boolean;
}>();

const emit = defineEmits<{
  completed: [];
}>();

const filingDialogRef = ref<InstanceType<typeof WorkInjuryFilingDetailDialog> | null>(null);
const approvalDialogRef = ref<InstanceType<typeof WorkInjury2301ApprovalDialog> | null>(null);

/** ensureFilingReady 等待中的 resolve */
let pendingEnsureResolve: ((ok: boolean) => void) | null = null;

const anchorReady = computed(
  () =>
    props.encounterId != null &&
    props.encounterId !== '' &&
    props.contractId != null &&
    props.contractId !== '',
);

/** 无就诊：仅可查看预查结果；有就诊：可引导备案上传 */
const actionLabel = computed(() => (anchorReady.value ? '一键备案' : '查看备案'));

function resolveEnsure(ok: boolean) {
  if (!pendingEnsureResolve) {
    return;
  }
  const resolve = pendingEnsureResolve;
  pendingEnsureResolve = null;
  resolve(ok);
}

function onApprovalCompleted() {
  resolveEnsure(true);
  emit('completed');
}

function onApprovalCancelled() {
  resolveEnsure(false);
}

function onFilingCompleted() {
  resolveEnsure(true);
  emit('completed');
}

/**
 * 主按钮：无就诊 → 仅打开详情查看；有就诊 → 打开详情并引导绑定/备案。
 */
function onActionClick() {
  if (!anchorReady.value) {
    filingDialogRef.value?.openDialog({ viewOnly: true });
    return;
  }
  openApprovalGuide();
}

function openDialog(options?: { autoGuideApproval?: boolean; viewOnly?: boolean }) {
  filingDialogRef.value?.openDialog(options);
}

/** 无有效审批时引导：打开详情 → 自动绑定已有审批，否则再 2301。 */
function openApprovalGuide() {
  openDialog({ autoGuideApproval: true });
}

/** 有就诊时直接打开 2301（详情内「一键备案」或 ensure 兜底）。 */
function openAddFiling(options?: { quickConfirm?: boolean }) {
  if (!anchorReady.value) {
    ElMessage.warning('请先点「确认挂号」完成预结算，系统会引导备案（未备案不能完成挂号）');
    filingDialogRef.value?.openDialog({ viewOnly: true });
    return;
  }
  window.setTimeout(() => {
    approvalDialogRef.value?.openDialog({ quickConfirm: options?.quickConfirm });
  }, 0);
}

/**
 * 挂号门禁：确保当前就诊已绑定有效审批。
 * 始终先走详情引导（自动绑定已有 9102），无有效行再 2301；取消则 false。
 */
async function ensureFilingReady(): Promise<boolean> {
  if (!anchorReady.value) {
    ElMessage.warning('缺少就诊信息，无法校验工伤备案');
    return false;
  }
  try {
    const ctx = await httpWorkInjuryLoadEncounterContext(props.encounterId!);
    if (ctx?.qualificationValid) {
      return true;
    }
  } catch {
    // 继续引导备案
  }

  return new Promise<boolean>((resolve) => {
    pendingEnsureResolve = resolve;
    ElMessage.warning('请先完成工伤备案以继续挂号');
    // 先绑定已有审批，确认无行后再一键 2301（见详情 maybeGuide）
    openApprovalGuide();
  });
}

defineExpose({ openDialog, openApprovalGuide, openAddFiling, ensureFilingReady });
</script>

<template>
  <ElButton type="warning" :link="link" @click="onActionClick()">{{ actionLabel }}</ElButton>
  <WorkInjuryFilingDetailDialog
    ref="filingDialogRef"
    :encounter-id="props.encounterId"
    :contract-id="props.contractId"
    :injure-affirm-id-hint="injureAffirmIdHint"
    @completed="onFilingCompleted"
    @cancelled="onApprovalCancelled"
  />
  <WorkInjury2301ApprovalDialog
    v-if="anchorReady"
    ref="approvalDialogRef"
    :encounter-id="encounterId!"
    :contract-id="contractId"
    :injure-affirm-id-hint="injureAffirmIdHint"
    @completed="onApprovalCompleted"
    @cancelled="onApprovalCancelled"
  />
</template>
