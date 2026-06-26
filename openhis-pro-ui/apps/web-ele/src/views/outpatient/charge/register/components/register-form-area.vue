<script setup lang="ts">
import type {
  OptionDto,
  RegisterBenefitDisplayPayload,
  RegisterBenefitPreviewContext,
  RegisterFormAreaExpose,
  RegisterPatientRecord,
  RegisterSlotSelectionPayload,
} from '../services';

import type {
  RegisterContractMatchDto,
  YbRegisterBenefitPreviewDto,
} from '#/api/outpatient-registration';

import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { ElAlert, ElButton, ElTabPane, ElTabs } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  collectFormFocusableElements,
  DEFAULT_FORM_KEYBOARD_SELECTORS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';
import { calcAgeFromBirthDate, parseChineseIdCard18 } from '#/utils/format/id-card-cn';

import {
  inferInsuranceCategoryFromInsuplc,
  INSURANCE_CONTRACT_CATEGORY_PRIORITY,
  normalizeInsuplcAdmdvs,
} from '../services/charge-type-category';
import {
  enrichInsuranceBenefitPreview,
  isInsuranceContractCategory,
} from '../services/register-benefit.service';
import { listPractitioners, loadRegisterFormInit } from '../services/register.service';
import { displayBenefitDash } from './register-benefit-display.utils';
import RegisterBenefitPreviewPanel from './register-benefit-preview-panel.vue';
import RegisterPatientEncounterHistory from './register-patient-encounter-history.vue';

defineOptions({
  name: 'RegisterFormArea',
});

const props = defineProps<{
  /** 当前选中患者，供历史就诊 Tab 查询 */
  activePatientId?: string;
  /** 待遇信息后台拉取中（含读卡/扫码） */
  benefitLoading?: boolean;
  benefitLoadingText?: string;
}>();

const emit = defineEmits<{
  contractChange: [category: string];
  departmentChange: [organizationId: string];
  doctorChange: [practitionerId: string];
  serviceTypeChange: [serviceTypeId: string];
}>();

function handleContractChange(contractId: unknown) {
  const category = getContractCategory(contractId) ?? '';
  emit('contractChange', category);
}

function handleRegisterValuesChange(values: Record<string, unknown>, fieldsChanged: string[]) {
  if (!fieldsChanged.includes('contractId')) {
    return;
  }
  const category = getContractCategory(values.contractId) ?? '';
  emit('contractChange', category);
}

/** 科室下拉：label 展示名，value 与主数据 id 一致 */
const departmentOptions = ref<OptionDto[]>([]);

/** IF-REG-05 拉取的可选医生 */
const doctorOptions = ref<OptionDto[]>([]);

/** 费用性质下拉 */
const contractOptions = ref<OptionDto[]>([]);

/** 费用性质主键 → 合同类别 */
const contractCategoryById = ref<Record<string, string>>({});

/** 费用性质主键 → 医保通道概要 */
const contractInsuranceProfileById = ref<
  Record<string, import('#/api/outpatient-registration').ContractInsuranceProfileDto>
>({});

/** 参保地与所选费用性质匹配结果 */
const contractMatch = ref<null | RegisterContractMatchDto>(null);

/** IF-REG-01 号源类型（OptionDto.value 即 serviceTypeId） */
const serviceTypeOptions = ref<OptionDto[]>([]);

/** 号源类型主键 → 挂号费销售价（元），来自初始化接口 */
const serviceTypeFeeById = ref<Record<string, number | string>>({});

/** 初始化接口下发的患者性别字典（Radio 选项） */
const genderOptions = ref<OptionDto[]>([]);

/** 预约带入后锁定科室/医生/号源类型 */
const bookingLocked = ref(false);

/** 表单区展示用金额占位（实际金额以预结算 / 正式结算接口为准） */
const registerFeeAmount = ref(0);

/**
 * 按科室拉取 IF-REG-05 医生列表；不传科室则 outpatient 全科规则由后端解析。
 */
const refreshDoctorOptions = async (organizationId?: string) => {
  const res =
    organizationId !== undefined && organizationId !== ''
      ? await listPractitioners({ organizationId })
      : await listPractitioners({});
  doctorOptions.value = res.practitionerOptions;
  registerFormApi.updateSchema([
    {
      fieldName: 'doctorId',
      componentProps: {
        options: doctorOptions.value,
      },
    },
  ]);
  registerFormApi.setFieldValue('doctorId', undefined);
};

/**
 * 医师变更：收窄号源类型并刷新时段提示。
 */
async function handleDoctorChange(value: string | undefined) {
  if (bookingLocked.value) {
    return;
  }
  emit('doctorChange', value || '');
  const values = await registerFormApi.getValues();
  const organizationId = String(values.departmentId ?? '').trim();
  await refreshServiceTypeOptions(organizationId || undefined, value || undefined);
  registerFormApi.setFieldValue('serviceTypeId', undefined);
  emit('serviceTypeChange', '');
  registerFeeAmount.value = 0;
}

const formWrapperClass = 'grid grid-cols-2 gap-x-2 gap-y-1';
const formAreaRef = ref<HTMLElement | null>(null);

/** 挂号表单区键盘导航：与项目统一 Hook 对齐，并补上 tabindex 类控件与禁用态过滤。 */
const registerFormKeyboardSelectors = `${DEFAULT_FORM_KEYBOARD_SELECTORS},[tabindex]:not([tabindex="-1"]):not([disabled])`;

const { handleFormKeydown } = useFormKeyboardNavigation(formAreaRef, {
  selectors: registerFormKeyboardSelectors,
  selectPolicy: true,
  shouldIgnoreEvent: (_event, target) => {
    if (target.tagName === 'TEXTAREA') {
      return true;
    }
    if (target.closest('.is-disabled')) {
      return true;
    }
    if (target.getAttribute('aria-hidden') === 'true') {
      return true;
    }
    return false;
  },
});

/**
 * 将焦点移到第一个可聚焦项（选完患者后由父组件调用）。
 */
async function focusFirstField() {
  await nextTick();
  await nextTick();
  // 等布局与表单项渲染稳定后再取焦点，双 rAF 减少取不到节点的情况
  await new Promise<void>((resolve) => {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => resolve());
    });
  });
  const root = formAreaRef.value;
  if (!root) {
    return;
  }
  collectFormFocusableElements(root, registerFormKeyboardSelectors)[0]?.focus();
}

/**
 * 身份证号输满 18 位时写入性别、出生日期、年龄（规则见 `#/utils/format/id-card-cn`）。
 */
function handleIdCardInput(value: string) {
  const parsed = parseChineseIdCard18(value);
  if (!parsed) {
    return;
  }
  patientFormApi.setFieldValue('patientGender', parsed.genderCode);
  patientFormApi.setFieldValue('patientBirthday', parsed.birthDate);
  patientFormApi.setFieldValue('medicalInsuranceNo', calcAgeFromBirthDate(parsed.birthDate));
}

/**
 * 科室变更：按 IF-REG-05 重拉医生下拉。
 */
async function handleDepartmentChange(value: string) {
  if (bookingLocked.value) {
    return;
  }
  emit('departmentChange', value || '');
  emit('doctorChange', '');
  emit('serviceTypeChange', '');
  await refreshDoctorOptions(value || undefined);
  await refreshServiceTypeOptions(value || undefined, undefined);
}

/**
 * 按科室/医师收窄号源类型下拉（booking 启用时与当日放号求交）。
 */
async function refreshServiceTypeOptions(organizationId?: string, practitionerId?: string) {
  const init = await loadRegisterFormInit({
    organizationId: organizationId || undefined,
    practitionerId: practitionerId || undefined,
  });
  serviceTypeOptions.value = [...init.serviceTypeOptions];
  serviceTypeFeeById.value = { ...init.serviceTypeFeeById };
  registerFormApi.updateSchema([
    {
      fieldName: 'serviceTypeId',
      componentProps: {
        disabled: bookingLocked.value,
        options: serviceTypeOptions.value,
        onChange: handleServiceTypeChange,
      },
    },
  ]);
}

/**
 * 更新挂号区三要素字段的禁用态（预约带入后锁定）。
 */
function syncBookingFieldLockState() {
  const disabled = bookingLocked.value;
  registerFormApi.updateSchema([
    {
      fieldName: 'departmentId',
      componentProps: {
        disabled,
        options: departmentOptions.value,
        onChange: handleDepartmentChange,
      },
    },
    {
      fieldName: 'doctorId',
      componentProps: {
        disabled,
        options: doctorOptions.value,
        onChange: handleDoctorChange,
      },
    },
    {
      fieldName: 'serviceTypeId',
      componentProps: {
        disabled,
        options: serviceTypeOptions.value,
        onChange: handleServiceTypeChange,
      },
    },
  ]);
}

/**
 * 预约快照预填科室/医生/号源并锁定。
 */
async function applyBookingSnapshot(snapshot: {
  healthcareServiceId: number | string;
  organizationId: number | string;
  practitionerId?: null | number | string;
}) {
  bookingLocked.value = true;
  const organizationId = String(snapshot.organizationId ?? '');
  await refreshDoctorOptions(organizationId || undefined);
  registerFormApi.setFieldValue('departmentId', organizationId || undefined);
  emit('departmentChange', organizationId);
  registerFormApi.setFieldValue(
    'doctorId',
    snapshot.practitionerId !== null &&
      snapshot.practitionerId !== undefined &&
      String(snapshot.practitionerId) !== ''
      ? String(snapshot.practitionerId)
      : undefined,
  );
  emit(
    'doctorChange',
    snapshot.practitionerId !== null &&
      snapshot.practitionerId !== undefined &&
      String(snapshot.practitionerId) !== ''
      ? String(snapshot.practitionerId)
      : '',
  );
  registerFormApi.setFieldValue('serviceTypeId', String(snapshot.healthcareServiceId ?? ''));
  emit('serviceTypeChange', String(snapshot.healthcareServiceId ?? ''));
  handleServiceTypeChange(String(snapshot.healthcareServiceId ?? ''));
  syncBookingFieldLockState();
}

/**
 * 号源日历点选：仅预填左侧科室/医师/号源类型，不锁定以便收费员微调；
 * 不向右侧当日号源同步筛选条件，避免点选后上方查询条被改写。
 */
async function applySlotSelection(payload: RegisterSlotSelectionPayload) {
  bookingLocked.value = false;
  const organizationId = String(payload.organizationId ?? '');
  const practitionerId =
    payload.practitionerId !== null &&
    payload.practitionerId !== undefined &&
    String(payload.practitionerId) !== ''
      ? String(payload.practitionerId)
      : undefined;
  const healthcareServiceId = String(payload.healthcareServiceId ?? '');
  await refreshDoctorOptions(organizationId || undefined);
  await refreshServiceTypeOptions(organizationId || undefined, practitionerId);
  registerFormApi.setFieldValue('departmentId', organizationId || undefined);
  registerFormApi.setFieldValue('doctorId', practitionerId);
  registerFormApi.setFieldValue('serviceTypeId', healthcareServiceId || undefined);
  updateRegisterFeeDisplay(healthcareServiceId || undefined);
  syncBookingFieldLockState();
}

/**
 * 解除预约锁定。
 */
function clearBookingLock() {
  bookingLocked.value = false;
  syncBookingFieldLockState();
}

/** 按号源类型刷新展示用挂号费（不触发右侧筛选同步） */
function updateRegisterFeeDisplay(value: string | undefined) {
  if (!value) {
    registerFeeAmount.value = 0;
    return;
  }
  const raw = serviceTypeFeeById.value[value];
  const fee = Number(raw);
  registerFeeAmount.value = Number.isFinite(fee) ? fee : 0;
}

/**
 * 号源类型变更：按初始化下发的销售价映射刷新展示用挂号费。
 */
function handleServiceTypeChange(value: string | undefined) {
  emit('serviceTypeChange', value || '');
  updateRegisterFeeDisplay(value);
}

const [PatientForm, patientFormApi] = useVbenForm(
  reactive({
    commonConfig: {
      componentProps: {
        style: { width: '100%' },
      },
    },
    layout: 'horizontal',
    wrapperClass: formWrapperClass,
    schema: computed(() => [
      {
        component: 'Input',
        fieldName: 'patientName',
        label: '患者姓名',
        required: true,
        componentProps: {
          placeholder: '请输入患者姓名',
          clearable: true,
        },
      },
      {
        component: 'Input',
        fieldName: 'patientIdCard',
        label: '身份证号',
        required: true,
        componentProps: {
          placeholder: '请输入或扫描身份证号',
          clearable: true,
          maxlength: 18,
          onInput: handleIdCardInput,
        },
      },
      {
        component: 'Input',
        fieldName: 'patientPhone',
        label: '联系电话',
        componentProps: {
          placeholder: '请输入联系电话',
          clearable: true,
          maxlength: 11,
        },
      },
      {
        component: 'RadioGroup',
        fieldName: 'patientGender',
        label: '性别',
        required: true,
        componentProps: {
          options: genderOptions.value,
        },
      },
      {
        component: 'DatePicker',
        fieldName: 'patientBirthday',
        label: '出生日期',
        componentProps: {
          type: 'date',
          placeholder: '请选择出生日期',
          style: { width: '100%' },
          format: 'YYYY-MM-DD',
          valueFormat: 'YYYY-MM-DD',
        },
      },
      {
        component: 'Input',
        fieldName: 'medicalInsuranceNo',
        label: '年龄',
        componentProps: {
          placeholder: '请输入年龄',
          clearable: true,
        },
      },
    ]),
    showDefaultActions: false,
  }),
);

const [RegisterForm, registerFormApi] = useVbenForm(
  reactive({
    commonConfig: {
      componentProps: {
        style: { width: '100%' },
      },
    },
    handleValuesChange: handleRegisterValuesChange,
    layout: 'horizontal',
    wrapperClass: formWrapperClass,
    schema: computed(() => [
      {
        component: 'Select',
        fieldName: 'departmentId',
        label: '科室',
        required: true,
        componentProps: {
          placeholder: '请选择科室',
          filterable: true,
          options: departmentOptions.value,
          onChange: handleDepartmentChange,
        },
      },
      {
        component: 'Select',
        fieldName: 'doctorId',
        label: '医生',
        required: true,
        componentProps: {
          placeholder: '请选择医生',
          filterable: true,
          options: doctorOptions.value,
        },
      },
      {
        component: 'Select',
        fieldName: 'contractId',
        label: '费用性质',
        required: true,
        componentProps: {
          placeholder: '请选择费用性质',
          filterable: true,
          options: contractOptions.value,
          onChange: handleContractChange,
        },
      },
      {
        component: 'Select',
        fieldName: 'serviceTypeId',
        label: '号源类型',
        required: true,
        componentProps: {
          options: serviceTypeOptions.value,
          onChange: handleServiceTypeChange,
        },
      },
    ]),
    showDefaultActions: false,
  }),
);

/** 下方「待遇确认」只读展示区（费用性质切换后异步灌入） */
function emptyBenefitInfo(): RegisterBenefitDisplayPayload {
  return { kind: '' };
}

const benefitInfo = ref<RegisterBenefitDisplayPayload>(emptyBenefitInfo());

/** 1101/2001/5301/5206 完整预览（下方 Tab 展示） */
const benefitYbPreview = ref<null | YbRegisterBenefitPreviewDto>(null);

/** 切换多险种时重查 2001 */
const benefitRefreshContext = ref<RegisterBenefitPreviewContext>({});

const benefitSectionTab = ref<'benefit' | 'history'>('benefit');

watch(
  () => props.benefitLoading,
  (loading) => {
    if (loading) {
      benefitSectionTab.value = 'benefit';
    }
  },
);

/** 无档案患者：用证件号查历史就诊 */
const historyPatientKeyword = ref('');

const hasBenefitDisplay = computed(() => {
  const info = benefitInfo.value;
  if (info.kind === 'INSURANCE') {
    return Boolean(benefitYbPreview.value?.psnNo || info.psnNo || info.cardNo || info.psnName);
  }
  if (info.kind === 'STUDENT') {
    return Boolean(info.studentNo || info.studentName);
  }
  return false;
});

const showContractMismatch = computed(
  () => Boolean(contractMatch.value?.matchStatus) && contractMatch.value?.matchStatus !== 'MATCH',
);

function displayBenefitDashLocal(value: null | number | string | undefined): string {
  return displayBenefitDash(value);
}

/**
 * 快捷按钮：写入科室并触发与手动选科室相同的医生列表联动。
 */
const quickSelectDepartment = async (departmentId: string) => {
  registerFormApi.setFieldValue('departmentId', departmentId);
  await handleDepartmentChange(departmentId);
};

/** 头部快捷科室：取前 4 条主数据，减少长列表点击成本 */
const commonDepartments = computed(() => departmentOptions.value.slice(0, 4));

/**
 * 表单清空后：刷新医生下拉（无科室筛选）并将展示用挂号费归零。
 * 不在此预填科室/医生/费用性质/号源首条（避免联调期默认值残留）。
 */
async function refreshDoctorsAndResetFeeDisplay() {
  await refreshDoctorOptions();
  registerFeeAmount.value = 0;
}

/**
 * 校验患者信息表单与挂号信息表单是否均通过。
 */
async function validateForSubmit(): Promise<boolean> {
  const patientValid = await patientFormApi.validate();
  const registerValid = await registerFormApi.validate();
  return Boolean(patientValid && registerValid);
}

/**
 * 读取两块表单当前值，并补上展示用挂号费（确认框仍以预结算 totalAmount 为准）。
 */
async function getSubmitValues(): Promise<Record<string, unknown>> {
  const patientValues = await patientFormApi.getValues();
  const registerValues = await registerFormApi.getValues();
  return {
    ...patientValues,
    ...registerValues,
    fee: registerFeeAmount.value,
  };
}

/**
 * 根据科室下拉 value 取展示用 label。
 */
function getDepartmentLabel(departmentId: unknown): string | undefined {
  return departmentOptions.value.find((d) => d.value === String(departmentId ?? ''))?.label;
}

/**
 * 根据医生下拉 value 取展示用 label（注意依赖当前科室筛选后的 doctorOptions）。
 */
function getDoctorLabel(doctorId: unknown): string | undefined {
  return doctorOptions.value.find((d) => d.value === String(doctorId ?? ''))?.label;
}

/**
 * 按费用性质下拉 value 取展示用 label。
 */
function getContractLabel(contractId: unknown): string | undefined {
  return contractOptions.value.find((d) => d.value === String(contractId ?? ''))?.label;
}

function getContractCategory(contractId: unknown): string | undefined {
  return contractCategoryById.value[String(contractId ?? '')];
}

function findBootstrapInsuranceContractId(): string | undefined {
  for (const code of INSURANCE_CONTRACT_CATEGORY_PRIORITY) {
    const matched = Object.entries(contractCategoryById.value).find(([, cat]) => cat === code);
    if (matched) {
      return matched[0];
    }
  }
  return Object.entries(contractCategoryById.value).find(([, cat]) =>
    isInsuranceContractCategory(cat),
  )?.[0];
}

/** 按发卡地/参保地前缀推荐医保费用性质（读卡后首次 1101 用）。 */
function findBootstrapInsuranceContractIdByInsuplc(insuplcAdmdvs?: string): string | undefined {
  const insu = normalizeInsuplcAdmdvs(insuplcAdmdvs);
  if (insu.length >= 4) {
    const preferredCategory = inferInsuranceCategoryFromInsuplc(insu);
    if (preferredCategory) {
      const matched = Object.entries(contractCategoryById.value).find(
        ([, cat]) => cat === preferredCategory,
      );
      if (matched) {
        return matched[0];
      }
    }
    let bestId: string | undefined;
    let bestLen = -1;
    for (const [id, profile] of Object.entries(contractInsuranceProfileById.value)) {
      const treatment = normalizeInsuplcAdmdvs(profile.treatmentAreaCode);
      if (!treatment || !isInsuranceContractCategory(contractCategoryById.value[id])) {
        continue;
      }
      let len = 0;
      while (len < Math.min(insu.length, treatment.length) && insu[len] === treatment[len]) {
        len += 1;
      }
      if (len > bestLen) {
        bestLen = len;
        bestId = id;
      }
    }
    if (bestId && bestLen >= 4) {
      return bestId;
    }
  }
  return findBootstrapInsuranceContractId();
}

function getContractBusNo(contractId: unknown): string | undefined {
  const profile = contractInsuranceProfileById.value[String(contractId ?? '')];
  return profile?.busNo?.trim() || undefined;
}

function applyContractMatch(match: null | RegisterContractMatchDto | undefined) {
  contractMatch.value = match ?? null;
}

async function applyContractId(contractId: string) {
  registerFormApi.setFieldValue('contractId', contractId);
  handleContractChange(contractId);
}

async function applyRecommendedContract() {
  const recommendedId = contractMatch.value?.recommendedContractId;
  if (!recommendedId) {
    return;
  }
  await applyContractId(recommendedId);
}

async function applyBenefitPreview(
  payload: RegisterBenefitDisplayPayload,
  ybPreview?: YbRegisterBenefitPreviewDto,
  refreshContext?: RegisterBenefitPreviewContext,
) {
  benefitRefreshContext.value = refreshContext ?? {};
  if (payload.kind === 'INSURANCE') {
    const patientValues = await patientFormApi.getValues();
    const registerValues = await registerFormApi.getValues();
    const patientFallback = {
      patientBirthday: String(patientValues.patientBirthday ?? '').trim() || undefined,
      patientGender: String(patientValues.patientGender ?? '').trim() || undefined,
      patientIdCard: String(patientValues.patientIdCard ?? '').trim() || undefined,
      patientName: String(patientValues.patientName ?? '').trim() || undefined,
    };
    const enriched: RegisterBenefitDisplayPayload = { ...payload };
    if (!enriched.psnName?.trim()) {
      enriched.psnName = patientFallback.patientName;
    }
    if (!enriched.certno?.trim()) {
      enriched.certno = patientFallback.patientIdCard;
    }
    benefitInfo.value = {
      ...emptyBenefitInfo(),
      ...enriched,
    };
    benefitYbPreview.value = ybPreview
      ? enrichInsuranceBenefitPreview(ybPreview, patientFallback)
      : null;
    if (!benefitRefreshContext.value.contractId) {
      benefitRefreshContext.value = {
        ...benefitRefreshContext.value,
        contractId: String(registerValues.contractId ?? '').trim() || undefined,
      };
    }
    return;
  }
  benefitInfo.value = {
    ...emptyBenefitInfo(),
    ...payload,
  };
  benefitYbPreview.value = null;
  benefitRefreshContext.value = {};
}

function clearBenefitPreview() {
  benefitInfo.value = emptyBenefitInfo();
  benefitYbPreview.value = null;
  benefitRefreshContext.value = {};
  contractMatch.value = null;
}

/**
 * 按号源类型下拉 value 取展示用 label（挂号类型展示名）。
 */
function getServiceTypeLabel(serviceTypeId: unknown): string | undefined {
  return serviceTypeOptions.value.find((d) => d.value === String(serviceTypeId ?? ''))?.label;
}

/**
 * 提交成功后的局部重置：清空患者与挂号表单，再刷新医生下拉并重置展示用挂号费（不预填挂号区）。
 */
async function resetForSubmitSuccess() {
  bookingLocked.value = false;
  patientFormApi.resetForm();
  registerFormApi.resetForm();
  registerFeeAmount.value = 0;
  benefitInfo.value = emptyBenefitInfo();
  benefitYbPreview.value = null;
  benefitRefreshContext.value = {};
  contractMatch.value = null;
  historyPatientKeyword.value = '';
  await refreshDoctorsAndResetFeeDisplay();
  syncBookingFieldLockState();
}

/**
 * 整页清空：表单与待遇确认展示区清空；医生下拉恢复为不按科室过滤的全量列表。
 */
function resetForClear() {
  bookingLocked.value = false;
  patientFormApi.resetForm();
  registerFormApi.resetForm();
  benefitInfo.value = emptyBenefitInfo();
  benefitYbPreview.value = null;
  benefitRefreshContext.value = {};
  contractMatch.value = null;
  historyPatientKeyword.value = '';
  void refreshDoctorsAndResetFeeDisplay();
  syncBookingFieldLockState();
}

/**
 * 仅清空患者信息表单（患者下拉被清空时由父组件调用）。
 */
function clearPatientFields() {
  historyPatientKeyword.value = '';
  patientFormApi.setValues({
    patientName: '',
    patientPhone: '',
    patientGender: undefined,
    patientBirthday: '',
    patientIdCard: '',
    medicalInsuranceNo: '',
  });
}

/**
 * 把父页选中的患者写入患者信息表单。
 */
function applyPatientRecord(patient: RegisterPatientRecord) {
  historyPatientKeyword.value = (patient.patientIdCard ?? '').trim();
  patientFormApi.setValues({
    patientName: patient.patientName,
    patientPhone: patient.patientPhone,
    patientGender: patient.patientGender,
    patientBirthday: patient.patientBirthday,
    patientIdCard: patient.patientIdCard,
    medicalInsuranceNo: patient.patientAge ?? '',
  });
}

defineExpose<RegisterFormAreaExpose>({
  applyBookingSnapshot,
  applySlotSelection,
  applyPatientRecord,
  applyBenefitPreview,
  applyContractId,
  applyContractMatch,
  applyRecommendedContract,
  clearBenefitPreview,
  clearBookingLock,
  clearPatientFields,
  findBootstrapInsuranceContractId,
  findBootstrapInsuranceContractIdByInsuplc,
  getContractBusNo,
  focusFirstField,
  getContractCategory,
  getContractLabel,
  getDepartmentLabel,
  getDoctorLabel,
  getServiceTypeLabel,
  getSubmitValues,
  resetForClear,
  resetForSubmitSuccess,
  validateForSubmit,
});

/**
 * 拉取科室/费用性质/医生/号源类型主数据并灌入下拉；绑定键盘导航；挂号区不预填首条主数据。
 */
onMounted(async () => {
  const init = await loadRegisterFormInit({});
  departmentOptions.value = [...init.departmentOptions];
  contractOptions.value = [...init.contractOptions];
  contractCategoryById.value = { ...init.contractCategoryById };
  contractInsuranceProfileById.value = { ...init.contractInsuranceProfileById };
  serviceTypeOptions.value = [...init.serviceTypeOptions];
  serviceTypeFeeById.value = { ...init.serviceTypeFeeById };
  genderOptions.value = [...init.genderOptions];
  await refreshDoctorOptions();
  patientFormApi.updateSchema([
    {
      fieldName: 'patientGender',
      componentProps: {
        options: genderOptions.value,
      },
    },
  ]);
  registerFormApi.updateSchema([
    {
      fieldName: 'departmentId',
      componentProps: {
        options: departmentOptions.value,
      },
    },
    {
      fieldName: 'contractId',
      componentProps: {
        options: contractOptions.value,
        onChange: handleContractChange,
      },
    },
    {
      fieldName: 'serviceTypeId',
      componentProps: {
        options: serviceTypeOptions.value,
        onChange: handleServiceTypeChange,
      },
    },
    {
      fieldName: 'doctorId',
      componentProps: {
        options: doctorOptions.value,
        onChange: handleDoctorChange,
      },
    },
  ]);
  registerFeeAmount.value = 0;
});
</script>

<template>
  <div
    ref="formAreaRef"
    class="reg-bill-form-area flex min-h-0 flex-1 flex-col overflow-y-auto px-2 py-1"
    @keydown.capture="handleFormKeydown"
  >
    <div class="space-y-2">
      <div>
        <SectionTitle class="mb-1" title="患者信息" />
        <PatientForm />
      </div>

      <div class="border-t pt-2">
        <div class="mb-1 flex items-center justify-between">
          <SectionTitle title="挂号信息" />
          <div class="flex flex-wrap gap-1">
            <ElButton
              v-for="dept in commonDepartments"
              :key="dept.value"
              type="default"
              @click="quickSelectDepartment(dept.value)"
            >
              {{ dept.label }}
            </ElButton>
          </div>
        </div>
        <RegisterForm />
        <div class="ml-auto mt-2 flex w-1/2 items-baseline justify-end gap-2">
          <span class="shrink-0 text-base text-gray-500">挂号费用</span>
          <span class="text-xl font-semibold tabular-nums text-[var(--el-color-primary)]">
            ¥{{ registerFeeAmount.toFixed(2) }}
          </span>
        </div>
      </div>

      <ElTabs v-model="benefitSectionTab" class="benefit-section-tabs">
        <ElTabPane label="待遇信息" name="benefit">
          <div
            v-loading="benefitLoading"
            :element-loading-text="benefitLoadingText || '正在拉取待遇信息…'"
            class="benefit-tab-panel min-h-[140px] border-t bg-[#f8fafc] px-2 pb-2 pt-2"
          >
            <div
              v-if="!benefitLoading && !hasBenefitDisplay"
              class="py-6 text-center text-sm text-gray-400"
            >
              切换医保/公费费用性质后将自动加载；医保类请先读身份证、医保卡或医保码
            </div>
            <div v-else-if="hasBenefitDisplay" class="benefit-tab-body space-y-2">
              <template v-if="benefitInfo.kind === 'INSURANCE'">
                <ElAlert
                  v-if="showContractMismatch && contractMatch?.hintMessage"
                  type="warning"
                  :closable="false"
                  show-icon
                >
                  <div class="flex flex-wrap items-center gap-2">
                    <span>{{ contractMatch.hintMessage }}</span>
                    <ElButton
                      v-if="contractMatch.recommendedContractName"
                      link
                      type="primary"
                      @click="applyRecommendedContract"
                    >
                      改用 {{ contractMatch.recommendedContractName }}
                    </ElButton>
                  </div>
                </ElAlert>
                <RegisterBenefitPreviewPanel
                  v-if="benefitYbPreview"
                  :preview="benefitYbPreview"
                  :card-no="benefitInfo.cardNo"
                  :medium-type="benefitInfo.mediumType"
                  :contract-id="benefitRefreshContext.contractId"
                  :yb-card-info="benefitRefreshContext.ybCardInfo"
                  :yb-card-sn="benefitRefreshContext.ybCardSn"
                  :yb-certno="benefitRefreshContext.ybCertno"
                  :yb-medium-type="benefitRefreshContext.ybMediumType"
                  :scrollable-tables="false"
                />
              </template>
              <div
                v-else-if="benefitInfo.kind === 'STUDENT'"
                class="grid grid-cols-3 gap-x-2 gap-y-1 text-sm"
              >
                <div>
                  <div class="mb-0.5 text-gray-500">姓名</div>
                  <div class="font-medium">
                    {{ displayBenefitDashLocal(benefitInfo.studentName) }}
                  </div>
                </div>
                <div>
                  <div class="mb-0.5 text-gray-500">学号</div>
                  <div class="font-medium">
                    {{ displayBenefitDashLocal(benefitInfo.studentNo) }}
                  </div>
                </div>
                <div>
                  <div class="mb-0.5 text-gray-500">院系</div>
                  <div class="font-medium">{{ displayBenefitDashLocal(benefitInfo.college) }}</div>
                </div>
                <div class="col-span-3">
                  <div class="mb-0.5 text-gray-500">公费资格</div>
                  <div class="font-medium">
                    {{
                      benefitInfo.eligible === true
                        ? '符合'
                        : benefitInfo.eligible === false
                          ? displayBenefitDashLocal(benefitInfo.eligibilityMessage || '不符合')
                          : '—'
                    }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </ElTabPane>
        <ElTabPane label="历史就诊" name="history">
          <div class="border-t bg-[#f8fafc] px-2 pb-2 pt-2">
            <RegisterPatientEncounterHistory
              :patient-id="props.activePatientId"
              :patient-keyword="historyPatientKeyword"
            />
          </div>
        </ElTabPane>
      </ElTabs>
    </div>
  </div>
</template>

<style scoped>
.reg-bill-form-area :deep(.el-form-item__label) {
  padding-right: 8px;
  color: #020817;
  font-weight: 500;
  line-height: 28px;
}

.reg-bill-form-area :deep(.el-form-item__content) {
  line-height: 28px;
}

.reg-bill-form-area :deep(.el-form-item) {
  margin-bottom: 6px;
}

.reg-bill-form-area :deep(.el-input__wrapper) {
  min-height: 30px;
  padding-top: 2px;
  padding-bottom: 2px;
}

.reg-bill-form-area :deep(.el-select .el-select__wrapper) {
  min-height: 30px;
  padding-top: 2px;
  padding-bottom: 2px;
}

.reg-bill-form-area :deep(.el-radio) {
  margin-right: 14px;
  height: 28px;
}

.benefit-section-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.benefit-section-tabs :deep(.el-tabs__item) {
  height: 32px;
  line-height: 32px;
  font-size: 13px;
}

.reg-bill-form-area :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}
</style>
