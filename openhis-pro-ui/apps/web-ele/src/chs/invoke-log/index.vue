<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { InvokeLogDetail, InvokeLogRow } from '#/chs/api/invoke-log';
import type { PageParams } from '#/types/api-response';

import { computed, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElInput,
  ElMessage,
  ElTag,
} from 'element-plus';

import { getInvokeLogDetailApi, pageInvokeLogApi } from '#/chs/api/invoke-log';
import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';

defineOptions({ name: 'ChsInvokeLogPage' });

interface InvokeLogQuery extends PageParams {
  /** QuickDateRange：YYYY-MM-DD HH:mm:ss */
  dateRange?: string[];
  infno?: string;
  /** 'true' | 'false' | undefined */
  successFlag?: string;
  msgid?: string;
  contractBusNo?: string;
  encounterBusNo?: string;
  patientName?: string;
  encounterId?: string;
  chrgBchno?: string;
  paymentDraftId?: string;
  settlementRecordId?: string;
  errMsgKeyword?: string;
}

function pad2(n: number) {
  return String(n).padStart(2, '0');
}

function formatLocalDateTime(d: Date, endOfDay = false): string {
  const y = d.getFullYear();
  const m = pad2(d.getMonth() + 1);
  const day = pad2(d.getDate());
  if (endOfDay) {
    return `${y}-${m}-${day} 23:59:59`;
  }
  return `${y}-${m}-${day} 00:00:00`;
}

/** 默认近 3 天（含今天） */
function defaultDateRange(): string[] {
  const end = new Date();
  const start = new Date();
  start.setDate(end.getDate() - 2);
  return [formatLocalDateTime(start, false), formatLocalDateTime(end, true)];
}

const queryParams = ref<InvokeLogQuery>({
  pageNo: 1,
  pageSize: 20,
  dateRange: defaultDateRange(),
});

const tableData = ref<InvokeLogRow[]>([]);
const total = ref(0);

const detailVisible = ref(false);
const detail = ref<InvokeLogDetail | null>(null);

/** 报文全文弹窗（格式化后的请求/响应） */
const payloadVisible = ref(false);
const payloadTitle = ref('');
const payloadText = ref('');

/** 报文尽量格式化为可读 JSON；非 JSON 原文返回 */
function formatJsonText(raw?: string | null): string {
  const text = String(raw ?? '').trim();
  if (!text) return '';
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

/** 详情内预览用的格式化请求/响应 */
const formattedRequestJson = computed(() => formatJsonText(detail.value?.requestJson));
const formattedResponseJson = computed(() => formatJsonText(detail.value?.responseJson));

const formItems = computed<FormItemConfig[]>(() => [
  {
    type: 'daterange',
    prop: 'dateRange',
    label: '调用时间',
    startPlaceholder: '开始',
    endPlaceholder: '结束',
    valueFormat: 'YYYY-MM-DD HH:mm:ss',
    clearable: false,
  },
  {
    type: 'input',
    prop: 'infno',
    label: '交易编号',
    placeholder: '如 2207',
    clearable: true,
  },
  {
    type: 'select',
    prop: 'successFlag',
    label: '结果',
    clearable: true,
    multiple: false,
    options: [
      { label: '成功', value: 'true' },
      { label: '失败', value: 'false' },
    ],
  },
  {
    type: 'input',
    prop: 'msgid',
    label: '报文ID',
    placeholder: 'msgid',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'patientName',
    label: '患者姓名',
    placeholder: '模糊匹配',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'encounterBusNo',
    label: '就诊号',
    placeholder: '院内就诊号',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'encounterId',
    label: '就诊ID',
    placeholder: 'encounterId',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'contractBusNo',
    label: '契约号',
    placeholder: 'contractBusNo',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'chrgBchno',
    label: '收费批次',
    placeholder: 'chrgBchno',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'settlementRecordId',
    label: '结算ID',
    placeholder: 'settlementRecordId',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'paymentDraftId',
    label: '支付单ID',
    placeholder: 'paymentDraftId',
    clearable: true,
  },
  {
    type: 'input',
    prop: 'errMsgKeyword',
    label: '错误关键字',
    placeholder: 'err_msg 模糊',
    clearable: true,
  },
]);

const tableColumns: TableColumn[] = [
  {
    prop: 'createdAt',
    label: '时间',
    width: 168,
    align: 'center',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => formatDisplayTime(cellValue),
  },
  {
    prop: 'infno',
    label: '交易',
    minWidth: 160,
    showOverflowTooltip: true,
    slot: 'infno',
  },
  {
    prop: 'success',
    label: '结果',
    width: 80,
    align: 'center',
    headerAlign: 'center',
    slot: 'success',
  },
  {
    prop: 'durationMs',
    label: '耗时(ms)',
    width: 90,
    align: 'center',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) =>
      cellValue === null || cellValue === undefined || cellValue === '' ? '—' : String(cellValue),
  },
  {
    prop: 'msgid',
    label: '报文ID',
    width: 140,
    showOverflowTooltip: true,
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'encounterBusNo',
    label: '就诊号',
    width: 140,
    showOverflowTooltip: true,
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'patientName',
    label: '患者',
    width: 100,
    showOverflowTooltip: true,
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'contractBusNo',
    label: '契约',
    width: 120,
    showOverflowTooltip: true,
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'chrgBchno',
    label: '批次号',
    width: 120,
    showOverflowTooltip: true,
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'errMsg',
    label: '错误',
    minWidth: 140,
    showOverflowTooltip: true,
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'requestPreview',
    label: '请求摘要',
    minWidth: 180,
    showOverflowTooltip: true,
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'responsePreview',
    label: '响应摘要',
    minWidth: 180,
    showOverflowTooltip: true,
    formatter: (_row, _column, cellValue) => String(cellValue ?? '').trim() || '—',
  },
  {
    prop: 'actions',
    label: '操作',
    width: 88,
    align: 'center',
    headerAlign: 'center',
    fixed: 'right',
    slot: 'actions',
  },
];

/** 列表/详情时间展示：去掉 T，截到秒 */
function formatDisplayTime(value: unknown): string {
  const raw = String(value ?? '').trim();
  if (!raw) return '—';
  return raw.replace('T', ' ').slice(0, 19);
}

function emptyToUndef(v?: string) {
  const t = String(v ?? '').trim();
  return t || undefined;
}

function toOptionalId(v?: string) {
  const t = emptyToUndef(v);
  if (!t) return undefined;
  if (!/^\d+$/.test(t)) return undefined;
  return t;
}

function buildPageParams(q: InvokeLogQuery) {
  const range = q.dateRange;
  if (!range?.[0] || !range?.[1]) {
    throw new Error('请选择调用时间范围');
  }
  let success: boolean | undefined;
  if (q.successFlag === 'true') success = true;
  else if (q.successFlag === 'false') success = false;

  return {
    pageNo: q.pageNo ?? 1,
    pageSize: q.pageSize ?? 20,
    createdFrom: range[0],
    createdTo: range[1],
    infno: emptyToUndef(q.infno),
    success,
    msgid: emptyToUndef(q.msgid),
    contractBusNo: emptyToUndef(q.contractBusNo),
    encounterBusNo: emptyToUndef(q.encounterBusNo),
    patientName: emptyToUndef(q.patientName),
    encounterId: toOptionalId(q.encounterId),
    chrgBchno: emptyToUndef(q.chrgBchno),
    paymentDraftId: toOptionalId(q.paymentDraftId),
    settlementRecordId: toOptionalId(q.settlementRecordId),
    errMsgKeyword: emptyToUndef(q.errMsgKeyword),
  };
}

const { loading: pageLoading, run: runPageQuery } = useRequest(pageInvokeLogApi, {
  manual: true,
  onSuccess: (result) => {
    tableData.value = result.content;
    total.value = result.totalCount;
  },
});

const { loading: detailLoading, run: runDetail } = useRequest(getInvokeLogDetailApi, {
  manual: true,
  onSuccess: (result) => {
    detail.value = result;
    detailVisible.value = true;
  },
});

async function loadList(params?: InvokeLogQuery) {
  const merged = { ...queryParams.value, ...(params ?? {}) };
  try {
    const apiParams = buildPageParams(merged);
    await runPageQuery(apiParams);
  } catch (e) {
    ElMessage.warning(e instanceof Error ? e.message : '查询参数无效');
  }
}

function handleQuery(params: InvokeLogQuery) {
  queryParams.value = {
    ...queryParams.value,
    ...params,
    pageNo: 1,
  };
  void loadList(queryParams.value);
}

function handleReset() {
  queryParams.value = {
    pageNo: 1,
    pageSize: queryParams.value.pageSize ?? 20,
    dateRange: defaultDateRange(),
  };
  void loadList(queryParams.value);
}

function handlePagination(pagination: { pageNo?: number; pageSize?: number }) {
  if (pagination.pageNo) {
    queryParams.value.pageNo = pagination.pageNo;
  }
  if (pagination.pageSize) {
    queryParams.value.pageSize = pagination.pageSize;
  }
  void loadList(queryParams.value);
}

async function openDetail(row: InvokeLogRow) {
  const id = row.id;
  if (id === null || id === undefined || id === '') {
    ElMessage.warning('缺少日志主键');
    return;
  }
  await runDetail(id);
}

/** 复制文本到剪贴板 */
async function copyText(text?: string, label = '内容') {
  const raw = String(text ?? '').trim();
  if (!raw) {
    ElMessage.warning(`${label}为空`);
    return;
  }
  try {
    await navigator.clipboard.writeText(raw);
    ElMessage.success(`已复制${label}`);
  } catch {
    ElMessage.error('复制失败');
  }
}

/** 打开格式化后的请求/响应全文弹窗 */
function openPayload(kind: 'request' | 'response') {
  if (!detail.value) return;
  const title = kind === 'request' ? '请求报文' : '响应报文';
  const text = kind === 'request' ? formattedRequestJson.value : formattedResponseJson.value;
  if (!text) {
    ElMessage.warning(`${title}为空`);
    return;
  }
  payloadTitle.value = title;
  payloadText.value = text;
  payloadVisible.value = true;
}

onMounted(() => {
  void loadList();
});
</script>

<template>
  <Page auto-content-height content-class="!overflow-hidden flex h-full min-h-0 flex-col">
    <TableLayout
      class="h-full min-h-0 flex-1"
      table-height="100%"
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
      row-key="id"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <ElAlert
          class="mb-2"
          type="info"
          :closable="false"
          show-icon
          title="查询本地医保网关调用日志（yb_invoke_log）。时间跨度最长 31 天；列表为报文摘要，详情可看全文。就诊号/患者姓名筛选会先解析就诊再查日志；无就诊上下文的交易无法按姓名命中。"
        />
      </template>

      <template #infno="{ row }">
        <span>
          {{ row.infno || '—' }}
          <template v-if="row.infname"> · {{ row.infname }}</template>
        </span>
      </template>

      <template #success="{ row }">
        <ElTag v-if="row.success === true" type="success" effect="light" size="small">成功</ElTag>
        <ElTag v-else-if="row.success === false" type="danger" effect="light" size="small">
          失败
        </ElTag>
        <span v-else>—</span>
      </template>

      <template #actions="{ row }">
        <ElButton link type="primary" :loading="detailLoading" @click="openDetail(row)">
          详情
        </ElButton>
      </template>
    </TableLayout>

    <AppDialog v-model="detailVisible" title="调用日志详情" width="900px">
      <template v-if="detail">
        <ElDescriptions :column="2" border size="small" class="mb-3">
          <ElDescriptionsItem label="时间">
            {{ formatDisplayTime(detail.createdAt) }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="耗时"> {{ detail.durationMs ?? '—' }} ms </ElDescriptionsItem>
          <ElDescriptionsItem label="交易">
            {{ detail.infno || '—' }}
            <template v-if="detail.infname"> · {{ detail.infname }}</template>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="结果">
            <ElTag v-if="detail.success === true" type="success" effect="light" size="small">
              成功
            </ElTag>
            <ElTag v-else-if="detail.success === false" type="danger" effect="light" size="small">
              失败
            </ElTag>
            <span v-else>—</span>
            <span v-if="detail.infcode" class="ml-2 text-gray-500">
              infcode={{ detail.infcode }}
            </span>
          </ElDescriptionsItem>
          <ElDescriptionsItem label="报文ID">{{ detail.msgid || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="契约">{{ detail.contractBusNo || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="就诊号">{{ detail.encounterBusNo || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="患者">{{ detail.patientName || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="就诊ID">{{ detail.encounterId || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="收费批次">{{ detail.chrgBchno || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="结算ID">
            {{ detail.settlementRecordId || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="支付单ID">
            {{ detail.paymentDraftId || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem label="预结算ID">{{ detail.preSettleId || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem label="错误信息" :span="2">
            {{ detail.errMsg || '—' }}
          </ElDescriptionsItem>
        </ElDescriptions>

        <div class="mb-2 flex items-center justify-between">
          <span class="font-medium" style="color: var(--el-text-color-primary)">请求报文</span>
          <div class="flex gap-2">
            <ElButton size="small" @click="openPayload('request')">查看完整</ElButton>
            <ElButton size="small" @click="copyText(formattedRequestJson, '请求报文')">
              复制
            </ElButton>
          </div>
        </div>
        <ElInput
          :model-value="formattedRequestJson"
          type="textarea"
          :rows="10"
          readonly
          class="mb-4 font-mono text-xs"
        />

        <div class="mb-2 flex items-center justify-between">
          <span class="font-medium" style="color: var(--el-text-color-primary)">响应报文</span>
          <div class="flex gap-2">
            <ElButton size="small" @click="openPayload('response')">查看完整</ElButton>
            <ElButton size="small" @click="copyText(formattedResponseJson, '响应报文')">
              复制
            </ElButton>
          </div>
        </div>
        <ElInput
          :model-value="formattedResponseJson"
          type="textarea"
          :rows="10"
          readonly
          class="font-mono text-xs"
        />
      </template>
    </AppDialog>

    <AppDialog v-model="payloadVisible" :title="payloadTitle" width="80%" top="4vh">
      <div class="mb-2 flex justify-end">
        <ElButton size="small" @click="copyText(payloadText, payloadTitle)">复制</ElButton>
      </div>
      <pre class="invoke-log-payload-pre">{{ payloadText }}</pre>
    </AppDialog>
  </Page>
</template>

<style scoped>
.invoke-log-payload-pre {
  margin: 0;
  max-height: calc(80vh - 120px);
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
