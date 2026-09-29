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

import { computed, onActivated, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useTabs } from '@vben/hooks';
import { useUserStore } from '@vben/stores';
import { formatDateTime } from '@vben/utils';

import { TableLayout } from '@vben-core/table-layout-ui';

import { ElButton, ElLoading, ElMessage, ElMessageBox, ElTag, ElTooltip } from 'element-plus';

import { httpLoadInpatientRegistrationContext } from '#/api/inpatient-registration';
import {
  exportHqmsCsvApi,
  exportWeistatN041CsvApi,
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
  type SettleListQuery4103Dto,
  type UploadResultDto,
} from '#/chs/api/medcas-ops';
import { useRequest } from '#/hooks/use-request';
import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import {
  preserveApiIdForRequest,
  resolveLoginOrganizationIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import { PRINT_SCENARIO_LABELS } from '#/utils/print/constants/print-scenario-labels';
import { resolveInpatientHomepagePrintScenario } from '#/utils/print/resolve-inpatient-homepage-scenario';
import {
  resolvePrintHospitalName,
  resolvePrintOrgCode,
} from '#/utils/print/resolve-print-hospital-name';
import { print } from '#/utils/print/runtime';

import MrmsInpatientClinicalHost from '../components/mrms-inpatient-clinical-host.vue';
import MrmsSettleQuery4103Dialog from '../components/mrms-settle-query-4103-dialog.vue';
import { buildMrmsPatientTabTitle } from '../mrms.constants';
import {
  resolveInpatientClinicalEditDenial,
  resolveInpatientNurseSheetEditDenial,
} from '../mrms-clinical-edit-gate';
import { loadManagedWardLocationIds } from '../mrms-managed-ward-ids';
import HomepageEditPanel from './components/homepage-edit-panel.vue';
import HomepageInsUploadActions from './components/homepage-ins-upload-actions.vue';
import HomepageRowActions from './components/homepage-row-actions.vue';
import { MRMS_HOMEPAGE_4401_UPLOAD_ENABLED } from '#/views/mrms/homepage/homepage-4401-upload.config';
import {
  MRMS_HOMEPAGE_HQMS_EXPORT_ENABLED,
  MRMS_HOMEPAGE_WEISTAT_EXPORT_ENABLED,
} from '#/views/mrms/homepage/homepage-weistat-export.config';
import {
  ALL_OPTION_VALUE,
  DEPT_ALL_OPTION,
  HOMEPAGE_ROW_ACTIONS,
  SETTLE_UPLOAD_STATUS_OPTIONS,
  UPLOAD_STATUS_LABEL,
  UPLOAD_STATUS_OPTIONS,
  YES_NO_DISCHARGE_OPTIONS,
  YES_NO_SETTLE_OPTIONS,
  settleUploadStatusLabel,
  settleUploadStatusTagType,
  uploadStatusTagType,
} from './homepage.constants';
import {
  cloneHomepageForm,
  homepageToFormModel,
  mapListItemToRow,
  mergeCaseRecordIntoFormModel,
  sectionsToRefreshCodes,
  toSaveHomepageParams,
} from './homepage-form.util';

defineOptions({ name: 'MrmsCaseRecordHomepage' });

/**
 * 主类型：T3 列表（无路由主键）+ T4 详情（/mrms/homepage/:caseRecordId 多开）
 */

type ViewMode = 'edit' | 'list';

const route = useRoute();
const router = useRouter();
const { closeCurrentTab, setTabTitle } = useTabs();
const userStore = useUserStore();
const authStore = useAuthStore();
const viewMode = ref<ViewMode>('list');
const tableData = ref<HomepageListRow[]>([]);
const total = ref(0);
const loading = ref(false);
/** 医保「一键上传」等行内操作中的行 id */
const insActingRowId = ref('');
/** 列表多选（卫统/HQMS 导出） */
const selectedRows = ref<HomepageListRow[]>([]);
/** 卫统 CSV 导出中 */
const weistatExporting = ref(false);
/** HQMS CSV 导出中 */
const hqmsExporting = ref(false);

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
/** 详情页签 loadPage 中（与保存 saving 分离，避免空白闪屏） */
const detailLoading = ref(false);
/**
 * 详情加载世代号：路由切换时递增，过期响应丢弃结果。
 * 同主键 inflight 复用 Promise，避免 onMounted+watch / 双发并发 bootstrap。
 */
let detailLoadSeq = 0;
let detailInflightKey: string | null = null;
let detailInflightPromise: Promise<void> | null = null;
const printing = ref(false);
const editStatusCode = ref('DRAFT');
const returnReason = ref('');
/** 中医信息段：医院 medins_type（loadPage 回填） */
const tcmInfoEnabled = ref(false);

/** 4103 查询结果弹窗 */
const query4103Visible = ref(false);
const query4103Result = ref<SettleListQuery4103Dto | null>(null);

/** 路由病案主键（详情多开） */
const routeCaseRecordId = computed(() => {
  const fromParam = route.params.caseRecordId;
  if (typeof fromParam === 'string' && fromParam.trim()) {
    return fromParam.trim();
  }
  const fromQuery = route.query.caseRecordId;
  if (typeof fromQuery === 'string' && fromQuery.trim()) {
    return fromQuery.trim();
  }
  return '';
});

/** 路由就诊主键（未建档打开详情） */
const routeEncounterId = computed(() => {
  const raw = route.query.encounterId;
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim();
  }
  return '';
});

/** 当前实例是否为病案详情页签（仅认详情路由名，勿用 query 误判列表） */
const isDetailRoute = computed(() => route.name === 'MrmsCaseRecordHomepageDetail');

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
  ...(MRMS_HOMEPAGE_4401_UPLOAD_ENABLED
    ? [
        {
          label: '首页上传',
          prop: 'homepageUploadStatus',
          type: 'select' as const,
          options: UPLOAD_STATUS_OPTIONS,
          clearable: true,
          multiple: false,
          filterable: false,
          width: '120px',
        },
      ]
    : []),
  {
    label: '结算上传',
    prop: 'settleUploadStatus',
    type: 'select',
    options: SETTLE_UPLOAD_STATUS_OPTIONS,
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

const tableColumns = computed<TableColumn[]>(() => {
  const cols: TableColumn[] = [];
  // 卫统/HQMS 导出开启时增加多选列（仅已建档可选）
  if (MRMS_HOMEPAGE_WEISTAT_EXPORT_ENABLED || MRMS_HOMEPAGE_HQMS_EXPORT_ENABLED) {
    cols.push({
      type: 'selection',
      width: 48,
      fixed: 'left',
      align: 'center',
      headerAlign: 'center',
      selectable: (row: HomepageListRow) => Boolean(row.caseRecordId),
    });
  }
  cols.push(
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
      width: 130,
      minWidth: 130,
      align: 'center',
      headerAlign: 'center',
      showOverflowTooltip: false,
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
  );
  // 未开通 4401 时隐藏「首页上传」列与筛选项
  if (MRMS_HOMEPAGE_4401_UPLOAD_ENABLED) {
    cols.push({
      prop: 'homepageUploadStatusCode',
      label: '首页上传',
      width: 96,
      slot: 'homepageUpload',
      align: 'center',
      headerAlign: 'center',
    });
  }
  cols.push(
    {
      prop: 'settleUploadStatusCode',
      label: '结算上传',
      width: 96,
      slot: 'settleUpload',
      align: 'center',
      headerAlign: 'center',
    },
    // 医嘱上传列暂隐：当前无医嘱上传业务入口，恢复时加回 orderUploadStatusCode 列 + #orderUpload
    {
      prop: 'psnNo',
      label: '证件/医保',
      // 身份证 18 + psn_no ~20，须足够宽；关 tooltip 溢出否则 vxe 单行裁切导致竖排糊字
      width: 320,
      minWidth: 320,
      showOverflowTooltip: false,
      slot: 'idInsKeys',
      align: 'left',
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
      // 病案详情 + 结算清单 + 更多，勿用 148 否则会裁掉「更多」
      width: 248,
      fixed: 'right',
      slot: 'rowActions',
      align: 'center',
      headerAlign: 'center',
    },
  );
  return cols;
});

const clinicalHostRef = ref<InstanceType<typeof MrmsInpatientClinicalHost> | null>(null);

/** 草稿/退回可编辑保存；4401 成功自动归档后只读 */
const canEditForm = computed(() => editStatusCode.value === 'DRAFT');

const { run: runPage, loading: pageLoading } = useRequest(queryCaseRecordPageApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData(data);
    tableData.value = parsed.content.map(mapListItemToRow);
    total.value = parsed.totalCount;
    // 翻页/重查后清空勾选，避免跨页脏选
    selectedRows.value = [];
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
      // 优先用服务端回传病案号（首次保存刚发号）；旧值仅兜底
      next.medcasno = data.medcasno || formModel.value.medcasno || next.medcasno;
      next.visitTimes = formModel.value.visitTimes ?? next.visitTimes;
      next.encounterId = formModel.value.encounterId || next.encounterId;
      next.organizationId = formModel.value.organizationId || next.organizationId;
      next.patientId = formModel.value.patientId || next.patientId;
      formModel.value = next;
      if (editRow.value && next.caseRecordId) {
        editRow.value.caseRecordId = next.caseRecordId;
        editRow.value.id = next.caseRecordId;
        editRow.value.medcasno = next.medcasno || editRow.value.medcasno;
      }
    }
    formDirty.value = false;
    ElMessage.success('首页已保存');
    syncDetailTabTitle();
    if (!isDetailRoute.value) {
      getList();
    }
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

/**
 * 同步表格多选行（卫统/HQMS 导出）。
 *
 * @param rows 当前勾选行
 */
function handleSelectionChange(rows: HomepageListRow[]) {
  selectedRows.value = rows ?? [];
}

/**
 * 多选导出卫统 N041 CSV（仅已建档病案）。
 */
async function handleExportWeistatN041() {
  const ids = selectedRows.value
    .map((row) => row.caseRecordId)
    .filter((id): id is string => Boolean(id && String(id).trim()));
  if (ids.length === 0) {
    ElMessage.warning('请先勾选已建档的病案');
    return;
  }
  weistatExporting.value = true;
  try {
    const result = await exportWeistatN041CsvApi({ caseRecordIds: ids });
    downloadBinaryExportFile(result, 'N041_export.csv');
    ElMessage.success(`已导出 ${result.exportedRowCount ?? ids.length} 条卫统 CSV`);
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '卫统导出失败';
    ElMessage.error(msg);
  } finally {
    weistatExporting.value = false;
  }
}

/**
 * 多选导出 HQMS CSV（仅已建档病案）。
 */
async function handleExportHqms() {
  const ids = selectedRows.value
    .map((row) => row.caseRecordId)
    .filter((id): id is string => Boolean(id && String(id).trim()));
  if (ids.length === 0) {
    ElMessage.warning('请先勾选已建档的病案');
    return;
  }
  hqmsExporting.value = true;
  try {
    const result = await exportHqmsCsvApi({ caseRecordIds: ids });
    downloadBinaryExportFile(result, 'hqmsts_export.csv');
    ElMessage.success(`已导出 ${result.exportedRowCount ?? ids.length} 条 HQMS CSV`);
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : 'HQMS 导出失败';
    ElMessage.error(msg);
  } finally {
    hqmsExporting.value = false;
  }
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

/**
 * 按表单患者刷新详情页签标题。
 */
function syncDetailTabTitle() {
  const form = formModel.value;
  const row = editRow.value;
  void setTabTitle(
    buildMrmsPatientTabTitle(
      '病案详情',
      form?.psnName || row?.patientName,
      form?.iptNo || row?.iptNo,
    ),
  );
}

/**
 * 打开病案详情：独立页签多开（带患者名/住院号标题）。
 *
 * @param row 列表行
 */
function openHomepageForm(row: HomepageListRow) {
  const caseRecordId = (row.caseRecordId ?? '').trim();
  const encounterId = (row.encounterId ?? '').trim();
  if (!caseRecordId && !encounterId) {
    ElMessage.warning('缺少就诊主键，无法打开首页');
    return;
  }
  router.push({
    name: 'MrmsCaseRecordHomepageDetail',
    params: caseRecordId ? { caseRecordId } : {},
    query: caseRecordId ? undefined : { encounterId },
  });
}

/**
 * 按路由主键加载病案详情（详情页签挂载/路由变更时）。
 * 同 caseRecordId|encounterId 并发调用合并为一次 loadPage。
 */
async function loadDetailFromRoute() {
  const caseRecordId = routeCaseRecordId.value;
  const encounterId = routeEncounterId.value;
  if (!caseRecordId && !encounterId) {
    detailLoading.value = false;
    return;
  }
  // 同主键已在飞：等待既有请求，禁止再打 loadPage（防并发建档）
  const loadKey = `${caseRecordId || ''}|${encounterId || ''}`;
  if (detailInflightPromise && detailInflightKey === loadKey) {
    await detailInflightPromise;
    return;
  }
  const seq = ++detailLoadSeq;
  formDirty.value = false;
  detailLoading.value = true;
  detailInflightKey = loadKey;
  detailInflightPromise = (async () => {
    try {
      const page = await loadHomepagePageApi({
        caseRecordId: caseRecordId || undefined,
        encounterId: encounterId || undefined,
      });
      // 过期：用户已切到别的详情，丢弃本响应
      if (seq !== detailLoadSeq) {
        return;
      }
      const homepage = page.homepage;
      // 无首页体：结束 loading，留空壳提示（勿闪回列表）
      if (!homepage) {
        ElMessage.warning('首页数据为空');
        formModel.value = null;
        return;
      }
      let form = homepageToFormModel(homepage, encounterId || undefined);
      form = mergeCaseRecordIntoFormModel(form, page.caseRecord);
      form.medcasno = form.medcasno || page.caseRecord?.medcasno;
      form.encounterId = encounterId || form.encounterId;
      formModel.value = cloneHomepageForm(form);
      editStatusCode.value = page.caseRecord?.statusCode || 'DRAFT';
      returnReason.value = page.caseRecord?.returnReason || '';
      // 中医信息：优先页级开关，回落 homepage 同名字段
      tcmInfoEnabled.value = Boolean(page.tcmInfoEnabled ?? page.homepage?.tcmInfoEnabled);
      const resolvedCaseId = page.caseRecord?.caseRecordId
        ? String(page.caseRecord.caseRecordId)
        : caseRecordId;
      editRow.value = {
        id: resolvedCaseId || encounterId,
        caseRecordId: resolvedCaseId || undefined,
        encounterId: form.encounterId || encounterId || '',
        patientName: form.psnName || '',
        iptNo: form.iptNo || '',
        medcasno: form.medcasno || '',
        patientId: form.patientId,
        organizationId: form.organizationId,
        dischargedCode: '0',
        settledCode: '0',
        homepageUploadStatusCode: 'none',
        settleUploadStatusCode: 'none',
        orderUploadStatusCode: 'none',
      };
      viewMode.value = 'edit';
      syncDetailTabTitle();
    } catch (error: unknown) {
      if (seq !== detailLoadSeq) {
        return;
      }
      const msg = error instanceof Error ? error.message : '加载病案首页失败';
      ElMessage.error(msg);
      formModel.value = null;
    } finally {
      // 仅最新世代清 inflight / loading，避免旧请求 finally 误关新请求
      if (seq === detailLoadSeq) {
        detailLoading.value = false;
        detailInflightKey = null;
        detailInflightPromise = null;
      }
    }
  })();
  await detailInflightPromise;
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

/** 关闭当前病案详情页签（列表页签仍保留，可多开） */
async function handleBackToList() {
  const ok = await confirmLeaveEdit();
  if (!ok) {
    return;
  }
  await closeCurrentTab();
}

/** 行操作分发：病案详情进表单；结算清单独立预览；临床宿主弹层 */
async function handleRowAction(payload: { code: HomepageRowActionCode; row: HomepageListRow }) {
  if (payload.code === 'homepage') {
    openHomepageForm(payload.row);
    return;
  }
  if (payload.code === 'settle-list') {
    openSettleListPreview(payload.row);
    return;
  }
  const clinicalKind = toClinicalKind(payload.code);
  if (clinicalKind) {
    const editable = await resolveClinicalEditable(clinicalKind, payload.row);
    clinicalHostRef.value?.open(clinicalKind, payload.row, { forceReadOnly: !editable });
    return;
  }
  ElMessage({
    type: 'info',
    message: `${rowActionLabel(payload.code)} · 待对接已有页面`,
    duration: 1600,
  });
}

/**
 * 临床弹层是否可写：病历/医嘱按入院科室；体温单/护理记录另放行管辖病区护士。
 *
 * @param kind 临床模块
 * @param row 列表行
 */
async function resolveClinicalEditable(
  kind: MrmsInpatientClinicalKind,
  row: HomepageListRow,
): Promise<boolean> {
  if (kind === 'temperature' || kind === 'nursing') {
    const managedWardIds = await loadManagedWardLocationIds();
    const denial = resolveInpatientNurseSheetEditDenial(
      loginPractitionerId.value,
      loginOrganizationId.value,
      managedWardIds,
      row,
    );
    return denial === null;
  }
  // 同入院科室可写；否则静默只读（页面无保存入口，不再弹 tip）
  const denial = resolveInpatientClinicalEditDenial(
    loginPractitionerId.value,
    loginOrganizationId.value,
    row,
  );
  return denial === null;
}

/**
 * 打开结算清单独立只读预览页（不进病案详情编辑）。
 *
 * @param row 列表行
 */
function openSettleListPreview(row: HomepageListRow) {
  const caseRecordId = (row.caseRecordId ?? '').trim();
  const encounterId = (row.encounterId ?? '').trim();
  if (!caseRecordId && !encounterId) {
    ElMessage.warning('缺少病案或就诊主键，无法打开结算清单');
    return;
  }
  router.push({
    name: 'MrmsSettleListPreview',
    params: caseRecordId ? { caseRecordId } : {},
    query: caseRecordId ? undefined : { encounterId },
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
    return `${label}：成功`;
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
  // 进行中禁止连点
  if (insActingRowId.value) {
    return;
  }
  const rowKey = payload.row.id || caseRecordId;
  insActingRowId.value = rowKey;
  const loadingText =
    payload.code === 'settle-upload-commit' || payload.code === 'one-click-report'
      ? '一键上传中…'
      : '上报处理中…';
  const loadingInst = ElLoading.service({
    lock: true,
    text: loadingText,
    background: 'rgba(0, 0, 0, 0.25)',
  });
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
    if (payload.code === 'settle-upload-commit') {
      // 清算上传(4101A) 成功后再提交审核(4102)
      const uploadStep = await uploadSettleListApi({ caseRecordId });
      const uploadOk = uploadStep.statusCode === 'success' || uploadStep.skipped === true;
      if (!uploadOk) {
        ElMessage.error(formatUploadStep('4101A 清算上传', uploadStep));
        await runPage(buildQueryPayload());
        return;
      }
      const commitStep = await modifySettleListStatusApi({
        caseRecordId,
        stasType: '1',
      });
      const commitOk = commitStep.statusCode === 'success' || commitStep.skipped === true;
      const lines = [
        formatUploadStep('4101A 清算上传', uploadStep),
        formatUploadStep('4102 提交审核', commitStep),
      ];
      if (commitOk) {
        ElMessage.success(lines.join('；'));
      } else {
        ElMessage.error(lines.join('；'));
      }
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
        // 直接弹可读摘要，不再只 toast / 甩 JSON
        query4103Result.value = row;
        query4103Visible.value = true;
      } else {
        ElMessage.error(row.errMsg || row.platformMsg || '4103 查询失败');
      }
      return;
    }
    ElMessage.info('未知医保操作');
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : '上报失败';
    ElMessage.error(msg);
  } finally {
    loadingInst.close();
    insActingRowId.value = '';
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
    next.organizationId = formModel.value.organizationId || next.organizationId;
    next.patientId = formModel.value.patientId || next.patientId;
    formModel.value = next;
    formDirty.value = true;
    ElMessage.success('已导入全部章节');
  } finally {
    saving.value = false;
  }
}

/** 导入单章（费用章=重新统计：落库快照覆盖，不标脏） */
async function handleImportSection(section: string) {
  if (!formModel.value?.caseRecordId) {
    return;
  }
  saving.value = true;
  try {
    const data = await refreshAutoFillApi({
      caseRecordId: formModel.value.caseRecordId!,
      // 住院信息章含人口学+入出院，须一次刷 basic+inpatient
      sections: sectionsToRefreshCodes(section),
      forceOverwrite: false,
    });
    const next = homepageToFormModel(data, formModel.value.encounterId);
    next.medcasno = formModel.value.medcasno || next.medcasno;
    next.visitTimes = formModel.value.visitTimes ?? next.visitTimes;
    next.organizationId = formModel.value.organizationId || next.organizationId;
    next.patientId = formModel.value.patientId || next.patientId;
    formModel.value = next;
    // 费用只读快照：重新统计才落库覆盖，不得标脏误导用户去保存费用
    if (section === 'fee') {
      ElMessage.success('已重新统计费用');
    } else {
      formDirty.value = true;
      ElMessage.success(`已导入章节：${section}`);
    }
  } finally {
    saving.value = false;
  }
}

/** 打印或预览住院病案首页（按 medins_type→tcmInfoEnabled 选中医/西医模板） */
async function handlePrint(payload: { preview: boolean }) {
  if (!formModel.value) {
    ElMessage.warning('请先打开病案详情');
    return;
  }
  printing.value = true;
  try {
    const scenarioCode = resolveInpatientHomepagePrintScenario(tcmInfoEnabled.value);
    const variant = tcmInfoEnabled.value ? 'tcm' : 'wm';
    await print({
      scenarioCode,
      scenarioLabel: PRINT_SCENARIO_LABELS[scenarioCode],
      source: {
        form: formModel.value,
        hospitalName: resolvePrintHospitalName(),
        orgCode: resolvePrintOrgCode(),
        variant,
      },
      preview: payload.preview,
      bizRef: {
        caseRecordId: formModel.value.caseRecordId,
        encounterId: formModel.value.encounterId,
      },
    });
  } finally {
    printing.value = false;
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
    editStatusCode.value = detail.statusCode || 'PENDING_ARCHIVE';
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
  // 详情：loading/edit 由下方 watch(immediate) 统一拉数，此处不再打 loadPage
  if (isDetailRoute.value) {
    detailLoading.value = true;
    viewMode.value = 'edit';
    return;
  }
  void loadDeptOptions();
  getList();
});

/**
 * KeepAlive 切回列表时恢复菜单标题，避免沿用「结算清单-患者」等脏标题。
 */
onActivated(() => {
  if (isDetailRoute.value) {
    return;
  }
  const menuTitle = typeof route.meta?.title === 'string' ? route.meta.title.trim() : '';
  if (menuTitle) {
    void setTabTitle(menuTitle);
  }
});

/**
 * 详情页签：immediate 首载 + 主键变化补载；与同主键 inflight 去重（见 loadDetailFromRoute）。
 */
watch(
  () => [isDetailRoute.value, routeCaseRecordId.value, routeEncounterId.value] as const,
  ([detail, caseId, encId], prev) => {
    if (!detail) {
      viewMode.value = 'list';
      return;
    }
    const prevDetail = prev?.[0];
    const prevCaseId = prev?.[1];
    const prevEncId = prev?.[2];
    const identityChanged = !prevDetail || caseId !== prevCaseId || encId !== prevEncId;
    const needRecover = viewMode.value !== 'edit' || !formModel.value;
    if (!identityChanged && !needRecover) {
      return;
    }
    // 已在加载且主键未变：不重复入队（同主键由 loadDetailFromRoute inflight 再挡一层）
    if (detailLoading.value && !identityChanged) {
      return;
    }
    detailLoading.value = true;
    viewMode.value = 'edit';
    void loadDetailFromRoute();
  },
  { immediate: true },
);
</script>

<template>
  <Page auto-content-height content-class="mrms-homepage-page">
    <!-- 详情路由：绝不渲染列表，避免 viewMode 未切 edit 时落到 TableLayout -->
    <div
      v-if="isDetailRoute && (detailLoading || !formModel)"
      v-loading="detailLoading"
      class="hp-detail-loading"
      element-loading-text="正在加载病案首页…"
    >
      <div v-if="!detailLoading && !formModel" class="hp-detail-loading__empty">
        <p>未能加载病案首页</p>
        <ElButton type="primary" @click="handleBackToList">关闭</ElButton>
      </div>
    </div>

    <HomepageEditPanel
      v-else-if="isDetailRoute && formModel"
      :form-model="formModel"
      :status-code="editStatusCode"
      :medcasno="editRow?.medcasno"
      :return-reason="returnReason"
      :readonly="!canEditForm"
      :can-save="canEditForm"
      :can-import="canEditForm"
      :can-submit="canEditForm"
      :saving="saving"
      :printing="printing"
      :tcm-info-enabled="tcmInfoEnabled"
      @back="handleBackToList"
      @dirty="handleFormDirty"
      @save="handleSave"
      @submit="handleSubmit"
      @import-all="handleImportAll"
      @import-section="handleImportSection"
      @print="handlePrint"
    />

    <TableLayout
      v-else-if="!isDetailRoute"
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
      @selection-change="handleSelectionChange"
    >
      <template
        v-if="MRMS_HOMEPAGE_WEISTAT_EXPORT_ENABLED || MRMS_HOMEPAGE_HQMS_EXPORT_ENABLED"
        #toolbar-actions
      >
        <ElButton
          v-if="MRMS_HOMEPAGE_WEISTAT_EXPORT_ENABLED"
          type="primary"
          plain
          :loading="weistatExporting"
          :disabled="selectedRows.length === 0"
          @click="handleExportWeistatN041"
        >
          导出卫统CSV{{ selectedRows.length > 0 ? `（${selectedRows.length}）` : '' }}
        </ElButton>
        <ElButton
          v-if="MRMS_HOMEPAGE_HQMS_EXPORT_ENABLED"
          type="primary"
          plain
          :loading="hqmsExporting"
          :disabled="selectedRows.length === 0"
          @click="handleExportHqms"
        >
          导出HQMS CSV{{ selectedRows.length > 0 ? `（${selectedRows.length}）` : '' }}
        </ElButton>
      </template>

      <template #patient="{ row }">
        <div class="hp-patient">
          <span class="hp-patient__name">{{ row.patientName }}</span>
          <span class="hp-patient__ipt">{{ row.iptNo }}</span>
        </div>
      </template>

      <template #idInsKeys="{ row }">
        <div
          class="hp-id-ins flex min-w-[300px] flex-col gap-1 py-1"
          style="min-width: 300px; width: 100%; box-sizing: border-box"
        >
          <div
            class="flex h-[18px] min-w-0 items-center gap-1.5 overflow-hidden whitespace-nowrap leading-[18px]"
            :title="row.certno || ''"
          >
            <span class="w-7 shrink-0 text-[11px]" style="color: var(--el-text-color-secondary)">
              证
            </span>
            <span
              class="min-w-0 flex-1 overflow-hidden text-ellipsis text-xs"
              style="color: var(--el-text-color-primary)"
            >
              {{ row.certno || '—' }}
            </span>
          </div>
          <div
            class="flex h-[18px] min-w-0 items-center gap-1.5 overflow-hidden whitespace-nowrap leading-[18px]"
            :title="row.psnNo || ''"
          >
            <span class="w-7 shrink-0 text-[11px]" style="color: var(--el-text-color-secondary)">
              psn
            </span>
            <span
              class="min-w-0 flex-1 overflow-hidden text-ellipsis text-xs"
              style="color: var(--el-text-color-primary)"
            >
              {{ row.psnNo || '—' }}
            </span>
          </div>
          <div
            class="flex h-[18px] min-w-0 items-center gap-1.5 overflow-hidden whitespace-nowrap leading-[18px]"
            :title="row.setlId || ''"
          >
            <span class="w-7 shrink-0 text-[11px]" style="color: var(--el-text-color-secondary)">
              setl
            </span>
            <span
              class="min-w-0 flex-1 overflow-hidden text-ellipsis text-xs"
              style="color: var(--el-text-color-primary)"
            >
              {{ row.setlId || '—' }}
            </span>
          </div>
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
        <ElTag
          size="small"
          effect="light"
          :type="settleUploadStatusTagType(row.settleUploadStatusCode)"
        >
          {{ settleUploadStatusLabel(row.settleUploadStatusCode) }}
        </ElTag>
      </template>

      <template #insUpload="{ row }">
        <HomepageInsUploadActions
          :row="row"
          :loading="insActingRowId === row.id"
          @action="handleInsAction"
        />
      </template>

      <template #rowActions="{ row }">
        <HomepageRowActions :row="row" @action="handleRowAction" />
      </template>
    </TableLayout>

    <MrmsInpatientClinicalHost ref="clinicalHostRef" />
    <MrmsSettleQuery4103Dialog v-model="query4103Visible" :result="query4103Result" />
  </Page>
</template>

<style scoped>
.mrms-homepage-page {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

/* 详情页签加载占位：占满可视高度，避免空白闪屏 */
.hp-detail-loading {
  position: relative;
  flex: 1;
  min-height: 320px;
  background: var(--el-bg-color);
  border-radius: 6px;
}

.hp-detail-loading__empty {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 320px;
  color: var(--el-text-color-regular);
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

/* 证件/医保列：布局以模板原子类 + 内联 min-width 为准（防 scoped 丢失） */
.hp-id-ins {
  font-variant-numeric: tabular-nums;
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
