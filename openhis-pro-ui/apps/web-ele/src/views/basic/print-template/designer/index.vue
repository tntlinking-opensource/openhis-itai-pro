<script lang="ts" setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { Page } from '@zxplat/common-ui';

import { HiprintDesigner } from '#/hiprint';

defineOptions({ name: 'BasicPrintTemplateDesigner' });

const route = useRoute();

/** 从列表「设计」入口带入的业务键 */
const busNo = computed(() => String(route.query.busNo ?? '').trim());
</script>

<template>
  <Page :auto-content-height="true" content-class="!p-0 overflow-hidden">
    <HiprintDesigner :bus-no="busNo || undefined" />
  </Page>
</template>
