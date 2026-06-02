<script lang="ts" setup>
import type { FormItemConfig, TableColumn, TreeNodeData } from '@vben-core/table-layout-ui';

import type { MedicationCatalogInitResponse, MedicationCatalogItem } from '#/api';

import { computed, onActivated, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElTag } from 'element-plus';

import {
  medicationCatalogInitApi,
  pageMedicationCatalogApi,
  startMedicationCatalogApi,
  stopMedicationCatalogApi,
} from '#/api';
import { PublicationStatusCode } from '#/constants/domain/publication-status';

import MedicationCatalogEditDialog from './medication-catalog-edit-dialog.vue';

function isMedicationPublicationActive(row: MedicationCatalogItem): boolean {
  return row.statusEnum === PublicationStatusCode.ACTIVE;
}

/** 后端启用接口仅允许 RETIRED；INACTIVE 视为同类停用态 */
function isMedicationPublicationRetired(row: MedicationCatalogItem): boolean {
  return (
    row.statusEnum === PublicationStatusCode.RETIRED ||
    row.statusEnum === PublicationStatusCode.INACTIVE
  );
}

const queryParams = ref({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  name: '',
  categoryCode: '',
});

const tableData = ref<MedicationCatalogItem[]>([]);

const loading = ref(false);
const total = ref(0);
const sideQueryLoading = ref(false);
const initOptions = ref<MedicationCatalogInitResponse>({} as MedicationCatalogInitResponse);

const catalogEditOpen = ref(false);
const catalogEditMedId = ref<string>('');

const convertOptionsToTree = (
  options:
    | Array<{
        children?: unknown[];
        info?: string;
        label?: string;
        name?: string;
        text?: string;
        value: number | string;
      }>
    | null
    | undefined,
): TreeNodeData[] => {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  return options.map((item) => ({
    id: item.value,
    label: item.label || item.info || item.text || item.name || String(item.value),
    value: item.value,
    children: item.children ? convertOptionsToTree(item.children as typeof options) : undefined,
  }));
};

// 药品分类树形数据
const siderData = computed(() => {
  const options = initOptions.value?.medicationCategoryCodeOptions as
    | Array<{
        children?: unknown[];
        info?: string;
        label?: string;
        name?: string;
        text?: string;
        value: number | string;
      }>
    | null
    | undefined;
  return convertOptionsToTree(options);
});

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
    prop: 'busNo',
    label: '药品编号',
    width: 180,
  },
  {
    prop: 'name',
    label: '药品名称',
    slot: 'name',
    width: 200,
  },
  {
    prop: 'specification',
    label: '规格',
    width: 80,
  },
  {
    prop: 'statusEnum_enumText',
    label: '药品状态',
    slot: 'statusEnum',
    width: 80,
    align: 'center',
  },
  {
    prop: 'categoryCode_dictText',
    width: 110,
    label: '药品分类',
  },
  {
    prop: 'ybNo',
    width: 110,
    label: '医保编码',
  },
  {
    prop: 'ybMatchFlag',
    label: '医保是否对码',
    slot: 'ybMatchFlag',
    width: 110,
    align: 'center',
  },
  {
    prop: 'nationalDrugCode',
    label: '69码',
    width: 130,
  },
  {
    prop: 'purchasePrice',
    label: '采购价',
    width: 110,
  },
  {
    prop: 'retailPrice',
    label: '售价',
    width: 110,
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 220,
  },
];

const formItems: FormItemConfig[] = [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入药品名称或编码',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'name',
    label: '药品名称',
    placeholder: '请输入药品名称',
    clearable: true,
  },
];

const handleQuery = async (params: Record<string, unknown>) => {
  loading.value = true;
  try {
    const response = await pageMedicationCatalogApi({
      pageNo: typeof params.pageNo === 'number' ? params.pageNo : queryParams.value.pageNo,
      pageSize: typeof params.pageSize === 'number' ? params.pageSize : queryParams.value.pageSize,
      keyword:
        typeof params.keyword === 'string' && params.keyword
          ? params.keyword
          : queryParams.value.keyword || undefined,
      name:
        typeof params.name === 'string' && params.name
          ? params.name
          : queryParams.value.name || undefined,
      categoryCode:
        typeof params.categoryCode === 'string' && params.categoryCode
          ? params.categoryCode
          : queryParams.value.categoryCode || undefined,
    });

    const responseData = response.data;
    if (responseData) {
      tableData.value = responseData.content || [];
      total.value = responseData.totalElements || 0;
    } else {
      tableData.value = [];
      total.value = 0;
    }
  } catch (error) {
    console.error('查询失败:', error);
    ElMessage.error('查询失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    keyword: '',
    name: '',
    categoryCode: '',
  };
  handleQuery(queryParams.value);
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  handleQuery(queryParams.value);
};

const selectedRows = ref<MedicationCatalogItem[]>([]);

const handleSelectionChange = (selection: MedicationCatalogItem[]) => {
  selectedRows.value = selection;
};

// 处理侧边筛选器选择
const handleSideQuery = (node: TreeNodeData) => {
  queryParams.value.categoryCode = node.id === '__ALL__' ? '' : node.value || node.id;
  queryParams.value.pageNo = 1;
  handleQuery(queryParams.value);
};

// 刷新侧边树形组件数据
const handleRefreshSideQuery = async () => {
  sideQueryLoading.value = true;
  try {
    const response = await medicationCatalogInitApi();
    const data: MedicationCatalogInitResponse =
      response.data || ({} as MedicationCatalogInitResponse);
    if (data) {
      initOptions.value = data;
    }
  } catch (error) {
    console.error('刷新树形数据失败:', error);
    ElMessage.error('刷新失败，请稍后重试');
  } finally {
    sideQueryLoading.value = false;
  }
};

/** 打开新增/编辑弹窗（不走路由） */
function openCatalogEditor(medicationDefId: string) {
  if (medicationDefId !== 'new' && !medicationDefId) {
    ElMessage.warning('缺少药品ID');
    return;
  }
  catalogEditMedId.value = medicationDefId;
  catalogEditOpen.value = true;
}

const handleAdd = () => {
  openCatalogEditor('new');
};

const handleRowClick = (row: MedicationCatalogItem) => {
  openCatalogEditor(String(row.medicationDefId || ''));
};

/** 弹窗保存成功后刷新当前表格 */
function handleCatalogFormSuccess() {
  handleQuery(queryParams.value);
}

const handleStart = async (row: MedicationCatalogItem) => {
  if (!row.medicationDefId) {
    ElMessage.warning('缺少药品ID');
    return;
  }
  try {
    await startMedicationCatalogApi([String(row.medicationDefId)]);
    ElMessage.success('启用成功');
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: MedicationCatalogItem) => {
  if (!row.medicationDefId) {
    ElMessage.warning('缺少药品ID');
    return;
  }
  try {
    await stopMedicationCatalogApi([String(row.medicationDefId)]);
    ElMessage.success('停用成功');
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleBatchStart = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要启用的药品');
    return;
  }

  const ids = selectedRows.value
    .filter((row) => isMedicationPublicationRetired(row) && row.medicationDefId)
    .map((row) => String(row.medicationDefId));

  if (ids.length === 0) {
    ElMessage.warning('所选药品中没有处于「停用」状态的可启用项');
    return;
  }

  try {
    await startMedicationCatalogApi(ids);
    ElMessage.success(`成功启用 ${ids.length} 个药品`);
    handleQuery(queryParams.value);
    selectedRows.value = [];
  } catch (error) {
    console.error('批量启用失败:', error);
    ElMessage.error('批量启用失败，请稍后重试');
  }
};

const handleBatchStop = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先选择要停用的药品');
    return;
  }

  const ids = selectedRows.value
    .filter((row) => isMedicationPublicationActive(row) && row.medicationDefId)
    .map((row) => String(row.medicationDefId));

  if (ids.length === 0) {
    ElMessage.warning('所选药品中没有处于「已启用」状态的可停用项');
    return;
  }

  try {
    await stopMedicationCatalogApi(ids);
    ElMessage.success(`成功停用 ${ids.length} 个药品`);
    handleQuery(queryParams.value);
    selectedRows.value = [];
  } catch (error) {
    console.error('批量停用失败:', error);
    ElMessage.error('批量停用失败，请稍后重试');
  }
};

const getStatusTagType = (statusEnum: unknown): 'danger' | 'info' | 'success' | 'warning' => {
  if (statusEnum === PublicationStatusCode.ACTIVE) {
    return 'success';
  }
  if (
    statusEnum === PublicationStatusCode.RETIRED ||
    statusEnum === PublicationStatusCode.INACTIVE
  ) {
    return 'warning';
  }
  if (statusEnum === PublicationStatusCode.DISCARDED) {
    return 'danger';
  }
  return 'info';
};

/** 布尔或与后端历史 0/1、'1' 兼容 */
function isTruthyFlag(value: unknown): boolean {
  return value === true || value === 1 || value === '1';
}

const getYbMatchFlagTagType = (value: unknown): 'success' | 'warning' => {
  return isTruthyFlag(value) ? 'success' : 'warning';
};

const initPage = async () => {
  try {
    const response = await medicationCatalogInitApi();
    const data: MedicationCatalogInitResponse =
      response.data || ({} as MedicationCatalogInitResponse);
    if (data) {
      initOptions.value = data;
    }
    handleQuery(queryParams.value);
  } catch (error) {
    console.error('初始化失败:', error);
  }
};

onActivated(() => {
  const needRefresh = sessionStorage.getItem('medicationCatalogRefresh');
  if (needRefresh === 'true') {
    sessionStorage.removeItem('medicationCatalogRefresh');
    handleQuery(queryParams.value);
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
      :show-top-query="true"
      :show-side-query="true"
      :show-pagination="true"
      :sider-data="siderData"
      :side-query-loading="sideQueryLoading"
      row-key="medicationDefId"
      border
      stripe
      size="default"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
      @side-query="handleSideQuery"
      @refresh-side-query="handleRefreshSideQuery"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton type="success" @click="handleBatchStart">批量启用</ElButton>
        <ElButton type="warning" @click="handleBatchStop">批量停用</ElButton>
      </template>

      <template #name="{ row }">
        <a
          href="javascript:void(0)"
          class="text-primary cursor-pointer hover:underline"
          @click="handleRowClick(row)"
        >
          {{ row.name }}
        </a>
      </template>

      <template #statusEnum="{ row }">
        <ElTag
          :type="getStatusTagType(row.statusEnum)"
          :effect="row.statusEnum === PublicationStatusCode.DISCARDED ? 'plain' : 'dark'"
        >
          {{ row.statusEnum_enumText || '未知' }}
        </ElTag>
      </template>

      <template #ybMatchFlag="{ row }">
        <ElTag :type="getYbMatchFlagTagType(row.ybMatchFlag)" effect="dark">
          {{ isTruthyFlag(row.ybMatchFlag) ? '是' : '否' }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click="handleRowClick(row)"> 编辑 </ElButton>
        <ElButton
          v-if="isMedicationPublicationActive(row)"
          type="warning"
          link
          @click="handleStop(row)"
        >
          停用
        </ElButton>
        <ElButton
          v-else-if="isMedicationPublicationRetired(row)"
          type="success"
          link
          @click="handleStart(row)"
        >
          启用
        </ElButton>
        <ElButton type="danger" link @click="() => ElMessage.warning(`删除 ${row.name || row.id}`)">
          删除
        </ElButton>
      </template>
    </TableLayout>

    <MedicationCatalogEditDialog
      v-model="catalogEditOpen"
      :medication-def-id="catalogEditMedId"
      :init-options-source="initOptions"
      @success="handleCatalogFormSuccess"
    />
  </Page>
</template>
