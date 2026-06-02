<script lang="ts" setup>
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { PageWarehousePurchaseParams, WarehousePurchaseItem } from '#/api/warehouse/purchase';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onActivated, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElMessage } from 'element-plus';

import { submitForReview } from '#/api/warehouse/common';
import {
  deleteWarehousePurchaseApi,
  initPharmacyWarehouseSuplyApi,
  pageWarehousePurchaseApi,
} from '#/api/warehouse/purchase';
import { useRequest } from '#/hooks/use-request';

import DetailDialog from './detail/dialog.vue';
import { getPurchaseStatusMeta } from './detail/util';

defineOptions({
  name: 'PurchaseManage',
});
const router = useRouter();
const tableLayoutRef = ref<InstanceType<typeof TableLayout> | null>(null);
const detailDialogRef = reactive<{
  close: () => void;
  open: (supplyBusNo: string, state: 'edit' | 'return' | 'view', isReturnOrder?: boolean) => void;
}>({
  open: () => {},
  close: () => {},
});
const purchaseinventoryList = ref<WarehousePurchaseItem[]>([]);
const loading = ref<boolean>(false);

const total = ref<number>(0);
const supplyTypeOptions = ref<ApiOptionItem[]>([]); // 入库单据类型
const practitionerListOptions = ref<ApiOptionItem[]>([]); // 查询经手人列表
const supplierListOptions = ref<ApiOptionItem[]>([]); // 供应商列表
const supplyStatusOptions = ref<ApiOptionItem[]>([]); // 审批状态

// 记录树形表格中展开的主单据（按单据号），用于刷新后恢复展开状态
const expandedBusNoList = ref<string[]>([]);

const { run: runPageWarehousePurchase, loading: pageLoading } = useRequest(
  pageWarehousePurchaseApi,
  {
    manual: true,
  },
);

const { run: runDeletePurchase, loading: deleteLoading } = useRequest(deleteWarehousePurchaseApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('删除成功');
    getList();
  },
});

watch(
  pageLoading,
  (val) => {
    loading.value = val ?? false;
  },
  { immediate: true },
);

const queryParams = reactive<PageWarehousePurchaseParams>({
  pageNo: 1,
  pageSize: 50,
  keyword: undefined,
  typeEnum: undefined,
  supplierId: undefined,
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
    options: supplyStatusOptions.value || [],
  },
  {
    label: '单据类型',
    prop: 'typeEnum',
    type: 'select',
    multiple: false,
    options: supplyTypeOptions.value || [],
  },
  {
    label: '供应商',
    prop: 'supplierId',
    type: 'select',
    multiple: false,
    options: supplierListOptions.value || [],
    filterable: true,
  },
  {
    label: '经手人',
    prop: 'practitionerId',
    type: 'select',
    multiple: false,
    options: practitionerListOptions.value || [],
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
    width: 50,
    align: 'center',
  },
  {
    prop: 'supplyBusNo',
    label: '单据号',
    align: 'center',
    width: 200,
    showOverflowTooltip: true,
    treeNode: true,
  },
  {
    prop: 'hasReturn',
    label: '退库状态',
    align: 'center',
    width: 140,
    slot: 'return',
  },
  { prop: 'statusEnumText', label: '审批状态', align: 'center', width: 100 },
  { prop: 'typeEnumText', label: '单据类型', align: 'center', width: 100 },
  {
    prop: 'supplierName',
    label: '供应商',
    align: 'center',
    showOverflowTooltip: true,
    width: 200,
  },
  {
    prop: 'practitionerId_dictText',
    label: '经手人',
    align: 'center',
    showOverflowTooltip: true,
    width: 100,
  },
  { prop: 'applicantId_dictText', label: '制单人', align: 'center', width: 100 },
  { prop: 'approverId_dictText', label: '审核人', align: 'center', width: 100 },
  { prop: 'createTime', label: '制单日期', align: 'center', width: 180, slot: 'createTime' },
  { prop: 'approvalTime', label: '审核日期', align: 'center', width: 180, slot: 'approvalTime' },
  { label: '操作', minWidth: 180, fixed: 'right', slot: 'actions', align: 'left' },
];

/**
 * 格式化时间
 */
function parseTime(time?: Date | number | string): string {
  if (!time) return '';
  return formatDateTime(time);
}

async function getPurchaseinventoryTypeList() {
  try {
    const data = await initPharmacyWarehouseSuplyApi();
    supplierListOptions.value = data.supplierOptions;
    practitionerListOptions.value = data.practitionerOptions;
    supplyTypeOptions.value = data.categoryOptions;
    supplyStatusOptions.value = data.statusOptions;
  } catch {
    supplyTypeOptions.value = [];
    practitionerListOptions.value = [];
    supplierListOptions.value = [];
    supplyStatusOptions.value = [];
  }
}

async function getList() {
  try {
    const gridApi = (tableLayoutRef.value as any)?.gridApi?.value;
    if (gridApi && typeof gridApi.getTreeExpandRecords === 'function') {
      const records = gridApi.getTreeExpandRecords() as any[];
      expandedBusNoList.value = records
        .map((item) => (item as any).supplyBusNo)
        .filter((val) => typeof val === 'string' && val);
    }
    const params: PageWarehousePurchaseParams = {
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      keyword: queryParams.keyword,
      statusEnum: queryParams.statusEnum,
      typeEnum: queryParams.typeEnum,
      supplierId: queryParams.supplierId,
      practitionerId: queryParams.practitionerId,
      dateRange: queryParams.occurrenceTime?.length
        ? {
            startTime: queryParams.occurrenceTime?.[0],
            endTime: queryParams.occurrenceTime?.[1],
          }
        : undefined,
    };

    Object.keys(params).forEach((key) => {
      if (params[key as keyof PageWarehousePurchaseParams] === undefined) {
        delete params[key as keyof PageWarehousePurchaseParams];
      }
    });

    const responseData = await runPageWarehousePurchase(params);

    purchaseinventoryList.value = (responseData.content || []).map(
      (record): WarehousePurchaseItem => {
        const item: WarehousePurchaseItem = {
          ...record,
          createTime: record.applyTime || record.createTime,
        };
        return item;
      },
    );

    total.value = responseData.totalCount || 0;

    await nextTick();
    const nextGridApi = (tableLayoutRef.value as any)?.gridApi?.value;
    if (
      nextGridApi &&
      Array.isArray(expandedBusNoList.value) &&
      expandedBusNoList.value.length > 0 &&
      typeof nextGridApi.setTreeExpand === 'function'
    ) {
      const needExpandRows = purchaseinventoryList.value.filter((row) =>
        expandedBusNoList.value.includes(row.supplyBusNo),
      );
      if (needExpandRows.length > 0) {
        needExpandRows.forEach((row) => {
          nextGridApi.setTreeExpand(row, true);
        });
      }
    }
  } catch {
    purchaseinventoryList.value = [];
    total.value = 0;
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

const onReview = async (supplyBusNo: string, type: string) => {
  await submitForReview('purchase', { supplyBusNo, type });
  ElMessage.success(type === 'submit' ? '提交审核成功' : '撤销审核成功');
  getList();
};

const addPurchaseOrder = () => {
  router.push({
    path: 'purchase/in',
  });
};

onMounted(() => {
  getPurchaseinventoryTypeList();
  getList();
});

onActivated(() => {
  getList();
});
</script>

<template>
  <Page>
    <TableLayout
      ref="tableLayoutRef"
      :table-data="purchaseinventoryList"
      :loading="loading || pageLoading || deleteLoading"
      :total="total"
      :query-params="queryParams as any"
      :form-items="formItems"
      :table-columns="tableColumns"
      row-key="supplyBusNo"
      :show-top-query="true"
      :show-pagination="true"
      :tree="true"
      :tree-config="{
        children: 'purchaseRequestPageDtoList',
        line: false,
      }"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #createTime="{ row }">
        <span>{{ parseTime(row.createTime) }}</span>
      </template>
      <template #approvalTime="{ row }">
        <span>{{ parseTime(row.approvalTime) }}</span>
      </template>
      <template #return="{ row }">
        <span>{{ row.hasReturn ? '已退库' : '-' }}</span>
      </template>
      <template #actions="{ row }">
        <!-- <ElButton link type="primary"  @click="detailDialogRef.open(row.supplyBusNo,'view')">
          详情
        </ElButton> -->
        <ElButton
          link
          type="primary"
          @click="
            detailDialogRef.open(
              row.supplyBusNo,
              getPurchaseStatusMeta(row.statusEnum).detailState,
              row.typeEnum === '5',
            )
          "
        >
          {{ getPurchaseStatusMeta(row.statusEnum).detailState === 'view' ? '查看' : '编辑' }}
        </ElButton>
        <ElButton
          v-if="getPurchaseStatusMeta(row.statusEnum).canDelete"
          link
          type="danger"
          @click="runDeletePurchase(row.supplyBusNo)"
        >
          删除
        </ElButton>
        <ElButton
          v-if="getPurchaseStatusMeta(row.statusEnum).canReturn && row.typeEnum !== '5'"
          link
          type="primary"
          @click="detailDialogRef.open(row.supplyBusNo, 'return')"
        >
          退库
        </ElButton>
        <ElButton
          v-if="getPurchaseStatusMeta(row.statusEnum).canSubmit"
          link
          type="primary"
          @click="onReview(row.supplyBusNo, 'submit')"
        >
          提交审批
        </ElButton>
        <ElButton
          v-if="getPurchaseStatusMeta(row.statusEnum).canCancelReview"
          link
          type="primary"
          @click="onReview(row.supplyBusNo, 'cancel')"
        >
          撤销审批
        </ElButton>
      </template>
      <template #toolbar-actions>
        <ElButton type="primary" @click="addPurchaseOrder"> 采购入库 </ElButton>
        <!-- <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete">
          删除
        </el-button> -->
      </template>
    </TableLayout>
    <DetailDialog ref="detailDialogRef" @success="getList" />
  </Page>
</template>
