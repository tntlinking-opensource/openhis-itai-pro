<script setup lang="ts">
import { Page } from '@vben/common-ui';

import NurseWorkloadPanel from './nurse-workload-panel.vue';

defineOptions({ name: 'NurseWorkloadReportPage' });
</script>

<template>
  <Page auto-content-height>
    <NurseWorkloadPanel />
  </Page>
</template>
