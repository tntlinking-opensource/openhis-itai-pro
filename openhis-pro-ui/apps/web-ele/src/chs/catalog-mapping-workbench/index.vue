<script setup lang="ts">
/**
 * 目录对码工作台：左院内 / 右平台，对照查看并批量 3301 / 3302。
 */
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  CatalogMappingWorkbenchStatus,
  HospitalMatchFilter,
  InstitutionCatalogMatchRow,
  PendingCatalogMappingRow,
} from '#/chs/api/catalog-workbench';
import type { ChargeType } from '#/api/core/common';
import type { PageParams } from '#/types/api-response';

import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElAlert,
  ElButton,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElProgress,
  ElSelect,
  ElStatistic,
  ElTag,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  pagePendingCatalogMappingsApi,
  queryInstitutionCatalogMatchPageApi,
  refreshFromPlatform1317Api,
} from '#/chs/api';
import { useCatalogMappingBatchRevoke } from '#/chs/composables/use-catalog-mapping-batch-revoke';
import { useCatalogMappingBatchUpload } from '#/chs/composables/use-catalog-mapping-batch-upload';
import {
  formatValiFlag,
  YB_CATALOG_LIST_TYPE_OPTIONS,
  YB_VALI_FLAG_OPTIONS,
} from '#/chs/constants/catalog-list-type';
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
type PlatformRow = InstitutionCatalogMatchRow & { rowKey: string };

interface WorkbenchQuery extends PageParams {
  contractBusNo?: string;
  productTable?: string;
  mappingStatus?: CatalogMappingWorkbenchStatus;
  keyword?: string;
}

interface PlatformMatchQuery extends PageParams {
  contractBusNo?: string;
  listType?: string;
  valiFlag?: string;
  keyword?: string;
  hospitalMatchFilter?: HospitalMatchFilter;
}

const PRODUCT_TABLE_OPTIONS = [
  { label: '全部', value: '' },
  { label: '药品', value: 'med_medication_definition' },
  { label: '诊疗', value: 'wkf_activity_definition' },
  { label: '耗材', value: 'adm_device_definition' },
];

/** 左侧只查相对平台的待处理；已在右侧镜像的不出现 */
const MAPPING_STATUS_OPTIONS = [
  { label: '全部待处理(平台无)', value: 'NOT_ON_PLATFORM' },
  { label: '待上传(有码可3301)', value: 'PENDING_UPLOAD' },
  { label: '未选医保码', value: 'NO_YB_NO' },
];

const LIST_TYPE_FILTER_OPTIONS = [
  { label: '全部', value: '' },
  ...YB_CATALOG_LIST_TYPE_OPTIONS.map((item) => ({
    label: item.label,
    value: item.value,
  })),
];

const VALI_FLAG_FILTER_OPTIONS = [
  { label: '全部', value: '' },
  ...YB_VALI_FLAG_OPTIONS.map((item) => ({
    label: item.label,
    value: item.value,
  })),
];

const HOSPITAL_MATCH_FILTER_OPTIONS = [
  { label: '全部已对照', value: '' },
  { label: '院内启用', value: 'ACTIVE' },
  { label: '院内不用/无匹配', value: 'UNUSED' },
];

/** 顶部共享：契约 + 关键字（左右同查） */
const sharedContractBusNo = ref<string | undefined>();
const sharedKeyword = ref('');

const queryParams = ref<WorkbenchQuery>({
  pageNo: 1,
  pageSize: 20,
  contractBusNo: undefined,
  productTable: '',
  mappingStatus: 'NOT_ON_PLATFORM',
  keyword: undefined,
});

const platformQuery = ref<PlatformMatchQuery>({
  pageNo: 1,
  pageSize: 20,
  contractBusNo: undefined,
  listType: '',
  valiFlag: '1',
  hospitalMatchFilter: '',
  keyword: undefined,
});

const tableData = ref<WorkbenchRow[]>([]);
const total = ref(0);
const summary = ref({ pending: 0, matched: 0, noYb: 0 });
const selectedRows = ref<WorkbenchRow[]>([]);
const chargeTypeOptions = ref<ChargeType[]>([]);
const progressDialogVisible = ref(false);
const revokeProgressDialogVisible = ref(false);

const platformRows = ref<PlatformRow[]>([]);
const platformTotal = ref(0);
const selectedPlatformRows = ref<PlatformRow[]>([]);
const syncing1317 = ref(false);

const batch = useCatalogMappingBatchUpload();
const revokeBatch = useCatalogMappingBatchRevoke();

const tableColumns = computed<TableColumn[]>(() => [
  { type: 'selection', width: 42, align: 'center' },
  { prop: 'productTypeLabel', label: '类型', width: 64, align: 'center', headerAlign: 'center' },
  { prop: 'busNo', label: '院内编码', minWidth: 100, showOverflowTooltip: true },
  { prop: 'name', label: '名称', minWidth: 120, showOverflowTooltip: true },
  { prop: 'ybNo', label: '医保编码', minWidth: 110, showOverflowTooltip: true },
  {
    prop: 'ybMatchFlag',
    label: '状态',
    width: 88,
    align: 'center',
    headerAlign: 'center',
    slot: 'ybMatchFlag',
  },
]);

const platformColumns = computed<TableColumn[]>(() => [
  { type: 'selection', width: 42, align: 'center' },
  { prop: 'medinsListCodg', label: '院内编码', minWidth: 100, showOverflowTooltip: true },
  { prop: 'medinsListName', label: '名称', minWidth: 120, showOverflowTooltip: true },
  { prop: 'medListCodg', label: '医保编码', minWidth: 110, showOverflowTooltip: true },
  {
    prop: 'hospitalMatchStatus',
    label: '院内',
    width: 96,
    align: 'center',
    headerAlign: 'center',
    slot: 'hospitalMatchStatus',
  },
  {
    prop: 'valiFlag',
    label: '有效',
    width: 64,
    align: 'center',
    headerAlign: 'center',
    slot: 'valiFlag',
  },
]);

/** 左侧：仅院内侧筛选（契约/关键字在顶部共享） */
const formItems = computed<FormItemConfig[]>(() => [
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
]);

/** 右侧：仅平台侧筛选 */
const platformFormItems = computed<FormItemConfig[]>(() => [
  {
    type: 'select',
    prop: 'hospitalMatchFilter',
    label: '院内状态',
    clearable: false,
    multiple: false,
    options: HOSPITAL_MATCH_FILTER_OPTIONS,
  },
  {
    type: 'select',
    prop: 'listType',
    label: '目录类别',
    clearable: false,
    multiple: false,
    options: LIST_TYPE_FILTER_OPTIONS,
  },
  {
    type: 'select',
    prop: 'valiFlag',
    label: '有效标志',
    clearable: false,
    multiple: false,
    options: VALI_FLAG_FILTER_OPTIONS,
  },
]);

const canBatchUpload = computed(
  () => selectedRows.value.some((row) => !!row.ybNo) && !batch.running.value,
);

const canBatchRevoke = computed(
  () => selectedPlatformRows.value.length > 0 && !revokeBatch.running.value && !syncing1317.value,
);

const contractOptions = computed(() =>
  chargeTypeOptions.value.map((item) => ({
    label: item.busNo ? `${item.label}（${item.busNo}）` : item.label,
    value: item.busNo as string,
  })),
);

function syncSharedToQueries() {
  queryParams.value.contractBusNo = sharedContractBusNo.value;
  platformQuery.value.contractBusNo = sharedContractBusNo.value;
  const kw = sharedKeyword.value.trim() || undefined;
  queryParams.value.keyword = kw;
  platformQuery.value.keyword = kw;
}

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
      matched: Number(data.notOnPlatformCount ?? data.matchedCount ?? 0),
      noYb: Number(data.noYbNoCount ?? 0),
    };
    selectedRows.value = [];
  },
});

const { run: runPlatformQuery, loading: platformLoading } = useRequest(
  queryInstitutionCatalogMatchPageApi,
  {
    manual: true,
    onSuccess: (data) => {
      platformRows.value = (data.rows ?? []).map((row) => ({
        ...row,
        rowKey: String(row.id ?? `${row.medinsListCodg}:${row.medListCodg}`),
      }));
      platformTotal.value = Number(data.total ?? 0);
      selectedPlatformRows.value = [];
    },
  },
);

/**
 * 组装院内待对码分页参数。
 */
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

/**
 * 组装平台已对照本地镜像分页参数。
 */
function buildPlatformParams(): PlatformMatchQuery {
  const params: PlatformMatchQuery = {
    pageNo: platformQuery.value.pageNo,
    pageSize: platformQuery.value.pageSize,
    contractBusNo: platformQuery.value.contractBusNo,
    keyword: platformQuery.value.keyword || undefined,
  };
  if (platformQuery.value.listType) {
    params.listType = platformQuery.value.listType;
  }
  if (platformQuery.value.valiFlag) {
    params.valiFlag = platformQuery.value.valiFlag;
  }
  if (platformQuery.value.hospitalMatchFilter) {
    params.hospitalMatchFilter = platformQuery.value.hospitalMatchFilter;
  }
  return params;
}

async function loadList() {
  syncSharedToQueries();
  if (!queryParams.value.contractBusNo) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  await runPageQuery(buildPageParams());
}

async function loadPlatformList() {
  syncSharedToQueries();
  if (!platformQuery.value.contractBusNo) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  await runPlatformQuery(buildPlatformParams());
}

/** 顶部「对照查询」：左右同关键字、同契约一起刷新 */
async function handleCompareQuery() {
  queryParams.value.pageNo = 1;
  platformQuery.value.pageNo = 1;
  syncSharedToQueries();
  await Promise.all([loadList(), loadPlatformList()]);
}

async function handleQuery(params: Record<string, any>) {
  queryParams.value = {
    ...queryParams.value,
    ...params,
    pageNo: 1,
    contractBusNo: sharedContractBusNo.value,
    keyword: sharedKeyword.value.trim() || undefined,
  };
  await loadList();
}

async function handleReset() {
  queryParams.value = {
    pageNo: 1,
    pageSize: 20,
    contractBusNo: sharedContractBusNo.value,
    productTable: '',
    mappingStatus: 'NOT_ON_PLATFORM',
    keyword: sharedKeyword.value.trim() || undefined,
  };
  await loadList();
}

async function handlePagination(pagination: { limit: number; page: number }) {
  queryParams.value.pageNo = pagination.page;
  queryParams.value.pageSize = pagination.limit;
  await loadList();
}

async function handlePlatformQuery(params: Record<string, any>) {
  platformQuery.value = {
    ...platformQuery.value,
    ...params,
    pageNo: 1,
    contractBusNo: sharedContractBusNo.value,
    keyword: sharedKeyword.value.trim() || undefined,
  };
  await loadPlatformList();
}

async function handlePlatformReset() {
  platformQuery.value = {
    pageNo: 1,
    pageSize: 20,
    contractBusNo: sharedContractBusNo.value,
    listType: '',
    valiFlag: '1',
    hospitalMatchFilter: '',
    keyword: sharedKeyword.value.trim() || undefined,
  };
  await loadPlatformList();
}

async function handlePlatformPagination(pagination: { limit: number; page: number }) {
  platformQuery.value.pageNo = pagination.page;
  platformQuery.value.pageSize = pagination.limit;
  await loadPlatformList();
}

function handleSelectionChange(rows: WorkbenchRow[]) {
  selectedRows.value = rows;
}

function handlePlatformSelectionChange(rows: PlatformRow[]) {
  selectedPlatformRows.value = rows;
}

function formatHospitalMatchStatus(status?: string) {
  if (status === 'ACTIVE') return '院内启用';
  if (status === 'INACTIVE') return '院内停用';
  if (status === 'MISSING') return '院内无匹配';
  return '-';
}

async function handleBatchUpload() {
  syncSharedToQueries();
  if (!queryParams.value.contractBusNo) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  const signedIn = await ensureInsuranceSignIn(queryParams.value.contractBusNo);
  if (!signedIn) return;

  const toUpload = selectedRows.value.filter((row) => !!row.ybNo);
  if (toUpload.length === 0) {
    ElMessage.warning('请勾选已有医保编码的待处理行');
    return;
  }
  const skipped = selectedRows.value.length - toUpload.length;
  if (skipped > 0) {
    ElMessage.info(`已跳过 ${skipped} 条未选医保码的行`);
  }

  progressDialogVisible.value = true;
  batch.reset();
  try {
    await batch.runSequentialUpload(queryParams.value.contractBusNo, toUpload);
    if (batch.successCount.value > 0) {
      await Promise.all([loadList(), loadPlatformList()]);
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '批量上传失败';
    ElMessage.error(msg);
    progressDialogVisible.value = false;
  }
}

async function handleBatchRevoke() {
  syncSharedToQueries();
  if (!platformQuery.value.contractBusNo) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认对选中的 ${selectedPlatformRows.value.length} 条平台已对照执行 3302 撤销？\n院内停用/无匹配项可直接撤销；成功后本地镜像标为无效。`,
      '批量撤销对照(3302)',
      { type: 'warning', confirmButtonText: '确认撤销', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }

  const signedIn = await ensureInsuranceSignIn(platformQuery.value.contractBusNo);
  if (!signedIn) return;

  revokeProgressDialogVisible.value = true;
  revokeBatch.reset();
  try {
    await revokeBatch.runSequentialRevoke(
      platformQuery.value.contractBusNo,
      selectedPlatformRows.value,
    );
    if (revokeBatch.successCount.value > 0) {
      await Promise.all([loadPlatformList(), loadList()]);
    }
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '批量撤销失败';
    ElMessage.error(msg);
    revokeProgressDialogVisible.value = false;
  }
}

/**
 * 从医保平台同步 1317：落本地镜像并正向回写院内 yb_no/yb_match_flag。
 */
async function handleSync1317() {
  syncSharedToQueries();
  if (!platformQuery.value.contractBusNo) {
    ElMessage.warning('请选择医保契约');
    return;
  }
  const signedIn = await ensureInsuranceSignIn(platformQuery.value.contractBusNo);
  if (!signedIn) return;

  syncing1317.value = true;
  try {
    const result = await refreshFromPlatform1317Api({
      // 全量同步：不带当前列表关键字/目录类别，避免只拉到筛选子集
      contractBusNo: platformQuery.value.contractBusNo,
      valiFlag: '1',
    });
    ElMessage.success(
      `同步完成：落库 ${result.upserted ?? 0}，回写院内 ${result.writtenBack ?? 0}，跳过 ${result.skipped ?? 0}` +
        `（${result.pagesFetched ?? 0} 页` +
        (result.platformRecordCounts != null
          ? `，平台宣称共 ${result.platformRecordCounts} 条`
          : '') +
        `）`,
    );
    await Promise.all([loadPlatformList(), loadList()]);
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '1317 同步失败';
    ElMessage.error(msg);
  } finally {
    syncing1317.value = false;
  }
}

function handleGoNationalCatalog() {
  void router.push({ path: '/chs/national-catalog' });
}

async function loadContracts() {
  const list = await getChargeTypeListApi();
  const eligible = filterInsuranceChargeTypes(list ?? []);
  chargeTypeOptions.value = eligible;
  const busNo = resolveDefaultInsuranceBusNo(eligible, sharedContractBusNo.value);
  sharedContractBusNo.value = busNo;
  syncSharedToQueries();
}

watch(sharedContractBusNo, (busNo) => {
  queryParams.value.contractBusNo = busNo;
  platformQuery.value.contractBusNo = busNo;
});

onMounted(async () => {
  const q = route.query.contractBusNo;
  if (q !== undefined && q !== null && String(q).trim() !== '') {
    sharedContractBusNo.value = String(q);
  }
  await loadContracts();
  await handleCompareQuery();
});
</script>

<template>
  <Page auto-content-height content-class="catalog-mapping-workbench">
    <div class="workbench-root">
      <div class="workbench-toolbar">
        <ElAlert
          class="workbench-alert"
          type="info"
          :closable="false"
          show-icon
          title="左右对照"
          description="左侧=院内有、平台镜像没有（相对 1317 待处理）；右侧=平台已对照。有医保码可批量 3301。先同步再对照。"
        />
        <div class="workbench-toolbar-row">
          <div class="workbench-field">
            <span class="workbench-label">医保契约</span>
            <ElSelect
              v-model="sharedContractBusNo"
              class="workbench-contract"
              placeholder="请选择契约"
              filterable
            >
              <ElOption
                v-for="opt in contractOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </ElSelect>
          </div>
          <div class="workbench-field grow">
            <span class="workbench-label">关键字</span>
            <ElInput
              v-model="sharedKeyword"
              clearable
              placeholder="两边同查：名称 / 院内编码 / 医保码"
              @keyup.enter="handleCompareQuery"
            />
          </div>
          <ElButton type="primary" @click="handleCompareQuery">对照查询</ElButton>
          <ElButton :loading="syncing1317" @click="handleSync1317">从平台同步(1317)</ElButton>
          <ElButton plain @click="handleGoNationalCatalog">国家目录</ElButton>
        </div>
      </div>

      <div class="workbench-split">
        <section class="workbench-pane">
          <header class="workbench-pane-header">
            <div>
              <h3 class="workbench-pane-title">院内待处理</h3>
              <p class="workbench-pane-desc">相对平台：右侧没有的院内品项；有码可批量 3301</p>
            </div>
            <div class="workbench-stats">
              <ElStatistic title="待处理" :value="summary.matched" />
              <ElStatistic title="可上传" :value="summary.pending" />
              <ElStatistic title="未选码" :value="summary.noYb" />
            </div>
          </header>
          <div class="workbench-pane-actions">
            <ElButton type="primary" :disabled="!canBatchUpload" @click="handleBatchUpload">
              批量上传 3301（{{ selectedRows.length }}）
            </ElButton>
          </div>
          <div class="workbench-pane-body">
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
              <template #ybMatchFlag="{ row }">
                <ElTag v-if="row.ybNo" type="warning" effect="light">待上传</ElTag>
                <ElTag v-else type="info" effect="light">未选码</ElTag>
              </template>
            </TableLayout>
          </div>
        </section>

        <section class="workbench-pane">
          <header class="workbench-pane-header">
            <div>
              <h3 class="workbench-pane-title">平台已对照</h3>
              <p class="workbench-pane-desc">
                以 1317 平台返回为准；院内不用/无匹配可批量撤销 3302
              </p>
            </div>
            <div class="workbench-stats">
              <ElStatistic title="平台已对照" :value="platformTotal" />
            </div>
          </header>
          <div class="workbench-pane-actions">
            <ElButton type="danger" plain :disabled="!canBatchRevoke" @click="handleBatchRevoke">
              批量撤销 3302（{{ selectedPlatformRows.length }}）
            </ElButton>
          </div>
          <div class="workbench-pane-body">
            <TableLayout
              :table-data="platformRows"
              :loading="platformLoading || syncing1317 || revokeBatch.running.value"
              :total="platformTotal"
              :query-params="platformQuery"
              :form-items="platformFormItems"
              :table-columns="platformColumns"
              :show-top-query="true"
              :show-pagination="true"
              border
              stripe
              row-key="rowKey"
              @query="handlePlatformQuery"
              @reset="handlePlatformReset"
              @pagination="handlePlatformPagination"
              @selection-change="handlePlatformSelectionChange"
            >
              <template #hospitalMatchStatus="{ row }">
                <ElTag v-if="row.hospitalMatchStatus === 'ACTIVE'" type="success" effect="light">
                  院内启用
                </ElTag>
                <ElTag
                  v-else-if="row.hospitalMatchStatus === 'INACTIVE'"
                  type="warning"
                  effect="light"
                >
                  院内停用
                </ElTag>
                <ElTag
                  v-else-if="row.hospitalMatchStatus === 'MISSING'"
                  type="danger"
                  effect="light"
                >
                  院内无匹配
                </ElTag>
                <span v-else>{{ formatHospitalMatchStatus(row.hospitalMatchStatus) }}</span>
              </template>

              <template #valiFlag="{ row }">
                {{ formatValiFlag(row.valiFlag) }}
              </template>
            </TableLayout>
          </div>
        </section>
      </div>
    </div>

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

    <AppDialog
      v-model="revokeProgressDialogVisible"
      title="批量撤销 3302"
      width="560px"
      :close-on-click-modal="false"
      :show-close="!revokeBatch.running.value"
    >
      <div class="space-y-4">
        <ElProgress
          :percentage="revokeBatch.percent.value"
          :status="
            revokeBatch.running.value
              ? undefined
              : revokeBatch.failCount.value > 0
                ? 'warning'
                : 'success'
          "
          :stroke-width="18"
          striped
          striped-flow
        />
        <p class="text-sm text-gray-600">{{ revokeBatch.detailLine.value }}</p>
        <p v-if="revokeBatch.currentName.value && revokeBatch.running.value" class="text-sm">
          当前：{{ revokeBatch.currentName.value }}
        </p>
        <div class="flex gap-4 text-sm">
          <span>已完成 {{ revokeBatch.processed.value }} / {{ revokeBatch.total.value }}</span>
          <span class="text-green-600">成功 {{ revokeBatch.successCount.value }}</span>
          <span class="text-red-600">失败 {{ revokeBatch.failCount.value }}</span>
        </div>
      </div>
      <template #footer>
        <ElButton
          v-if="revokeBatch.running.value"
          type="warning"
          plain
          @click="revokeBatch.requestCancel"
        >
          取消
        </ElButton>
        <ElButton
          :disabled="revokeBatch.running.value"
          @click="revokeProgressDialogVisible = false"
        >
          关闭
        </ElButton>
      </template>
    </AppDialog>
  </Page>
</template>

<style scoped>
.workbench-root {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
}

.workbench-toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex-shrink: 0;
}

.workbench-alert :deep(.el-alert__description) {
  margin-top: 2px;
}

.workbench-toolbar-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.workbench-field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 220px;
}

.workbench-field.grow {
  flex: 1 1 280px;
}

.workbench-label {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  white-space: nowrap;
}

.workbench-contract {
  width: 280px;
}

.workbench-split {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.workbench-pane {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  overflow: hidden;
}

.workbench-pane-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 12px 0;
  flex-shrink: 0;
}

.workbench-pane-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.3;
}

.workbench-pane-desc {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.workbench-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.workbench-pane-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 12px;
  flex-shrink: 0;
}

.workbench-pane-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0 8px 8px;
}

@media (max-width: 1100px) {
  .workbench-split {
    flex-direction: column;
  }

  .workbench-pane {
    min-height: 420px;
  }
}
</style>
