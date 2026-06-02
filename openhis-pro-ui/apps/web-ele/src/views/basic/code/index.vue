<script setup lang="ts">
import type { TableColumn, TreeNodeData } from '@vben-core/table-layout-ui';

import type { CodeListParams, CodeListResponse } from '#/api/basic/code';

import { onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteCodeSystemApi,
  deleteConceptApi,
  getCodeListApi,
  getConceptListApi,
  getEnumConceptListApi,
  getEnumDictListApi,
} from '#/api/basic/code';

import ConceptEditDialog from './concept-edit-dialog.vue';
import EditDialog from './edit-dialog.vue';

defineOptions({
  name: 'CodeList',
});

interface ConceptItem {
  id?: string;
  name?: string;
  code?: string;
  description?: string;
  [key: string]: any;
}

const siderData = ref<TreeNodeData[]>([]);
const enumSiderData = ref<TreeNodeData[]>([]);
const sideQueryLoading = ref(false);
const selectedCodeId = ref<string>('');
const selectedEnumName = ref<string>('');
const activeSideTab = ref<'codesystem' | 'enum'>('codesystem');

const allConceptData = ref<ConceptItem[]>([]);
const tableData = ref<ConceptItem[]>([]);
const loading = ref(false);
const total = ref(0);
const initQueryParams = {
  pageNo: 1,
  pageSize: 20,
};
const queryParams = reactive<CodeListParams>({ ...initQueryParams });

const formItems = ref([
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '请输入关键词',
    clearable: true,
  },
  {
    label: '名称',
    prop: 'name',
    type: 'input',
    placeholder: '请输入名称',
    clearable: true,
  },
]);

const tableColumns = ref<TableColumn[]>([
  {
    type: 'index',
    label: '序号',
    width: 60,
  },
  {
    label: '名称',
    prop: 'display',
    width: 200,
  },
  {
    label: '代码',
    prop: 'code',
    width: 200,
  },
  {
    label: '排序',
    prop: 'sortOrder',
    width: 100,
  },
  {
    label: '描述',
    prop: 'definition',
    minWidth: 200,
    showOverflowTooltip: true,
  },
  {
    label: '操作',
    prop: 'action',
    fixed: 'right',
    slot: 'action',
    width: 200,
  },
]);

const transformCodeToTreeData = (codes: CodeListResponse[]): TreeNodeData[] => {
  return codes.map((code) => ({
    id: code.id,
    label: `${code.title}-${code.name}`,
    name: code.name,
    title: code.title,
    version: code.version,
    status: code.status,
    description: code.description,
  }));
};

const loadSiderData = async () => {
  sideQueryLoading.value = true;
  try {
    const response = await getCodeListApi({
      pageNo: 1,
      pageSize: 1000,
    });
    // 后端返回为 PagedResult：{ content, totalCount, pageNo, pageSize }
    // requestClient 已将外层 { code, data } 解包，这里直接取 content
    const list = Array.isArray(response)
      ? response
      : (response as any)?.content || (response as any)?.data?.content || [];
    siderData.value = transformCodeToTreeData(list || []);
  } catch (error) {
    console.error('加载侧边栏数据失败:', error);
    ElMessage.error('加载侧边栏数据失败，请稍后重试');
    siderData.value = [];
  } finally {
    sideQueryLoading.value = false;
  }
};

const loadEnumSiderData = async () => {
  sideQueryLoading.value = true;
  try {
    const response = await getEnumDictListApi();
    const list = Array.isArray(response) ? response : (response as any)?.data || [];
    enumSiderData.value = (list || []).map((x: any) => ({
      id: x.value,
      label: x.label,
      name: x.value,
    }));
  } catch (error) {
    console.error('加载枚举侧边栏数据失败:', error);
    ElMessage.error('加载枚举侧边栏数据失败，请稍后重试');
    enumSiderData.value = [];
  } finally {
    sideQueryLoading.value = false;
  }
};

const handleSideQuery = async (node: TreeNodeData) => {
  if (!node || !node.id || node.id === '__ALL__') {
    tableData.value = [];
    total.value = 0;
    return;
  }
  if (activeSideTab.value === 'codesystem') {
    selectedEnumName.value = '';
    selectedCodeId.value = node.id as string;
    await loadConceptList(node.id as string);
  } else {
    selectedCodeId.value = '';
    selectedEnumName.value = node.id as string;
    await loadEnumConceptList(node.id as string);
  }
};

const loadConceptList = async (codeId: string) => {
  loading.value = true;
  try {
    const response = await getConceptListApi({ id: codeId });
    allConceptData.value = Array.isArray(response) ? response : response?.data || [];
    applyFilters();
  } catch (error: any) {
    console.error('加载概念列表失败:', error);
    const msg =
      error?.response?.data?.message ||
      error?.response?.data?.msg ||
      error?.message ||
      '加载概念列表失败，请稍后重试';
    ElMessage.error(msg);
    allConceptData.value = [];
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const loadEnumConceptList = async (enumName: string) => {
  loading.value = true;
  try {
    const response = await getEnumConceptListApi({ enumName });
    allConceptData.value = Array.isArray(response) ? response : response?.data || [];
    applyFilters();
  } catch (error) {
    console.error('加载枚举概念列表失败:', error);
    ElMessage.error('加载枚举概念列表失败，请稍后重试');
    allConceptData.value = [];
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const applyFilters = () => {
  let filtered = [...allConceptData.value];

  if (queryParams.keyword) {
    const keyword = queryParams.keyword.toLowerCase();
    filtered = filtered.filter(
      (item) =>
        (item.display || item.name || '').toLowerCase().includes(keyword) ||
        (item.code || '').toLowerCase().includes(keyword) ||
        (item.definition || item.description || '').toLowerCase().includes(keyword),
    );
  }

  if (queryParams.name) {
    const name = queryParams.name.toLowerCase();
    filtered = filtered.filter((item) =>
      (item.display || item.name || '').toLowerCase().includes(name),
    );
  }

  tableData.value = filtered;
  total.value = filtered.length;
};

const handleQuery = () => {
  if (activeSideTab.value === 'codesystem' && !selectedCodeId.value) {
    ElMessage.warning('请先选择左侧字典系统');
    return;
  }
  if (activeSideTab.value === 'enum' && !selectedEnumName.value) {
    ElMessage.warning('请先选择左侧枚举字典');
    return;
  }
  applyFilters();
};

const handleReset = () => {
  Object.assign(queryParams, initQueryParams);
  applyFilters();
};

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<CodeListResponse>>({});

const conceptDialogVisible = ref(false);
const conceptDialogFormData = ref<Partial<ConceptItem>>({});

const handleAdd = () => {
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleAddConcept = () => {
  if (activeSideTab.value === 'enum') {
    ElMessage.warning('枚举字典不支持新增');
    return;
  }
  if (!selectedCodeId.value) {
    ElMessage.warning('请先选择左侧代码系统');
    return;
  }
  conceptDialogFormData.value = {};
  conceptDialogVisible.value = true;
};

const handleDialogSuccess = () => {
  loadSiderData();
  if (selectedCodeId.value) {
    loadConceptList(selectedCodeId.value);
  }
};

const handleConceptDialogSuccess = () => {
  if (selectedCodeId.value) {
    loadConceptList(selectedCodeId.value);
  }
};

const handleRefreshSideQuery = () => {
  if (activeSideTab.value === 'codesystem') {
    loadSiderData();
  } else {
    loadEnumSiderData();
  }
};

const handleEditCodeSystem = (data: TreeNodeData) => {
  dialogFormData.value = {
    id: data.id as string,
    name: data.name,
    title: data.title,
    version: data.version,
    description: data.description,
    status: data.status,
  };
  dialogVisible.value = true;
};

const handleDeleteCodeSystem = async (data: TreeNodeData) => {
  if (!data?.id) {
    ElMessage.warning('无法获取代码系统ID');
    return;
  }
  try {
    await ElMessageBox.confirm('确定要删除该代码系统及其下所有编码吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    sideQueryLoading.value = true;
    await deleteCodeSystemApi({ id: data.id as string });
    ElMessage.success('删除成功');

    // 如果当前选中的代码系统被删除，重置选中和列表数据
    if (selectedCodeId.value === data.id) {
      selectedCodeId.value = '';
      tableData.value = [];
      total.value = 0;
    }

    await loadSiderData();
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMessage =
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        error?.message ||
        error?.msg ||
        '删除失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  } finally {
    sideQueryLoading.value = false;
  }
};

const handleSideNodeAction = (payload: { command: string; data: TreeNodeData }) => {
  const { command, data } = payload;
  if (command === 'edit') {
    handleEditCodeSystem(data);
  } else if (command === 'delete') {
    handleDeleteCodeSystem(data);
  }
};

const handleEdit = (row: ConceptItem) => {
  if (activeSideTab.value === 'enum') {
    ElMessage.warning('枚举字典不支持编辑');
    return;
  }
  conceptDialogFormData.value = { ...row };
  conceptDialogVisible.value = true;
};

const handleDelete = async (row: ConceptItem) => {
  if (activeSideTab.value === 'enum') {
    ElMessage.warning('枚举字典不支持删除');
    return;
  }
  if (!row.id) {
    ElMessage.warning('无法获取数据ID');
    return;
  }
  try {
    await ElMessageBox.confirm('确定要删除这条记录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    loading.value = true;
    await deleteConceptApi({ id: row.id });
    ElMessage.success('删除成功');
    if (selectedCodeId.value) {
      await loadConceptList(selectedCodeId.value);
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      const errorMessage =
        error?.response?.data?.msg ||
        error?.response?.data?.message ||
        error?.message ||
        error?.msg ||
        '删除失败，请稍后重试';
      ElMessage.error(errorMessage);
    }
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadSiderData();
  loadEnumSiderData();
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
      :show-top-query="true"
      :show-side-query="true"
      :show-pagination="true"
      :is-all-data="true"
      :side-width="10"
      :sider-data="activeSideTab === 'codesystem' ? siderData : enumSiderData"
      :show-all-node="false"
      :side-query-loading="sideQueryLoading"
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
      @side-query="handleSideQuery"
      @refresh-side-query="handleRefreshSideQuery"
      @side-node-action="handleSideNodeAction"
    >
      <template #side-header>
        <el-tabs v-model="activeSideTab" class="w-full">
          <el-tab-pane label="字典系统" name="codesystem" />
          <el-tab-pane label="枚举字典" name="enum" />
        </el-tabs>
        <ElButton v-if="activeSideTab === 'codesystem'" type="primary" @click="handleAdd"
          >新增</ElButton
        >
      </template>

      <template #toolbar-actions>
        <ElButton v-if="activeSideTab === 'codesystem'" type="primary" @click="handleAddConcept">
          新增
        </ElButton>
      </template>

      <template #side-node-menu>
        <template v-if="activeSideTab === 'codesystem'">
          <el-dropdown-item command="edit">编辑</el-dropdown-item>
          <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
        </template>
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click.stop="handleEdit(row)"> 编辑 </ElButton>
        <ElButton type="danger" link @click.stop="handleDelete(row)"> 删除 </ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      @success="handleDialogSuccess"
    />

    <ConceptEditDialog
      v-model:visible="conceptDialogVisible"
      :form-data="conceptDialogFormData"
      :code-system-id="selectedCodeId"
      @success="handleConceptDialogSuccess"
    />
  </Page>
</template>

<style scoped>
/* 本页左侧树需要展示更长的“中文-英文”标签，允许换行而不是省略号截断 */
:deep(.el-tree-node__content span.text-ellipsis) {
  white-space: normal;
  overflow: visible;
  text-overflow: unset;
  word-break: break-all;
}
</style>
