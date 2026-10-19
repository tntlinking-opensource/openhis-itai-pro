<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';

import { AuthPageLayout } from '@zxplat/layouts/es/authentication';
import { preferences } from '@zxplat/preferences/es/preferences-manager';

import { fetchHealthInfo } from '#/api/core/health';
import { AUTH_COPYRIGHT_TEXT } from '#/layouts/auth-copyright';

/** 《天天开源软件（社区版）许可协议》打开协议页面，避免走文件下载地址。 */
const LICENSE_LABEL = '《天天开源软件（社区版）许可协议》';
const LICENSE_HREF =
  'https://gitee.com/tntlinking-opensource/openhis/blob/V2.2/天天开源软件（社区版）许可协议.pdf';

const copyrightParts = computed(() => {
  const text = AUTH_COPYRIGHT_TEXT;
  const index = text.indexOf(LICENSE_LABEL);
  if (index < 0) {
    return null;
  }
  return {
    after: text.slice(index + LICENSE_LABEL.length),
    before: text.slice(0, index),
  };
});

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
        <span v-if="copyrightParts">
          {{ copyrightParts.before }}
          <a
            class="auth-footer__license"
            :href="LICENSE_HREF"
            rel="noopener noreferrer"
            target="_blank"
          >{{ LICENSE_LABEL }}</a>
          {{ copyrightParts.after }}
        </span>
        <span v-else>{{ AUTH_COPYRIGHT_TEXT }}</span>
        <span class="auth-footer__version">{{ versionLine }}</span>
      </div>
    </template>
  </AuthPageLayout>
</template>

<style scoped>
.auth-footer {
  display: flex;
  flex-flow: column wrap;
  gap: 4px;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 0 16px;
  line-height: 1.4;
  text-align: center;
  white-space: normal;
}

.auth-footer__license {
  color: inherit;
  text-decoration: underline;
}

.auth-footer__version {
  flex-shrink: 0;
  opacity: 0.75;
  font-size: 12px;
}
</style>
