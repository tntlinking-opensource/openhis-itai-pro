<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { MetadataFieldDto } from '#/api/emr/metadata-field';

import { computed, reactive, ref } from 'vue';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  checkMetadataFieldReferenceApi,
  deleteMetadataFieldApi,
  getMetadataFieldListApi,
} from '#/api/emr/metadata-field';
import { useRequest } from '#/hooks/use-request';

import MetadataFieldEditDialog from './metadata-field-edit-dialog.vue';
import {
  formatMetadataValueSource,
  METADATA_VALUE_SOURCE_OPTIONS,
} from './metadata-field.constants';

defineOptions({
  name: 'EmrMetadataFieldTab',
});

const list = ref<MetadataFieldDto[]>([]);
const total = ref(0);
const editDialogRef = ref<InstanceType<typeof MetadataFieldEditDialog> | null>(null);

const queryParams = reactive<{
  pageNo: number;
  pageSize: number;
  keyword?: string;
  valueSource?: string;
  isEnabled?: boolean;
}>({
  pageNo: 1,
  pageSize: 50,
});

const { run: runList, loading: listLoading } = useRequest(getMetadataFieldListApi, {
  manual: true,
});
const { run: runDelete, loading: deleteLoading } = useRequest(deleteMetadataFieldApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('删除成功');
    getList();
  },
});

const tableLoading = computed(() => listLoading.value || deleteLoading.value);

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: 'fieldKey / 显示名',
  },
  {
    label: '取值来源',
    prop: 'valueSource',
    type: 'select',
    clearable: true,
    filterable: true,
    options: [{ label: '全部', value: '' }, ...METADATA_VALUE_SOURCE_OPTIONS],
  },
  {
    label: '状态',
    prop: 'isEnabled',
    type: 'select',
    options: [
      { label: '全部', value: undefined },
      { label: '启用', value: true },
      { label: '停用', value: false },
    ],
    clearable: true,
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60, align: 'center' },
  {
    prop: 'fieldKey',
    label: 'fieldKey',
    minWidth: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'label',
    label: '显示名',
    minWidth: 120,
    showOverflowTooltip: true,
  },
  {
    label: '取值来源',
    width: 120,
    align: 'center',
    slot: 'valueSource',
  },
  {
    prop: 'sourcePath',
    label: 'source_path',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    label: '可编辑',
    width: 88,
    align: 'center',
    slot: 'editable',
  },
  {
    label: '脱敏',
    prop: 'maskRule',
    width: 88,
    align: 'center',
  },
  {
    prop: 'sortOrder',
    label: '排序',
    width: 72,
    align: 'center',
  },
  {
    label: '状态',
    width: 88,
    align: 'center',
    slot: 'enabled',
  },
  { align: 'left', fixed: 'right', label: '操作', slot: 'actions', width: 140 },
];

async function getList() {
  try {
    const params = {
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword?.trim() || undefined,
      valueSource: queryParams.valueSource || undefined,
      isEnabled: typeof queryParams.isEnabled === 'boolean' ? queryParams.isEnabled : undefined,
    };
    const res = await runList(params);
    list.value = res?.content ?? [];
    total.value = Number(res?.totalCount ?? 0);
  } catch (error) {
    console.error(error);
    list.value = [];
    total.value = 0;
  }
}

function handleReset() {
  queryParams.keyword = undefined;
  queryParams.valueSource = undefined;
  queryParams.isEnabled = undefined;
  queryParams.pageNo = 1;
  getList();
}

function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

function openCreate() {
  editDialogRef.value?.openCreate();
}

function openEdit(row: MetadataFieldDto) {
  editDialogRef.value?.openEdit(row);
}

async function handleDelete(row: MetadataFieldDto) {
  const id = row.id;
  const fieldKey = row.fieldKey;
  if (id === undefined || id === null || !fieldKey) return;

  try {
    const refCheck = await checkMetadataFieldReferenceApi({ fieldKey });
    if (refCheck?.referenced) {
      const names = refCheck.templateNames?.join('、') || '未知模板';
      ElMessage.warning(`该字段已被模板引用（${names}），请禁用而非删除`);
      return;
    }
  } catch (error) {
    console.error(error);
  }

  ElMessageBox.confirm(`确定删除元数据字段「${row.label ?? fieldKey}」吗？`, '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确定',
    type: 'warning',
  }).then(() => {
    runDelete({ id: String(id) });
  });
}

function isRowEnabled(row: MetadataFieldDto) {
  return row.isEnabled !== false;
}

defineExpose({
  refresh: getList,
});

getList();
</script>

<template>
  <div class="metadata-field-tab">
    <TableLayout
      :form-items="formItems"
      :loading="tableLoading"
      :query-params="queryParams"
      :show-pagination="true"
      :show-top-query="true"
      :table-columns="tableColumns"
      :table-data="list"
      :total="total"
      @pagination="handlePagination"
      @query="getList"
      @reset="handleReset"
    >
      <template #valueSource="{ row }">
        {{ formatMetadataValueSource(row.valueSource) }}
      </template>
      <template #editable="{ row }">
        <ElTag :type="row.editableDefault !== false ? 'success' : 'info'" size="small">
          {{ row.editableDefault !== false ? '是' : '否' }}
        </ElTag>
      </template>
      <template #enabled="{ row }">
        <ElTag :type="isRowEnabled(row) ? 'success' : 'info'" size="small">
          {{ isRowEnabled(row) ? '启用' : '停用' }}
        </ElTag>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">编辑</ElButton>
        <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
      </template>
      <template #toolbar-actions>
        <ElButton type="primary" @click="openCreate">新增字段</ElButton>
      </template>
    </TableLayout>
    <MetadataFieldEditDialog ref="editDialogRef" @success="getList" />
  </div>
</template>

<style scoped>
.metadata-field-tab {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}
</style>
