<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  FrequencyInitResponse,
  FrequencyItem,
  PageFrequencyParams,
  PageFrequencyResponse,
} from '#/api/basic/frequency';
import type { ApiResponse } from '#/types/api-response';

import { computed, onActivated, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElSpace, ElTag } from 'element-plus';

import {
  deleteFrequencyApi,
  disableFrequencyApi,
  enableFrequencyApi,
  initFrequencyApi,
  pageFrequencyApi,
} from '#/api/basic/frequency';

import EditDialog from './edit-dialog.vue';

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

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 58, fixed: 'left' },
  {
    prop: 'rateCode',
    label: '频次编码',
    width: 120,
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
    width: 88,
    align: 'center',
    slot: 'activeFlag',
  },
  {
    prop: 'sortNo',
    label: '排序号',
    width: 80,
  },
  {
    prop: 'cycleType',
    label: '周期类型',
    width: 110,
    slot: 'cycleType',
  },
  {
    prop: 'dayInterval',
    label: '日间隔(天)',
    width: 95,
  },
  {
    prop: 'dayTimes',
    label: '日内次数/时间点',
    minWidth: 130,
    showOverflowTooltip: true,
  },
  {
    prop: 'weekCycleFlag',
    label: '按周',
    width: 65,
    slot: 'weekCycleFlag',
  },
  {
    prop: 'weekInterval',
    label: '周间隔',
    width: 75,
  },
  {
    prop: 'totalExecutionCount',
    label: '总执行次数',
    width: 100,
  },
  {
    prop: 'executionPeriod',
    label: '执行周期长度',
    width: 110,
  },
  {
    prop: 'executionPeriodUnit',
    label: '执行周期单位',
    width: 110,
  },
  {
    prop: 'applicableScenes',
    label: '适用场景',
    minWidth: 120,
    slot: 'applicableScenes',
    showOverflowTooltip: true,
  },
  {
    prop: 'allowCrossDay',
    label: '允许跨日',
    width: 85,
    slot: 'allowCrossDay',
  },
  {
    prop: 'thirdCode',
    label: '第三方编码',
    width: 110,
    showOverflowTooltip: true,
  },
  {
    prop: 'memo',
    label: '备注',
    minWidth: 140,
    showOverflowTooltip: true,
  },
  {
    prop: 'action',
    label: '操作',
    fixed: 'right',
    slot: 'action',
    width: 240,
    align: 'center',
  },
];

function optionLabel(
  options: Array<{ label: string; value: string }> | undefined,
  v: unknown,
): string {
  if (v === null || v === undefined || String(v).trim() === '') return '-';
  const hit = (options ?? []).find((o) => o.value === String(v));
  return hit?.label ?? String(v);
}

/** 周期类型展示文案：来自后端 init() 的 cycleTypeOptions */
function cycleTypeLabel(v: null | number | undefined): string {
  return optionLabel((cycleTypeOptions.value ?? []) as any, v);
}

/** 状态展示文案：来自后端 init() 的 statusOptions */
function activeFlagLabel(v: null | number | undefined): string {
  return optionLabel((statusOptions.value ?? []) as any, v);
}

/** 状态标签类型（与 init 的 statusOptions 中 value=1/启用 等一致） */
function activeFlagTagType(v: null | boolean | number | string | undefined): 'info' | 'success' {
  if (v === true) return 'success';
  const s = v === null || v === undefined ? '' : String(v).trim();
  if (s === '1' || s.toLowerCase() === 'true') return 'success';
  return 'info';
}

/** 允许跨日展示文案：来自后端 init() 的 allowCrossDayOptions */
function allowCrossDayLabel(v: null | number | undefined): string {
  return optionLabel((allowCrossDayOptions.value ?? []) as any, v);
}

/** 适用场景展示文案：来自后端 init() 的 sceneOptions，支持逗号分隔如 "1,2,3" */
function applicableScenesLabel(v: null | string | undefined): string {
  if (v === null || v === undefined || String(v).trim() === '') return '-';
  const parts = String(v)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((key) => optionLabel((sceneOptions.value ?? []) as any, key));
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
  } catch {
    statusOptions.value = [];
    cycleTypeOptions.value = [];
    sceneOptions.value = [];
    allowCrossDayOptions.value = [];
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

/** 复制频次：以当前行为模板，预填除编码外的字段（需求 3.2.2） */
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

/** 启用频次（需求 5.6、9.2.5） */
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

/** 停用频次（需求 3.2.3、4.4：默认不支持物理删除，仅停用） */
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

/** 删除：需求 4.4 默认不允许物理删除，仅允许停用；若保留删除则需二次确认 */
const handleDelete = async (row: FrequencyItem) => {
  const id = row.id !== null && row.id !== undefined ? String(row.id) : undefined;
  if (!id) {
    ElMessage.warning('缺少频次ID');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '建议优先使用「停用」而非删除。确定要物理删除该频次吗？',
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

      <template #weekCycleFlag="{ row }">
        {{ row.weekCycleFlag === 1 ? '是' : row.weekCycleFlag === 0 ? '否' : '-' }}
      </template>

      <template #activeFlag="{ row }">
        <ElTag :type="activeFlagTagType(row.activeFlag)" size="small" effect="plain">
          {{ activeFlagLabel(row.activeFlag) }}
        </ElTag>
      </template>

      <template #cycleType="{ row }">
        {{ cycleTypeLabel(row.cycleType) }}
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
          <ElButton type="primary" link @click.stop="handleEnable(row)">启用</ElButton>
          <ElButton type="warning" link @click.stop="handleDisable(row)">停用</ElButton>
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
