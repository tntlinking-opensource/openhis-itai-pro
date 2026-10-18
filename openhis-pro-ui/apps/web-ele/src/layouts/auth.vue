<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { AuthPageLayout } from '@zxplat/layouts/es/authentication';
import { preferences } from '@zxplat/preferences/es/preferences-manager';

import { fetchHealthInfo } from '#/api/core/health';
import { AUTH_COPYRIGHT_TEXT } from '#/layouts/auth-copyright';

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);
const logoDark = computed(() => preferences.logo.sourceDark);

/** 业务产品版本（根目录 VERSION），非 Vben package.json 的 5.x */
const frontendVersion = String(import.meta.env.VITE_PRODUCT_VERSION ?? '').trim();
const backendVersion = ref('');

const versionLine = computed(() => {
  const fe = frontendVersion || '—';
  const be = backendVersion.value || '—';
  return `前端 ${fe} · 后端 ${be}`;
});

onMounted(async () => {
  // 后端版本经 /api/health，服务端只读 VERSION 文件（非 POM）
  const health = await fetchHealthInfo();
  const ver = health?.version?.trim();
  if (ver) {
    backendVersion.value = ver;
  }
});
</script>

<template>
  <AuthPageLayout
    :app-name="appName"
    :logo="logo"
    :logo-dark="logoDark"
    page-description="Hospital Information System"
    page-title="医院信息管理系统"
    :toolbar="false"
  >
    <template #copyright>
      <div class="auth-footer">
        <span>{{ AUTH_COPYRIGHT_TEXT }}</span>
        <span class="auth-footer__version">{{ versionLine }}</span>
      </div>
    </template>
  </AuthPageLayout>
</template>

<style scoped>
.auth-footer {
  display: flex;
  flex-flow: row nowrap;
  gap: 12px;
  align-items: center;
  justify-content: center;
  width: 100%;
  line-height: 1.4;
  white-space: nowrap;
}

.auth-footer__version {
  flex-shrink: 0;
  opacity: 0.75;
  font-size: 12px;
}
</style>
