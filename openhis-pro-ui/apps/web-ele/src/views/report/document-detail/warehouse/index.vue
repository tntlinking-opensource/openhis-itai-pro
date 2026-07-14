<script setup lang="ts">
import { DOCUMENT_DETAIL_DOMAIN_WAREHOUSE } from '#/api/report/document-detail';

import DocumentDetailReportPage from '../document-detail-report.vue';

defineOptions({ name: 'WarehouseDocumentDetailReportPage' });
</script>

<template>
  <DocumentDetailReportPage
    :domain-scope="DOCUMENT_DETAIL_DOMAIN_WAREHOUSE"
    page-title="药库单据详情"
  />
</template>
