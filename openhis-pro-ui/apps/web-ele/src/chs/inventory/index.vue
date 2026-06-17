<script setup lang="ts">
import type {
  CompareLocalVsPlatformResult,
  InitializeInventoryResult,
  InvCompareRow,
  LocalInvUploadRecord,
  PlatformInvQueryResult,
} from '#/chs/api';

import { onMounted, ref, computed } from 'vue';

import { Page } from '@vben/common-ui';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getChargeTypeListApi } from '#/api/core/common';
import { initPharmacyStocktakeWorkbenchApi } from '#/api/inventory/pharmacy/stocktake';
import { initWarehouseStocktakeWorkbenchApi } from '#/api/inventory/warehouse/stocktake';
import {
  compareLocalVsPlatformApi,
  deleteInvData3507Api,
  listLocalInvUploadRecordsApi,
  queryPlatformInboundTrace3512Api,
  queryPlatformInvChange3509Api,
  queryPlatformInventory3508Api,
  queryPlatformPurchase3510Api,
  queryPlatformSale3511Api,
  queryPlatformSaleTrace3513Api,
  retryPendingInvUploadsApi,
  uploadStocktake3501Api,
  uploadInvChange3502Api,
  initializeInventoryApi,
} from '#/chs/api';
import {
  filterInsuranceChargeTypes,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';
import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';

defineOptions({ name: 'ChsInventoryOpsPage' });

type QueryTab = '3508' | '3509' | '3510' | '3511' | '3512' | '3513';
type OpsTab = 'local' | 'compare' | '3501' | '3502' | '3507' | 'init' | QueryTab;

const activeTab = ref<OpsTab>('local');
const contractBusNo = ref<string>();
const chargeTypeOptions = ref<Array<{ busNo?: string; label?: string }>>([]);
const loadingChargeTypes = ref(false);

const localInfno = ref('');
const localSourceType = ref('');
const localRecords = ref<LocalInvUploadRecord[]>([]);
const localLoading = ref(false);

const queryDateRange = ref<[string, string]>();
const queryMedinsListCodg = ref('');
const queryFixmedinsBchno = ref('');
const queryMedListCodg = ref('');
const queryCertno = ref('');
const queryDrugTracCodg = ref('');
const platformRows = ref<Array<Record<string, unknown>>>([]);
const platformLoading = ref(false);
const platformInfno = ref<string>();

const compareQueryInfno = ref<'3508' | '3509' | '3510' | '3511' | '3512' | '3513'>('3511');
const compareLoading = ref(false);
const compareResult = ref<CompareLocalVsPlatformResult | null>(null);
const compareRows = ref<InvCompareRow[]>([]);

const compareScopeOptions = [
  { label: '3508 库存 ↔ 本地3501', value: '3508' },
  { label: '3509 变更 ↔ 本地3502', value: '3509' },
  { label: '3510 采购 ↔ 本地3503', value: '3510' },
  { label: '3511 销售 ↔ 本地3505', value: '3511' },
  { label: '3512 入库追溯 ↔ 本地3502/3503', value: '3512' },
  { label: '3513 销售追溯 ↔ 本地3505/3506', value: '3513' },
] as const;

const isTraceCompareScope = computed(
  () => compareQueryInfno.value === '3512' || compareQueryInfno.value === '3513',
);

const stocktake3501Loading = ref(false);
const stocktakeForm = ref({
  medListCodg: '',
  fixmedinsHilistId: '',
  fixmedinsHilistName: '',
  fixmedinsBchno: '',
  invdate: '',
  invCnt: '',
  manuLotnum: '',
  memo: '',
});

const invChange3502Loading = ref(false);
const invChangeForm = ref({
  medListCodg: '',
  invChgType: '103',
  fixmedinsHilistId: '',
  fixmedinsHilistName: '',
  fixmedinsBchno: '',
  cnt: '',
  pric: '',
  invChgTime: '',
  invChgOpterName: '',
  memo: '',
});

const invChgTypeOptions = [
  { label: '101 调拨入库', value: '101' },
  { label: '102 调拨出库', value: '102' },
  { label: '103 盘盈', value: '103' },
  { label: '104 盘损', value: '104' },
  { label: '105 销毁', value: '105' },
  { label: '106 其他入库', value: '106' },
  { label: '107 其他出库', value: '107' },
  { label: '108 采购入库', value: '108' },
  { label: '109 退货出库', value: '109' },
  { label: '110 赠药入库', value: '110' },
  { label: '111 赠药退回出库', value: '111' },
];

const initLoading = ref(false);
const initLocationOptions = ref<Array<{ locationId: string; label: string }>>([]);
const initForm = ref({
  locationId: '' as string | number,
  remark: '首次进销存初始化',
  maxLines: '200',
});
const initPageNo = ref(0);
const initResult = ref<InitializeInventoryResult | null>(null);
const initCumulative = ref({
  attemptedPairs: 0,
  succeededPairs: 0,
  failedPairs: 0,
  skippedNoYbNo: 0,
  skippedAlreadyInitialized: 0,
});

const delete3507Loading = ref(false);
const delete3507Form = ref({
  fixmedinsBchno: '',
  invDataType: '4' as '1' | '2' | '3' | '4',
});

const invDataTypeOptions = [
  { label: '1 盘存', value: '1' },
  { label: '2 库存变更', value: '2' },
  { label: '3 采购', value: '3' },
  { label: '4 销售', value: '4' },
];

const dateFieldProps = {
  type: 'daterange' as const,
  valueFormat: 'YYYY-MM-DD',
  rangeSeparator: '至',
  startPlaceholder: '开始日期',
  endPlaceholder: '结束日期',
  clearable: true,
};

const infnoOptions = [
  { label: '全部', value: '' },
  { label: '3501 盘存', value: '3501' },
  { label: '3502 库存变更', value: '3502' },
  { label: '3503 采购', value: '3503' },
  { label: '3504 采购退货', value: '3504' },
  { label: '3505 销售', value: '3505' },
  { label: '3506 销售退货', value: '3506' },
  { label: '3507 删除', value: '3507' },
];

const sourceTypeOptions = [
  { label: '全部', value: '' },
  { label: '发药', value: 'DISPENSE' },
  { label: '进货', value: 'INBOUND' },
  { label: '盘存', value: 'STOCKTAKE' },
  { label: '调拨', value: 'TRANSFER' },
  { label: '损益', value: 'GAIN_LOSS' },
  { label: '初始化', value: 'INIT' },
  { label: '运维', value: 'MANUAL' },
];

const queryApiMap: Record<QueryTab, typeof queryPlatformSale3511Api> = {
  '3508': queryPlatformInventory3508Api,
  '3509': queryPlatformInvChange3509Api,
  '3510': queryPlatformPurchase3510Api,
  '3511': queryPlatformSale3511Api,
  '3512': queryPlatformInboundTrace3512Api,
  '3513': queryPlatformSaleTrace3513Api,
};

onMounted(async () => {
  loadingChargeTypes.value = true;
  try {
    const chargeTypes = await getChargeTypeListApi();
    const insuranceTypes = filterInsuranceChargeTypes(chargeTypes);
    chargeTypeOptions.value = insuranceTypes;
    contractBusNo.value = resolveDefaultInsuranceBusNo(insuranceTypes);
  } finally {
    loadingChargeTypes.value = false;
  }
});

function uploadStatusTagType(status?: string) {
  if (status === 'SUCCESS') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status?.startsWith('SKIPPED')) return 'info';
  return 'warning';
}

async function loadLocalRecords() {
  if (!contractBusNo.value) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  localLoading.value = true;
  try {
    localRecords.value = await listLocalInvUploadRecordsApi({
      infno: localInfno.value || undefined,
      sourceType: localSourceType.value || undefined,
    });
  } finally {
    localLoading.value = false;
  }
}

async function handleRetryPending() {
  if (!contractBusNo.value) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  await ensureInsuranceSignIn(contractBusNo.value);
  const result = await retryPendingInvUploadsApi({ batchSize: 50 });
  ElMessage.success(
    `重试完成：尝试 ${result.attempted ?? 0}，成功 ${result.succeeded ?? 0}，失败 ${result.failed ?? 0}`,
  );
  await loadLocalRecords();
}

function buildQueryParams() {
  const [begndate, enddate] = queryDateRange.value ?? [];
  return {
    contractBusNo: contractBusNo.value!,
    begndate,
    enddate,
    medinsListCodg: queryMedinsListCodg.value || undefined,
    fixmedinsBchno: queryFixmedinsBchno.value || undefined,
    medListCodg: queryMedListCodg.value || undefined,
    certno: queryCertno.value || undefined,
    drugTracCodg: queryDrugTracCodg.value || undefined,
    valiFlag: '1',
  };
}

function compareDiffTagType(diffType?: string) {
  if (diffType === 'MATCH') return 'success';
  if (diffType === 'LOCAL_ONLY') return 'warning';
  if (diffType === 'PLATFORM_ONLY') return 'danger';
  return 'info';
}

async function runCompareLocalVsPlatform() {
  if (!contractBusNo.value) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  const [begndate, enddate] = queryDateRange.value ?? [];
  if (!begndate || !enddate) {
    ElMessage.warning('请先选择日期区间');
    return;
  }
  compareLoading.value = true;
  try {
    await ensureInsuranceSignIn(contractBusNo.value);
    const result = await compareLocalVsPlatformApi({
      ...buildQueryParams(),
      compareQueryInfno: compareQueryInfno.value,
    });
    compareResult.value = result;
    compareRows.value = result.rows ?? [];
    if (compareRows.value.length === 0) {
      ElMessage.info('比对完成，无差异行');
    } else {
      ElMessage.success(
        `比对完成：匹配 ${result.matchCount ?? 0}；仅本地 ${result.localOnlyCount ?? 0}；仅平台 ${result.platformOnlyCount ?? 0}`,
      );
    }
  } finally {
    compareLoading.value = false;
  }
}

async function runPlatformQuery(tab: QueryTab) {
  if (!contractBusNo.value) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  platformLoading.value = true;
  platformInfno.value = tab;
  try {
    await ensureInsuranceSignIn(contractBusNo.value);
    const api = queryApiMap[tab];
    const result: PlatformInvQueryResult = await api(buildQueryParams());
    platformRows.value = result.rows ?? [];
    if (platformRows.value.length === 0) {
      ElMessage.info('平台未返回记录');
    }
  } finally {
    platformLoading.value = false;
  }
}

function onQueryTabChange(name: string | number) {
  if (typeof name === 'string' && name in queryApiMap) {
    void runPlatformQuery(name as QueryTab);
  }
}

async function submitStocktake3501() {
  if (!contractBusNo.value) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  if (!stocktakeForm.value.medListCodg.trim() || !stocktakeForm.value.fixmedinsBchno.trim()) {
    ElMessage.warning('医保编码与批次流水号必填');
    return;
  }
  if (!stocktakeForm.value.invdate.trim() || !stocktakeForm.value.invCnt.trim()) {
    ElMessage.warning('盘存日期与数量必填');
    return;
  }
  stocktake3501Loading.value = true;
  try {
    await ensureInsuranceSignIn(contractBusNo.value);
    const results = await uploadStocktake3501Api({
      contractBusNo: contractBusNo.value,
      lines: [
        {
          medListCodg: stocktakeForm.value.medListCodg.trim(),
          fixmedinsHilistId: stocktakeForm.value.fixmedinsHilistId.trim() || undefined,
          fixmedinsHilistName: stocktakeForm.value.fixmedinsHilistName.trim() || undefined,
          fixmedinsBchno: stocktakeForm.value.fixmedinsBchno.trim(),
          invdate: stocktakeForm.value.invdate.trim(),
          invCnt: Number(stocktakeForm.value.invCnt),
          manuLotnum: stocktakeForm.value.manuLotnum.trim() || undefined,
          memo: stocktakeForm.value.memo.trim() || undefined,
        },
      ],
    });
    const row = results[0];
    if (row?.uploadStatus === 'SUCCESS') {
      ElMessage.success('3501 盘存上报成功');
    } else {
      ElMessage.warning(row?.platformMsg || `3501 上报状态：${row?.uploadStatus ?? '未知'}`);
    }
    await loadLocalRecords();
  } finally {
    stocktake3501Loading.value = false;
  }
}

onMounted(async () => {
  loadingChargeTypes.value = true;
  try {
    const chargeTypes = await getChargeTypeListApi();
    const insuranceTypes = filterInsuranceChargeTypes(chargeTypes);
    chargeTypeOptions.value = insuranceTypes;
    contractBusNo.value = resolveDefaultInsuranceBusNo(insuranceTypes);
  } finally {
    loadingChargeTypes.value = false;
  }
  await loadInitLocationOptions();
});

async function loadInitLocationOptions() {
  try {
    const [warehouse, pharmacy] = await Promise.all([
      initWarehouseStocktakeWorkbenchApi(),
      initPharmacyStocktakeWorkbenchApi(),
    ]);
    const options: Array<{ locationId: string; label: string }> = [];
    for (const row of warehouse.locationRawOptions ?? []) {
      if (row.locationId === undefined || row.locationId === null) continue;
      const id = String(row.locationId).trim();
      if (!id) continue;
      options.push({
        locationId: id,
        label: `[药库] ${row.displayName ?? id}`,
      });
    }
    for (const row of pharmacy.locationRawOptions ?? []) {
      if (row.locationId === undefined || row.locationId === null) continue;
      const id = String(row.locationId).trim();
      if (!id) continue;
      options.push({
        locationId: id,
        label: `[药房] ${row.displayName ?? id}`,
      });
    }
    initLocationOptions.value = options;
    if (!initForm.value.locationId && options.length > 0) {
      initForm.value.locationId = options[0]!.locationId;
    }
  } catch {
    initLocationOptions.value = [];
  }
}

function resetInitProgress() {
  initPageNo.value = 0;
  initResult.value = null;
  initCumulative.value = {
    attemptedPairs: 0,
    succeededPairs: 0,
    failedPairs: 0,
    skippedNoYbNo: 0,
    skippedAlreadyInitialized: 0,
  };
}

function mergeInitCumulative(batch: InitializeInventoryResult) {
  initCumulative.value.attemptedPairs += batch.attemptedPairs ?? 0;
  initCumulative.value.succeededPairs += batch.succeededPairs ?? 0;
  initCumulative.value.failedPairs += batch.failedPairs ?? 0;
  initCumulative.value.skippedNoYbNo += batch.skippedNoYbNo ?? 0;
  initCumulative.value.skippedAlreadyInitialized += batch.skippedAlreadyInitialized ?? 0;
}

async function runInitializeInventoryBatch(pageNo: number) {
  if (!contractBusNo.value) {
    ElMessage.warning('请先选择医保契约');
    return null;
  }
  const locationId = initForm.value.locationId;
  if (locationId === '' || locationId === undefined || locationId === null) {
    ElMessage.warning('请选择库位');
    return null;
  }
  await ensureInsuranceSignIn(contractBusNo.value);
  return initializeInventoryApi({
    contractBusNo: contractBusNo.value,
    locationId,
    remark: initForm.value.remark.trim() || undefined,
    maxLines: initForm.value.maxLines.trim() ? Number(initForm.value.maxLines) : undefined,
    pageNo,
    skipInitialized: true,
  });
}

async function submitInitializeInventory() {
  resetInitProgress();
  initLoading.value = true;
  try {
    const result = await runInitializeInventoryBatch(0);
    if (!result) return;
    initResult.value = result;
    mergeInitCumulative(result);
    initPageNo.value = result.nextPageNo ?? 0;
    ElMessage.success(
      `本批成功 ${result.succeededPairs ?? 0} / 尝试 ${result.attemptedPairs ?? 0}${
        result.hasMore ? '；仍有后续批次' : ''
      }`,
    );
    await loadLocalRecords();
  } finally {
    initLoading.value = false;
  }
}

async function continueInitializeInventory() {
  if (!initResult.value?.hasMore) {
    ElMessage.info('没有更多待初始化库存');
    return;
  }
  const nextPage = initResult.value.nextPageNo ?? initPageNo.value;
  initLoading.value = true;
  try {
    const result = await runInitializeInventoryBatch(nextPage);
    if (!result) return;
    initResult.value = result;
    mergeInitCumulative(result);
    initPageNo.value = result.nextPageNo ?? nextPage;
    ElMessage.success(
      `续跑本批成功 ${result.succeededPairs ?? 0} / 尝试 ${result.attemptedPairs ?? 0}${
        result.hasMore ? '；仍有后续批次' : '；已全部完成'
      }`,
    );
    await loadLocalRecords();
  } finally {
    initLoading.value = false;
  }
}

async function submitInitializeInventoryAll() {
  resetInitProgress();
  initLoading.value = true;
  try {
    let pageNo = 0;
    let hasMore = true;
    while (hasMore) {
      const result = await runInitializeInventoryBatch(pageNo);
      if (!result) break;
      initResult.value = result;
      mergeInitCumulative(result);
      hasMore = Boolean(result.hasMore);
      pageNo = result.nextPageNo ?? pageNo + 1;
      if (
        hasMore &&
        (result.attemptedPairs ?? 0) === 0 &&
        (result.skippedAlreadyInitialized ?? 0) === 0
      ) {
        break;
      }
    }
    ElMessage.success(
      `全部完成：累计成功 ${initCumulative.value.succeededPairs} / 尝试 ${initCumulative.value.attemptedPairs}`,
    );
    await loadLocalRecords();
  } finally {
    initLoading.value = false;
  }
}

async function submitInvChange3502() {
  if (!contractBusNo.value) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  if (!invChangeForm.value.medListCodg.trim() || !invChangeForm.value.fixmedinsBchno.trim()) {
    ElMessage.warning('医保编码与批次流水号必填');
    return;
  }
  if (!invChangeForm.value.invChgTime.trim() || !invChangeForm.value.cnt.trim()) {
    ElMessage.warning('变更时间与数量必填');
    return;
  }
  invChange3502Loading.value = true;
  try {
    await ensureInsuranceSignIn(contractBusNo.value);
    const results = await uploadInvChange3502Api({
      contractBusNo: contractBusNo.value,
      lines: [
        {
          medListCodg: invChangeForm.value.medListCodg.trim(),
          invChgType: invChangeForm.value.invChgType,
          fixmedinsHilistId: invChangeForm.value.fixmedinsHilistId.trim() || undefined,
          fixmedinsHilistName: invChangeForm.value.fixmedinsHilistName.trim() || undefined,
          fixmedinsBchno: invChangeForm.value.fixmedinsBchno.trim(),
          cnt: Number(invChangeForm.value.cnt),
          pric: invChangeForm.value.pric.trim() ? Number(invChangeForm.value.pric) : undefined,
          invChgTime: invChangeForm.value.invChgTime.trim(),
          invChgOpterName: invChangeForm.value.invChgOpterName.trim() || undefined,
          memo: invChangeForm.value.memo.trim() || undefined,
        },
      ],
    });
    const row = results[0];
    if (row?.uploadStatus === 'SUCCESS') {
      ElMessage.success('3502 库存变更上报成功');
    } else {
      ElMessage.warning(row?.platformMsg || `3502 上报状态：${row?.uploadStatus ?? '未知'}`);
    }
    await loadLocalRecords();
  } finally {
    invChange3502Loading.value = false;
  }
}

async function submitDelete3507() {
  if (!contractBusNo.value) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  if (!delete3507Form.value.fixmedinsBchno.trim()) {
    ElMessage.warning('批次流水号必填');
    return;
  }
  delete3507Loading.value = true;
  try {
    await ensureInsuranceSignIn(contractBusNo.value);
    const result = await deleteInvData3507Api({
      contractBusNo: contractBusNo.value,
      fixmedinsBchno: delete3507Form.value.fixmedinsBchno.trim(),
      invDataType: delete3507Form.value.invDataType,
    });
    if (result.uploadStatus === 'SUCCESS') {
      ElMessage.success('3507 删除成功');
    } else {
      ElMessage.warning(result.platformMsg || `3507 状态：${result.uploadStatus ?? '未知'}`);
    }
    await loadLocalRecords();
  } finally {
    delete3507Loading.value = false;
  }
}
</script>

<template>
  <Page auto-content-height title="医保进销存运维">
    <ElCard shadow="never" class="mb-4">
      <ElForm inline>
        <ElFormItem label="医保契约" required>
          <ElSelect
            v-model="contractBusNo"
            class="!w-72"
            placeholder="请选择医保契约"
            :loading="loadingChargeTypes"
          >
            <ElOption
              v-for="item in chargeTypeOptions"
              :key="item.busNo"
              :label="item.busNo ? `${item.label}（${item.busNo}）` : item.label"
              :value="item.busNo"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElTabs v-model="activeTab" @tab-change="onQueryTabChange">
      <ElTabPane label="本地上传流水" name="local">
        <ElCard shadow="never">
          <ElForm inline class="mb-3">
            <ElFormItem label="交易号">
              <ElSelect v-model="localInfno" clearable style="width: 140px">
                <ElOption
                  v-for="opt in infnoOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="来源类型">
              <ElSelect v-model="localSourceType" clearable style="width: 120px">
                <ElOption
                  v-for="opt in sourceTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="localLoading" @click="loadLocalRecords">
                查询本地
              </ElButton>
              <ElButton @click="handleRetryPending">重试失败/待传</ElButton>
            </ElFormItem>
          </ElForm>
          <ElTable v-loading="localLoading" :data="localRecords" border stripe max-height="520">
            <ElTableColumn prop="infno" label="交易号" width="80" />
            <ElTableColumn prop="fixmedinsBchno" label="批次流水号" min-width="120" />
            <ElTableColumn prop="medListCodg" label="医保编码" min-width="120" />
            <ElTableColumn prop="sourceType" label="来源" width="90" />
            <ElTableColumn prop="sourceLineId" label="来源行ID" min-width="120" />
            <ElTableColumn label="状态" width="110">
              <template #default="{ row }">
                <ElTag :type="uploadStatusTagType(row.uploadStatus)" size="small">
                  {{ row.uploadStatus }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="platformMsg"
              label="平台消息"
              min-width="160"
              show-overflow-tooltip
            />
            <ElTableColumn prop="createdAt" label="创建时间" width="170" />
          </ElTable>
        </ElCard>
      </ElTabPane>

      <ElTabPane label="差异比对" name="compare">
        <ElCard shadow="never">
          <ElAlert
            type="info"
            :closable="false"
            class="mb-3"
            title="3508–3511 按 fixmedins_bchno + med_list_codg 比对；3512/3513 按 drug_trac_codg 比对本地 yb_inv_upload_trace 与平台追溯查询结果。"
          />
          <ElForm inline class="mb-3">
            <ElFormItem label="比对范围" required>
              <ElSelect v-model="compareQueryInfno" style="width: 200px">
                <ElOption
                  v-for="opt in compareScopeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="日期区间" required>
              <ElDatePicker v-model="queryDateRange" v-bind="dateFieldProps" />
            </ElFormItem>
            <ElFormItem label="机构目录编码">
              <ElInput v-model="queryMedinsListCodg" clearable style="width: 160px" />
            </ElFormItem>
            <ElFormItem label="批次流水号">
              <ElInput v-model="queryFixmedinsBchno" clearable style="width: 140px" />
            </ElFormItem>
            <ElFormItem label="医保目录编码">
              <ElInput v-model="queryMedListCodg" clearable style="width: 140px" />
            </ElFormItem>
            <ElFormItem v-if="isTraceCompareScope" label="追溯码">
              <ElInput
                v-model="queryDrugTracCodg"
                clearable
                placeholder="可选过滤"
                style="width: 180px"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="compareLoading" @click="runCompareLocalVsPlatform">
                执行比对
              </ElButton>
            </ElFormItem>
          </ElForm>
          <ElAlert
            v-if="compareResult"
            type="success"
            :closable="false"
            class="mb-3"
            :title="`平台 ${compareResult.platformRowCount ?? 0} 行 / 本地 ${compareResult.localRowCount ?? 0} 行（本地 infno ${compareResult.localUploadInfno ?? '—'}）`"
          />
          <ElTable v-loading="compareLoading" :data="compareRows" border stripe max-height="520">
            <ElTableColumn label="差异" width="120">
              <template #default="{ row }">
                <ElTag :type="compareDiffTagType(row.diffType)" size="small">
                  {{ row.diffType }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="fixmedinsBchno" label="批次流水号" min-width="140" />
            <ElTableColumn prop="medListCodg" label="医保编码" min-width="120" />
            <ElTableColumn
              prop="drugTracCodg"
              label="追溯码"
              min-width="160"
              show-overflow-tooltip
            />
            <ElTableColumn prop="localUploadStatus" label="本地上传状态" width="120" />
            <ElTableColumn prop="localCreatedAt" label="本地创建时间" width="170" />
            <ElTableColumn prop="compareKey" label="比对键" min-width="180" show-overflow-tooltip />
          </ElTable>
        </ElCard>
      </ElTabPane>

      <ElTabPane label="3501 盘存上报" name="3501">
        <ElCard shadow="never">
          <ElAlert
            type="warning"
            :closable="false"
            class="mb-3"
            title="运维手工盘存上报；首次初始化须配合 3502，本页仅提交单行 3501。"
          />
          <ElForm inline class="mb-3" label-width="110px">
            <ElFormItem label="医保目录编码" required>
              <ElInput v-model="stocktakeForm.medListCodg" clearable style="width: 160px" />
            </ElFormItem>
            <ElFormItem label="机构目录编码">
              <ElInput v-model="stocktakeForm.fixmedinsHilistId" clearable style="width: 140px" />
            </ElFormItem>
            <ElFormItem label="机构目录名称">
              <ElInput v-model="stocktakeForm.fixmedinsHilistName" clearable style="width: 160px" />
            </ElFormItem>
            <ElFormItem label="批次流水号" required>
              <ElInput v-model="stocktakeForm.fixmedinsBchno" clearable style="width: 140px" />
            </ElFormItem>
            <ElFormItem label="盘存日期" required>
              <ElInput
                v-model="stocktakeForm.invdate"
                placeholder="yyyy-MM-dd"
                style="width: 140px"
              />
            </ElFormItem>
            <ElFormItem label="库存数量" required>
              <ElInput v-model="stocktakeForm.invCnt" style="width: 100px" />
            </ElFormItem>
            <ElFormItem label="生产批号">
              <ElInput v-model="stocktakeForm.manuLotnum" clearable style="width: 120px" />
            </ElFormItem>
            <ElFormItem label="备注">
              <ElInput v-model="stocktakeForm.memo" clearable style="width: 160px" />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="stocktake3501Loading" @click="submitStocktake3501">
                提交 3501
              </ElButton>
            </ElFormItem>
          </ElForm>
        </ElCard>
      </ElTabPane>

      <ElTabPane label="3502 库存变更" name="3502">
        <ElCard shadow="never">
          <ElAlert
            type="warning"
            :closable="false"
            class="mb-3"
            title="运维手工库存变更；盘点同意业务流由药库盘点单触发 3501+3502。"
          />
          <ElForm inline class="mb-3" label-width="110px">
            <ElFormItem label="医保目录编码" required>
              <ElInput v-model="invChangeForm.medListCodg" clearable style="width: 160px" />
            </ElFormItem>
            <ElFormItem label="变更类型" required>
              <ElSelect v-model="invChangeForm.invChgType" style="width: 140px">
                <ElOption
                  v-for="opt in invChgTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="机构目录编码">
              <ElInput v-model="invChangeForm.fixmedinsHilistId" clearable style="width: 140px" />
            </ElFormItem>
            <ElFormItem label="机构目录名称">
              <ElInput v-model="invChangeForm.fixmedinsHilistName" clearable style="width: 160px" />
            </ElFormItem>
            <ElFormItem label="批次流水号" required>
              <ElInput v-model="invChangeForm.fixmedinsBchno" clearable style="width: 140px" />
            </ElFormItem>
            <ElFormItem label="变更数量" required>
              <ElInput v-model="invChangeForm.cnt" style="width: 100px" />
            </ElFormItem>
            <ElFormItem label="单价">
              <ElInput v-model="invChangeForm.pric" style="width: 100px" />
            </ElFormItem>
            <ElFormItem label="变更时间" required>
              <ElInput
                v-model="invChangeForm.invChgTime"
                placeholder="yyyy-MM-dd HH:mm:ss"
                style="width: 180px"
              />
            </ElFormItem>
            <ElFormItem label="经办人">
              <ElInput v-model="invChangeForm.invChgOpterName" clearable style="width: 120px" />
            </ElFormItem>
            <ElFormItem label="备注">
              <ElInput v-model="invChangeForm.memo" clearable style="width: 160px" />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="invChange3502Loading" @click="submitInvChange3502">
                提交 3502
              </ElButton>
            </ElFormItem>
          </ElForm>
        </ElCard>
      </ElTabPane>

      <ElTabPane label="批初始化 3501+3502" name="init">
        <ElCard shadow="never">
          <ElAlert
            type="warning"
            :closable="false"
            class="mb-3"
            title="选择药库/药房库位；每行先 3501 再 3502(106)。已初始化行自动跳过，支持分页续跑。"
          />
          <ElForm inline class="mb-3" label-width="110px">
            <ElFormItem label="库位" required>
              <ElSelect
                v-model="initForm.locationId"
                class="!w-72"
                filterable
                clearable
                placeholder="请选择库位"
                @change="resetInitProgress"
              >
                <ElOption
                  v-for="opt in initLocationOptions"
                  :key="opt.locationId"
                  :label="opt.label"
                  :value="opt.locationId"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="单次上限">
              <ElInput v-model="initForm.maxLines" style="width: 100px" />
            </ElFormItem>
            <ElFormItem label="备注">
              <ElInput v-model="initForm.remark" clearable style="width: 220px" />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="initLoading" @click="submitInitializeInventory">
                执行本批
              </ElButton>
              <ElButton
                :loading="initLoading"
                :disabled="!initResult?.hasMore"
                @click="continueInitializeInventory"
              >
                续跑下一批
              </ElButton>
              <ElButton :loading="initLoading" @click="submitInitializeInventoryAll">
                执行至完成
              </ElButton>
            </ElFormItem>
          </ElForm>
          <ElAlert
            v-if="initResult"
            type="info"
            :closable="false"
            class="mb-2"
            :title="`本批：尝试 ${initResult.attemptedPairs ?? 0}；成功 ${initResult.succeededPairs ?? 0}；失败 ${initResult.failedPairs ?? 0}；未对码 ${initResult.skippedNoYbNo ?? 0}；已初始化跳过 ${initResult.skippedAlreadyInitialized ?? 0}；库存共 ${initResult.totalElements ?? '—'} 行${initResult.hasMore ? '（仍有后续）' : ''}`"
          />
          <ElAlert
            v-if="initCumulative.attemptedPairs > 0"
            type="success"
            :closable="false"
            :title="`累计：尝试 ${initCumulative.attemptedPairs}；成功 ${initCumulative.succeededPairs}；失败 ${initCumulative.failedPairs}；未对码 ${initCumulative.skippedNoYbNo}；已跳过 ${initCumulative.skippedAlreadyInitialized}`"
          />
        </ElCard>
      </ElTabPane>

      <ElTabPane label="3507 平台删除" name="3507">
        <ElCard shadow="never">
          <ElAlert
            type="warning"
            :closable="false"
            class="mb-3"
            title="按批次流水号删除平台进销存数据；成功后本地 SUCCESS 流水标记为 DELETED。"
          />
          <ElForm inline class="mb-3" label-width="110px">
            <ElFormItem label="批次流水号" required>
              <ElInput v-model="delete3507Form.fixmedinsBchno" clearable style="width: 180px" />
            </ElFormItem>
            <ElFormItem label="数据类型" required>
              <ElSelect v-model="delete3507Form.invDataType" style="width: 140px">
                <ElOption
                  v-for="opt in invDataTypeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem>
              <ElButton type="danger" :loading="delete3507Loading" @click="submitDelete3507">
                提交 3507 删除
              </ElButton>
            </ElFormItem>
          </ElForm>
        </ElCard>
      </ElTabPane>

      <ElTabPane
        v-for="tab in ['3508', '3509', '3510', '3511', '3512', '3513'] as QueryTab[]"
        :key="tab"
        :label="`${tab} 平台查询`"
        :name="tab"
      >
        <ElCard shadow="never">
          <ElForm inline class="mb-3">
            <ElFormItem label="日期区间">
              <ElDatePicker v-model="queryDateRange" v-bind="dateFieldProps" />
            </ElFormItem>
            <ElFormItem label="机构目录编码">
              <ElInput v-model="queryMedinsListCodg" clearable style="width: 160px" />
            </ElFormItem>
            <ElFormItem label="批次流水号">
              <ElInput v-model="queryFixmedinsBchno" clearable style="width: 140px" />
            </ElFormItem>
            <ElFormItem label="医保目录编码">
              <ElInput v-model="queryMedListCodg" clearable style="width: 140px" />
            </ElFormItem>
            <ElFormItem v-if="tab === '3513'" label="证件号">
              <ElInput v-model="queryCertno" clearable style="width: 160px" />
            </ElFormItem>
            <ElFormItem v-if="tab === '3512' || tab === '3513'" label="追溯码">
              <ElInput v-model="queryDrugTracCodg" clearable style="width: 180px" />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="platformLoading" @click="runPlatformQuery(tab)">
                查询平台
              </ElButton>
            </ElFormItem>
          </ElForm>
          <ElAlert
            v-if="platformInfno === tab"
            type="info"
            :closable="false"
            class="mb-3"
            title="medins_list_codg 与 fixmedins_bchno 至少传一；3512/3513 可按追溯码或目录编码组合查询。"
          />
          <ElTable v-loading="platformLoading" :data="platformRows" border stripe max-height="520">
            <ElTableColumn
              v-for="(_, key) in platformRows[0] ?? { _hint: '无数据' }"
              :key="String(key)"
              :prop="String(key)"
              :label="String(key)"
              min-width="120"
              show-overflow-tooltip
            />
          </ElTable>
        </ElCard>
      </ElTabPane>
    </ElTabs>
  </Page>
</template>
