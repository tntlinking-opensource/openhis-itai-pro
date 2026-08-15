<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { OptionDto } from '#/api/outpatient-registration';
import type { OutpatientEmrListItemDto, QueryOutpatientEmrParams } from '#/api/mrms/types';

import { computed, onActivated, onMounted, provide, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElDialog, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { generateDocumentPdfApi } from '#/api/emr/document';
import {
  archiveOutpatientClinicalCaseApi,
  queryOutpatientEmrPageApi,
  unarchiveOutpatientClinicalCaseApi,
} from '#/api/mrms';
import { httpListDepartments, httpListPractitioners } from '#/api/outpatient-registration';
import { ENSURE_CLINICAL_RECEPTION_KEY } from '#/components/doctor-station';
import MedicalRecordForm from '#/components/outpatient/medical-record-form.vue';
import EmrPdfPreviewDialog from '#/components/outpatient/emr-pdf-preview-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import { EMR_TEMPLATE_STATION_KIND } from '#/views/basic/emr-template/emr-template-scope.constants';

import { isOutpatientClinicalEditable } from '../mrms-clinical-edit-gate';

defineOptions({ name: 'MrmsOutpatientEmrQuery' });

/**
 * T3：门诊病历查询（独立菜单）
 * 列表与编辑均以就诊 encounter 为锚点；侧栏仅本就诊文书，不展示患者跨就诊历史。
 * 本人接诊可编辑；非接诊/已归档只读。「归档病例」由后端 ROLE_MRMS_ADMIN 校验。
 */

const userStore = useUserStore();
const tableData = ref<OutpatientEmrListItemDto[]>([]);
const total = ref(0);
const loading = ref(false);

const departmentOptions = ref<OptionDto[]>([]);
const doctorOptions = ref<OptionDto[]>([]);

const queryParams = reactive<QueryOutpatientEmrParams & { encounterRange?: string[] }>({
  pageNo: 1,
  pageSize: 20,
});

const pdfVisible = ref(false);
const pdfBase64 = ref('');
const pdfPageCount = ref<number | undefined>();
const pdfTitle = ref('门诊病历');
const pdfAutoPrint = ref(false);

/** 病历编辑弹层 */
const editVisible = ref(false);
const editReadonly = ref(true);
const editEncounterId = ref('');
const editPatientId = ref('');
const editDocumentId = ref('');
const editTitle = ref('门诊病历');

/** 当前登录医务人员主键 */
const loginPractitionerId = computed(() =>
  resolveLoginPractitionerIdForRequest(userStore.userInfo as Record<string, unknown> | null),
);

const DOCUMENT_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '已提交',
  LOCKED: '已锁定',
  ARCHIVED: '已归档',
};

const DOCUMENT_STATUS_TAG: Record<string, 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'info',
  SUBMITTED: 'success',
  LOCKED: 'warning',
  ARCHIVED: 'danger',
};

const DOCUMENT_STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已提交', value: 'SUBMITTED' },
  { label: '已锁定', value: 'LOCKED' },
  { label: '已归档', value: 'ARCHIVED' },
];

const { run: runPage, loading: pageLoading } = useRequest(queryOutpatientEmrPageApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData<OutpatientEmrListItemDto>(data);
    tableData.value = parsed.content;
    total.value = parsed.totalCount;
  },
});

watch(pageLoading, (v) => {
  loading.value = v ?? false;
});

/** 病案弹层内：已绑定就诊即可继续 */
provide(ENSURE_CLINICAL_RECEPTION_KEY, async () => {
  if (!editEncounterId.value || !editPatientId.value) {
    ElMessage.warning('缺少就诊或患者主键');
    return false;
  }
  return true;
});

/** 下拉选项：保证 value 为 string */
function toSelectOptions(rows: OptionDto[] | undefined): OptionDto[] {
  if (!rows?.length) {
    return [];
  }
  // 过滤无主键项，并统一 value 为 string
  const options: OptionDto[] = [];
  for (const row of rows) {
    const value = preserveApiIdForRequest(row.value);
    if (!value) {
      continue;
    }
    options.push({
      value,
      label: row.label ?? value,
      disabled: row.disabled,
    });
  }
  return options;
}

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '关键词',
    prop: 'keyword',
    type: 'input',
    placeholder: '姓名/门诊号/病案号',
  },
  {
    label: '科室',
    prop: 'organizationId',
    type: 'select',
    placeholder: '请选择科室',
    options: departmentOptions.value,
    clearable: true,
    filterable: true,
  },
  {
    label: '接诊医生',
    prop: 'admitterPractitionerId',
    type: 'select',
    placeholder: '请选择接诊医生',
    options: doctorOptions.value,
    clearable: true,
    filterable: true,
  },
  {
    label: '状态',
    prop: 'statusCode',
    type: 'select',
    options: DOCUMENT_STATUS_OPTIONS,
    clearable: true,
  },
  {
    label: '就诊日期',
    prop: 'encounterRange',
    type: 'daterange',
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 56, align: 'center' },
  { prop: 'patientName', label: '姓名', width: 90, align: 'center' },
  { prop: 'outpatientNo', label: '门诊号', width: 130, align: 'center', showOverflowTooltip: true },
  { prop: 'deptName', label: '科室', width: 120, align: 'center', showOverflowTooltip: true },
  { prop: 'doctorName', label: '接诊医生', width: 100, align: 'center', showOverflowTooltip: true },
  { prop: 'encounterTime', label: '就诊时间', width: 168, align: 'center', slot: 'encounterTime' },
  { prop: 'title', label: '文书标题', minWidth: 160, align: 'center', showOverflowTooltip: true },
  { prop: 'statusCode', label: '状态', width: 100, align: 'center', slot: 'status' },
  { prop: 'updatedAt', label: '更新时间', width: 168, align: 'center', slot: 'updatedAt' },
  { label: '操作', width: 320, fixed: 'right', slot: 'actions', align: 'center' },
];

/** 格式化日期时间展示 */
function fmtTime(v?: string): string {
  if (!v) {
    return '';
  }
  return formatDateTime(v);
}

/** 组装分页查询参数 */
function buildQuery(): QueryOutpatientEmrParams {
  const range = queryParams.encounterRange;
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    organizationId: preserveApiIdForRequest(queryParams.organizationId),
    admitterPractitionerId: preserveApiIdForRequest(queryParams.admitterPractitionerId),
    statusCode: queryParams.statusCode?.trim() || undefined,
    encounterDateFrom: range?.[0],
    encounterDateTo: range?.[1],
  };
}

/** 拉取列表 */
async function getList() {
  await runPage(buildQuery());
}

/** 搜索：回第一页再查 */
function handleQuery() {
  queryParams.pageNo = 1;
  getList();
}

/** 重置筛选（病案跨患者查询：接诊医生默认不限） */
function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 20;
  queryParams.keyword = undefined;
  queryParams.organizationId = undefined;
  queryParams.admitterPractitionerId = undefined;
  queryParams.statusCode = undefined;
  queryParams.encounterRange = undefined;
  getList();
}

/** 分页变更（TableLayout 契约为 page/limit） */
function handlePagination(pagination: { page: number; limit: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

/** 状态 code → 展示文案 */
function statusText(row: OutpatientEmrListItemDto): string {
  return DOCUMENT_STATUS_LABEL[row.statusCode ?? ''] || row.statusCode || '';
}

/**
 * 打开门诊病历编辑/只读弹层（仅本就诊；侧栏不拉患者历史）。
 *
 * @param row 列表行
 */
function openEdit(row: OutpatientEmrListItemDto) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  const patientId = preserveApiIdForRequest(row.patientId);
  if (!encounterId) {
    ElMessage.warning('缺少就诊主键，无法打开病历');
    return;
  }
  if (!patientId) {
    ElMessage.warning('缺少患者主键，无法打开病历');
    return;
  }
  const editable = isOutpatientClinicalEditable(loginPractitionerId.value, row);
  editEncounterId.value = encounterId;
  editPatientId.value = patientId;
  editDocumentId.value = preserveApiIdForRequest(row.documentId) ?? '';
  editReadonly.value = !editable;
  editTitle.value = row.title || `${row.patientName || ''}门诊病历`;
  editVisible.value = true;
}

/**
 * 生成门诊病历 PDF 并打开预览/打印对话框。
 *
 * @param row 列表行
 * @param autoPrint 是否自动调起系统打印
 */
async function openPdf(row: OutpatientEmrListItemDto, autoPrint: boolean) {
  const documentId = preserveApiIdForRequest(row.documentId);
  if (!documentId) {
    ElMessage.warning('文书主键缺失');
    return;
  }
  try {
    loading.value = true;
    const result = await generateDocumentPdfApi({
      documentId,
      patientId: preserveApiIdForRequest(row.patientId),
      encounterId: preserveApiIdForRequest(row.encounterId),
      documentTitle: row.title || '门诊病历',
      documentTypeName: row.documentTypeName || '门诊病历',
      previewMode: true,
    });
    const base64 = result?.pdfBase64?.trim();
    if (!base64) {
      ElMessage.error('未生成 PDF 内容');
      return;
    }
    pdfBase64.value = base64;
    pdfPageCount.value = result.pageCount;
    pdfTitle.value = row.title || `${row.patientName || ''}门诊病历`;
    pdfAutoPrint.value = autoPrint;
    pdfVisible.value = true;
  } catch {
    ElMessage.error(autoPrint ? '打印失败' : '预览失败');
  } finally {
    loading.value = false;
  }
}

/**
 * 归档病例：确认后锁定该就诊下 EMR 文书。
 *
 * @param row 列表行
 */
async function handleArchiveClinicalCase(row: OutpatientEmrListItemDto) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) {
    ElMessage.warning('缺少就诊主键，无法归档病例');
    return;
  }
  try {
    await ElMessageBox.confirm('归档后该次就诊的病历将不可编辑，确认归档病例？', '归档病例', {
      type: 'warning',
      confirmButtonText: '确认归档',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    loading.value = true;
    await archiveOutpatientClinicalCaseApi({ encounterId });
    ElMessage.success('病例已归档');
    await getList();
  } catch {
    // 错误由全局拦截器提示
  } finally {
    loading.value = false;
  }
}

/**
 * 解档病例：确认后解锁 LOCKED 文书（病案主档已归档时后端拒绝）。
 *
 * @param row 列表行
 */
async function handleUnarchiveClinicalCase(row: OutpatientEmrListItemDto) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) {
    ElMessage.warning('缺少就诊主键，无法解档病例');
    return;
  }
  try {
    await ElMessageBox.confirm('解档后接诊医生可再次编辑病历，确认解档病例？', '解档病例', {
      type: 'warning',
      confirmButtonText: '确认解档',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    loading.value = true;
    await unarchiveOutpatientClinicalCaseApi({ encounterId });
    ElMessage.success('病例已解档');
    await getList();
  } catch {
    // 错误由全局拦截器提示
  } finally {
    loading.value = false;
  }
}

/** 加载可挂号科室与全部医生下拉 */
async function loadFilterOptions() {
  const [deptRes, doctorRes] = await Promise.all([
    httpListDepartments({}),
    httpListPractitioners({}),
  ]);
  departmentOptions.value = toSelectOptions(deptRes?.departmentOptions);
  doctorOptions.value = toSelectOptions(doctorRes?.practitionerOptions);
}

onMounted(async () => {
  await loadFilterOptions();
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
      row-key="documentId"
      :show-top-query="true"
      :show-pagination="true"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #status="{ row }">
        <ElTag :type="DOCUMENT_STATUS_TAG[row.statusCode ?? ''] ?? 'info'" size="small">
          {{ statusText(row) }}
        </ElTag>
      </template>
      <template #encounterTime="{ row }">
        <span>{{ fmtTime(row.encounterTime) }}</span>
      </template>
      <template #updatedAt="{ row }">
        <span>{{ fmtTime(row.updatedAt) }}</span>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openEdit(row)">病历</ElButton>
        <ElButton link type="primary" @click="openPdf(row, false)">预览</ElButton>
        <ElButton link type="primary" @click="openPdf(row, true)">打印</ElButton>
        <ElButton
          v-if="!row.clinicalArchived"
          link
          type="warning"
          @click="handleArchiveClinicalCase(row)"
        >
          归档病例
        </ElButton>
        <ElButton
          v-if="row.clinicalArchived"
          link
          type="warning"
          @click="handleUnarchiveClinicalCase(row)"
        >
          解档病例
        </ElButton>
      </template>
    </TableLayout>

    <ElDialog
      v-model="editVisible"
      :title="editTitle"
      width="96%"
      top="2vh"
      destroy-on-close
      append-to-body
      class="mrms-outpatient-emr-edit-dialog"
    >
      <div v-if="editVisible" class="mrms-outpatient-emr-edit-dialog__body">
        <MedicalRecordForm
          :station-kind="EMR_TEMPLATE_STATION_KIND.OUTPATIENT"
          :encounter-id="editEncounterId"
          :patient-id="editPatientId"
          :initial-document-id="editDocumentId"
          encounter-scoped
          :force-read-only="editReadonly"
        />
      </div>
    </ElDialog>

    <EmrPdfPreviewDialog
      v-model="pdfVisible"
      :pdf-base64="pdfBase64"
      :page-count="pdfPageCount"
      :title="pdfTitle"
      :auto-print="pdfAutoPrint"
    />
  </Page>
</template>

<style scoped>
.mrms-outpatient-emr-edit-dialog__body {
  height: min(82vh, 900px);
  min-height: 480px;
  overflow: hidden;
}
</style>
