<script setup lang="ts">
import { Page } from '@vben/common-ui';

import PractitionerWorkloadPanel from './practitioner-workload-panel.vue';

defineOptions({ name: 'PractitionerWorkloadReportPage' });
</script>

<template>
  <Page auto-content-height>
    <PractitionerWorkloadPanel />
  </Page>
</template>
