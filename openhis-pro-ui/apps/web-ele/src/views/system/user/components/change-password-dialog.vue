<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';
import type { ChangePasswordParams, UserInfo } from '#/api/user/user';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { ElMessage } from 'element-plus';

import { useVbenForm, z } from '#/adapter/form';
import { changePasswordApi } from '#/api/user/user';

defineOptions({ name: 'ChangePasswordDialog' });

interface Props {
  modelValue: boolean;
  user?: null | UserInfo;
}

const props = withDefaults(defineProps<Props>(), {
  user: null,
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'success'): void;
}>();

const originalData = ref<Record<string, unknown>>({});

/** 提交新密码；须带用户 ID，且与确认密码一致 */
async function handleSubmit() {
  const { valid } = await formApi.validate();
  if (!valid) {
    return;
  }

  const userId = props.user?.id;
  if (!userId) {
    ElMessage.error('用户信息缺失，无法修改密码');
    return;
  }

  modalApi.lock(true);
  try {
    const formValues = await formApi.getValues();
    // 去除首尾空白，避免看似成功却无法用「界面可见密码」登录
    const newPassword = String(formValues.newPassword ?? '').trim();
    if (!newPassword) {
      ElMessage.error('请输入新密码');
      return;
    }
    const changePasswordData: ChangePasswordParams = {
      id: userId,
      newPassword,
    };
    await changePasswordApi(changePasswordData);
    ElMessage.success('密码修改成功');
    emit('success');
    modalApi.close();
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
    modalApi.unlock();
  }
}

/** 打开弹窗时清空表单，避免沿用上一次输入 */
async function loadFormData() {
  if (props.user) {
    originalData.value = { ...props.user };
  } else {
    originalData.value = {};
  }
  await formApi.resetForm();
}

const [Modal, modalApi] = useVbenModal({
  onOpenChange: (isOpen: boolean) => {
    emit('update:modelValue', isOpen);
  },
  onOpened: async () => {
    await nextTick();
    await loadFormData();
  },
  onConfirm: handleSubmit,
  showCancelButton: true,
  showConfirmButton: true,
  confirmText: '确定',
  cancelText: '取消',
});

const formConfig = reactive({
  commonConfig: {
    componentProps: {
      style: { width: '240px' },
    },
  },
  layout: 'horizontal' as const,
  schema: computed((): VbenFormSchema[] => [
    {
      component: 'Input',
      fieldName: 'newPassword',
      label: '新密码',
      componentProps: {
        type: 'password',
        showPassword: true,
        placeholder: '请输入新密码（至少6位）',
        autocomplete: 'new-password',
      },
      rules: z.string().min(6, { message: '密码长度不能少于6位' }),
    },
    {
      component: 'Input',
      fieldName: 'confirmPassword',
      label: '确认密码',
      componentProps: {
        type: 'password',
        showPassword: true,
        placeholder: '请再次输入新密码',
        autocomplete: 'new-password',
      },
      dependencies: {
        rules(values: Record<string, unknown>) {
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
  ]),
  showDefaultActions: false,
});

const [Form, formApi] = useVbenForm(formConfig);

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      modalApi.open();
    } else {
      modalApi.close();
    }
  },
  { immediate: true },
);
</script>

<template>
  <Modal
    class="w-[500px]"
    title="修改密码"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
  >
    <Form />
  </Modal>
</template>
