<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  PageSpecimenDefinitionParams,
  SpecimenDefinitionItem,
} from '#/api/basic/specimen-definition';
import type { ApiOptionItem, ApiResponse, PageApiResponse } from '#/types/api-response';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElSpace, ElTag } from 'element-plus';

import {
  deleteSpecimenDefinitionApi,
  disableSpecimenDefinitionApi,
  enableSpecimenDefinitionApi,
  getSpecimenDefinitionApi,
  initSpecimenDefinitionApi,
  pageSpecimenDefinitionApi,
  SPECIMEN_STATUS_ENUM,
} from '#/api/basic/specimen-definition';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';

defineOptions({ name: 'BasicSpecimenDefinitionIndex' });

const queryParams = ref<PageSpecimenDefinitionParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  statusEnum: undefined,
  typeEnum: undefined,
});

const tableData = ref<SpecimenDefinitionItem[]>([]);
const loading = ref(false);
const total = ref(0);

const typeEnumOptions = ref<ApiOptionItem[]>([]);
const statusEnumOptions = ref<ApiOptionItem[]>([]);

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
    label: '样本名称',
    minWidth: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'typeEnumText',
    label: '标本种类',
    width: 120,
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
    prop: 'pyStr',
    label: '拼音码',
    width: 100,
    showOverflowTooltip: true,
  },
  {
    prop: 'wbStr',
    label: '五笔码',
    width: 100,
    showOverflowTooltip: true,
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

/** 判断样本定义是否为启用态 */
function isSpecimenActive(row: SpecimenDefinitionItem): boolean {
  return row.statusEnum === SPECIMEN_STATUS_ENUM.ACTIVE;
}

/** 状态列 Tag 类型映射 */
function statusTagType(statusEnum?: string): 'danger' | 'info' | 'success' | 'warning' {
  switch (statusEnum) {
    case SPECIMEN_STATUS_ENUM.ACTIVE: {
      return 'success';
    }
    case SPECIMEN_STATUS_ENUM.DRAFT: {
      return 'warning';
    }
    case SPECIMEN_STATUS_ENUM.DISCARDED: {
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
    const res = await initSpecimenDefinitionApi();
    const data =
      res && typeof res === 'object' && 'data' in res
        ? (
            res as ApiResponse<{
              statusEnumOptions?: ApiOptionItem[];
              typeEnumOptions?: ApiOptionItem[];
            }>
          ).data
        : (res as {
            statusEnumOptions?: ApiOptionItem[];
            typeEnumOptions?: ApiOptionItem[];
          });
    typeEnumOptions.value = data?.typeEnumOptions ?? [];
    statusEnumOptions.value = data?.statusEnumOptions ?? [];
  } catch {
    typeEnumOptions.value = [];
    statusEnumOptions.value = [];
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
    prop: 'typeEnum',
    label: '标本种类',
    placeholder: '全部',
    clearable: true,
    width: '160px',
    options: typeEnumOptions.value,
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
    const res = await pageSpecimenDefinitionApi(queryParams.value);
    const data: PageApiResponse<SpecimenDefinitionItem> | undefined =
      (res as ApiResponse<PageApiResponse<SpecimenDefinitionItem>>)?.data ??
      (res as PageApiResponse<SpecimenDefinitionItem>);
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
  } as PageSpecimenDefinitionParams;
  loadData();
}

/** 重置筛选条件并刷新列表 */
function handleReset() {
  queryParams.value = {
    pageNo: 1,
    pageSize: queryParams.value.pageSize,
    keyword: '',
    statusEnum: undefined,
    typeEnum: undefined,
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
const dialogFormData = ref<Partial<SpecimenDefinitionItem>>({});

/** 打开新增弹窗 */
function handleAdd() {
  dialogFormData.value = {};
  dialogVisible.value = true;
}

/** 编辑前拉取详情 */
async function handleEdit(row: SpecimenDefinitionItem) {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少样本定义ID');
    return;
  }
  try {
    const detail = await getSpecimenDefinitionApi(id);
    dialogFormData.value = detail ?? { ...row, id };
    dialogVisible.value = true;
  } catch {
    ElMessage.error('获取样本详情失败，请稍后重试');
  }
}

/** 保存成功后刷新列表 */
function handleDialogSuccess() {
  loadData();
}

/** 启用样本定义 */
async function handleEnable(row: SpecimenDefinitionItem) {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少样本定义ID');
    return;
  }
  try {
    await enableSpecimenDefinitionApi(id);
    ElMessage.success('已启用');
    loadData();
  } catch {
    ElMessage.error('启用失败，请稍后重试');
  }
}

/** 停用样本定义 */
async function handleDisable(row: SpecimenDefinitionItem) {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少样本定义ID');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '停用后该样本将不再出现在新业务的选品中，历史记录仍可查看。确定停用吗？',
      '停用样本',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    );
    await disableSpecimenDefinitionApi(id);
    ElMessage.success('已停用');
    loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败，请稍后重试');
    }
  }
}

/** 逻辑删除样本定义 */
async function handleDelete(row: SpecimenDefinitionItem) {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少样本定义ID');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '建议优先使用「停用」而非删除。删除后该样本将不再出现在列表中（逻辑删除），确定继续吗？',
      '删除样本',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' },
    );
    loading.value = true;
    await deleteSpecimenDefinitionApi(id);
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
            v-if="!isSpecimenActive(row)"
            type="primary"
            link
            @click.stop="handleEnable(row)"
          >
            启用
          </ElButton>
          <ElButton
            v-if="isSpecimenActive(row)"
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
