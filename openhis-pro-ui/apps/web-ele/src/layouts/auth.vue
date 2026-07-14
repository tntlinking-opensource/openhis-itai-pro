<script lang="ts" setup>
import { computed } from 'vue';

import { AuthPageLayout } from '@vben/layouts/es/authentication';
import { preferences } from '@vben/preferences/es/preferences-manager';

import { AUTH_COPYRIGHT_TEXT } from '#/layouts/auth-copyright';

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
      <span>{{ AUTH_COPYRIGHT_TEXT }}</span>
    </template>
  </AuthPageLayout>
</template>
