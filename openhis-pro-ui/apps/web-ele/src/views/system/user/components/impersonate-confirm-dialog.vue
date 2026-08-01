<script lang="ts" setup>
import type { UserInfo } from '#/api/user/user';

import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { ElButton, ElDialog, ElForm, ElFormItem, ElInput, ElMessage } from 'element-plus';

import { useImpersonation } from '#/composables/use-impersonation';
import { resetAndRefreshAccessRoutes } from '#/router/access-init';

const props = defineProps<{
  modelValue: boolean;
  user: UserInfo | null;
}>();

const emit = defineEmits<{
  success: [];
  'update:modelValue': [value: boolean];
}>();

const router = useRouter();
const { impersonateUser } = useImpersonation();
const loading = ref(false);
const reason = ref('');

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
});

const targetLabel = computed(() => {
  if (!props.user) {
    return '';
  }
  const name = props.user.name || props.user.userName;
  return `${name}（${props.user.userName}）`;
});

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      reason.value = '';
    }
  },
);

async function handleConfirm() {
  if (!props.user) {
    return;
  }
  loading.value = true;
  try {
    await impersonateUser(props.user.id, { reason: reason.value.trim() || undefined });
    ElMessage.success(`已代理登录为 ${props.user.userName}`);
    visible.value = false;
    emit('success');
    // 先按被代理用户重载菜单，再进首页；选科未就绪时回登录页完成范围选择
    try {
      await resetAndRefreshAccessRoutes(router);
      await router.replace('/');
    } catch (scopeError: any) {
      if (scopeError?.name === 'LoginScopeNotReadyError') {
        await router.replace('/auth/login');
        return;
      }
      throw scopeError;
    }
  } catch (error: any) {
    const message =
      error?.response?.data?.msg ||
      error?.response?.data?.message ||
      error?.message ||
      '代理登录失败，请稍后重试';
    ElMessage.error(message);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <ElDialog v-model="visible" title="代理登录确认" width="480px" destroy-on-close>
    <div class="space-y-3 text-sm leading-6 text-[var(--el-text-color-regular)]">
      <p>您将以以下用户身份操作系统，无需知道其密码：</p>
      <p class="font-medium text-[var(--el-text-color-primary)]">{{ targetLabel }}</p>
      <ul class="list-disc space-y-1 pl-5">
        <li>代理会话约 30 分钟后过期</li>
        <li>所有操作将记录审计日志</li>
        <li>可随时通过顶部横幅退出代理</li>
      </ul>
    </div>
    <ElForm class="mt-4" label-width="72px">
      <ElFormItem label="原因">
        <ElInput
          v-model="reason"
          maxlength="200"
          placeholder="可选，便于审计追溯"
          show-word-limit
          type="textarea"
          :rows="2"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="visible = false">取消</ElButton>
      <ElButton :loading="loading" type="warning" @click="handleConfirm">确认代理登录</ElButton>
    </template>
  </ElDialog>
</template>
