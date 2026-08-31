<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  Department,
  DepartmentClassOption,
  DepartmentOption,
  DepartmentTypeOption,
  InitDepartmentResponse,
  PractitionerOption,
  QueryDepartmentListParams,
} from '#/api';

import { computed, onActivated, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  activeDepartmentApi,
  deleteDepartmentApi,
  inactiveDepartmentApi,
  initDepartmentApi,
  pageDepartmentApi,
} from '#/api';
import { PublicationStatusCode } from '#/constants/domain/publication-status';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';

defineOptions({ name: 'BasicDepartment' });

interface DepartmentApiResponse {
  data?: Department[];
}

function isDepartmentRow(value: unknown): value is Department {
  if (!value || typeof value !== 'object') {
    return false;
  }
  return 'id' in value;
}

function unwrapDepartmentListResponse(response: unknown): Department[] {
  if (Array.isArray(response)) {
    return response.filter(isDepartmentRow);
  }
  if (response && typeof response === 'object' && 'data' in response) {
    const data = (response as DepartmentApiResponse).data;
    if (Array.isArray(data)) {
      return data.filter(isDepartmentRow);
    }
  }
  return [];
}

const initQueryParams = {
  keyword: '',
  classEnum: undefined as string | undefined,
  typeEnum: undefined as number | string | undefined,
  statusEnum: undefined as string | undefined,
};
const queryParams = reactive({ ...initQueryParams });

const tableData = ref<Department[]>([]);
const loading = ref(false);
const total = ref(0);
const departmentClassOptions = ref<DepartmentClassOption[]>([]);
const departmentTypeOptions = ref<DepartmentTypeOption[]>([]);
const departmentOptions = ref<DepartmentOption[]>([]);
const practitionerOptions = ref<PractitionerOption[]>([]);

// 列表：名称列保留树层级；编号紧随其后。
const tableColumns: TableColumn[] = [
  { type: 'selection', fixed: 'left', width: 48, align: 'center', headerAlign: 'center' },
  {
    prop: 'name',
    label: '科室名称',
    treeNode: true,
    showOverflowTooltip: true,
  },
  {
    prop: 'busNo',
    label: '编号',
    minWidth: 140,
    showOverflowTooltip: true,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'typeEnum_dictText',
    label: '类型',
    showOverflowTooltip: true,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'classEnum_dictText',
    label: '类别',
    showOverflowTooltip: true,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'displayOrder',
    label: '排序号',
    width: 88,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'statusEnum_dictText',
    label: '状态',
    slot: 'statusEnum',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 168,
    align: 'center',
    headerAlign: 'center',
  },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入名称、医保信息、拼音、五笔等',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'typeEnum',
    label: '类型',
    placeholder: '请选择类型',
    clearable: true,
    multiple: false,
    options: departmentTypeOptions.value.map((item) => ({ label: item.label, value: item.value })),
  },
  {
    type: 'select',
    prop: 'classEnum',
    label: '类别',
    placeholder: '请选择类别',
    clearable: true,
    multiple: false,
    options: departmentClassOptions.value.map((item) => ({ label: item.label, value: item.value })),
  },
  {
    type: 'select',
    prop: 'statusEnum',
    label: '状态',
    placeholder: '请选择状态',
    clearable: true,
    multiple: false,
    options: [
      { label: '启用', value: PublicationStatusCode.ACTIVE },
      { label: '停用', value: PublicationStatusCode.INACTIVE },
    ],
  },
]);

const selectedRows = ref<Department[]>([]);
const dialogVisible = ref(false);
const dialogFormData = ref<Partial<Department>>({});

function countTreeNodes(items: Department[] | undefined): number {
  if (!items || items.length === 0) {
    return 0;
  }
  let count = 0;
  for (const item of items) {
    count++;
    count += countTreeNodes(item.children);
  }
  return count;
}

function buildTreeFromFlat(items: Department[]): Department[] {
  const nodeMap = new Map<string, Department>();
  for (const item of items) {
    if (item.id !== undefined && item.id !== null) {
      nodeMap.set(String(item.id), { ...item, children: [] });
    }
  }

  const roots: Department[] = [];
  for (const item of items) {
    const node =
      item.id !== undefined && item.id !== null ? nodeMap.get(String(item.id)) : undefined;
    if (!node) {
      continue;
    }
    const parentKey =
      item.parentId === undefined || item.parentId === null ? '' : String(item.parentId);
    const parentNode = parentKey ? nodeMap.get(parentKey) : undefined;
    if (parentNode) {
      if (!parentNode.children) {
        parentNode.children = [];
      }
      parentNode.children.push(node);
    } else {
      roots.push(node);
    }
  }
  return roots;
}

function normalizeOptionalFilter(value: unknown): string | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  const raw = Array.isArray(value) ? value[0] : value;
  if (raw === undefined || raw === null || raw === '') {
    return undefined;
  }
  return String(raw);
}

/** 组装列表查询参数，传给后端 getDepartmentList */
function buildListQueryParams(): QueryDepartmentListParams {
  const params: QueryDepartmentListParams = {};
  const keyword = queryParams.keyword?.trim();
  if (keyword) {
    params.keyword = keyword;
  }
  const typeEnum = normalizeOptionalFilter(queryParams.typeEnum);
  if (typeEnum) {
    params.typeEnum = typeEnum;
  }
  const classEnum = normalizeOptionalFilter(queryParams.classEnum);
  if (classEnum) {
    params.classEnum = classEnum;
  }
  const statusEnum = normalizeOptionalFilter(queryParams.statusEnum);
  if (statusEnum) {
    params.statusEnum = statusEnum;
  }
  return params;
}

function syncQueryParams(params?: {
  classEnum?: string;
  keyword?: string;
  statusEnum?: string;
  typeEnum?: string;
}) {
  if (!params) {
    return;
  }
  if (params.keyword !== undefined) {
    queryParams.keyword = params.keyword || '';
  }
  queryParams.typeEnum = normalizeOptionalFilter(params.typeEnum);
  queryParams.classEnum = normalizeOptionalFilter(params.classEnum);
  queryParams.statusEnum = normalizeOptionalFilter(params.statusEnum);
}

const loadAllData = async () => {
  loading.value = true;
  try {
    const response = await pageDepartmentApi(buildListQueryParams());
    const list = unwrapDepartmentListResponse(response);
    // 后端已返回 children 时直接使用；否则回退到前端按 parentId 组树。
    const hasTreeChildren = list.some(
      (item) => Array.isArray(item.children) && item.children.length > 0,
    );
    const roots = hasTreeChildren ? list : buildTreeFromFlat(list);

    const sortTreeData = (items: Department[]): Department[] => {
      const sorted = [...items].toSorted((a, b) => {
        const aOrder = a.displayOrder;
        const bOrder = b.displayOrder;
        const aHasOrder = aOrder !== undefined && aOrder !== null;
        const bHasOrder = bOrder !== undefined && bOrder !== null;
        if (aHasOrder && bHasOrder) {
          return aOrder - bOrder;
        }
        if (aHasOrder) {
          return -1;
        }
        if (bHasOrder) {
          return 1;
        }
        const aId = a.id == null ? 0 : Number(a.id);
        const bId = b.id == null ? 0 : Number(b.id);
        return aId - bId;
      });
      for (const item of sorted) {
        if (item.children && item.children.length > 0) {
          item.children = sortTreeData(item.children);
        }
      }
      return sorted;
    };

    tableData.value = sortTreeData(roots);
    total.value = countTreeNodes(tableData.value);
  } catch (error) {
    console.error('加载数据失败:', error);
    ElMessage.error('加载数据失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

async function loadInitOptions() {
  try {
    const response = await initDepartmentApi();
    const responseData = response as InitDepartmentResponse | { data?: InitDepartmentResponse };
    const data = 'data' in responseData ? responseData.data : responseData;

    // init 下拉 OptionDto：sort=旧版枚举值，value=领域 code；表单绑定 sort 与详情/列表 typeEnum/classEnum 对齐。
    if (data?.departmentClassOptions) {
      departmentClassOptions.value = data.departmentClassOptions.map((item) => ({
        label: item.label,
        value: item.sort == null ? String(item.value) : String(item.sort),
        sort: item.sort,
      }));
    }
    if (data?.departmentTypeOptions) {
      departmentTypeOptions.value = data.departmentTypeOptions.map((item) => ({
        label: item.label,
        value: item.sort == null ? String(item.value) : String(item.sort),
        sort: item.sort,
      }));
    }
    if (data?.departmentOptions) {
      const processTreeOptions = (items: DepartmentOption[]): DepartmentOption[] =>
        items.map((item) => ({
          label: item.label,
          value: item.value,
          sort: item.sort,
          children:
            item.children && item.children.length > 0
              ? processTreeOptions(item.children)
              : item.children === null
                ? null
                : undefined,
        }));
      departmentOptions.value = processTreeOptions(data.departmentOptions);
    }
    if (data?.practitionerOptions) {
      practitionerOptions.value = data.practitionerOptions.map((item) => ({
        label: item.label,
        value: String(item.value),
      }));
    }
  } catch (error) {
    console.error('加载组织选项失败:', error);
  }
}

/** 搜索：同步表单条件并请求列表接口 */
function handleQuery(params?: {
  classEnum?: string;
  keyword?: string;
  statusEnum?: string;
  typeEnum?: string;
}) {
  syncQueryParams(params);
  void loadAllData();
}

/** 重置：清空条件并重新请求列表接口 */
function handleReset() {
  Object.assign(queryParams, initQueryParams);
  void loadAllData();
}

const handleSelectionChange = (selection: Department[] | Record<string, unknown>[]) => {
  selectedRows.value = selection.filter(isDepartmentRow);
};

const handleAdd = () => {
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleEdit = (row: Department) => {
  dialogFormData.value = { ...row };
  dialogVisible.value = true;
};

const handleDialogSuccess = async () => {
  await loadInitOptions();
  await loadAllData();
};

/** 删除科室（作废并软删对应组织单元） */
const handleDelete = async (row: Department) => {
  if (!row.id) {
    ElMessage.warning('缺少科室ID');
    return;
  }
  const deptName = row.name || '该科室';
  try {
    await ElMessageBox.confirm(`确定要删除「${deptName}」吗？删除后无法恢复！`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await deleteDepartmentApi(row.id);
    ElMessage.success('删除成功');
    await loadAllData();
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    }
  }
};

const handleStart = async (row: Department) => {
  if (!row.id) {
    ElMessage.warning('缺少组织ID');
    return;
  }
  try {
    await activeDepartmentApi([String(row.id)]);
    ElMessage.success('启用成功');
    loadAllData();
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: Department) => {
  if (!row.id) {
    ElMessage.warning('缺少组织ID');
    return;
  }
  try {
    await inactiveDepartmentApi([String(row.id)]);
    ElMessage.success('停用成功');
    loadAllData();
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleBatchStart = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要启用的组织');
    return;
  }
  const ids = selectedRows.value
    .filter((row) => row.statusEnum !== PublicationStatusCode.ACTIVE && row.id)
    .map((row) => String(row.id));
  if (ids.length === 0) {
    ElMessage.warning('所选组织中已全部启用，无需重复操作');
    return;
  }
  try {
    await activeDepartmentApi(ids);
    ElMessage.success(`成功启用 ${ids.length} 个组织`);
    loadAllData();
    selectedRows.value = [];
  } catch (error) {
    console.error('批量启用失败:', error);
    ElMessage.error('批量启用失败，请稍后重试');
  }
};

const handleBatchStop = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要停用的组织');
    return;
  }
  const ids = selectedRows.value
    .filter((row) => row.statusEnum === PublicationStatusCode.ACTIVE && row.id)
    .map((row) => String(row.id));
  if (ids.length === 0) {
    ElMessage.warning('所选组织中已全部停用，无需重复操作');
    return;
  }
  try {
    await inactiveDepartmentApi(ids);
    ElMessage.success(`成功停用 ${ids.length} 个组织`);
    loadAllData();
    selectedRows.value = [];
  } catch (error) {
    console.error('批量停用失败:', error);
    ElMessage.error('批量停用失败，请稍后重试');
  }
};

const initPage = async () => {
  await loadInitOptions();
  await loadAllData();
};

onMounted(() => {
  initPage();
});

onActivated(() => {
  const needRefresh = sessionStorage.getItem('departmentRefresh');
  if (needRefresh === 'true') {
    sessionStorage.removeItem('departmentRefresh');
    loadAllData();
  }
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
      :max-height="BASIC_TABLE_MAX_HEIGHT"
      :show-side-query="false"
      :show-top-query="true"
      :show-pagination="false"
      :tree="true"
      :tree-config="{
        children: 'children',
        indent: 20,
        line: false,
        expandAll: true,
        accordion: false,
        trigger: 'default',
      }"
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton type="success" @click="handleBatchStart">批量启用</ElButton>
        <ElButton type="warning" @click="handleBatchStop">批量停用</ElButton>
      </template>

      <template #statusEnum="{ row }">
        <ElTag :type="row.statusEnum === PublicationStatusCode.ACTIVE ? 'success' : 'info'">
          {{ row.statusEnum_dictText || row.statusEnum }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click.stop="handleEdit(row)">编辑</ElButton>
        <ElButton
          v-if="row.statusEnum === PublicationStatusCode.ACTIVE"
          type="warning"
          link
          @click.stop="handleStop(row)"
        >
          停用
        </ElButton>
        <ElButton v-else type="success" link @click.stop="handleStart(row)">启用</ElButton>
        <ElButton type="danger" link @click.stop="handleDelete(row)">删除</ElButton>
      </template>
    </TableLayout>

    <EditDialog
      v-model:visible="dialogVisible"
      :form-data="dialogFormData"
      :department-class-options="departmentClassOptions"
      :department-type-options="departmentTypeOptions"
      :department-options="departmentOptions"
      :practitioner-options="practitionerOptions"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
