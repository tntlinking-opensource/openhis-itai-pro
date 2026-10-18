<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type {
  PageWarehouseInventoryParams,
  WarehouseInventoryItem,
} from '#/api/warehouse/inventory';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@zxplat/common-ui';
import { formatDateTime } from '@zxplat/utils';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { submitForReview } from '#/api/warehouse/common';
import {
  deleteInventoryApi,
  initPharmacyWarehouseSuplyApi,
  pageWarehouseInventoryApi,
} from '#/api/warehouse/inventory';
import { useRequest } from '#/hooks/use-request';

import DetailDialog from './detail/dialog.vue';
import { getInventoryStatusMeta } from './detail/util';

defineOptions({
  name: 'WarehouseInventory',
});

const router = useRouter();
const detailDialogRef = ref<InstanceType<typeof DetailDialog> | null>(null);
const inventoryList = ref<WarehouseInventoryItem[]>([]);
const loading = ref(false);
const total = ref(0);

const statusOptions = ref<ApiOptionItem[]>([]);
const typeOptions = ref<ApiOptionItem[]>([]);
const supplierOptions = ref<ApiOptionItem[]>([]);

const { run: runPageWarehouseInventory, loading: pageLoading } = useRequest(
  pageWarehouseInventoryApi,
  { manual: true },
);

const { run: runDeleteInventory, loading: deleteLoading } = useRequest(deleteInventoryApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('删除成功');
    getList();
  },
});

const queryParams = reactive<PageWarehouseInventoryParams>({
  pageNo: 1,
  pageSize: 50,
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '单据号',
    prop: 'keyword',
    type: 'input',
    placeholder: '请输入单据号',
  },
  {
    label: '审批状态',
    prop: 'statusEnum',
    type: 'select',
    multiple: false,
    options: statusOptions.value || [],
  },
  {
    label: '单据类型',
    prop: 'typeEnum',
    type: 'select',
    multiple: false,
    options: typeOptions.value || [],
  },
  // {
  //   label: '供应商',
  //   prop: 'supplierId',
  //   type: 'select',
  //   multiple: false,
  //   options: supplierOptions.value || [],
  //   filterable: true,
  // },
  {
    label: '制单日期',
    prop: 'occurrenceTime',
    type: 'daterange',
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'selection', width: 50, align: 'center' },
  {
    type: 'index',
    label: '序号',
    width: 50,
    align: 'center',
  },
  {
    prop: 'supplyBusNo',
    label: '单据号',
    align: 'center',
    width: 200,
    showOverflowTooltip: true,
  },
  { prop: 'statusEnumText', label: '审批状态', align: 'center', width: 100 },
  { prop: 'typeEnumText', label: '单据类型', align: 'center', width: 100 },
  // {
  //   prop: 'supplierName',
  //   label: '供应商',
  //   align: 'center',
  //   showOverflowTooltip: true,
  //   width: 200,
  // },
  { prop: 'createTime', label: '制单日期', align: 'center', width: 180, slot: 'createTime' },
  { label: '操作', minWidth: 180, fixed: 'right', slot: 'actions', align: 'left' },
];

function parseTime(time?: Date | number | string): string {
  if (!time) return '';
  return formatDateTime(time);
}

async function getInventoryOptions() {
  try {
    const data = await initPharmacyWarehouseSuplyApi();
    statusOptions.value = data.statusOptions || [];
    supplierOptions.value = data.supplierOptions || [];
    typeOptions.value = data.categoryOptions || [];
  } catch {
    statusOptions.value = [];
    supplierOptions.value = [];
    typeOptions.value = [];
  }
}

async function getList() {
  try {
    loading.value = true;
    const params: PageWarehouseInventoryParams = {
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword,
      statusEnum: queryParams.statusEnum,
      typeEnum: queryParams.typeEnum,
      supplierId: queryParams.supplierId,
      dateRange: queryParams.occurrenceTime?.length
        ? {
            startTime: queryParams.occurrenceTime?.[0],
            endTime: queryParams.occurrenceTime?.[1],
          }
        : undefined,
    };

    Object.keys(params).forEach((key) => {
      if (params[key as keyof PageWarehouseInventoryParams] === undefined) {
        delete params[key as keyof PageWarehouseInventoryParams];
      }
    });

    const responseData = await runPageWarehouseInventory(params);
    inventoryList.value = (responseData.content || []) as WarehouseInventoryItem[];
    total.value = responseData.totalCount || 0;
  } finally {
    loading.value = false;
  }
}

function handleReset() {
  queryParams.occurrenceTime = undefined;
  getList();
}

function handlePagination(pagination: { page: number; limit: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

function handleDelete(row: WarehouseInventoryItem) {
  ElMessageBox.confirm('确定删除该盘点单吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    runDeleteInventory({ busNo: row.supplyBusNo });
  });
}

const onReview = async (supplyBusNo: string, type: string) => {
  await submitForReview('inventory', { supplyBusNo, type });
  ElMessage.success(type === 'submit' ? '提交审核成功' : '撤销审核成功');
  getList();
};

onMounted(() => {
  getInventoryOptions();
  getList();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="inventoryList"
      :loading="loading || pageLoading || deleteLoading"
      :total="total"
      :query-params="queryParams as any"
      :form-items="formItems"
      :table-columns="tableColumns"
      row-key="supplyBusNo"
      :show-top-query="true"
      :show-pagination="true"
      :highlight-current-row="true"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #createTime="{ row }">
        <span>{{ parseTime(row.createTime) }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton
          link
          type="primary"
          @click="
            detailDialogRef?.open(
              row.supplyBusNo,
              getInventoryStatusMeta(row.statusEnum).detailState,
            )
          "
        >
          {{ getInventoryStatusMeta(row.statusEnum).detailState === 'view' ? '查看' : '编辑' }}
        </ElButton>
        <ElButton
          v-if="getInventoryStatusMeta(row.statusEnum).canDelete"
          link
          type="danger"
          @click="handleDelete(row)"
        >
          删除
        </ElButton>
        <ElButton
          v-if="getInventoryStatusMeta(row.statusEnum).canSubmit"
          link
          type="primary"
          @click="onReview(row.supplyBusNo, 'submit')"
        >
          提交审批
        </ElButton>
        <ElButton
          v-if="getInventoryStatusMeta(row.statusEnum).canCancelReview"
          link
          type="primary"
          @click="onReview(row.supplyBusNo, 'cancel')"
        >
          撤销审批
        </ElButton>
      </template>
      <template #toolbar-actions>
        <ElButton type="primary" @click="router.push('/warehouse/inventory-create')">
          新增盘点
        </ElButton>
        <ElButton type="primary" @click="router.push('/warehouse/inventory-batch')">
          批量盘点
        </ElButton>
      </template>
    </TableLayout>
    <DetailDialog ref="detailDialogRef" @success="getList" />
  </Page>
</template>
