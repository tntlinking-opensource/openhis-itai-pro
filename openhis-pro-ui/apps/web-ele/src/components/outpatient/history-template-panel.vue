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
  activeTab: 'template',
  patientId: '',
  encounterId: '',
  currentDocumentId: '',
});

const emit = defineEmits<{
  select: [item: EmrTemplateTreeNode];
  selectDocument: [row: DocumentSummaryDto];
  tabChange: [tab: 'history' | 'template'];
}>();

interface Props {
  activeTab?: 'history' | 'template';
  patientId?: string;
  encounterId?: string;
  /** 当前打开的文书 ID，用于高亮历史列表项 */
  currentDocumentId?: string;
  /** 门诊 / 住院医生站场景，驱动模板可见范围 */
  stationKind?: EmrTemplateStationKind;
}

const activeTab = ref(props.activeTab);
const searchKeyword = ref('');
const historyLoading = ref(false);
const historyRows = ref<DocumentSummaryDto[]>([]);
const templateTreeRef = ref<InstanceType<typeof EmrTemplateTree> | null>(null);

const STATUS_LABEL: Record<string, string> = {
  ARCHIVED: '已归档',
  DRAFT: '草稿',
  LOCKED: '已锁定',
  SUBMITTED: '已提交',
};

/** 文书状态展示文案 */
function formatDocumentStatus(status?: string): string {
  const key = (status ?? '').trim().toUpperCase();
  if (!key) return '—';
  return STATUS_LABEL[key] ?? status ?? '—';
}

/** 列表主标题：优先标题，否则文书类型名 */
function rowPrimaryLabel(row: DocumentSummaryDto): string {
  const title = (row.title ?? '').trim();
  if (title) return title;
  const typeName = (row.documentTypeName ?? '').trim();
  if (typeName) return typeName;
  const code = (row.documentTypeCode ?? '').trim();
  return code || '未命名文书';
}

/** 修改时间展示（接口为 ISO 时取本地日期时间简写） */
function formatUpdatedAt(raw?: string): string {
  const s = (raw ?? '').trim();
  if (!s) return '';
  const normalized = s.includes('T') ? s.replace('T', ' ').slice(0, 16) : s.slice(0, 16);
  return normalized;
}

/** 拉取当前患者/就诊下的文书列表 */
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
      keyword: searchKeyword.value.trim() || undefined,
      pageNo: 1,
      pageSize: 100,
    });
    const parsed = parsePagedRowsFromApiData<DocumentSummaryDto>(data);
    historyRows.value = parsed.content ?? [];
  } catch {
    historyRows.value = [];
  } finally {
    historyLoading.value = false;
  }
}

/** 切换 Tab */
function handleTabChange(tab: 'history' | 'template') {
  activeTab.value = tab;
  emit('tabChange', tab);
  if (tab === 'history') {
    void fetchHistoryList();
  }
}

/** 搜索历史病历 */
function handleSearch() {
  if (activeTab.value === 'history') {
    void fetchHistoryList();
  }
}

/** 点击历史行打开文书 */
function handleHistoryRowClick(row: DocumentSummaryDto) {
  emit('selectDocument', row);
}

watch(
  () => [props.patientId, props.encounterId] as const,
  () => {
    void fetchHistoryList();
  },
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
  <div class="flex h-full flex-col border-l bg-white">
    <div class="flex h-[42px] border-b">
      <div
        class="flex-1 cursor-pointer px-4 py-2 text-center text-sm transition-colors"
        :class="{
          'border-b-2 border-blue-500 font-medium text-blue-500': activeTab === 'template',
          'text-gray-600 hover:text-gray-900': activeTab !== 'template',
        }"
        @click="handleTabChange('template')"
      >
        模版（新建）
      </div>
      <div
        class="flex-1 cursor-pointer px-4 py-2 text-center text-sm transition-colors"
        :class="{
          'border-b-2 border-blue-500 font-medium text-blue-500': activeTab === 'history',
          'text-gray-600 hover:text-gray-900': activeTab !== 'history',
        }"
        @click="handleTabChange('history')"
      >
        历史（打开）
      </div>
    </div>

    <div class="flex h-[42px] flex-shrink-0 items-center border-b px-[10px]">
      <ElInput
        v-model="searchKeyword"
        placeholder="病历名称搜索..."
        clearable
        class="flex-1"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
    </div>

    <div class="min-h-0 flex-1 overflow-y-auto">
      <div v-show="activeTab === 'template'" class="p-4">
        <EmrTemplateTree
          ref="templateTreeRef"
          :station-kind="stationKind"
          :filter-text="searchKeyword"
          @select="(data) => emit('select', data)"
        />
      </div>

      <div
        v-show="activeTab === 'history'"
        v-loading="historyLoading"
        class="history-panel__list px-2 py-2"
      >
        <p
          v-if="!(patientId ?? '').trim()"
          class="history-panel__empty"
          style="color: var(--el-text-color-secondary)"
        >
          请先选择患者
        </p>
        <p
          v-else-if="!historyLoading && historyRows.length === 0"
          class="history-panel__empty"
          style="color: var(--el-text-color-secondary)"
        >
          暂无历史病历
        </p>
        <ul v-else class="history-panel__items m-0 list-none p-0">
          <li
            v-for="row in historyRows"
            :key="String(row.id ?? row.documentTypeCode ?? row.updatedAt)"
            class="history-panel__item"
            :class="{
              'history-panel__item--active':
                currentDocumentId && String(row.id) === String(currentDocumentId),
            }"
            @click="handleHistoryRowClick(row)"
          >
            <div class="history-panel__item-title" style="color: var(--el-text-color-primary)">
              {{ rowPrimaryLabel(row) }}
            </div>
            <div class="history-panel__item-meta" style="color: var(--el-text-color-regular)">
              <span v-if="row.documentTypeName && row.title">{{ row.documentTypeName }}</span>
              <span>{{ formatDocumentStatus(row.status) }}</span>
              <span v-if="formatUpdatedAt(row.updatedAt)">{{
                formatUpdatedAt(row.updatedAt)
              }}</span>
            </div>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.history-panel__empty {
  padding: 2rem 0.5rem;
  text-align: center;
  font-size: 13px;
}

.history-panel__item {
  margin-bottom: 6px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
  cursor: pointer;
  transition:
    border-color 0.15s,
    background-color 0.15s;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    background-color: var(--el-fill-color-light);
  }

  &--active {
    border-color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
  }
}

.history-panel__item-title {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  word-break: break-all;
}

.history-panel__item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.3;
}
</style>
