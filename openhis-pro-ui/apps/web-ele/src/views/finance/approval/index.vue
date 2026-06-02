<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  PagePendingApprovalOrdersParams,
  PagePendingApprovalOrdersResponse,
} from '#/api/finance';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { executeApproval, init, pagePendingApprovalOrders } from '#/api/finance';
import type { ApiOptionItem } from '#/types/api-response';
import { useRequest } from '#/hooks/use-request';

import WarehouseAdapter from './components/warehouse-adapter.vue';
import { ElMessage } from 'element-plus';

defineOptions({
  name: 'FinanceApproval',
});

const tableData = ref<PagePendingApprovalOrdersResponse[]>([]);
const total = ref(0);
const loading = ref(false);
const warehouseAdapterRef = ref<InstanceType<typeof WarehouseAdapter> | null>(null);

const queryParams = reactive<PagePendingApprovalOrdersParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: '',
  busNo: '',
  statusEnum: undefined,
  typeEnum: undefined,
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '请输入关键词',
    clearable: true,
  },
  {
    label: '单据号',
    prop: 'busNo',
    type: 'input',
    placeholder: '请输入单据号',
    clearable: true,
  },
  {
    label: '审批状态',
    prop: 'statusEnum',
    type: 'select',
    multiple: false,
    clearable: true,
    options: statusOptions.value || [],
  },
  {
    label: '单据类型',
    prop: 'typeEnum',
    type: 'select',
    multiple: false,
    clearable: true,
    options: typeOptions.value || [],
  },
]);

const tableColumns: TableColumn[] = [
  {
    type: 'index',
    label: '序号',
    width: 60,
    align: 'center',
  },
  {
    prop: 'busNo',
    label: '单据号',
    minWidth: 200,
    showOverflowTooltip: true,
  },
  {
    prop: 'supplierName',
    label: '供应商',
    minWidth: 200,
    showOverflowTooltip: true,
  },
  {
    prop: 'applicantId_dictText',
    label: '申请人',
    width: 120,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'applyTime',
    label: '申请时间',
    width: 180,
    align: 'center',
    slot: 'applyTime',
  },
  {
    prop: 'statusEnumText',
    label: '审批状态',
    width: 120,
    align: 'center',
  },
  {
    prop: 'typeEnumText',
    label: '单据类型',
    width: 120,
    align: 'center',
  },
  {
    prop: 'approveName_dictText',
    label: '审批人',
    width: 120,
    align: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'approvalTime',
    label: '审批时间',
    width: 180,
    align: 'center',
    slot: 'approvalTime',
  },
  {
    label: '操作',
    minWidth: 160,
    fixed: 'right',
    align: 'left',
    slot: 'actions',
  },
];

function parseTime(time?: Date | number | string): string {
  if (!time) return '';
  return formatDateTime(time);
}

const { run: runPagePendingApprovalOrders, loading: pageLoading } = useRequest(
  pagePendingApprovalOrders,
  { manual: true },
);

const { run: runExecuteApproval, loading: executeLoading } = useRequest(executeApproval, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('审批成功');
  },
});

const initData = ref<any>(null);
const { run: runInit, loading: initLoading } = useRequest(init, { manual: true });
const statusOptions = ref<ApiOptionItem[]>([]);
const typeOptions = ref<ApiOptionItem[]>([]);

async function initPage() {
  try {
    const res = await runInit();
    initData.value = res;
    statusOptions.value = (res?.statusOptions || []) as ApiOptionItem[];
    typeOptions.value = (res?.typeOptions || []) as ApiOptionItem[];
  } catch {
    initData.value = null;
    statusOptions.value = [];
    typeOptions.value = [];
  }
}

async function handleExecute(
  row: PagePendingApprovalOrdersResponse,
  cancelFlag: 'agree' | 'reject',
) {
  await runExecuteApproval({ busNo: row.busNo, cancelFlag });
  await getList();
  warehouseAdapterRef.value?.close();
}

async function getList() {
  try {
    loading.value = true;
    const params: PagePendingApprovalOrdersParams = {
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      statusEnum: queryParams.statusEnum || undefined,
      typeEnum: queryParams.typeEnum || undefined,
      busNo: queryParams.busNo || undefined,
      keyword: queryParams.keyword || undefined,
    };

    Object.keys(params).forEach((key) => {
      if (params[key as keyof PagePendingApprovalOrdersParams] === undefined) {
        delete params[key as keyof PagePendingApprovalOrdersParams];
      }
    });

    const responseData = await runPagePendingApprovalOrders(params);
    tableData.value = responseData.content || [];
    total.value = responseData.totalCount || 0;
  } finally {
    loading.value = false;
  }
}

function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 50;
  queryParams.keyword = '';
  queryParams.busNo = '';
  queryParams.statusEnum = undefined;
  queryParams.typeEnum = undefined;
  getList();
}

function handlePagination(pagination: { page: number; limit: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

function handleView(row: PagePendingApprovalOrdersResponse) {
  warehouseAdapterRef.value?.open({
    busNo: row.busNo,
    type: row.typeEnum,
    row,
  });
}

onMounted(() => {
  initPage();
  getList();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="tableData"
      :loading="loading || pageLoading || initLoading || executeLoading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-pagination="true"
      row-key="busNo"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #applyTime="{ row }">
        <span>{{ parseTime(row.applyTime) }}</span>
      </template>
      <template #approvalTime="{ row }">
        <span>{{ parseTime(row.approvalTime) }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="handleView(row)">查看</ElButton>
        <ElButton
          v-if="row.statusEnum === '2'"
          link
          type="primary"
          @click="handleExecute(row, 'agree')"
        >
          通过
        </ElButton>
        <ElButton
          v-if="row.statusEnum === '2'"
          link
          type="danger"
          @click="handleExecute(row, 'reject')"
        >
          驳回
        </ElButton>
      </template>
    </TableLayout>
    <WarehouseAdapter ref="warehouseAdapterRef">
      <template #footer="{ row }">
        <template v-if="row?.statusEnum === '2'">
          <ElButton type="primary" :loading="executeLoading" @click="handleExecute(row, 'agree')">
            通过
          </ElButton>
          <ElButton type="danger" :loading="executeLoading" @click="handleExecute(row, 'reject')">
            驳回
          </ElButton>
        </template>
      </template>
    </WarehouseAdapter>
  </Page>
</template>
