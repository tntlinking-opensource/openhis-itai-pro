<script lang="ts" setup>
import { computed } from 'vue';

import { AuthPageLayout } from '@vben/layouts/es/authentication';
import { preferences } from '@vben/preferences/es/preferences-manager';

const appName = computed(() => preferences.app.name);
const logo = computed(() => preferences.logo.source);
const logoDark = computed(() => preferences.logo.sourceDark);
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
      <span>
        Copyright © 2025湖北天天数链技术有限公司 本系统软件源代码许可来源于《
        <a
          class="hover:text-primary-hover"
          href="https://open.tntlinking.com/communityTreaty"
          rel="noopener noreferrer"
          target="_blank"
        >
          天天开源软件（社区版）许可协议
        </a>
        》
      </span>
    </template>
  </AuthPageLayout>
</template>
