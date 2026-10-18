<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';

import { computed, ref } from 'vue';

import { ProfilePasswordSetting, z } from '@zxplat/common-ui';

import { ElMessage } from 'element-plus';

import { changeOwnPasswordApi } from '#/api/core/auth';
import { useAuthStore } from '#/store';

defineOptions({ name: 'ProfilePasswordSettingPage' });

const profilePasswordSettingRef = ref();
const submitting = ref(false);
const authStore = useAuthStore();

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'oldPassword',
      label: '旧密码',
      component: 'VbenInputPassword',
      componentProps: {
        placeholder: '请输入旧密码',
      },
      rules: z.string({ required_error: '请输入旧密码' }).min(1, { message: '请输入旧密码' }),
    },
    {
      fieldName: 'newPassword',
      label: '新密码',
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: '请输入新密码（至少6位）',
      },
      rules: z.string({ required_error: '请输入新密码' }).min(6, { message: '密码长度至少6位' }),
    },
    {
      fieldName: 'confirmPassword',
      label: '确认密码',
      component: 'VbenInputPassword',
      componentProps: {
        passwordStrength: true,
        placeholder: '请再次输入新密码',
      },
      dependencies: {
        rules(values) {
          const { newPassword } = values;
          return z
            .string({ required_error: '请再次输入新密码' })
            .min(1, { message: '请再次输入新密码' })
            .refine((value) => value === newPassword, {
              message: '两次输入的密码不一致',
            });
        },
        triggerFields: ['newPassword'],
      },
    },
  ];
});

/** 提交自助改密；成功后退出登录，用新密码重新登录 */
async function handleSubmit(values: Record<string, unknown>) {
  if (submitting.value) {
    return;
  }

  const oldPassword = String(values.oldPassword ?? '');
  // 去除首尾空白，避免看似成功却无法用「界面可见密码」登录
  const newPassword = String(values.newPassword ?? '').trim();
  if (!oldPassword) {
    ElMessage.error('请输入旧密码');
    return;
  }
  if (!newPassword) {
    ElMessage.error('请输入新密码');
    return;
  }
  if (newPassword.length < 6) {
    ElMessage.error('密码长度至少6位');
    return;
  }
  if (oldPassword === newPassword) {
    ElMessage.error('新密码不能与旧密码相同');
    return;
  }

  submitting.value = true;
  try {
    await changeOwnPasswordApi({ oldPassword, newPassword });
    ElMessage.success('密码修改成功，请重新登录');
    await authStore.logout(true);
  } catch (error: unknown) {
    const err = error as {
      message?: string;
      msg?: string;
      response?: { data?: { message?: string; msg?: string } };
    };
    const errorMessage =
      err?.response?.data?.msg ||
      err?.response?.data?.message ||
      err?.message ||
      err?.msg ||
      '密码修改失败，请稍后重试';
    ElMessage.error(errorMessage);
  } finally {
    submitting.value = false;
  }
}
</script>
<template>
  <ProfilePasswordSetting
    ref="profilePasswordSettingRef"
    class="w-1/3"
    :form-schema="formSchema"
    @submit="handleSubmit"
  />
</template>
