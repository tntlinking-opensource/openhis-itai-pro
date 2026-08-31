<script setup lang="ts">
import type {
  BatchRowDto,
  CampusRosterCandidateDto,
  CampusRosterDepartmentOptionDto,
  CompanyRowDto,
  ContractRowDto,
  GroupRosterRowDto,
  LoadRosterSummaryResponse,
  RosterImportRowDto,
} from '#/api/peis/group-check';
import type { PeisPackageDto } from '#/api/peis/catalog-config';

import { computed, onMounted, ref, watch } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { searchPackageApi, searchActivityApi } from '#/api/peis/catalog-config';
import type { PeisActivityDto } from '#/api/peis/catalog-config';
import {
  adjustRosterServiceRequestsApi,
  cancelGroupArrivalApi,
  cancelGroupEnrollmentApi,
  confirmBatchEnrollmentApi,
  confirmGroupArrivalApi,
  downloadRosterImportTemplateApi,
  importRosterApi,
  importRosterFromCampusApi,
  importRosterFromTemplateApi,
  listCampusRosterClassNamesApi,
  listCampusRosterDepartmentsApi,
  listBatchesApi,
  listCompaniesApi,
  listContractsApi,
  markContractPaidApi,
  loadRosterSummaryApi,
  pageRosterApi,
  previewCampusRosterImportApi,
  saveCompanyApi,
  saveContractApi,
  saveGroupBatchApi,
} from '#/api/peis/group-check';
import {
  getCheckOrderDetailApi,
  type CheckOrderDetailResponse,
  type ServiceRequestLineDto,
} from '#/api/peis/personal-registration';
import { downloadBinaryExportFile } from '#/utils/export/download-binary-export';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { printPeisGuide } from '#/utils/print/print-peis-guide';
import {
  ARRIVAL_STATUS,
  arrivalLabel,
  arrivalTagType,
  CONTRACT_PAY_STATUS,
  contractPayStatusLabel,
  contractPayStatusTagType,
  enrollmentLabel,
  enrollmentTagType,
  inferRosterMemberKind,
  isRosterEnrolled,
  packageLabel,
  packageLabels,
  packageDisplayName,
  filterPackagesByIds,
  GENDER_OPTIONS,
  genderLabel,
  rosterMemberNo,
  rosterMemberNoLabel,
  rosterOrgUnitLabel,
  ROSTER_MEMBER_KIND_OPTIONS,
  type RosterMemberKind,
} from '#/views/peis/group-check/constants';
import {
  ElButton,
  ElCard,
  ElCheckbox,
  ElDatePicker,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
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

defineOptions({ name: 'PeisGroupCheck' });

const userStore = useUserStore();
const activeTab = ref('company');

const orgId = computed(() => {
  const fromUser = userStore.userInfo?.organizationId;
  return fromUser != null ? String(fromUser) : '';
});

const companies = ref<CompanyRowDto[]>([]);
const contracts = ref<ContractRowDto[]>([]);
const batches = ref<BatchRowDto[]>([]);
const packages = ref<PeisPackageDto[]>([]);
const selectedCompanyId = ref('');
const selectedBatchId = ref('');

const companyForm = ref({
  companyId: '',
  name: '',
  contactName: '',
  contactPhone: '',
});

const contractForm = ref({
  contractId: '',
  packageActivityIds: [] as string[],
  contractName: '',
  contractAmount: undefined as number | undefined,
  validFrom: '',
  validTo: '',
});

const batchForm = ref({
  batchId: '',
  contractId: '',
  batchName: '',
  packageActivityId: '',
  bookingDate: '',
});

const rosterRows = ref<GroupRosterRowDto[]>([]);
const rosterTotal = ref(0);
const rosterPageNo = ref(1);
const rosterPageSize = ref(20);
const rosterLoading = ref(false);
const rosterEnrollLoading = ref(false);
const selectedRosterRows = ref<GroupRosterRowDto[]>([]);
const rosterSummary = ref<LoadRosterSummaryResponse | null>(null);

const rosterKeyword = ref('');
const rosterEnrollmentFilter = ref<'' | 'ENROLLED' | 'UNENROLLED'>('');
const rosterArrivalFilter = ref<'' | 'ARRIVED' | 'PENDING'>('');

const rosterMemberKind = ref<RosterMemberKind>('ENTERPRISE');
const importAutoEnroll = ref(true);
const importSyncCampus = ref(true);
const campusPullKind = ref<'STAFF' | 'STUDENT'>('STUDENT');
const campusDepartments = ref<CampusRosterDepartmentOptionDto[]>([]);
const campusClassNames = ref<string[]>([]);
const campusDepartmentId = ref('');
const campusClassName = ref('');
const campusCandidates = ref<CampusRosterCandidateDto[]>([]);
const campusSelectedRows = ref<CampusRosterCandidateDto[]>([]);
const campusPullLoading = ref(false);
const templateImportLoading = ref(false);
const importTemplateInputRef = ref<HTMLInputElement | null>(null);

const adjustDrawerOpen = ref(false);
const adjustRoster = ref<GroupRosterRowDto | null>(null);
const adjustOrderDetail = ref<CheckOrderDetailResponse | null>(null);
const adjustLoading = ref(false);
const addActivityId = ref('');
const activityMap = ref<Map<string, PeisActivityDto>>(new Map());

const importForm = ref<RosterImportRowDto>({
  fullName: '',
  genderEnum: '',
  studentNo: '',
  employeeNo: '',
  collegeId: '',
  className: '',
  idCardNo: '',
  mobilePhone: '',
});

const selectedCompany = computed(() =>
  companies.value.find((item) => String(item.companyId ?? '') === selectedCompanyId.value),
);

const selectedBatch = computed(() =>
  batches.value.find((item) => String(item.batchId ?? '') === selectedBatchId.value),
);

const rosterMemberNoFieldLabel = computed(() => rosterMemberNoLabel(rosterMemberKind.value));
const rosterOrgUnitFieldLabel = computed(() => rosterOrgUnitLabel(rosterMemberKind.value));

const selectedBatchLabel = computed(() => {
  const batch = selectedBatch.value;
  if (!batch) return '请先选择批次';
  const pkg = packageLabel(String(batch.packageActivityId ?? ''), packages.value);
  return `${batch.batchName ?? '未命名批次'} · 套餐：${pkg}`;
});

function requireBatchPackage() {
  const batch = selectedBatch.value;
  const pkgId = String(batch?.packageActivityId ?? '');
  if (!pkgId) {
    ElMessage.warning('当前批次未绑定套餐，请先在「合同与批次」页为批次选择套餐');
    return null;
  }
  return {
    pkgId,
    pkgName: packageLabel(pkgId, packages.value),
    batchName: batch?.batchName ?? '未命名批次',
  };
}

const batchContractPackages = computed(() => {
  const contract = contracts.value.find(
    (item) => String(item.contractId ?? '') === batchForm.value.contractId,
  );
  return filterPackagesByIds(packages.value, contract?.packageActivityIds);
});

const contractCatalogAmountSum = computed(() => {
  const selected = packages.value.filter((pkg) =>
    contractForm.value.packageActivityIds.includes(String(pkg.packageActivityId ?? '')),
  );
  if (selected.length === 0) return 0;
  let sum = 0;
  let hasPrice = false;
  for (const pkg of selected) {
    const price = Number(pkg.salePrice ?? 0);
    if (!Number.isNaN(price) && price > 0) {
      sum += price;
      hasPrice = true;
    }
  }
  return hasPrice ? sum : 0;
});

const contractCatalogAmountHint = computed(() =>
  contractCatalogAmountSum.value > 0 ? formatMoneyDisplay(contractCatalogAmountSum.value) : '',
);

function parseContractAmount(value?: number | string | null) {
  if (value == null || value === '') return undefined;
  const num = Number(value);
  return Number.isNaN(num) ? undefined : num;
}

function formatContractAmount(value?: number | string | null) {
  if (value == null || value === '') return '—';
  return formatMoneyDisplay(value);
}

function applyCatalogContractAmount() {
  if (contractCatalogAmountSum.value <= 0) {
    ElMessage.warning('所选套餐暂无目录价可参考');
    return;
  }
  contractForm.value.contractAmount = contractCatalogAmountSum.value;
}

function editContract(row: ContractRowDto) {
  contractForm.value = {
    contractId: String(row.contractId ?? ''),
    packageActivityIds: (
      row.packageActivityIds ?? (row.packageActivityId ? [row.packageActivityId] : [])
    ).map((id) => String(id)),
    contractName: row.contractName ?? '',
    contractAmount: parseContractAmount(row.contractAmount),
    validFrom: row.validFrom ?? '',
    validTo: row.validTo ?? '',
  };
}

function resetContractForm() {
  contractForm.value = {
    contractId: '',
    packageActivityIds: [],
    contractName: '',
    contractAmount: undefined,
    validFrom: '',
    validTo: '',
  };
}

async function loadPackages() {
  const resp = await searchPackageApi({ pageNo: 1, pageSize: 200, statusEnum: 'active' });
  packages.value = resp?.content ?? [];
}

async function loadActivities() {
  const resp = await searchActivityApi({ pageNo: 1, pageSize: 500, statusEnum: 'active' });
  const map = new Map<string, PeisActivityDto>();
  for (const row of resp?.content ?? []) {
    if (row.activityId != null) {
      map.set(String(row.activityId), row);
    }
  }
  activityMap.value = map;
}

const adjustOrderLines = computed(() => adjustOrderDetail.value?.serviceRequestLines ?? []);

const packageBaselineActivityIds = computed(() => {
  const pkgId =
    adjustOrderDetail.value?.packageActivityId ?? selectedBatch.value?.packageActivityId;
  if (pkgId == null) return new Set<string>();
  const pkg = packages.value.find((item) => String(item.packageActivityId) === String(pkgId));
  return new Set((pkg?.childActivityIds ?? []).map((id) => String(id)));
});

const availableAddActivities = computed(() => {
  const selected = new Set(
    adjustOrderLines.value.map((line) => String(line.activityId ?? '')).filter(Boolean),
  );
  const rows: PeisActivityDto[] = [];
  for (const row of activityMap.value.values()) {
    if (row.activityId == null || selected.has(String(row.activityId))) {
      continue;
    }
    rows.push(row);
  }
  return rows;
});

function activityById(activityId?: string | number | null) {
  if (activityId == null) return undefined;
  return activityMap.value.get(String(activityId));
}

function isAddOnLine(line: ServiceRequestLineDto) {
  const activityId = String(line.activityId ?? '');
  if (!activityId) return false;
  if (packageBaselineActivityIds.value.size === 0) return false;
  return !packageBaselineActivityIds.value.has(activityId);
}

function formatActivityOptionLabel(row: PeisActivityDto) {
  const price = row.salePrice != null ? formatMoneyDisplay(row.salePrice) : '';
  return price
    ? `${row.display || row.code || '项目'} · ${price}`
    : row.display || row.code || '项目';
}

async function openAdjustDrawer(row: GroupRosterRowDto) {
  if (!row.encounterId || !row.rosterId) {
    ElMessage.warning('请先完成建单后再调整项目');
    return;
  }
  adjustRoster.value = row;
  adjustDrawerOpen.value = true;
  addActivityId.value = '';
  await refreshAdjustOrderDetail();
}

async function refreshAdjustOrderDetail() {
  const encounterId = String(adjustRoster.value?.encounterId ?? '');
  if (!encounterId) return;
  adjustLoading.value = true;
  try {
    adjustOrderDetail.value = await getCheckOrderDetailApi({ encounterId });
  } finally {
    adjustLoading.value = false;
  }
}

async function removeAdjustLine(line: ServiceRequestLineDto) {
  const rosterId = String(adjustRoster.value?.rosterId ?? '');
  if (!rosterId || !line.serviceRequestId) return;
  if (line.execStatusCode && line.execStatusCode !== 'pending') {
    ElMessage.warning('仅待检项目可减项');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认减项「${line.display || line.activityId}」？`, '减项确认', {
      type: 'warning',
    });
  } catch {
    return;
  }
  adjustLoading.value = true;
  try {
    await adjustRosterServiceRequestsApi({
      rosterId,
      removeServiceRequestIds: [String(line.serviceRequestId)],
    });
    ElMessage.success('已减项');
    await refreshAdjustOrderDetail();
    await loadRoster();
  } finally {
    adjustLoading.value = false;
  }
}

async function addAdjustActivity() {
  const rosterId = String(adjustRoster.value?.rosterId ?? '');
  if (!rosterId) return;
  if (!addActivityId.value) {
    ElMessage.warning('请选择要加项的体检项目');
    return;
  }
  adjustLoading.value = true;
  try {
    await adjustRosterServiceRequestsApi({
      rosterId,
      addActivityIds: [addActivityId.value],
    });
    addActivityId.value = '';
    ElMessage.success('已加项，请引导受检者到「团检加项收费」缴纳');
    await refreshAdjustOrderDetail();
    await loadRoster();
  } finally {
    adjustLoading.value = false;
  }
}

async function loadCompanies() {
  companies.value = (await listCompaniesApi()) ?? [];
}

async function loadContractsAndBatches() {
  if (!selectedCompanyId.value) {
    contracts.value = [];
    batches.value = [];
    return;
  }
  contracts.value = (await listContractsApi({ companyId: selectedCompanyId.value })) ?? [];
  batches.value = (await listBatchesApi({ companyId: selectedCompanyId.value })) ?? [];
  const stillExists = batches.value.some(
    (item) => String(item.batchId ?? '') === selectedBatchId.value,
  );
  if (!stillExists) {
    selectedBatchId.value = batches.value.length > 0 ? String(batches.value[0]?.batchId ?? '') : '';
  }
}

async function loadRosterSummary() {
  if (!selectedBatchId.value) {
    rosterSummary.value = null;
    return;
  }
  rosterSummary.value = await loadRosterSummaryApi({ batchId: selectedBatchId.value });
}

async function loadRoster() {
  if (!selectedBatchId.value) {
    rosterRows.value = [];
    rosterTotal.value = 0;
    rosterSummary.value = null;
    return;
  }
  rosterLoading.value = true;
  try {
    const resp = await pageRosterApi({
      batchId: selectedBatchId.value,
      enrollmentFilter: rosterEnrollmentFilter.value || undefined,
      arrivalStatusCode: rosterArrivalFilter.value || undefined,
      pageNo: rosterPageNo.value,
      pageSize: rosterPageSize.value,
    });
    let rows = resp?.content ?? [];
    const keyword = rosterKeyword.value.trim();
    if (keyword) {
      rows = rows.filter((row) => {
        const haystack = [
          row.fullName,
          row.studentNo,
          row.employeeNo,
          row.idCardNo,
          row.mobilePhone,
          row.className,
        ]
          .map((item) => String(item ?? ''))
          .join(' ');
        return haystack.includes(keyword);
      });
    }
    rosterRows.value = rows;
    rosterTotal.value = keyword ? rows.length : (resp?.totalCount ?? 0);
    await loadRosterSummary();
  } finally {
    rosterLoading.value = false;
  }
}

function resetCompanyForm() {
  companyForm.value = {
    companyId: '',
    name: '',
    contactName: '',
    contactPhone: '',
  };
}

function editCompany(row: CompanyRowDto) {
  const companyId = String(row.companyId ?? '');
  if (companyId) {
    selectedCompanyId.value = companyId;
  }
  companyForm.value = {
    companyId,
    name: row.name ?? '',
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
  };
}

async function handleSaveCompany() {
  if (!companyForm.value.name || !orgId.value) {
    ElMessage.warning('请填写单位名称并确认机构');
    return;
  }
  const resp = await saveCompanyApi({
    companyId: companyForm.value.companyId || undefined,
    name: companyForm.value.name,
    orgId: orgId.value,
    contactName: companyForm.value.contactName || undefined,
    contactPhone: companyForm.value.contactPhone || undefined,
  });
  ElMessage.success(`单位已保存：${resp?.companyId ?? ''}`);
  if (resp?.companyId != null) {
    selectedCompanyId.value = String(resp.companyId);
  }
  resetCompanyForm();
  await loadCompanies();
  await loadContractsAndBatches();
}

async function handleSaveContract() {
  if (!selectedCompanyId.value || contractForm.value.packageActivityIds.length === 0) {
    ElMessage.warning('请选择单位并至少选择一个套餐');
    return;
  }
  if (contractForm.value.contractAmount == null || contractForm.value.contractAmount < 0) {
    ElMessage.warning('请填写合同金额');
    return;
  }
  const resp = await saveContractApi({
    contractId: contractForm.value.contractId || undefined,
    companyId: selectedCompanyId.value,
    packageActivityIds: contractForm.value.packageActivityIds,
    contractName: contractForm.value.contractName || undefined,
    contractAmount: contractForm.value.contractAmount,
    validFrom: contractForm.value.validFrom || undefined,
    validTo: contractForm.value.validTo || undefined,
    orgId: orgId.value || undefined,
  });
  ElMessage.success(`合同已保存：${resp?.contractId ?? ''}`);
  resetContractForm();
  await loadContractsAndBatches();
}

async function handleMarkContractPaid(row: ContractRowDto) {
  const contractId = String(row.contractId ?? '');
  if (!contractId) return;
  if (row.payStatusCode === CONTRACT_PAY_STATUS.PAID) {
    ElMessage.info('该合同已确认收款');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认单位已对合同「${row.contractName || contractId}」完成后结收款？`,
      '单位后结确认',
      { type: 'warning', confirmButtonText: '确认已收', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  await markContractPaidApi({ contractId });
  ElMessage.success('合同已标记为已收');
  await loadContractsAndBatches();
}

async function handleSaveBatch() {
  if (!batchForm.value.contractId || !batchForm.value.batchName) {
    ElMessage.warning('请选择合同并填写批次名称');
    return;
  }
  if (!batchForm.value.packageActivityId) {
    ElMessage.warning('请从合同可选套餐中选择本批次套餐');
    return;
  }
  const resp = await saveGroupBatchApi({
    batchId: batchForm.value.batchId || undefined,
    companyId: selectedCompanyId.value || undefined,
    contractId: batchForm.value.contractId,
    batchName: batchForm.value.batchName,
    packageActivityId: batchForm.value.packageActivityId || undefined,
    bookingDate: batchForm.value.bookingDate || undefined,
    orgId: orgId.value || undefined,
  });
  ElMessage.success(`批次已保存：${resp?.batchId ?? ''}`);
  batchForm.value = {
    batchId: '',
    contractId: '',
    batchName: '',
    packageActivityId: '',
    bookingDate: '',
  };
  await loadContractsAndBatches();
}

async function handleImportRoster() {
  if (!selectedBatchId.value || !importForm.value.fullName?.trim()) {
    ElMessage.warning('请选择批次并填写姓名');
    return;
  }
  const row = importForm.value;
  const memberNo =
    rosterMemberKind.value === 'STUDENT' ? row.studentNo?.trim() : row.employeeNo?.trim();
  const hasIdentity = Boolean(row.idCardNo?.trim() || row.mobilePhone?.trim() || memberNo);
  if (!hasIdentity) {
    const noLabel = rosterMemberNoFieldLabel.value;
    ElMessage.warning(`请至少填写身份证、手机号或${noLabel}之一`);
    return;
  }
  if (importAutoEnroll.value && !requireBatchPackage()) {
    return;
  }
  const payload: RosterImportRowDto = { ...importForm.value };
  if (rosterMemberKind.value === 'STUDENT') {
    payload.employeeNo = '';
  } else {
    payload.studentNo = '';
  }
  const resp = await importRosterApi({
    batchId: selectedBatchId.value,
    rows: [payload],
    autoEnroll: importAutoEnroll.value,
    memberKind: rosterMemberKind.value,
    syncCampus: rosterMemberKind.value === 'STAFF' ? importSyncCampus.value : undefined,
  });
  const imported = resp?.importedCount ?? 0;
  const enrolled = resp?.enrolledCount ?? 0;
  const failCount = resp?.enrollmentFailures?.length ?? 0;
  const campusFailCount = resp?.campusFailures?.length ?? 0;
  const campusSynced = resp?.campusSyncedCount ?? 0;
  if (campusFailCount > 0) {
    const first = resp?.campusFailures?.[0];
    ElMessage.warning(
      `Campus 同步失败 ${campusFailCount} 人${first?.message ? `：${first.message}` : ''}`,
    );
    await loadRoster();
    return;
  }
  if (importAutoEnroll.value) {
    if (failCount > 0) {
      const first = resp?.enrollmentFailures?.[0];
      ElMessage.warning(
        `导入 ${imported} 人，建单成功 ${enrolled} 人，失败 ${failCount} 人${first?.message ? `：${first.message}` : ''}`,
      );
    } else if (imported > 0) {
      const campusNote =
        rosterMemberKind.value === 'STAFF' && importSyncCampus.value && campusSynced > 0
          ? `，Campus 同步 ${campusSynced} 人`
          : '';
      ElMessage.success(`导入并建单 ${enrolled} 人${campusNote}`);
    } else {
      ElMessage.info('该人员已在当前批次名册中');
    }
  } else {
    const campusNote =
      rosterMemberKind.value === 'STAFF' && importSyncCampus.value && campusSynced > 0
        ? `，Campus 同步 ${campusSynced} 人`
        : '';
    ElMessage.success(`导入 ${imported} 人${campusNote}`);
  }
  importForm.value = {
    fullName: '',
    genderEnum: '',
    studentNo: '',
    employeeNo: '',
    collegeId: '',
    className: '',
    idCardNo: '',
    mobilePhone: '',
  };
  await loadRoster();
}

function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result;
      if (typeof result !== 'string') {
        reject(new Error('读取文件失败'));
        return;
      }
      const commaIndex = result.indexOf(',');
      resolve(commaIndex >= 0 ? result.slice(commaIndex + 1) : result);
    };
    reader.onerror = () => reject(reader.error ?? new Error('读取文件失败'));
    reader.readAsDataURL(file);
  });
}

async function loadCampusPullOptions() {
  campusDepartments.value = [];
  campusClassNames.value = [];
  campusCandidates.value = [];
  campusSelectedRows.value = [];
  if (!selectedCompanyId.value) {
    return;
  }
  try {
    const [deptResp, classResp] = await Promise.all([
      listCampusRosterDepartmentsApi({ companyId: selectedCompanyId.value }),
      selectedCompany.value?.campusCollegeId
        ? listCampusRosterClassNamesApi({ companyId: selectedCompanyId.value })
        : Promise.resolve({ classNames: [] as string[] }),
    ]);
    campusDepartments.value = deptResp?.departments ?? [];
    campusClassNames.value = classResp?.classNames ?? [];
  } catch (error) {
    console.error('加载 Campus 名册选项失败:', error);
  }
}

async function handlePreviewCampusRoster() {
  if (!selectedCompanyId.value) {
    ElMessage.warning('请先选择团检单位');
    return;
  }
  if (campusPullKind.value === 'STAFF' && !campusDepartmentId.value) {
    ElMessage.warning('请选择 Campus 部门');
    return;
  }
  if (campusPullKind.value === 'STUDENT' && !selectedCompany.value?.campusCollegeId) {
    ElMessage.warning('当前单位未绑定 Campus 学院，无法拉取学生名册');
    return;
  }
  campusPullLoading.value = true;
  try {
    const resp = await previewCampusRosterImportApi({
      companyId: selectedCompanyId.value,
      memberKind: campusPullKind.value,
      className:
        campusPullKind.value === 'STUDENT' ? campusClassName.value || undefined : undefined,
      departmentId: campusPullKind.value === 'STAFF' ? campusDepartmentId.value : undefined,
    });
    campusCandidates.value = resp?.candidates ?? [];
    campusSelectedRows.value = [];
    if (campusCandidates.value.length === 0) {
      ElMessage.info('未找到可挂册人员（须已在 Campus 绑定患者）');
    }
  } catch (error) {
    console.error('预览 Campus 名册失败:', error);
    ElMessage.error('预览失败，请确认 Campus 已启用且档案完整');
  } finally {
    campusPullLoading.value = false;
  }
}

function handleCampusSelection(rows: CampusRosterCandidateDto[]) {
  campusSelectedRows.value = rows;
}

async function handleImportCampusRoster() {
  if (!selectedBatchId.value || !selectedCompanyId.value) {
    ElMessage.warning('请先选择单位和批次');
    return;
  }
  if (campusCandidates.value.length === 0) {
    ElMessage.warning('请先预览 Campus 名册');
    return;
  }
  if (importAutoEnroll.value && !requireBatchPackage()) {
    return;
  }
  const targets =
    campusSelectedRows.value.length > 0 ? campusSelectedRows.value : campusCandidates.value;
  const patientIds = targets.map((row) => String(row.patientId ?? '')).filter(Boolean);
  if (patientIds.length === 0) {
    ElMessage.warning('没有可导入的人员');
    return;
  }
  campusPullLoading.value = true;
  try {
    const resp = await importRosterFromCampusApi({
      batchId: selectedBatchId.value,
      companyId: selectedCompanyId.value,
      memberKind: campusPullKind.value,
      className:
        campusPullKind.value === 'STUDENT' ? campusClassName.value || undefined : undefined,
      departmentId: campusPullKind.value === 'STAFF' ? campusDepartmentId.value : undefined,
      patientIds,
      autoEnroll: importAutoEnroll.value,
    });
    const imported = resp?.importedCount ?? 0;
    const enrolled = resp?.enrolledCount ?? 0;
    const failCount = resp?.enrollmentFailures?.length ?? 0;
    if (importAutoEnroll.value && failCount > 0) {
      const first = resp?.enrollmentFailures?.[0];
      ElMessage.warning(
        `导入 ${imported} 人，建单成功 ${enrolled} 人，失败 ${failCount} 人${first?.message ? `：${first.message}` : ''}`,
      );
    } else {
      ElMessage.success(
        importAutoEnroll.value
          ? `从 Campus 导入并建单 ${enrolled} 人`
          : `从 Campus 导入 ${imported} 人`,
      );
    }
    await loadRoster();
  } catch (error) {
    console.error('从 Campus 导入失败:', error);
    ElMessage.error('从 Campus 导入失败');
  } finally {
    campusPullLoading.value = false;
  }
}

async function handleDownloadRosterTemplate(kind: RosterMemberKind) {
  try {
    const data = await downloadRosterImportTemplateApi({ memberKind: kind });
    if (!data?.fileBytes?.trim()) {
      ElMessage.warning('模板内容为空，请稍后重试');
      return;
    }
    const defaultName =
      kind === 'STUDENT'
        ? '团检名册导入模板_学生_v1.0.xlsx'
        : kind === 'STAFF'
          ? '团检名册导入模板_教职工_v1.0.xlsx'
          : '团检名册导入模板_企事业_v1.0.xlsx';
    downloadBinaryExportFile(data, data.fileName ?? defaultName);
    ElMessage.success('模板已下载');
  } catch (error) {
    console.error('下载名册模板失败:', error);
    ElMessage.error('下载模板失败，请稍后重试');
  }
}

function handleTemplateImportClick() {
  if (!selectedBatchId.value) {
    ElMessage.warning('请先选择批次');
    return;
  }
  if (importAutoEnroll.value && !requireBatchPackage()) {
    return;
  }
  importTemplateInputRef.value?.click();
}

async function handleTemplateImportFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !selectedBatchId.value) {
    return;
  }
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.warning('请上传 .xlsx 格式的导入模板');
    return;
  }
  templateImportLoading.value = true;
  try {
    const fileBytes = await readFileAsBase64(file);
    const resp = await importRosterFromTemplateApi({
      batchId: selectedBatchId.value,
      memberKind: rosterMemberKind.value,
      fileBytes,
      autoEnroll: importAutoEnroll.value,
      syncCampus: rosterMemberKind.value === 'STAFF' ? importSyncCampus.value : undefined,
    });
    const imported = resp?.importedCount ?? 0;
    const enrolled = resp?.enrolledCount ?? 0;
    const failCount = resp?.enrollmentFailures?.length ?? 0;
    const campusFailCount = resp?.campusFailures?.length ?? 0;
    const campusSynced = resp?.campusSyncedCount ?? 0;
    if (campusFailCount > 0) {
      const first = resp?.campusFailures?.[0];
      ElMessage.warning(
        `模板导入 Campus 同步失败 ${campusFailCount} 人${first?.message ? `：${first.message}` : ''}`,
      );
      await loadRoster();
      return;
    }
    if (importAutoEnroll.value) {
      if (failCount > 0) {
        const first = resp?.enrollmentFailures?.[0];
        ElMessage.warning(
          `导入 ${imported} 人，建单成功 ${enrolled} 人，失败 ${failCount} 人${first?.message ? `：${first.message}` : ''}`,
        );
      } else {
        const campusNote =
          rosterMemberKind.value === 'STAFF' && importSyncCampus.value && campusSynced > 0
            ? `，Campus 同步 ${campusSynced} 人`
            : '';
        ElMessage.success(`模板导入并建单 ${enrolled} 人${campusNote}`);
      }
    } else {
      const campusNote =
        rosterMemberKind.value === 'STAFF' && importSyncCampus.value && campusSynced > 0
          ? `，Campus 同步 ${campusSynced} 人`
          : '';
      ElMessage.success(`模板导入 ${imported} 人${campusNote}`);
    }
    await loadRoster();
  } catch (error) {
    console.error('模板导入失败:', error);
    ElMessage.error('模板导入失败，请检查文件格式与数据');
  } finally {
    templateImportLoading.value = false;
  }
}

function handleRosterSelection(rows: GroupRosterRowDto[]) {
  selectedRosterRows.value = rows;
}

function requireSelectedRosterIds() {
  const ids = selectedRosterRows.value.map((row) => String(row.rosterId ?? '')).filter(Boolean);
  if (ids.length === 0) {
    ElMessage.warning('请先勾选名册人员');
    return null;
  }
  return ids;
}

async function handleBatchEnrollment() {
  const rosterIds = requireSelectedRosterIds();
  if (!rosterIds) return;
  const batchPackage = requireBatchPackage();
  if (!batchPackage) return;
  await ElMessageBox.confirm(
    `批次「${batchPackage.batchName}」将使用套餐「${batchPackage.pkgName}」为 ${rosterIds.length} 人免收费建单，确认？`,
    '批量建单',
    { type: 'warning' },
  );
  rosterEnrollLoading.value = true;
  try {
    const resp = await confirmBatchEnrollmentApi({
      rosterIds,
      orgId: orgId.value || undefined,
    });
    const failCount = resp?.failures?.length ?? 0;
    if (failCount > 0) {
      const first = resp?.failures?.[0];
      ElMessage.warning(
        `成功 ${resp?.successCount ?? 0} 人，失败 ${failCount} 人${first?.message ? `：${first.message}` : ''}`,
      );
    } else {
      ElMessage.success(`已成功建单 ${resp?.successCount ?? 0} 人`);
    }
    selectedRosterRows.value = [];
    await loadRoster();
  } finally {
    rosterEnrollLoading.value = false;
  }
}

async function handleSingleEnrollment(row: GroupRosterRowDto) {
  if (!row.rosterId) return;
  const batchPackage = requireBatchPackage();
  if (!batchPackage) return;
  await ElMessageBox.confirm(
    `将使用批次套餐「${batchPackage.pkgName}」为 ${row.fullName ?? ''} 建单，确认？`,
    '团检建单',
    { type: 'warning' },
  );
  const resp = await confirmBatchEnrollmentApi({
    rosterIds: [String(row.rosterId)],
    orgId: orgId.value || undefined,
  });
  if ((resp?.failures?.length ?? 0) > 0) {
    ElMessage.error(resp?.failures?.[0]?.message ?? '建单失败');
    return;
  }
  ElMessage.success(`${row.fullName ?? ''} 已建单`);
  await loadRoster();
}

async function handleCancelEnrollment(row: GroupRosterRowDto) {
  if (!row.rosterId) return;
  await ElMessageBox.confirm(
    `确认撤销 ${row.fullName ?? ''} 的建单？将作废本次体检就诊记录。`,
    '撤销建单',
    { type: 'warning' },
  );
  await cancelGroupEnrollmentApi({ rosterId: String(row.rosterId) });
  ElMessage.success('已撤销建单');
  await loadRoster();
}

async function handleConfirmArrival(row: GroupRosterRowDto) {
  if (!row.rosterId) return;
  await confirmGroupArrivalApi({ rosterId: String(row.rosterId) });
  ElMessage.success(`${row.fullName ?? ''} 已到检确认`);
  await loadRoster();
}

async function handleCancelArrival(row: GroupRosterRowDto) {
  if (!row.rosterId) return;
  await ElMessageBox.confirm(`确认撤销 ${row.fullName ?? ''} 的到检状态？`, '撤销到检', {
    type: 'warning',
  });
  await cancelGroupArrivalApi({ rosterId: String(row.rosterId) });
  ElMessage.success('已撤销到检');
  await loadRoster();
}

async function handleBatchArrival() {
  const rows = selectedRosterRows.value.filter(
    (row) =>
      isRosterEnrolled(row) && row.arrivalStatusCode !== ARRIVAL_STATUS.ARRIVED && row.rosterId,
  );
  if (rows.length === 0) {
    ElMessage.warning('请勾选已建单且未到检的人员');
    return;
  }
  await ElMessageBox.confirm(`确认 ${rows.length} 人到检？`, '批量到检', { type: 'warning' });
  let success = 0;
  for (const row of rows) {
    try {
      await confirmGroupArrivalApi({ rosterId: String(row.rosterId!) });
      success++;
    } catch (error) {
      console.warn('batch arrival failed', row.rosterId, error);
    }
  }
  ElMessage.success(`已到检确认 ${success} 人`);
  selectedRosterRows.value = [];
  await loadRoster();
}

async function handlePrintGuide(row: GroupRosterRowDto) {
  if (!row.encounterId) {
    ElMessage.warning('请先建单后再打印指引单');
    return;
  }
  await printPeisGuide({ encounterId: row.encounterId });
}

async function handleBatchPrintGuide() {
  const rows = selectedRosterRows.value.filter((row) => row.encounterId);
  if (rows.length === 0) {
    ElMessage.warning('请勾选已建单人员');
    return;
  }
  for (const row of rows) {
    await printPeisGuide({ encounterId: row.encounterId, background: true });
  }
  ElMessage.success(`已提交 ${rows.length} 份指引单打印`);
}

function onRosterFilterChange() {
  rosterPageNo.value = 1;
  loadRoster();
}

function onRosterPageChange(page: number) {
  rosterPageNo.value = page;
  loadRoster();
}

function onRosterSizeChange(size: number) {
  rosterPageSize.value = size;
  rosterPageNo.value = 1;
  loadRoster();
}

watch(selectedCompanyId, () => {
  selectedBatchId.value = '';
  rosterMemberKind.value = inferRosterMemberKind(selectedCompany.value);
  importForm.value.studentNo = '';
  importForm.value.employeeNo = '';
  void loadCampusPullOptions();
  loadContractsAndBatches();
});

watch(rosterMemberKind, (kind) => {
  if (kind === 'STUDENT') {
    importForm.value.employeeNo = '';
  } else {
    importForm.value.studentNo = '';
  }
});

watch(
  () => batchForm.value.contractId,
  () => {
    const allowed = batchContractPackages.value;
    if (
      batchForm.value.packageActivityId &&
      !allowed.some((item) => String(item.packageActivityId) === batchForm.value.packageActivityId)
    ) {
      batchForm.value.packageActivityId = '';
    }
    if (!batchForm.value.packageActivityId && allowed.length === 1) {
      batchForm.value.packageActivityId = String(allowed[0]?.packageActivityId ?? '');
    }
  },
);

watch(selectedBatchId, () => {
  rosterPageNo.value = 1;
  if (activeTab.value === 'roster') {
    loadRoster();
  }
});

watch(activeTab, (tab) => {
  if (tab === 'roster') {
    rosterPageNo.value = 1;
    loadRoster();
  }
});

onMounted(async () => {
  await Promise.all([loadCompanies(), loadPackages(), loadActivities()]);
  await loadContractsAndBatches();
  await loadCampusPullOptions();
});
</script>

<template>
  <Page auto-content-height content-class="group-check-page p-4">
    <ElCard shadow="never" class="page-intro mb-4">
      <div class="page-intro__title">团检筹备</div>
      <div class="page-intro__sub">
        按 Tab 分步维护：先建单位 → 合同与批次 → 导入名册。日常到检请在「体检登记」按人操作。
      </div>
    </ElCard>

    <ElTabs v-model="activeTab" class="group-check-tabs">
      <ElTabPane label="单位档案" name="company">
        <div class="split-grid">
          <ElCard shadow="never" class="panel">
            <div class="panel__head">单位列表</div>
            <p class="panel__hint">点击行可编辑，并设为「合同与批次 / 名册导入」的筹备单位</p>
            <ElTable
              :data="companies"
              stripe
              height="360"
              highlight-current-row
              @row-click="editCompany"
            >
              <ElTableColumn prop="name" label="单位名称" min-width="160" show-overflow-tooltip />
              <ElTableColumn prop="contactName" label="联系人" width="100" />
              <ElTableColumn prop="contactPhone" label="电话" width="120" />
              <ElTableColumn label="操作" width="80" fixed="right">
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="editCompany(row)">编辑</ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </ElCard>

          <ElCard shadow="never" class="panel">
            <div class="panel__head">
              {{ companyForm.companyId ? '编辑单位' : '新建单位' }}
            </div>
            <ElForm label-width="88px">
              <ElFormItem label="单位名称" required>
                <ElInput v-model="companyForm.name" placeholder="请输入单位名称" />
              </ElFormItem>
              <ElFormItem label="联系人">
                <ElInput v-model="companyForm.contactName" />
              </ElFormItem>
              <ElFormItem label="联系电话">
                <ElInput v-model="companyForm.contactPhone" />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" @click="handleSaveCompany">保存单位</ElButton>
                <ElButton @click="resetCompanyForm">清空</ElButton>
              </ElFormItem>
            </ElForm>
          </ElCard>
        </div>
      </ElTabPane>

      <ElTabPane label="合同与批次" name="contract-batch">
        <div class="scope-bar">
          <span class="scope-bar__label">本页筹备单位</span>
          <ElSelect
            v-model="selectedCompanyId"
            filterable
            clearable
            placeholder="选择要维护合同/批次的单位"
            class="scope-bar__select scope-bar__select--company"
          >
            <ElOption
              v-for="item in companies"
              :key="String(item.companyId)"
              :label="item.name"
              :value="String(item.companyId)"
            />
          </ElSelect>
          <ElButton
            link
            type="primary"
            :disabled="!selectedCompanyId"
            @click="loadContractsAndBatches"
          >
            刷新合同与批次
          </ElButton>
        </div>
        <div v-if="!selectedCompanyId" class="empty-wrap">
          <ElEmpty description="请在本页选择筹备单位，或在「单位档案」中点击单位行" />
        </div>
        <div v-else class="split-grid">
          <ElCard shadow="never" class="panel">
            <div class="panel__head">合同维护</div>
            <ElForm label-width="88px">
              <ElFormItem label="可选套餐" required>
                <ElSelect
                  v-model="contractForm.packageActivityIds"
                  multiple
                  filterable
                  placeholder="选择一个或多个体检套餐"
                  class="package-multi-select"
                  style="width: 100%"
                >
                  <ElOption
                    v-for="item in packages"
                    :key="String(item.packageActivityId)"
                    :label="packageDisplayName(item)"
                    :value="String(item.packageActivityId)"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="合同名称">
                <ElInput v-model="contractForm.contractName" placeholder="如 2026 教职工体检" />
              </ElFormItem>
              <ElFormItem label="合同金额" required>
                <div class="contract-amount-field">
                  <ElInputNumber
                    v-model="contractForm.contractAmount"
                    :min="0"
                    :precision="2"
                    :step="100"
                    controls-position="right"
                    placeholder="元"
                    class="contract-amount-field__input"
                  />
                  <ElButton
                    v-if="contractCatalogAmountHint"
                    link
                    type="primary"
                    @click="applyCatalogContractAmount"
                  >
                    填入目录价 {{ contractCatalogAmountHint }}
                  </ElButton>
                </div>
                <p v-if="contractCatalogAmountHint" class="field-hint">
                  所选套餐目录价合计
                  {{
                    contractCatalogAmountHint
                  }}，可按协议价调整；现场免收费建单，单位款项在此合同上后结确认
                </p>
              </ElFormItem>
              <ElFormItem label="有效期起">
                <ElDatePicker
                  v-model="contractForm.validFrom"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
              </ElFormItem>
              <ElFormItem label="有效期止">
                <ElDatePicker
                  v-model="contractForm.validTo"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" @click="handleSaveContract">保存合同</ElButton>
                <ElButton @click="resetContractForm">清空</ElButton>
              </ElFormItem>
            </ElForm>
            <ElTable
              :data="contracts"
              stripe
              class="mt-4"
              max-height="220"
              highlight-current-row
              @row-click="editContract"
            >
              <ElTableColumn prop="contractName" label="合同" min-width="120" />
              <ElTableColumn label="合同金额" width="110" align="right">
                <template #default="{ row }">
                  {{ formatContractAmount(row.contractAmount) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="支付状态" width="96" align="center">
                <template #default="{ row }">
                  <ElTag :type="contractPayStatusTagType(row.payStatusCode)" size="small">
                    {{ contractPayStatusLabel(row.payStatusCode) }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn label="可选套餐" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ packageLabels(row.packageActivityIds, packages) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <ElButton link type="primary" @click.stop="editContract(row)">编辑</ElButton>
                  <ElButton
                    v-if="row.payStatusCode !== CONTRACT_PAY_STATUS.PAID"
                    link
                    type="success"
                    @click.stop="handleMarkContractPaid(row)"
                  >
                    单位已收
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
          </ElCard>

          <ElCard shadow="never" class="panel">
            <div class="panel__head">批次维护</div>
            <ElForm label-width="88px">
              <ElFormItem label="合同" required>
                <ElSelect v-model="batchForm.contractId" placeholder="选择合同" style="width: 100%">
                  <ElOption
                    v-for="item in contracts"
                    :key="String(item.contractId)"
                    :label="`${item.contractName || item.contractId}${item.contractAmount != null ? ` · ${formatContractAmount(item.contractAmount)}` : ''}${item.payStatusCode === 'PAID' ? ' · 已收' : ''}`"
                    :value="String(item.contractId)"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="批次名称" required>
                <ElInput v-model="batchForm.batchName" placeholder="如 第一批" />
              </ElFormItem>
              <ElFormItem label="套餐" required>
                <ElSelect
                  v-model="batchForm.packageActivityId"
                  filterable
                  placeholder="从合同可选套餐中选择"
                  style="width: 100%"
                  :disabled="!batchForm.contractId"
                >
                  <ElOption
                    v-for="item in batchContractPackages"
                    :key="String(item.packageActivityId)"
                    :label="packageDisplayName(item)"
                    :value="String(item.packageActivityId)"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="预约日期">
                <ElDatePicker
                  v-model="batchForm.bookingDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" @click="handleSaveBatch">保存批次</ElButton>
              </ElFormItem>
            </ElForm>
            <ElTable :data="batches" stripe class="mt-4" max-height="220">
              <ElTableColumn prop="batchName" label="批次" min-width="120" />
              <ElTableColumn label="套餐" min-width="160">
                <template #default="{ row }">
                  {{ packageLabel(String(row.packageActivityId ?? ''), packages) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="bookingDate" label="预约日" width="110" />
              <ElTableColumn prop="statusCode" label="状态" width="90" />
            </ElTable>
          </ElCard>
        </div>
      </ElTabPane>

      <ElTabPane label="名册导入" name="roster">
        <ElAlert
          class="mb-4"
          type="info"
          show-icon
          :closable="false"
          title="日常到检登记请在「体检登记」按人操作；本页用于活动前导入名册与管理员批量补录。"
        />
        <div class="scope-bar">
          <span class="scope-bar__label">筹备单位</span>
          <ElSelect
            v-model="selectedCompanyId"
            filterable
            clearable
            placeholder="选择单位"
            class="scope-bar__select scope-bar__select--company"
          >
            <ElOption
              v-for="item in companies"
              :key="String(item.companyId)"
              :label="item.name"
              :value="String(item.companyId)"
            />
          </ElSelect>
          <span class="scope-bar__label">导入批次</span>
          <ElSelect
            v-model="selectedBatchId"
            filterable
            clearable
            placeholder="选择批次"
            class="scope-bar__select scope-bar__select--batch"
            :disabled="!selectedCompanyId"
          >
            <ElOption
              v-for="item in batches"
              :key="String(item.batchId)"
              :label="item.batchName"
              :value="String(item.batchId)"
            />
          </ElSelect>
          <span v-if="selectedBatchLabel" class="scope-bar__hint">{{ selectedBatchLabel }}</span>
        </div>
        <div v-if="!selectedBatchId" class="empty-wrap">
          <ElEmpty description="请先选择筹备单位与导入批次" />
        </div>
        <template v-else>
          <div class="stats-grid mb-4">
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">名册总数</div>
              <div class="stat-card__value">{{ rosterSummary?.totalCount ?? 0 }}</div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">已建单</div>
              <div class="stat-card__value text-primary">
                {{ rosterSummary?.enrolledCount ?? 0 }}
              </div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">未建单</div>
              <div class="stat-card__value">{{ rosterSummary?.unenrolledCount ?? 0 }}</div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">待到检</div>
              <div class="stat-card__value text-warning">
                {{ rosterSummary?.pendingArrivalCount ?? 0 }}
              </div>
            </ElCard>
            <ElCard shadow="never" class="stat-card">
              <div class="stat-card__label">已到检</div>
              <div class="stat-card__value text-success">
                {{ rosterSummary?.arrivedCount ?? 0 }}
              </div>
            </ElCard>
          </div>

          <ElCard shadow="never" class="panel mb-4">
            <div class="panel__head">模板导入</div>
            <div class="panel__toolbar mb-3 flex flex-wrap items-center gap-2">
              <ElButton @click="handleDownloadRosterTemplate('STUDENT')">下载学生模板</ElButton>
              <ElButton @click="handleDownloadRosterTemplate('STAFF')">下载教职工模板</ElButton>
              <ElButton @click="handleDownloadRosterTemplate('ENTERPRISE')"
                >下载企事业模板</ElButton
              >
              <ElButton
                type="primary"
                :loading="templateImportLoading"
                :disabled="!selectedBatchId"
                @click="handleTemplateImportClick"
              >
                上传模板导入
              </ElButton>
              <span class="text-xs text-gray-500">
                按上方「名册类型」选择对应模板；与手工录入共用「导入后自动建单」选项。
              </span>
            </div>
            <input
              ref="importTemplateInputRef"
              type="file"
              accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              class="hidden"
              @change="handleTemplateImportFileChange"
            />
          </ElCard>

          <ElCard shadow="never" class="panel mb-4">
            <div class="panel__head">从 Campus 拉取</div>
            <div class="panel__toolbar mb-2 flex flex-wrap items-center gap-2">
              <ElRadioGroup v-model="campusPullKind" size="small" @change="campusCandidates = []">
                <ElRadioButton value="STUDENT">学生</ElRadioButton>
                <ElRadioButton value="STAFF">教职工</ElRadioButton>
              </ElRadioGroup>
              <template v-if="campusPullKind === 'STUDENT'">
                <ElSelect
                  v-model="campusClassName"
                  clearable
                  placeholder="班级（空=全学院）"
                  class="w-44"
                >
                  <ElOption label="全学院" value="" />
                  <ElOption
                    v-for="name in campusClassNames"
                    :key="name"
                    :label="name"
                    :value="name"
                  />
                </ElSelect>
              </template>
              <template v-else>
                <ElSelect
                  v-model="campusDepartmentId"
                  clearable
                  placeholder="选择部门"
                  class="w-44"
                >
                  <ElOption
                    v-for="dept in campusDepartments"
                    :key="String(dept.departmentId ?? '')"
                    :label="dept.departmentName"
                    :value="String(dept.departmentId ?? '')"
                  />
                </ElSelect>
              </template>
              <ElButton :loading="campusPullLoading" @click="handlePreviewCampusRoster"
                >预览</ElButton
              >
              <ElButton
                type="primary"
                :loading="campusPullLoading"
                :disabled="!selectedBatchId || campusCandidates.length === 0"
                @click="handleImportCampusRoster"
              >
                {{
                  campusSelectedRows.length > 0
                    ? `导入选中 ${campusSelectedRows.length} 人`
                    : '导入全部'
                }}
              </ElButton>
            </div>
            <div class="mb-2 text-xs text-gray-500">
              仅拉取 Campus 中已绑定患者的在校学生/在职教职工；不写回 Campus，直接挂团检名册。
            </div>
            <ElTable
              v-if="campusCandidates.length > 0"
              :data="campusCandidates"
              size="small"
              max-height="240"
              @selection-change="handleCampusSelection"
            >
              <ElTableColumn type="selection" width="42" />
              <ElTableColumn prop="fullName" label="姓名" min-width="88" />
              <ElTableColumn label="编号" min-width="100">
                <template #default="{ row }">
                  {{ row.studentNo || row.employeeNo || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="班级/部门" min-width="100">
                <template #default="{ row }">
                  {{ row.className || row.departmentName || '—' }}
                </template>
              </ElTableColumn>
            </ElTable>
          </ElCard>

          <ElCard shadow="never" class="panel mb-4">
            <div class="panel__head">手工录入</div>
            <div class="panel__toolbar mb-2">
              <span class="mr-2 text-sm text-gray-600">名册类型</span>
              <ElRadioGroup v-model="rosterMemberKind" size="small">
                <ElRadioButton
                  v-for="item in ROSTER_MEMBER_KIND_OPTIONS"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </ElRadioButton>
              </ElRadioGroup>
            </div>
            <ElForm inline class="panel__toolbar roster-import-form">
              <ElFormItem label="姓名" required>
                <ElInput v-model="importForm.fullName" placeholder="必填" clearable class="w-28" />
              </ElFormItem>
              <ElFormItem label="性别">
                <ElSelect
                  v-model="importForm.genderEnum"
                  clearable
                  placeholder="可选"
                  class="gender-select"
                >
                  <ElOption
                    v-for="item in GENDER_OPTIONS"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </ElSelect>
              </ElFormItem>
              <ElFormItem label="身份证">
                <ElInput v-model="importForm.idCardNo" clearable class="w-44" />
              </ElFormItem>
              <ElFormItem label="手机号">
                <ElInput v-model="importForm.mobilePhone" clearable class="w-32" />
              </ElFormItem>
              <ElFormItem v-if="rosterMemberKind === 'STUDENT'" :label="rosterMemberNoFieldLabel">
                <ElInput v-model="importForm.studentNo" clearable class="w-28" />
              </ElFormItem>
              <ElFormItem v-else :label="rosterMemberNoFieldLabel">
                <ElInput v-model="importForm.employeeNo" clearable class="w-28" />
              </ElFormItem>
              <ElFormItem :label="rosterOrgUnitFieldLabel">
                <ElInput v-model="importForm.className" clearable class="w-28" />
              </ElFormItem>
              <ElFormItem>
                <ElButton type="primary" @click="handleImportRoster">导入一行</ElButton>
              </ElFormItem>
            </ElForm>
            <div class="panel__toolbar mb-2 flex flex-wrap items-center gap-4">
              <ElCheckbox v-model="importAutoEnroll">导入后自动建单（使用当前批次套餐）</ElCheckbox>
              <ElCheckbox v-if="rosterMemberKind === 'STAFF'" v-model="importSyncCampus">
                同步到 Campus 教职工档案
              </ElCheckbox>
            </div>
            <div class="roster-import-hint text-xs text-gray-500">
              患者档案由系统自动匹配或创建；身份证、手机号、{{
                rosterMemberNoFieldLabel
              }}至少填一项。
              <template v-if="rosterMemberKind === 'STAFF' && importSyncCampus">
                教职工类型将按工号写入 Campus 教职工表，部门须与 Campus 部门名称一致。
              </template>
              <template v-if="importAutoEnroll"
                >导入成功后将按批次套餐「{{
                  packageLabel(String(selectedBatch?.packageActivityId ?? ''), packages)
                }}」自动建单。</template
              >
            </div>
          </ElCard>

          <ElCard shadow="never" class="panel">
            <div class="panel__head">名册总览（管理员批量补录）</div>
            <div class="panel__toolbar flex flex-wrap items-center gap-2">
              <ElInput
                v-model="rosterKeyword"
                clearable
                placeholder="姓名/编号/身份证"
                class="w-48"
                @change="onRosterFilterChange"
              />
              <ElSelect
                v-model="rosterEnrollmentFilter"
                clearable
                placeholder="建单状态"
                class="w-32"
                @change="onRosterFilterChange"
              >
                <ElOption label="已建单" value="ENROLLED" />
                <ElOption label="未建单" value="UNENROLLED" />
              </ElSelect>
              <ElSelect
                v-model="rosterArrivalFilter"
                clearable
                placeholder="到检状态"
                class="w-32"
                @change="onRosterFilterChange"
              >
                <ElOption label="已到检" value="ARRIVED" />
                <ElOption label="待到检" value="PENDING" />
              </ElSelect>
              <ElButton
                type="primary"
                :loading="rosterEnrollLoading"
                @click="handleBatchEnrollment"
              >
                批量建单
              </ElButton>
              <ElButton @click="handleBatchArrival">批量到检</ElButton>
              <ElButton @click="handleBatchPrintGuide">批量打印指引单</ElButton>
            </div>
            <ElTable
              v-loading="rosterLoading"
              :data="rosterRows"
              stripe
              height="360"
              @selection-change="handleRosterSelection"
            >
              <ElTableColumn type="selection" width="48" />
              <ElTableColumn prop="fullName" label="姓名" min-width="90" fixed="left" />
              <ElTableColumn label="性别" width="64">
                <template #default="{ row }">
                  {{ genderLabel(row.genderEnum) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="idCardNo" label="身份证" min-width="150" show-overflow-tooltip />
              <ElTableColumn prop="mobilePhone" label="手机号" width="120" />
              <ElTableColumn label="编号" width="110">
                <template #default="{ row }">
                  {{ rosterMemberNo(row) }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="部门/班级" width="100" show-overflow-tooltip>
                <template #default="{ row }">
                  {{ row.className || '—' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="建单" width="100">
                <template #default="{ row }">
                  <ElTag :type="enrollmentTagType(row)" size="small">
                    {{ enrollmentLabel(row) }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn label="到检" width="90">
                <template #default="{ row }">
                  <ElTag :type="arrivalTagType(row.arrivalStatusCode)" size="small">
                    {{ arrivalLabel(row.arrivalStatusCode) }}
                  </ElTag>
                </template>
              </ElTableColumn>
              <ElTableColumn label="操作" width="360" fixed="right">
                <template #default="{ row }">
                  <ElButton
                    v-if="!isRosterEnrolled(row)"
                    link
                    type="primary"
                    size="small"
                    @click="handleSingleEnrollment(row)"
                  >
                    建单
                  </ElButton>
                  <ElButton
                    v-if="isRosterEnrolled(row)"
                    link
                    type="primary"
                    size="small"
                    @click="openAdjustDrawer(row)"
                  >
                    加/减项
                  </ElButton>
                  <ElButton
                    v-if="isRosterEnrolled(row) && row.payStatusCode === 'ENROLLED'"
                    link
                    type="warning"
                    size="small"
                    @click="handleCancelEnrollment(row)"
                  >
                    撤销建单
                  </ElButton>
                  <ElButton
                    v-if="isRosterEnrolled(row) && row.arrivalStatusCode !== ARRIVAL_STATUS.ARRIVED"
                    link
                    type="primary"
                    size="small"
                    @click="handleConfirmArrival(row)"
                  >
                    到检
                  </ElButton>
                  <ElButton
                    v-if="row.arrivalStatusCode === ARRIVAL_STATUS.ARRIVED"
                    link
                    type="warning"
                    size="small"
                    @click="handleCancelArrival(row)"
                  >
                    撤销到检
                  </ElButton>
                  <ElButton
                    v-if="row.encounterId"
                    link
                    type="primary"
                    size="small"
                    @click="handlePrintGuide(row)"
                  >
                    指引单
                  </ElButton>
                </template>
              </ElTableColumn>
            </ElTable>
            <div class="panel__pager">
              <ElPagination
                background
                layout="total, prev, pager, next"
                :total="rosterTotal"
                :current-page="rosterPageNo"
                :page-size="rosterPageSize"
                @current-change="onRosterPageChange"
                @size-change="onRosterSizeChange"
              />
            </div>
          </ElCard>
        </template>
      </ElTabPane>
    </ElTabs>

    <ElDrawer
      v-model="adjustDrawerOpen"
      :title="`${adjustRoster?.fullName || '团员'} · 加/减项`"
      size="480px"
    >
      <p v-if="adjustOrderDetail?.busNo" class="adjust-drawer__busno">
        流水号 {{ adjustOrderDetail.busNo }}
      </p>
      <p class="adjust-drawer__hint">套餐内项目免收费；加项需在「团检加项收费」缴纳。</p>
      <div v-loading="adjustLoading" class="adjust-drawer__body">
        <div
          v-for="line in adjustOrderLines"
          :key="String(line.serviceRequestId)"
          class="adjust-line"
        >
          <span class="adjust-line__name">{{ line.display || '项目' }}</span>
          <ElTag v-if="isAddOnLine(line)" size="small" type="warning" effect="plain">加项</ElTag>
          <ElTag size="small" type="info">{{ line.execStatusCode || 'pending' }}</ElTag>
          <ElButton
            link
            type="danger"
            :disabled="line.execStatusCode !== 'pending' && line.execStatusCode != null"
            @click="removeAdjustLine(line)"
          >
            减项
          </ElButton>
        </div>
        <ElEmpty v-if="adjustOrderLines.length === 0" description="暂无已开项目" :image-size="48" />
      </div>
      <div class="adjust-drawer__footer">
        <ElSelect
          v-model="addActivityId"
          filterable
          clearable
          placeholder="选择项目加项"
          class="adjust-drawer__select"
        >
          <ElOption
            v-for="opt in availableAddActivities"
            :key="String(opt.activityId)"
            :label="formatActivityOptionLabel(opt)"
            :value="String(opt.activityId)"
          />
        </ElSelect>
        <ElButton type="primary" plain :loading="adjustLoading" @click="addAdjustActivity">
          加项
        </ElButton>
      </div>
    </ElDrawer>
  </Page>
</template>

<style scoped>
.group-check-page {
  --panel-border: var(--el-border-color-lighter);
}

.context-card :deep(.el-card__body),
.page-intro :deep(.el-card__body) {
  padding: 16px 20px;
}

.page-intro__title {
  font-size: 16px;
  font-weight: 600;
}

.page-intro__sub {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.scope-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.scope-bar__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.scope-bar__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.scope-bar__select--company {
  width: min(280px, 100%);
}

.scope-bar__select--batch {
  width: min(200px, 100%);
}

.panel__hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.stat-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.stat-card__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-card__value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 600;
}

.text-primary {
  color: var(--el-color-primary);
}

.text-success {
  color: var(--el-color-success);
}

.text-warning {
  color: var(--el-color-warning);
}

.panel :deep(.el-card__body) {
  padding: 16px;
}

.panel__head {
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
}

.panel__toolbar {
  margin-bottom: 12px;
}

.panel__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.adjust-drawer__busno {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.adjust-drawer__hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.adjust-drawer__body {
  min-height: 200px;
}

.adjust-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.adjust-line__name {
  flex: 1;
  min-width: 120px;
  font-size: 14px;
}

.adjust-drawer__footer {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.adjust-drawer__select {
  flex: 1;
}

.toolbar-spacer {
  flex: 1;
}

.split-grid {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 16px;
}

.empty-wrap {
  padding: 48px 0;
}

.package-multi-select :deep(.el-select__tags) {
  max-width: 100%;
}

.package-multi-select :deep(.el-select__tags-text) {
  white-space: normal;
}

.hidden {
  display: none;
}

.gender-select {
  width: 88px;
}

.contract-amount-field {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.contract-amount-field__input {
  width: 160px;
}

.field-hint {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .split-grid {
    grid-template-columns: 1fr;
  }
}
</style>
