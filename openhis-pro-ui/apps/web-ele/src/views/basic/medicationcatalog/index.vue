<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { MedicationCatalogInitResponse, MedicationCatalogItem } from '#/api';

import { computed, onActivated, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import {
  deleteMedicationCatalogApi,
  medicationCatalogInitApi,
  pageMedicationCatalogApi,
  startMedicationCatalogApi,
  stopMedicationCatalogApi,
} from '#/api';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { formatMoneyDisplay } from '#/utils/format/money-scale';

import MedicationCatalogEditDialog from './medication-catalog-edit-dialog.vue';

function isMedicationPublicationActive(row: MedicationCatalogItem): boolean {
  return row.statusEnum === PublicationStatusCode.ACTIVE;
}

/** 后端启用接口仅允许 INACTIVE 停用态 */
function isMedicationPublicationRetired(row: MedicationCatalogItem): boolean {
  return row.statusEnum === PublicationStatusCode.INACTIVE;
}

const queryParams = ref({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  name: '',
  categoryCode: '',
});

const tableData = ref<MedicationCatalogItem[]>([]);

const loading = ref(false);
const total = ref(0);
const initOptions = ref<MedicationCatalogInitResponse>({} as MedicationCatalogInitResponse);

const catalogEditOpen = ref(false);
const catalogEditMedId = ref<string>('');

interface CategoryOptionItem {
  children?: CategoryOptionItem[];
  info?: string;
  label?: string;
  name?: string;
  text?: string;
  value: number | string;
}

/** 将树形分类选项展平为下拉框选项（保留层级缩进） */
function flattenCategorySelectOptions(
  options: CategoryOptionItem[] | null | undefined,
  depth = 0,
): Array<{ label: string; value: string }> {
  if (!options || !Array.isArray(options)) {
    return [];
  }
  const prefix = depth > 0 ? `${'　'.repeat(depth)}` : '';
  const result: Array<{ label: string; value: string }> = [];
  for (const item of options) {
    const label = item.label || item.info || item.text || item.name || String(item.value);
    result.push({ label: `${prefix}${label}`, value: String(item.value) });
    if (item.children?.length) {
      result.push(...flattenCategorySelectOptions(item.children, depth + 1));
    }
  }
  return result;
}

const categoryCodeOptions = computed(() =>
  flattenCategorySelectOptions(
    initOptions.value?.medicationCategoryCodeOptions as CategoryOptionItem[] | undefined,
  ),
);

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
    width: 60,
    align: 'center',
  },
  {
    prop: 'busNo',
    label: '编码',
    width: 150,
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '项目名',
    slot: 'name',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'specification',
    label: '规格',
    width: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnum',
    label: '状态',
    slot: 'statusEnum',
    width: 90,
    align: 'center',
  },
  {
    prop: 'categoryCode_dictText',
    label: '分类',
    width: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'manufacturerText',
    label: '生产厂家',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'retailPrice',
    label: '价格',
    width: 120,
    align: 'right',
    headerAlign: 'right',
    slot: 'retailPrice',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 200,
    align: 'center',
    headerAlign: 'center',
  },
];

function formatRetailPriceWithUnit(price: unknown, unitText: unknown): string {
  if (price === null || price === undefined || price === '') {
    return '';
  }
  const num = Number(price);
  const formatted = Number.isFinite(num) ? formatMoneyDisplay(num) : String(price);
  const unit = typeof unitText === 'string' && unitText.trim() ? unitText.trim() : '';
  return unit ? `${formatted}/${unit}` : formatted;
}

const formItems = computed<FormItemConfig[]>(() => [
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
  {
    type: 'select',
    prop: 'categoryCode',
    label: '药品分类',
    placeholder: '请选择药品分类',
    clearable: true,
    filterable: true,
    options: categoryCodeOptions.value,
  },
]);

const handleQuery = async (params: Record<string, unknown>) => {
  queryParams.value = {
    ...queryParams.value,
    ...params,
  };
  loading.value = true;
  try {
    const response = await pageMedicationCatalogApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      keyword: queryParams.value.keyword || undefined,
      name: queryParams.value.name || undefined,
      categoryCode: queryParams.value.categoryCode || undefined,
    });

    const parsed = parsePagedRowsFromApiData<MedicationCatalogItem>(response.data);
    tableData.value = parsed.content;
    total.value = parsed.totalCount;
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
    pageSize: 10,
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

const doDeleteByIds = async (ids: string[]) => {
  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的药品');
    return;
  }
  await ElMessageBox.confirm(`确定要删除选中的 ${ids.length} 条药品吗？删除后无法恢复！`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await deleteMedicationCatalogApi(ids);
  ElMessage.success('删除成功');
  await handleQuery(queryParams.value);
  selectedRows.value = [];
};

const handleDelete = async () => {
  const ids = selectedRows.value
    .map((row) => row.medicationDefId)
    .filter(Boolean)
    .map(String);
  try {
    await doDeleteByIds(ids);
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    }
  }
};

const handleRowDelete = async (row: MedicationCatalogItem) => {
  if (!row.medicationDefId) {
    ElMessage.warning('缺少药品ID');
    return;
  }
  try {
    await doDeleteByIds([String(row.medicationDefId)]);
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    }
  }
};

const getStatusTagType = (statusEnum: unknown): 'danger' | 'info' | 'success' | 'warning' => {
  if (statusEnum === PublicationStatusCode.ACTIVE) {
    return 'success';
  }
  if (statusEnum === PublicationStatusCode.INACTIVE) {
    return 'warning';
  }
  if (statusEnum === PublicationStatusCode.DISCARDED) {
    return 'danger';
  }
  return 'info';
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
      :show-pagination="true"
      row-key="medicationDefId"
      border
      stripe
      size="default"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton type="success" @click="handleBatchStart">批量启用</ElButton>
        <ElButton type="warning" @click="handleBatchStop">批量停用</ElButton>
        <ElButton type="danger" @click="handleDelete">删除</ElButton>
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

      <template #retailPrice="{ row }">
        {{ formatRetailPriceWithUnit(row.retailPrice, row.packageUnitCode_dictText) }}
      </template>

      <template #action="{ row }">
        <div class="flex items-center justify-center gap-1">
          <ElButton type="primary" link @click="handleRowClick(row)">编辑</ElButton>
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
          <ElButton type="danger" link @click="handleRowDelete(row)">删除</ElButton>
        </div>
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
