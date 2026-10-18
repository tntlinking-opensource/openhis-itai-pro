<script setup lang="ts">
import type { EmrTemplateTreeNode } from './build-emr-template-tree';

import type { DocumentSummaryDto } from '#/api/emr/document';
import type { TemplateDto } from '#/api/emr/template';
import type { EmrTemplateStationKind } from '#/views/basic/emr-template/emr-template-scope.constants';

import { computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Tickets } from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElInput, ElMessage } from 'element-plus';

import { getDocumentListApi } from '#/api/emr/document';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { EMR_TEMPLATE_STATION_KIND } from '#/views/basic/emr-template/emr-template-scope.constants';

import EmrTemplateTree from './emr-template-tree.vue';

defineOptions({
  name: 'HistoryTemplatePanel',
});

const props = withDefaults(defineProps<Props>(), {
  patientId: '',
  encounterId: '',
  currentDocumentId: '',
  openDocumentIds: () => [],
  viewOnly: false,
  encounterScoped: false,
  restrictHistoryToInpatient: false,
  canUpdateTemplateContent: false,
});

const emit = defineEmits<{
  select: [item: EmrTemplateTreeNode];
  selectDocument: [row: DocumentSummaryDto];
  openDocumentInNewTab: [row: DocumentSummaryDto];
  deleteDocument: [row: DocumentSummaryDto];
  renameDocument: [row: DocumentSummaryDto];
  reuseDocument: [row: DocumentSummaryDto];
  renameTemplate: [tpl: TemplateDto];
  updateTemplateContent: [tpl: TemplateDto];
  deleteTemplate: [tpl: TemplateDto];
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
  /** 只读调阅：侧栏不展示可新建模板，仅打开已有病历 */
  viewOnly?: boolean;
  /**
   * 按就诊收窄：仅拉取本 encounter 文书，不拉患者跨就诊历史。
   * 病案「门诊/住院病例查询」打开编辑时使用（列表行锚点为就诊）。
   */
  encounterScoped?: boolean;
  /**
   * 仅护士站：侧栏文书按「住院病历」目录过滤。
   * 病案只读查阅勿开。
   */
  restrictHistoryToInpatient?: boolean;
  /** 当前编辑器是否可用来覆盖更新模板正文 */
  canUpdateTemplateContent?: boolean;
}

const router = useRouter();
const searchKeyword = ref('');
const historyLoading = ref(false);
/** 已写病历列表：医生站=本次+历史；就诊收窄=仅本 encounter */
const historyRows = ref<DocumentSummaryDto[]>([]);
const templateTreeRef = ref<InstanceType<typeof EmrTemplateTree> | null>(null);

/** 侧栏根节点文案：就诊收窄时不用「历史」以免误解 */
const historyRootLabel = computed(() =>
  props.encounterScoped || props.viewOnly ? '本次就诊病历' : '历史病历',
);

/** 搜索框占位 */
const searchPlaceholder = computed(() =>
  props.viewOnly || props.encounterScoped ? '搜索本次就诊病历…' : '搜索本诊病历或模板…',
);

/** 住院医生站可写：侧栏提供病案首页跳转入口 */
const showCaseRecordHomepageEntry = computed(
  () =>
    props.stationKind === EMR_TEMPLATE_STATION_KIND.INPATIENT &&
    !props.viewOnly &&
    !!(props.encounterId ?? '').trim(),
);

/** 按 id 去重合并文书列表（保留先出现的项） */
function mergeDocumentsUnique(...lists: DocumentSummaryDto[][]): DocumentSummaryDto[] {
  const seen = new Set<string>();
  const out: DocumentSummaryDto[] = [];
  for (const list of lists) {
    for (const doc of list) {
      const id = preserveApiIdForRequest(doc.id) ?? '';
      if (id) {
        if (seen.has(id)) continue;
        seen.add(id);
      }
      out.push(doc);
    }
  }
  return out;
}

/**
 * 拉取侧栏已写文书。
 * 就诊收窄 / 只读调阅：仅当前 encounter；医生站可写：本次+关联+患者全量历史。
 */
async function fetchHistoryList() {
  const patientId = (props.patientId ?? '').trim();
  if (!patientId) {
    historyRows.value = [];
    return;
  }
  const encounterId = (props.encounterId ?? '').trim();
  historyLoading.value = true;
  try {
    // 病案按就诊打开 / 护士站只读：禁止跨就诊患者史
    if (props.viewOnly || props.encounterScoped) {
      if (!encounterId) {
        historyRows.value = [];
        return;
      }
      const sessionResult = await getDocumentListApi({
        patientId,
        encounterId,
        includeLinkedEncounters: false,
        pageNo: 1,
        pageSize: 200,
      });
      historyRows.value =
        parsePagedRowsFromApiData<DocumentSummaryDto>(sessionResult).content ?? [];
      return;
    }

    const [sessionResult, allResult] = await Promise.allSettled([
      encounterId
        ? getDocumentListApi({
            patientId,
            encounterId,
            includeLinkedEncounters: true,
            pageNo: 1,
            pageSize: 200,
          })
        : Promise.resolve(null),
      getDocumentListApi({
        patientId,
        pageNo: 1,
        pageSize: 200,
      }),
    ]);

    const sessionData =
      sessionResult.status === 'fulfilled' && sessionResult.value
        ? (parsePagedRowsFromApiData<DocumentSummaryDto>(sessionResult.value).content ?? [])
        : [];
    const allData =
      allResult.status === 'fulfilled'
        ? (parsePagedRowsFromApiData<DocumentSummaryDto>(allResult.value).content ?? [])
        : [];

    // 全量优先；并入本次/关联文书，避免全量接口漏数时侧栏缺本次
    historyRows.value = mergeDocumentsUnique(allData, sessionData);
  } catch {
    historyRows.value = [];
  } finally {
    historyLoading.value = false;
  }
}

/** 刷新左侧模板树（存为模版后） */
function refreshTemplateTree() {
  void templateTreeRef.value?.reload?.();
}

/** 跳转当前就诊病案首页详情（无案号按 encounterId 打开） */
function handleOpenCaseRecordHomepage() {
  const encounterId = preserveApiIdForRequest(props.encounterId);
  if (!encounterId) {
    ElMessage.warning('缺少就诊主键，无法打开病案首页');
    return;
  }
  void router.push({
    name: 'MrmsCaseRecordHomepageDetail',
    query: { encounterId },
  });
}

watch(
  () => [props.patientId, props.encounterId, props.viewOnly, props.encounterScoped] as const,
  () => {
    void fetchHistoryList();
  },
  { immediate: true },
);

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
      <ElInput v-model="searchKeyword" clearable size="small" :placeholder="searchPlaceholder" />
    </div>

    <div
      v-if="showCaseRecordHomepageEntry"
      class="history-template-panel__homepage flex h-[36px] flex-shrink-0 items-center border-b px-[10px]"
    >
      <ElButton
        type="primary"
        link
        class="history-template-panel__homepage-btn"
        @click="handleOpenCaseRecordHomepage"
      >
        <ElIcon class="mr-1"><Tickets /></ElIcon>
        病案首页
      </ElButton>
    </div>

    <div v-loading="historyLoading" class="min-h-0 flex-1 overflow-y-auto p-3">
      <div
        v-if="!(patientId ?? '').trim()"
        class="history-template-panel__hint py-6 text-center text-sm"
      >
        {{
          viewOnly || encounterScoped
            ? '缺少患者主键，无法加载本次就诊病历'
            : '请先选择患者；本诊已写在顶部，模板在下方'
        }}
      </div>
      <EmrTemplateTree
        v-else
        ref="templateTreeRef"
        :station-kind="stationKind"
        :filter-text="searchKeyword"
        :history-documents="historyRows"
        :history-root-label="historyRootLabel"
        :current-document-id="currentDocumentId"
        :open-document-ids="openDocumentIds"
        :current-encounter-id="encounterId"
        :view-only="viewOnly"
        :restrict-history-to-inpatient="restrictHistoryToInpatient"
        :can-update-template-content="canUpdateTemplateContent"
        @select="(item) => emit('select', item)"
        @select-document="(row) => emit('selectDocument', row)"
        @open-document-in-new-tab="(row) => emit('openDocumentInNewTab', row)"
        @delete-document="(row) => emit('deleteDocument', row)"
        @rename-document="(row) => emit('renameDocument', row)"
        @reuse-document="(row) => emit('reuseDocument', row)"
        @rename-template="(tpl) => emit('renameTemplate', tpl)"
        @update-template-content="(tpl) => emit('updateTemplateContent', tpl)"
        @delete-template="(tpl) => emit('deleteTemplate', tpl)"
      />
    </div>
  </div>
</template>

<style scoped>
.history-template-panel__hint {
  color: var(--el-text-color-secondary);
}

.history-template-panel__homepage-btn {
  padding: 0;
  font-size: 13px;
}
</style>
