<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  BatchSaveLisPacsItemMappingResult,
  LisPacsItemMappingItem,
  PageLisPacsItemMappingParams,
  PageUnmappedActivityParams,
  UnmappedActivityItem,
} from '#/api/basic/lis-pacs-item-mapping';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElSpace,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  activateLisPacsItemMappingApi,
  batchSaveLisPacsItemMappingApi,
  deactivateLisPacsItemMappingApi,
  deleteLisPacsItemMappingApi,
  LIS_PACS_CATEGORY,
  pageLisPacsItemMappingApi,
  pageUnmappedActivityApi,
} from '#/api/basic/lis-pacs-item-mapping';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';

defineOptions({ name: 'BasicLisPacsItemMappingIndex' });

const activeTab = ref<'mapped' | 'unmapped'>('unmapped');

const mappedQuery = ref<PageLisPacsItemMappingParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  categoryEnum: '',
  activeFlag: undefined,
});

const unmappedQuery = ref<PageUnmappedActivityParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  categoryEnum: '',
});

const mappedData = ref<LisPacsItemMappingItem[]>([]);
const unmappedData = ref<UnmappedActivityItem[]>([]);
const mappedTotal = ref(0);
const unmappedTotal = ref(0);
const loading = ref(false);
const batchSaving = ref(false);

const dialogVisible = ref(false);
const dialogFormData = ref<
  Partial<LisPacsItemMappingItem> & { id?: string; suggestedExternalCode?: string }
>({});

const mappedCategoryOptions = [
  { label: '全部', value: '' },
  { label: '检查', value: LIS_PACS_CATEGORY.PACS },
  { label: '检验', value: LIS_PACS_CATEGORY.LIS },
];

/** 未对码清单按诊疗业务类型 type_enum：1=检验 / 2=检查 */
const unmappedTypeOptions = [
  { label: '全部', value: '' },
  { label: '检查', value: '2' },
  { label: '检验', value: '1' },
];

const activeFlagOptions = [
  { label: '全部', value: '' },
  { label: '启用', value: 'true' },
  { label: '停用', value: 'false' },
];

const mappedFormItems = computed<FormItemConfig[]>(() => [
  {
    type: 'radio',
    prop: 'categoryEnum',
    label: '业务类型',
    options: mappedCategoryOptions,
  },
  {
    type: 'select',
    prop: 'activeFlag',
    label: '状态',
    options: activeFlagOptions,
    clearable: true,
    width: '120px',
  },
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '院内码 / 名称 / 外发码',
    clearable: true,
    width: '260px',
  },
]);

const unmappedFormItems = computed<FormItemConfig[]>(() => [
  {
    type: 'radio',
    prop: 'categoryEnum',
    label: '业务类型',
    options: unmappedTypeOptions,
  },
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '院内码 / 名称',
    clearable: true,
    width: '260px',
  },
]);

const mappedColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'categoryLabel', label: '业务类型', width: 100, align: 'center' },
  { prop: 'activityBusNo', label: '院内编码', width: 140, showOverflowTooltip: true },
  { prop: 'activityName', label: '院内名称', minWidth: 160, showOverflowTooltip: true },
  { prop: 'externalCode', label: '外发码', width: 140, showOverflowTooltip: true },
  { prop: 'externalName', label: '外发名称', minWidth: 140, showOverflowTooltip: true },
  { prop: 'activeFlag', label: '状态', width: 90, align: 'center', slot: 'activeFlag' },
  { prop: 'action', label: '操作', fixed: 'right', slot: 'action', width: 200, align: 'center' },
];

const unmappedColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'categoryLabel', label: '业务类型', width: 100, align: 'center' },
  { prop: 'activityBusNo', label: '院内编码', width: 140, showOverflowTooltip: true },
  { prop: 'activityName', label: '院内名称', minWidth: 180, showOverflowTooltip: true },
  {
    prop: 'draftExternalCode',
    label: '外送码',
    width: 180,
    slot: 'draftExternalCode',
  },
  {
    prop: 'suggestedExternalCode',
    label: '建议外发码',
    width: 140,
    showOverflowTooltip: true,
  },
];

const pendingSaveCount = computed(
  () => unmappedData.value.filter((row) => String(row.draftExternalCode ?? '').trim()).length,
);

function normalizeId(value: null | number | string | undefined): string | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return String(value);
}

function parseActiveFlag(raw: unknown): boolean | undefined {
  if (raw === true || raw === 'true') {
    return true;
  }
  if (raw === false || raw === 'false') {
    return false;
  }
  return undefined;
}

async function loadMapped() {
  loading.value = true;
  try {
    const params: PageLisPacsItemMappingParams = {
      ...mappedQuery.value,
      categoryEnum: mappedQuery.value.categoryEnum || undefined,
      activeFlag: parseActiveFlag(mappedQuery.value.activeFlag as unknown),
    };
    const res = await pageLisPacsItemMappingApi(params);
    const { content, totalCount } = parsePagedRowsFromApiData<LisPacsItemMappingItem>(res);
    mappedData.value = content.map((row) => ({
      ...row,
      id: normalizeId(row.id),
      activityId: normalizeId(row.activityId),
    }));
    mappedTotal.value = totalCount;
  } catch {
    ElMessage.error('查询对照失败');
    mappedData.value = [];
    mappedTotal.value = 0;
  } finally {
    loading.value = false;
  }
}

async function loadUnmapped() {
  loading.value = true;
  try {
    const rawType = unmappedQuery.value.categoryEnum;
    const typeFilter =
      rawType === '1' || rawType === '2' || rawType === '22' || rawType === '23'
        ? String(rawType)
        : undefined;
    const params: PageUnmappedActivityParams = {
      ...unmappedQuery.value,
      categoryEnum: typeFilter,
      keyword: unmappedQuery.value.keyword?.trim() || undefined,
    };
    const res = await pageUnmappedActivityApi(params);
    const { content, totalCount } = parsePagedRowsFromApiData<UnmappedActivityItem>(res);
    unmappedData.value = content.map((row) => ({
      ...row,
      activityId: normalizeId(row.activityId),
      draftExternalCode: row.suggestedExternalCode?.trim() || '',
    }));
    unmappedTotal.value = totalCount;
  } catch (error) {
    const message = error instanceof Error && error.message ? error.message : '查询未对码清单失败';
    ElMessage.error(message);
    unmappedData.value = [];
    unmappedTotal.value = 0;
  } finally {
    loading.value = false;
  }
}

function refreshCurrentTab() {
  if (activeTab.value === 'mapped') {
    void loadMapped();
  } else {
    void loadUnmapped();
  }
}

function handleMappedQuery(params: Record<string, unknown>) {
  mappedQuery.value = {
    ...mappedQuery.value,
    ...params,
    pageNo: 1,
  } as PageLisPacsItemMappingParams;
  void loadMapped();
}

function handleMappedReset() {
  mappedQuery.value = {
    pageNo: 1,
    pageSize: 50,
    keyword: '',
    categoryEnum: '',
    activeFlag: undefined,
  };
  void loadMapped();
}

function handleUnmappedQuery(params: Record<string, unknown>) {
  unmappedQuery.value = {
    ...unmappedQuery.value,
    ...params,
    pageNo: 1,
  } as PageUnmappedActivityParams;
  void loadUnmapped();
}

function handleUnmappedReset() {
  unmappedQuery.value = {
    pageNo: 1,
    pageSize: 50,
    keyword: '',
    categoryEnum: '',
  };
  void loadUnmapped();
}

function handleMappedPagination(pagination: { limit: number; page: number }) {
  mappedQuery.value.pageNo = pagination.page;
  mappedQuery.value.pageSize = pagination.limit;
  void loadMapped();
}

function handleUnmappedPagination(pagination: { limit: number; page: number }) {
  unmappedQuery.value.pageNo = pagination.page;
  unmappedQuery.value.pageSize = pagination.limit;
  void loadUnmapped();
}

function handleGoUnmapped() {
  activeTab.value = 'unmapped';
  void loadUnmapped();
  ElMessage.info('请在未对码清单中填写外送码后批量保存');
}

function handleEdit(row: LisPacsItemMappingItem) {
  dialogFormData.value = {
    ...row,
    id: normalizeId(row.id),
    activityId: normalizeId(row.activityId),
  };
  dialogVisible.value = true;
}

function handleFillSuggested() {
  let filled = 0;
  for (const row of unmappedData.value) {
    const suggest = row.suggestedExternalCode?.trim();
    if (!suggest) {
      continue;
    }
    if (!String(row.draftExternalCode ?? '').trim()) {
      row.draftExternalCode = suggest;
      filled += 1;
    }
  }
  if (filled === 0) {
    ElMessage.info('没有可填充的建议外发码');
    return;
  }
  ElMessage.success(`已填充 ${filled} 条建议外发码`);
}

async function handleBatchSave() {
  const items = unmappedData.value
    .map((row) => {
      const activityId = normalizeId(row.activityId);
      const externalCode = String(row.draftExternalCode ?? '').trim();
      if (!activityId || !externalCode) {
        return null;
      }
      return {
        activityId,
        externalCode,
        externalName: row.activityName,
        activeFlag: true,
      };
    })
    .filter((item): item is NonNullable<typeof item> => item !== null);

  if (items.length === 0) {
    ElMessage.warning('请先填写外送码');
    return;
  }

  try {
    await ElMessageBox.confirm(`将保存 ${items.length} 条对照，确定继续吗？`, '批量保存', {
      type: 'warning',
    });
  } catch {
    return;
  }

  batchSaving.value = true;
  try {
    const res = await batchSaveLisPacsItemMappingApi({ items });
    const result = (res ?? {}) as BatchSaveLisPacsItemMappingResult;
    const succeeded = Number(result.succeeded ?? 0);
    const failed = Number(result.failed ?? 0);
    if (failed > 0) {
      const detail = (result.errors ?? []).slice(0, 3).join('；');
      ElMessage.warning(`成功 ${succeeded} 条，失败 ${failed} 条${detail ? `：${detail}` : ''}`);
    } else {
      ElMessage.success(`已保存 ${succeeded} 条对照`);
    }
    void loadUnmapped();
    void loadMapped();
  } catch {
    ElMessage.error('批量保存失败');
  } finally {
    batchSaving.value = false;
  }
}

async function handleActivate(row: LisPacsItemMappingItem) {
  const id = normalizeId(row.id);
  if (!id) {
    return;
  }
  try {
    await activateLisPacsItemMappingApi(id);
    ElMessage.success('已启用');
    void loadMapped();
  } catch {
    ElMessage.error('启用失败');
  }
}

async function handleDeactivate(row: LisPacsItemMappingItem) {
  const id = normalizeId(row.id);
  if (!id) {
    return;
  }
  try {
    await ElMessageBox.confirm('停用后外发将跳过该项目，确定停用吗？', '停用对照', {
      type: 'warning',
    });
    await deactivateLisPacsItemMappingApi(id);
    ElMessage.success('已停用');
    void loadMapped();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败');
    }
  }
}

async function handleDelete(row: LisPacsItemMappingItem) {
  const id = normalizeId(row.id);
  if (!id) {
    return;
  }
  try {
    await ElMessageBox.confirm('删除后可在未对码清单中重新补码，确定删除吗？', '删除对照', {
      type: 'warning',
    });
    await deleteLisPacsItemMappingApi(id);
    ElMessage.success('已删除');
    void loadMapped();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
}

function handleDialogSuccess() {
  void loadMapped();
  if (activeTab.value === 'unmapped') {
    void loadUnmapped();
  }
}

onMounted(() => {
  void loadUnmapped();
});
</script>

<template>
  <Page>
    <ElTabs v-model="activeTab" class="mb-2" @tab-change="refreshCurrentTab">
      <ElTabPane label="未对码清单" name="unmapped" />
      <ElTabPane label="已对码" name="mapped" />
    </ElTabs>

    <TableLayout
      v-if="activeTab === 'unmapped'"
      :table-data="unmappedData"
      :loading="loading"
      :total="unmappedTotal"
      :query-params="unmappedQuery"
      :form-items="unmappedFormItems"
      :table-columns="unmappedColumns"
      :max-height="BASIC_TABLE_MAX_HEIGHT"
      :show-top-query="true"
      :show-side-query="false"
      :show-pagination="true"
      row-key="activityId"
      stripe
      @query="handleUnmappedQuery"
      @reset="handleUnmappedReset"
      @pagination="handleUnmappedPagination"
    >
      <template #toolbar-actions>
        <ElSpace wrap :size="8">
          <ElButton @click="handleFillSuggested">填充建议码</ElButton>
          <ElButton
            type="primary"
            :loading="batchSaving"
            :disabled="pendingSaveCount === 0"
            @click="handleBatchSave"
          >
            批量保存{{ pendingSaveCount > 0 ? `（${pendingSaveCount}）` : '' }}
          </ElButton>
        </ElSpace>
      </template>

      <template #draftExternalCode="{ row }">
        <ElInput
          v-model="row.draftExternalCode"
          placeholder="填写外送码"
          clearable
          size="small"
          @click.stop
        />
      </template>
    </TableLayout>

    <TableLayout
      v-else
      :table-data="mappedData"
      :loading="loading"
      :total="mappedTotal"
      :query-params="mappedQuery"
      :form-items="mappedFormItems"
      :table-columns="mappedColumns"
      :max-height="BASIC_TABLE_MAX_HEIGHT"
      :show-top-query="true"
      :show-side-query="false"
      :show-pagination="true"
      row-key="id"
      stripe
      @query="handleMappedQuery"
      @reset="handleMappedReset"
      @pagination="handleMappedPagination"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleGoUnmapped">去未对码补码</ElButton>
      </template>

      <template #activeFlag="{ row }">
        <ElTag :type="row.activeFlag ? 'success' : 'info'" size="small" effect="plain">
          {{ row.activeFlag ? '启用' : '停用' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElSpace wrap :size="4">
          <ElButton type="primary" link @click.stop="handleEdit(row)">编辑</ElButton>
          <ElButton v-if="!row.activeFlag" type="primary" link @click.stop="handleActivate(row)">
            启用
          </ElButton>
          <ElButton v-if="row.activeFlag" type="warning" link @click.stop="handleDeactivate(row)">
            停用
          </ElButton>
          <ElButton type="danger" link @click.stop="handleDelete(row)">删除</ElButton>
        </ElSpace>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
