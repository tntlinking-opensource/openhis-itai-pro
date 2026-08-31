<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { LabDeviceItem, PageLabDeviceParams } from '#/api/basic/lab-device';
import type { ApiOptionItem, ApiResponse, PageApiResponse } from '#/types/api-response';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElSpace, ElTag } from 'element-plus';

import {
  deleteLabDeviceApi,
  disableLabDeviceApi,
  enableLabDeviceApi,
  getLabDeviceApi,
  initLabDeviceApi,
  LAB_DEVICE_STATUS_ENUM,
  pageLabDeviceApi,
} from '#/api/basic/lab-device';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';

defineOptions({ name: 'BasicLabDeviceIndex' });

const queryParams = ref<PageLabDeviceParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  statusEnum: undefined,
  categoryCode: undefined,
  orgId: undefined,
});

const tableData = ref<LabDeviceItem[]>([]);
const loading = ref(false);
const total = ref(0);

const categoryCodeOptions = ref<ApiOptionItem[]>([]);
const statusEnumOptions = ref<ApiOptionItem[]>([]);
const organizationOptions = ref<ApiOptionItem[]>([]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, align: 'center' },
  {
    prop: 'busNo',
    label: '业务编码',
    width: 130,
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '设备名称',
    minWidth: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'categoryCodeText',
    label: '设备分类',
    width: 120,
    showOverflowTooltip: true,
  },
  {
    prop: 'orgName',
    label: '归属科室',
    width: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnumText',
    label: '状态',
    width: 90,
    align: 'center',
    slot: 'statusEnum',
  },
  {
    prop: 'updatedAt',
    label: '更新时间',
    width: 170,
    slot: 'updatedAt',
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 200,
    align: 'center',
  },
];

/** 判断检验设备是否为启用态 */
function isLabDeviceActive(row: LabDeviceItem): boolean {
  return row.statusEnum === LAB_DEVICE_STATUS_ENUM.ACTIVE;
}

/** 状态列 Tag 类型映射 */
function statusTagType(statusEnum?: string): 'danger' | 'info' | 'success' | 'warning' {
  switch (statusEnum) {
    case LAB_DEVICE_STATUS_ENUM.ACTIVE: {
      return 'success';
    }
    case LAB_DEVICE_STATUS_ENUM.DRAFT: {
      return 'warning';
    }
    case LAB_DEVICE_STATUS_ENUM.DISCARDED: {
      return 'danger';
    }
    default: {
      return 'info';
    }
  }
}

/** 格式化列表中的更新时间 */
function formatUpdatedAt(value?: string): string {
  if (!value) {
    return '-';
  }
  return formatDateTime(value) || value;
}

/** 加载维护页下拉选项 */
async function loadInitOptions() {
  try {
    const res = await initLabDeviceApi();
    const data =
      res && typeof res === 'object' && 'data' in res
        ? (
            res as ApiResponse<{
              categoryCodeOptions?: ApiOptionItem[];
              organizationOptions?: ApiOptionItem[];
              statusEnumOptions?: ApiOptionItem[];
            }>
          ).data
        : (res as {
            categoryCodeOptions?: ApiOptionItem[];
            organizationOptions?: ApiOptionItem[];
            statusEnumOptions?: ApiOptionItem[];
          });
    categoryCodeOptions.value = data?.categoryCodeOptions ?? [];
    statusEnumOptions.value = data?.statusEnumOptions ?? [];
    organizationOptions.value = data?.organizationOptions ?? [];
  } catch {
    categoryCodeOptions.value = [];
    statusEnumOptions.value = [];
    organizationOptions.value = [];
  }
}

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '名称 | 编码 | 拼音 | 五笔',
    clearable: true,
    width: '240px',
  },
  {
    type: 'select',
    prop: 'categoryCode',
    label: '设备分类',
    placeholder: '全部',
    clearable: true,
    width: '160px',
    options: categoryCodeOptions.value,
  },
  {
    type: 'select',
    prop: 'orgId',
    label: '归属科室',
    placeholder: '全部',
    clearable: true,
    width: '160px',
    options: organizationOptions.value,
  },
  {
    type: 'select',
    prop: 'statusEnum',
    label: '状态',
    placeholder: '全部',
    clearable: true,
    width: '140px',
    options: statusEnumOptions.value,
  },
]);

/** 按当前分页与筛选条件拉取列表 */
async function loadData() {
  loading.value = true;
  try {
    const res = await pageLabDeviceApi(queryParams.value);
    const data: PageApiResponse<LabDeviceItem> | undefined =
      (res as ApiResponse<PageApiResponse<LabDeviceItem>>)?.data ??
      (res as PageApiResponse<LabDeviceItem>);
    tableData.value = data?.content ?? [];
    total.value = data?.totalCount ?? data?.totalElements ?? 0;
  } catch {
    ElMessage.error('查询失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

/** 触发查询（TableLayout 回传筛选条件） */
function handleQuery(params: Record<string, unknown>) {
  queryParams.value = {
    ...queryParams.value,
    ...params,
    pageNo: 1,
  } as PageLabDeviceParams;
  loadData();
}

/** 重置筛选条件并刷新列表 */
function handleReset() {
  queryParams.value = {
    pageNo: 1,
    pageSize: queryParams.value.pageSize,
    keyword: '',
    statusEnum: undefined,
    categoryCode: undefined,
    orgId: undefined,
  };
  loadData();
}

/** 分页变更后重新查询 */
function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
}

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<LabDeviceItem>>({});

/** 打开新增弹窗 */
function handleAdd() {
  dialogFormData.value = {};
  dialogVisible.value = true;
}

/** 编辑前拉取详情 */
async function handleEdit(row: LabDeviceItem) {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少设备ID');
    return;
  }
  try {
    const detail = await getLabDeviceApi(id);
    dialogFormData.value = detail ?? { ...row, id };
    dialogVisible.value = true;
  } catch {
    ElMessage.error('获取设备详情失败，请稍后重试');
  }
}

/** 保存成功后刷新列表 */
function handleDialogSuccess() {
  loadData();
}

/** 启用检验设备 */
async function handleEnable(row: LabDeviceItem) {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少设备ID');
    return;
  }
  try {
    await enableLabDeviceApi(id);
    ElMessage.success('已启用');
    loadData();
  } catch {
    ElMessage.error('启用失败，请稍后重试');
  }
}

/** 停用检验设备 */
async function handleDisable(row: LabDeviceItem) {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少设备ID');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '停用后该设备将不再出现在合管配置等业务选品中，历史记录仍可查看。确定停用吗？',
      '停用设备',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    );
    await disableLabDeviceApi(id);
    ElMessage.success('已停用');
    loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败，请稍后重试');
    }
  }
}

/** 逻辑删除检验设备 */
async function handleDelete(row: LabDeviceItem) {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少设备ID');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '建议优先使用「停用」而非删除。删除后该设备将不再出现在列表中（逻辑删除），确定继续吗？',
      '删除设备',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' },
    );
    loading.value = true;
    await deleteLabDeviceApi(id);
    ElMessage.success('删除成功');
    loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请稍后重试');
    }
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await loadInitOptions();
  await loadData();
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
      :show-top-query="true"
      :show-side-query="false"
      :show-pagination="true"
      row-key="id"
      stripe
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>

      <template #statusEnum="{ row }">
        <ElTag :type="statusTagType(row.statusEnum)" size="small" effect="plain">
          {{ row.statusEnumText || '-' }}
        </ElTag>
      </template>

      <template #updatedAt="{ row }">
        {{ formatUpdatedAt(row.updatedAt) }}
      </template>

      <template #action="{ row }">
        <ElSpace wrap :size="4">
          <ElButton type="primary" link @click.stop="handleEdit(row)">编辑</ElButton>
          <ElButton
            v-if="!isLabDeviceActive(row)"
            type="primary"
            link
            @click.stop="handleEnable(row)"
          >
            启用
          </ElButton>
          <ElButton
            v-if="isLabDeviceActive(row)"
            type="warning"
            link
            @click.stop="handleDisable(row)"
          >
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
