<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { CaseRecordSummaryDto, QueryCaseRecordParams } from '#/api/mrms/types';

import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElTag } from 'element-plus';

import { queryCaseRecordPageApi } from '#/api/mrms';
import { useRequest } from '#/hooks/use-request';
import { useUserStore } from '@vben/stores';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import {
  CASE_RECORD_STATUS_LABEL,
  CASE_RECORD_STATUS_OPTIONS,
  CASE_RECORD_STATUS_TAG,
} from './case-record.constants';
import { buildDefaultListQuery, detectRecordRoomRole } from './homepage/homepage-form.util';

defineOptions({ name: 'MrmsCaseRecordList' });

const router = useRouter();
const userStore = useUserStore();
const tableData = ref<CaseRecordSummaryDto[]>([]);
const total = ref(0);
const loading = ref(false);

const isRecordRoom = computed(() =>
  detectRecordRoomRole(userStore.userInfo?.roles as string[] | undefined),
);

/** 从登录信息解析科室编码（多字段兼容） */
function resolveDeptCode(): string {
  const u = userStore.userInfo as Record<string, unknown> | undefined;
  const raw = u?.deptCode ?? u?.departmentCode ?? u?.orgDeptCode;
  return raw === null || raw === undefined ? '' : String(raw).trim();
}

const queryParams = reactive<QueryCaseRecordParams>({
  pageNo: 1,
  pageSize: 20,
});

const { run: runPage, loading: pageLoading } = useRequest(queryCaseRecordPageApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData<CaseRecordSummaryDto>(data);
    tableData.value = parsed.content;
    total.value = parsed.totalCount;
  },
});

watch(pageLoading, (v) => {
  loading.value = v ?? false;
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '姓名/病案号/住院号',
  },
  {
    label: '状态',
    prop: 'statusCode',
    type: 'select',
    options: CASE_RECORD_STATUS_OPTIONS,
    clearable: true,
  },
  {
    label: '科室',
    prop: 'deptCode',
    type: 'input',
    placeholder: '科室编码',
  },
  {
    label: '出院日期',
    prop: 'dischargeRange',
    type: 'daterange',
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 56, align: 'center' },
  { prop: 'medcasno', label: '病案号', width: 120, showOverflowTooltip: true },
  { prop: 'patientName', label: '姓名', width: 90 },
  { prop: 'inpatientNo', label: '住院号', width: 120, showOverflowTooltip: true },
  { prop: 'deptName', label: '科室', width: 120, showOverflowTooltip: true },
  { prop: 'statusCode', label: '状态', width: 100, slot: 'status' },
  { prop: 'admissionAt', label: '入院时间', width: 168, slot: 'admissionAt' },
  { prop: 'dischargeAt', label: '出院时间', width: 168, slot: 'dischargeAt' },
  { prop: 'primaryDiagnosisText', label: '主诊断', minWidth: 160, showOverflowTooltip: true },
  { label: '操作', width: 120, fixed: 'right', slot: 'actions', align: 'center' },
];

/** 格式化日期时间展示 */
function fmtTime(v?: string): string {
  if (!v) {
    return '';
  }
  return formatDateTime(v);
}

/** 组装分页查询参数 */
function buildQuery(): QueryCaseRecordParams {
  const range = (queryParams as QueryCaseRecordParams & { dischargeRange?: string[] })
    .dischargeRange;
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    statusCode: queryParams.statusCode || undefined,
    deptCode: queryParams.deptCode?.trim() || undefined,
    dischargeFrom: range?.[0],
    dischargeTo: range?.[1],
  };
}

/** 拉取列表 */
async function getList() {
  await runPage(buildQuery());
}

/** 重置为角色默认筛选 */
function handleReset() {
  const defaults = buildDefaultListQuery(isRecordRoom.value, resolveDeptCode());
  queryParams.pageNo = defaults.pageNo;
  queryParams.pageSize = defaults.pageSize;
  queryParams.statusCode = defaults.statusCode;
  queryParams.deptCode = defaults.deptCode;
  queryParams.keyword = undefined;
  const ext = queryParams as QueryCaseRecordParams & { dischargeRange?: string[] };
  ext.dischargeRange =
    defaults.dischargeFrom && defaults.dischargeTo
      ? [defaults.dischargeFrom, defaults.dischargeTo]
      : undefined;
  getList();
}

/** 分页变更 */
function handlePagination(payload: { pageNo: number; pageSize: number }) {
  queryParams.pageNo = payload.pageNo;
  queryParams.pageSize = payload.pageSize;
  getList();
}

/** 打开病案首页全屏编辑 */
function openHomepage(row: CaseRecordSummaryDto) {
  const id = String(row.id ?? '').trim();
  if (!id) {
    return;
  }
  router.push({ name: 'MrmsCaseRecordHomepage', params: { caseRecordId: id } });
}

/** 应用角色默认筛选并首次查询 */
function applyRoleDefaults() {
  const defaults = buildDefaultListQuery(isRecordRoom.value, resolveDeptCode());
  Object.assign(queryParams, {
    pageNo: defaults.pageNo,
    pageSize: defaults.pageSize,
    statusCode: defaults.statusCode,
    deptCode: defaults.deptCode,
  });
  const ext = queryParams as QueryCaseRecordParams & { dischargeRange?: string[] };
  ext.dischargeRange =
    defaults.dischargeFrom && defaults.dischargeTo
      ? [defaults.dischargeFrom, defaults.dischargeTo]
      : undefined;
}

onMounted(() => {
  applyRoleDefaults();
  getList();
});

onActivated(() => {
  getList();
});
</script>

<template>
  <Page auto-content-height>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams as Record<string, unknown>"
      :form-items="formItems"
      :table-columns="tableColumns"
      row-key="id"
      :show-top-query="true"
      :show-pagination="true"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #status="{ row }">
        <ElTag :type="CASE_RECORD_STATUS_TAG[row.statusCode ?? ''] ?? 'info'" size="small">
          {{ CASE_RECORD_STATUS_LABEL[row.statusCode ?? ''] ?? row.statusCode }}
        </ElTag>
      </template>
      <template #admissionAt="{ row }">
        <span>{{ fmtTime(row.admissionAt) }}</span>
      </template>
      <template #dischargeAt="{ row }">
        <span>{{ fmtTime(row.dischargeAt) }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openHomepage(row)"> 病案首页 </ElButton>
      </template>
    </TableLayout>
  </Page>
</template>
