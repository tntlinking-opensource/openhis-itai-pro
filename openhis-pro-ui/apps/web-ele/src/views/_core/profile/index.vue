<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Profile } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { getApplicationConfigurationApi } from '#/api/core/auth';

import ProfileBase from './base-setting.vue';
import ProfileClientSetting from './client-setting.vue';
import ProfileDepartmentSetting from './department-setting.vue';
import ProfileNotificationSetting from './notification-setting.vue';
import ProfilePasswordSetting from './password-setting.vue';
import ProfilePrintSetting from './print-setting.vue';
import ProfileSecuritySetting from './security-setting.vue';

defineOptions({ name: 'ProfileIndex' });

const userStore = useUserStore();
const route = useRoute();

const tabsValue = ref<string>('basic');

const tabs = ref([
  {
    label: '基本设置',
    value: 'basic',
  },
  {
    label: '安全设置',
    value: 'security',
  },
  {
    label: '修改密码',
    value: 'password',
  },
  {
    label: '新消息提醒',
    value: 'notice',
  },
  {
    label: '科室设置',
    value: 'department',
  },
  {
    label: '打印设置',
    value: 'print',
  },
  {
    label: '客户端',
    value: 'client',
  },
]);

/** 支持 ?tab=print 直达打印设置 */
function syncTabFromRoute(): void {
  const tab = typeof route.query.tab === 'string' ? route.query.tab.trim() : '';
  if (tab && tabs.value.some((item) => item.value === tab)) {
    tabsValue.value = tab;
  }
}

/** 用 applicationConfiguration.currentUser 补齐展示字段与角色 */
async function syncProfileFromConfiguration() {
  try {
    const configuration = await getApplicationConfigurationApi();
    const currentUser = configuration?.currentUser;
    if (!currentUser) {
      return;
    }
    const prev = userStore.userInfo;
    userStore.setUserInfo({
      ...(prev ?? {}),
      id: currentUser.id ?? prev?.id,
      userName: currentUser.userName || prev?.userName || '',
      name: currentUser.name ?? prev?.name ?? null,
      email: currentUser.email || prev?.email || '',
      phoneNumber: currentUser.phoneNumber ?? prev?.phoneNumber ?? null,
      practitionerId: currentUser.practitionerId ?? prev?.practitionerId,
      organizationId: currentUser.organizationId ?? prev?.organizationId,
      roles: currentUser.roles?.length ? currentUser.roles : prev?.roles,
    } as any);
    if (currentUser.roles?.length) {
      userStore.setUserRoles(currentUser.roles);
    }
  } catch (error) {
    console.error('Failed to sync profile from configuration:', error);
  }
}

onMounted(() => {
  syncTabFromRoute();
  // 进入个人中心时刷新一次，保证角色/邮箱等回显完整
  void syncProfileFromConfiguration();
});

watch(
  () => route.query.tab,
  () => {
    syncTabFromRoute();
  },
);
</script>
<template>
  <Profile
    v-model:model-value="tabsValue"
    title="个人中心"
    :user-info="userStore.userInfo"
    :tabs="tabs"
  >
    <template #content>
      <ProfileBase v-if="tabsValue === 'basic'" />
      <ProfileSecuritySetting v-if="tabsValue === 'security'" />
      <ProfilePasswordSetting v-if="tabsValue === 'password'" />
      <ProfileNotificationSetting v-if="tabsValue === 'notice'" />
      <ProfileDepartmentSetting v-if="tabsValue === 'department'" />
      <ProfilePrintSetting v-if="tabsValue === 'print'" />
      <ProfileClientSetting v-if="tabsValue === 'client'" />
    </template>
  </Profile>
</template>
