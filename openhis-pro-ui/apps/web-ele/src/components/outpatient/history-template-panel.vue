<script setup lang="ts">
import type { EmrTemplateTreeNode } from './build-emr-template-tree';

import type { DocumentSummaryDto } from '#/api/emr/document';
import type { EmrTemplateStationKind } from '#/views/basic/emr-template/emr-template-scope.constants';

import { ref, watch } from 'vue';

import { ElInput } from 'element-plus';

import { getDocumentListApi } from '#/api/emr/document';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import EmrTemplateTree from './emr-template-tree.vue';

defineOptions({
  name: 'HistoryTemplatePanel',
});

const props = withDefaults(defineProps<Props>(), {
  patientId: '',
  encounterId: '',
  currentDocumentId: '',
  openDocumentIds: () => [],
});

const emit = defineEmits<{
  select: [item: EmrTemplateTreeNode];
  selectDocument: [row: DocumentSummaryDto];
  openDocumentInNewTab: [row: DocumentSummaryDto];
}>();

interface Props {
  patientId?: string;
  encounterId?: string;
  /** 当前激活文书 ID，用于高亮 */
  currentDocumentId?: string;
  /** 已打开（含非激活）的文书 ID */
  openDocumentIds?: string[];
  /** 门诊 / 住院医生站场景，驱动模板可见范围 */
  stationKind?: EmrTemplateStationKind;
}

const searchKeyword = ref('');
const historyLoading = ref(false);
const historyRows = ref<DocumentSummaryDto[]>([]);
const templateTreeRef = ref<InstanceType<typeof EmrTemplateTree> | null>(null);

/** 拉取当前患者/就诊下的文书列表（含关联门诊/住院对端；挂到模板树下） */
async function fetchHistoryList() {
  const patientId = (props.patientId ?? '').trim();
  if (!patientId) {
    historyRows.value = [];
    return;
  }
  const encounterId = (props.encounterId ?? '').trim();
  historyLoading.value = true;
  try {
    const data = await getDocumentListApi({
      patientId,
      encounterId: encounterId || undefined,
      includeLinkedEncounters: Boolean(encounterId),
      pageNo: 1,
      pageSize: 200,
    });
    const parsed = parsePagedRowsFromApiData<DocumentSummaryDto>(data);
    historyRows.value = parsed.content ?? [];
  } catch {
    historyRows.value = [];
  } finally {
    historyLoading.value = false;
  }
}

watch(
  () => [props.patientId, props.encounterId] as const,
  () => {
    void fetchHistoryList();
  },
  { immediate: true },
);

/** 刷新左侧模板树（存为模版后） */
function refreshTemplateTree() {
  void templateTreeRef.value?.reload?.();
}

defineExpose({
  refreshHistory: fetchHistoryList,
  refreshTemplateTree,
});
</script>

<template>
  <div class="history-template-panel flex h-full flex-col border-l bg-white">
    <div
      class="history-template-panel__search flex h-[42px] flex-shrink-0 items-center border-b px-[10px]"
    >
      <ElInput v-model="searchKeyword" placeholder="搜索模板或病历…" clearable class="flex-1" />
    </div>

    <div v-loading="historyLoading" class="min-h-0 flex-1 overflow-y-auto p-3">
      <p
        v-if="!(patientId ?? '').trim()"
        class="history-template-panel__hint py-6 text-center text-sm"
        style="color: var(--el-text-color-secondary)"
      >
        请先选择患者；可先浏览模板，接诊后在对应模板下查看已写病历
      </p>
      <EmrTemplateTree
        ref="templateTreeRef"
        :station-kind="stationKind"
        :filter-text="searchKeyword"
        :documents="historyRows"
        :current-document-id="currentDocumentId"
        :current-encounter-id="encounterId"
        :open-document-ids="openDocumentIds"
        @select="(data) => emit('select', data)"
        @select-document="(row) => emit('selectDocument', row)"
        @open-document-in-new-tab="(row) => emit('openDocumentInNewTab', row)"
      />
    </div>
  </div>
</template>
