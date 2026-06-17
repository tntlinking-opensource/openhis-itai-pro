<script lang="ts" setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { useImpersonation } from '#/composables/use-impersonation';
import { resetAndRefreshAccessRoutes } from '#/router/access-init';

const router = useRouter();
const { impersonationMeta, isImpersonating, stopImpersonating } = useImpersonation();

const bannerText = computed(() => {
  if (!impersonationMeta.value) {
    return '';
  }
  const { impersonatorUserName, impersonatedUserName } = impersonationMeta.value;
  return `代理登录：${impersonatedUserName}（操作者：${impersonatorUserName}）`;
});

async function handleStop() {
  try {
    await ElMessageBox.confirm('确定要退出代理登录并返回您的账号吗？', '退出代理', {
      confirmButtonText: '退出代理',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const restored = await stopImpersonating();
    if (restored) {
      ElMessage.success('已退出代理登录');
      await router.replace('/');
      await resetAndRefreshAccessRoutes(router);
    } else {
      ElMessage.warning('未找到原登录会话，请重新登录');
      await router.replace('/auth/login');
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('退出代理登录失败，请稍后重试');
    }
  }
}
</script>

<template>
  <div
    v-if="isImpersonating"
    class="impersonation-banner flex flex-wrap items-center justify-between gap-2 px-4 py-2 text-sm text-white"
  >
    <span>{{ bannerText }}</span>
    <ElButton size="small" type="warning" plain @click="handleStop">退出代理</ElButton>
  </div>
</template>

<style scoped>
.impersonation-banner {
  background: linear-gradient(90deg, #e65100 0%, #ff9800 100%);
}
</style>
