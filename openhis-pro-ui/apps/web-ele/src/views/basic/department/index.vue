<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';
import type {
  Department,
  DepartmentClassOption,
  DepartmentOption,
  DepartmentTypeOption,
  InitDepartmentResponse,
  PractitionerOption,
} from '#/api';

import { computed, onActivated, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { TableLayout } from '@vben-core/table-layout-ui';
import { ElButton, ElMessage, ElTag } from 'element-plus';

import {
  activeDepartmentApi,
  inactiveDepartmentApi,
  initDepartmentApi,
  pageDepartmentApi,
} from '#/api';

import EditDialog from './edit-dialog.vue';

interface DepartmentApiResponse {
  data?: Department[];
}

type DepartmentStatusFlag = '1' | '2' | undefined;

interface DepartmentViewModel extends Department {
  activeFlag?: DepartmentStatusFlag;
  displayName?: string;
  isEnabled?: boolean;
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

function toDepartmentStatusFlag(activeFlag: Department['activeFlag']): DepartmentStatusFlag {
  if (activeFlag === '1' || activeFlag === 1) {
    return '1';
  }
  if (activeFlag === '2' || activeFlag === 2) {
    return '2';
  }
  return undefined;
}

const queryParams = ref({
  keyword: '',
  classEnum: undefined as number | undefined,
  typeEnum: undefined as number | string | undefined,
  activeFlag: undefined as string | undefined,
});

const allData = ref<Department[]>([]);
const tableData = ref<Department[]>([]);
const loading = ref(false);
const total = ref(0);
const departmentClassOptions = ref<DepartmentClassOption[]>([]);
const departmentTypeOptions = ref<DepartmentTypeOption[]>([]);
const departmentOptions = ref<DepartmentOption[]>([]);
const practitionerOptions = ref<PractitionerOption[]>([]);

const tableColumns: TableColumn[] = [
  { type: 'selection', fixed: 'left', width: 50, align: 'center' },
  { type: 'index', label: '序号' },
  { prop: 'name', label: '科室名称', treeNode: true, width: 200 },
  { prop: 'busNo', label: '科室编号', width: 180 },
  { prop: 'typeEnum_dictText', label: '类型' },
  { prop: 'classEnum_dictText', label: '类别' },
  { prop: 'ybNo', label: '医保编码' },
  { prop: 'ybName', label: '医保名称' },
  { prop: 'medinsId', label: '医保机构标识', width: 180 },
  { prop: 'medinsAdmdvs', label: '医保区划', width: 120 },
  { prop: 'medinsType', label: '医保机构类型', width: 160 },
  { prop: 'medinsLv', label: '医保机构等级', width: 140 },
  { prop: 'pyStr', label: '拼音', width: 120 },
  { prop: 'wbStr', label: '五笔', width: 120 },
  { prop: 'defDoctorId', label: '默认医师ID', width: 130 },
  { prop: 'caty', label: '科别', width: 120 },
  { prop: 'displayOrder', label: '显示顺序', width: 110 },
  { prop: 'activeFlag_dictText', label: '状态', slot: 'activeFlag', width: 100, align: 'center' },
  { prop: 'action', label: '操作', fixed: 'right', slot: 'action', width: 250 },
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
    prop: 'activeFlag',
    label: '状态',
    placeholder: '请选择状态',
    clearable: true,
    multiple: false,
    options: [
      { label: '有效', value: '1' },
      { label: '无效', value: '2' },
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

function buildDepartmentOptionsFromTree(items: Department[]): DepartmentOption[] {
  return items.map((item, index) => {
    const children =
      item.children && item.children.length > 0
        ? buildDepartmentOptionsFromTree(item.children)
        : undefined;
    return {
      label: item.name || `组织-${item.id}`,
      value: String(item.id),
      sort: item.displayOrder ?? index + 1,
      children,
    };
  });
}

// 统一标准化后端树节点：补齐 displayName/isEnabled/sortOrder 的兼容字段映射。
function normalizeDepartmentTree(items: Department[]): Department[] {
  return items.map((item) => {
    const itemView = item as DepartmentViewModel;
    const rawChildren = Array.isArray(item.children) ? item.children : [];
    const normalizedChildren = normalizeDepartmentTree(rawChildren);
    const processedItem: DepartmentViewModel = {
      ...item,
      name: itemView.displayName || item.name,
      children: normalizedChildren,
    };
    if (processedItem.displayOrder === undefined || processedItem.displayOrder === null) {
      const sortOrder = item.sortOrder;
      if (sortOrder !== undefined && sortOrder !== null) {
        processedItem.displayOrder = sortOrder;
      }
    }
    if (itemView.isEnabled !== undefined && itemView.isEnabled !== null) {
      const enabled = Boolean(itemView.isEnabled);
      processedItem.activeFlag = enabled ? '1' : '2';
      processedItem.activeFlag_dictText = enabled ? '有效' : '无效';
    }
    return processedItem;
  });
}

// 兼容老接口仅返回平铺列表时，按 parentId 组装树。
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

const filterTreeData = (
  items: Department[],
  filters: {
    activeFlag?: string;
    classEnum?: number;
    keyword?: string;
    typeEnum?: number | string;
  },
): Department[] => {
  const { keyword, typeEnum, classEnum, activeFlag } = filters;
  const searchKeyLower = keyword?.toLowerCase().trim() || '';
  const result: Department[] = [];

  for (const item of items) {
    const filteredChildren =
      item.children && item.children.length > 0 ? filterTreeData(item.children, filters) : [];

    const matchesSearchKey =
      !searchKeyLower ||
      item.name?.toLowerCase().includes(searchKeyLower) ||
      item.ybNo?.toLowerCase().includes(searchKeyLower) ||
      item.ybName?.toLowerCase().includes(searchKeyLower) ||
      item.medinsId?.toLowerCase().includes(searchKeyLower) ||
      item.medinsAdmdvs?.toLowerCase().includes(searchKeyLower) ||
      item.medinsType?.toLowerCase().includes(searchKeyLower) ||
      item.medinsLv?.toLowerCase().includes(searchKeyLower) ||
      item.pyStr?.toLowerCase().includes(searchKeyLower) ||
      item.wbStr?.toLowerCase().includes(searchKeyLower) ||
      String(item.defDoctorId ?? '')
        .toLowerCase()
        .includes(searchKeyLower) ||
      item.caty?.toLowerCase().includes(searchKeyLower);

    const matchesTypeEnum =
      typeEnum === undefined ||
      typeEnum === null ||
      String(item.typeEnum ?? '') === String(typeEnum);
    const matchesClassEnum = classEnum === undefined || item.classEnum === classEnum;
    const matchesActiveFlag =
      activeFlag === undefined || toDepartmentStatusFlag(item.activeFlag) === activeFlag;
    const matchesAll = matchesSearchKey && matchesTypeEnum && matchesClassEnum && matchesActiveFlag;

    if (matchesAll || filteredChildren.length > 0) {
      result.push({
        ...item,
        children: filteredChildren,
      });
    }
  }
  return result;
};

const applyFilters = () => {
  const filters = {
    keyword: queryParams.value.keyword,
    typeEnum: queryParams.value.typeEnum,
    classEnum: queryParams.value.classEnum,
    activeFlag: queryParams.value.activeFlag,
  };
  tableData.value = filterTreeData(allData.value, filters);
  total.value = countTreeNodes(tableData.value);
};

const loadAllData = async () => {
  loading.value = true;
  try {
    const response = await pageDepartmentApi();
    const list = unwrapDepartmentListResponse(response);
    const normalized = normalizeDepartmentTree(list || []);
    // 后端已返回 children 时直接使用；否则回退到前端按 parentId 组树。
    const hasTreeChildren = normalized.some(
      (item) => Array.isArray(item.children) && item.children.length > 0,
    );
    const roots = hasTreeChildren ? normalized : buildTreeFromFlat(normalized);

    const sortTreeData = (items: Department[]): Department[] => {
      const sorted = [...items].toSorted((a, b) => {
        const aOrder = a.displayOrder;
        const bOrder = b.displayOrder;
        if (aOrder !== undefined && aOrder !== null && bOrder !== undefined && bOrder !== null) {
          return aOrder - bOrder;
        }
        if (aOrder !== undefined && aOrder !== null) {
          return -1;
        }
        if (bOrder !== undefined && bOrder !== null) {
          return 1;
        }
        return 0;
      });
      for (const item of sorted) {
        if (item.children && item.children.length > 0) {
          item.children = sortTreeData(item.children);
        }
      }
      return sorted;
    };

    allData.value = sortTreeData(roots);
    // 兜底：即使 init 未返回 departmentOptions，也保证上级组织下拉有数据。
    departmentOptions.value = buildDepartmentOptionsFromTree(allData.value);
    applyFilters();
  } catch (error) {
    console.error('加载数据失败:', error);
    ElMessage.error('加载数据失败，请稍后重试');
    allData.value = [];
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

    if (data?.departmentClassOptions) {
      departmentClassOptions.value = data.departmentClassOptions.map((item) => ({
        label: item.label,
        value: item.value,
      }));
    }
    if (data?.departmentTypeOptions) {
      departmentTypeOptions.value = data.departmentTypeOptions.map((item) => ({
        label: item.label,
        value: item.value,
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
        value: item.value,
      }));
    }
  } catch (error) {
    console.error('加载组织选项失败:', error);
  }
}

const handleQuery = (params: {
  activeFlag?: string;
  classEnum?: string;
  keyword?: string;
  typeEnum?: string;
}) => {
  if (params.keyword !== undefined) {
    queryParams.value.keyword = params.keyword || '';
  }
  if (params.typeEnum !== undefined) {
    queryParams.value.typeEnum = Array.isArray(params.typeEnum)
      ? params.typeEnum[0]
      : params.typeEnum;
  }
  if (params.classEnum !== undefined) {
    queryParams.value.classEnum = Array.isArray(params.classEnum)
      ? params.classEnum[0]
      : params.classEnum;
  }
  if (params.activeFlag !== undefined) {
    queryParams.value.activeFlag = Array.isArray(params.activeFlag)
      ? params.activeFlag[0]
      : params.activeFlag;
  }
  applyFilters();
};

const handleReset = () => {
  queryParams.value = {
    keyword: '',
    classEnum: undefined,
    typeEnum: undefined,
    activeFlag: undefined,
  };
  applyFilters();
};

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

const handleDelete = async (_row: Department) => {
  ElMessage.warning('删除功能待实现');
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
    .filter((row) => toDepartmentStatusFlag(row.activeFlag) !== '1' && row.id)
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
    .filter((row) => toDepartmentStatusFlag(row.activeFlag) === '1' && row.id)
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

onActivated(() => {
  const needRefresh = sessionStorage.getItem('departmentRefresh');
  if (needRefresh === 'true') {
    sessionStorage.removeItem('departmentRefresh');
    loadAllData();
  }
});

initPage();
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
      :show-top-query="true"
      :show-pagination="false"
      :is-all-data="true"
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

      <template #activeFlag="{ row }">
        <ElTag :type="row.activeFlag === '1' ? 'success' : 'info'">
          {{ row.activeFlag_dictText || (row.activeFlag === '1' ? '有效' : '无效') }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click.stop="handleEdit(row)">编辑</ElButton>
        <ElButton v-if="row.activeFlag === '1'" type="warning" link @click.stop="handleStop(row)">
          停用
        </ElButton>
        <ElButton
          v-else-if="row.activeFlag !== '1'"
          type="success"
          link
          @click.stop="handleStart(row)"
        >
          启用
        </ElButton>
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
