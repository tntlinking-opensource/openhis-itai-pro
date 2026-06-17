<script setup lang="ts">
import type { ReportTemplateListItemDto } from '#/api/report/report-design';

import { ref, watch } from 'vue';

import ReportPdfPreviewDialog from '#/components/report/ReportPdfPreviewDialog.vue';
import { previewBase64ToBlob, reportDesignPreviewApi } from '#/api/report/report-design';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ReportTemplatePreviewDialog' });

const visible = ref(false);
const activeRow = ref<ReportTemplateListItemDto | null>(null);
const pdfBlob = ref<Blob | null>(null);

const { run: runPreview, loading: previewLoading } = useRequest(reportDesignPreviewApi, {
  manual: true,
  onSuccess: (result) => {
    pdfBlob.value = previewBase64ToBlob(result);
  },
});

watch(visible, (open) => {
  if (!open) {
    pdfBlob.value = null;
    activeRow.value = null;
  }
});

async function open(row: ReportTemplateListItemDto) {
  const code = row.code?.trim();
  if (!code) {
    return;
  }
  activeRow.value = row;
  visible.value = true;
  pdfBlob.value = null;
  await runPreview({
    templateCode: code,
    useSampleData: true,
  });
}

async function handleRefresh() {
  const code = activeRow.value?.code?.trim();
  if (!code) {
    return;
  }
  await runPreview({
    templateCode: code,
    useSampleData: true,
  });
}

defineExpose({ open });
</script>

<template>
  <ReportPdfPreviewDialog
    v-model="visible"
    :pdf-blob="pdfBlob"
    :loading="previewLoading"
    :title="`试打预览 — ${activeRow?.name ?? activeRow?.code ?? ''}`"
    @refresh="handleRefresh"
  />
</template>
