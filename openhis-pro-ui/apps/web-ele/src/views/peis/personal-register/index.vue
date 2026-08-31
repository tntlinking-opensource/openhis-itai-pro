<script setup lang="ts">
import type { PhysicalExamChargeItemRowDto } from '#/api/peis/physical-exam-charge';
import type { Patient } from '#/api/basic/patient';
import type { PeisActivityDto, PeisPackageDto } from '#/api/peis/catalog-config';
import type {
  CheckOrderDetailResponse,
  CustomerRowDto,
  PersonalRegistrationHistoryRow,
  ServiceRequestLineDto,
} from '#/api/peis/personal-registration';
import type { ApiOptionItem } from '#/types/api-response';

import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import {
  httpAdjustPhysicalExamChargePrices,
  httpLoadPhysicalExamChargeItems,
  type LoadPhysicalExamChargeItemsResponse,
} from '#/api/peis/physical-exam-charge';
import { patientInitApi } from '#/api/basic/patient';
import { searchActivityApi, searchPackageApi } from '#/api/peis/catalog-config';
import {
  confirmBatchEnrollmentApi,
  confirmGroupArrivalApi,
  listBatchesApi,
  listCompaniesApi,
  lookupRosterForDeskApi,
  type BatchRowDto,
  type CompanyRowDto,
  type GroupRosterDeskContextDto,
} from '#/api/peis/group-check';
import {
  checkArchiveCompletenessApi,
  loadArchiveApi,
  saveArchiveApi,
  type ArchiveCompletenessDto,
} from '#/api/peis/health-archive';
import {
  adjustServiceRequestsApi,
  createRegistrationApi,
  getChargeLaunchContextApi,
  getChargeStatusApi,
  getCheckOrderDetailApi,
  listActivePersonalRegistrationsApi,
  pagePersonalRegistrationsApi,
  searchCustomerApi,
} from '#/api/peis/personal-registration';
import { printPeisGuide } from '#/utils/print/print-peis-guide';
import { getDateRangeQuick } from '#/utils/format/date-range-quick';
import {
  formatMoneyYuanWithSymbol,
  scaleMoneySettle,
  sumMoneyThenCalc,
  sumMoneyThenSettle,
} from '#/utils/format/money-scale';
import { extractRequestErrorMessage } from '#/utils/http/stale-cross-role-error';
import {
  ARRIVAL_STATUS,
  arrivalLabel,
  arrivalTagType,
  enrollmentLabel,
  enrollmentTagType,
  isRosterEnrolled,
  packageDisplayName,
} from '#/views/peis/group-check/constants';
import EditDialog from '#/views/basic/patient/edit-dialog.vue';
import PeisChargePriceAdjustDialog from '#/components/peis/peis-charge-price-adjust-dialog.vue';
import {
  ElButton,
  ElDatePicker,
  ElAlert,
  ElDialog,
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
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

defineOptions({ name: 'PeisPersonalRegistration' });

const router = useRouter();
const userStore = useUserStore();

const customerKeyword = ref('');
const customerLoading = ref(false);
const customerRows = ref<CustomerRowDto[]>([]);
const selectedCustomer = ref<CustomerRowDto | null>(null);
const customerPickerOpen = ref(true);

const patientDialogVisible = ref(false);
const patientDialogForm = ref<null | Partial<Patient>>(null);
const sexOptions = ref<ApiOptionItem[]>([]);
const certTypeOptions = ref<ApiOptionItem[]>([]);
const maritalStatusOptions = ref<ApiOptionItem[]>([]);
const bloodTypeAboOptions = ref<ApiOptionItem[]>([]);
const bloodTypeRhOptions = ref<ApiOptionItem[]>([]);
const educationLevelOptions = ref<ApiOptionItem[]>([]);

const packageKeyword = ref('');
const packageLoading = ref(false);
const packages = ref<PeisPackageDto[]>([]);
const selectedPackage = ref<PeisPackageDto | null>(null);
/** 体检类型：默认个人体检 */
const examKind = ref('personal');
const companyId = ref('');
const groupBatchId = ref('');
const referrerName = ref('');
const breakfastFlag = ref<boolean | null>(null);
const companyOptions = ref<CompanyRowDto[]>([]);
const batchOptions = ref<BatchRowDto[]>([]);
const companyLoading = ref(false);
const batchLoading = ref(false);
const groupRosterMatches = ref<GroupRosterDeskContextDto[]>([]);
const groupRosterContext = ref<GroupRosterDeskContextDto | null>(null);
const groupRosterLoading = ref(false);
const groupDeskLoading = ref(false);
const archiveCompleteness = ref<ArchiveCompletenessDto | null>(null);
const archiveDialogVisible = ref(false);
const historyDrawerOpen = ref(false);
const summaryDrawerOpen = ref(false);
const archiveSaving = ref(false);
const archiveForm = ref({
  pastHistory: '',
  allergySummary: '',
  familyHistory: '',
  habitSummary: '',
  remark: '',
});

const activityMap = ref<Map<string, PeisActivityDto>>(new Map());
const activityLoading = ref(false);

const orgIdInput = ref('');
const registering = ref(false);

const currentEncounterId = ref('');
const orderDetail = ref<CheckOrderDetailResponse | null>(null);
const chargeItemsDetail = ref<LoadPhysicalExamChargeItemsResponse | null>(null);
/** 当前客户在检体检单 */
const activeOrders = ref<PersonalRegistrationHistoryRow[]>([]);
const activeOrdersLoading = ref(false);
/** 个检套餐整单 ChargeItem 锚点表名 */
const SERVICE_TABLE_ACTIVITY_DEFINITION = 'wkf_activity_definition';
const priceAdjustVisible = ref(false);
const priceAdjustRow = ref<PhysicalExamChargeItemRowDto | null>(null);
const packageAdjustVisible = ref(false);
const packageAdjustAmount = ref(0);
const packageAdjustLoading = ref(false);
const orderLoading = ref(false);
const chargePaid = ref<boolean | null>(null);
const chargeUnknown = ref(false);
const orderPanelRef = ref<HTMLElement | null>(null);

const addActivityId = ref('');

const historyKeyword = ref('');
const historyDateRange = ref<[string, string]>(
  getDateRangeQuick({ strategy: 'calendar-day' }) as [string, string],
);
const historyQuick = ref<'day' | 'week' | 'month'>('day');
const historyLoading = ref(false);
const historyRows = ref<PersonalRegistrationHistoryRow[]>([]);
const historyTotal = ref(0);
const historyPageNo = ref(1);
const historyPageSize = ref(10);

const genderLabel: Record<string, string> = {
  male: '男',
  female: '女',
  unknown: '未知',
  MALE: '男',
  FEMALE: '女',
  UNKNOWN: '未知',
  '1': '男',
  '2': '女',
  '0': '未知',
};

const execStatusLabel: Record<string, string> = {
  pending: '待检',
  checked: '已检',
  abandoned: '弃检',
  deferred: '延检',
};

const hasLoginOrg = computed(() => {
  const fromUser = userStore.userInfo?.organizationId;
  return fromUser != null && String(fromUser).trim().length > 0;
});

const defaultOrgId = computed(() => {
  if (hasLoginOrg.value) {
    return String(userStore.userInfo!.organizationId);
  }
  return orgIdInput.value.trim();
});

const packageChildPreview = computed(() => {
  const ids = selectedPackage.value?.childActivityIds ?? [];
  return ids.map((id, index) => {
    const key = String(id);
    const hit = activityMap.value.get(key);
    return {
      index: index + 1,
      activityId: key,
      display: hit?.display || '未知项目',
      code: hit?.code || key,
      priceLabel: formatActivityPriceLabel(hit),
    };
  });
});

const filteredPackages = computed(() => {
  const kw = packageKeyword.value.trim().toLowerCase();
  if (!kw) {
    return packages.value;
  }
  return packages.value.filter((p) => {
    const text = `${p.display || ''} ${p.code || ''}`.toLowerCase();
    return text.includes(kw);
  });
});

const availableAddActivities = computed(() => {
  const selected = new Set(
    (orderDetail.value?.serviceRequestLines ?? [])
      .map((l) => String(l.activityId ?? ''))
      .filter(Boolean),
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

const orderLines = computed(() => orderDetail.value?.serviceRequestLines ?? []);

/** 登记套餐原始子项（用于识别加项行） */
const packageBaselineActivityIds = computed(() => {
  const pkgId = orderDetail.value?.packageActivityId ?? selectedPackage.value?.packageActivityId;
  if (pkgId == null) return new Set<string>();
  const pkg = packages.value.find((item) => String(item.packageActivityId) === String(pkgId));
  return new Set((pkg?.childActivityIds ?? []).map((id) => String(id)));
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

const addOnLines = computed(() => orderLines.value.filter((line) => isAddOnLine(line)));

const chargeItemByServiceRequestId = computed(() => {
  const map = new Map<string, PhysicalExamChargeItemRowDto>();
  for (const item of chargeItemsDetail.value?.items ?? []) {
    if (item.serviceRequestId) {
      map.set(String(item.serviceRequestId), item);
    }
  }
  return map;
});

const addOnAmountTotal = computed(() => {
  let sum = 0;
  for (const line of addOnLines.value) {
    const chargeItem = chargeItemByServiceRequestId.value.get(String(line.serviceRequestId ?? ''));
    const price = Number(chargeItem?.totalPrice ?? activityById(line.activityId)?.salePrice ?? 0);
    if (!Number.isNaN(price)) sum += price;
  }
  return sum;
});

const selectedAddActivity = computed(() =>
  addActivityId.value ? activityById(addActivityId.value) : undefined,
);

const registered = computed(() => !!orderDetail.value?.encounterId);

const isGroupExam = computed(() => examKind.value === 'group');

const isGroupRosterDesk = computed(() => groupRosterContext.value != null);

const groupPackageName = computed(() => {
  const ctx = groupRosterContext.value;
  if (!ctx?.packageActivityId) return '—';
  const hit = packages.value.find(
    (item) => String(item.packageActivityId) === String(ctx.packageActivityId),
  );
  return hit ? packageDisplayName(hit) : '团检套餐';
});

const canGroupEnroll = computed(() => {
  const ctx = groupRosterContext.value;
  return (
    !!ctx?.rosterId &&
    !ctx.enrolled &&
    !!selectedCustomer.value?.patientId &&
    !groupDeskLoading.value
  );
});

const canGroupArrival = computed(() => {
  const ctx = groupRosterContext.value;
  return (
    !!ctx?.rosterId &&
    !!ctx.enrolled &&
    ctx.arrivalStatusCode !== ARRIVAL_STATUS.ARRIVED &&
    !groupDeskLoading.value
  );
});

const canRegister = computed(() => {
  if (isGroupRosterDesk.value) {
    return false;
  }
  if (groupRosterMatches.value.length > 0) {
    return false;
  }
  if (
    !selectedCustomer.value?.patientId ||
    !selectedPackage.value?.packageActivityId ||
    !defaultOrgId.value ||
    registering.value
  ) {
    return false;
  }
  return true;
});

const registerBlockReason = computed(() => {
  if (isGroupRosterDesk.value) {
    return '';
  }
  if (groupRosterMatches.value.length > 0) {
    return '检测到团检名册，请从上方选择批次后使用「团检建单」';
  }
  if (!selectedCustomer.value?.patientId) return '请先选择客户';
  if (!selectedPackage.value?.packageActivityId) return '请先选择体检套餐';
  if (!defaultOrgId.value) return '请填写体检机构';
  return '';
});

const currentStep = computed(() => {
  if (registered.value) return 3;
  if (selectedCustomer.value && selectedPackage.value) return 2;
  if (selectedCustomer.value) return 1;
  return 0;
});

/** 登记台收费状态标签：未登记 / 加项待收 / 已结算 / 未结算金额。 */
const chargeLabel = computed(() => {
  if (!registered.value) {
    return { text: '未登记', type: 'info' as const };
  }
  if (chargeUnknown.value) {
    return { text: '未知', type: 'info' as const };
  }
  // 个检有加项待收时优先提示加项金额
  if (
    !isGroupRosterDesk.value &&
    addOnLines.value.length > 0 &&
    formatPackagePrice(addOnAmountTotal.value)
  ) {
    return {
      text: `加项待收 ${formatPackagePrice(addOnAmountTotal.value)}`,
      type: 'warning' as const,
    };
  }
  if (chargePaid.value) {
    return { text: '已结算', type: 'success' as const };
  }
  const planned = formatPackagePrice(packageChargeTotal.value);
  if (planned) {
    return { text: `未结算 ${planned}`, type: 'warning' as const };
  }
  return { text: '未结算', type: 'warning' as const };
});

/** 登记信息抽屉：档案提示级别对应 Alert 类型。 */
const archiveAlertType = computed(() => {
  const level = archiveCompleteness.value?.level;
  if (level === 'block') {
    return 'error';
  }
  if (level === 'warn') {
    return 'warning';
  }
  return 'success';
});

/** 抽屉套餐展示名。 */
const summaryPackageName = computed(() => {
  return selectedPackage.value?.display || groupPackageName.value || '未选套餐';
});

/** 抽屉项目数：已登记用已开项，否则用当前套餐含项。 */
const summaryItemCount = computed(() => {
  if (registered.value) {
    return orderLines.value.length;
  }
  return packageChildPreview.value.length;
});

function labelGender(code?: string) {
  if (!code) return '—';
  return genderLabel[code] || genderLabel[code.toLowerCase()] || code;
}

function labelExec(code?: string) {
  if (!code) return '—';
  return execStatusLabel[code] || code;
}

function execTagType(code?: string) {
  if (code === 'checked') return 'success';
  if (code === 'abandoned') return 'danger';
  if (code === 'deferred') return 'warning';
  return 'info';
}

function packageItemCount(pkg: PeisPackageDto) {
  return (pkg.childActivityIds ?? []).length;
}

/**
 * 套餐/收费金额展示：空值返回空串，有值带 ¥（结算 2 位，仅展示）。
 *
 * @param value SALE 或收费金额
 */
function formatPackagePrice(value?: number | string | null) {
  // 无金额不展示，避免套餐列表出现 ¥0.00 占位
  if (value == null || value === '') {
    return '';
  }
  const num = Number(value);
  if (Number.isNaN(num)) {
    return '';
  }
  return formatMoneyYuanWithSymbol(num);
}

/**
 * 左侧套餐列表价：优先套餐 SALE，无定价时回落子项目录价合计。
 *
 * @param pkg 套餐行
 */
function packageSaleAmount(pkg?: PeisPackageDto | null) {
  if (!pkg) {
    return null;
  }
  if (pkg.salePrice != null && pkg.salePrice !== '') {
    return pkg.salePrice;
  }
  const ids = pkg.childActivityIds ?? [];
  const childPrices: Array<number | string> = [];
  for (const id of ids) {
    const act = activityMap.value.get(String(id));
    const num = Number(act?.salePrice);
    if (Number.isNaN(num)) {
      continue;
    }
    childPrices.push(num);
  }
  if (childPrices.length === 0) {
    return null;
  }
  return sumMoneyThenCalc(childPrices);
}

/**
 * 项目价签：免费 / 目录价 / 暂无定价。
 *
 * @param act 体检项目
 */
function formatActivityPriceLabel(act?: PeisActivityDto | null) {
  if (!act) {
    return '';
  }
  if (act.pricingFlag === false) {
    return '免费';
  }
  const price = formatPackagePrice(act.salePrice);
  if (price) {
    return price;
  }
  return '暂无定价';
}

/**
 * 加项下拉选项文案（名称 + 价格）。
 *
 * @param act 体检项目
 */
function formatActivityOptionLabel(act: PeisActivityDto) {
  const name = `${act.display || '项目'}（${act.code || act.activityId || ''}）`;
  const price = formatActivityPriceLabel(act);
  if (price) {
    return `${name} · ${price}`;
  }
  return name;
}

/**
 * 已开项目行价：套餐内显示「含在套餐」，加项用收费项单价，否则回落目录价。
 *
 * @param line 服务申请行
 */
function linePriceLabel(line: ServiceRequestLineDto) {
  // 套餐内子项不再单独挂费
  if (!isAddOnLine(line)) {
    return '含在套餐';
  }
  const chargeItem = chargeItemByServiceRequestId.value.get(String(line.serviceRequestId ?? ''));
  if (chargeItem?.unitPrice != null && chargeItem.unitPrice !== '') {
    const charged = formatPackagePrice(chargeItem.unitPrice);
    if (charged) {
      return charged;
    }
    return '';
  }
  return formatActivityPriceLabel(activityById(line.activityId));
}

/**
 * 按服务申请取已挂收费项。
 *
 * @param line 服务申请行
 */
function chargeItemForLine(line: ServiceRequestLineDto) {
  return chargeItemByServiceRequestId.value.get(String(line.serviceRequestId ?? ''));
}

/**
 * 仅加项且待执行、已挂费可改价时显示「改价」。
 *
 * @param line 服务申请行
 */
function canAdjustLinePrice(line: ServiceRequestLineDto) {
  if (chargePaid.value) {
    return false;
  }
  if (!isAddOnLine(line)) {
    return false;
  }
  const item = chargeItemForLine(line);
  return !!item?.priceEditable && line.execStatusCode === 'pending';
}

/** 个检套餐整单 ChargeItem（锚 AD，0 或 1 条）。 */
const packageChargeItem = computed(() => {
  for (const item of chargeItemsDetail.value?.items ?? []) {
    if (item.serviceTable === SERVICE_TABLE_ACTIVITY_DEFINITION) {
      return item;
    }
  }
  return null;
});

/** 已开项目区展示的套餐名：登记选中套餐优先，其次收费回读名。 */
const openedPackageName = computed(() => {
  if (selectedPackage.value) {
    return packageDisplayName(selectedPackage.value);
  }
  const chargeName = (chargeItemsDetail.value?.packageName ?? '').trim();
  if (chargeName) {
    return chargeName;
  }
  if (isGroupRosterDesk.value) {
    return groupPackageName.value;
  }
  return '';
});

/** 已开项目套餐行金额：优先套餐整单单价，否则回落目录 SALE。 */
const packageLineAmount = computed(() => {
  const item = packageChargeItem.value;
  if (item) {
    const raw = item.unitPrice ?? item.totalPrice;
    if (raw != null && raw !== '') {
      return raw;
    }
  }
  return packageSaleAmount(selectedPackage.value);
});

/** 已登记且能解析出套餐名时，在已开项目顶部展示套餐行。 */
const showOpenedPackageRow = computed(() => {
  if (!registered.value && !isGroupRosterDesk.value) {
    return false;
  }
  const name = openedPackageName.value;
  if (!name || name === '—') {
    return false;
  }
  return true;
});

/** 已开项目空态文案：无在检单时也给出占位说明。 */
const openedItemsEmptyText = computed(() => {
  if (!selectedCustomer.value) {
    return '请先检索并选择客户';
  }
  if (activeOrdersLoading.value) {
    return '正在查询在检体检单…';
  }
  if (!registered.value && activeOrders.value.length === 0) {
    return '当前客户无在检体检单，确认登记后在此显示项目';
  }
  return '登记成功后在此加/减项';
});

/** 套餐应收展示：优先后端 plannedTotal，否则行金额加总后 settle。 */
const packageChargeTotal = computed(() => {
  if (chargeItemsDetail.value?.plannedTotalAmount != null) {
    const planned = Number(chargeItemsDetail.value.plannedTotalAmount);
    if (!Number.isNaN(planned)) {
      return planned;
    }
  }
  const values: Array<number | string> = [];
  for (const item of chargeItemsDetail.value?.items ?? []) {
    const raw = item.totalPrice ?? item.unitPrice;
    if (raw != null && raw !== '') {
      values.push(raw);
    }
  }
  return sumMoneyThenSettle(values);
});

/** 个检、未结算且套餐整单待收费时允许套餐改价。 */
const canAdjustPackagePrice = computed(
  () =>
    registered.value &&
    !isGroupRosterDesk.value &&
    !chargePaid.value &&
    !!packageChargeItem.value?.priceEditable,
);

/**
 * 打开已开项目单项改价。
 *
 * @param line 服务申请行
 */
function openLinePriceAdjust(line: ServiceRequestLineDto) {
  const item = chargeItemForLine(line);
  if (!item) {
    return;
  }
  priceAdjustRow.value = item;
  priceAdjustVisible.value = true;
}

/** 打开套餐改价：预填套餐整单当前单价（结算 2 位）。 */
function openPackagePriceAdjust() {
  if (!canAdjustPackagePrice.value) {
    return;
  }
  const packageItem = packageChargeItem.value;
  packageAdjustAmount.value = scaleMoneySettle(
    packageItem?.unitPrice ?? packageItem?.totalPrice ?? 0,
  );
  packageAdjustVisible.value = true;
}

/** 提交套餐议价：只改套餐 AD 那一条 ChargeItem 单价。 */
async function submitPackagePriceAdjust() {
  if (!currentEncounterId.value || packageAdjustAmount.value < 0) {
    ElMessage.warning('套餐金额不能为负');
    return;
  }
  const packageItem = packageChargeItem.value;
  if (!packageItem) {
    return;
  }
  packageAdjustLoading.value = true;
  try {
    await httpAdjustPhysicalExamChargePrices({
      encounterId: currentEncounterId.value,
      items: [
        {
          chargeItemId: String(packageItem.chargeItemId),
          unitPrice: packageAdjustAmount.value,
          overrideReasonCode: 'PEIS_PACKAGE_PRICE',
          overrideReasonText: '套餐议价',
        },
      ],
    });
    ElMessage.success('套餐改价成功，请重新预结算后再收费');
    packageAdjustVisible.value = false;
    await handlePriceAdjustSuccess();
  } catch (error) {
    ElMessage.error(extractRequestErrorMessage(error, '套餐改价失败'));
  } finally {
    packageAdjustLoading.value = false;
  }
}

/**
 * 刷新就诊下收费明细（改价后回读）。
 *
 * @param encounterId 就诊主键
 */
async function refreshChargeItems(encounterId: string) {
  try {
    chargeItemsDetail.value = await httpLoadPhysicalExamChargeItems({ encounterId });
  } catch {
    chargeItemsDetail.value = null;
  }
}

/** 单项/套餐改价成功后回读收费明细。 */
async function handlePriceAdjustSuccess() {
  if (currentEncounterId.value) {
    await refreshChargeItems(currentEncounterId.value);
  }
}

async function searchCustomers() {
  const raw = customerKeyword.value.trim();
  if (!raw) {
    ElMessage.warning('请输入身份证号或姓名');
    return;
  }
  customerLoading.value = true;
  customerPickerOpen.value = true;
  try {
    const isIdCard = /^\d{15}(\d{2}[\dXx])?$/.test(raw);
    const res = await searchCustomerApi({
      idCardNo: isIdCard ? raw : undefined,
      nameKeyword: isIdCard ? undefined : raw,
      pageNo: 1,
      pageSize: 20,
    });
    customerRows.value = res.rows ?? [];
    if (customerRows.value.length === 0) {
      ElMessage.info('未找到客户，可点「新建患者」建档');
      return;
    }
    if (customerRows.value.length === 1) {
      pickCustomer(customerRows.value[0]!);
      ElMessage.success(`已选中：${customerRows.value[0]!.name}`);
    }
  } finally {
    customerLoading.value = false;
  }
}

function pickCustomer(row: CustomerRowDto) {
  clearRegistrationSession();
  selectedCustomer.value = row;
  customerPickerOpen.value = false;
  void loadArchiveCompleteness(row.patientId);
  void resolveGroupRoster(row.patientId);
  void loadActiveOrders(row.patientId);
}

function clearGroupRosterContext() {
  groupRosterMatches.value = [];
  groupRosterContext.value = null;
}

/** 清空当前登记会话（套餐/团检上下文/已开项目），切换患者前调用 */
function clearRegistrationSession() {
  selectedPackage.value = null;
  examKind.value = 'personal';
  companyId.value = '';
  groupBatchId.value = '';
  referrerName.value = '';
  breakfastFlag.value = null;
  batchOptions.value = [];
  clearGroupRosterContext();
  currentEncounterId.value = '';
  orderDetail.value = null;
  chargeItemsDetail.value = null;
  activeOrders.value = [];
  chargePaid.value = null;
  chargeUnknown.value = false;
  addActivityId.value = '';
}

async function resolveGroupRoster(patientId?: string | number) {
  clearGroupRosterContext();
  if (patientId == null || patientId === '') return;
  groupRosterLoading.value = true;
  try {
    const resp = await lookupRosterForDeskApi({ patientId: String(patientId) });
    const matches = resp?.matches ?? [];
    groupRosterMatches.value = matches;
    if (matches.length === 1) {
      applyGroupRosterContext(matches[0]!);
    } else if (matches.length > 1) {
      examKind.value = 'group';
      if (companyOptions.value.length === 0) {
        await loadCompanies();
      }
    }
  } catch (error) {
    clearGroupRosterContext();
    ElMessage.warning('团检名册查询失败，请稍后重试');
    console.warn('lookupRosterForDesk failed', error);
  } finally {
    groupRosterLoading.value = false;
  }
}

function applyGroupRosterContext(ctx: GroupRosterDeskContextDto) {
  groupRosterContext.value = ctx;
  examKind.value = 'group';
  if (ctx.companyId != null) {
    companyId.value = String(ctx.companyId);
  }
  if (ctx.batchId != null) {
    groupBatchId.value = String(ctx.batchId);
  }
  if (companyOptions.value.length === 0) {
    void loadCompanies();
  }
  if (companyId.value) {
    void loadBatches(companyId.value);
  }
  if (ctx.packageActivityId != null) {
    const pkg = packages.value.find(
      (item) => String(item.packageActivityId) === String(ctx.packageActivityId),
    );
    if (pkg) {
      selectedPackage.value = pkg;
    }
  }
  if (ctx.encounterId != null) {
    currentEncounterId.value = String(ctx.encounterId);
    void refreshOrderDetail(String(ctx.encounterId));
  } else {
    orderDetail.value = null;
    currentEncounterId.value = '';
    chargePaid.value = null;
    chargeUnknown.value = false;
  }
}

function onGroupRosterPick(rosterId: string) {
  const hit = groupRosterMatches.value.find((item) => String(item.rosterId) === rosterId);
  if (hit) {
    applyGroupRosterContext(hit);
  }
}

async function handleGroupEnroll() {
  const ctx = groupRosterContext.value;
  if (!ctx?.rosterId) return;
  groupDeskLoading.value = true;
  try {
    const resp = await confirmBatchEnrollmentApi({
      rosterIds: [String(ctx.rosterId)],
      orgId: defaultOrgId.value || undefined,
    });
    if ((resp?.failures?.length ?? 0) > 0) {
      ElMessage.error(resp?.failures?.[0]?.message ?? '团检建单失败');
      return;
    }
    ElMessage.success('团检建单成功');
    if (selectedCustomer.value?.patientId != null) {
      await resolveGroupRoster(selectedCustomer.value.patientId);
    }
    void loadHistory();
  } finally {
    groupDeskLoading.value = false;
  }
}

async function handleGroupArrival() {
  const ctx = groupRosterContext.value;
  if (!ctx?.rosterId) return;
  groupDeskLoading.value = true;
  try {
    await confirmGroupArrivalApi({ rosterId: String(ctx.rosterId) });
    ElMessage.success('到检确认成功');
    if (selectedCustomer.value?.patientId != null) {
      await resolveGroupRoster(selectedCustomer.value.patientId);
    }
  } finally {
    groupDeskLoading.value = false;
  }
}

async function handleGroupPrintGuide() {
  const encounterId = groupRosterContext.value?.encounterId ?? currentEncounterId.value;
  if (!encounterId) {
    ElMessage.warning('请先完成团检建单');
    return;
  }
  await printPeisGuide({ encounterId });
}

function changeCustomer() {
  selectedCustomer.value = null;
  customerPickerOpen.value = true;
  customerRows.value = [];
  customerKeyword.value = '';
  archiveCompleteness.value = null;
  clearRegistrationSession();
}

async function loadArchiveCompleteness(patientId?: string | number) {
  archiveCompleteness.value = null;
  if (patientId == null || patientId === '') return;
  try {
    const res = await checkArchiveCompletenessApi({ patientId });
    archiveCompleteness.value = res.completeness || null;
  } catch {
    // Campus 未启用或非学生时忽略
  }
}

async function openArchiveDialog() {
  const pid = selectedCustomer.value?.patientId;
  if (pid == null) {
    ElMessage.warning('请先选择客户');
    return;
  }
  archiveDialogVisible.value = true;
  try {
    const res = await loadArchiveApi({ patientId: pid, includeCompleteness: true });
    archiveForm.value = {
      pastHistory: res.pastHistory || '',
      allergySummary: res.allergySummary || '',
      familyHistory: res.familyHistory || '',
      habitSummary: res.habitSummary || '',
      remark: res.remark || '',
    };
    if (res.completeness) {
      archiveCompleteness.value = res.completeness;
    }
  } catch {
    archiveForm.value = {
      pastHistory: '',
      allergySummary: '',
      familyHistory: '',
      habitSummary: '',
      remark: '',
    };
  }
}

async function saveArchiveDialog() {
  const pid = selectedCustomer.value?.patientId;
  if (pid == null) return;
  archiveSaving.value = true;
  try {
    await saveArchiveApi({
      patientId: pid,
      orgId: defaultOrgId.value || undefined,
      pastHistory: archiveForm.value.pastHistory.trim() || undefined,
      allergySummary: archiveForm.value.allergySummary.trim() || undefined,
      familyHistory: archiveForm.value.familyHistory.trim() || undefined,
      habitSummary: archiveForm.value.habitSummary.trim() || undefined,
      remark: archiveForm.value.remark.trim() || undefined,
    });
    ElMessage.success('健康档案已保存');
    archiveDialogVisible.value = false;
    await loadArchiveCompleteness(pid);
  } finally {
    archiveSaving.value = false;
  }
}

function openCreatePatient() {
  const raw = customerKeyword.value.trim();
  const isIdCard = /^\d{15}(\d{2}[\dXx])?$/.test(raw);
  patientDialogForm.value = raw ? (isIdCard ? { idCard: raw } : { name: raw }) : null;
  patientDialogVisible.value = true;
}

function patientToCustomerRow(patient: Patient): CustomerRowDto {
  return {
    patientId: patient.id,
    name: patient.name,
    idCardNo: patient.idCard,
    genderCode: patient.genderEnum,
    phone: patient.phone,
  };
}

function onPatientCreated(patient?: Patient) {
  if (!patient?.id) {
    ElMessage.warning('建档成功，请重新检索客户');
    return;
  }
  const row = patientToCustomerRow(patient);
  customerRows.value = [row];
  pickCustomer(row);
  if (patient.name) {
    customerKeyword.value = patient.name;
  }
}

async function loadPatientInit() {
  try {
    const initRes = await patientInitApi();
    const allCertTypes = initRes?.identityDocumentType ?? [];
    certTypeOptions.value = allCertTypes.filter(
      (opt) => opt.value !== null && String(opt.value) !== '1',
    );
    maritalStatusOptions.value = initRes?.maritalStatus ?? [];
    bloodTypeAboOptions.value = initRes?.bloodTypeABO ?? [];
    bloodTypeRhOptions.value = initRes?.bloodTypeRH ?? [];
    sexOptions.value = initRes?.sex ?? [];
    educationLevelOptions.value = initRes?.educationLevelOptions ?? [];
  } catch (error) {
    console.error('患者建档初始化失败:', error);
  }
}

async function loadPackagesAndActivities() {
  packageLoading.value = true;
  activityLoading.value = true;
  try {
    const [pkgRes, actRes] = await Promise.all([
      searchPackageApi({ pageNo: 1, pageSize: 200, statusEnum: 'active' }),
      searchActivityApi({ pageNo: 1, pageSize: 500, statusEnum: 'active' }),
    ]);
    packages.value = pkgRes.content ?? [];
    const map = new Map<string, PeisActivityDto>();
    for (const row of actRes.content ?? []) {
      if (row.activityId != null) {
        map.set(String(row.activityId), row);
      }
    }
    activityMap.value = map;
  } finally {
    packageLoading.value = false;
    activityLoading.value = false;
  }
}

function pickPackage(pkg: PeisPackageDto) {
  selectedPackage.value = pkg;
}

async function handleRegister() {
  if (!canRegister.value) {
    ElMessage.warning(registerBlockReason.value || '无法登记');
    return;
  }
  registering.value = true;
  try {
    const res = await createRegistrationApi({
      patientId: String(selectedCustomer.value!.patientId),
      packageActivityId: String(selectedPackage.value!.packageActivityId),
      orgId: defaultOrgId.value,
      examKind: isGroupRosterDesk.value ? 'group' : examKind.value,
      referrerName: referrerName.value.trim() || undefined,
      breakfastFlag: breakfastFlag.value,
    });
    ElMessage.success(`登记成功，流水号 ${res.busNo || res.encounterId}`);
    if (res.encounterId != null) {
      currentEncounterId.value = String(res.encounterId);
      await refreshOrderDetail(String(res.encounterId));
      await nextTick();
      orderPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    void loadHistory();
    if (selectedCustomer.value?.patientId != null) {
      void loadActiveOrders(selectedCustomer.value.patientId, false);
    }
  } finally {
    registering.value = false;
  }
}

async function refreshOrderDetail(encounterId: string) {
  if (!encounterId) return;
  orderLoading.value = true;
  try {
    orderDetail.value = await getCheckOrderDetailApi({ encounterId });
    currentEncounterId.value = String(orderDetail.value.encounterId ?? encounterId);
    const pkgId = orderDetail.value.packageActivityId;
    if (pkgId != null) {
      const pkg = packages.value.find((item) => String(item.packageActivityId) === String(pkgId));
      if (pkg) {
        selectedPackage.value = pkg;
      }
    }
    await refreshChargeItems(currentEncounterId.value);
    await refreshChargeStatus(currentEncounterId.value);
  } finally {
    orderLoading.value = false;
  }
}

/**
 * 检索确认客户后拉取在检体检单；仅一条时直接打开。
 *
 * @param patientId 患者主键
 * @param autoOpen 命中一条是否自动打开
 */
async function loadActiveOrders(patientId?: string | number, autoOpen = true) {
  activeOrders.value = [];
  // 未选客户时不请求
  if (patientId == null || patientId === '') {
    return;
  }
  activeOrdersLoading.value = true;
  try {
    const res = await listActivePersonalRegistrationsApi({ patientId });
    activeOrders.value = res.rows ?? [];
    // 在检通常只有一条，直接打开避免再点选
    if (autoOpen && activeOrders.value.length === 1) {
      const only = activeOrders.value[0];
      if (only?.encounterId != null) {
        await openActiveOrder(only);
        ElMessage.success(`已加载在检体检单 ${only.busNo || ''}`.trim());
      }
    }
  } catch (error) {
    activeOrders.value = [];
    ElMessage.warning(extractRequestErrorMessage(error, '查询在检体检单失败'));
  } finally {
    activeOrdersLoading.value = false;
  }
}

/**
 * 打开一条在检体检单（不更换客户）。
 *
 * @param row 在检行
 */
async function openActiveOrder(row: PersonalRegistrationHistoryRow) {
  if (row.encounterId == null) {
    return;
  }
  await refreshOrderDetail(String(row.encounterId));
  await nextTick();
  orderPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

async function refreshChargeStatus(encounterId: string) {
  try {
    const res = await getChargeStatusApi({ encounterId });
    chargePaid.value = !!res.paid;
    chargeUnknown.value = !!res.unknown;
  } catch {
    chargePaid.value = null;
    chargeUnknown.value = true;
  }
}

async function removeLine(line: ServiceRequestLineDto) {
  if (!currentEncounterId.value || !line.serviceRequestId) return;
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
  orderLoading.value = true;
  try {
    orderDetail.value = await adjustServiceRequestsApi({
      encounterId: currentEncounterId.value,
      removeServiceRequestIds: [String(line.serviceRequestId)],
    });
    ElMessage.success('已减项');
    await refreshChargeItems(currentEncounterId.value);
    await refreshChargeStatus(currentEncounterId.value);
  } finally {
    orderLoading.value = false;
  }
}

async function addActivityLine() {
  if (!currentEncounterId.value) {
    ElMessage.warning('请先完成登记或加载体检单');
    return;
  }
  if (!addActivityId.value) {
    ElMessage.warning('请选择要加项的体检项目');
    return;
  }
  const addedActivity = selectedAddActivity.value;
  const priceLabel = formatActivityPriceLabel(addedActivity);
  orderLoading.value = true;
  try {
    orderDetail.value = await adjustServiceRequestsApi({
      encounterId: currentEncounterId.value,
      addActivityIds: [addActivityId.value],
    });
    addActivityId.value = '';
    await refreshChargeItems(currentEncounterId.value);
    await refreshChargeStatus(currentEncounterId.value);
    if (isGroupRosterDesk.value) {
      ElMessage.success('已加项');
      return;
    }
    const chargeHint =
      priceLabel && priceLabel !== '免费' && priceLabel !== '暂无定价'
        ? `加项金额 ${priceLabel}，需在收费页缴纳。`
        : '加项成功，请前往收费页确认待缴费用。';
    try {
      await ElMessageBox.confirm(chargeHint, '加项待收费', {
        type: 'info',
        confirmButtonText: '去收费',
        cancelButtonText: '稍后',
      });
      await goCharge();
    } catch {
      ElMessage.success('已加项，请稍后在收费页缴纳加项费用');
    }
  } finally {
    orderLoading.value = false;
  }
}

async function goCharge() {
  if (!currentEncounterId.value) {
    ElMessage.warning('请先登记或加载体检单');
    return;
  }
  const ctx = await getChargeLaunchContextApi({
    encounterId: currentEncounterId.value,
  });
  const path = ctx.pathHint || '/peis/charge';
  await router.push({
    path,
    query: {
      encounterId: String(ctx.encounterId ?? currentEncounterId.value),
      patientId: ctx.patientId != null ? String(ctx.patientId) : undefined,
    },
  });
}

async function copyBusNo() {
  const busNo = orderDetail.value?.busNo;
  if (!busNo) return;
  try {
    await navigator.clipboard.writeText(busNo);
    ElMessage.success('流水号已复制');
  } catch {
    ElMessage.info(busNo);
  }
}

function resetNew() {
  selectedCustomer.value = null;
  archiveCompleteness.value = null;
  customerRows.value = [];
  customerKeyword.value = '';
  customerPickerOpen.value = true;
  clearRegistrationSession();
}

async function loadCompanies() {
  companyLoading.value = true;
  try {
    companyOptions.value = (await listCompaniesApi()) || [];
  } catch {
    companyOptions.value = [];
  } finally {
    companyLoading.value = false;
  }
}

async function loadBatches(cid: string) {
  groupBatchId.value = '';
  batchOptions.value = [];
  if (!cid) return;
  batchLoading.value = true;
  try {
    batchOptions.value = (await listBatchesApi({ companyId: cid })) || [];
  } catch {
    batchOptions.value = [];
  } finally {
    batchLoading.value = false;
  }
}

watch(examKind, (kind) => {
  if (kind === 'group') {
    if (companyOptions.value.length === 0) {
      void loadCompanies();
    }
  } else {
    companyId.value = '';
    groupBatchId.value = '';
    batchOptions.value = [];
  }
});

watch(companyId, (cid) => {
  void loadBatches(cid);
});

watch(
  () => userStore.userInfo?.organizationId,
  (v) => {
    if (v != null && !orgIdInput.value) {
      orgIdInput.value = String(v);
    }
  },
  { immediate: true },
);

function applyHistoryQuick(spec: 'day' | 'week' | 'month') {
  historyQuick.value = spec;
  const strategy =
    spec === 'day' ? 'calendar-day' : spec === 'week' ? 'calendar-week' : 'calendar-month';
  historyDateRange.value = getDateRangeQuick({ strategy }) as [string, string];
  historyPageNo.value = 1;
  void loadHistory();
}

async function loadHistory() {
  const [from, to] = historyDateRange.value ?? [];
  if (!from || !to) {
    ElMessage.warning('请选择日期范围');
    return;
  }
  historyLoading.value = true;
  try {
    const res = await pagePersonalRegistrationsApi({
      dateFrom: from.slice(0, 10),
      dateTo: to.slice(0, 10),
      keyword: historyKeyword.value.trim() || undefined,
      pageNo: historyPageNo.value,
      pageSize: historyPageSize.value,
    });
    historyRows.value = res.content ?? [];
    historyTotal.value = Number(res.totalCount ?? 0);
  } finally {
    historyLoading.value = false;
  }
}

function onHistoryPageChange(page: number) {
  historyPageNo.value = page;
  void loadHistory();
}

function onHistorySizeChange(size: number) {
  historyPageSize.value = size;
  historyPageNo.value = 1;
  void loadHistory();
}

/** 登记历史列表时间展示（去掉秒，列宽更紧凑） */
function formatHistoryTime(raw?: string) {
  const s = String(raw ?? '').trim();
  if (!s) return '—';
  return s.length >= 16 ? s.slice(0, 16) : s;
}

async function openHistoryRow(row: PersonalRegistrationHistoryRow) {
  if (row.encounterId == null) return;
  historyDrawerOpen.value = false;
  clearRegistrationSession();
  if (row.patientId != null) {
    selectedCustomer.value = {
      patientId: row.patientId,
      name: row.patientName,
      genderCode: row.genderCode,
      phone: row.phone,
    };
    customerPickerOpen.value = false;
    void loadArchiveCompleteness(row.patientId);
    void loadActiveOrders(row.patientId, false);
  }
  currentEncounterId.value = String(row.encounterId);
  await refreshOrderDetail(String(row.encounterId));
  await nextTick();
  orderPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

async function goChargeFromHistory(row: PersonalRegistrationHistoryRow) {
  await openHistoryRow(row);
  await goCharge();
}

async function printGuideFromHistory(row: PersonalRegistrationHistoryRow) {
  const encounterId = row.encounterId;
  if (encounterId == null || encounterId === '') {
    ElMessage.warning('缺少就诊信息，无法打印指引单');
    return;
  }
  await printPeisGuide({ encounterId });
}

function chargeText(row: PersonalRegistrationHistoryRow) {
  if (row.chargeUnknown) return '未知';
  return row.paid ? '已收费' : '未收费';
}

function chargeTagType(row: PersonalRegistrationHistoryRow) {
  if (row.chargeUnknown) return 'info';
  return row.paid ? 'success' : 'warning';
}

onMounted(() => {
  void loadPackagesAndActivities();
  void loadPatientInit();
  void loadHistory();
});
</script>

<template>
  <Page content-class="peis-reg-page-content">
    <div class="reg-page">
      <div class="reg">
        <!-- 步骤条 -->
        <div class="reg-steps">
          <div
            class="reg-step"
            :class="{ 'is-done': currentStep > 0, 'is-active': currentStep === 0 }"
          >
            <span class="reg-step__num">1</span>
            <span>选择客户</span>
          </div>
          <div class="reg-step__line" :class="{ 'is-done': currentStep > 0 }" />
          <div
            class="reg-step"
            :class="{ 'is-done': currentStep > 1, 'is-active': currentStep === 1 }"
          >
            <span class="reg-step__num">2</span>
            <span>选择套餐</span>
          </div>
          <div class="reg-step__line" :class="{ 'is-done': currentStep > 1 }" />
          <div
            class="reg-step"
            :class="{ 'is-done': currentStep >= 3, 'is-active': currentStep === 2 }"
          >
            <span class="reg-step__num">3</span>
            <span>确认登记</span>
          </div>
          <div class="reg-step__line" :class="{ 'is-done': currentStep >= 3 }" />
          <div
            class="reg-step"
            :class="{ 'is-done': currentStep >= 3, 'is-active': currentStep >= 3 }"
          >
            <span class="reg-step__num">4</span>
            <span>收费</span>
          </div>
          <div class="reg-steps__actions">
            <ElButton size="small" @click="summaryDrawerOpen = true">登记信息</ElButton>
            <ElButton size="small" @click="historyDrawerOpen = true">
              登记历史
              <span v-if="historyTotal > 0" class="reg-steps__badge">{{ historyTotal }}</span>
            </ElButton>
          </div>
        </div>

        <div class="reg-body">
          <div class="reg-left">
            <!-- 人员区 -->
            <section class="panel person-panel">
              <div class="person-bar">
                <div class="person-bar__search">
                  <ElInput
                    v-model="customerKeyword"
                    clearable
                    placeholder="身份证号 / 姓名，回车检索"
                    @keyup.enter="searchCustomers"
                  />
                  <ElButton type="primary" :loading="customerLoading" @click="searchCustomers">
                    检索
                  </ElButton>
                  <ElButton @click="openCreatePatient">新建患者</ElButton>
                </div>

                <div v-if="selectedCustomer" class="person-chip">
                  <div class="person-chip__avatar">
                    {{ (selectedCustomer.name || '?').slice(0, 1) }}
                  </div>
                  <div class="person-chip__info">
                    <div class="person-chip__name">
                      {{ selectedCustomer.name }}
                      <ElTag size="small" effect="plain" type="info">
                        {{ labelGender(selectedCustomer.genderCode) }}
                      </ElTag>
                    </div>
                    <div class="person-chip__meta">
                      {{ selectedCustomer.idCardNo || '无证件号' }}
                      <template v-if="selectedCustomer.phone">
                        · {{ selectedCustomer.phone }}
                      </template>
                    </div>
                  </div>
                  <ElButton link type="primary" @click="changeCustomer">更换</ElButton>
                </div>

                <div v-else class="person-chip person-chip--empty">尚未选择客户</div>

                <div v-if="selectedCustomer" class="person-bar__encounter">
                  <span class="person-bar__encounter-label">在检体检单</span>
                  <span v-if="activeOrdersLoading" class="person-bar__encounter-empty"
                    >查询中…</span
                  >
                  <span v-else-if="activeOrders.length === 0" class="person-bar__encounter-empty">
                    无在检记录，可新登记
                  </span>
                  <div v-else class="person-bar__order-list">
                    <button
                      v-for="row in activeOrders"
                      :key="String(row.encounterId)"
                      type="button"
                      class="person-bar__order-chip"
                      :class="{
                        'is-active':
                          currentEncounterId !== '' &&
                          String(row.encounterId) === currentEncounterId,
                      }"
                      @click="openActiveOrder(row)"
                    >
                      <span class="person-bar__order-main">
                        {{ row.busNo || '体检单' }}
                      </span>
                      <span v-if="row.packageName" class="person-bar__order-sub">
                        {{ row.packageName }}
                      </span>
                      <span class="person-bar__order-sub">
                        {{ formatHistoryTime(row.registeredAt) }}
                      </span>
                      <ElTag size="small" effect="plain" :type="chargeTagType(row)">
                        {{ chargeText(row) }}
                      </ElTag>
                    </button>
                  </div>
                </div>

                <div v-if="!hasLoginOrg" class="person-bar__org">
                  <span class="person-bar__org-label">机构</span>
                  <ElInput v-model="orgIdInput" clearable placeholder="体检中心机构主键" />
                </div>
              </div>

              <div
                v-if="customerPickerOpen && (customerLoading || customerRows.length > 0)"
                v-loading="customerLoading"
                class="person-results"
              >
                <button
                  v-for="row in customerRows"
                  :key="String(row.patientId)"
                  type="button"
                  class="list-item"
                  :class="{
                    'is-active':
                      selectedCustomer?.patientId != null &&
                      String(selectedCustomer.patientId) === String(row.patientId),
                  }"
                  @click="pickCustomer(row)"
                >
                  <span class="list-item__main">{{ row.name }}</span>
                  <span class="list-item__sub">
                    {{ labelGender(row.genderCode) }} · {{ row.idCardNo || '—' }}
                  </span>
                  <span class="list-item__sub">{{ row.phone || '' }}</span>
                </button>
              </div>
            </section>

            <!-- 三栏选择区 -->
            <div class="picker-row">
              <section
                v-loading="packageLoading"
                class="panel picker-panel"
                :class="{ 'picker-panel--muted': isGroupRosterDesk }"
              >
                <div class="panel__head">
                  <span>{{ isGroupRosterDesk ? '团检套餐（已定）' : '体检套餐' }}</span>
                  <span class="panel__count">{{
                    isGroupRosterDesk ? 1 : filteredPackages.length
                  }}</span>
                </div>
                <div v-if="isGroupRosterDesk" class="panel__body">
                  <div class="list-item--static is-active list-item">
                    <span class="list-item__main">{{ groupPackageName }}</span>
                    <span class="list-item__sub">{{ groupRosterContext?.batchName }}</span>
                  </div>
                </div>
                <template v-else>
                  <div class="panel__tools">
                    <ElInput v-model="packageKeyword" clearable placeholder="搜索套餐名称 / 编码" />
                  </div>
                  <div class="panel__body">
                    <button
                      v-for="pkg in filteredPackages"
                      :key="String(pkg.packageActivityId)"
                      type="button"
                      class="list-item"
                      :class="{
                        'is-active':
                          selectedPackage?.packageActivityId != null &&
                          String(selectedPackage.packageActivityId) ===
                            String(pkg.packageActivityId),
                      }"
                      @click="pickPackage(pkg)"
                    >
                      <span class="list-item__main">{{ pkg.display }}</span>
                      <span class="list-item__price">
                        {{ formatPackagePrice(packageSaleAmount(pkg)) || '—' }}
                      </span>
                      <span class="list-item__badge"> {{ packageItemCount(pkg) }} 项 </span>
                      <span class="list-item__sub">{{ pkg.code }}</span>
                    </button>
                    <ElEmpty
                      v-if="!packageLoading && filteredPackages.length === 0"
                      description="暂无启用套餐"
                      :image-size="48"
                    />
                  </div>
                </template>
              </section>

              <section v-loading="activityLoading" class="panel picker-panel">
                <div class="panel__head">
                  <span>套餐含项</span>
                  <span class="panel__count">{{ packageChildPreview.length }}</span>
                </div>
                <div class="panel__body">
                  <div
                    v-for="item in packageChildPreview"
                    :key="item.activityId"
                    class="list-item--static list-item"
                  >
                    <span class="list-item__index">{{ item.index }}</span>
                    <span class="list-item__main">{{ item.display }}</span>
                    <span v-if="item.priceLabel" class="list-item__price">
                      {{ item.priceLabel }}
                    </span>
                    <span class="list-item__sub">{{ item.code }}</span>
                  </div>
                  <ElEmpty
                    v-if="packageChildPreview.length === 0"
                    :description="selectedPackage ? '该套餐未绑定项目' : '请先选择左侧套餐'"
                    :image-size="48"
                  />
                </div>
              </section>

              <section
                ref="orderPanelRef"
                v-loading="orderLoading"
                class="panel picker-panel"
                :class="{ 'is-highlight': registered }"
              >
                <div class="panel__head">
                  <span>已开项目</span>
                  <span class="panel__count">{{ orderLines.length }}</span>
                  <span
                    v-if="registered && formatPackagePrice(packageChargeTotal)"
                    class="list-item__price panel__head-price"
                  >
                    {{ formatPackagePrice(packageChargeTotal) }}
                  </span>
                  <ElButton
                    v-if="orderDetail?.busNo"
                    link
                    type="primary"
                    class="panel__copy"
                    @click="copyBusNo"
                  >
                    复制流水号
                  </ElButton>
                </div>
                <div v-if="orderDetail?.busNo" class="panel__banner">
                  流水号 {{ orderDetail.busNo }}
                </div>
                <div class="panel__body">
                  <div
                    v-if="showOpenedPackageRow"
                    class="list-item--static order-line order-line--package list-item"
                  >
                    <ElTag size="small" type="primary" effect="plain">套餐</ElTag>
                    <span class="list-item__main">{{ openedPackageName }}</span>
                    <span v-if="formatPackagePrice(packageLineAmount)" class="list-item__price">
                      {{ formatPackagePrice(packageLineAmount) }}
                    </span>
                    <ElButton
                      v-if="canAdjustPackagePrice"
                      link
                      type="primary"
                      @click="openPackagePriceAdjust"
                    >
                      改价
                    </ElButton>
                  </div>
                  <div
                    v-for="line in orderLines"
                    :key="String(line.serviceRequestId)"
                    class="list-item--static order-line list-item"
                    :class="{ 'order-line--nested': !isAddOnLine(line) }"
                  >
                    <span class="list-item__main">{{ line.display || '项目' }}</span>
                    <ElTag v-if="isAddOnLine(line)" size="small" type="warning" effect="plain">
                      加项
                    </ElTag>
                    <span v-if="linePriceLabel(line)" class="list-item__price">
                      {{ linePriceLabel(line) }}
                    </span>
                    <ElButton
                      v-if="canAdjustLinePrice(line)"
                      link
                      type="primary"
                      @click="openLinePriceAdjust(line)"
                    >
                      改价
                    </ElButton>
                    <ElTag size="small" :type="execTagType(line.execStatusCode)">
                      {{ labelExec(line.execStatusCode) }}
                    </ElTag>
                    <ElButton
                      link
                      type="danger"
                      :disabled="line.execStatusCode !== 'pending'"
                      @click="removeLine(line)"
                    >
                      减项
                    </ElButton>
                  </div>
                  <ElEmpty
                    v-if="orderLines.length === 0 && !showOpenedPackageRow"
                    :description="openedItemsEmptyText"
                    :image-size="48"
                  />
                </div>
                <div v-if="registered" class="panel__footer">
                  <div class="panel__footer-add">
                    <ElSelect
                      v-model="addActivityId"
                      filterable
                      clearable
                      placeholder="选择项目加项（含金额）"
                      class="panel__footer-select"
                    >
                      <ElOption
                        v-for="opt in availableAddActivities"
                        :key="String(opt.activityId)"
                        :label="formatActivityOptionLabel(opt)"
                        :value="String(opt.activityId)"
                      />
                    </ElSelect>
                    <ElButton type="primary" plain @click="addActivityLine">加项</ElButton>
                  </div>
                  <p v-if="selectedAddActivity" class="panel__footer-price">
                    本次加项：
                    <strong>{{ formatActivityPriceLabel(selectedAddActivity) }}</strong>
                    <span class="panel__footer-price-hint">（加项后需到收费页缴纳）</span>
                  </p>
                  <p
                    v-else-if="addOnLines.length > 0 && !isGroupRosterDesk"
                    class="panel__footer-price"
                  >
                    已加项合计：
                    <strong>{{ formatPackagePrice(addOnAmountTotal) || '—' }}</strong>
                    <ElButton link type="primary" @click="goCharge">去收费</ElButton>
                  </p>
                </div>
              </section>
            </div>
          </div>

          <!-- 右侧操作台（紧凑：详情见抽屉） -->
          <aside class="reg-right">
            <section class="panel action-panel action-rail">
              <div class="panel__head">快捷操作</div>
              <div class="action-rail__compact">
                <div class="action-rail__line">
                  <span class="action-rail__label">客户</span>
                  <span class="action-rail__value">{{ selectedCustomer?.name || '未选' }}</span>
                </div>
                <div class="action-rail__line">
                  <span class="action-rail__label">套餐</span>
                  <span class="action-rail__value">
                    {{ selectedPackage?.display || groupPackageName || '未选' }}
                  </span>
                  <span class="action-rail__trail">
                    <span
                      v-if="formatPackagePrice(packageSaleAmount(selectedPackage))"
                      class="action-rail__price"
                    >
                      {{ formatPackagePrice(packageSaleAmount(selectedPackage)) }}
                    </span>
                    <ElButton
                      v-if="canAdjustPackagePrice"
                      link
                      type="primary"
                      class="action-rail__action"
                      @click="openPackagePriceAdjust"
                    >
                      改价
                    </ElButton>
                  </span>
                </div>
                <div class="action-rail__line">
                  <span class="action-rail__label">收费</span>
                  <span class="action-rail__value">
                    <ElTag size="small" :type="chargeLabel.type">{{ chargeLabel.text }}</ElTag>
                  </span>
                </div>
                <div class="action-rail__line">
                  <span class="action-rail__label"></span>
                  <ElButton
                    link
                    type="primary"
                    class="action-rail__more"
                    @click="summaryDrawerOpen = true"
                  >
                    展开登记信息
                  </ElButton>
                </div>
              </div>

              <div class="action-btns">
                <template v-if="isGroupRosterDesk">
                  <ElButton
                    type="primary"
                    class="action-btns__primary"
                    :loading="groupDeskLoading"
                    :disabled="!canGroupEnroll"
                    @click="handleGroupEnroll"
                  >
                    团检建单
                  </ElButton>
                  <ElButton
                    class="action-btns__secondary"
                    :loading="groupDeskLoading"
                    :disabled="!canGroupArrival"
                    @click="handleGroupArrival"
                  >
                    确认到检
                  </ElButton>
                  <ElButton
                    class="action-btns__secondary"
                    :disabled="!groupRosterContext?.encounterId && !currentEncounterId"
                    @click="handleGroupPrintGuide"
                  >
                    打印指引单
                  </ElButton>
                </template>
                <template v-else>
                  <ElButton
                    type="primary"
                    class="action-btns__primary"
                    :loading="registering"
                    :disabled="!canRegister"
                    :title="registerBlockReason"
                    @click="handleRegister"
                  >
                    确认登记
                  </ElButton>
                  <ElButton
                    class="action-btns__secondary"
                    :disabled="!registered"
                    @click="goCharge"
                  >
                    去收费
                  </ElButton>
                </template>
                <ElButton class="action-btns__secondary" text bg @click="resetNew">
                  清空重录
                </ElButton>
              </div>

              <p
                v-if="registerBlockReason && !canRegister && !registered && !isGroupRosterDesk"
                class="action-hint action-hint--warn action-hint--compact"
              >
                {{ registerBlockReason }}
              </p>
              <p
                v-else-if="registered && addOnLines.length > 0 && !isGroupRosterDesk"
                class="action-hint action-hint--warn action-hint--compact"
              >
                加项 {{ addOnLines.length }} 项待收
                {{ formatPackagePrice(addOnAmountTotal) }}，请到收费页缴纳
              </p>
            </section>
          </aside>
        </div>
      </div>
    </div>

    <ElDrawer
      v-model="summaryDrawerOpen"
      title="登记信息"
      direction="rtl"
      size="420px"
      destroy-on-close
      class="reg-summary-drawer"
    >
      <div class="reg-summary">
        <ElAlert
          v-if="groupRosterLoading"
          type="info"
          show-icon
          :closable="false"
          title="正在匹配团检名册…"
        />
        <ElAlert
          v-else-if="groupRosterMatches.length > 1 && !groupRosterContext"
          type="warning"
          show-icon
          :closable="false"
          title="该客户在多个团检批次名册中，请选择本次到检批次"
        >
          <ElSelect
            class="reg-summary__full"
            placeholder="选择团检批次"
            @change="onGroupRosterPick"
          >
            <ElOption
              v-for="item in groupRosterMatches"
              :key="String(item.rosterId)"
              :label="`${item.companyName || '单位'} · ${item.batchName || '批次'}`"
              :value="String(item.rosterId)"
            />
          </ElSelect>
        </ElAlert>
        <ElAlert
          v-else-if="isGroupRosterDesk"
          type="success"
          show-icon
          :closable="false"
          :title="`${groupRosterContext?.companyName || '团检单位'} · ${groupRosterContext?.batchName || '批次'}`"
        >
          <div class="reg-summary__alert-meta">套餐：{{ groupPackageName }}</div>
          <div class="reg-summary__alert-tags">
            <ElTag size="small" :type="enrollmentTagType(groupRosterContext)">
              {{ enrollmentLabel(groupRosterContext) }}
            </ElTag>
            <ElTag size="small" :type="arrivalTagType(groupRosterContext?.arrivalStatusCode)">
              {{ arrivalLabel(groupRosterContext?.arrivalStatusCode) }}
            </ElTag>
          </div>
        </ElAlert>
        <ElAlert
          v-if="archiveCompleteness"
          :type="archiveAlertType"
          :title="archiveCompleteness.summary || '档案提示'"
          show-icon
          :closable="false"
        >
          <div class="reg-summary__alert-actions">
            <ElButton type="primary" link @click="openArchiveDialog">去补齐档案</ElButton>
            <ElButton
              v-if="selectedCustomer?.patientId"
              link
              type="primary"
              @click="
                router.push({
                  path: '/peis/history',
                  query: { patientId: String(selectedCustomer.patientId) },
                })
              "
            >
              查看体检历史
            </ElButton>
          </div>
        </ElAlert>

        <section class="reg-summary__card">
          <div class="reg-summary__card-head">客户与登记</div>
          <div class="reg-summary__fields">
            <div class="reg-summary__field">
              <span class="reg-summary__label">客户</span>
              <span class="reg-summary__value">{{ selectedCustomer?.name || '未选' }}</span>
            </div>
            <div class="reg-summary__field">
              <span class="reg-summary__label">体检类型</span>
              <span v-if="isGroupRosterDesk" class="reg-summary__value">团体体检</span>
              <ElSelect
                v-else
                v-model="examKind"
                placeholder="请选择"
                size="small"
                class="reg-summary__control"
              >
                <ElOption label="个人体检" value="personal" />
                <ElOption label="新生体检" value="freshman" />
                <ElOption label="在校年检" value="annual" />
                <ElOption label="教职工" value="staff" />
                <ElOption label="其他" value="other" />
              </ElSelect>
            </div>
            <div v-if="isGroupRosterDesk && isGroupExam" class="reg-summary__field">
              <span class="reg-summary__label">单位</span>
              <span class="reg-summary__value">{{ groupRosterContext?.companyName || '—' }}</span>
            </div>
            <div v-if="isGroupRosterDesk && isGroupExam" class="reg-summary__field">
              <span class="reg-summary__label">分组</span>
              <span class="reg-summary__value">{{ groupRosterContext?.batchName || '—' }}</span>
            </div>
            <div class="reg-summary__field">
              <span class="reg-summary__label">推荐人</span>
              <ElInput
                v-model="referrerName"
                clearable
                placeholder="选填"
                size="small"
                class="reg-summary__control"
              />
            </div>
            <div class="reg-summary__field">
              <span class="reg-summary__label">早餐</span>
              <ElSelect
                v-model="breakfastFlag"
                clearable
                placeholder="请选择"
                size="small"
                class="reg-summary__control"
              >
                <ElOption label="有" :value="true" />
                <ElOption label="无" :value="false" />
              </ElSelect>
            </div>
          </div>
        </section>

        <section class="reg-summary__card">
          <div class="reg-summary__package">
            <div class="reg-summary__package-name">{{ summaryPackageName }}</div>
            <div
              v-if="formatPackagePrice(packageSaleAmount(selectedPackage))"
              class="reg-summary__package-price"
            >
              {{ formatPackagePrice(packageSaleAmount(selectedPackage)) }}
            </div>
          </div>
          <div class="reg-summary__meta">
            <div class="reg-summary__meta-item">
              <span class="reg-summary__meta-label">项目</span>
              <span class="reg-summary__meta-value">{{ summaryItemCount }} 项</span>
            </div>
            <div class="reg-summary__meta-item">
              <span class="reg-summary__meta-label">收费</span>
              <ElTag size="small" :type="chargeLabel.type">{{ chargeLabel.text }}</ElTag>
            </div>
            <div
              v-if="orderDetail?.busNo"
              class="reg-summary__meta-item reg-summary__meta-item--wide"
            >
              <span class="reg-summary__meta-label">流水号</span>
              <span class="reg-summary__meta-value reg-summary__mono">{{ orderDetail.busNo }}</span>
              <ElButton link type="primary" @click="copyBusNo">复制</ElButton>
            </div>
            <div
              v-if="registered && addOnLines.length > 0 && !isGroupRosterDesk"
              class="reg-summary__meta-item reg-summary__meta-item--wide"
            >
              <span class="reg-summary__meta-label">加项</span>
              <span class="reg-summary__meta-value">
                {{ addOnLines.length }} 项 · {{ formatPackagePrice(addOnAmountTotal) || '—' }}
              </span>
              <ElButton link type="primary" @click="goCharge">去收费</ElButton>
            </div>
          </div>
        </section>

        <p v-if="isGroupRosterDesk && canGroupEnroll" class="action-hint action-hint--ok">
          团检免收费：确认建单后可打印指引单
        </p>
        <p v-else-if="isGroupRosterDesk && canGroupArrival" class="action-hint action-hint--ok">
          已建单，请确认到检并打印指引单
        </p>
        <p
          v-else-if="registered && !chargePaid && !isGroupRosterDesk"
          class="action-hint action-hint--ok"
        >
          登记完成，可在「已开项目」加/减项或改价后去收费
        </p>
      </div>
    </ElDrawer>

    <ElDrawer
      v-model="historyDrawerOpen"
      title="登记历史"
      direction="btt"
      size="56%"
      destroy-on-close
      class="reg-history-drawer"
      @open="loadHistory"
    >
      <section class="history-panel history-panel--drawer panel">
        <div class="history-toolbar">
          <ElInput
            v-model="historyKeyword"
            clearable
            size="small"
            class="history-toolbar__keyword"
            placeholder="姓名 / 流水号"
            @keyup.enter="
              () => {
                historyPageNo = 1;
                loadHistory();
              }
            "
          />
          <ElDatePicker
            v-model="historyDateRange"
            type="daterange"
            size="small"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :unlink-panels="true"
            teleported
            class="history-toolbar__dates"
            @change="
              () => {
                historyPageNo = 1;
                loadHistory();
              }
            "
          />
          <ElRadioGroup
            class="history-toolbar__quick"
            :model-value="historyQuick"
            size="small"
            @update:model-value="(v) => applyHistoryQuick(v as 'day' | 'week' | 'month')"
          >
            <ElRadioButton value="day">今天</ElRadioButton>
            <ElRadioButton value="week">本周</ElRadioButton>
            <ElRadioButton value="month">本月</ElRadioButton>
          </ElRadioGroup>
          <div class="history-toolbar__spacer" />
          <span class="history-toolbar__hint">双击行打开登记</span>
          <ElButton size="small" :loading="historyLoading" @click="loadHistory">刷新</ElButton>
        </div>
        <div v-loading="historyLoading" class="history-table-wrap">
          <ElTable
            class="history-table"
            :data="historyRows"
            height="100%"
            size="small"
            stripe
            highlight-current-row
            empty-text="暂无登记记录"
            @row-dblclick="openHistoryRow"
          >
            <ElTableColumn
              prop="busNo"
              label="流水号"
              width="156"
              show-overflow-tooltip
              class-name="history-col-mono"
            />
            <ElTableColumn prop="patientName" label="客户" min-width="96" show-overflow-tooltip />
            <ElTableColumn label="性别" width="56" align="center">
              <template #default="{ row }">
                {{ labelGender(row.genderCode) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="packageName" label="套餐" min-width="132" show-overflow-tooltip />
            <ElTableColumn label="登记时间" width="144" align="center">
              <template #default="{ row }">
                <span class="history-time">{{ formatHistoryTime(row.registeredAt) }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="收费" width="76" align="center">
              <template #default="{ row }">
                <ElTag size="small" :type="chargeTagType(row)">
                  {{ chargeText(row) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="196" fixed="right" align="center">
              <template #default="{ row }">
                <div class="history-row-actions">
                  <ElButton link type="primary" size="small" @click="openHistoryRow(row)">
                    打开
                  </ElButton>
                  <ElButton link type="primary" size="small" @click="printGuideFromHistory(row)">
                    指引单
                  </ElButton>
                  <ElButton
                    link
                    type="primary"
                    size="small"
                    :disabled="!!row.paid"
                    @click="goChargeFromHistory(row)"
                  >
                    去收费
                  </ElButton>
                </div>
              </template>
            </ElTableColumn>
          </ElTable>
        </div>
        <div class="history-pager">
          <ElPagination
            background
            layout="total, sizes, prev, pager, next"
            :total="historyTotal"
            :current-page="historyPageNo"
            :page-size="historyPageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="onHistoryPageChange"
            @size-change="onHistorySizeChange"
          />
        </div>
      </section>
    </ElDrawer>

    <ElDialog v-model="archiveDialogVisible" title="补齐健康档案" width="520px" destroy-on-close>
      <p class="mb-3 text-sm text-[var(--el-text-color-secondary)]">
        提示项来自体检健康档案（既往史、过敏史等）。填写后保存即可消除登记摘要中的待补齐提示。
      </p>
      <ElForm label-width="88px">
        <ElFormItem label="既往史">
          <ElInput
            v-model="archiveForm.pastHistory"
            type="textarea"
            :rows="3"
            placeholder="如：高血压、糖尿病等；无则填「无」"
          />
        </ElFormItem>
        <ElFormItem label="过敏史">
          <ElInput
            v-model="archiveForm.allergySummary"
            type="textarea"
            :rows="2"
            placeholder="如：青霉素过敏；无则填「无」"
          />
        </ElFormItem>
        <ElFormItem label="家族史">
          <ElInput
            v-model="archiveForm.familyHistory"
            type="textarea"
            :rows="2"
            placeholder="选填"
          />
        </ElFormItem>
        <ElFormItem label="生活习惯">
          <ElInput
            v-model="archiveForm.habitSummary"
            type="textarea"
            :rows="2"
            placeholder="选填"
          />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput v-model="archiveForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="archiveDialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="archiveSaving" @click="saveArchiveDialog">
          保存
        </ElButton>
      </template>
    </ElDialog>

    <PeisChargePriceAdjustDialog
      v-model:visible="priceAdjustVisible"
      :encounter-id="currentEncounterId"
      :row="priceAdjustRow"
      @success="handlePriceAdjustSuccess"
    />

    <ElDialog
      v-model="packageAdjustVisible"
      title="套餐改价"
      width="420px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="space-y-3">
        <div class="text-sm text-[var(--el-text-color-regular)]">
          套餐：<strong>{{ selectedPackage?.display || '—' }}</strong>
        </div>
        <div class="text-sm text-[var(--el-text-color-secondary)]">
          当前套餐价：{{ formatPackagePrice(packageChargeItem?.unitPrice) || '—' }}
        </div>
        <ElForm label-width="88px">
          <ElFormItem label="新套餐价" required>
            <ElInputNumber
              v-model="packageAdjustAmount"
              :min="0"
              :precision="2"
              :step="1"
              class="!w-full"
            />
          </ElFormItem>
        </ElForm>
      </div>
      <template #footer>
        <ElButton @click="packageAdjustVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="packageAdjustLoading" @click="submitPackagePriceAdjust">
          确认改价
        </ElButton>
      </template>
    </ElDialog>

    <EditDialog
      v-model:visible="patientDialogVisible"
      :cert-type-options="certTypeOptions"
      :marital-status-options="maritalStatusOptions"
      :blood-type-abo-options="bloodTypeAboOptions"
      :blood-type-rh-options="bloodTypeRhOptions"
      :sex-options="sexOptions"
      :education-level-options="educationLevelOptions"
      :form-data="patientDialogForm || undefined"
      @success="onPatientCreated"
    />
  </Page>
</template>

<style scoped>
.reg-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
  height: calc(100vh - 128px);
  min-height: 0;
  overflow: hidden;
}

.reg {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.reg-steps {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-extra-light);
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex: none;
  flex-wrap: wrap;
}

.reg-steps__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  flex: none;
}

.reg-steps__badge {
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 999px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 11px;
  line-height: 18px;
}

.reg-step {
  display: flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.reg-step__num {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.reg-step.is-active {
  color: var(--el-color-primary);
  font-weight: 600;
}

.reg-step.is-active .reg-step__num,
.reg-step.is-done .reg-step__num {
  background: var(--el-color-primary);
  color: #fff;
}

.reg-step.is-done {
  color: var(--el-text-color-regular);
}

.reg-step__line {
  flex: 1;
  height: 1px;
  min-width: 16px;
  background: var(--el-border-color-lighter);
}

.reg-step__line.is-done {
  background: var(--el-color-primary-light-5);
}

.reg-body {
  display: flex;
  gap: 10px;
  flex: 1;
  min-height: 0;
  align-items: stretch;
}

.reg-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

.reg-right {
  width: 240px;
  flex: none;
}

.action-rail {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.action-rail__compact {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  flex: none;
}

.action-rail__line {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) auto;
  align-items: center;
  column-gap: 8px;
  min-height: 22px;
  font-size: 12px;
  line-height: 1.4;
}

.action-rail__label {
  grid-column: 1;
  color: var(--el-text-color-secondary);
  text-align: right;
}

.action-rail__value {
  grid-column: 2;
  min-width: 0;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-rail__trail {
  grid-column: 3;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex: none;
}

.action-rail__price {
  color: var(--el-color-danger);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.action-rail__action {
  padding: 0 !important;
  height: auto !important;
  min-height: 0 !important;
  font-size: 12px !important;
}

.action-rail__more {
  grid-column: 2 / -1;
  justify-self: start;
  padding: 0 !important;
  height: auto !important;
  min-height: 0 !important;
  font-size: 12px !important;
}

.reg-summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.reg-summary__full {
  width: 100%;
  margin-top: 8px;
}

.reg-summary__alert-meta {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.reg-summary__alert-tags,
.reg-summary__alert-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.reg-summary__card {
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 6px;
  overflow: hidden;
  background: var(--el-bg-color);
}

.reg-summary__card-head {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.reg-summary__fields {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  padding: 12px;
}

.reg-summary__field {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.reg-summary__label {
  width: 64px;
  flex: none;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.reg-summary__value {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
  word-break: break-all;
}

.reg-summary__control {
  flex: 1;
  min-width: 0;
}

.reg-summary__package {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  background: var(--el-color-primary-light-9);
}

.reg-summary__package-name {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
}

.reg-summary__package-price {
  flex: none;
  font-size: 16px;
  font-weight: 700;
  color: var(--el-color-danger);
  font-variant-numeric: tabular-nums;
}

.reg-summary__meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 12px;
  padding: 12px;
}

.reg-summary__meta-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.reg-summary__meta-item--wide {
  grid-column: 1 / -1;
}

.reg-summary__meta-label {
  flex: none;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.reg-summary__meta-value {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  font-weight: 500;
}

.reg-summary__mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.history-panel--drawer {
  flex: 1;
  min-height: 0;
  height: auto;
  border: none;
  border-radius: 0;
}

.history-panel--drawer .history-table-wrap {
  min-height: 200px;
}

:deep(.reg-history-drawer .el-drawer__body),
:deep(.reg-summary-drawer .el-drawer__body) {
  padding: 12px 16px;
  overflow: auto;
}

:deep(.reg-history-drawer .el-drawer__body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-bg-color);
  overflow: hidden;
  min-height: 0;
}

.panel.is-highlight {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-8);
}

.panel__head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  background: var(--el-fill-color-lighter);
  flex: none;
}

.panel__head-price {
  font-weight: 600;
}

.panel__count {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-size: 11px;
  font-weight: 500;
  text-align: center;
  line-height: 18px;
}

.panel__copy {
  margin-left: auto;
}

.panel__tools {
  padding: 6px 8px 0;
  flex: none;
}

.panel__banner {
  padding: 5px 10px;
  font-size: 12px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-bottom: 1px solid var(--el-color-primary-light-7);
  flex: none;
}

.panel__body {
  flex: 1;
  overflow: auto;
  padding: 6px;
  min-height: 0;
}

.panel__footer {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  border-top: 1px solid var(--el-border-color-extra-light);
  flex: none;
  background: var(--el-fill-color-blank);
}

.panel__footer-add {
  display: flex;
  gap: 8px;
  align-items: center;
}

.panel__footer-price {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.panel__footer-price strong {
  color: var(--el-color-danger);
  font-variant-numeric: tabular-nums;
}

.panel__footer-price-hint {
  margin-left: 4px;
  color: var(--el-text-color-placeholder);
}

.panel__footer-select {
  flex: 1;
  min-width: 0;
}

.person-panel {
  flex: none;
}

.person-bar {
  display: flex;
  flex-wrap: nowrap;
  gap: 10px;
  align-items: center;
  padding: 10px;
}

.person-bar__search {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 360px;
  flex: none;
}

.person-bar__search :deep(.el-input) {
  flex: 1;
  min-width: 0;
}

.person-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 160px;
  max-width: 320px;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
}

.person-chip--empty {
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-lighter);
  border-color: var(--el-border-color-extra-light);
  font-size: 13px;
}

.person-chip__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  flex: none;
}

.person-chip__info {
  flex: 1;
  min-width: 0;
}

.person-chip__name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 13px;
}

.person-chip__meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 1px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.person-bar__org {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 200px;
  flex: none;
}

.person-bar__org-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex: none;
}

.person-bar__encounter {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1 1 280px;
  min-width: 0;
  margin-left: auto;
}

.person-bar__encounter-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex: none;
  white-space: nowrap;
}

.person-bar__encounter-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

.person-bar__order-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.person-bar__order-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  padding: 4px 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  cursor: pointer;
}

.person-bar__order-chip:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.person-bar__order-chip.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.person-bar__order-main {
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.person-bar__order-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.person-results {
  max-height: 140px;
  overflow: auto;
  padding: 0 8px 8px;
  border-top: 1px solid var(--el-border-color-extra-light);
}

.picker-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 10px;
  flex: 1;
  min-height: 0;
}

.picker-panel {
  height: 100%;
  min-height: 0;
}

.list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-bottom: 4px;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition:
    background 0.12s ease,
    border-color 0.12s ease;
}

.list-item:hover {
  background: var(--el-fill-color-light);
}

.list-item.is-active {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.list-item--static {
  cursor: default;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  border-radius: 0;
  margin-bottom: 0;
}

.list-item--static:hover {
  background: transparent;
}

.list-item__main {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.list-item__sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  flex: none;
  max-width: 42%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.list-item__badge {
  flex: none;
  font-size: 11px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-8);
  padding: 0 6px;
  border-radius: 999px;
  line-height: 18px;
}

.list-item__price {
  flex: none;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-color-danger);
  font-weight: 600;
  white-space: nowrap;
}

.list-item__index {
  width: 16px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  flex: none;
  text-align: right;
}

.order-line {
  justify-content: flex-start;
}

.order-line--package {
  background: var(--el-color-primary-light-9);
  border-bottom: 1px solid var(--el-color-primary-light-7);
}

.order-line--package:hover {
  background: var(--el-color-primary-light-9);
}

.order-line--package .list-item__main {
  font-weight: 600;
}

.order-line--nested {
  padding-left: 20px;
}

.action-panel {
  position: sticky;
  top: 0;
  height: 100%;
}

.action-rail .action-btns {
  padding: 10px 12px;
}

.action-rail .action-btns .el-button {
  padding: 8px 12px;
  margin: 0;
}

.action-btns {
  display: grid;
  gap: 8px;
  padding: 10px 12px;
}

.action-btns__primary,
.action-btns__secondary {
  width: 100%;
  margin: 0 !important;
}

.action-hint {
  margin: 0 12px 12px;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  border: none;
}

.action-hint--compact {
  margin-top: 0;
}

.action-hint--warn {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
}

.action-hint--ok {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success-dark-2);
}

.history-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.history-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  flex: none;
  background: var(--el-fill-color-blank);
}

.history-toolbar__keyword {
  width: 188px;
  flex: none;
}

.history-toolbar__dates {
  width: 300px;
  flex: none;
}

.history-toolbar__quick {
  flex: none;
}

.history-toolbar__spacer {
  flex: 1;
  min-width: 12px;
}

.history-toolbar__hint {
  flex: none;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.history-table-wrap {
  flex: 1;
  min-height: 0;
  padding: 6px 8px 0;
}

.history-table {
  width: 100%;
}

.history-time {
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}

.history-row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex-wrap: nowrap;
}

:deep(.history-col-mono .cell) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

:deep(.history-toolbar__dates.el-date-editor) {
  --el-date-editor-width: 300px;
}

:deep(.history-toolbar__dates.el-date-editor .el-range-input) {
  font-size: 12px;
}

:deep(.history-toolbar__dates.el-date-editor .el-range-separator) {
  flex: none;
  padding: 0 2px;
}

.history-pager {
  display: flex;
  justify-content: flex-end;
  padding: 6px 10px 8px;
  flex: none;
  border-top: 1px solid var(--el-border-color-extra-light);
}

@media (max-width: 1680px), (max-height: 940px) {
  .person-bar {
    flex-wrap: wrap;
  }

  .person-bar__search {
    width: 100%;
    max-width: 420px;
  }

  .person-bar__encounter {
    margin-left: 0;
    width: 100%;
  }

  .person-chip {
    max-width: none;
    flex: 1 1 200px;
  }
}

@media (max-width: 1280px) {
  .reg-page {
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .reg {
    height: auto;
    min-height: 0;
  }

  .reg-body {
    flex-direction: column;
  }

  .reg-right {
    width: 100%;
  }

  .action-panel {
    position: static;
    height: auto;
  }

  .person-bar {
    flex-wrap: wrap;
  }

  .person-bar__search {
    width: 100%;
  }

  .person-chip {
    max-width: none;
  }

  .picker-row {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .picker-panel {
    height: auto;
    min-height: 240px;
  }

  .panel__body {
    max-height: 260px;
  }

  .history-table-wrap {
    min-height: 220px;
  }
}
</style>
