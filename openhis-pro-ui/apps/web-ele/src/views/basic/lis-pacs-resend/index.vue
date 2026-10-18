<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { FailedDispatchItem, PageFailedDispatchParams } from '#/api/basic/lis-pacs-resend';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElSpace, ElTag } from 'element-plus';

import { pageFailedDispatchApi, resendBatchApi, resendOneApi } from '#/api/basic/lis-pacs-resend';
import { LIS_PACS_CATEGORY } from '#/api/basic/lis-pacs-item-mapping';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import { BASIC_TABLE_MAX_HEIGHT } from '../basic-table-max-height';

defineOptions({ name: 'BasicLisPacsResendIndex' });

const queryParams = ref<PageFailedDispatchParams>({
  pageNo: 1,
  pageSize: 50,
  categoryEnum: '',
});

const tableData = ref<FailedDispatchItem[]>([]);
const total = ref(0);
const loading = ref(false);
const selectedRows = ref<FailedDispatchItem[]>([]);

const categoryOptions = [
  { label: '全部', value: '' },
  { label: 'LIS检验', value: LIS_PACS_CATEGORY.LIS },
  { label: 'PACS检查', value: LIS_PACS_CATEGORY.PACS },
];

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'radio',
    prop: 'categoryEnum',
    label: '系统',
    options: categoryOptions,
  },
]);

/** select/radio 偶发把空值打成数组；后端 categoryEnum 只要字符串 */
function normalizeCategoryEnum(value: unknown): string | undefined {
  const raw = Array.isArray(value) ? value[0] : value;
  if (raw === undefined || raw === null || raw === '') {
    return undefined;
  }
  return String(raw);
}
const tableColumns: TableColumn[] = [
  { type: 'selection', width: 48, align: 'center' },
  { type: 'index', label: '序号', width: 58, align: 'center' },
  { prop: 'systemLabel', label: '系统', width: 100, align: 'center', slot: 'systemLabel' },
  { prop: 'patientName', label: '患者', width: 100, showOverflowTooltip: true },
  { prop: 'encounterBusNo', label: '就诊号', width: 130, showOverflowTooltip: true },
  { prop: 'activityBusNo', label: '院内编码', width: 130, showOverflowTooltip: true },
  { prop: 'activityName', label: '项目名称', minWidth: 160, showOverflowTooltip: true },
  { prop: 'prescriptionNo', label: '申请单号', width: 130, showOverflowTooltip: true },
  { prop: 'failCount', label: '失败次数', width: 90, align: 'center' },
  { prop: 'lastError', label: '失败原因', minWidth: 180, showOverflowTooltip: true },
  { prop: 'updatedAt', label: '失败时间', width: 170, showOverflowTooltip: true },
  { prop: 'action', label: '操作', fixed: 'right', slot: 'action', width: 100, align: 'center' },
];

function normalizeId(value: null | number | string | undefined): string | undefined {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  return String(value);
}

async function loadData() {
  loading.value = true;
  try {
    const res = await pageFailedDispatchApi({
      ...queryParams.value,
      categoryEnum: normalizeCategoryEnum(queryParams.value.categoryEnum),
    });
    const { content, totalCount } = parsePagedRowsFromApiData<FailedDispatchItem>(res);
    tableData.value = content.map((row) => ({
      ...row,
      serviceRequestId: normalizeId(row.serviceRequestId),
      activityId: normalizeId(row.activityId),
    }));
    total.value = totalCount;
  } catch {
    ElMessage.error('查询失败清单失败');
    tableData.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function handleQuery(params: Record<string, unknown>) {
  queryParams.value = {
    ...queryParams.value,
    ...params,
    categoryEnum: normalizeCategoryEnum(params.categoryEnum) ?? '',
    pageNo: 1,
  } as PageFailedDispatchParams;
  void loadData();
}

function handleReset() {
  queryParams.value = { pageNo: 1, pageSize: 50, categoryEnum: '' };
  void loadData();
}

function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  void loadData();
}

function handleSelectionChange(rows: FailedDispatchItem[]) {
  selectedRows.value = rows;
}

function rowKey(row: FailedDispatchItem) {
  return `${row.serviceRequestId}-${row.lis ? 'lis' : 'pacs'}`;
}

async function handleResendOne(row: FailedDispatchItem) {
  const id = normalizeId(row.serviceRequestId);
  if (!id) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认补传「${row.activityName || id}」到 ${row.systemLabel || ''}？`,
      '手动补传',
      { type: 'warning' },
    );
    const res = await resendOneApi({ serviceRequestId: id, lis: !!row.lis });
    const data = (res ?? {}) as { succeeded?: number };
    if ((data.succeeded ?? 0) > 0) {
      ElMessage.success('补传成功');
    } else {
      ElMessage.warning('补传未成功，请查看失败原因后重试');
    }
    void loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('补传失败');
    }
  }
}

async function handleResendBatch() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选要补传的记录');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认批量补传已选 ${selectedRows.value.length} 条？`, '批量补传', {
      type: 'warning',
    });
    const items = selectedRows.value
      .map((row) => {
        const id = normalizeId(row.serviceRequestId);
        if (!id) {
          return null;
        }
        return { serviceRequestId: id, lis: !!row.lis };
      })
      .filter((item): item is { serviceRequestId: string; lis: boolean } => item !== null);
    const res = await resendBatchApi(items);
    const data = (res ?? {}) as { succeeded?: number; failed?: number };
    ElMessage.success(`补传完成：成功 ${data.succeeded ?? 0}，失败 ${data.failed ?? 0}`);
    void loadData();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量补传失败');
    }
  }
}

onMounted(() => {
  void loadData();
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
      :row-key="rowKey"
      stripe
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <ElButton type="primary" :disabled="selectedRows.length === 0" @click="handleResendBatch">
          批量补传
        </ElButton>
        <ElButton @click="loadData">刷新</ElButton>
      </template>

      <template #systemLabel="{ row }">
        <ElTag :type="row.lis ? 'primary' : 'warning'" size="small" effect="plain">
          {{ row.systemLabel }}
        </ElTag>
      </template>

      <template #action="{ row }">
        <ElSpace>
          <ElButton type="primary" link @click.stop="handleResendOne(row)">补传</ElButton>
        </ElSpace>
      </template>
    </TableLayout>
  </Page>
</template>
