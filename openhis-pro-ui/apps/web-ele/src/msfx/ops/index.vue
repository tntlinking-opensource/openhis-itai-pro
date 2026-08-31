<script setup lang="ts">
/**
 * 码上放心运维：上传记录分页 + 尝试时间线 + 部分失败码 + 企业 ent_id 查询。
 */
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { MsfxUploadRecordDto } from '#/api/msfx/types';
import type { MsfxEntInfoRowDto } from '#/api/msfx/box-enterprise';
import type { MsfxUploadAttemptDto } from '#/api/msfx/upload';
import type { PageParams } from '#/types/api-response';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElDrawer,
  ElInput,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { listMsfxEntInfoApi } from '#/api/msfx/box-enterprise';
import {
  listMsfxUploadAttemptsApi,
  pageMsfxUploadRecordsApi,
  queryMsfxPartFailDetailApi,
  refreshMsfxBillStatusApi,
  retryMsfxUploadApi,
} from '#/api/msfx/upload';
import {
  MSFX_BILL_TYPE_LABEL,
  MSFX_UPLOAD_STATUS_LABEL,
  canRefreshMsfxStatus,
  canRetryMsfxUpload,
  msfxAttemptTypeLabel,
  msfxBillTypeLabel,
  msfxHisSceneLabel,
  msfxUploadStatusLabel,
  msfxUploadStatusTagType,
} from '#/msfx/constants';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

defineOptions({ name: 'MsfxUploadOpsPage' });

interface MsfxOpsQuery extends PageParams {
  busNo?: string;
  billType?: string;
  uploadStatusEnum?: string;
}

const queryParams = ref<MsfxOpsQuery>({
  pageNo: 1,
  pageSize: 20,
  busNo: '',
  billType: '',
  uploadStatusEnum: '',
});

const tableData = ref<MsfxUploadRecordDto[]>([]);
const total = ref(0);
const loading = ref(false);

const detailVisible = ref(false);
const detailRecord = ref<MsfxUploadRecordDto | null>(null);
const attempts = ref<MsfxUploadAttemptDto[]>([]);
const partFailCodes = ref<{ traceCode?: string; reason?: string }[]>([]);

const entKeyword = ref('');
const entRows = ref<MsfxEntInfoRowDto[]>([]);
const entLoading = ref(false);

const formItems = computed<FormItemConfig[]>(() => [
  { prop: 'busNo', label: '业务单号', type: 'input', placeholder: 'busNo' },
  {
    prop: 'billType',
    label: '单据类型',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      ...Object.entries(MSFX_BILL_TYPE_LABEL).map(([value, label]) => ({ label, value })),
    ],
  },
  {
    prop: 'uploadStatusEnum',
    label: '上传状态',
    type: 'select',
    options: [
      { label: '全部', value: '' },
      ...Object.entries(MSFX_UPLOAD_STATUS_LABEL).map(([value, label]) => ({ label, value })),
    ],
  },
]);

const tableColumns = computed<TableColumn[]>(() => [
  { prop: 'busNo', label: '业务单号', minWidth: 140, showOverflowTooltip: true },
  { prop: 'billType', label: '单据类型', width: 130, slot: 'billType' },
  { prop: 'hisSceneEnum', label: '场景', minWidth: 120, slot: 'scene', showOverflowTooltip: true },
  { prop: 'uploadStatusEnum', label: '状态', width: 110, slot: 'status' },
  { prop: 'billCode', label: '平台单号', minWidth: 140, showOverflowTooltip: true },
  { prop: 'traceCount', label: '码数', width: 70 },
  { prop: 'lastMsgInfo', label: '最近消息', minWidth: 180, showOverflowTooltip: true },
  { prop: 'uploadAt', label: '上传时间', minWidth: 160 },
  { prop: 'actions', label: '操作', width: 220, fixed: 'right', slot: 'actions' },
]);

/** 拉取上传记录分页 */
async function fetchList() {
  loading.value = true;
  try {
    const res = await pageMsfxUploadRecordsApi({
      pageNo: queryParams.value.pageNo,
      pageSize: queryParams.value.pageSize,
      busNo: queryParams.value.busNo?.trim() || undefined,
      billType: queryParams.value.billType?.trim() || undefined,
      uploadStatusEnum: queryParams.value.uploadStatusEnum?.trim() || undefined,
    });
    const { content, totalCount } = parsePagedRowsFromApiData<MsfxUploadRecordDto>(res.data);
    tableData.value = content;
    total.value = totalCount;
  } catch (error) {
    tableData.value = [];
    total.value = 0;
    ElMessage.error(error instanceof Error ? error.message : '查询失败');
  } finally {
    loading.value = false;
  }
}

function handleQuery(params: Record<string, unknown>) {
  Object.assign(queryParams.value, params);
  queryParams.value.pageNo = 1;
  void fetchList();
}

function handleReset() {
  queryParams.value = { pageNo: 1, pageSize: 20, busNo: '', billType: '', uploadStatusEnum: '' };
  void fetchList();
}

function handlePagination(p: { limit: number; page: number }) {
  queryParams.value.pageNo = p.page;
  queryParams.value.pageSize = p.limit;
  void fetchList();
}

/** 打开记录详情（尝试时间线 + 可选部分失败码） */
async function openDetail(row: MsfxUploadRecordDto) {
  detailRecord.value = row;
  detailVisible.value = true;
  attempts.value = [];
  partFailCodes.value = [];
  const recordId = row.id;
  if (recordId === undefined || recordId === null || recordId === '') return;
  try {
    const res = await listMsfxUploadAttemptsApi({ recordId });
    attempts.value = (res.data as MsfxUploadAttemptDto[]) ?? [];
  } catch {
    attempts.value = [];
  }
  if (row.uploadStatusEnum === 'PARTIAL') {
    try {
      const res = await queryMsfxPartFailDetailApi({ recordId });
      partFailCodes.value = res.data?.items ?? [];
    } catch {
      partFailCodes.value = [];
    }
  }
}

async function handleRefresh(row: MsfxUploadRecordDto) {
  const recordId = row.id;
  if (recordId === undefined || recordId === null) return;
  try {
    await refreshMsfxBillStatusApi({ recordId });
    ElMessage.success('已刷新');
    void fetchList();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '刷新失败');
  }
}

async function handleRetry(row: MsfxUploadRecordDto) {
  const recordId = row.id;
  if (recordId === undefined || recordId === null) return;
  try {
    await retryMsfxUploadApi({ recordId, renewBillCode: true });
    ElMessage.success('已触发重传');
    void fetchList();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重传失败');
  }
}

/** 查询供应商企业 ent_id */
async function searchEnt() {
  const name = entKeyword.value.trim();
  if (!name) {
    ElMessage.warning('请输入企业名称关键字');
    return;
  }
  entLoading.value = true;
  try {
    const res = await listMsfxEntInfoApi({ entName: name });
    entRows.value = res.data?.rows ?? [];
    if (entRows.value.length === 0) {
      ElMessage.info(res.data?.msgInfo || '无匹配企业');
    }
  } catch (error) {
    entRows.value = [];
    ElMessage.error(error instanceof Error ? error.message : '企业查询失败');
  } finally {
    entLoading.value = false;
  }
}

function attemptMessage(row: MsfxUploadAttemptDto): string {
  const info = String(row.msgInfo ?? '').trim();
  if (info) return info;
  const code = String(row.msgCode ?? '').trim();
  if (code) return code;
  const summary = String(row.responseSummary ?? '').trim();
  if (summary) return summary.length > 80 ? `${summary.slice(0, 80)}…` : summary;
  return row.successFlag ? '—' : '无返回说明';
}

function copyText(text?: string) {
  const t = String(text ?? '').trim();
  if (!t) return;
  void navigator.clipboard.writeText(t).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.warning('复制失败'),
  );
}

onMounted(() => {
  void fetchList();
});
</script>

<template>
  <Page auto-content-height>
    <div class="flex h-full min-h-0 flex-col gap-3 p-3">
      <TableLayout
        title="码上放心 · 上传记录"
        :form-items="formItems"
        :table-columns="tableColumns"
        :table-data="tableData"
        :loading="loading"
        :total="total"
        :query-params="queryParams"
        @query="handleQuery"
        @reset="handleReset"
        @pagination="handlePagination"
      >
        <template #billType="{ row }">
          {{ msfxBillTypeLabel((row as MsfxUploadRecordDto).billType) }}
        </template>
        <template #scene="{ row }">
          {{ msfxHisSceneLabel((row as MsfxUploadRecordDto).hisSceneEnum) }}
        </template>
        <template #status="{ row }">
          <ElTag
            size="small"
            effect="plain"
            :type="msfxUploadStatusTagType((row as MsfxUploadRecordDto).uploadStatusEnum)"
          >
            {{
              MSFX_UPLOAD_STATUS_LABEL[(row as MsfxUploadRecordDto).uploadStatusEnum ?? ''] ||
              (row as MsfxUploadRecordDto).uploadStatusEnum ||
              '—'
            }}
          </ElTag>
        </template>
        <template #actions="{ row }">
          <ElButton link type="primary" @click="openDetail(row as MsfxUploadRecordDto)">
            详情
          </ElButton>
          <ElButton
            link
            :disabled="!canRefreshMsfxStatus((row as MsfxUploadRecordDto).uploadStatusEnum)"
            @click="handleRefresh(row as MsfxUploadRecordDto)"
          >
            刷新
          </ElButton>
          <ElButton
            link
            type="warning"
            :disabled="!canRetryMsfxUpload((row as MsfxUploadRecordDto).uploadStatusEnum)"
            @click="handleRetry(row as MsfxUploadRecordDto)"
          >
            重传
          </ElButton>
        </template>
      </TableLayout>

      <div class="rounded border p-3" style="border-color: var(--el-border-color)">
        <div class="mb-2 text-sm font-medium">企业 ent_id 查询（getentinfolist）</div>
        <div class="mb-2 flex flex-wrap items-center gap-2">
          <ElInput
            v-model="entKeyword"
            class="!w-[280px]"
            clearable
            placeholder="企业名称关键字"
            @keyup.enter="searchEnt"
          />
          <ElButton type="primary" :loading="entLoading" @click="searchEnt">查询</ElButton>
        </div>
        <ElTable :data="entRows" height="200" size="small">
          <ElTableColumn prop="entName" label="企业名称" min-width="180" show-overflow-tooltip />
          <ElTableColumn prop="entId" label="ent_id" min-width="140" show-overflow-tooltip />
          <ElTableColumn prop="refEntId" label="ref_ent_id" min-width="140" show-overflow-tooltip />
          <ElTableColumn prop="auditStatus" label="审核" width="80" />
          <ElTableColumn prop="settleStatus" label="入驻" width="80" />
          <ElTableColumn label="操作" width="90">
            <template #default="{ row }">
              <ElButton link type="primary" @click="copyText(row.entId)">复制 ent</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
      </div>
    </div>

    <ElDrawer v-model="detailVisible" title="上传记录详情" size="480px">
      <template v-if="detailRecord">
        <div class="mb-3 space-y-1 text-sm">
          <div>单号：{{ detailRecord.busNo }}</div>
          <div>
            场景：{{ msfxHisSceneLabel(detailRecord.hisSceneEnum) }}
            <span v-if="detailRecord.billType">
              / {{ msfxBillTypeLabel(detailRecord.billType) }}
            </span>
          </div>
          <div>平台单号：{{ detailRecord.billCode || '—' }}</div>
          <div>状态：{{ msfxUploadStatusLabel(detailRecord.uploadStatusEnum) }}</div>
          <div>消息：{{ detailRecord.lastMsgInfo || '—' }}</div>
        </div>
        <div class="mb-2 text-sm font-medium">尝试时间线</div>
        <ElTable :data="attempts" size="small" max-height="240">
          <ElTableColumn label="类型" width="110">
            <template #default="{ row }">
              {{ msfxAttemptTypeLabel(row.attemptTypeEnum) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="successFlag" label="成功" width="70">
            <template #default="{ row }">
              {{ row.successFlag ? '是' : '否' }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="消息" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              {{ attemptMessage(row) }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="createdAt" label="时间" width="150" />
        </ElTable>
        <template v-if="partFailCodes.length > 0">
          <div class="mb-2 mt-4 text-sm font-medium">部分失败码</div>
          <ElTable :data="partFailCodes" size="small" max-height="200">
            <ElTableColumn prop="traceCode" label="追溯码" min-width="160" />
            <ElTableColumn prop="reason" label="原因" min-width="160" show-overflow-tooltip />
          </ElTable>
        </template>
      </template>
    </ElDrawer>
  </Page>
</template>
