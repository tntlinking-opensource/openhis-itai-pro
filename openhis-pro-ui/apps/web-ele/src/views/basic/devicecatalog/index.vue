<script lang="ts" setup>
import type { FormItemConfig, TableColumn, TreeNodeData } from '@vben-core/table-layout-ui';

import type { DeviceCatalogInitResponse, DeviceCatalogItem } from '#/api';

import { computed, nextTick, onActivated, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import {
  batchStartDeviceCatalogApi,
  batchStopDeviceCatalogApi,
  deleteDeviceCatalogApi,
  initDeviceCatalogApi,
  pageDeviceCatalogApi,
} from '#/api';
import { PublicationStatusCode } from '#/constants/domain/publication-status';
import StatusEnumTag from '#/components/status-enum-tag.vue';
import DeviceCatalogEditDialog from './[id]/index.vue';

function isDevicePublicationActive(row: DeviceCatalogItem): boolean {
  const status = row.statusEnum;
  if (
    status === PublicationStatusCode.RETIRED ||
    status === PublicationStatusCode.INACTIVE ||
    status === PublicationStatusCode.DISCARDED ||
    status === 3 ||
    status === '3'
  ) {
    return false;
  }
  return (
    status === PublicationStatusCode.ACTIVE ||
    status === 1 ||
    status === '1' ||
    status === 2 ||
    status === '2'
  );
}

const queryParams = ref({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  /** 耗材类型侧栏筛选；空字符串表示「全部」，与药品 categoryCode 约定一致 */
  typeCode: '',
});

const tableData = ref<DeviceCatalogItem[]>([]);
const loading = ref(false);
const total = ref(0);
const selectedRows = ref<DeviceCatalogItem[]>([]);
const tableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);

const sideQueryLoading = ref(false);
const initOptions = ref<DeviceCatalogInitResponse>({} as DeviceCatalogInitResponse);
const catalogEditOpen = ref(false);
const catalogEditId = ref<string>('');

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

/** 耗材类型树（与药品侧 medicationCategoryCodeOptions → siderData 同源思路） */
const siderData = computed(() => {
  const options = initOptions.value?.equipmentTypes as
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
    width: 60,
    align: 'center',
  },
  {
    prop: 'busNo',
    label: '编码',
    width: 150,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '耗材名称',
    width: 100,
    align: 'center',
    showOverflowTooltip: true,
    slot: 'name',
  },
  {
    prop: 'size',
    label: '包装规格',
    width: 100,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'categoryCode_dictText',
    label: '耗材分类',
    width: 100,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'unitCode_dictText',
    label: '采购单位',
    width: 100,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'minUnitCode_dictText',
    label: '最小使用单位',
    width: 100,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'packageUnitCode_dictText',
    label: '包装单位',
    width: 100,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'ybNo',
    label: '医保编码',
    width: 110,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'ybOrgNo',
    label: '医药机构目录编码',
    width: 130,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnum',
    label: '状态',
    width: 90,
    align: 'center',
    slot: 'statusEnum',
  },
  {
    prop: 'manufacturerText',
    label: '生产厂家',
    width: 200,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'retailPrice',
    label: '售价',
    width: 90,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'itemTypeCode_dictText',
    label: '财务类别',
    width: 90,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'hvcmFlag_enumText',
    label: '高值耗材标志',
    width: 120,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 150,
    align: 'center',
  },
];

const formItems: FormItemConfig[] = [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '请输入耗材名称或编码',
    clearable: true,
  },
];

function normalizeTypeCodeForApi(raw: unknown): string | undefined {
  if (raw === undefined || raw === null) {
    return undefined;
  }
  const s = String(raw).trim();
  return s === '' ? undefined : s;
}

const handleQuery = async (params: Record<string, unknown>) => {
  loading.value = true;
  try {
    const mergedTypeRaw =
      typeof params.typeCode === 'string' && params.typeCode
        ? params.typeCode
        : queryParams.value.typeCode || '';
    const response = await pageDeviceCatalogApi({
      pageNo: typeof params.pageNo === 'number' ? params.pageNo : queryParams.value.pageNo,
      pageSize: typeof params.pageSize === 'number' ? params.pageSize : queryParams.value.pageSize,
      keyword:
        typeof params.keyword === 'string' && params.keyword
          ? params.keyword
          : queryParams.value.keyword || undefined,
      typeCode: normalizeTypeCodeForApi(mergedTypeRaw),
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
    typeCode: '',
  };
  handleQuery(queryParams.value);
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  handleQuery(queryParams.value);
};

const handleSelectionChange = (selection: DeviceCatalogItem[]) => {
  selectedRows.value = selection;
};

const handleSortChange = (_sortInfo: { column: unknown; order: string; prop: string }) => {};

const handleSideQuery = (node: TreeNodeData) => {
  queryParams.value.typeCode =
    !node || node.id === '__ALL__' ? '' : String(node.value ?? node.id ?? '');
  queryParams.value.pageNo = 1;
  handleQuery(queryParams.value);
};

const handleRefreshSideQuery = async () => {
  sideQueryLoading.value = true;
  try {
    const res = await initDeviceCatalogApi();
    const data: DeviceCatalogInitResponse =
      ((res as unknown as { data?: DeviceCatalogInitResponse })
        ?.data as DeviceCatalogInitResponse) ||
      (res as unknown as DeviceCatalogInitResponse) ||
      ({} as DeviceCatalogInitResponse);
    initOptions.value = data;
  } catch (error) {
    console.error('刷新耗材类型失败:', error);
    ElMessage.error('刷新失败，请稍后重试');
  } finally {
    sideQueryLoading.value = false;
  }
};

function openCatalogEditor(id: string) {
  catalogEditId.value = id;
  catalogEditOpen.value = true;
}

const handleAdd = () => {
  openCatalogEditor('new');
};

const clearSelection = async () => {
  selectedRows.value = [];
  await nextTick();
  const gridApi = tableLayoutRef.value?.gridApi;
  if (gridApi) {
    const grid = gridApi.grid || gridApi;
    if (typeof grid.clearCheckboxRow === 'function') {
      grid.clearCheckboxRow();
    }
  }
};

const doDeleteByIds = async (ids: string[]) => {
  if (ids.length === 0) {
    ElMessage.warning('请先选择要删除的数据');
    return;
  }
  await ElMessageBox.confirm(`确定要删除选中的 ${ids.length} 条耗材吗？删除后无法恢复！`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  });
  await deleteDeviceCatalogApi(ids);
  ElMessage.success('删除成功');
  await handleQuery(queryParams.value);
  await clearSelection();
};

const handleDelete = async () => {
  const ids = selectedRows.value
    .map((item) => item.id)
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

const handleEnable = async () => {
  const ids = selectedRows.value.map((item) => item.id).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先选择要启用的数据');
    return;
  }
  try {
    await batchStartDeviceCatalogApi(ids.map(String));
    ElMessage.success('启用成功');
    await handleQuery(queryParams.value);
    await clearSelection();
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleDisable = async () => {
  const ids = selectedRows.value.map((item) => item.id).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先选择要停用的数据');
    return;
  }
  try {
    await batchStopDeviceCatalogApi(ids.map(String));
    ElMessage.success('停用成功');
    await handleQuery(queryParams.value);
    await clearSelection();
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleStart = async (row: DeviceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await batchStartDeviceCatalogApi([String(row.id)]);
    ElMessage.success('启用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('启用失败:', error);
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleStop = async (row: DeviceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await batchStopDeviceCatalogApi([String(row.id)]);
    ElMessage.success('停用成功');
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('停用失败:', error);
    ElMessage.error('停用失败，请稍后重试');
  }
};

const handleRowClick = (row: DeviceCatalogItem) => {
  openCatalogEditor(String(row.id || ''));
};

const handleRowDelete = async (row: DeviceCatalogItem) => {
  if (!row.id) {
    ElMessage.warning('缺少ID');
    return;
  }
  try {
    await doDeleteByIds([String(row.id)]);
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error);
      ElMessage.error('删除失败，请稍后重试');
    }
  }
};

const initPage = async () => {
  try {
    const res = await initDeviceCatalogApi();
    const data: DeviceCatalogInitResponse =
      ((res as unknown as { data?: DeviceCatalogInitResponse })
        ?.data as DeviceCatalogInitResponse) ||
      (res as unknown as DeviceCatalogInitResponse) ||
      ({} as DeviceCatalogInitResponse);
    initOptions.value = data;
    await handleQuery(queryParams.value);
  } catch (error) {
    console.error('加载初始化数据失败:', error);
    await handleQuery(queryParams.value);
  }
};

onActivated(() => {
  const needRefresh = sessionStorage.getItem('deviceCatalogRefresh');
  if (needRefresh === 'true') {
    sessionStorage.removeItem('deviceCatalogRefresh');
    handleQuery(queryParams.value);
  }
});

initPage();
</script>

<template>
  <Page>
    <TableLayout
      ref="tableLayoutRef"
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
      row-key="id"
      border
      stripe
      size="default"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
      @side-query="handleSideQuery"
      @refresh-side-query="handleRefreshSideQuery"
    >
      <template #side-header>
        <div class="text-muted-foreground px-1 text-sm font-medium">耗材类型</div>
      </template>

      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
        <ElButton type="success" @click="handleEnable">批量启用</ElButton>
        <ElButton type="warning" @click="handleDisable">批量停用</ElButton>
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
        <StatusEnumTag :status-enum="row.statusEnum" :status-text="row.statusEnum_enumText" />
      </template>

      <template #action="{ row }">
        <ElButton type="primary" link @click="handleRowClick(row)"> 编辑 </ElButton>
        <ElButton
          v-if="!isDevicePublicationActive(row)"
          type="success"
          link
          @click="handleStart(row)"
        >
          启用
        </ElButton>
        <ElButton v-else type="warning" link @click="handleStop(row)"> 停用 </ElButton>
        <ElButton type="danger" link @click="handleRowDelete(row)"> 删除 </ElButton>
      </template>
    </TableLayout>

    <DeviceCatalogEditDialog
      v-model="catalogEditOpen"
      :catalog-id="catalogEditId"
      :init-options-source="initOptions"
      @success="handleQuery(queryParams)"
    />
  </Page>
</template>
