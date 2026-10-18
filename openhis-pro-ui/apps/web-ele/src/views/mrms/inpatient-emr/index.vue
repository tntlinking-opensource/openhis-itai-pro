<script setup lang="ts">
import type { FormItemConfig, TableColumn } from '@zxplat-core/table-layout-ui';

import type { HomepageListQuery, HomepageListRow } from '../homepage/homepage-list.types';
import type { HomepageRowActionCode, HomepageSelectOption } from '../homepage/homepage.constants';
import type { MrmsInpatientClinicalKind } from '../components/mrms-inpatient-clinical-host.vue';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';
import { useUserStore } from '@zxplat/stores';
import { formatDateTime } from '@zxplat/utils';

import { TableLayout } from '@zxplat-core/table-layout-ui';

import { ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus';

import { httpLoadInpatientRegistrationContext } from '#/api/inpatient-registration';
import {
  archiveInpatientClinicalCaseApi,
  queryInpatientEmrPageApi,
  unarchiveInpatientClinicalCaseApi,
} from '#/api/mrms';
import { useRequest } from '#/hooks/use-request';
import { useAuthStore } from '#/store';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import {
  preserveApiIdForRequest,
  resolveLoginOrganizationIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';

import MrmsInpatientClinicalHost from '../components/mrms-inpatient-clinical-host.vue';
import {
  ALL_OPTION_VALUE,
  DEPT_ALL_OPTION,
  YES_NO_DISCHARGE_OPTIONS,
} from '../homepage/homepage.constants';
import { mapListItemToRow } from '../homepage/homepage-form.util';
import {
  resolveInpatientClinicalEditDenial,
  resolveInpatientNurseSheetEditDenial,
} from '../mrms-clinical-edit-gate';
import { loadManagedWardLocationIds } from '../mrms-managed-ward-ids';

defineOptions({ name: 'MrmsInpatientEmrQuery' });

/**
 * T3：住院病例查询列表（独立菜单页）
 * 列表与临床弹层均以住院就诊 encounter 为锚点；病历侧栏仅本就诊文书。
 * 快捷入口：病历 / 医嘱 / 体温单 / 护理记录。
 * 病历/医嘱按入院科室；体温单/护理记录另放行管辖病区护士。
 */

const userStore = useUserStore();
const authStore = useAuthStore();
const tableData = ref<HomepageListRow[]>([]);
const total = ref(0);
const loading = ref(false);
const clinicalHostRef = ref<InstanceType<typeof MrmsInpatientClinicalHost> | null>(null);

/** 当前登录医务人员主键（须有大夫身份） */
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

/** 科室下拉：全部 + 住院登记 organizationOptions */
const deptOptions = ref<HomepageSelectOption[]>([DEPT_ALL_OPTION]);

const queryParams = reactive<HomepageListQuery>({
  pageNo: 1,
  pageSize: 20,
  keyword: undefined,
  admCatyName: ALL_OPTION_VALUE,
  admTimeRange: undefined,
  dscgTimeRange: undefined,
  dischargedCode: ALL_OPTION_VALUE,
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
    label: '是否出院',
    prop: 'dischargedCode',
    type: 'select',
    options: YES_NO_DISCHARGE_OPTIONS,
    clearable: true,
    width: '120px',
  },
]);

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 56, align: 'center' },
  { prop: 'patientName', label: '患者', minWidth: 140, slot: 'patient', align: 'left' },
  { prop: 'medcasno', label: '病案号', width: 150, align: 'center', showOverflowTooltip: true },
  { prop: 'admDeptName', label: '科室', width: 110, align: 'center', showOverflowTooltip: true },
  { prop: 'admTime', label: '入院', width: 168, align: 'center', slot: 'admTime' },
  { prop: 'dscgTime', label: '出院', width: 168, align: 'center', slot: 'dscgTime' },
  {
    prop: 'dischargeDiagnosisText',
    label: '出院诊断',
    minWidth: 160,
    align: 'left',
    showOverflowTooltip: true,
  },
  { prop: 'dischargedCode', label: '出院', width: 88, align: 'center', slot: 'discharged' },
  {
    label: '操作',
    width: 400,
    fixed: 'right',
    slot: 'actions',
    align: 'center',
  },
];

const { run: runPage, loading: pageLoading } = useRequest(queryInpatientEmrPageApi, {
  manual: true,
  onSuccess: (data) => {
    const parsed = parsePagedRowsFromApiData(data);
    // 出参字段与 CaseRecordListItemDto 对齐，复用首页行映射
    tableData.value = parsed.content.map(mapListItemToRow);
    total.value = parsed.totalCount;
  },
});

watch(pageLoading, (v) => {
  loading.value = v ?? false;
});

/** 格式化时间 */
function fmtTime(v?: string): string {
  if (!v) {
    return '—';
  }
  return formatDateTime(v) || '—';
}

/** 组装住院病例查询请求（独立 AppService，以住院就诊为行） */
function buildQuery() {
  const admRange = queryParams.admTimeRange ?? [];
  const dscgRange = queryParams.dscgTimeRange ?? [];
  const admCaty =
    queryParams.admCatyName && queryParams.admCatyName !== ALL_OPTION_VALUE
      ? queryParams.admCatyName
      : undefined;
  const discharged =
    queryParams.dischargedCode && queryParams.dischargedCode !== ALL_OPTION_VALUE
      ? queryParams.dischargedCode
      : undefined;
  return {
    pageNo: queryParams.pageNo,
    pageSize: queryParams.pageSize,
    keyword: queryParams.keyword?.trim() || undefined,
    admCatyName: admCaty,
    dischargedCode: discharged,
    admFrom: toDateOnly(admRange[0]),
    admTo: toDateOnly(admRange[1]),
    dscgFrom: toDateOnly(dscgRange[0]),
    dscgTo: toDateOnly(dscgRange[1]),
  };
}

/** 时间范围截成 yyyy-MM-dd */
function toDateOnly(v?: string): string | undefined {
  if (!v) {
    return undefined;
  }
  return v.slice(0, 10);
}

/** 拉取列表 */
async function getList() {
  await runPage(buildQuery());
}

/** 搜索 */
function handleQuery() {
  queryParams.pageNo = 1;
  getList();
}

/** 重置 */
function handleReset() {
  queryParams.pageNo = 1;
  queryParams.pageSize = 20;
  queryParams.keyword = undefined;
  queryParams.admCatyName = ALL_OPTION_VALUE;
  queryParams.admTimeRange = undefined;
  queryParams.dscgTimeRange = undefined;
  queryParams.dischargedCode = ALL_OPTION_VALUE;
  getList();
}

/** 分页 */
function handlePagination(pagination: { page: number; limit: number }) {
  queryParams.pageNo = pagination.page;
  queryParams.pageSize = pagination.limit;
  getList();
}

/**
 * 打开临床模块：按行判定可写/只读。
 *
 * @param code 行操作 code
 * @param current 列表行
 */
async function openClinical(code: HomepageRowActionCode, current: HomepageListRow) {
  const kind = toClinicalKind(code);
  if (!kind) {
    ElMessage.info('暂不支持该入口');
    return;
  }
  const editable = await resolveClinicalEditable(kind, current);
  clinicalHostRef.value?.open(kind, current, { forceReadOnly: !editable });
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
  const denial = resolveInpatientClinicalEditDenial(
    loginPractitionerId.value,
    loginOrganizationId.value,
    row,
  );
  return denial === null;
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
  return null;
}

/**
 * 归档病例：确认后锁定该住院就诊下 EMR 文书。
 *
 * @param row 列表行
 */
async function handleArchiveClinicalCase(row: HomepageListRow) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) {
    ElMessage.warning('缺少就诊主键，无法归档病例');
    return;
  }
  try {
    await ElMessageBox.confirm('归档后该次住院的病历将不可编辑，确认归档病例？', '归档病例', {
      type: 'warning',
      confirmButtonText: '确认归档',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    loading.value = true;
    await archiveInpatientClinicalCaseApi({ encounterId });
    ElMessage.success('病例已归档');
    await getList();
  } catch {
    // 错误由全局拦截器提示
  } finally {
    loading.value = false;
  }
}

/**
 * 解档病例：确认后将 ARCHIVED 文书恢复为 DRAFT（病案主档已归档时后端拒绝）。
 *
 * @param row 列表行
 */
async function handleUnarchiveClinicalCase(row: HomepageListRow) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) {
    ElMessage.warning('缺少就诊主键，无法解档病例');
    return;
  }
  try {
    await ElMessageBox.confirm('解档后同科室大夫可再次编辑病历，确认解档病例？', '解档病例', {
      type: 'warning',
      confirmButtonText: '确认解档',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    loading.value = true;
    await unarchiveInpatientClinicalCaseApi({ encounterId });
    ElMessage.success('病例已解档');
    await getList();
  } catch {
    // 错误由全局拦截器提示
  } finally {
    loading.value = false;
  }
}

/** 拉取住院科室下拉（登记上下文；失败不阻塞列表） */
async function loadDeptOptions() {
  try {
    const context = await httpLoadInpatientRegistrationContext({});
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
  <Page auto-content-height content-class="mrms-inpatient-emr-page">
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
      border
      stripe
      size="small"
      @query="handleQuery"
      @reset="handleReset"
      @pagination="handlePagination"
    >
      <template #patient="{ row }">
        <div class="ip-emr-patient">
          <span class="ip-emr-patient__name">{{ row.patientName }}</span>
          <span class="ip-emr-patient__ipt">{{ row.iptNo || '—' }}</span>
        </div>
      </template>
      <template #admTime="{ row }">
        <span>{{ fmtTime(row.admTime) }}</span>
      </template>
      <template #dscgTime="{ row }">
        <span>{{ fmtTime(row.dscgTime) }}</span>
      </template>
      <template #discharged="{ row }">
        <ElTag size="small" effect="light" :type="row.dischargedCode === '1' ? 'success' : 'info'">
          {{ row.dischargedCode === '1' ? '已出院' : '未出院' }}
        </ElTag>
      </template>
      <template #actions="{ row }">
        <ElButton link type="primary" @click="openClinical('emr', row)">病历</ElButton>
        <ElButton link type="primary" @click="openClinical('order', row)">医嘱</ElButton>
        <ElButton link type="primary" @click="openClinical('temperature', row)">体温单</ElButton>
        <ElButton link type="primary" @click="openClinical('nursing', row)">护理记录</ElButton>
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

    <MrmsInpatientClinicalHost ref="clinicalHostRef" />
  </Page>
</template>

<style scoped>
.ip-emr-patient {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.3;
}

.ip-emr-patient__name {
  color: var(--el-color-primary);
  font-weight: 600;
}

.ip-emr-patient__ipt {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
