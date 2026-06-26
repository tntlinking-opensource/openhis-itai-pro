<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { CaseRecordListItemDto, QueryCaseRecordParams } from '#/api/mrms/types';

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
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';

import {
  CASE_RECORD_STATUS_LABEL,
  CASE_RECORD_STATUS_OPTIONS,
  CASE_RECORD_STATUS_TAG,
} from './case-record.constants';
import { buildDefaultListQuery, detectRecordRoomRole } from './homepage/homepage-form.util';

defineOptions({ name: 'MrmsCaseRecordList' });

const router = useRouter();
const userStore = useUserStore();
const tableData = ref<CaseRecordListItemDto[]>([]);
const total = ref(0);
const loading = ref(false);

const isRecordRoom = computed(() =>
  detectRecordRoomRole(userStore.userInfo?.roles as string[] | undefined),
);

/** 从登录信息解析科室名称（多字段兼容） */
function resolveAdmCatyName(): string {
  const u = userStore.userInfo as Record<string, unknown> | undefined;
  const raw = u?.deptName ?? u?.departmentName ?? u?.orgDeptName ?? u?.deptCode;
  return raw === null || raw === undefined ? '' : String(raw).trim();
}

const queryParams = reactive<QueryCaseRecordParams & { dischargeRange?: string[] }>({
  pageNo: 1,
  pageSize: 20,
});

/** 当前登录医师 practitionerId（与就诊参与人一致） */
function resolveAttendingPractitionerId(): string | undefined {
  return resolveLoginPractitionerIdForRequest(
    userStore.userInfo as Record<string, unknown> | undefined,
  );
}

const { run: runPage, loading: pageLoading } = useRequest(queryCaseRecordPageApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData<CaseRecordListItemDto>(data);
    tableData.value = parsed.content;
    total.value = parsed.totalCount;
  },
});

watch(pageLoading, (v) => {
  loading.value = v ?? false;
});

watch(
  () => queryParams.statusCode,
  (val) => {
    if (val) {
      queryParams.statusCodes = undefined;
    }
  },
);

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
    label: '入院科别',
    prop: 'admCatyName',
    type: 'input',
    placeholder: '科别名称',
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
  { prop: 'psnName', label: '姓名', width: 90 },
  { prop: 'iptNo', label: '住院号', width: 120, showOverflowTooltip: true },
  { prop: 'admCatyName', label: '入院科别', width: 120, showOverflowTooltip: true },
  { prop: 'statusCode', label: '状态', width: 100, slot: 'status' },
  { prop: 'admTime', label: '入院时间', width: 168, slot: 'admTime' },
  { prop: 'dscgTime', label: '出院时间', width: 168, slot: 'dscgTime' },
  { prop: 'mainDiagName', label: '主诊断', minWidth: 160, showOverflowTooltip: true },
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
  const range = queryParams.dischargeRange;
  const singleStatus = queryParams.statusCode?.trim();
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    statusCode: singleStatus || undefined,
    statusCodes: singleStatus ? undefined : queryParams.statusCodes,
    admCatyName: queryParams.admCatyName?.trim() || undefined,
    attendingPractitionerId: preserveApiIdForRequest(queryParams.attendingPractitionerId),
    dscgFrom: range?.[0],
    dscgTo: range?.[1],
  };
}

/** 拉取列表 */
async function getList() {
  await runPage(buildQuery());
}

/** 重置为角色默认筛选 */
function handleReset() {
  const defaults = buildDefaultListQuery(
    isRecordRoom.value,
    resolveAdmCatyName(),
    resolveAttendingPractitionerId(),
  );
  queryParams.pageNo = defaults.pageNo;
  queryParams.pageSize = defaults.pageSize;
  queryParams.statusCode = defaults.statusCode;
  queryParams.statusCodes = defaults.statusCodes;
  queryParams.admCatyName = defaults.admCatyName;
  queryParams.attendingPractitionerId = defaults.attendingPractitionerId;
  queryParams.keyword = undefined;
  queryParams.dischargeRange =
    defaults.dscgFrom && defaults.dscgTo ? [defaults.dscgFrom, defaults.dscgTo] : undefined;
  getList();
}

/** 分页变更 */
function handlePagination(payload: { pageNo: number; pageSize: number }) {
  queryParams.pageNo = payload.pageNo;
  queryParams.pageSize = payload.pageSize;
  getList();
}

/** 打开病案首页全屏编辑 */
function openHomepage(row: CaseRecordListItemDto) {
  const id = preserveApiIdForRequest(row.caseRecordId);
  if (!id) {
    return;
  }
  router.push({ name: 'MrmsCaseRecordHomepage', params: { caseRecordId: id } });
}

/** 应用角色默认筛选并首次查询 */
function applyRoleDefaults() {
  const defaults = buildDefaultListQuery(
    isRecordRoom.value,
    resolveAdmCatyName(),
    resolveAttendingPractitionerId(),
  );
  Object.assign(queryParams, {
    pageNo: defaults.pageNo,
    pageSize: defaults.pageSize,
    statusCode: defaults.statusCode,
    statusCodes: defaults.statusCodes,
    admCatyName: defaults.admCatyName,
    attendingPractitionerId: defaults.attendingPractitionerId,
  });
  queryParams.dischargeRange =
    defaults.dscgFrom && defaults.dscgTo ? [defaults.dscgFrom, defaults.dscgTo] : undefined;
}

function statusText(row: CaseRecordListItemDto): string {
  return row.statusLabel || CASE_RECORD_STATUS_LABEL[row.statusCode ?? ''] || row.statusCode || '';
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
      row-key="caseRecordId"
      :show-top-query="true"
      :show-pagination="true"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #status="{ row }">
        <ElTag :type="CASE_RECORD_STATUS_TAG[row.statusCode ?? ''] ?? 'info'" size="small">
          {{ statusText(row) }}
        </ElTag>
      </template>
      <template #admTime="{ row }">
        <span>{{ fmtTime(row.admTime) }}</span>
      </template>
      <template #dscgTime="{ row }">
        <span>{{ fmtTime(row.dscgTime) }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openHomepage(row)"> 病案首页 </ElButton>
      </template>
    </TableLayout>
  </Page>
</template>
