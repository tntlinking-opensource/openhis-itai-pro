<script setup lang="ts">
import { Page } from '@zxplat/common-ui';

import NurseWorkloadPanel from './nurse-workload-panel.vue';

defineOptions({ name: 'NurseWorkloadReportPage' });
</script>

<template>
  <Page auto-content-height>
    <NurseWorkloadPanel />
  </Page>
</template>
