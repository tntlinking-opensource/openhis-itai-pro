<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type {
  CaseRecordListItemDto,
  QcDefectDto,
  QcRunSummaryDto,
  QueryCaseRecordParams,
} from '#/api/mrms/types';

import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import {
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  addManualDefectApi,
  archiveCaseRecordApi,
  closeDefectApi,
  listDefectsApi,
  listQcRunsApi,
  queryCaseRecordPageApi,
  rerunQcApi,
  startQcApi,
} from '#/api/mrms';
import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

import {
  CASE_RECORD_STATUS_LABEL,
  CASE_RECORD_STATUS_OPTIONS,
  CASE_RECORD_STATUS_TAG,
} from '../case-record/case-record.constants';
import {
  QC_DEFECT_SEVERITY_LABEL,
  QC_DEFECT_SEVERITY_OPTIONS,
  QC_DEFECT_SEVERITY_TAG,
  QC_DEFECT_STATUS_LABEL,
  QC_DEFECT_STATUS_TAG,
  QC_WORKBENCH_DEFAULT_STATUSES,
  QUALITY_GRADE_OPTIONS,
} from './qc.constants';

defineOptions({ name: 'MrmsQcWorkbench' });

const router = useRouter();
const route = useRoute();
const tableData = ref<CaseRecordListItemDto[]>([]);
const total = ref(0);
const loading = ref(false);

const queryParams = reactive<QueryCaseRecordParams & { dischargeRange?: string[] }>({
  pageNo: 1,
  pageSize: 20,
  statusCodes: [...QC_WORKBENCH_DEFAULT_STATUSES],
});

const drawerVisible = ref(false);
const activeRow = ref<CaseRecordListItemDto | null>(null);
const defects = ref<QcDefectDto[]>([]);
const qcRuns = ref<QcRunSummaryDto[]>([]);
const lastRunSummary = ref<{ openBlockCount?: number; openWarnCount?: number; passed?: boolean }>(
  {},
);

const manualDefectVisible = ref(false);
const manualSeverity = ref('warn');
const manualMessage = ref('');

const archiveVisible = ref(false);
const qualityGradeCode = ref('');

const activeCaseRecordId = computed(() => preserveApiIdForRequest(activeRow.value?.caseRecordId));

const activeStatus = computed(() => activeRow.value?.statusCode ?? '');

const canStartQc = computed(() => activeStatus.value === 'received');
const canRerunOrArchive = computed(() => activeStatus.value === 'qcing');
const canOpenVolume = computed(
  () => activeStatus.value === 'qcing' || activeStatus.value === 'archived',
);

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

const { run: runListDefects, loading: defectsLoading } = useRequest(listDefectsApi, {
  manual: true,
  onSuccess: (data) => {
    defects.value = data ?? [];
  },
});

const { run: runListRuns, loading: runsLoading } = useRequest(listQcRunsApi, {
  manual: true,
  onSuccess: (data) => {
    qcRuns.value = data ?? [];
  },
});

const { run: runStartQc, loading: startingQc } = useRequest(startQcApi, {
  manual: true,
  onSuccess: (data) => {
    ElMessage.success('已开始质控');
    applyRunResult(data);
    refreshActiveRow();
  },
});

const { run: runRerunQc, loading: rerunningQc } = useRequest(rerunQcApi, {
  manual: true,
  onSuccess: (data) => {
    ElMessage.success('质控已重新执行');
    applyRunResult(data);
    loadDetail();
  },
});

const { run: runCloseDefect, loading: closingDefect } = useRequest(closeDefectApi, {
  manual: true,
  onSuccess: () => {
    ElMessage.success('缺陷已关闭');
    loadDetail();
  },
});

const { run: runAddManual, loading: addingManual } = useRequest(addManualDefectApi, {
  manual: true,
  onSuccess: () => {
    manualDefectVisible.value = false;
    manualMessage.value = '';
    ElMessage.success('已登记手工缺陷');
    loadDetail();
  },
});

const { run: runArchive, loading: archiving } = useRequest(archiveCaseRecordApi, {
  manual: true,
  onSuccess: () => {
    archiveVisible.value = false;
    ElMessage.success('归档成功');
    drawerVisible.value = false;
    getList();
  },
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
    options: CASE_RECORD_STATUS_OPTIONS.filter((o) =>
      QC_WORKBENCH_DEFAULT_STATUSES.includes(o.value as never),
    ),
    clearable: true,
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
  { prop: 'statusCode', label: '状态', width: 100, slot: 'status' },
  { prop: 'dscgTime', label: '出院时间', width: 168, slot: 'dscgTime' },
  { prop: 'mainDiagName', label: '主诊断', minWidth: 160, showOverflowTooltip: true },
  { label: '操作', width: 100, fixed: 'right', slot: 'actions', align: 'center' },
];

const defectColumns: TableColumn[] = [
  { prop: 'severityCode', label: '级别', width: 80, slot: 'severity' },
  { prop: 'statusCode', label: '状态', width: 90, slot: 'defectStatus' },
  { prop: 'ruleCode', label: '规则', width: 140, showOverflowTooltip: true },
  { prop: 'defectMessage', label: '描述', minWidth: 200, showOverflowTooltip: true },
  { label: '操作', width: 90, slot: 'defectActions', align: 'center' },
];

function fmtTime(v?: string): string {
  if (!v) {
    return '';
  }
  return formatDateTime(v);
}

function buildQuery(): QueryCaseRecordParams {
  const range = queryParams.dischargeRange;
  const singleStatus = queryParams.statusCode?.trim();
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    statusCode: singleStatus || undefined,
    statusCodes: singleStatus ? undefined : queryParams.statusCodes,
    dscgFrom: range?.[0],
    dscgTo: range?.[1],
  };
}

async function getList() {
  await runPage(buildQuery());
}

function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 20;
  queryParams.keyword = undefined;
  queryParams.statusCode = undefined;
  queryParams.statusCodes = [...QC_WORKBENCH_DEFAULT_STATUSES];
  queryParams.dischargeRange = undefined;
  getList();
}

function handlePagination(payload: { pageNo: number; pageSize: number }) {
  queryParams.pageNo = payload.pageNo;
  queryParams.pageSize = payload.pageSize;
  getList();
}

function statusText(row: CaseRecordListItemDto): string {
  return row.statusLabel || CASE_RECORD_STATUS_LABEL[row.statusCode ?? ''] || row.statusCode || '';
}

function applyRunResult(data: {
  openBlockCount?: number;
  openWarnCount?: number;
  passed?: boolean;
  defects?: QcDefectDto[];
  caseRecord?: { statusCode?: string; statusLabel?: string };
}) {
  lastRunSummary.value = {
    openBlockCount: data.openBlockCount,
    openWarnCount: data.openWarnCount,
    passed: data.passed,
  };
  if (data.defects) {
    defects.value = data.defects;
  }
  if (data.caseRecord?.statusCode && activeRow.value) {
    activeRow.value = {
      ...activeRow.value,
      statusCode: data.caseRecord.statusCode,
      statusLabel: data.caseRecord.statusLabel,
    };
  }
}

function refreshActiveRow() {
  const id = activeCaseRecordId.value;
  if (!id) {
    return;
  }
  const found = tableData.value.find((r) => preserveApiIdForRequest(r.caseRecordId) === id);
  if (found) {
    activeRow.value = { ...found };
  }
  getList();
  loadDetail();
}

async function loadDetail() {
  const id = activeCaseRecordId.value;
  if (!id) {
    return;
  }
  await Promise.all([runListDefects({ caseRecordId: id }), runListRuns({ caseRecordId: id })]);
}

function openWorkbench(row: CaseRecordListItemDto) {
  activeRow.value = row;
  drawerVisible.value = true;
  lastRunSummary.value = {};
  loadDetail();
}

async function onStartQc() {
  const id = activeCaseRecordId.value;
  if (!id) {
    return;
  }
  await ElMessageBox.confirm('确认开始质控？状态将变为「质控中」并执行规则。', '开始质控', {
    type: 'warning',
  });
  await runStartQc({ caseRecordId: id });
}

async function onRerunQc() {
  const id = activeCaseRecordId.value;
  if (!id) {
    return;
  }
  await runRerunQc({ caseRecordId: id });
}

function openManualDefect() {
  manualSeverity.value = 'warn';
  manualMessage.value = '';
  manualDefectVisible.value = true;
}

async function confirmManualDefect() {
  const id = activeCaseRecordId.value;
  const message = manualMessage.value.trim();
  if (!id || !message) {
    ElMessage.warning('请填写缺陷描述');
    return;
  }
  await runAddManual({
    caseRecordId: id,
    severityCode: manualSeverity.value,
    defectMessage: message,
  });
}

async function onCloseDefect(row: QcDefectDto) {
  const defectId = preserveApiIdForRequest(row.defectId);
  if (!defectId) {
    return;
  }
  await runCloseDefect({ defectId });
}

function openArchiveDialog() {
  qualityGradeCode.value = '';
  archiveVisible.value = true;
}

async function confirmArchive() {
  const id = activeCaseRecordId.value;
  if (!id) {
    return;
  }
  await ElMessageBox.confirm(
    '确认归档？归档前将复检规则，存在阻断级开放缺陷时将拒绝归档。',
    '归档',
    { type: 'warning' },
  );
  await runArchive({
    caseRecordId: id,
    qualityGradeCode: qualityGradeCode.value || undefined,
  });
}

function openVolume() {
  const id = activeCaseRecordId.value;
  if (!id) {
    return;
  }
  router.push({ name: 'MrmsCaseRecordVolume', params: { caseRecordId: id } });
}

function openHomepage() {
  const id = activeCaseRecordId.value;
  if (!id) {
    return;
  }
  router.push({ name: 'MrmsCaseRecordHomepage', params: { caseRecordId: id } });
}

onMounted(async () => {
  await getList();
  const queryId = preserveApiIdForRequest(String(route.query.caseRecordId ?? ''));
  if (queryId) {
    const found = tableData.value.find((r) => preserveApiIdForRequest(r.caseRecordId) === queryId);
    if (found) {
      openWorkbench(found);
    } else {
      activeRow.value = { caseRecordId: queryId, statusCode: 'received' };
      drawerVisible.value = true;
      loadDetail();
    }
  }
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
      <template #dscgTime="{ row }">
        <span>{{ fmtTime(row.dscgTime) }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openWorkbench(row)">质控</ElButton>
      </template>
    </TableLayout>

    <ElDrawer v-model="drawerVisible" title="病案质控" size="720px" destroy-on-close>
      <template v-if="activeRow">
        <ElDescriptions :column="2" border size="small" class="mb-4">
          <ElDescriptionsItem label="病案号">{{ activeRow.medcasno }}</ElDescriptionsItem>
          <ElDescriptionsItem label="姓名">{{ activeRow.psnName }}</ElDescriptionsItem>
          <ElDescriptionsItem label="住院号">{{ activeRow.iptNo }}</ElDescriptionsItem>
          <ElDescriptionsItem label="状态">
            <ElTag :type="CASE_RECORD_STATUS_TAG[activeStatus] ?? 'info'" size="small">
              {{ CASE_RECORD_STATUS_LABEL[activeStatus] || activeStatus }}
            </ElTag>
          </ElDescriptionsItem>
          <ElDescriptionsItem v-if="lastRunSummary.openBlockCount !== undefined" label="开放阻断">
            {{ lastRunSummary.openBlockCount ?? 0 }}
          </ElDescriptionsItem>
          <ElDescriptionsItem v-if="lastRunSummary.openWarnCount !== undefined" label="开放警告">
            {{ lastRunSummary.openWarnCount ?? 0 }}
          </ElDescriptionsItem>
        </ElDescriptions>

        <div class="mb-3 flex flex-wrap gap-2">
          <ElButton v-if="canStartQc" type="primary" :loading="startingQc" @click="onStartQc">
            开始质控
          </ElButton>
          <ElButton v-if="canRerunOrArchive" :loading="rerunningQc" @click="onRerunQc">
            重新质控
          </ElButton>
          <ElButton v-if="canRerunOrArchive" type="success" @click="openArchiveDialog">
            归档
          </ElButton>
          <ElButton v-if="canRerunOrArchive" @click="openManualDefect">登记缺陷</ElButton>
          <ElButton v-if="canOpenVolume" @click="openVolume">卷宗预览</ElButton>
          <ElButton link type="primary" @click="openHomepage">病案首页</ElButton>
        </div>

        <div class="mb-2 font-medium">质控缺陷</div>
        <TableLayout
          :table-data="defects"
          :loading="defectsLoading || closingDefect"
          :total="defects.length"
          :table-columns="defectColumns"
          row-key="defectId"
          :show-top-query="false"
          :show-pagination="false"
          max-height="280"
        >
          <template #severity="{ row }">
            <ElTag :type="QC_DEFECT_SEVERITY_TAG[row.severityCode ?? ''] ?? 'info'" size="small">
              {{ QC_DEFECT_SEVERITY_LABEL[row.severityCode ?? ''] || row.severityCode }}
            </ElTag>
          </template>
          <template #defectStatus="{ row }">
            <ElTag :type="QC_DEFECT_STATUS_TAG[row.statusCode ?? ''] ?? 'info'" size="small">
              {{ QC_DEFECT_STATUS_LABEL[row.statusCode ?? ''] || row.statusCode }}
            </ElTag>
          </template>
          <template #defectActions="{ row }">
            <ElButton
              v-if="row.statusCode === 'open' || row.statusCode === 'rectified'"
              link
              type="primary"
              @click="onCloseDefect(row)"
            >
              关闭
            </ElButton>
          </template>
        </TableLayout>

        <div class="mb-2 mt-4 font-medium">质控批次</div>
        <ElTable v-loading="runsLoading" :data="qcRuns" size="small" border>
          <ElTableColumn prop="finishedAt" label="执行时间" width="168">
            <template #default="{ row }">{{ fmtTime(row.finishedAt) }}</template>
          </ElTableColumn>
          <ElTableColumn prop="triggerCode" label="触发" width="100" />
          <ElTableColumn prop="passed" label="通过" width="70">
            <template #default="{ row }">
              <ElTag :type="row.passed ? 'success' : 'danger'" size="small">
                {{ row.passed ? '是' : '否' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="defectOpenCount" label="阻断" width="70" />
          <ElTableColumn prop="defectWarnCount" label="警告" width="70" />
        </ElTable>
      </template>
    </ElDrawer>

    <AppDialog v-model="manualDefectVisible" title="登记手工缺陷" width="480px">
      <ElForm label-width="88px">
        <ElFormItem label="严重级别">
          <ElSelect v-model="manualSeverity" style="width: 100%">
            <ElOption
              v-for="opt in QC_DEFECT_SEVERITY_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="缺陷描述">
          <ElInput
            v-model="manualMessage"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="manualDefectVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="addingManual" @click="confirmManualDefect"
          >确定</ElButton
        >
      </template>
    </AppDialog>

    <AppDialog v-model="archiveVisible" title="病案归档" width="400px">
      <ElForm label-width="88px">
        <ElFormItem label="质量等级">
          <ElSelect v-model="qualityGradeCode" clearable placeholder="可选" style="width: 100%">
            <ElOption
              v-for="opt in QUALITY_GRADE_OPTIONS"
              :key="String(opt.value)"
              :label="opt.label"
              :value="opt.value"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="archiveVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="archiving" @click="confirmArchive">确认归档</ElButton>
      </template>
    </AppDialog>
  </Page>
</template>
