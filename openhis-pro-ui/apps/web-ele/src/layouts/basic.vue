<script lang="ts" setup>
import type { NotificationItem } from '@zxplat/layouts';

import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationLoginExpiredModal } from '@zxplat/common-ui';
import { useWatermark } from '@zxplat/hooks';
import { BasicLayout, LockScreen, Notification, UserDropdown } from '@zxplat/layouts';
import { preferences } from '@zxplat/preferences';
import { useAccessStore, useUserStore } from '@zxplat/stores';

import SelectDepartment from '#/components/select-department.vue';
import ImpersonationBanner from '#/components/impersonation-banner.vue';
import {
  isEinvoiceCompensationNotificationId,
  useEinvoiceCompensationNotification,
} from '#/composables/use-einvoice-compensation-notification';
import { useLayoutCompactViewport } from '#/composables/use-layout-compact-viewport';
import { $t } from '#/locales';
import { useAuthStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';

const userNotifications = ref<NotificationItem[]>([]);

const { dismissToday: dismissEinvoiceCompensationToday, einvoiceNotifications } =
  useEinvoiceCompensationNotification();

const notifications = computed(() => [...einvoiceNotifications.value, ...userNotifications.value]);

const router = useRouter();
const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const { destroyWatermark, updateWatermark } = useWatermark();
const showDot = computed(() => notifications.value.some((item) => !item.isRead));

useLayoutCompactViewport();

const menus = computed(() => [
  {
    handler: () => {
      router.push({ name: 'Profile' });
    },
    icon: 'lucide:user',
    text: $t('page.auth.profile'),
  },
  {
    handler: () => {
      router.push({ name: 'Profile', query: { tab: 'print' } });
    },
    icon: 'lucide:printer',
    text: '打印设置',
  },
  // {
  //   handler: () => {
  //     openWindow(VBEN_DOC_URL, {
  //       target: '_blank',
  //     });
  //   },
  //   icon: BookOpenText,
  //   text: $t('ui.widgets.document'),
  // },
  // {
  //   handler: () => {
  //     openWindow(VBEN_GITHUB_URL, {
  //       target: '_blank',
  //     });
  //   },
  //   icon: SvgGithubIcon,
  //   text: 'GitHub',
  // },
  // {
  //   handler: () => {
  //     openWindow(`${VBEN_GITHUB_URL}/issues`, {
  //       target: '_blank',
  //     });
  //   },
  //   icon: CircleHelp,
  //   text: $t('ui.widgets.qa'),
  // },
]);

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
});

function handleLogout() {
  void authStore.logout(false);
}

function handleNoticeClear() {
  dismissEinvoiceCompensationToday();
  userNotifications.value = [];
}

function markRead(id: number | string) {
  if (isEinvoiceCompensationNotificationId(id)) {
    dismissEinvoiceCompensationToday();
    return;
  }
  const item = userNotifications.value.find((item) => item.id === id);
  if (item) {
    item.isRead = true;
  }
}

function remove(id: number | string) {
  if (isEinvoiceCompensationNotificationId(id)) {
    dismissEinvoiceCompensationToday();
    return;
  }
  userNotifications.value = userNotifications.value.filter((item) => item.id !== id);
}

function handleMakeAll() {
  if (einvoiceNotifications.value.length > 0) {
    dismissEinvoiceCompensationToday();
  }
  userNotifications.value.forEach((item) => (item.isRead = true));
}
watch(
  () => ({
    enable: preferences.app.watermark,
    content: preferences.app.watermarkContent,
  }),
  async ({ enable, content }) => {
    if (enable) {
      await updateWatermark({
        content:
          content ||
          `${userStore.userInfo?.username || userStore.userInfo?.userName || ''} - ${
            userStore.userInfo?.realName || userStore.userInfo?.name || ''
          }`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <ImpersonationBanner />
    <BasicLayout class="min-h-0 flex-1" @clear-preferences-and-logout="handleLogout">
      <template #user-dropdown>
        <UserDropdown
          :avatar
          :menus
          :text="
            userStore.userInfo?.realName ||
            userStore.userInfo?.name ||
            userStore.userInfo?.username ||
            userStore.userInfo?.userName
          "
          @logout="handleLogout"
        />
      </template>
      <template #notification>
        <Notification
          :dot="showDot"
          :notifications="notifications"
          @clear="handleNoticeClear"
          @read="(item) => item.id && markRead(item.id)"
          @remove="(item) => item.id && remove(item.id)"
          @make-all="handleMakeAll"
        />
      </template>
      <template #extra>
        <AuthenticationLoginExpiredModal v-model:open="accessStore.loginExpired" :avatar>
          <LoginForm />
        </AuthenticationLoginExpiredModal>
      </template>
      <template #lock-screen>
        <LockScreen :avatar @to-login="handleLogout" />
      </template>
      <template #department-selector>
        <SelectDepartment class="mr-1 sm:mr-4" />
      </template>
    </BasicLayout>
  </div>
</template>
