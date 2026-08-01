<script setup lang="ts">
import type {
  ExternalDocumentDto,
  ExternalDocumentSourceDto,
} from '#/api/health/external-document';

import { ref } from 'vue';

import { Page } from '@vben/common-ui';

import { ElAlert, ElButton, ElCard, ElOption, ElSelect } from 'element-plus';

import {
  listExternalDocumentSourcesApi,
  resolveExternalDocumentApi,
} from '#/api/health/external-document';
import { useRequest } from '#/hooks/use-request';

defineOptions({
  name: 'WhaleReportExternalViewer',
});

const sources = ref<ExternalDocumentSourceDto[]>([]);
const selectedCode = ref('');
const resolved = ref<ExternalDocumentDto | null>(null);

const { run: runListSources, loading: listLoading } = useRequest(listExternalDocumentSourcesApi, {
  manual: true,
  onSuccess: (rows) => {
    sources.value = Array.isArray(rows) ? rows : [];
    if (!selectedCode.value && sources.value.length > 0) {
      selectedCode.value = sources.value[0]?.documentCode ?? '';
    }
  },
});

const { run: runResolve, loading: resolveLoading } = useRequest(resolveExternalDocumentApi, {
  manual: true,
  onSuccess: (data) => {
    resolved.value = data ?? null;
  },
});

runListSources();

function handleResolve() {
  if (!selectedCode.value) {
    return;
  }
  runResolve({ documentCode: selectedCode.value });
}
</script>

<template>
  <Page auto-content-height>
    <ElCard shadow="never" v-loading="listLoading">
      <template #header>外系统报告查看</template>
      <div class="flex flex-wrap items-center gap-3">
        <ElSelect v-model="selectedCode" placeholder="选择报告类型" style="width: 240px">
          <ElOption
            v-for="item in sources"
            :key="item.documentCode"
            :label="item.title ?? item.documentCode"
            :value="item.documentCode ?? ''"
          />
        </ElSelect>
        <ElButton type="primary" :loading="resolveLoading" @click="handleResolve">
          解析文档
        </ElButton>
      </div>
    </ElCard>

    <ElCard v-if="resolved" class="mt-4" shadow="never" v-loading="resolveLoading">
      <template #header>{{ resolved.title ?? resolved.documentCode }}</template>
      <ElAlert
        v-if="!resolved.available"
        type="warning"
        :closable="false"
        :title="resolved.message || '外系统 Adapter 未接入'"
        show-icon
      />
      <iframe
        v-else-if="resolved.documentUrl"
        :src="resolved.documentUrl"
        class="h-[70vh] w-full border-0"
        title="external-report"
      />
    </ElCard>
  </Page>
</template>
