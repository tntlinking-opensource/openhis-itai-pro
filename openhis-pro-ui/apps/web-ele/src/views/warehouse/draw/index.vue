<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { DrawRequisitionItem, PageWarehouseDrawParams } from '#/api/warehouse/draw';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@zxplat/common-ui';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElMessage, ElMessageBox } from 'element-plus';

import { submitForReview } from '#/api/warehouse/common';
import {
  deleteRequisitionRequestApi,
  initDrawRequisitionApi,
  pageDrawRequisitionApi,
} from '#/api/warehouse/draw';
import { useRequest } from '#/hooks/use-request';

import DetailDialog from './detail/dialog.vue';
import { getDrawStatusMeta } from './detail/util';

defineOptions({
  name: 'WarehouseDraw',
});

const router = useRouter();
const detailDialogRef = ref<InstanceType<typeof DetailDialog> | null>(null);

const drawList = ref<DrawRequisitionItem[]>([]);
const loading = ref(false);
const total = ref(0);

const statusOptions = ref<ApiOptionItem[]>([]);
const typeOptions = ref<ApiOptionItem[]>([]);
const practitionerOptions = ref<ApiOptionItem[]>([]);

const { run: runPageDraw, loading: pageLoading } = useRequest(pageDrawRequisitionApi, {
  manual: true,
});

const { run: runInitOptions, loading: initLoading } = useRequest(initDrawRequisitionApi, {
  manual: true,
  onSuccess: (res) => {
    statusOptions.value = (res?.statusOptions ?? []) as ApiOptionItem[];
    typeOptions.value = (res?.categoryOptions ?? []) as ApiOptionItem[];
    practitionerOptions.value = (res?.practitionerOptions ?? []) as ApiOptionItem[];
  },
});

const { run: runDelete, loading: deleteLoading } = useRequest(deleteRequisitionRequestApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('删除成功');
    getList();
  },
});

const queryParams = reactive<PageWarehouseDrawParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: undefined,
  busNo: undefined,
  statusEnum: undefined,
  typeEnum: undefined,
  practitionerId: undefined,
  occurrenceTime: undefined,
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
    label: '领用类型',
    prop: 'typeEnum',
    type: 'select',
    multiple: false,
    options: typeOptions.value || [],
  },
  {
    label: '经手人',
    prop: 'practitionerId',
    type: 'select',
    multiple: false,
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
  { type: 'index', label: '序号', width: 60, align: 'center' },
  {
    prop: 'supplyBusNo',
    label: '单据号',
    align: 'center',
    width: 200,
    showOverflowTooltip: true,
  },
  { prop: 'statusEnumText', label: '审批状态', align: 'center', width: 100 },
  { prop: 'typeEnumText', label: '领用类型', align: 'center', width: 120 },
  { prop: 'practitionerName', label: '经手人', align: 'center', width: 100 },
  { prop: 'applicantName', label: '制单人', align: 'center', width: 100 },
  { prop: 'approverName', label: '审核人', align: 'center', width: 100 },
  { prop: 'applyTime', label: '制单日期', align: 'center', width: 180 },
  { prop: 'approvalTime', label: '审核日期', align: 'center', width: 180 },
  { label: '操作', minWidth: 180, fixed: 'right', align: 'left', slot: 'actions' },
];

async function getList() {
  try {
    loading.value = true;
    const params: PageWarehouseDrawParams = {
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword,
      busNo: queryParams.busNo,
      statusEnum: queryParams.statusEnum,
      typeEnum: queryParams.typeEnum,
      practitionerId: queryParams.practitionerId,
      dateRange: queryParams.occurrenceTime?.length
        ? {
            startTime: queryParams.occurrenceTime[0],
            endTime: queryParams.occurrenceTime[1],
          }
        : undefined,
    };
    Object.keys(params).forEach((key) => {
      if (params[key as keyof PageWarehouseDrawParams] === undefined) {
        delete params[key as keyof PageWarehouseDrawParams];
      }
    });
    const res = await runPageDraw(params);
    drawList.value = (res?.content || []) as DrawRequisitionItem[];
    total.value = res?.totalCount ?? 0;
  } catch {
    drawList.value = [];
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

function handleDelete(row: DrawRequisitionItem) {
  ElMessageBox.confirm('确定删除该领用单吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    runDelete({ busNo: row.supplyBusNo ?? row.busNo ?? '' });
  });
}

const onReview = async (supplyBusNo: string, type: string) => {
  await submitForReview('draw', { supplyBusNo, type });
  ElMessage.success(type === 'submit' ? '提交审核成功' : '撤销审核成功');
  getList();
};

onMounted(() => {
  runInitOptions();
  getList();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="drawList"
      :loading="loading || pageLoading || initLoading || deleteLoading"
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
      <template #actions="{ row }">
        <ElButton
          link
          type="primary"
          @click="
            detailDialogRef?.open(
              row.supplyBusNo ?? row.busNo,
              getDrawStatusMeta(row.statusEnum).detailState,
            )
          "
        >
          {{ getDrawStatusMeta(row.statusEnum).detailState === 'view' ? '查看' : '编辑' }}
        </ElButton>
        <ElButton
          v-if="getDrawStatusMeta(row.statusEnum).canDelete"
          link
          type="danger"
          @click="handleDelete(row)"
        >
          删除
        </ElButton>
        <ElButton
          v-if="getDrawStatusMeta(row.statusEnum).canSubmit"
          link
          type="primary"
          @click="onReview(row.supplyBusNo ?? row.busNo ?? '', 'submit')"
        >
          提交审批
        </ElButton>
        <ElButton
          v-if="getDrawStatusMeta(row.statusEnum).canCancelReview"
          link
          type="primary"
          @click="onReview(row.supplyBusNo ?? row.busNo ?? '', 'cancel')"
        >
          撤销审批
        </ElButton>
      </template>
      <template #toolbar-actions>
        <ElButton type="primary" @click="router.push('/warehouse/draw-create')">
          新增领用
        </ElButton>
      </template>
    </TableLayout>
    <DetailDialog ref="detailDialogRef" @success="getList" />
  </Page>
</template>
