<script setup lang="ts">
import { computed, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import { getOrganizationApiByLoginUser } from '#/api/core/auth';
import { useAuthStore } from '#/store';
import { unwrapApiList } from '#/utils/api/unwrap-api-list';
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
const organizationId = ref('');
const organizationOptions = ref<Array<{ label: string; value: string }>>([]);
const orgLoading = ref(false);
const confirmLoading = ref(false);

const showOrganizationField = computed(
  () => scopeLevel.value === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT,
);

async function loadOrganizationOptions() {
  orgLoading.value = true;
  try {
    const response = await getOrganizationApiByLoginUser();
    const list = unwrapApiList<{ displayName?: string; id?: number | string; name?: string }>(
      response,
    );
    organizationOptions.value = list
      .map((item) => {
        const value = preserveApiIdForRequest(item.id);
        if (!value) return null;
        const label = (item.name ?? item.displayName ?? value).trim() || value;
        return { label, value };
      })
      .filter((x): x is { label: string; value: string } => x != null);

    const selected =
      preserveApiIdForRequest(authStore.getSelectedOrganization()) ??
      organizationOptions.value[0]?.value ??
      '';
    if (selected && organizationOptions.value.some((o) => o.value === selected)) {
      organizationId.value = selected;
    } else {
      organizationId.value = organizationOptions.value[0]?.value ?? '';
    }
  } catch {
    organizationOptions.value = [];
    organizationId.value = '';
  } finally {
    orgLoading.value = false;
  }
}

function resetFormOnOpen() {
  templateName.value = '';
  scopeLevel.value = EMR_TEMPLATE_SCOPE_LEVEL.PERSONAL;
  organizationId.value = '';
  void loadOrganizationOptions();
}

function handleConfirm() {
  const name = templateName.value.trim();
  if (!name) {
    ElMessage.warning('请输入模板名称');
    return;
  }
  if (scopeLevel.value === EMR_TEMPLATE_SCOPE_LEVEL.DEPARTMENT) {
    const orgId = organizationId.value.trim();
    if (!orgId) {
      ElMessage.warning('请选择适用科室');
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
      <ElFormItem v-if="showOrganizationField" label="适用科室" required>
        <ElSelect
          v-model="organizationId"
          class="w-full"
          filterable
          clearable
          :loading="orgLoading"
          placeholder="请选择科室"
        >
          <ElOption
            v-for="opt in organizationOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </ElSelect>
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
