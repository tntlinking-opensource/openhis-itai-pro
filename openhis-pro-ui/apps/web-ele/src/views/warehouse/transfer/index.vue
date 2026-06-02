<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { PageTransferRequestParams, TransferPageResponse } from '#/api/warehouse/transfer';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { submitForReview } from '#/api/warehouse/common';
import {
  deleteTransferRequest,
  initTransferRequestOptions,
  pageTransferRequest,
} from '#/api/warehouse/transfer';
import { useRequest } from '#/hooks/use-request';

import DetailDialog from './detail/dialog.vue';
import { getTransferStatusMeta } from './detail/util';

defineOptions({
  name: 'WarehouseTransfer',
});

const transferList = ref<TransferPageResponse[]>([]);
const loading = ref(false);

const total = ref(0);
const typeOptions = ref<ApiOptionItem[]>([]);
const statusOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);
const categoryOptions = ref<ApiOptionItem[]>([]);
const drugTypeOptions = ref<ApiOptionItem[]>([]);
const supplierOptions = ref<ApiOptionItem[]>([]);
const warehouseOptions = ref<ApiOptionItem[]>([]);
const warehouseTypeOptions = ref<ApiOptionItem[]>([]);
const workFlowOptions = ref<ApiOptionItem[]>([]);
const router = useRouter();
const detailDialogRef = ref<InstanceType<typeof DetailDialog> | null>(null);

const { run: runPageTransferRequest, loading: pageLoading } = useRequest(pageTransferRequest, {
  manual: true,
});

const { run: runDeleteTransfer, loading: deleteLoading } = useRequest(deleteTransferRequest, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('删除成功');
    getList();
  },
});

const { run: runInitOptions, loading: initLoading } = useRequest(initTransferRequestOptions, {
  manual: true,
  onSuccess: (res) => {
    const data = res || {};
    categoryOptions.value = data.categoryOptions || [];
    drugTypeOptions.value = data.drugTypeOptions || [];
    practitionerOptions.value = data.practitionerOptions || [];
    statusOptions.value = data.statusOptions || [];
    supplierOptions.value = data.supplierOptions || [];
    warehouseOptions.value = data.warehouseOptions || [];
    warehouseTypeOptions.value = data.warehouseTypeOptions || [];
    workFlowOptions.value = data.workFlowOptions || [];
    typeOptions.value = (data as any).typeOptions || data.categoryOptions || [];
  },
});

const queryParams = reactive<PageTransferRequestParams>({
  pageNo: 1,
  pageSize: 50,
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '调拨单号',
    prop: 'keyword',
    type: 'input',
    placeholder: '请输入调拨单号',
  },
  {
    label: '审批状态',
    prop: 'statusEnum',
    type: 'select',
    options: statusOptions.value || [],
  },
  {
    label: '调拨类型',
    prop: 'typeEnum',
    type: 'select',
    options: typeOptions.value || [],
  },
  {
    label: '经手人',
    prop: 'practitionerId',
    type: 'select',
    options: practitionerOptions.value || [],
    filterable: true,
  },
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
    width: 60,
    align: 'center',
  },
  {
    prop: 'supplyBusNo',
    label: '调拨单号',
    align: 'center',
    width: 200,
    showOverflowTooltip: true,
  },
  {
    prop: 'statusEnumText',
    label: '审批状态',
    align: 'center',
    width: 100,
  },
  {
    prop: 'typeEnumText',
    label: '调拨类型',
    align: 'center',
    width: 120,
  },
  {
    prop: 'sourceLocationIdName',
    label: '调出仓库',
    align: 'center',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'purposeLocationIdName',
    label: '调入仓库',
    align: 'center',
    minWidth: 160,
    showOverflowTooltip: true,
  },
  {
    prop: 'practitionerName',
    label: '经手人',
    align: 'center',
    width: 100,
  },
  {
    prop: 'applicantName',
    label: '制单人',
    align: 'center',
    width: 100,
  },
  {
    prop: 'approverName',
    label: '审核人',
    align: 'center',
    width: 100,
  },
  {
    prop: 'applyTime',
    label: '制单日期',
    align: 'center',
    width: 180,
  },
  {
    prop: 'approvalTime',
    label: '审核日期',
    align: 'center',
    width: 180,
  },
  { label: '操作', width: 180, fixed: 'right', slot: 'actions', align: 'left' },
];

async function getList() {
  try {
    loading.value = true;
    const params: PageTransferRequestParams = {
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword,
      statusEnum: queryParams.statusEnum,
      typeEnum: queryParams.typeEnum,
      practitionerId: queryParams.practitionerId,
      dateRange: queryParams.occurrenceTime?.length
        ? {
            startTime: queryParams.occurrenceTime?.[0],
            endTime: queryParams.occurrenceTime?.[1],
          }
        : undefined,
    };

    Object.keys(params).forEach((key) => {
      if (params[key as keyof PageTransferRequestParams] === undefined) {
        delete params[key as keyof PageTransferRequestParams];
      }
    });

    const res = await runPageTransferRequest(params);
    const data = res;
    transferList.value = data.content || [];
    total.value = data.totalCount || 0;
  } catch (error) {
    console.error(error);
    transferList.value = [];
    total.value = 0;
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

function handleDelete(row: TransferPageResponse) {
  ElMessageBox.confirm('确定删除该调拨单吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    runDeleteTransfer({ busNo: row.supplyBusNo });
  });
}

const onReview = async (supplyBusNo: string, type: string) => {
  await submitForReview('transfer', { supplyBusNo, type });
  ElMessage.success(type === 'submit' ? '提交审核成功' : '撤销审核成功');
  getList();
};

runInitOptions();
getList();
</script>

<template>
  <Page>
    <TableLayout
      :table-data="transferList"
      :loading="loading || pageLoading || initLoading || deleteLoading"
      :total="total"
      :query-params="queryParams as any"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-pagination="true"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #actions="{ row }">
        <ElButton
          link
          type="primary"
          @click="
            detailDialogRef?.open(
              row.supplyBusNo,
              getTransferStatusMeta(row.statusEnum).detailState,
            )
          "
        >
          {{ getTransferStatusMeta(row.statusEnum).detailState === 'view' ? '查看' : '编辑' }}
        </ElButton>
        <ElButton
          v-if="getTransferStatusMeta(row.statusEnum).canDelete"
          link
          type="danger"
          @click="handleDelete(row)"
        >
          删除
        </ElButton>
        <ElButton
          v-if="getTransferStatusMeta(row.statusEnum).canSubmit"
          link
          type="primary"
          @click="onReview(row.supplyBusNo, 'submit')"
        >
          提交审批
        </ElButton>
        <ElButton
          v-if="getTransferStatusMeta(row.statusEnum).canCancelReview"
          link
          type="primary"
          @click="onReview(row.supplyBusNo, 'cancel')"
        >
          撤销审批
        </ElButton>
      </template>
      <template #toolbar-actions>
        <ElButton type="primary" @click="router.push('/warehouse/transfer-create')">
          新增调拨
        </ElButton>
      </template>
    </TableLayout>
    <DetailDialog ref="detailDialogRef" @success="getList" />
  </Page>
</template>
