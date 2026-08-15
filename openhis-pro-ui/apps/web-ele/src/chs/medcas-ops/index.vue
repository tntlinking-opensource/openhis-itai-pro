<script setup lang="ts">
/**
 * 医保 · 住院业务上报（对齐进销存运维台）。
 * 日常一键上报在「病案首页」；本页负责失败/待传补传、流水查询与 4103 平台查询入口。
 */
import type {
  CaseRecordUploadSummaryDto,
  SettleListQuery4103Dto,
  UploadLogDto,
} from '#/chs/api/medcas-ops';

import { computed, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import {
  ElAlert,
  ElButton,
  ElCard,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
} from 'element-plus';
import dayjs from 'dayjs';

import { getChargeTypeListApi } from '#/api/core/common';
import {
  previewAssembled4401Api,
  querySettleList4103Api,
  queryUploadLogsApi,
  queryUploadPageApi,
  reportOneClickApi,
} from '#/chs/api/medcas-ops';
import {
  filterInsuranceChargeTypes,
  INVENTORY_OPS_CONTRACT_ALL,
  isInventoryOpsContractAll,
  resolveDefaultInsuranceBusNo,
} from '#/chs/constants/insurance-contract';

defineOptions({ name: 'ChsInpatientMedcasOps' });

/** 主 Tab：本地上报 | 平台查询 4103 */
type MainTab = 'local' | 'platformQuery4103';

/** 交易号：ALL 或具体 infno（筛选用；后端字段未齐前仅 UI 保留） */
type InfnoTab = 'ALL' | '4401' | '4101A' | '4102';

/** 契约下拉（含「全部」） */
interface ContractOption {
  busNo: string;
  label: string;
}

const mainTab = ref<MainTab>('local');
const contractBusNo = ref<string>(INVENTORY_OPS_CONTRACT_ALL);
const chargeTypeOptions = ref<ContractOption[]>([]);
const defaultInsuranceBusNo = ref<string>();
const loadingChargeTypes = ref(false);

const activeInfno = ref<InfnoTab>('ALL');
/** 默认 failed：进页看待补传 */
const uploadStatusCode = ref('failed');
const keyword = ref('');
const settleFlag = ref('');
/** 出院时间范围（默认近 30 天） */
const dscgRange = ref<[string, string] | undefined>();

const tableData = ref<CaseRecordUploadSummaryDto[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const loading = ref(false);

const uploadDialogVisible = ref(false);
const uploadSubmitting = ref(false);
const uploadRow = ref<CaseRecordUploadSummaryDto | null>(null);
const uploadContractBusNo = ref('');

const viewDialogVisible = ref(false);
const viewRow = ref<CaseRecordUploadSummaryDto | null>(null);
const viewPayloadJson = ref('');

const logsDialogVisible = ref(false);
const uploadLogs = ref<UploadLogDto[]>([]);
const logsLoading = ref(false);

const previewDialogVisible = ref(false);
const previewJson = ref('');
const previewLoading = ref(false);

/** 4103 查询条件（平台键 psn_no+setl_id，或院内病案号/主键解析） */
const query4103 = reactive({
  medcasno: '',
  psnNo: '',
  setlId: '',
  caseRecordId: '',
});
const query4103Loading = ref(false);
const query4103Rows = ref<SettleListQuery4103Dto[]>([]);
const detail4103Visible = ref(false);
const detail4103Json = ref('');

const infnoTabs: Array<{ label: string; value: InfnoTab }> = [
  { label: '全部', value: 'ALL' },
  { label: '4401 病案首页', value: '4401' },
  { label: '4101A 结算清单', value: '4101A' },
  { label: '4102 提交/撤回', value: '4102' },
];

const uploadStatusOptions = [
  { label: '全部', value: '' },
  { label: '未上传', value: 'none' },
  { label: '待上传', value: 'pending' },
  { label: '上传成功', value: 'success' },
  { label: '上传失败', value: 'failed' },
  { label: '无需上传', value: 'skipped' },
];

const settleFlagOptions = [
  { label: '全部', value: '' },
  { label: '已结算', value: '1' },
  { label: '未结算', value: '0' },
];

const UPLOAD_STATUS_LABEL: Record<string, string> = {
  none: '未上传',
  pending: '待上传',
  success: '上传成功',
  failed: '上传失败',
  skipped: '无需上传',
};

/**
 * 出院时间默认近 30 天。
 */
function defaultDscgRange(): [string, string] {
  return [
    dayjs().subtract(29, 'day').startOf('day').format('YYYY-MM-DD HH:mm:ss'),
    dayjs().endOf('day').format('YYYY-MM-DD HH:mm:ss'),
  ];
}

/**
 * 上报用契约：选「全部」时回落默认医保契约。
 */
const gatewayContractBusNo = computed(() => {
  if (isInventoryOpsContractAll(contractBusNo.value)) {
    return defaultInsuranceBusNo.value;
  }
  return contractBusNo.value;
});

/**
 * 状态 Tag 颜色（按 code）。
 */
function uploadStatusTagType(code?: string) {
  if (code === 'success') return 'success';
  if (code === 'failed') return 'danger';
  if (code === 'pending') return 'warning';
  return 'info';
}

/**
 * 状态展示文案。
 */
function formatUploadStatus(code?: string) {
  if (!code) return '—';
  return UPLOAD_STATUS_LABEL[code] ?? code;
}

/**
 * 仅 failed/pending 允许补传。
 */
function canRetryUpload(code?: string) {
  return code === 'failed' || code === 'pending';
}

/**
 * 时间展示。
 */
function formatTime(value?: string) {
  if (!value) return '—';
  const formatted = formatDateTime(value);
  if (formatted && formatted !== String(value)) return formatted;
  return String(value).replace('T', ' ').slice(0, 19);
}

/**
 * 契约下拉文案。
 */
function formatContractOptionLabel(item: ContractOption) {
  if (isInventoryOpsContractAll(item.busNo)) return item.label;
  return item.busNo ? `${item.label}（${item.busNo}）` : item.label;
}

/**
 * 加载本地上报分页。
 */
async function loadLocalRecords() {
  loading.value = true;
  try {
    const page = await queryUploadPageApi({
      pageNo: pageNo.value,
      pageSize: pageSize.value,
      statusCode: uploadStatusCode.value || undefined,
      keyword: keyword.value.trim() || undefined,
    });
    tableData.value = page.rows ?? [];
    total.value = page.total ?? 0;
  } finally {
    loading.value = false;
  }
}

/**
 * 查询：回到第一页再拉列表。
 */
async function handleQuery() {
  pageNo.value = 1;
  await loadLocalRecords();
}

/**
 * 切换交易号后立即查询（交易号筛选项后端未齐时仍重置分页刷新）。
 */
async function onInfnoTabChange() {
  await handleQuery();
}

/**
 * 切换契约后刷新。
 */
async function onContractBusNoChange() {
  tableData.value = [];
  await handleQuery();
}

/**
 * 分页变更。
 */
async function onPageChange(no: number) {
  pageNo.value = no;
  await loadLocalRecords();
}

/**
 * 每页条数变更。
 */
async function onPageSizeChange(size: number) {
  pageSize.value = size;
  pageNo.value = 1;
  await loadLocalRecords();
}

/**
 * 重置筛选为默认（近 30 天 + 失败）。
 */
async function resetFilters() {
  activeInfno.value = 'ALL';
  uploadStatusCode.value = 'failed';
  keyword.value = '';
  settleFlag.value = '';
  dscgRange.value = defaultDscgRange();
  await handleQuery();
}

/**
 * 批量补传：对当前页失败/待传逐条一键上报（对齐进销存批量重放）。
 */
async function handleBatchRetry() {
  const targets = tableData.value.filter((r) => canRetryUpload(r.chsUploadStatusCode));
  if (targets.length === 0) {
    ElMessage.warning('当前列表没有失败/待传记录');
    return;
  }
  const busNo = gatewayContractBusNo.value;
  if (!busNo) {
    ElMessage.warning('请先选择医保契约');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `将对当前页 ${targets.length} 条失败/待传执行一键补传（4401→4101A→4102），是否继续？`,
      '批量补传',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  loading.value = true;
  let ok = 0;
  let fail = 0;
  try {
    // 逐条补传，避免并发打爆网关
    for (const row of targets) {
      if (!row.caseRecordId) {
        fail += 1;
        continue;
      }
      try {
        const result = await reportOneClickApi({
          caseRecordId: row.caseRecordId,
          contractBusNo: busNo,
        });
        if (result.allSuccess) {
          ok += 1;
        } else {
          fail += 1;
        }
      } catch {
        fail += 1;
      }
    }
    ElMessage.success(`批量补传完成：成功 ${ok}，失败 ${fail}`);
    await loadLocalRecords();
  } finally {
    loading.value = false;
  }
}

/**
 * 打开补传对话框（一键重放）。
 */
function openUpload(row: CaseRecordUploadSummaryDto) {
  if (!canRetryUpload(row.chsUploadStatusCode)) {
    ElMessage.warning('仅失败/待传记录可补传');
    return;
  }
  uploadRow.value = row;
  uploadContractBusNo.value = gatewayContractBusNo.value || '';
  uploadDialogVisible.value = true;
}

/**
 * 提交单条一键补传。
 */
async function submitUpload() {
  if (!uploadRow.value?.caseRecordId) {
    ElMessage.warning('未选择补传记录');
    return;
  }
  if (!uploadContractBusNo.value.trim()) {
    ElMessage.warning('请选择或填写医保契约');
    return;
  }
  uploadSubmitting.value = true;
  try {
    const result = await reportOneClickApi({
      caseRecordId: uploadRow.value.caseRecordId,
      contractBusNo: uploadContractBusNo.value.trim(),
    });
    if (result.allSuccess) {
      ElMessage.success('一键补传成功');
    } else {
      ElMessage.warning('一键补传未全部成功，请查看上传日志');
    }
    uploadDialogVisible.value = false;
    await loadLocalRecords();
  } finally {
    uploadSubmitting.value = false;
  }
}

/**
 * 从本地行带入 4103 条件并切到平台查询 Tab，随后自动查询。
 */
function openPlatformQuery4103(row: CaseRecordUploadSummaryDto) {
  query4103.caseRecordId = row.caseRecordId || '';
  query4103.medcasno = row.medcasno || '';
  query4103.psnNo = '';
  query4103.setlId = '';
  query4103Rows.value = [];
  mainTab.value = 'platformQuery4103';
  void runQuery4103();
}

/**
 * 查看行摘要。
 */
function openViewDialog(row: CaseRecordUploadSummaryDto) {
  viewRow.value = row;
  viewPayloadJson.value = JSON.stringify(row, null, 2);
  viewDialogVisible.value = true;
}

/**
 * 打开上传日志。
 */
async function showLogs(row: CaseRecordUploadSummaryDto) {
  if (!row.caseRecordId) return;
  logsLoading.value = true;
  logsDialogVisible.value = true;
  try {
    uploadLogs.value = await queryUploadLogsApi({ caseRecordId: row.caseRecordId });
  } finally {
    logsLoading.value = false;
  }
}

/**
 * 4401 组装预检。
 */
async function handlePreview(row: CaseRecordUploadSummaryDto) {
  const busNo = gatewayContractBusNo.value;
  if (!busNo) {
    ElMessage.warning('请先选择医保契约后再预检');
    return;
  }
  if (!row.caseRecordId) {
    ElMessage.warning('缺少病案主键');
    return;
  }
  previewLoading.value = true;
  previewDialogVisible.value = true;
  try {
    const data = await previewAssembled4401Api({
      caseRecordId: row.caseRecordId,
      contractBusNo: busNo,
    });
    previewJson.value = JSON.stringify(data, null, 2);
  } finally {
    previewLoading.value = false;
  }
}

/**
 * 4103 平台查询。
 */
async function runQuery4103() {
  const busNo = gatewayContractBusNo.value;
  const psnNo = query4103.psnNo.trim();
  const setlId = query4103.setlId.trim();
  const medcasno = query4103.medcasno.trim();
  const caseRecordId = query4103.caseRecordId.trim();
  const hasPlatformKeys = Boolean(psnNo && setlId);
  const hasCaseKeys = Boolean(caseRecordId || medcasno);
  if (!hasPlatformKeys && !hasCaseKeys) {
    ElMessage.warning('请填写 psn_no+setl_id，或病案号/病案主键');
    return;
  }
  if (!hasCaseKeys && !busNo) {
    ElMessage.warning('直接按平台键查询时请先选择医保契约');
    return;
  }
  query4103Loading.value = true;
  try {
    const row = await querySettleList4103Api({
      contractBusNo: busNo,
      caseRecordId: caseRecordId || undefined,
      medcasno: medcasno || undefined,
      psnNo: psnNo || undefined,
      setlId: setlId || undefined,
    });
    query4103Rows.value = [row];
    if (row.success) {
      ElMessage.success('4103 查询成功');
    } else {
      ElMessage.error(row.errMsg || row.platformMsg || '4103 查询失败');
    }
  } finally {
    query4103Loading.value = false;
  }
}

/**
 * 查看 4103 setlinfo 详情。
 */
function openDetail4103(row: SettleListQuery4103Dto) {
  detail4103Json.value = JSON.stringify(row.setlinfo ?? row, null, 2);
  detail4103Visible.value = true;
}

/**
 * 重置 4103 条件。
 */
function resetQuery4103() {
  query4103.medcasno = '';
  query4103.psnNo = '';
  query4103.setlId = '';
  query4103.caseRecordId = '';
  query4103Rows.value = [];
}

onMounted(async () => {
  dscgRange.value = defaultDscgRange();
  loadingChargeTypes.value = true;
  try {
    const chargeTypes = await getChargeTypeListApi();
    const insuranceTypes = filterInsuranceChargeTypes(chargeTypes);
    defaultInsuranceBusNo.value = resolveDefaultInsuranceBusNo(insuranceTypes);
    chargeTypeOptions.value = [
      { busNo: INVENTORY_OPS_CONTRACT_ALL, label: '全部' },
      ...insuranceTypes.map((item) => ({
        busNo: (item.busNo ?? '').trim(),
        label: item.label || (item.busNo ?? ''),
      })),
    ];
    contractBusNo.value = INVENTORY_OPS_CONTRACT_ALL;
    await loadLocalRecords();
  } finally {
    loadingChargeTypes.value = false;
  }
});
</script>

<template>
  <Page
    auto-content-height
    title="住院业务上报"
    description="本地查询/补传 4401·4101A·4102；平台查询 4103。日常一键上报请用「病案首页」。"
  >
    <ElCard shadow="never" class="mb-4">
      <ElForm inline>
        <ElFormItem label="医保契约">
          <ElSelect
            v-model="contractBusNo"
            class="!w-72"
            placeholder="请选择医保契约"
            :loading="loadingChargeTypes"
            @change="onContractBusNoChange"
          >
            <ElOption
              v-for="item in chargeTypeOptions"
              :key="item.busNo"
              :label="formatContractOptionLabel(item)"
              :value="item.busNo"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
    </ElCard>

    <ElTabs v-model="mainTab">
      <ElTabPane label="本地上报流水" name="local">
        <ElCard shadow="never">
          <ElAlert
            type="warning"
            :closable="false"
            class="mb-3"
            title="按条件查本地上报流水；失败/待传点「补传」重放。日常一键请用病案首页；本页专责运维补传与排查。"
          />

          <div class="mb-3">
            <span class="mr-3 text-sm" style="color: var(--el-text-color-regular)">交易号</span>
            <ElRadioGroup v-model="activeInfno" @change="onInfnoTabChange">
              <ElRadioButton v-for="tab in infnoTabs" :key="tab.value" :label="tab.value">
                {{ tab.label }}
              </ElRadioButton>
            </ElRadioGroup>
          </div>

          <ElForm inline class="mb-3">
            <ElFormItem label="出院时间">
              <ElDatePicker
                v-model="dscgRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始"
                end-placeholder="结束"
                value-format="YYYY-MM-DD HH:mm:ss"
                :clearable="true"
                style="width: 360px"
                @change="handleQuery"
              />
            </ElFormItem>
            <ElFormItem label="上报状态">
              <ElSelect
                v-model="uploadStatusCode"
                clearable
                style="width: 140px"
                @change="handleQuery"
              >
                <ElOption
                  v-for="opt in uploadStatusOptions"
                  :key="opt.value || 'all-status'"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="结算">
              <ElSelect v-model="settleFlag" clearable style="width: 120px" @change="handleQuery">
                <ElOption
                  v-for="opt in settleFlagOptions"
                  :key="opt.value || 'all-settle'"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="关键字">
              <ElInput
                v-model="keyword"
                clearable
                placeholder="病案号/姓名"
                style="width: 200px"
                @keyup.enter="handleQuery"
                @clear="handleQuery"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="loading" @click="handleQuery">查询</ElButton>
              <ElButton @click="resetFilters">重置</ElButton>
              <ElButton @click="handleBatchRetry">批量补传失败/待传</ElButton>
            </ElFormItem>
          </ElForm>

          <ElTable v-loading="loading" :data="tableData" border stripe max-height="560">
            <ElTableColumn
              prop="medcasno"
              label="病案号"
              width="120"
              align="center"
              show-overflow-tooltip
            />
            <ElTableColumn prop="psnName" label="患者" width="100" align="center" />
            <ElTableColumn label="出院时间" width="170" align="center">
              <template #default="{ row }">
                {{ formatTime(row.dscgTime) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="归档时间" width="170" align="center">
              <template #default="{ row }">
                {{ formatTime(row.archivedAt) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" width="110" align="center">
              <template #default="{ row }">
                <ElTag :type="uploadStatusTagType(row.chsUploadStatusCode)" size="small">
                  {{ formatUploadStatus(row.chsUploadStatusCode) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="chsUploadError"
              label="平台消息"
              min-width="220"
              show-overflow-tooltip
            />
            <ElTableColumn label="操作" width="320" fixed="right" align="center">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openViewDialog(row)">查看</ElButton>
                <ElButton
                  v-if="canRetryUpload(row.chsUploadStatusCode)"
                  link
                  type="primary"
                  @click="openUpload(row)"
                >
                  补传
                </ElButton>
                <ElButton link @click="showLogs(row)">日志</ElButton>
                <ElButton link @click="handlePreview(row)">预检</ElButton>
                <ElButton link type="primary" @click="openPlatformQuery4103(row)">
                  平台查询
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>

          <div class="mt-3 flex justify-end">
            <ElPagination
              v-model:current-page="pageNo"
              v-model:page-size="pageSize"
              background
              layout="total, sizes, prev, pager, next"
              :total="total"
              :page-sizes="[10, 20, 50, 100]"
              @current-change="onPageChange"
              @size-change="onPageSizeChange"
            />
          </div>
        </ElCard>
      </ElTabPane>

      <ElTabPane label="平台查询 4103" name="platformQuery4103">
        <ElCard shadow="never">
          <ElAlert
            type="info"
            :closable="false"
            class="mb-3"
            title="4103 只查平台结算清单，不改院内数据。可填 psn_no+setl_id；或病案号/从本地行带入后解析。"
          />
          <ElForm inline class="mb-3">
            <ElFormItem label="人员编号">
              <ElInput
                v-model="query4103.psnNo"
                clearable
                placeholder="psn_no"
                style="width: 180px"
                @keyup.enter="runQuery4103"
              />
            </ElFormItem>
            <ElFormItem label="结算ID">
              <ElInput
                v-model="query4103.setlId"
                clearable
                placeholder="setl_id"
                style="width: 180px"
                @keyup.enter="runQuery4103"
              />
            </ElFormItem>
            <ElFormItem label="病案号">
              <ElInput
                v-model="query4103.medcasno"
                clearable
                placeholder="院内病案号"
                style="width: 160px"
                @keyup.enter="runQuery4103"
                @clear="runQuery4103"
              />
            </ElFormItem>
            <ElFormItem>
              <ElButton type="primary" :loading="query4103Loading" @click="runQuery4103">
                查询
              </ElButton>
              <ElButton @click="resetQuery4103">重置</ElButton>
            </ElFormItem>
          </ElForm>

          <ElTable
            v-loading="query4103Loading"
            :data="query4103Rows"
            border
            stripe
            max-height="480"
          >
            <ElTableColumn
              prop="setlId"
              label="结算ID"
              min-width="140"
              align="center"
              show-overflow-tooltip
            />
            <ElTableColumn
              prop="setlListId"
              label="清单流水号"
              min-width="140"
              align="center"
              show-overflow-tooltip
            />
            <ElTableColumn prop="medcasno" label="病案号" width="120" align="center" />
            <ElTableColumn prop="psnName" label="患者" width="100" align="center" />
            <ElTableColumn label="结算时间" width="170" align="center">
              <template #default="{ row }">
                {{ formatTime(row.setlTime) }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="总费用" width="110" align="right" header-align="right">
              <template #default="{ row }">
                {{ row.medfeeSumamt == null ? '—' : Number(row.medfeeSumamt).toFixed(2) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="setlStatusText" label="平台状态" width="120" align="center" />
            <ElTableColumn
              prop="platformMsg"
              label="平台消息"
              min-width="160"
              show-overflow-tooltip
            />
            <ElTableColumn label="操作" width="100" fixed="right" align="center">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openDetail4103(row)">详情</ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>
      </ElTabPane>
    </ElTabs>

    <ElDialog v-model="uploadDialogVisible" title="补传（一键重放）" width="520px">
      <ElAlert
        type="info"
        :closable="false"
        class="mb-3"
        title="补传按 4401→4101A→4102 一键重放，不改院内首页字段。日常一键请回病案首页。"
      />
      <ElForm label-width="110px">
        <ElFormItem label="病案号">
          <span>{{ uploadRow?.medcasno }}</span>
        </ElFormItem>
        <ElFormItem label="患者">
          <span>{{ uploadRow?.psnName }}</span>
        </ElFormItem>
        <ElFormItem label="失败原因">
          <span class="whitespace-pre-wrap break-all" style="color: var(--el-color-danger)">
            {{ uploadRow?.chsUploadError || '—' }}
          </span>
        </ElFormItem>
        <ElFormItem label="医保契约" required>
          <ElSelect
            v-model="uploadContractBusNo"
            class="!w-full"
            filterable
            allow-create
            default-first-option
            placeholder="选择或填写 busNo"
          >
            <ElOption
              v-for="item in chargeTypeOptions.filter((o) => !isInventoryOpsContractAll(o.busNo))"
              :key="item.busNo"
              :label="formatContractOptionLabel(item)"
              :value="item.busNo"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="uploadDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="uploadSubmitting" @click="submitUpload">
          提交补传
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="viewDialogVisible" title="查看上报摘要" width="720px">
      <ElForm label-width="110px">
        <ElFormItem label="病案号">
          <span>{{ viewRow?.medcasno }}</span>
        </ElFormItem>
        <ElFormItem label="状态">
          <ElTag :type="uploadStatusTagType(viewRow?.chsUploadStatusCode)" size="small">
            {{ formatUploadStatus(viewRow?.chsUploadStatusCode) }}
          </ElTag>
        </ElFormItem>
        <ElFormItem label="平台消息">
          <span class="whitespace-pre-wrap break-all">{{ viewRow?.chsUploadError || '—' }}</span>
        </ElFormItem>
        <ElFormItem label="摘要 JSON">
          <ElInput v-model="viewPayloadJson" type="textarea" :rows="14" readonly />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton type="primary" @click="viewDialogVisible = false">关闭</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="logsDialogVisible" title="上传日志" width="820px">
      <ElTable v-loading="logsLoading" :data="uploadLogs" size="small" border>
        <ElTableColumn label="时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="infno" label="交易号" width="90" align="center" />
        <ElTableColumn label="状态" width="100" align="center">
          <template #default="{ row }">
            {{ formatUploadStatus(row.statusCode) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="infcode" prop="infcode" width="90" align="center" />
        <ElTableColumn prop="contractBusNo" label="契约" width="100" align="center" />
        <ElTableColumn label="错误" prop="errMsg" min-width="200" show-overflow-tooltip />
      </ElTable>
    </ElDialog>

    <ElDialog v-model="previewDialogVisible" title="4401 预检 JSON" width="800px">
      <ElInput
        v-loading="previewLoading"
        :model-value="previewJson"
        type="textarea"
        :rows="22"
        readonly
      />
    </ElDialog>

    <ElDialog v-model="detail4103Visible" title="4103 setlinfo 详情" width="800px">
      <ElInput v-model="detail4103Json" type="textarea" :rows="22" readonly />
      <template #footer>
        <ElButton type="primary" @click="detail4103Visible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </Page>
</template>
