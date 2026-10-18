<script setup lang="ts">
import type { WorkInjury2301FormValues } from './work-injury-2301-form';

import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElScrollbar,
} from 'element-plus';

import AppDialog from '#/components/app-dialog.vue';

import {
  createDefaultWorkInjury2301Form,
  toBuildWorkInjury2301PayloadRequest,
} from './work-injury-2301-form';
import { httpWorkInjuryBuild2301Payload } from './work-injury.http';
import { runWorkInjuryRegister2301 } from './use-work-injury-register';
import { httpWorkInjuryModuleCapability } from './work-injury.http';
import { isWorkInjuryPluginOnline } from './work-injury-plugin';

defineOptions({ name: 'WorkInjury2301ApprovalDialog' });

const props = defineProps<{
  encounterId: string | number;
  contractId?: string | number;
  injureAffirmIdHint?: string;
}>();

const emit = defineEmits<{
  completed: [];
  cancelled: [];
}>();

const visible = defineModel<boolean>({ default: false });

const form = ref<WorkInjury2301FormValues>(createDefaultWorkInjury2301Form());
const previewJson = ref('');
const previewLoading = ref(false);
const submitLoading = ref(false);
const showAdvanced = ref(false);
/** 打开后若认定 ID 齐全则自动弹出一键确认 */
const pendingQuickConfirm = ref(false);
/** 区分成功关闭与取消关闭，避免 completed 后再误发 cancelled */
const closedAfterSuccess = ref(false);

const canQuickSubmit = computed(() => Boolean(form.value.injureAffirmId.trim()));

const summaryText = computed(() => {
  const f = form.value;
  const end = f.endDate.trim() || '开始日+30天';
  return `审批类型 ${f.approveType || '12'} · ${f.startDate || '今天'} ~ ${end} · 认定 ${f.injureAffirmId || '—'}`;
});

watch(visible, async (open) => {
  if (!open) {
    return;
  }
  closedAfterSuccess.value = false;
  form.value = createDefaultWorkInjury2301Form({
    injureAffirmId: props.injureAffirmIdHint?.trim() ?? '',
  });
  previewJson.value = '';
  showAdvanced.value = false;
  if (pendingQuickConfirm.value && canQuickSubmit.value) {
    pendingQuickConfirm.value = false;
    await tryQuickSubmit();
  } else {
    pendingQuickConfirm.value = false;
  }
});

function onDialogClosed() {
  if (closedAfterSuccess.value) {
    closedAfterSuccess.value = false;
    return;
  }
  emit('cancelled');
}

function assertFormReady(): boolean {
  if (!form.value.approveType.trim()) {
    ElMessage.warning('请填写审批类型');
    return false;
  }
  if (!form.value.startDate.trim()) {
    ElMessage.warning('请选择开始日期');
    return false;
  }
  if (!form.value.terminReason.trim()) {
    ElMessage.warning('请填写终止原因');
    return false;
  }
  if (!form.value.injureAffirmId.trim()) {
    ElMessage.warning('缺少认定 ID：请先读卡/查询备案后再添加');
    showAdvanced.value = true;
    return false;
  }
  return true;
}

async function previewPayload() {
  if (!assertFormReady()) {
    return;
  }
  previewLoading.value = true;
  try {
    const body = toBuildWorkInjury2301PayloadRequest(
      props.encounterId,
      props.contractId,
      form.value,
    );
    const res = await httpWorkInjuryBuild2301Payload(body);
    previewJson.value = JSON.stringify(res.input ?? {}, null, 2);
    ElMessage.success('2301 预览已生成');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '2301 预览失败');
  } finally {
    previewLoading.value = false;
  }
}

async function doSubmit(): Promise<boolean> {
  if (props.contractId == null || props.contractId === '') {
    ElMessage.warning('缺少工伤契约');
    return false;
  }
  if (!assertFormReady()) {
    return false;
  }
  submitLoading.value = true;
  try {
    const cap = await httpWorkInjuryModuleCapability();
    if (!cap?.enabled) {
      throw new Error('工伤模块未启用');
    }
    if (!(await isWorkInjuryPluginOnline())) {
      throw new Error('请启动 Zxplat.Win 并启用工伤模块');
    }
    await runWorkInjuryRegister2301({
      encounterId: props.encounterId,
      contractId: props.contractId,
      form: form.value,
    });
    closedAfterSuccess.value = true;
    visible.value = false;
    emit('completed');
    return true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工伤审批上传失败');
    return false;
  } finally {
    submitLoading.value = false;
  }
}

async function tryQuickSubmit() {
  if (!canQuickSubmit.value) {
    ElMessage.warning('缺少认定 ID，请核对后提交');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将按默认门诊工伤审批上传（2301→9102）。\n${summaryText.value}`,
      '一键备案',
      {
        type: 'warning',
        confirmButtonText: '确认上传',
        cancelButtonText: '返回修改',
      },
    );
  } catch {
    return;
  }
  await doSubmit();
}

async function submitApproval() {
  await doSubmit();
}

function handleCancel() {
  visible.value = false;
}

function openDialog(options?: { quickConfirm?: boolean }) {
  pendingQuickConfirm.value = Boolean(options?.quickConfirm);
  visible.value = true;
}

defineExpose({ openDialog, tryQuickSubmit });
</script>

<template>
  <AppDialog
    v-model="visible"
    title="工伤审批上传（2301）"
    width="560px"
    destroy-on-close
    :close-on-click-modal="false"
    @closed="onDialogClosed"
  >
    <p class="mb-3 text-sm text-[var(--el-text-color-secondary)]">
      默认按门诊工伤备案；有认定 ID 时可一键上传，成功后自动查询审批（9102）。
    </p>

    <ElForm label-width="100px" class="pr-2">
      <ElFormItem label="开始日期" required>
        <ElDatePicker
          v-model="form.startDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择开始日期"
          class="w-full"
        />
      </ElFormItem>
      <ElFormItem label="结束日期">
        <ElDatePicker
          v-model="form.endDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="空则自动 +30 天"
          class="w-full"
        />
      </ElFormItem>
      <ElFormItem label="认定 ID" required>
        <ElInput v-model="form.injureAffirmId" placeholder="由读卡/查询备案自动带出" clearable />
      </ElFormItem>

      <div class="mb-2">
        <ElButton link type="primary" @click="showAdvanced = !showAdvanced">
          {{ showAdvanced ? '收起高级选项' : '高级选项' }}
        </ElButton>
      </div>

      <template v-if="showAdvanced">
        <ElFormItem label="审批类型" required>
          <ElInput v-model="form.approveType" placeholder="院点多为 12" />
        </ElFormItem>
        <ElFormItem label="终止原因" required>
          <ElInput v-model="form.terminReason" />
        </ElFormItem>
        <ElFormItem label="转诊机构编码">
          <ElInput v-model="form.orgCode" placeholder="转诊类才需填写" />
        </ElFormItem>
        <ElFormItem label="转诊机构名称">
          <ElInput v-model="form.orgName" placeholder="转诊类才需填写" />
        </ElFormItem>
      </template>
    </ElForm>

    <div v-if="previewJson" class="mt-2">
      <div class="mb-1 text-sm text-[var(--el-text-color-secondary)]">Win input 预览</div>
      <ElScrollbar max-height="180px">
        <pre class="rounded bg-[var(--el-fill-color-light)] p-3 text-xs">{{ previewJson }}</pre>
      </ElScrollbar>
    </div>

    <template #footer>
      <ElButton :disabled="submitLoading" @click="handleCancel">取消</ElButton>
      <ElButton v-if="showAdvanced" :loading="previewLoading" @click="previewPayload">
        预览
      </ElButton>
      <ElButton
        type="warning"
        :loading="submitLoading"
        :disabled="!canQuickSubmit"
        @click="tryQuickSubmit"
      >
        一键备案
      </ElButton>
      <ElButton type="primary" :loading="submitLoading" @click="submitApproval">
        上传并继续 9102
      </ElButton>
    </template>
  </AppDialog>
</template>
