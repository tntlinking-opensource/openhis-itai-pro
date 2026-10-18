<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type {
  FrequencyInitResponse,
  FrequencyItem,
  PageFrequencyParams,
  PageFrequencyResponse,
} from '#/api/basic/frequency';
import type { ApiResponse } from '#/types/api-response';

import { computed, onActivated, onMounted, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElSpace, ElTag } from 'element-plus';

import {
  deleteFrequencyApi,
  disableFrequencyApi,
  enableFrequencyApi,
  initFrequencyApi,
  pageFrequencyApi,
} from '#/api/basic/frequency';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';
import EditDialog from './edit-dialog.vue';

defineOptions({ name: 'BasicFrequencyIndex' });

const queryParams = ref<PageFrequencyParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
});

const tableData = ref<FrequencyItem[]>([]);
const loading = ref(false);
const total = ref(0);

const statusOptions = ref<FrequencyInitResponse['statusOptions']>([]);
const cycleTypeOptions = ref<FrequencyInitResponse['cycleTypeOptions']>([]);
const sceneOptions = ref<FrequencyInitResponse['sceneOptions']>([]);
const allowCrossDayOptions = ref<FrequencyInitResponse['allowCrossDayOptions']>([]);
const periodUnitOptions = ref<FrequencyInitResponse['periodUnitOptions']>([]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, fixed: 'left' },
  {
    prop: 'rateCode',
    label: '频次编码',
    width: 110,
    fixed: 'left',
    showOverflowTooltip: true,
  },
  {
    prop: 'name',
    label: '频次名称',
    minWidth: 120,
    fixed: 'left',
    showOverflowTooltip: true,
  },
  {
    prop: 'activeFlag',
    label: '状态',
    width: 80,
    align: 'center',
    slot: 'activeFlag',
  },
  {
    prop: 'cycleType',
    label: '周期类型',
    width: 100,
    slot: 'cycleType',
  },
  {
    prop: 'dayTimes',
    label: '执行时点',
    minWidth: 130,
    showOverflowTooltip: true,
  },
  {
    prop: 'dayCount',
    label: '日计数',
    width: 75,
  },
  {
    prop: 'executionPeriod',
    label: '执行周期',
    width: 90,
    slot: 'executionPeriod',
  },
  {
    prop: 'dayInterval',
    label: '日间隔',
    width: 75,
  },
  {
    prop: 'totalExecutionCount',
    label: '总次数',
    width: 75,
  },
  {
    prop: 'applicableScenes',
    label: '适用场景',
    minWidth: 110,
    slot: 'applicableScenes',
    showOverflowTooltip: true,
  },
  {
    prop: 'allowCrossDay',
    label: '跨日策略',
    width: 95,
    slot: 'allowCrossDay',
  },
  {
    prop: 'sortNo',
    label: '排序',
    width: 65,
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

/** 判断频次是否为启用态（兼容后端 1/0 与布尔） */
function isFrequencyActive(v: unknown): boolean {
  if (v === true || v === 1) {
    return true;
  }
  if (v === false || v === 0) {
    return false;
  }
  const s = v === null || v === undefined ? '' : String(v).trim();
  return s === '1' || s.toLowerCase() === 'true';
}

function optionLabel(
  options: Array<{ label: string; value: string }> | undefined,
  v: unknown,
): string {
  if (v === null || v === undefined || String(v).trim() === '') return '-';
  const hit = (options ?? []).find((o) => o.value === String(v));
  return hit?.label ?? String(v);
}

function cycleTypeLabel(v: unknown): string {
  return optionLabel(cycleTypeOptions.value, v);
}

function activeFlagLabel(v: unknown): string {
  return optionLabel(statusOptions.value, v);
}

function activeFlagTagType(v: FrequencyItem['activeFlag']): 'success' | 'warning' {
  return isFrequencyActive(v) ? 'success' : 'warning';
}

function allowCrossDayLabel(v: unknown): string {
  return optionLabel(allowCrossDayOptions.value, v);
}

function periodUnitLabel(v: unknown): string {
  return optionLabel(periodUnitOptions.value, v);
}

function executionPeriodLabel(row: FrequencyItem): string {
  if (row.executionPeriod === null || row.executionPeriod === undefined) return '-';
  const unit = periodUnitLabel(row.executionPeriodUnit);
  return unit === '-' ? String(row.executionPeriod) : `${row.executionPeriod}${unit}`;
}

function applicableScenesLabel(v: null | string | undefined): string {
  if (v === null || v === undefined || String(v).trim() === '') return '-';
  const parts = String(v)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((key) => optionLabel(sceneOptions.value, key));
  return parts.length > 0 ? parts.join('、') : '-';
}

async function loadInitOptions() {
  try {
    const res = await initFrequencyApi();
    const data =
      res && typeof res === 'object' && 'data' in res
        ? (res as ApiResponse<FrequencyInitResponse>).data
        : (res as FrequencyInitResponse);
    statusOptions.value = data?.statusOptions ?? [];
    cycleTypeOptions.value = data?.cycleTypeOptions ?? [];
    sceneOptions.value = data?.sceneOptions ?? [];
    allowCrossDayOptions.value = data?.allowCrossDayOptions ?? [];
    periodUnitOptions.value = data?.periodUnitOptions ?? [];
  } catch {
    statusOptions.value = [];
    cycleTypeOptions.value = [];
    sceneOptions.value = [];
    allowCrossDayOptions.value = [];
    periodUnitOptions.value = [];
  }
}

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'input',
    prop: 'keyword',
    label: '关键词',
    placeholder: '频次名称或编码',
    clearable: true,
    width: '280px',
  },
]);

const loadData = async () => {
  loading.value = true;
  try {
    const res = await pageFrequencyApi(queryParams.value);
    const data: PageFrequencyResponse | undefined = (res as { data?: PageFrequencyResponse })?.data;
    const list = data?.content ?? [];
    const totalCount = data?.totalCount ?? data?.totalElements ?? 0;
    tableData.value = Array.isArray(list) ? list : [];
    total.value = Number(totalCount);
  } catch {
    ElMessage.error('查询失败，请稍后重试');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
};

const handleQuery = (params: Record<string, unknown>) => {
  queryParams.value = {
    ...queryParams.value,
    ...params,
  } as PageFrequencyParams;
  loadData();
};

const handleReset = () => {
  queryParams.value = {
    pageNo: 1,
    pageSize: 50,
    keyword: '',
  };
  loadData();
};

const handlePagination = (pagination: { limit: number; page: number }) => {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  loadData();
};

const dialogVisible = ref(false);
const dialogFormData = ref<Partial<FrequencyItem> & { id?: string }>({});
const isCopyMode = ref(false);

const handleAdd = () => {
  isCopyMode.value = false;
  dialogFormData.value = {};
  dialogVisible.value = true;
};

const handleEdit = (row: FrequencyItem) => {
  isCopyMode.value = false;
  dialogFormData.value = {
    ...row,
    id: row.id !== null && row.id !== undefined ? String(row.id) : undefined,
  };
  dialogVisible.value = true;
};

const handleCopy = (row: FrequencyItem) => {
  isCopyMode.value = true;
  const { id: _id, rateCode: _code, ...rest } = row;
  dialogFormData.value = {
    ...rest,
    rateCode: undefined,
    name: rest.name ? `${rest.name}（复制）` : '',
  };
  dialogVisible.value = true;
};

const handleDialogSuccess = () => {
  loadData();
};

const handleEnable = async (row: FrequencyItem) => {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少频次ID');
    return;
  }
  try {
    await enableFrequencyApi(id);
    ElMessage.success('已启用');
    loadData();
  } catch {
    ElMessage.error('启用失败，请稍后重试');
  }
};

const handleDisable = async (row: FrequencyItem) => {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少频次ID');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '停用后该频次将不再出现在新业务的频次选择中，历史记录仍可查看。确定停用吗？',
      '停用频次',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    );
    await disableFrequencyApi(id);
    ElMessage.success('已停用');
    loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('停用失败，请稍后重试');
    }
  }
};

const handleDelete = async (row: FrequencyItem) => {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少频次ID');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '建议优先使用「停用」而非删除。删除后该频次将不再出现在列表中（逻辑删除），确定继续吗？',
      '删除频次',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' },
    );
    loading.value = true;
    await deleteFrequencyApi(id);
    ElMessage.success('删除成功');
    loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请稍后重试');
    }
  } finally {
    loading.value = false;
  }
};

onActivated(() => {
  const needRefresh = sessionStorage.getItem('frequencyRefresh');
  if (needRefresh === 'true') {
    sessionStorage.removeItem('frequencyRefresh');
    loadData();
  }
});

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

      <template #activeFlag="{ row }">
        <ElTag :type="activeFlagTagType(row.activeFlag)" size="small" effect="plain">
          {{ activeFlagLabel(row.activeFlag) }}
        </ElTag>
      </template>

      <template #cycleType="{ row }">
        {{ cycleTypeLabel(row.cycleType) }}
      </template>

      <template #executionPeriod="{ row }">
        {{ executionPeriodLabel(row) }}
      </template>

      <template #applicableScenes="{ row }">
        {{ applicableScenesLabel(row.applicableScenes) }}
      </template>

      <template #allowCrossDay="{ row }">
        {{ allowCrossDayLabel(row.allowCrossDay) }}
      </template>

      <template #action="{ row }">
        <ElSpace wrap :size="4">
          <ElButton type="primary" link @click.stop="handleEdit(row)">编辑</ElButton>
          <ElButton type="primary" link @click.stop="handleCopy(row)">复制</ElButton>
          <ElButton
            v-if="!isFrequencyActive(row.activeFlag)"
            type="primary"
            link
            @click.stop="handleEnable(row)"
          >
            启用
          </ElButton>
          <ElButton
            v-if="isFrequencyActive(row.activeFlag)"
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
      :is-copy-mode="isCopyMode"
      @success="handleDialogSuccess"
    />
  </Page>
</template>
