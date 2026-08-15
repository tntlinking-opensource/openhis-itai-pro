<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { useAuthStore } from '#/store';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  EMR_TEMPLATE_SCOPE_LEVEL,
  type EmrTemplateScopeLevel,
} from '#/views/basic/emr-template/emr-template-scope.constants';

defineOptions({ name: 'SaveAsEmrTemplateDialog' });

const emit = defineEmits<{
  confirm: [payload: { name: string; organizationId?: string; scopeLevel: EmrTemplateScopeLevel }];
}>();

const open = defineModel<boolean>('open', { default: false });

const authStore = useAuthStore();

const templateName = ref('');
const scopeLevel = ref<EmrTemplateScopeLevel>(EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL);
const confirmLoading = ref(false);

/** 当前登录科室（科室模板强制绑定，与后端会话 org 一致） */
const loginOrganizationId = computed(
  () => preserveApiIdForRequest(authStore.getSelectedOrganization()) ?? '',
);

const showDepartmentHint = computed(() => scopeLevel.value === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT);

function resetFormOnOpen() {
  templateName.value = '';
  scopeLevel.value = EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL;
}

function handleConfirm() {
  const name = templateName.value.trim();
  if (!name) {
    ElMessage.warning('请输入模板名称');
    return;
  }
  if (scopeLevel.value === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT) {
    const orgId = loginOrganizationId.value.trim();
    if (!orgId) {
      ElMessage.warning('请先选择登录科室后再保存科室模板');
      return;
    }
    emit('confirm', {
      name,
      scopeLevel: EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT,
      organizationId: orgId,
    });
    return;
  }
  emit('confirm', {
    name,
    scopeLevel: EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL,
  });
}

watch(open, (visible) => {
  if (visible) {
    confirmLoading.value = false;
    resetFormOnOpen();
  }
});

defineExpose({
  setConfirmLoading: (loading: boolean) => {
    confirmLoading.value = loading;
  },
  close: () => {
    open.value = false;
  },
});
</script>

<template>
  <ElDialog
    v-model="open"
    title="存为模版"
    width="480px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <ElForm label-width="88px" class="save-as-emr-template-form">
      <ElFormItem label="模板名称" required>
        <ElInput
          v-model="templateName"
          maxlength="64"
          show-word-limit
          placeholder="请输入模板名称"
          clearable
          @keyup.enter="handleConfirm"
        />
      </ElFormItem>
      <ElFormItem label="归属范围" required>
        <ElRadioGroup v-model="scopeLevel">
          <ElRadio :value="EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL">个人模板</ElRadio>
          <ElRadio :value="EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT">科室模板</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem v-if="showDepartmentHint" label="适用科室">
        <span class="text-sm" style="color: var(--el-text-color-regular)">
          绑定当前登录科室（同科医生可见；请确认右上角登录科室正确）
        </span>
      </ElFormItem>
    </ElForm>

    <template #footer>
      <ElButton @click="open = false">取消</ElButton>
      <ElButton type="primary" :loading="confirmLoading" @click="handleConfirm">保存</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.save-as-emr-template-form :deep(.el-form-item) {
  margin-bottom: 14px;
}
</style>
