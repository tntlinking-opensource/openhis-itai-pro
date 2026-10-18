<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type {
  InitLossReportResponse,
  LossReportItem,
  PageLossReportParams,
} from '#/api/warehouse/loss';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

import { submitForReview } from '#/api/warehouse/common';
import { deleteLossRequestApi, initLossReportApi, pageLossReportApi } from '#/api/warehouse/loss';
import { useRequest } from '#/hooks/use-request';

import DetailDialog from './detail/dialog.vue';
import { getLossStatusMeta } from './detail/util';

defineOptions({
  name: 'WarehouseLoss',
});

const router = useRouter();
const detailDialogRef = ref<InstanceType<typeof DetailDialog> | null>(null);

const lossList = ref<LossReportItem[]>([]);
const loading = ref(false);
const total = ref(0);

const statusOptions = ref<ApiOptionItem[]>([]);
const typeOptions = ref<ApiOptionItem[]>([]);

const { run: runPageLossReport, loading: pageLoading } = useRequest(pageLossReportApi, {
  manual: true,
});

const { run: runDeleteLoss, loading: deleteLoading } = useRequest(deleteLossRequestApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('删除成功');
    getList();
  },
});

const queryParams = reactive<PageLossReportParams>({
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
  {
    label: '制单日期',
    prop: 'occurrenceTime',
    type: 'daterange',
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'selection', minWidth: 50, align: 'center' },
  {
    type: 'index',
    label: '序号',
    minWidth: 50,
    align: 'center',
  },
  {
    prop: 'supplyBusNo',
    label: '单据号',
    align: 'center',
    minWidth: 200,
    showOverflowTooltip: true,
  },
  { prop: 'statusEnumText', label: '审批状态', align: 'center', minWidth: 100 },
  { prop: 'typeEnumText', label: '单据类型', align: 'center', minWidth: 100 },
  { prop: 'practitionerName', label: '经手人', align: 'center', minWidth: 100 },
  { prop: 'applyTime', label: '制单日期', align: 'center', minWidth: 180 },
  { label: '操作', minWidth: 180, fixed: 'right', slot: 'actions', align: 'left' },
];

async function getLossOptions() {
  try {
    const data: InitLossReportResponse = await initLossReportApi();
    statusOptions.value = data.statusOptions || [];
    typeOptions.value = data.typeOptions || data.categoryOptions || [];
  } catch {
    statusOptions.value = [];
    typeOptions.value = [];
  }
}

async function getList() {
  try {
    loading.value = true;
    const params: PageLossReportParams = {
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword,
      busNo: queryParams.busNo,
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
      if (params[key as keyof PageLossReportParams] === undefined) {
        delete params[key as keyof PageLossReportParams];
      }
    });

    const responseData = await runPageLossReport(params);
    lossList.value = (responseData.content || []) as LossReportItem[];
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

function goCreateLoss() {
  router.push('/warehouse/loss-create');
}

function handleDelete(row: LossReportItem) {
  ElMessageBox.confirm('确定删除该报损单吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    runDeleteLoss({ busNo: row.supplyBusNo });
  });
}

const onReview = async (supplyBusNo: string, type: string) => {
  await submitForReview('loss', { supplyBusNo, type });
  ElMessage.success(type === 'submit' ? '提交审核成功' : '撤销审核成功');
  getList();
};

onMounted(() => {
  getLossOptions();
  getList();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="lossList"
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
      <template #toolbar-actions>
        <ElButton type="primary" @click="goCreateLoss"> 新增报损 </ElButton>
      </template>
      <template #actions="{ row }">
        <ElButton
          link
          type="primary"
          @click="
            detailDialogRef?.open(row.supplyBusNo, getLossStatusMeta(row.statusEnum).detailState)
          "
        >
          {{ getLossStatusMeta(row.statusEnum).detailState === 'view' ? '查看' : '编辑' }}
        </ElButton>
        <ElButton
          v-if="getLossStatusMeta(row.statusEnum).canDelete"
          link
          type="danger"
          @click="handleDelete(row)"
        >
          删除
        </ElButton>
        <ElButton
          v-if="getLossStatusMeta(row.statusEnum).canSubmit"
          link
          type="primary"
          @click="onReview(row.supplyBusNo, 'submit')"
        >
          提交审批
        </ElButton>
        <ElButton
          v-if="getLossStatusMeta(row.statusEnum).canCancelReview"
          link
          type="primary"
          @click="onReview(row.supplyBusNo, 'cancel')"
        >
          撤销审批
        </ElButton>
      </template>
    </TableLayout>
    <DetailDialog ref="detailDialogRef" @success="getList" />
  </Page>
</template>
