<script setup lang="ts">
import type { VolumeItemDto } from '#/api/mrms/types';

import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@zxplat/common-ui';
import { formatDateTime } from '@zxplat/utils';

import {
  ElButton,
  ElCheckbox,
  ElEmpty,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getVolumeCatalogApi, previewVolumePdfApi } from '#/api/mrms';
import { pdfBase64ToObjectUrl, revokePdfObjectUrl } from '#/emrEditor/utils/emr-pdf-blob';
import { useRequest } from '#/hooks/use-request';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

defineOptions({ name: 'MrmsCaseRecordVolume' });

const route = useRoute();
const router = useRouter();

const caseRecordId = computed(() =>
  preserveApiIdForRequest(String(route.params.caseRecordId ?? '')),
);
const medcasno = ref('');
const catalogRows = ref<VolumeItemDto[]>([]);
const includeHomepage = ref(true);
const selectedRefIds = ref<string[]>([]);
const pdfPreviewUrl = ref('');
const pdfPageCount = ref<number | undefined>();

const { run: runCatalog, loading: catalogLoading } = useRequest(getVolumeCatalogApi, {
  manual: true,
  onSuccess: (data) => {
    medcasno.value = data.medcasno ?? '';
    const rows: VolumeItemDto[] = [];
    if (data.homepageItem) {
      rows.push(data.homepageItem);
    }
    if (data.documents?.length) {
      rows.push(...data.documents);
    }
    catalogRows.value = rows;
    selectedRefIds.value = rows
      .filter((r) => r.printable !== false)
      .map((r) => String(r.refId ?? ''));
  },
});

const { run: runPreview, loading: previewLoading } = useRequest(previewVolumePdfApi, {
  manual: true,
  onSuccess: (data) => {
    revokePdfObjectUrl(pdfPreviewUrl.value);
    if (!data.pdfBase64) {
      ElMessage.warning('未生成 PDF 内容');
      return;
    }
    pdfPreviewUrl.value = pdfBase64ToObjectUrl(data.pdfBase64);
    pdfPageCount.value = data.pageCount;
  },
});

function fmtTime(v?: string): string {
  if (!v) {
    return '';
  }
  return formatDateTime(v);
}

function itemTypeLabel(row: VolumeItemDto): string {
  if (row.itemType === 'homepage') {
    return '病案首页';
  }
  return row.documentTypeName || row.documentTypeCode || 'EMR 文书';
}

function loadCatalog() {
  const id = caseRecordId.value;
  if (!id) {
    return;
  }
  runCatalog({ caseRecordId: id });
}

async function onPreview() {
  const id = caseRecordId.value;
  if (!id) {
    return;
  }
  const docIds = selectedRefIds.value
    .filter((refId) => {
      const row = catalogRows.value.find((r) => String(r.refId) === refId);
      return row?.itemType === 'emr_document';
    })
    .map((refId) => refId);

  await runPreview({
    caseRecordId: id,
    includeHomepage: includeHomepage.value,
    documentIds: docIds.length > 0 ? docIds : undefined,
  });
}

function goBack() {
  router.back();
}

function onSelectionChange(rows: VolumeItemDto[]) {
  selectedRefIds.value = rows.map((r) => String(r.refId ?? '')).filter(Boolean);
}

onMounted(() => {
  loadCatalog();
});

onBeforeUnmount(() => {
  revokePdfObjectUrl(pdfPreviewUrl.value);
});
</script>

<template>
  <Page auto-content-height>
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <ElButton @click="goBack">返回</ElButton>
      <span v-if="medcasno" class="text-base font-medium">病案号：{{ medcasno }}</span>
      <ElCheckbox v-model="includeHomepage">包含病案首页</ElCheckbox>
      <ElButton type="primary" :loading="previewLoading" @click="onPreview">预览卷宗 PDF</ElButton>
      <span v-if="pdfPageCount" class="text-sm text-gray-500">共 {{ pdfPageCount }} 页</span>
    </div>

    <ElTable
      v-loading="catalogLoading"
      :data="catalogRows"
      border
      size="small"
      row-key="refId"
      @selection-change="onSelectionChange"
    >
      <ElTableColumn
        type="selection"
        width="48"
        :selectable="(row: VolumeItemDto) => row.printable !== false"
      />
      <ElTableColumn label="类型" width="120">
        <template #default="{ row }">{{ itemTypeLabel(row) }}</template>
      </ElTableColumn>
      <ElTableColumn prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <ElTableColumn prop="documentTypeCode" label="文书编码" width="140" show-overflow-tooltip />
      <ElTableColumn label="创建时间" width="168">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </ElTableColumn>
      <ElTableColumn label="可打印" width="80" align="center">
        <template #default="{ row }">
          <ElTag :type="row.printable ? 'success' : 'info'" size="small">
            {{ row.printable ? '是' : '否' }}
          </ElTag>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElEmpty
      v-if="!catalogLoading && catalogRows.length === 0"
      description="暂无卷宗目录（需质控中或已归档）"
    />

    <div v-if="pdfPreviewUrl" class="mt-4">
      <iframe :src="pdfPreviewUrl" class="h-[70vh] w-full border" title="卷宗 PDF 预览" />
    </div>
  </Page>
</template>
