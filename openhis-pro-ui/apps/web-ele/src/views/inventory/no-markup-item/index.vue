<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { NoMarkupItem, PageNoMarkupItemParams } from '#/api/inventory/no-markup-item';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTabPane, ElTabs } from 'element-plus';

import {
  addNoMarkupItemApi,
  pageNoMarkupCandidatesApi,
  pageNoMarkupItemApi,
  removeNoMarkupItemApi,
} from '#/api/inventory/no-markup-item';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { BASIC_TABLE_MAX_HEIGHT } from '../../basic/basic-table-max-height';

defineOptions({ name: 'InventoryNoMarkupItemIndex' });

const activeTab = ref<'candidates' | 'maintained'>('maintained');

const kindOptions = [
  { label: '全部', value: '' },
  { label: '药品', value: '药品' },
  { label: '耗材', value: '耗材' },
];

const maintainedQuery = ref<PageNoMarkupItemParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  kindLabel: '',
});

const candidateQuery = ref<PageNoMarkupItemParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  kindLabel: '',
});

const maintainedData = ref<NoMarkupItem[]>([]);
const candidateData = ref<NoMarkupItem[]>([]);
const maintainedTotal = ref(0);
const candidateTotal = ref(0);
const loading = ref(false);

const maintainedFormItems = computed<FormItemConfig[]>(() => [
  { type: 'radio', prop: 'kindLabel', label: '品种', options: kindOptions },
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '编码 / 名称',
    clearable: true,
    width: '260px',
  },
]);

const candidateFormItems = computed<FormItemConfig[]>(() => [
  { type: 'radio', prop: 'kindLabel', label: '品种', options: kindOptions },
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '编码 / 名称',
    clearable: true,
    width: '260px',
  },
]);

const maintainedColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'kindLabel', label: '品种', width: 90, align: 'center' },
  { prop: 'itemBusNo', label: '编码', width: 160, showOverflowTooltip: true },
  { prop: 'itemName', label: '名称', minWidth: 220, showOverflowTooltip: true },
  { prop: 'action', label: '操作', fixed: 'right', slot: 'action', width: 100, align: 'center' },
];

const candidateColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'kindLabel', label: '品种', width: 90, align: 'center' },
  { prop: 'itemBusNo', label: '编码', width: 160, showOverflowTooltip: true },
  { prop: 'itemName', label: '名称', minWidth: 220, showOverflowTooltip: true },
  { prop: 'action', label: '操作', fixed: 'right', slot: 'action', width: 100, align: 'center' },
];

/** 雪花主键保持字符串，避免 Number 抹掉末位。 */
function normalizeId(value: null | number | string | undefined): string | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return String(value);
}

/** 列表行主键转字符串，候选行用目录表加主键做行键。 */
function mapRow(row: NoMarkupItem): NoMarkupItem & { rowKey: string } {
  const itemId = normalizeId(row.itemId);
  return {
    ...row,
    id: normalizeId(row.id),
    itemId,
    rowKey: `${row.itemTable ?? ''}:${itemId ?? ''}`,
  };
}

/** 拉取已维护的不加价清单。 */
async function loadMaintained() {
  loading.value = true;
  try {
    const res = await pageNoMarkupItemApi({
      ...maintainedQuery.value,
      keyword: maintainedQuery.value.keyword?.trim() || undefined,
      kindLabel: maintainedQuery.value.kindLabel || undefined,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<NoMarkupItem>(res);
    maintainedData.value = content.map(mapRow);
    maintainedTotal.value = totalCount;
  } catch {
    ElMessage.error('查询不加价清单失败');
    maintainedData.value = [];
    maintainedTotal.value = 0;
  } finally {
    loading.value = false;
  }
}

/** 拉取尚未加入清单的在用药品和耗材。 */
async function loadCandidates() {
  loading.value = true;
  try {
    const res = await pageNoMarkupCandidatesApi({
      ...candidateQuery.value,
      keyword: candidateQuery.value.keyword?.trim() || undefined,
      kindLabel: candidateQuery.value.kindLabel || undefined,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<NoMarkupItem>(res);
    candidateData.value = content.map(mapRow);
    candidateTotal.value = totalCount;
  } catch {
    ElMessage.error('查询目录失败');
    candidateData.value = [];
    candidateTotal.value = 0;
  } finally {
    loading.value = false;
  }
}

/** 切换页签时刷新当前列表。 */
function refreshCurrentTab() {
  if (activeTab.value === 'maintained') {
    void loadMaintained();
  } else {
    void loadCandidates();
  }
}

/** 按品种和关键词重查已维护清单。 */
function handleMaintainedQuery(params: Record<string, unknown>) {
  maintainedQuery.value = {
    ...maintainedQuery.value,
    ...params,
    pageNo: 1,
  } as PageNoMarkupItemParams;
  void loadMaintained();
}

/** 清空已维护清单的筛选。 */
function handleMaintainedReset() {
  maintainedQuery.value = { pageNo: 1, pageSize: 50, keyword: '', kindLabel: '' };
  void loadMaintained();
}

/** 按品种和关键词重查目录候选。 */
function handleCandidateQuery(params: Record<string, unknown>) {
  candidateQuery.value = {
    ...candidateQuery.value,
    ...params,
    pageNo: 1,
  } as PageNoMarkupItemParams;
  void loadCandidates();
}

/** 清空目录候选的筛选。 */
function handleCandidateReset() {
  candidateQuery.value = { pageNo: 1, pageSize: 50, keyword: '', kindLabel: '' };
  void loadCandidates();
}

/** 已维护清单翻页。 */
function handleMaintainedPagination(pagination: { limit: number; page: number }) {
  maintainedQuery.value.pageNo = pagination.page;
  maintainedQuery.value.pageSize = pagination.limit;
  void loadMaintained();
}

/** 目录候选翻页。 */
function handleCandidatePagination(pagination: { limit: number; page: number }) {
  candidateQuery.value.pageNo = pagination.page;
  candidateQuery.value.pageSize = pagination.limit;
  void loadCandidates();
}

/** 把一条在用目录加入不加价清单。 */
async function handleAdd(row: NoMarkupItem) {
  const itemId = normalizeId(row.itemId);
  if (!itemId || !row.itemTable) {
    ElMessage.warning('这条目录缺少主键');
    return;
  }
  try {
    await addNoMarkupItemApi({ itemTable: row.itemTable, itemId });
    ElMessage.success('已加入不加价清单');
    void loadCandidates();
  } catch {
    ElMessage.error('加入失败');
  }
}

/** 从清单移出后，该品种进货同意恢复自动加价。 */
async function handleRemove(row: NoMarkupItem) {
  const id = normalizeId(row.id);
  if (!id) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `移出后，${row.itemName ?? '该品种'}进货同意将恢复自动加价。`,
      '移出不加价清单',
      { type: 'warning' },
    );
    await removeNoMarkupItemApi(id);
    ElMessage.success('已移出');
    void loadMaintained();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('移出失败');
    }
  }
}

onMounted(() => {
  void loadMaintained();
});
</script>

<template>
  <Page>
    <ElTabs v-model="activeTab" class="mb-2" @tab-change="refreshCurrentTab">
      <ElTabPane label="不加价清单" name="maintained" />
      <ElTabPane label="从目录加入" name="candidates" />
    </ElTabs>

    <TableLayout
      v-if="activeTab === 'maintained'"
      :table-data="maintainedData"
      :loading="loading"
      :total="maintainedTotal"
      :query-params="maintainedQuery"
      :form-items="maintainedFormItems"
      :table-columns="maintainedColumns"
      :max-height="BASIC_TABLE_MAX_HEIGHT"
      :show-top-query="true"
      :show-side-query="false"
      :show-pagination="true"
      row-key="id"
      stripe
      @query="handleMaintainedQuery"
      @reset="handleMaintainedReset"
      @pagination="handleMaintainedPagination"
    >
      <template #action="{ row }">
        <ElButton link type="danger" @click="handleRemove(row)">移出</ElButton>
      </template>
    </TableLayout>

    <TableLayout
      v-else
      :table-data="candidateData"
      :loading="loading"
      :total="candidateTotal"
      :query-params="candidateQuery"
      :form-items="candidateFormItems"
      :table-columns="candidateColumns"
      :max-height="BASIC_TABLE_MAX_HEIGHT"
      :show-top-query="true"
      :show-side-query="false"
      :show-pagination="true"
      row-key="rowKey"
      stripe
      @query="handleCandidateQuery"
      @reset="handleCandidateReset"
      @pagination="handleCandidatePagination"
    >
      <template #action="{ row }">
        <ElButton link type="primary" @click="handleAdd(row)">加入</ElButton>
      </template>
    </TableLayout>
  </Page>
</template>
