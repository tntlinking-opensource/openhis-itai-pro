<script setup lang="ts">
import { DOCUMENT_DETAIL_DOMAIN_DEPARTMENT } from '#/api/report/document-detail';

import DocumentDetailReportPage from '../document-detail-report.vue';

defineOptions({ name: 'DepartmentDocumentDetailReportPage' });
</script>

<template>
  <DocumentDetailReportPage
    :domain-scope="DOCUMENT_DETAIL_DOMAIN_DEPARTMENT"
    page-title="科室单据详情"
  />
</template>
