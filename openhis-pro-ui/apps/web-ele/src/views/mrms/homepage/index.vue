<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@vben-core/table-layout-ui';

import type { HomepageFormModel } from './homepage-form.types';
import type { HomepageListQuery, HomepageListRow } from './homepage-list.types';
import type {
  HomepageInsActionCode,
  HomepageRowActionCode,
  HomepageSelectOption,
} from './homepage.constants';
import type { MrmsInpatientClinicalKind } from '../components/mrms-inpatient-clinical-host.vue';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElMessage, ElMessageBox, ElTag, ElTooltip } from 'element-plus';

import { httpLoadInpatientRegistrationContext } from '#/api/inpatient-registration';
import {
  loadHomepagePageApi,
  queryCaseRecordPageApi,
  refreshAutoFillApi,
  saveHomepageApi,
  submitHomepageApi,
} from '#/api/mrms';
import {
  modifySettleListStatusApi,
  querySettleList4103Api,
  reportOneClickApi,
  upload4401Api,
  uploadSettleListApi,
  type OneClickReportResultDto,
  type UploadResultDto,
} from '#/chs/api/medcas-ops';
import { useRequest } from '#/hooks/use-request';
import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  resolveLoginOrganizationIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';

import MrmsInpatientClinicalHost from '../components/mrms-inpatient-clinical-host.vue';
import { resolveInpatientClinicalEditDenial } from '../mrms-clinical-edit-gate';
import HomepageEditPanel from './components/homepage-edit-panel.vue';
import HomepageInsUploadActions from './components/homepage-ins-upload-actions.vue';
import HomepageRowActions from './components/homepage-row-actions.vue';
import {
  ALL_OPTION_VALUE,
  DEPT_ALL_OPTION,
  HOMEPAGE_ROW_ACTIONS,
  UPLOAD_STATUS_LABEL,
  UPLOAD_STATUS_OPTIONS,
  YES_NO_DISCHARGE_OPTIONS,
  YES_NO_SETTLE_OPTIONS,
  uploadStatusTagType,
} from './homepage.constants';
import {
  cloneHomepageForm,
  homepageToFormModel,
  mapListItemToRow,
  mergeCaseRecordIntoFormModel,
  sectionToRefreshCode,
  toSaveHomepageParams,
} from './homepage-form.util';

defineOptions({ name: 'MrmsCaseRecordHomepage' });

/**
 * 主类型：T3 列表 + T4 首页表单
 */

type ViewMode = 'edit' | 'list';

const userStore = useUserStore();
const authStore = useAuthStore();
const viewMode = ref<ViewMode>('list');
const tableData = ref<HomepageListRow[]>([]);
const total = ref(0);
const loading = ref(false);

/** 当前登录医务人员主键（临床弹层编辑门禁） */
const loginPractitionerId = computed(() =>
  resolveLoginPractitionerIdForRequest(userStore.userInfo as Record<string, unknown> | null),
);

/** 登录所选/所属科室（同科室门禁） */
const loginOrganizationId = computed(() =>
  resolveLoginOrganizationIdForRequest(
    userStore.userInfo as Record<string, unknown> | null,
    authStore.getSelectedOrganization(),
  ),
);

/** 科室下拉：全部 + 住院登记 organizationOptions（value=科室名，对齐 admCatyName） */
const deptOptions = ref<HomepageSelectOption[]>([DEPT_ALL_OPTION]);

const formModel = ref<HomepageFormModel | null>(null);
const editRow = ref<HomepageListRow | null>(null);
const formDirty = ref(false);
const saving = ref(false);
const editStatusCode = ref('draft');
const returnReason = ref('');

const queryParams = reactive<HomepageListQuery>({
  pageNo: 1,
  pageSize: 20,
  keyword: undefined,
  admCatyName: ALL_OPTION_VALUE,
  /** 默认不限入院/出院日，避免在院患者被时间条件滤空 */
  admTimeRange: undefined,
  dscgTimeRange: undefined,
  homepageUploadStatus: ALL_OPTION_VALUE,
  settleUploadStatus: ALL_OPTION_VALUE,
  dischargedCode: ALL_OPTION_VALUE,
  settledCode: ALL_OPTION_VALUE,
});

const formItems = computed<FormItemConfig[]>(() => [
  {
    label: '条件',
    prop: 'keyword',
    type: 'input',
    placeholder: '住院号 / 姓名 / 身份证 / 疾病',
    width: '260px',
    clearable: true,
  },
  {
    label: '科室',
    prop: 'admCatyName',
    type: 'select',
    options: deptOptions.value,
    clearable: true,
    multiple: false,
    filterable: true,
    width: '160px',
  },
  {
    label: '入院时间',
    prop: 'admTimeRange',
    type: 'daterange',
    startPlaceholder: '开始',
    endPlaceholder: '结束',
    valueFormat: 'YYYY-MM-DD',
    clearable: true,
    width: '280px',
  },
  {
    label: '出院时间',
    prop: 'dscgTimeRange',
    type: 'daterange',
    startPlaceholder: '开始',
    endPlaceholder: '结束',
    valueFormat: 'YYYY-MM-DD',
    clearable: true,
    width: '280px',
  },
  {
    label: '首页上传',
    prop: 'homepageUploadStatus',
    type: 'select',
    options: UPLOAD_STATUS_OPTIONS,
    clearable: true,
    multiple: false,
    filterable: false,
    width: '120px',
  },
  {
    label: '结算上传',
    prop: 'settleUploadStatus',
    type: 'select',
    options: UPLOAD_STATUS_OPTIONS,
    clearable: true,
    multiple: false,
    filterable: false,
    width: '120px',
  },
  {
    label: '是否出院',
    prop: 'dischargedCode',
    type: 'select',
    options: YES_NO_DISCHARGE_OPTIONS,
    clearable: true,
    multiple: false,
    filterable: false,
    width: '110px',
  },
  {
    label: '是否结算',
    prop: 'settledCode',
    type: 'select',
    options: YES_NO_SETTLE_OPTIONS,
    clearable: true,
    multiple: false,
    filterable: false,
    width: '110px',
  },
]);

const tableColumns: TableColumn[] = [
  {
    prop: 'patientName',
    label: '患者',
    width: 120,
    fixed: 'left',
    slot: 'patient',
    align: 'left',
    headerAlign: 'center',
  },
  {
    prop: 'medcasno',
    label: '病案号',
    width: 120,
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: true,
  },
  {
    prop: 'admDeptName',
    label: '科室',
    width: 88,
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'insuranceTypeText',
    label: '医保',
    width: 88,
    align: 'center',
    headerAlign: 'center',
    formatter: (row: HomepageListRow) => row.insuranceTypeText || '—',
  },
  {
    prop: 'admTime',
    label: '入院',
    width: 156,
    slot: 'admTime',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'dscgTime',
    label: '出院',
    width: 156,
    slot: 'dscgTime',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'dischargeDiagnosisText',
    label: '出院诊断',
    minWidth: 200,
    slot: 'diagnosis',
    align: 'left',
    headerAlign: 'center',
  },
  {
    prop: 'dischargedCode',
    label: '出院',
    width: 84,
    slot: 'discharged',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'settledCode',
    label: '结算',
    width: 84,
    slot: 'settled',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'homepageUploadStatusCode',
    label: '首页上传',
    width: 96,
    slot: 'homepageUpload',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'settleUploadStatusCode',
    label: '结算上传',
    width: 96,
    slot: 'settleUpload',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'orderUploadStatusCode',
    label: '医嘱上传',
    width: 96,
    slot: 'orderUpload',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'certno',
    label: '证件号',
    width: 168,
    align: 'center',
    headerAlign: 'center',
  },
  {
    label: '医保业务',
    width: 148,
    slot: 'insUpload',
    align: 'center',
    headerAlign: 'center',
  },
  {
    label: '操作',
    width: 148,
    fixed: 'right',
    slot: 'rowActions',
    align: 'center',
    headerAlign: 'center',
  },
];

const clinicalHostRef = ref<InstanceType<typeof MrmsInpatientClinicalHost> | null>(null);

/** 草稿/退回可编辑保存；4401 成功自动归档后只读 */
const canEditForm = computed(
  () => editStatusCode.value === 'draft' || editStatusCode.value === 'returned',
);

const { run: runPage, loading: pageLoading } = useRequest(queryCaseRecordPageApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData(data);
    tableData.value = parsed.content.map(mapListItemToRow);
    total.value = parsed.totalCount;
  },
});

watch(pageLoading, (v) => {
  loading.value = v ?? false;
});

const { run: runSave, loading: saveLoading } = useRequest(saveHomepageApi, {
  manual: true,
  onSuccess: (data) => {
    if (data && formModel.value) {
      const next = homepageToFormModel(data, formModel.value.encounterId);
      next.medcasno = formModel.value.medcasno || next.medcasno;
      next.visitTimes = formModel.value.visitTimes ?? next.visitTimes;
      next.encounterId = formModel.value.encounterId || next.encounterId;
      formModel.value = next;
      if (editRow.value && next.caseRecordId) {
        editRow.value.caseRecordId = next.caseRecordId;
        editRow.value.id = next.caseRecordId;
        editRow.value.medcasno = next.medcasno || editRow.value.medcasno;
      }
    }
    formDirty.value = false;
    ElMessage.success('首页已保存');
    getList();
  },
});

watch(saveLoading, (v) => {
  saving.value = v ?? false;
});

/** 时间范围截成 yyyy-MM-dd */
function toDateOnly(v?: string): string | undefined {
  if (!v) {
    return undefined;
  }
  return v.slice(0, 10);
}

/** 组装列表查询入参 */
function buildQueryPayload() {
  const admRange = queryParams.admTimeRange ?? [];
  const dscgRange = queryParams.dscgTimeRange ?? [];
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword || undefined,
    admCatyName: queryParams.admCatyName || undefined,
    admFrom: toDateOnly(admRange[0]),
    admTo: toDateOnly(admRange[1]),
    dscgFrom: toDateOnly(dscgRange[0]),
    dscgTo: toDateOnly(dscgRange[1]),
    homepageUploadStatus: queryParams.homepageUploadStatus || undefined,
    settleUploadStatus: queryParams.settleUploadStatus || undefined,
    dischargedCode: queryParams.dischargedCode || undefined,
    settledCode: queryParams.settledCode || undefined,
  };
}

/** 拉取列表 */
async function getList() {
  await runPage(buildQueryPayload());
}

/** 重置筛选 */
function handleReset() {
  queryParams.pageNo = 1;
  queryParams.keyword = undefined;
  queryParams.admCatyName = ALL_OPTION_VALUE;
  queryParams.admTimeRange = undefined;
  queryParams.dscgTimeRange = undefined;
  queryParams.homepageUploadStatus = ALL_OPTION_VALUE;
  queryParams.settleUploadStatus = ALL_OPTION_VALUE;
  queryParams.dischargedCode = ALL_OPTION_VALUE;
  queryParams.settledCode = ALL_OPTION_VALUE;
  getList();
}

/** 分页变更 */
function handlePagination(pagination: { page: number; limit: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

/** 格式化时间列 */
function fmtTime(v?: string): string {
  if (!v) {
    return '—';
  }
  return formatDateTime(v);
}

/** 上传状态展示文案 */
function uploadLabel(code?: string): string {
  if (!code) {
    return UPLOAD_STATUS_LABEL.none;
  }
  return UPLOAD_STATUS_LABEL[code] ?? code;
}

/** 诊断首行预览 */
function diagnosisPreview(text?: string): { first: string; extra: number } {
  const lines = (text ?? '')
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean);
  if (!lines.length) {
    return { first: '—', extra: 0 };
  }
  return { first: lines[0]!, extra: Math.max(0, lines.length - 1) };
}

/** 解析行操作文案 */
function rowActionLabel(code: HomepageRowActionCode): string {
  return HOMEPAGE_ROW_ACTIONS.find((a) => a.code === code)?.label ?? code;
}

/** 打开病案首页：未建档按 encounterId 打开（服务端 bootstrap + 入院信息带出） */
async function openHomepageForm(row: HomepageListRow) {
  if (!row.encounterId && !row.caseRecordId) {
    ElMessage.warning('缺少就诊主键，无法打开首页');
    return;
  }
  editRow.value = row;
  formDirty.value = false;
  saving.value = true;
  try {
    const page = await loadHomepagePageApi({
      caseRecordId: row.caseRecordId,
      encounterId: row.encounterId,
    });
    const homepage = page.homepage;
    if (!homepage) {
      ElMessage.warning('首页数据为空');
      return;
    }
    let form = homepageToFormModel(homepage, row.encounterId);
    form = mergeCaseRecordIntoFormModel(form, page.caseRecord);
    form.medcasno = form.medcasno || row.medcasno || page.caseRecord?.medcasno;
    form.encounterId = row.encounterId || form.encounterId;
    form.patientId = row.patientId || form.patientId;
    formModel.value = cloneHomepageForm(form);
    editStatusCode.value = page.caseRecord?.statusCode || 'draft';
    returnReason.value = page.caseRecord?.returnReason || '';
    if (page.caseRecord?.caseRecordId) {
      row.caseRecordId = String(page.caseRecord.caseRecordId);
      row.id = row.caseRecordId;
      row.medcasno = page.caseRecord.medcasno || row.medcasno;
    }
    viewMode.value = 'edit';
  } finally {
    saving.value = false;
  }
}

/** 若有未保存改动，确认后再离开编辑 */
async function confirmLeaveEdit(): Promise<boolean> {
  if (!formDirty.value) {
    return true;
  }
  try {
    await ElMessageBox.confirm('当前首页有未保存修改，确认离开？', '提示', {
      type: 'warning',
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
    });
    return true;
  } catch {
    return false;
  }
}

/** 返回列表 */
async function handleBackToList() {
  const ok = await confirmLeaveEdit();
  if (!ok) {
    return;
  }
  viewMode.value = 'list';
  formModel.value = null;
  editRow.value = null;
  formDirty.value = false;
  getList();
}

/** 行操作分发：病案详情进表单；病历/医嘱/体温/护理/费用清单开宿主弹层 */
function handleRowAction(payload: { code: HomepageRowActionCode; row: HomepageListRow }) {
  if (payload.code === 'homepage') {
    openHomepageForm(payload.row);
    return;
  }
  const clinicalKind = toClinicalKind(payload.code);
  if (clinicalKind) {
    // 同入院科室可写；否则静默只读（页面无保存入口，不再弹 tip）
    const denial = resolveInpatientClinicalEditDenial(
      loginPractitionerId.value,
      loginOrganizationId.value,
      payload.row,
    );
    const editable = denial === null;
    clinicalHostRef.value?.open(clinicalKind, payload.row, { forceReadOnly: !editable });
    return;
  }
  ElMessage({
    type: 'info',
    message: `${rowActionLabel(payload.code)} · 待对接已有页面`,
    duration: 1600,
  });
}

/** 行操作 code → 临床宿主 kind */
function toClinicalKind(code: HomepageRowActionCode): MrmsInpatientClinicalKind | null {
  if (code === 'emr') {
    return 'emr';
  }
  if (code === 'order') {
    return 'order';
  }
  if (code === 'temperature') {
    return 'temperature';
  }
  if (code === 'nursing') {
    return 'nursing';
  }
  if (code === 'bill') {
    return 'bill';
  }
  return null;
}

/**
 * 格式化单步上报结果文案。
 *
 * @param label 步骤名
 * @param step 结果
 */
function formatUploadStep(label: string, step?: UploadResultDto): string {
  if (!step) {
    return `${label}：未执行`;
  }
  if (step.skipped) {
    return `${label}：已跳过（此前已成功）`;
  }
  if (step.statusCode === 'success') {
    const extra = step.setlListId ? ` setl_list_id=${step.setlListId}` : '';
    return `${label}：成功${extra}`;
  }
  return `${label}：失败 ${step.errMsg || step.infcode || step.statusCode || ''}`.trim();
}

/**
 * 展示一键上报分步结果。
 *
 * @param result 分步结果
 */
function showOneClickResult(result: OneClickReportResultDto) {
  const lines = [
    formatUploadStep('4401 首页', result.homepage4401),
    formatUploadStep('4101A 结算', result.settle4101A),
    formatUploadStep('4102 提交', result.commit4102),
  ];
  if (result.allSuccess) {
    ElMessage.success(lines.join('；'));
    return;
  }
  ElMessage.error(lines.join('；'));
}

/** 医保业务：一键上报 / 分传 / 提交撤回 */
async function handleInsAction(payload: { code: HomepageInsActionCode; row: HomepageListRow }) {
  const caseRecordId = payload.row.caseRecordId;
  if (!caseRecordId) {
    ElMessage.warning('请先打开病案详情完成建档后再上报');
    return;
  }
  if (payload.row.settledCode !== '1') {
    ElMessage.warning('病人确认结算后才能上报');
    return;
  }
  try {
    if (payload.code === 'one-click-report') {
      const result = await reportOneClickApi({ caseRecordId });
      showOneClickResult(result);
      await runPage(buildQueryPayload());
      return;
    }
    if (payload.code === 'upload-homepage') {
      const step = await upload4401Api({ caseRecordId });
      ElMessage[step.statusCode === 'success' ? 'success' : 'error'](
        formatUploadStep('4401', step),
      );
      await runPage(buildQueryPayload());
      return;
    }
    if (payload.code === 'upload-settle') {
      const step = await uploadSettleListApi({ caseRecordId });
      ElMessage[step.statusCode === 'success' ? 'success' : 'error'](
        formatUploadStep('4101A', step),
      );
      await runPage(buildQueryPayload());
      return;
    }
    if (payload.code === 'settle-submit') {
      const step = await modifySettleListStatusApi({ caseRecordId, stasType: '1' });
      ElMessage[step.statusCode === 'success' ? 'success' : 'error'](
        formatUploadStep('提交审核', step),
      );
      await runPage(buildQueryPayload());
      return;
    }
    if (payload.code === 'settle-pending') {
      const step = await modifySettleListStatusApi({ caseRecordId, stasType: '0' });
      ElMessage[step.statusCode === 'success' ? 'success' : 'error'](
        formatUploadStep('撤回审核', step),
      );
      await runPage(buildQueryPayload());
      return;
    }
    if (payload.code === 'query-settle') {
      const row = await querySettleList4103Api({ caseRecordId });
      if (row.success) {
        const name = row.psnName || '—';
        const fee = row.medfeeSumamt == null ? '—' : Number(row.medfeeSumamt).toFixed(2);
        ElMessage.success(`4103 成功：${name} / setl_id=${row.setlId || '—'} / 总额 ${fee}`);
      } else {
        ElMessage.error(row.errMsg || row.platformMsg || '4103 查询失败');
      }
      return;
    }
    ElMessage.info('未知医保操作');
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '上报失败';
    ElMessage.error(msg);
  }
}

/** 标记表单脏 */
function handleFormDirty() {
  formDirty.value = true;
}

/** 保存首页 */
async function handleSave() {
  if (!formModel.value) {
    return;
  }
  await runSave(toSaveHomepageParams(formModel.value));
}

/** 全部导入（强制覆盖 auto 字段，便于补齐业务表新带出项） */
async function handleImportAll() {
  if (!formModel.value?.caseRecordId) {
    return;
  }
  saving.value = true;
  try {
    const data = await refreshAutoFillApi({
      caseRecordId: formModel.value.caseRecordId!,
      sections: ['all'],
      forceOverwrite: true,
    });
    const next = homepageToFormModel(data, formModel.value.encounterId);
    next.medcasno = formModel.value.medcasno || next.medcasno;
    next.visitTimes = formModel.value.visitTimes ?? next.visitTimes;
    formModel.value = next;
    formDirty.value = true;
    ElMessage.success('已导入全部章节');
  } finally {
    saving.value = false;
  }
}

/** 导入单章 */
async function handleImportSection(section: string) {
  if (!formModel.value?.caseRecordId) {
    return;
  }
  saving.value = true;
  try {
    const data = await refreshAutoFillApi({
      caseRecordId: formModel.value.caseRecordId!,
      sections: [sectionToRefreshCode(section)],
      forceOverwrite: false,
    });
    const next = homepageToFormModel(data, formModel.value.encounterId);
    next.medcasno = formModel.value.medcasno || next.medcasno;
    next.visitTimes = formModel.value.visitTimes ?? next.visitTimes;
    formModel.value = next;
    formDirty.value = true;
    ElMessage.success(`已导入章节：${section}`);
  } finally {
    saving.value = false;
  }
}

/** 提交首页 */
async function handleSubmit() {
  if (!formModel.value) {
    return;
  }
  if (formDirty.value) {
    await handleSave();
  }
  try {
    await ElMessageBox.confirm('确认提交病案首页？提交后医生侧不可再编辑。', '提交确认', {
      type: 'warning',
    });
  } catch {
    return;
  }
  saving.value = true;
  try {
    const detail = await submitHomepageApi(formModel.value.caseRecordId!);
    editStatusCode.value = detail.statusCode || 'submitted';
    formDirty.value = false;
    ElMessage.success('已提交');
  } finally {
    saving.value = false;
  }
}

/** 拉取住院科室下拉（登记上下文；失败不阻塞列表） */
async function loadDeptOptions() {
  try {
    const context = await httpLoadInpatientRegistrationContext({});
    // 筛选用科室名精确匹配后端 admCatyName / organizationName
    const fromApi: HomepageSelectOption[] = (context.organizationOptions ?? []).map((item) => ({
      label: item.label,
      value: item.label,
    }));
    deptOptions.value = [DEPT_ALL_OPTION, ...fromApi];
  } catch {
    deptOptions.value = [DEPT_ALL_OPTION];
  }
}

onMounted(() => {
  void loadDeptOptions();
  getList();
});
</script>

<template>
  <Page auto-content-height content-class="mrms-homepage-page">
    <HomepageEditPanel
      v-if="viewMode === 'edit' && formModel"
      :form-model="formModel"
      :status-code="editStatusCode"
      :medcasno="editRow?.medcasno"
      :return-reason="returnReason"
      :readonly="!canEditForm"
      :can-save="canEditForm"
      :can-import="canEditForm"
      :can-submit="canEditForm"
      :saving="saving"
      @back="handleBackToList"
      @dirty="handleFormDirty"
      @save="handleSave"
      @submit="handleSubmit"
      @import-all="handleImportAll"
      @import-section="handleImportSection"
    />

    <TableLayout
      v-else
      class="mrms-homepage-table"
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :form-items="formItems"
      :table-columns="tableColumns"
      row-key="id"
      :show-top-query="true"
      :show-pagination="true"
      border
      stripe
      size="small"
      @query="getList"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #patient="{ row }">
        <div class="hp-patient">
          <span class="hp-patient__name">{{ row.patientName }}</span>
          <span class="hp-patient__ipt">{{ row.iptNo }}</span>
        </div>
      </template>

      <template #admTime="{ row }">
        <span class="hp-time">{{ fmtTime(row.admTime) }}</span>
      </template>

      <template #dscgTime="{ row }">
        <span class="hp-time">{{ fmtTime(row.dscgTime) }}</span>
      </template>

      <template #diagnosis="{ row }">
        <ElTooltip
          :content="row.dischargeDiagnosisText || '—'"
          placement="top"
          :disabled="!row.dischargeDiagnosisText"
        >
          <div class="hp-diagnosis">
            <span class="hp-diagnosis__first">
              {{ diagnosisPreview(row.dischargeDiagnosisText).first }}
            </span>
            <span
              v-if="diagnosisPreview(row.dischargeDiagnosisText).extra > 0"
              class="hp-diagnosis__more"
            >
              +{{ diagnosisPreview(row.dischargeDiagnosisText).extra }}
            </span>
          </div>
        </ElTooltip>
      </template>

      <template #discharged="{ row }">
        <ElTag size="small" effect="light" :type="row.dischargedCode === '1' ? 'success' : 'info'">
          {{ row.dischargedCode === '1' ? '已出院' : '未出院' }}
        </ElTag>
      </template>

      <template #settled="{ row }">
        <ElTag size="small" effect="light" :type="row.settledCode === '1' ? 'success' : 'warning'">
          {{ row.settledCode === '1' ? '已结算' : '未结算' }}
        </ElTag>
      </template>

      <template #homepageUpload="{ row }">
        <ElTag
          size="small"
          effect="light"
          :type="uploadStatusTagType(row.homepageUploadStatusCode)"
        >
          {{ uploadLabel(row.homepageUploadStatusCode) }}
        </ElTag>
      </template>

      <template #settleUpload="{ row }">
        <ElTag size="small" effect="light" :type="uploadStatusTagType(row.settleUploadStatusCode)">
          {{ uploadLabel(row.settleUploadStatusCode) }}
        </ElTag>
      </template>

      <template #orderUpload="{ row }">
        <ElTag size="small" effect="light" :type="uploadStatusTagType(row.orderUploadStatusCode)">
          {{ uploadLabel(row.orderUploadStatusCode) }}
        </ElTag>
      </template>

      <template #insUpload="{ row }">
        <HomepageInsUploadActions :row="row" @action="handleInsAction" />
      </template>

      <template #rowActions="{ row }">
        <HomepageRowActions :row="row" @action="handleRowAction" />
      </template>
    </TableLayout>

    <MrmsInpatientClinicalHost ref="clinicalHostRef" />
  </Page>
</template>

<style scoped>
.mrms-homepage-page {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.hp-patient {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
}

.hp-patient__name {
  color: var(--el-color-primary);
  font-weight: 600;
}

.hp-patient__ipt {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.hp-time {
  color: var(--el-text-color-regular);
  font-variant-numeric: tabular-nums;
}

.hp-diagnosis {
  display: flex;
  gap: 6px;
  align-items: baseline;
  min-width: 0;
}

.hp-diagnosis__first {
  overflow: hidden;
  color: var(--el-text-color-regular);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hp-diagnosis__more {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
