<script setup lang="ts">
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

import { patientInitApi } from '#/api/basic/patient';
import { searchActivityApi, searchPackageApi } from '#/api/peis/catalog-config';
import {
  checkArchiveCompletenessApi,
  type ArchiveCompletenessDto,
} from '#/api/peis/health-archive';
import {
  adjustServiceRequestsApi,
  createRegistrationApi,
  getChargeLaunchContextApi,
  getChargeStatusApi,
  getCheckOrderDetailApi,
  pagePersonalRegistrationsApi,
  searchCustomerApi,
} from '#/api/peis/personal-registration';
import { getDateRangeQuick } from '#/utils/format/date-range-quick';
import EditDialog from '#/views/basic/patient/edit-dialog.vue';
import {
  ElButton,
  ElDatePicker,
  ElAlert,
  ElEmpty,
  ElInput,
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

const packageKeyword = ref('');
const packageLoading = ref(false);
const packages = ref<PeisPackageDto[]>([]);
const selectedPackage = ref<PeisPackageDto | null>(null);
/** 空=后端按 Campus/首检推断 */
const examKind = ref('');
const archiveCompleteness = ref<ArchiveCompletenessDto | null>(null);

const activityMap = ref<Map<string, PeisActivityDto>>(new Map());
const activityLoading = ref(false);

const orgIdInput = ref('');
const registering = ref(false);

const currentEncounterId = ref('');
const orderDetail = ref<CheckOrderDetailResponse | null>(null);
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

const registered = computed(() => !!orderDetail.value?.encounterId);

const canRegister = computed(() => {
  return (
    !!selectedCustomer.value?.patientId &&
    !!selectedPackage.value?.packageActivityId &&
    !!defaultOrgId.value &&
    packageChildPreview.value.length > 0 &&
    !registering.value
  );
});

const registerBlockReason = computed(() => {
  if (!selectedCustomer.value?.patientId) return '请先选择客户';
  if (!selectedPackage.value?.packageActivityId) return '请先选择体检套餐';
  if (packageChildPreview.value.length === 0) return '所选套餐没有项目';
  if (!defaultOrgId.value) return '请填写体检机构';
  return '';
});

const currentStep = computed(() => {
  if (registered.value) return 3;
  if (selectedCustomer.value && selectedPackage.value) return 2;
  if (selectedCustomer.value) return 1;
  return 0;
});

const chargeLabel = computed(() => {
  if (!registered.value) return { text: '未登记', type: 'info' as const };
  if (chargeUnknown.value) return { text: '未知', type: 'info' as const };
  if (chargePaid.value) return { text: '已结算', type: 'success' as const };
  return { text: '未结算', type: 'warning' as const };
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
  selectedCustomer.value = row;
  customerPickerOpen.value = false;
  void loadArchiveCompleteness(row.patientId);
}

function changeCustomer() {
  selectedCustomer.value = null;
  customerPickerOpen.value = true;
  customerRows.value = [];
  archiveCompleteness.value = null;
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
      examKind: examKind.value || undefined,
    });
    ElMessage.success(`登记成功，流水号 ${res.busNo || res.encounterId}`);
    if (res.encounterId != null) {
      currentEncounterId.value = String(res.encounterId);
      await refreshOrderDetail(String(res.encounterId));
      await nextTick();
      orderPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
    void loadHistory();
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
    await refreshChargeStatus(currentEncounterId.value);
  } finally {
    orderLoading.value = false;
  }
}

async function loadOrderByInput() {
  const id = currentEncounterId.value.trim();
  if (!id) {
    ElMessage.warning('请输入就诊主键');
    return;
  }
  await refreshOrderDetail(id);
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
  orderLoading.value = true;
  try {
    orderDetail.value = await adjustServiceRequestsApi({
      encounterId: currentEncounterId.value,
      addActivityIds: [addActivityId.value],
    });
    addActivityId.value = '';
    ElMessage.success('已加项');
    await refreshChargeStatus(currentEncounterId.value);
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
  selectedPackage.value = null;
  examKind.value = '';
  archiveCompleteness.value = null;
  customerRows.value = [];
  customerKeyword.value = '';
  customerPickerOpen.value = true;
  currentEncounterId.value = '';
  orderDetail.value = null;
  chargePaid.value = null;
  chargeUnknown.value = false;
  addActivityId.value = '';
}

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

async function openHistoryRow(row: PersonalRegistrationHistoryRow) {
  if (row.encounterId == null) return;
  currentEncounterId.value = String(row.encounterId);
  await refreshOrderDetail(String(row.encounterId));
  if (row.patientId != null) {
    selectedCustomer.value = {
      patientId: row.patientId,
      name: row.patientName,
      genderCode: row.genderCode,
      phone: row.phone,
    };
    customerPickerOpen.value = false;
  }
  await nextTick();
  orderPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

async function goChargeFromHistory(row: PersonalRegistrationHistoryRow) {
  await openHistoryRow(row);
  await goCharge();
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
  <Page
    title="个检登记"
    description="检索或新建患者 → 选套餐 → 登记 → 加/减项 → 收费"
    content-class="peis-reg-page-content"
  >
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

                <div v-if="!hasLoginOrg" class="person-bar__org">
                  <span class="person-bar__org-label">机构</span>
                  <ElInput v-model="orgIdInput" clearable placeholder="体检中心机构主键" />
                </div>
              </div>

              <div class="person-subbar">
                <span class="person-subbar__label">已有体检单</span>
                <ElInput
                  v-model="currentEncounterId"
                  clearable
                  class="encounter-input"
                  placeholder="就诊主键，回车加载"
                  @keyup.enter="loadOrderByInput"
                />
                <ElButton :loading="orderLoading" @click="loadOrderByInput">加载</ElButton>
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
              <section v-loading="packageLoading" class="panel picker-panel">
                <div class="panel__head">
                  <span>体检套餐</span>
                  <span class="panel__count">{{ filteredPackages.length }}</span>
                </div>
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
                        String(selectedPackage.packageActivityId) === String(pkg.packageActivityId),
                    }"
                    @click="pickPackage(pkg)"
                  >
                    <span class="list-item__main">{{ pkg.display }}</span>
                    <span class="list-item__badge"> {{ packageItemCount(pkg) }} 项 </span>
                    <span class="list-item__sub">{{ pkg.code }}</span>
                  </button>
                  <ElEmpty
                    v-if="!packageLoading && filteredPackages.length === 0"
                    description="暂无启用套餐"
                    :image-size="48"
                  />
                </div>
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
                    v-for="line in orderLines"
                    :key="String(line.serviceRequestId)"
                    class="list-item--static order-line list-item"
                  >
                    <span class="list-item__main">{{ line.display || '项目' }}</span>
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
                    v-if="orderLines.length === 0"
                    description="登记成功后在此加/减项"
                    :image-size="48"
                  />
                </div>
                <div v-if="registered" class="panel__footer">
                  <ElSelect
                    v-model="addActivityId"
                    filterable
                    clearable
                    placeholder="选择项目加项"
                    class="panel__footer-select"
                  >
                    <ElOption
                      v-for="opt in availableAddActivities"
                      :key="String(opt.activityId)"
                      :label="`${opt.display || ''}（${opt.code || ''}）`"
                      :value="String(opt.activityId)"
                    />
                  </ElSelect>
                  <ElButton type="primary" plain @click="addActivityLine">加项</ElButton>
                </div>
              </section>
            </div>
          </div>

          <!-- 右侧操作台 -->
          <aside class="reg-right">
            <section class="panel action-panel">
              <div class="panel__head">登记摘要</div>
              <ElAlert
                v-if="archiveCompleteness"
                class="mb-3"
                :type="
                  archiveCompleteness.level === 'block'
                    ? 'error'
                    : archiveCompleteness.level === 'warn'
                      ? 'warning'
                      : 'success'
                "
                :title="archiveCompleteness.summary || '档案提示'"
                show-icon
                :closable="false"
              >
                <template v-if="archiveCompleteness.missingItems?.length">
                  <div
                    v-for="item in archiveCompleteness.missingItems"
                    :key="item.code"
                    class="text-xs"
                  >
                    · {{ item.message }}
                  </div>
                </template>
                <ElButton
                  v-if="selectedCustomer?.patientId"
                  link
                  type="primary"
                  class="mt-1"
                  @click="
                    router.push({
                      path: '/peis/history',
                      query: { patientId: String(selectedCustomer.patientId) },
                    })
                  "
                >
                  查看体检历史
                </ElButton>
              </ElAlert>
              <div class="summary">
                <div class="summary__row">
                  <span class="summary__label">客户</span>
                  <span class="summary__value">
                    {{ selectedCustomer?.name || '未选' }}
                  </span>
                </div>
                <div class="summary__row">
                  <span class="summary__label">体检类型</span>
                  <ElSelect
                    v-model="examKind"
                    clearable
                    placeholder="自动推断"
                    size="small"
                    style="width: 140px"
                  >
                    <ElOption label="自动推断" value="" />
                    <ElOption label="新生体检" value="freshman" />
                    <ElOption label="在校年检" value="annual" />
                    <ElOption label="教职工" value="staff" />
                    <ElOption label="其他" value="other" />
                  </ElSelect>
                </div>
                <div class="summary__row">
                  <span class="summary__label">套餐</span>
                  <span class="summary__value">
                    {{ selectedPackage?.display || '未选' }}
                  </span>
                </div>
                <div class="summary__row">
                  <span class="summary__label">项目数</span>
                  <span class="summary__value">
                    {{ registered ? orderLines.length : packageChildPreview.length }}
                  </span>
                </div>
                <div class="summary__row">
                  <span class="summary__label">收费</span>
                  <ElTag size="small" :type="chargeLabel.type">
                    {{ chargeLabel.text }}
                  </ElTag>
                </div>
                <div v-if="orderDetail?.busNo" class="summary__row">
                  <span class="summary__label">流水号</span>
                  <span class="summary__value summary__mono">
                    {{ orderDetail.busNo }}
                  </span>
                </div>
              </div>

              <div class="action-btns">
                <ElButton
                  type="primary"
                  size="large"
                  class="action-btns__primary"
                  :loading="registering"
                  :disabled="!canRegister"
                  :title="registerBlockReason"
                  @click="handleRegister"
                >
                  确认登记
                </ElButton>
                <ElButton
                  size="large"
                  class="action-btns__secondary"
                  :disabled="!registered"
                  @click="goCharge"
                >
                  去收费
                </ElButton>
                <ElButton size="large" text bg @click="resetNew">清空重录</ElButton>
              </div>

              <p v-if="!canRegister && !registered" class="action-hint action-hint--warn">
                {{ registerBlockReason }}
              </p>
              <p v-else-if="registered && !chargePaid" class="action-hint action-hint--ok">
                登记完成，可在「已开项目」加/减项后去收费
              </p>
            </section>
          </aside>
        </div>
      </div>

      <section class="history-panel panel">
        <div class="panel__head">
          <span>登记历史</span>
          <span class="panel__count">{{ historyTotal }}</span>
        </div>
        <div class="history-toolbar flex flex-wrap items-center gap-2">
          <ElInput
            v-model="historyKeyword"
            clearable
            class="history-toolbar__keyword w-[180px] shrink-0"
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
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            class="history-toolbar__dates w-[260px] shrink-0"
            @change="
              () => {
                historyPageNo = 1;
                loadHistory();
              }
            "
          />
          <ElRadioGroup
            class="shrink-0"
            :model-value="historyQuick"
            size="small"
            @update:model-value="(v) => applyHistoryQuick(v as 'day' | 'week' | 'month')"
          >
            <ElRadioButton value="day">今天</ElRadioButton>
            <ElRadioButton value="week">本周</ElRadioButton>
            <ElRadioButton value="month">本月</ElRadioButton>
          </ElRadioGroup>
          <ElButton class="shrink-0" :loading="historyLoading" @click="loadHistory">刷新</ElButton>
        </div>
        <div v-loading="historyLoading" class="history-table-wrap">
          <ElTable
            :data="historyRows"
            height="100%"
            size="small"
            stripe
            highlight-current-row
            empty-text="暂无登记记录"
            @row-dblclick="openHistoryRow"
          >
            <ElTableColumn prop="busNo" label="流水号" min-width="140" show-overflow-tooltip />
            <ElTableColumn prop="patientName" label="客户" min-width="100" show-overflow-tooltip />
            <ElTableColumn label="性别" width="64">
              <template #default="{ row }">
                {{ labelGender(row.genderCode) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="packageName" label="套餐" min-width="140" show-overflow-tooltip />
            <ElTableColumn
              prop="registeredAt"
              label="登记时间"
              min-width="160"
              show-overflow-tooltip
            />
            <ElTableColumn label="收费" width="88">
              <template #default="{ row }">
                <ElTag size="small" :type="chargeTagType(row)">
                  {{ chargeText(row) }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <ElButton link type="primary" @click="openHistoryRow(row)">打开</ElButton>
                <ElButton
                  link
                  type="primary"
                  :disabled="!!row.paid"
                  @click="goChargeFromHistory(row)"
                >
                  去收费
                </ElButton>
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
    </div>

    <EditDialog
      v-model:visible="patientDialogVisible"
      :cert-type-options="certTypeOptions"
      :marital-status-options="maritalStatusOptions"
      :blood-type-abo-options="bloodTypeAboOptions"
      :blood-type-rh-options="bloodTypeRhOptions"
      :sex-options="sexOptions"
      :form-data="patientDialogForm || undefined"
      @success="onPatientCreated"
    />
  </Page>
</template>

<style scoped>
.reg-page {
  display: grid;
  grid-template-rows: minmax(0, 1.15fr) minmax(220px, 0.85fr);
  gap: 10px;
  height: calc(100vh - 148px);
  min-height: 560px;
  overflow: hidden;
}

.reg {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  overflow: auto;
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
  width: 268px;
  flex: none;
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
  gap: 8px;
  padding: 8px;
  border-top: 1px solid var(--el-border-color-extra-light);
  flex: none;
  background: var(--el-fill-color-blank);
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
  min-width: 200px;
  max-width: 420px;
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

.person-subbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px 10px;
}

.person-subbar__label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex: none;
  white-space: nowrap;
}

.encounter-input {
  width: 240px;
  flex: none;
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

.action-panel {
  position: sticky;
  top: 0;
  height: 100%;
}

.summary {
  padding: 12px 12px 4px;
}

.summary__row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 13px;
}

.summary__label {
  width: 48px;
  flex: none;
  color: var(--el-text-color-secondary);
}

.summary__value {
  flex: 1;
  min-width: 0;
  word-break: break-all;
  font-weight: 500;
}

.summary__mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.action-btns {
  display: grid;
  gap: 8px;
  padding: 8px 12px;
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
  /* display/flex 由模板原子类承担，避免生产丢 scoped 后工具条塌成纵向 */
  padding: 8px 10px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  flex: none;
}

.history-toolbar__keyword {
  width: 180px;
}

.history-toolbar__dates {
  width: 260px;
}

.history-table-wrap {
  flex: 1;
  min-height: 0;
  padding: 0 4px;
}

.history-pager {
  display: flex;
  justify-content: flex-end;
  padding: 6px 10px 8px;
  flex: none;
  border-top: 1px solid var(--el-border-color-extra-light);
}

@media (max-width: 1280px) {
  .reg-page {
    height: auto;
    min-height: 0;
    grid-template-rows: auto auto;
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
