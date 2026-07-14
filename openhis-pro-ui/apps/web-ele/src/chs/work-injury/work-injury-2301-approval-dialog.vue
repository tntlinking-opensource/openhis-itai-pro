<script setup lang="ts">
import type { WorkInjury2301FormValues } from './work-injury-2301-form';

import { ref, watch } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
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
}>();

const visible = defineModel<boolean>({ default: false });

const form = ref<WorkInjury2301FormValues>(createDefaultWorkInjury2301Form());
const previewJson = ref('');
const previewLoading = ref(false);
const submitLoading = ref(false);

watch(visible, (open) => {
  if (!open) {
    return;
  }
  form.value = createDefaultWorkInjury2301Form({
    injureAffirmId: props.injureAffirmIdHint?.trim() ?? '',
  });
  previewJson.value = '';
});

async function previewPayload() {
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

async function submitApproval() {
  if (props.contractId == null || props.contractId === '') {
    ElMessage.warning('缺少工伤契约');
    return;
  }
  submitLoading.value = true;
  try {
    const cap = await httpWorkInjuryModuleCapability();
    if (!cap?.enabled) {
      throw new Error('工伤模块未启用');
    }
    if (!(await isWorkInjuryPluginOnline())) {
      throw new Error('请启动 OpenHIS.Win 并启用工伤模块');
    }
    await runWorkInjuryRegister2301({
      encounterId: props.encounterId,
      contractId: props.contractId,
      form: form.value,
    });
    visible.value = false;
    emit('completed');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '工伤审批上传失败');
  } finally {
    submitLoading.value = false;
  }
}

function openDialog() {
  visible.value = true;
}

defineExpose({ openDialog });
</script>

<template>
  <AppDialog
    v-model="visible"
    title="工伤审批上传（2301）"
    width="720px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <ElForm label-width="120px" class="pr-2">
      <ElFormItem label="审批类型" required>
        <ElInput v-model="form.approveType" placeholder="院点多为 12" />
      </ElFormItem>
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
          placeholder="空则后端 +30 天"
          class="w-full"
        />
      </ElFormItem>
      <ElFormItem label="终止原因" required>
        <ElInput v-model="form.terminReason" />
      </ElFormItem>
      <ElFormItem label="认定 ID" required>
        <ElInput v-model="form.injureAffirmId" placeholder="1101 diagnosis.injureAffirmID" />
      </ElFormItem>
      <ElFormItem label="转诊机构编码">
        <ElInput v-model="form.orgCode" />
      </ElFormItem>
      <ElFormItem label="转诊机构名称">
        <ElInput v-model="form.orgName" />
      </ElFormItem>
    </ElForm>

    <div v-if="previewJson" class="mt-2">
      <div class="mb-1 text-sm text-[var(--el-text-color-secondary)]">Win input 预览</div>
      <ElScrollbar max-height="220px">
        <pre class="rounded bg-[var(--el-fill-color-light)] p-3 text-xs">{{ previewJson }}</pre>
      </ElScrollbar>
    </div>

    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton :loading="previewLoading" @click="previewPayload">预览 2301</ElButton>
      <ElButton type="primary" :loading="submitLoading" @click="submitApproval">
        上传并继续 9102
      </ElButton>
    </template>
  </AppDialog>
</template>
