<script setup lang="ts">
import type { VbenFormSchema } from '#/adapter/form';

import { computed, nextTick, onMounted, ref, watch } from 'vue';

import { ProfileBaseSetting } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { getUserRolesApi } from '#/api/user/user';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'ProfileBaseSettingPage' });

const userStore = useUserStore();
const profileBaseSettingRef = ref<{
  getFormApi: () => { setValues: (v: Record<string, unknown>) => Promise<void> | void };
}>();

/** 角色展示文案（displayName，非内部 name/code） */
const roleDisplayText = ref('');

const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      fieldName: 'realName',
      component: 'Input',
      label: '姓名',
      componentProps: {
        disabled: true,
        placeholder: '暂无姓名',
      },
    },
    {
      fieldName: 'username',
      component: 'Input',
      label: '用户名',
      componentProps: {
        disabled: true,
        placeholder: '暂无用户名',
      },
    },
    {
      fieldName: 'roles',
      component: 'Input',
      label: '角色',
      componentProps: {
        disabled: true,
        placeholder: '暂无角色',
      },
    },
    {
      fieldName: 'email',
      component: 'Input',
      label: '邮箱',
      componentProps: {
        disabled: true,
        placeholder: '暂无邮箱',
      },
    },
    {
      fieldName: 'phoneNumber',
      component: 'Input',
      label: '手机号',
      componentProps: {
        disabled: true,
        placeholder: '暂无手机号',
      },
    },
  ];
});

/** 拉取当前用户角色的中文 displayName */
async function loadRoleDisplayNames() {
  const rawId = userStore.userInfo?.id;
  const userId = preserveApiIdForRequest(rawId) ?? (rawId == null ? '' : String(rawId));
  if (!userId) {
    roleDisplayText.value = '';
    return;
  }
  try {
    const roles = await getUserRolesApi({ value: userId });
    // 优先展示名，缺失时再退回内部 name
    const names = (Array.isArray(roles) ? roles : [])
      .map((role) => String(role.displayName || role.name || '').trim())
      .filter((name) => name !== '');
    roleDisplayText.value = names.join('、');
  } catch (error) {
    console.error('加载个人角色失败:', error);
    roleDisplayText.value = '';
  }
}

/** 把 userStore 中的登录用户写入基本设置表单 */
async function fillFormFromUserStore() {
  const formApi = profileBaseSettingRef.value?.getFormApi?.();
  const info = userStore.userInfo;
  if (!formApi || !info) {
    return;
  }
  await formApi.setValues({
    realName: info.realName || info.name || '',
    username: info.username || info.userName || '',
    roles: roleDisplayText.value,
    email: info.email || '',
    phoneNumber: info.phoneNumber || '',
  });
}

onMounted(async () => {
  // 对历史缓存再走一遍 setUserInfo，补齐 username/realName
  if (userStore.userInfo) {
    userStore.setUserInfo(userStore.userInfo);
  }
  await loadRoleDisplayNames();
  await nextTick();
  await fillFormFromUserStore();
});

watch(
  () => userStore.userInfo?.id,
  async () => {
    await loadRoleDisplayNames();
    await nextTick();
    await fillFormFromUserStore();
  },
);

watch(
  () => [userStore.userInfo, roleDisplayText.value] as const,
  async () => {
    await nextTick();
    await fillFormFromUserStore();
  },
  { deep: true },
);
</script>
<template>
  <ProfileBaseSetting
    ref="profileBaseSettingRef"
    class="w-1/2"
    :form-schema="formSchema"
    :show-submit="false"
  />
</template>
