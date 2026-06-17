<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  CatalogMappingWorkbenchStatus,
  PendingCatalogMappingRow,
} from '#/chs/api/catalog-workbench';
import type { ChargeType } from '#/api/core/common';
import type { PageParams } from '#/types/api-response';

import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElAlert, ElButton, ElMessage, ElProgress, ElStatistic, ElTag } from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import { pagePendingCatalogMappingsApi } from '#/chs/api';
import { useCatalogMappingBatchUpload } from '#/chs/composables/use-catalog-mapping-batch-upload';
import { formatCatalogListType } from '#/chs/constants/catalog-list-type';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';
import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ChsCatalogMappingWorkbenchPage' });

const router = useRouter();
const route = useRoute();

type WorkbenchRow = PendingCatalogMappingRow & { rowKey: string };

interface WorkbenchQuery extends PageParams {
  contractBusNo?: string;
  productTable?: string;
  mappingStatus?: CatalogMappingWorkbenchStatus;
  keyword?: string;
}

const PRODUCT_TABLE_OPTIONS = [
  { label: '全部', value: '' },
  { label: '药品', value: 'med_medication_definition' },
  { label: '诊疗', value: 'wkf_activity_definition' },
  { label: '耗材', value: 'adm_device_definition' },
];

const MAPPING_STATUS_OPTIONS = [
  { label: '待上传(有码未对照)', value: 'PENDING_UPLOAD' },
  { label: '已对照', value: 'MATCHED' },
  { label: '未选医保码', value: 'NO_YB_NO' },
];

const queryParams = ref<WorkbenchQuery>({
  pageNo: 1,
  pageSize: 20,
  contractBusNo: undefined,
  productTable: '',
  mappingStatus: 'PENDING_UPLOAD',
  keyword: undefined,
});

const tableData = ref<WorkbenchRow[]>([]);
const total = ref(0);
const summary = ref({ pending: 0, matched: 0, noYb: 0 });
const selectedRows = ref<WorkbenchRow[]>([]);
const chargeTypeOptions = ref<ChargeType[]>([]);
const progressDialogVisible = ref(false);

const batch = useCatalogMappingBatchUpload();

const tableColumns = computed<TableColumn[]>(() => [
  { type: 'selection', width: 48, align: 'center' },
  { prop: 'productTypeLabel', label: '类型', width: 72, align: 'center', headerAlign: 'center' },
  { prop: 'busNo', label: '院内编码', minWidth: 120, showOverflowTooltip: true },
  { prop: 'name', label: '名称', minWidth: 160, showOverflowTooltip: true },
  { prop: 'ybNo', label: '医保编码', minWidth: 140, showOverflowTooltip: true },
  {
    prop: 'listType',
    label: 'list_type',
    width: 88,
    align: 'center',
    headerAlign: 'center',
    slot: 'listType',
  },
  {
    prop: 'ybMatchFlag',
    label: '对码状态',
    width: 96,
    align: 'center',
    headerAlign: 'center',
    slot: 'ybMatchFlag',
  },
]);

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'select',
    prop: 'contractBusNo',
    label: '医保契约',
    placeholder: '请选择契约',
    clearable: false,
    multiple: false,
    options: chargeTypeOptions.value.map((item) => ({
      label: item.busNo ? `${item.label}（${item.busNo}）` : item.label,
      value: item.busNo,
    })),
  },
  {
    type: 'select',
    prop: 'mappingStatus',
    label: '对码状态',
    clearable: false,
    multiple: false,
    options: MAPPING_STATUS_OPTIONS,
  },
  {
    type: 'select',
    prop: 'productTable',
    label: '产品类型',
    clearable: false,
    multiple: false,
    options: PRODUCT_TABLE_OPTIONS,
  },
  {
    type: 'input',
    prop: 'keyword',
    label: '关键字',
    placeholder: '名称/编码/医保码',
    clearable: true,
  },
]);

const canBatchUpload = computed(
  () =>
    queryParams.value.mappingStatus === 'PENDING_UPLOAD' &&
    selectedRows.value.length > 0 &&
    !batch.running.value,
);

const { run: runPageQuery, loading: pageLoading } = useRequest(pagePendingCatalogMappingsApi, {
  manual: true,
  onSuccess: (data) => {
    tableData.value = (data.rows ?? []).map((row) => ({
      ...row,
      rowKey: `${row.productTable}:${row.productId}`,
    }));
    total.value = Number(data.total ?? 0);
    summary.value = {
      pending: Number(data.pendingUploadCount ?? 0),
      matched: Number(data.matchedCount ?? 0),
      noYb: Number(data.noYbNoCount ?? 0),
    };
    selectedRows.value = [];
  },
});

function buildPageParams(): WorkbenchQuery {
  const params: WorkbenchQuery = {
    pageNo: queryParams.value.pageNo,
    pageSize: queryParams.value.pageSize,
    contractBusNo: queryParams.value.contractBusNo,
    mappingStatus: queryParams.value.mappingStatus,
    keyword: queryParams.value.keyword || undefined,
  };
  if (queryParams.value.productTable) {
    params.productTable = queryParams.value.productTable;
  }
  return params;
}

async function loadList() {
  await runPageQuery(buildPageParams());
}

async function handleQuery(params: WorkbenchQuery) {
  queryParams.value = { ...queryParams.value, ...params, pageNo: 1 };
  await loadList();
}

async function handleReset() {
  queryParams.value = {
    pageNo: 1,
    pageSize: 20,
    contractBusNo: queryParams.value.contractBusNo,
    productTable: '',
    mappingStatus: 'PENDING_UPLOAD',
    keyword: undefined,
  };
  await loadList();
}

async function handlePagination(params: { pageNo: number; pageSize: number }) {
  queryParams.value.pageNo = params.pageNo;
  queryParams.value.pageSize = params.pageSize;
  await loadList();
}

function handleSelectionChange(rows: WorkbenchRow[]) {
  selectedRows.value = rows;
}

async function handleBatchUpload() {
  if (!queryParams.value.contractBusNo) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  const signedIn = await ensureInsuranceSignIn(queryParams.value.contractBusNo);
  if (!signedIn) return;

  progressDialogVisible.value = true;
  batch.reset();
  try {
    await batch.runSequentialUpload(queryParams.value.contractBusNo, selectedRows.value);
    if (batch.successCount.value > 0) {
      await loadList();
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '批量上传失败';
    ElMessage.error(msg);
    progressDialogVisible.value = false;
  }
}

function handleGoNationalCatalog() {
  void router.push({ path: '/chs/national-catalog' });
}

async function loadContracts() {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list ?? []);
  chargeTypeOptions.value = eligible;
  queryParams.value.contractBusNo = resolveDefaultInsuranceBusNo(
    eligible,
    queryParams.value.contractBusNo,
  );
}

onMounted(async () => {
  const q = route.query.contractBusNo;
  if (q !== undefined && q !== null && String(q).trim() !== '') {
    queryParams.value.contractBusNo = String(q);
  }
  await loadContracts();
  await loadList();
});
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      :table-data="tableData"
      :loading="pageLoading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      :show-top-query="true"
      :show-pagination="true"
      border
      stripe
      row-key="rowKey"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
      @selection-change="handleSelectionChange"
    >
      <template #toolbar-actions>
        <div class="mb-2 flex w-full flex-col gap-2">
          <ElAlert
            type="info"
            :closable="false"
            show-icon
            title="批量对码说明"
            description="国家平台 3301 不支持批量接口。本页对选中记录逐条调用 3301，并实时显示进度；单条失败不影响后续条目，可修正后重试。"
          />
          <div class="flex flex-wrap items-center gap-6">
            <ElStatistic title="待上传" :value="summary.pending" />
            <ElStatistic title="已对照" :value="summary.matched" />
            <ElStatistic title="未选码" :value="summary.noYb" />
          </div>
          <div class="flex flex-wrap gap-2">
            <ElButton type="primary" :disabled="!canBatchUpload" @click="handleBatchUpload">
              批量上传 3301（{{ selectedRows.length }} 条）
            </ElButton>
            <ElButton plain @click="handleGoNationalCatalog">国家目录查询</ElButton>
          </div>
        </div>
      </template>

      <template #listType="{ row }">
        {{ formatCatalogListType(row.listType) }}
      </template>

      <template #ybMatchFlag="{ row }">
        <ElTag v-if="row.ybMatchFlag" type="success" effect="light">已对照</ElTag>
        <ElTag v-else-if="row.ybNo" type="warning" effect="light">待上传</ElTag>
        <ElTag v-else type="info" effect="light">未选码</ElTag>
      </template>
    </TableLayout>

    <AppDialog
      v-model="progressDialogVisible"
      title="批量上传 3301"
      width="560px"
      :close-on-click-modal="false"
      :show-close="!batch.running.value"
    >
      <div class="space-y-4">
        <ElProgress
          :percentage="batch.percent.value"
          :status="
            batch.running.value ? undefined : batch.failCount.value > 0 ? 'warning' : 'success'
          "
          :stroke-width="18"
          striped
          striped-flow
        />
        <p class="text-sm text-gray-600">{{ batch.detailLine.value }}</p>
        <p v-if="batch.currentName.value && batch.running.value" class="text-sm">
          当前：{{ batch.currentName.value }}
        </p>
        <div class="flex gap-4 text-sm">
          <span>已完成 {{ batch.processed.value }} / {{ batch.total.value }}</span>
          <span class="text-green-600">成功 {{ batch.successCount.value }}</span>
          <span class="text-red-600">失败 {{ batch.failCount.value }}</span>
        </div>
      </div>
      <template #footer>
        <ElButton v-if="batch.running.value" type="warning" plain @click="batch.requestCancel">
          取消
        </ElButton>
        <ElButton :disabled="batch.running.value" @click="progressDialogVisible = false">
          关闭
        </ElButton>
      </template>
    </AppDialog>
  </Page>
</template>
