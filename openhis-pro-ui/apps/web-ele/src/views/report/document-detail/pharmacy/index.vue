<script setup lang="ts">
import { DOCUMENT_DETAIL_DOMAIN_PHARMACY } from '#/api/report/document-detail';

import DocumentDetailReportPage from '../document-detail-report.vue';

defineOptions({ name: 'PharmacyDocumentDetailReportPage' });
</script>

<template>
  <DocumentDetailReportPage
    :domain-scope="DOCUMENT_DETAIL_DOMAIN_PHARMACY"
    page-title="药房单据详情"
  />
</template>
