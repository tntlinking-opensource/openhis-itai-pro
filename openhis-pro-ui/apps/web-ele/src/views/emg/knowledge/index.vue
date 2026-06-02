<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { KnowledgeBaseItem, KnowledgeBaseQueryParams } from '#/api/emg/knowledge';
import { deleteKnowledgeBaseApi, listKnowledgeBaseApi } from '#/api/emg/knowledge';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import KnowledgeBaseFormDialog from './components/knowledge-base-form-dialog.vue';

defineOptions({
  name: 'KnowledgeBaseManagement',
});

const queryParams = ref<KnowledgeBaseQueryParams>({
  pageNo: 1,
  pageSize: 50,
  name: '',
  pinyinCode: '',
  triageLevel: null,
  type: null,
  category: null,
});

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

const tableData = ref<KnowledgeBaseItem[]>([]);
const loading = ref(false);
const total = ref(0);

const categories = [
  { label: '头部', value: 'HEAD' },
  { label: '胸部', value: 'CHEST' },
  { label: '腹部', value: 'ABDOMEN' },
  { label: '四肢', value: 'LIMBS' },
  { label: '全身', value: 'WHOLE' },
  { label: '其他', value: 'OTHER' },
];

const types = [
  { label: '症状', value: 'SYMPTOM' },
  { label: '疾病', value: 'DISEASE' },
  { label: '药物', value: 'MEDICINE' },
  { label: '其他', value: 'OTHER' },
];

const triageLevels = [
  { label: 'I 级-濒危', value: 1 },
  { label: 'II 级-危重', value: 2 },
  { label: 'III 级-急症', value: 3 },
  { label: 'IV 级-非急症', value: 4 },
  { label: 'V 级-亚急症', value: 5 },
];

const tableColumns: TableColumn[] = [
  {
    type: 'selection',
    fixed: 'left',
    width: 50,
    align: 'center',
  },
  {
    type: 'index',
    label: '序号',
  },
  {
    prop: 'name',
    label: '名称',
    minWidth: 150,
  },
  {
    prop: 'pinyinCode',
    label: '拼音码',
    width: 120,
  },
  {
    label: '分诊级别',
    slot: 'triageLevel',
    align: 'center',
    width: 120,
  },
  {
    label: '类型',
    slot: 'type',
    align: 'center',
    width: 100,
  },
  {
    label: '分类',
    slot: 'category',
    align: 'center',
    width: 100,
  },
  {
    label: '状态',
    slot: 'status',
    align: 'center',
    width: 80,
  },
  {
    prop: 'sortOrder',
    label: '序号',
    width: 80,
  },
  {
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 150,
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'name',
    label: '名称/拼音码',
    placeholder: '请输入名称或拼音码',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'triageLevel',
    label: '分诊级别',
    placeholder: '请选择分诊级别',
    clearable: true,
    multiple: false,
    options: triageLevels,
  },
  {
    type: 'select',
    prop: 'type',
    label: '类型',
    placeholder: '请选择类型',
    clearable: true,
    multiple: false,
    options: types,
  },
  {
    type: 'select',
    prop: 'category',
    label: '分类',
    placeholder: '请选择分类',
    clearable: true,
    multiple: false,
    options: categories,
  },
]);

// 加载知识库数据
const loadData = async () => {
  loading.value = true;
  try {
    const response = await listKnowledgeBaseApi(queryParams.value);
    tableData.value = response.content || [];
    total.value = response.totalCount || 0;
  } catch {
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

// 处理查询条件变化
const handleQuery = (params: Partial<KnowledgeBaseQueryParams>) => {
  if (params.name !== undefined) {
    queryParams.value.name = params.name || '';
  }
  if (params.triageLevel !== undefined) {
    queryParams.value.triageLevel = params.triageLevel;
  }
  if (params.type !== undefined) {
    queryParams.value.type = params.type;
  }
  if (params.category !== undefined) {
    queryParams.value.category = params.category;
  }
  queryParams.value.pageNo = 1;
  loadData();
};

// 重置查询条件
const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    name: '',
    pinyinCode: '',
    triageLevel: null,
    type: null,
    category: null,
  };
  loadData();
};

const selectedRows = ref<KnowledgeBaseItem[]>([]);

// 处理表格选择变化
const handleSelectionChange = (selection: KnowledgeBaseItem[] | Record<string, unknown>[]) => {
  selectedRows.value = selection as KnowledgeBaseItem[];
};

const dialogVisible = ref(false);
const currentKnowledgeBase = ref<KnowledgeBaseItem | null>(null);

// 打开新增知识库对话框
const handleAdd = () => {
  currentKnowledgeBase.value = null;
  dialogVisible.value = true;
};

// 打开编辑知识库对话框
const handleEdit = (row: KnowledgeBaseItem) => {
  currentKnowledgeBase.value = row;
  dialogVisible.value = true;
};

// 处理对话框操作成功
const handleDialogSuccess = () => {
  loadData();
};

// 删除知识库
const handleDelete = async (row: KnowledgeBaseItem) => {
  if (!row.id) {
    ElMessage.warning('缺少知识库ID');
    return;
  }

  try {
    await ElMessageBox.confirm('确定删除该知识库吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteKnowledgeBaseApi(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error?.msg || error?.message || '删除失败，请稍后重试');
    }
  }
};

// 组件挂载时加载数据
onMounted(() => {
  loadData();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-side-query="false"
      :show-pagination="true"
      :is-all-data="true"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>

      <template #triageLevel="{ row }">
        <ElTag v-if="row.triageLevel === 1" type="danger">I 级</ElTag>
        <ElTag v-else-if="row.triageLevel === 2" type="warning">II 级</ElTag>
        <ElTag v-else-if="row.triageLevel === 3" type="info">III 级</ElTag>
        <ElTag v-else-if="row.triageLevel === 4" type="success">IV 级</ElTag>
        <ElTag v-else type="info">V 级</ElTag>
      </template>

      <template #type="{ row }">
        <ElTag>{{ types.find((t) => t.value === row.type)?.label }}</ElTag>
      </template>

      <template #category="{ row }">
        <ElTag>{{ categories.find((c) => c.value === row.category)?.label }}</ElTag>
      </template>

      <template #status="{ row }">
        <ElTag v-if="row.status === 1" type="success">启用</ElTag>
        <ElTag v-else type="danger">禁用</ElTag>
      </template>

      <template #action="{ row }">
        <ElButton link type="primary" @click="handleEdit(row)">编辑</ElButton>
        <ElButton link type="danger" @click="handleDelete(row)">删除</ElButton>
      </template>
    </TableLayout>

    <KnowledgeBaseFormDialog
      v-model="dialogVisible"
      :knowledge-base="currentKnowledgeBase"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
