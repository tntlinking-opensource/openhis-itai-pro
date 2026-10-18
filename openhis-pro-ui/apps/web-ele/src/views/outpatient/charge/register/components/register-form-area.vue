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

import { isSameBookingId } from '#/api/booking/booking-id';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';

import { ElAlert, ElButton, ElTabPane, ElTabs } from 'element-plus';
import { z } from 'zod';

import { useVbenForm } from '#/adapter/form';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  collectFormFocusableElements,
  DEFAULT_FORM_KEYBOARD_SELECTORS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';
import {
  birthDateFromAgeYears,
  calcAgeFromBirthDate,
  isAcceptableChineseIdCardInput,
  parseChineseIdCard18,
} from '#/utils/format/id-card-cn';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { REGISTRATION_DOCTOR_REQUIRED } from '#/views/outpatient/charge/register/constants/register-doctor.config';
import { REGISTRATION_ID_CARD_REQUIRED } from '#/views/outpatient/charge/register/constants/register-id-card.config';
import { REGISTRATION_PHONE_REQUIRED } from '#/views/outpatient/charge/register/constants/register-phone.config';

/** 中国大陆手机号：1 开头第二位 3–9，共 11 位（与后端 PHONE_REGEX 对齐） */
const CN_MOBILE_PHONE_REGEX = /^1[3-9]\d{9}$/;

import {
  inferInsuranceCategoryFromInsuplc,
  INSURANCE_CONTRACT_CATEGORY_PRIORITY,
  isSelfPayContractCategory,
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
import WorkInjuryBenefitSummary from '#/chs/work-injury/work-injury-benefit-summary.vue';
import WorkInjuryFilingDetailAction from '#/chs/work-injury/work-injury-filing-detail-action.vue';
import { isWorkInjuryUiEnabled } from '#/utils/project-optional-modules';

defineOptions({
  name: 'RegisterFormArea',
});

const workInjuryFilingActionRef = ref<InstanceType<typeof WorkInjuryFilingDetailAction> | null>(
  null,
);

function openWorkInjuryApprovalGuide() {
  workInjuryFilingActionRef.value?.openApprovalGuide();
}

/** 挂号门禁：无有效备案则引导备案，成功返回 true。 */
async function ensureWorkInjuryFilingReady(): Promise<boolean> {
  return (await workInjuryFilingActionRef.value?.ensureFilingReady()) ?? false;
}

function getWorkInjuryBenefitSummary() {
  return benefitInfo.value.workInjury;
}

const props = withDefaults(
  defineProps<{
    /** 当前选中患者，供历史就诊 Tab 查询 */
    activePatientId?: string;
    /** 人员/省市医保同步拉取中（含读卡/扫码） */
    benefitLoading?: boolean;
    benefitLoadingText?: string;
    /** 待遇明细（2001/5301/5206）异步拉取中 */
    treatmentLoading?: boolean;
    /** 分栏布局：左患者信息、右挂号信息（对齐新挂号页线框） */
    layout?: 'split' | 'stacked';
    /** 当前费用性质是否为工伤（按钮可见性，不依赖待遇 kind）。 */
    workInjurySelected?: boolean;
    /** 工伤预结算后的就诊 ID（供备案详情 / 2301）。 */
    workInjuryPreEncounterId?: string | number;
    workInjuryPreContractId?: string | number;
  }>(),
  {
    activePatientId: undefined,
    benefitLoading: false,
    benefitLoadingText: '',
    treatmentLoading: false,
    layout: 'split',
    workInjurySelected: false,
    workInjuryPreEncounterId: undefined,
    workInjuryPreContractId: undefined,
  },
);

const emit = defineEmits<{
  contractChange: [category: string];
  departmentChange: [organizationId: string];
  doctorChange: [practitionerId: string];
  /** 改了姓名/证件/性别/生日，不再沿用选中患者主键 */
  patientIdentityDetach: [{ idCardChanged: boolean }];
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

/** 号源类型主键 → 挂号费销售价（元），来自初始化接口（全量 ACTIVE，不随医师收窄） */
const serviceTypeFeeById = ref<Record<string, number | string>>({});

/** 初始化接口下发的患者性别字典（Radio 选项） */
const genderOptions = ref<OptionDto[]>([]);

/** 预约带入后锁定科室/医生/号源类型 */
const bookingLocked = ref(false);

/**
 * 号源类型下拉是否可选（须先选科室或医生；预约锁定时不可改）。
 * 放进 computed schema，避免写死 disabled:true 被 options 刷新冲掉 updateSchema。
 */
const serviceTypeSelectEnabled = ref(false);

/** 号源类型下拉占位文案 */
const serviceTypeSelectPlaceholder = ref('请先选择科室或医生');

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
  // 程序性清空勿校验：否则国基必填时一进页/换科室就红框
  registerFormApi.setFieldValue('doctorId', undefined, false);
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
  // 联动清空号源类型，勿触发必填红框
  registerFormApi.setFieldValue('serviceTypeId', undefined, false);
  emit('serviceTypeChange', '');
  registerFeeAmount.value = 0;
  await syncServiceTypeFieldState();
}

const patientFormWrapperClass = 'register-patient-grid';
const registerFormWrapperClass = 'register-reg-grid';
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
 * 跳过患者工具栏搜索框，避免焦点回到 SelectTable 再次打开下拉。
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
  const focusables = collectFormFocusableElements(root, registerFormKeyboardSelectors);
  const target = focusables.find(
    (el) => !el.closest('.register-workspace-layout__patient-toolbar'),
  );
  target?.focus();
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
 * 年龄变更：未填身份证时回写出生日期（年龄↔生日双向联动）。
 */
function handleAgeInput(value: string) {
  void (async () => {
    const values = await patientFormApi.getValues();
    if (String(values.patientIdCard ?? '').trim()) {
      return;
    }
    const ageText = String(value ?? '').trim();
    if (ageText === '' || Number.isNaN(Number(ageText))) {
      return;
    }
    const age = Number(ageText);
    if (age < 0 || age > 150) {
      return;
    }
    patientFormApi.setFieldValue('patientBirthday', birthDateFromAgeYears(age));
  })();
}

/**
 * 出生日期变更：未填身份证时回写年龄（年龄↔生日双向联动）。
 */
function handleBirthdayChange(value: string) {
  void (async () => {
    const values = await patientFormApi.getValues();
    if (String(values.patientIdCard ?? '').trim()) {
      return;
    }
    const birthday = String(value ?? '').trim();
    if (!birthday) {
      return;
    }
    const ageText = calcAgeFromBirthDate(birthday);
    if (!ageText) {
      return;
    }
    patientFormApi.setFieldValue('medicalInsuranceNo', ageText);
  })();
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
  // 联动清空号源类型，勿触发必填红框
  registerFormApi.setFieldValue('serviceTypeId', undefined, false);
  registerFeeAmount.value = 0;
  await syncServiceTypeFieldState();
}

/**
 * 按科室/医师重拉号源类型（下拉按 BR-REG-014 收窄；费用 map 为全量，合并写入以免丢价）。
 */
async function refreshServiceTypeOptions(organizationId?: string, practitionerId?: string) {
  const init = await loadRegisterFormInit({
    organizationId: organizationId || undefined,
    practitionerId: practitionerId || undefined,
  });
  serviceTypeOptions.value = [...init.serviceTypeOptions];
  // 合并全量费用 map，避免收窄下拉后页旁查不到已绑诊查费价格
  serviceTypeFeeById.value = {
    ...serviceTypeFeeById.value,
    ...(init.serviceTypeFeeById ?? {}),
  };
  await syncServiceTypeFieldState();
  // 费用 map 刷新后按当前选中号源重算页旁金额
  const values = await registerFormApi.getValues();
  updateRegisterFeeDisplay(String(values.serviceTypeId ?? '').trim() || undefined);
}

/** 须先选科室或医生后才允许选号源（预约锁定除外）。 */
async function syncServiceTypeFieldState() {
  const values = await registerFormApi.getValues();
  const hasDepartment = String(values.departmentId ?? '').trim() !== '';
  const hasDoctor = String(values.doctorId ?? '').trim() !== '';
  const canSelectServiceType = bookingLocked.value || hasDepartment || hasDoctor;
  // 写 ref 驱动 computed schema，禁止再写死 disabled:true 与 updateSchema 互踩
  serviceTypeSelectEnabled.value = canSelectServiceType && !bookingLocked.value;
  if (canSelectServiceType) {
    serviceTypeSelectPlaceholder.value = '请选择号源类型';
  } else {
    serviceTypeSelectPlaceholder.value = '请先选择科室或医生';
  }
}

/**
 * 更新挂号区三要素字段的禁用态（预约带入后锁定）。
 * 号源类型禁用态走 serviceTypeSelectEnabled，与 computed schema 同源。
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
  ]);
  // 预约锁定时号源不可改；解锁后由 syncServiceTypeFieldState 按科/医重算
  if (disabled) {
    serviceTypeSelectEnabled.value = false;
  } else {
    void syncServiceTypeFieldState();
  }
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
  lastSlotSelection.value = { ...payload };
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
  // 点选号源带回的类型可能不在「医师可接诊」收窄列表里；并入下拉，禁止只显示雪花 ID
  ensureServiceTypeOptionPresent(healthcareServiceId, payload.healthcareServiceName || '号源类型');
  registerFormApi.setFieldValue('departmentId', organizationId || undefined);
  registerFormApi.setFieldValue('doctorId', practitionerId);
  registerFormApi.setFieldValue('serviceTypeId', healthcareServiceId || undefined);
  updateRegisterFeeDisplay(healthcareServiceId || undefined);
  syncBookingFieldLockState();
}

/**
 * 保证号源类型下拉含指定项（与 isSameBookingId 对齐，避免 value 类型不一致漏匹配）。
 */
function ensureServiceTypeOptionPresent(serviceTypeId: string, label: string) {
  if (!serviceTypeId) {
    return;
  }
  const exists = serviceTypeOptions.value.some((item) =>
    isSameBookingId(item.value, serviceTypeId),
  );
  if (exists) {
    return;
  }
  serviceTypeOptions.value = [
    ...serviceTypeOptions.value,
    { value: serviceTypeId, label: label || '号源类型' },
  ];
  void syncServiceTypeFieldState();
}

/**
 * 解除预约锁定。
 */
function clearBookingLock() {
  bookingLocked.value = false;
  syncBookingFieldLockState();
}

/**
 * 按号源类型刷新展示用挂号费（不触发右侧筛选同步）。
 * 用 String + isSameBookingId 匹配 map 键，避免 ElSelectV2 传 number 时 Snowflake 精度丢失误显 0。
 *
 * @param value 号源类型主键（OptionDto.value；允许 number 运行时）
 */
function updateRegisterFeeDisplay(value: null | number | string | undefined) {
  const key = value === null || value === undefined ? '' : String(value).trim();
  if (!key) {
    registerFeeAmount.value = 0;
    return;
  }
  let raw: number | string | undefined;
  // 先精确键；再按 Snowflake 主键宽松匹配（与下拉 isSameBookingId 同口径）
  if (Object.prototype.hasOwnProperty.call(serviceTypeFeeById.value, key)) {
    raw = serviceTypeFeeById.value[key];
  } else {
    for (const [mapKey, fee] of Object.entries(serviceTypeFeeById.value)) {
      if (isSameBookingId(mapKey, key)) {
        raw = fee;
        break;
      }
    }
  }
  const fee = Number(raw);
  // 查不到或非数字时归零，确认弹窗仍以预结算 totalAmount 为准
  if (Number.isFinite(fee)) {
    registerFeeAmount.value = fee;
  } else {
    registerFeeAmount.value = 0;
  }
}

/**
 * 号源类型变更：按初始化下发的销售价映射刷新展示用挂号费。
 */
function handleServiceTypeChange(value: null | number | string | undefined) {
  const normalized = value === null || value === undefined ? '' : String(value).trim();
  emit('serviceTypeChange', normalized);
  updateRegisterFeeDisplay(normalized);
}

/** 选中或读卡写入时的身份快照；提交时一致才沿用患者主键 */
interface BoundPatientIdentity {
  birthday: string;
  gender: string;
  idCard: string;
  name: string;
}

const PATIENT_IDENTITY_FIELDS = [
  'patientName',
  'patientIdCard',
  'patientGender',
  'patientBirthday',
] as const;

const boundPatientIdentity = ref<BoundPatientIdentity | null>(null);

/** 出生日期统一成 YYYY-MM-DD，避免接口带时间后被当成改过生日。 */
function normalizeIdentityDate(value: unknown): string {
  const text = String(value ?? '').trim();
  if (text.length >= 10 && text[4] === '-' && text[7] === '-') {
    return text.slice(0, 10);
  }
  return text;
}

/** 从表单或回填记录取出可比对的身份字段。 */
function toBoundPatientIdentity(source: {
  birthday?: unknown;
  gender?: unknown;
  idCard?: unknown;
  name?: unknown;
}): BoundPatientIdentity {
  return {
    birthday: normalizeIdentityDate(source.birthday),
    gender: String(source.gender ?? '').trim(),
    idCard: String(source.idCard ?? '')
      .trim()
      .toUpperCase(),
    name: String(source.name ?? '').trim(),
  };
}

/**
 * 记住回填身份。
 * 选患者/读卡重绑；补姓名或地址时证件号已变则保留原快照，避免把改过的人又绑回失败那次。
 */
function rememberBoundPatientIdentity(patient: RegisterPatientRecord, rebind: boolean) {
  const next = toBoundPatientIdentity({
    birthday: patient.patientBirthday,
    gender: patient.patientGender,
    idCard: patient.patientIdCard,
    name: patient.patientName,
  });
  const bound = boundPatientIdentity.value;
  const idCardDiverged = Boolean(
    bound && bound.idCard !== '' && next.idCard !== '' && bound.idCard !== next.idCard,
  );
  if (!rebind && idCardDiverged) {
    return;
  }
  boundPatientIdentity.value = next;
}

/** 清空、提交成功或身份已变时丢掉绑定快照。 */
function clearBoundPatientIdentity() {
  boundPatientIdentity.value = null;
}

/**
 * 身份字段与快照不一致则解除绑定。
 * 快照生日为空、提交时才按年龄补出生日，不算挂号员改了人。
 */
function releaseBoundPatientIfIdentityChanged(
  values: Record<string, unknown>,
): { idCardChanged: boolean } | null {
  const bound = boundPatientIdentity.value;
  if (!bound) {
    return null;
  }
  const current = toBoundPatientIdentity({
    birthday: values.patientBirthday,
    gender: values.patientGender,
    idCard: values.patientIdCard,
    name: values.patientName,
  });
  const idCardChanged = bound.idCard !== current.idCard;
  const nameChanged = bound.name !== current.name;
  const genderChanged = bound.gender !== current.gender;
  const birthdayChanged = bound.birthday !== '' && bound.birthday !== current.birthday;
  if (!idCardChanged && !nameChanged && !genderChanged && !birthdayChanged) {
    return null;
  }
  clearBoundPatientIdentity();
  return { idCardChanged };
}

/** 挂号员改了身份字段时通知父页丢掉患者主键。 */
function handlePatientValuesChange(values: Record<string, unknown>, fieldsChanged: string[]) {
  const identityTouched = fieldsChanged.some((field) =>
    (PATIENT_IDENTITY_FIELDS as readonly string[]).includes(field),
  );
  if (!identityTouched) {
    return;
  }
  const released = releaseBoundPatientIfIdentityChanged(values);
  if (released) {
    emit('patientIdentityDetach', released);
  }
}

const [PatientForm, patientFormApi] = useVbenForm(
  reactive({
    commonConfig: {
      componentProps: {
        style: { width: '100%' },
      },
    },
    compact: true,
    handleValuesChange: handlePatientValuesChange,
    layout: 'vertical',
    wrapperClass: patientFormWrapperClass,
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
        required: REGISTRATION_ID_CARD_REQUIRED,
        componentProps: {
          placeholder: REGISTRATION_ID_CARD_REQUIRED
            ? '请输入或扫描身份证号'
            : '可不填（无证须填年龄）',
          clearable: true,
          maxlength: 18,
          onInput: handleIdCardInput,
        },
        // 产品：填了则校 15/18 位格式；空串放行（国基等院点可无证）
        rules: z.preprocess(
          (v) => (v === null || v === undefined ? '' : String(v)).trim(),
          z.string().refine((val) => isAcceptableChineseIdCardInput(val), {
            message: '请输入正确的身份证号格式（15位或18位）',
          }),
        ),
      },
      {
        component: 'Input',
        fieldName: 'patientPhone',
        label: '联系电话',
        // 必填仅由院点开关控制；与格式无关——只要填了就必须是合法 11 位手机号
        required: REGISTRATION_PHONE_REQUIRED,
        componentProps: {
          placeholder: '请输入11位手机号',
          clearable: true,
          maxlength: 11,
        },
        rules: z.preprocess(
          (v) => (v === null || v === undefined ? '' : String(v)).trim(),
          z.string().refine((val) => val === '' || CN_MOBILE_PHONE_REGEX.test(val), {
            message: '请输入正确的11位手机号',
          }),
        ),
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
          onChange: handleBirthdayChange,
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
        component: 'Input',
        fieldName: 'medicalInsuranceNo',
        label: '年龄',
        // 产品必填：有证时由身份证回填；无证院点（身份证可选）须手填
        required: true,
        componentProps: {
          placeholder: '请输入年龄',
          clearable: true,
          onInput: handleAgeInput,
        },
      },
      {
        component: 'Input',
        fieldName: 'patientAddress',
        label: '地址',
        // BR-REG-019：可选；读卡户籍地址默认写入，挂号员可改；空不回写主档
        required: false,
        formItemClass: 'register-patient-item--address',
        componentProps: {
          placeholder: '请输入地址（可选）',
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
    compact: true,
    handleValuesChange: handleRegisterValuesChange,
    layout: 'vertical',
    wrapperClass: registerFormWrapperClass,
    schema: computed(() => [
      {
        component: 'Select',
        fieldName: 'contractId',
        label: '费用性质',
        required: true,
        componentProps: {
          placeholder: '请选择费用性质',
          clearable: true,
          filterable: true,
          options: contractOptions.value,
          onChange: handleContractChange,
        },
      },
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
        // BR-REG-001：医生统一可选；未选则挂到科室（BR-DOR-009 待诊科室共享）
        // 院点若需必选：override register-doctor.config → REGISTRATION_DOCTOR_REQUIRED=true
        required: REGISTRATION_DOCTOR_REQUIRED,
        componentProps: {
          placeholder: REGISTRATION_DOCTOR_REQUIRED ? '请选择医生' : '可不选（挂到科室）',
          clearable: !REGISTRATION_DOCTOR_REQUIRED,
          filterable: true,
          options: doctorOptions.value,
          onChange: handleDoctorChange,
        },
      },
      {
        component: 'Select',
        fieldName: 'serviceTypeId',
        label: '号源类型',
        required: true,
        componentProps: {
          placeholder: serviceTypeSelectPlaceholder.value,
          disabled: !serviceTypeSelectEnabled.value,
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

const benefitYbPreviewForDisplay = computed<YbRegisterBenefitPreviewDto>(
  () => benefitYbPreview.value ?? {},
);

/** 切换多险种时重查 2001 */
const benefitRefreshContext = ref<RegisterBenefitPreviewContext>({});

const benefitSectionTab = ref<'benefit' | 'history'>('benefit');

/** 右侧摘要：最近一次点选的号源（仅用于表单回填，不再展示摘要条） */
const lastSlotSelection = ref<null | RegisterSlotSelectionPayload>(null);

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
  if (info.kind === 'WORK_INJURY') {
    return Boolean(
      info.workInjury?.psnNo ||
        info.workInjury?.statusLabel ||
        info.workInjury?.errorMessage ||
        info.eligibilityMessage,
    );
  }
  return false;
});

/** 选工伤费用性质即显示备案入口（不依赖待遇 kind 是否已切到 WORK_INJURY）。 */
const showWorkInjuryFilingAction = computed(
  () =>
    isWorkInjuryUiEnabled() &&
    (props.workInjurySelected || benefitInfo.value.kind === 'WORK_INJURY'),
);

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
 * 不在此预填科室/医生/号源首条；费用性质由 applyDefaultSelfPayContract 单独回填自费。
 */
async function refreshDoctorsAndResetFeeDisplay() {
  await refreshDoctorOptions();
  registerFeeAmount.value = 0;
}

/** 从费用性质下拉取首个自费合同 id（按类别码，不用展示名）。 */
function findDefaultSelfPayContractId(): string | undefined {
  // 只在当前下拉可见项中找自费，避免选到已停用合同
  const matched = contractOptions.value.find((opt) =>
    isSelfPayContractCategory(contractCategoryById.value[String(opt.value ?? '')]),
  );
  const id = String(matched?.value ?? '').trim();
  return id || undefined;
}

/** 挂号窗口默认费用性质：自费（清空/提交成功/首次进入均回填）。 */
async function applyDefaultSelfPayContract() {
  const selfPayId = findDefaultSelfPayContractId();
  if (!selfPayId) {
    return;
  }
  await applyContractId(selfPayId);
}

/**
 * 校验患者信息表单与挂号信息表单是否均通过。
 * formApi.validate() 返回 { valid, errors }，不可对整段结果做 Boolean（对象恒为 true）。
 */
async function validateForSubmit(): Promise<boolean> {
  const { valid: patientValid } = await patientFormApi.validate();
  const { valid: registerValid } = await registerFormApi.validate();
  return Boolean(patientValid && registerValid);
}

/**
 * 读取两块表单当前值，并补上展示用挂号费（确认框仍以预结算 totalAmount 为准）。
 * 无证且已填年龄时，提交前再兜底推算出生日期，避免 onInput 异步未完成。
 */
async function getSubmitValues(): Promise<Record<string, unknown>> {
  const patientValues = await patientFormApi.getValues();
  const registerValues = await registerFormApi.getValues();
  const idCard = String(patientValues.patientIdCard ?? '').trim();
  const ageText = String(patientValues.medicalInsuranceNo ?? '').trim();
  let birthday = String(patientValues.patientBirthday ?? '').trim();
  if (!idCard && ageText !== '' && !Number.isNaN(Number(ageText))) {
    const age = Number(ageText);
    if (age >= 0 && age <= 150 && !birthday) {
      birthday = birthDateFromAgeYears(age);
      patientFormApi.setFieldValue('patientBirthday', birthday);
    }
  }
  return {
    ...patientValues,
    ...(birthday ? { patientBirthday: birthday } : {}),
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

async function applyContractId(contractId: string, options?: { emitChange?: boolean }) {
  registerFormApi.setFieldValue('contractId', contractId);
  if (options?.emitChange === false) {
    return;
  }
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
  return serviceTypeOptions.value.find((d) => isSameBookingId(d.value, serviceTypeId))?.label;
}

/**
 * 提交成功后的局部重置：清空患者与挂号表单，再刷新医生下拉并重置展示用挂号费；费用性质回填自费。
 */
async function resetForSubmitSuccess() {
  bookingLocked.value = false;
  lastSlotSelection.value = null;
  clearBoundPatientIdentity();
  patientFormApi.resetForm();
  registerFormApi.resetForm();
  registerFeeAmount.value = 0;
  benefitInfo.value = emptyBenefitInfo();
  benefitYbPreview.value = null;
  benefitRefreshContext.value = {};
  contractMatch.value = null;
  historyPatientKeyword.value = '';
  await refreshDoctorsAndResetFeeDisplay();
  await applyDefaultSelfPayContract();
  syncBookingFieldLockState();
}

/**
 * 整页清空：表单与待遇确认展示区清空；医生下拉恢复为不按科室过滤的全量列表；费用性质回填自费。
 */
function resetForClear() {
  bookingLocked.value = false;
  lastSlotSelection.value = null;
  clearBoundPatientIdentity();
  patientFormApi.resetForm();
  registerFormApi.resetForm();
  benefitInfo.value = emptyBenefitInfo();
  benefitYbPreview.value = null;
  benefitRefreshContext.value = {};
  contractMatch.value = null;
  historyPatientKeyword.value = '';
  void refreshDoctorsAndResetFeeDisplay();
  void applyDefaultSelfPayContract();
  syncBookingFieldLockState();
}

/**
 * 仅清空患者信息表单（患者下拉被清空时由父组件调用）。
 */
function clearPatientFields() {
  historyPatientKeyword.value = '';
  clearBoundPatientIdentity();
  patientFormApi.setValues({
    patientName: '',
    patientPhone: '',
    patientGender: undefined,
    patientBirthday: '',
    patientIdCard: '',
    medicalInsuranceNo: '',
    patientAddress: '',
  });
}

/**
 * 把父页选中的患者写入患者信息表单。
 * <p>{@code patientAddress} 为 undefined 时不覆盖表单已有地址（待遇回写姓名等场景）。</p>
 */
function applyPatientRecord(patient: RegisterPatientRecord, options?: { rebind?: boolean }) {
  historyPatientKeyword.value = (patient.patientIdCard ?? '').trim();
  rememberBoundPatientIdentity(patient, options?.rebind !== false);
  patientFormApi.setValues({
    patientName: patient.patientName,
    patientPhone: patient.patientPhone,
    patientGender: patient.patientGender,
    patientBirthday: patient.patientBirthday,
    patientIdCard: patient.patientIdCard,
    medicalInsuranceNo: patient.patientAge ?? '',
    ...(patient.patientAddress !== undefined ? { patientAddress: patient.patientAddress } : {}),
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
  findDefaultSelfPayContractId,
  getContractBusNo,
  focusFirstField,
  getContractCategory,
  getContractLabel,
  getDepartmentLabel,
  getDoctorLabel,
  getServiceTypeLabel,
  getSubmitValues,
  releaseBoundPatientIfIdentityChanged,
  resetForClear,
  resetForSubmitSuccess,
  validateForSubmit,
  openWorkInjuryApprovalGuide,
  ensureWorkInjuryFilingReady,
  getWorkInjuryBenefitSummary,
});

/**
 * 拉取科室/费用性质/医生/号源类型主数据并灌入下拉；绑定键盘导航；
 * 科室/医生/号源不预填，费用性质默认自费。
 */
onMounted(async () => {
  const init = await loadRegisterFormInit({});
  departmentOptions.value = [...init.departmentOptions];
  contractOptions.value = [...init.contractOptions];
  contractCategoryById.value = { ...init.contractCategoryById };
  contractInsuranceProfileById.value = { ...init.contractInsuranceProfileById };
  serviceTypeOptions.value = [...init.serviceTypeOptions];
  serviceTypeFeeById.value = { ...(init.serviceTypeFeeById ?? {}) };
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
  // 科室/医生/费用性质 options 已由 computed schema 绑定 ref；此处仅补院点必填态
  registerFormApi.updateSchema([
    {
      fieldName: 'doctorId',
      required: REGISTRATION_DOCTOR_REQUIRED,
      componentProps: {
        placeholder: REGISTRATION_DOCTOR_REQUIRED ? '请选择医生' : '可不选（挂到科室）',
        clearable: !REGISTRATION_DOCTOR_REQUIRED,
        filterable: true,
        options: doctorOptions.value,
        onChange: handleDoctorChange,
      },
    },
  ]);
  registerFeeAmount.value = 0;
  await applyDefaultSelfPayContract();
  await syncServiceTypeFieldState();
});
</script>

<template>
  <div
    ref="formAreaRef"
    class="reg-bill-form-area h-full min-h-0"
    @keydown.capture="handleFormKeydown"
  >
    <div v-if="layout === 'split'" class="register-workspace-layout h-full min-h-0">
      <div class="register-workspace-layout__main">
        <aside class="register-workspace-layout__panel register-workspace-layout__panel--left">
          <SectionTitle class="register-workspace-layout__section-title" title="患者信息" />
          <div v-if="$slots['patient-toolbar']" class="register-workspace-layout__patient-toolbar">
            <slot name="patient-toolbar" />
          </div>
          <PatientForm />

          <div class="register-workspace-layout__section-split" />

          <div
            v-if="showWorkInjuryFilingAction"
            class="register-workspace-layout__section-title flex items-center justify-between gap-2"
          >
            <SectionTitle title="待遇信息" />
            <WorkInjuryFilingDetailAction
              ref="workInjuryFilingActionRef"
              link
              :encounter-id="workInjuryPreEncounterId"
              :contract-id="workInjuryPreContractId"
              :injure-affirm-id-hint="benefitInfo.workInjury?.injureAffirmId"
            />
          </div>
          <SectionTitle v-else class="register-workspace-layout__section-title" title="待遇信息" />
          <div
            v-loading="benefitLoading"
            :element-loading-text="benefitLoadingText || '正在拉取人员与省市医保信息…'"
            class="register-workspace-layout__benefit"
            :class="{ 'register-workspace-layout__benefit--filled': hasBenefitDisplay }"
          >
            <template
              v-if="
                benefitInfo.kind === 'STUDENT' && (benefitInfo.studentNo || benefitInfo.studentName)
              "
            >
              <div class="register-benefit-section__field-grid">
                <div class="register-benefit-section__field">
                  <div class="register-benefit-section__field-label">姓名</div>
                  <div class="register-benefit-section__field-value">
                    {{ displayBenefitDashLocal(benefitInfo.studentName) }}
                  </div>
                </div>
                <div class="register-benefit-section__field">
                  <div class="register-benefit-section__field-label">学号</div>
                  <div class="register-benefit-section__field-value">
                    {{ displayBenefitDashLocal(benefitInfo.studentNo) }}
                  </div>
                </div>
                <div
                  class="register-benefit-section__field register-benefit-section__field--span-2"
                >
                  <div class="register-benefit-section__field-label">院系</div>
                  <div class="register-benefit-section__field-value">
                    {{ displayBenefitDashLocal(benefitInfo.college) }}
                  </div>
                </div>
                <div
                  class="register-benefit-section__field register-benefit-section__field--span-2"
                >
                  <div class="register-benefit-section__field-label">公费资格</div>
                  <div class="register-benefit-section__field-value">
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
            </template>
            <div
              v-else-if="
                isWorkInjuryUiEnabled() &&
                (benefitInfo.kind === 'WORK_INJURY' || workInjurySelected)
              "
              class="space-y-2"
            >
              <WorkInjuryBenefitSummary
                :error-message="benefitInfo.workInjury?.errorMessage"
                :has-valid-filing="benefitInfo.workInjury?.hasValidFiling"
                :injure-affirm-id="benefitInfo.workInjury?.injureAffirmId"
                :loading-hint="benefitLoading ? '正在查询工伤备案…' : undefined"
                :psn-name="benefitInfo.workInjury?.psnName || benefitInfo.psnName"
                :psn-no="benefitInfo.workInjury?.psnNo || benefitInfo.psnNo"
                :rows="benefitInfo.workInjury?.rows"
                :status-label="benefitInfo.workInjury?.statusLabel"
              />
            </div>
            <template v-else-if="hasBenefitDisplay">
              <ElAlert
                v-if="showContractMismatch && contractMatch?.hintMessage"
                type="warning"
                :closable="false"
                show-icon
                class="register-workspace-layout__benefit-alert"
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
                layout="vertical"
                :preview="benefitYbPreviewForDisplay"
                :card-no="benefitInfo.cardNo"
                :medium-type="benefitInfo.mediumType"
                :contract-id="benefitRefreshContext.contractId"
                :yb-card-info="benefitRefreshContext.ybCardInfo"
                :yb-card-sn="benefitRefreshContext.ybCardSn"
                :yb-certno="benefitRefreshContext.ybCertno"
                :yb-medium-type="benefitRefreshContext.ybMediumType"
                :scrollable-tables="false"
                :suppress-area-alert="showContractMismatch"
                :external-treatment-loading="treatmentLoading"
                default-show-all-treatment
              />
            </template>
            <div v-else class="register-workspace-layout__benefit-hint">
              读卡或选择医保类费用性质后将显示待遇信息
            </div>
          </div>
        </aside>

        <aside class="register-workspace-layout__panel register-workspace-layout__panel--right">
          <div class="register-workspace-layout__reg-head">
            <SectionTitle class="register-workspace-layout__section-title" title="挂号信息" />
            <div v-if="commonDepartments.length > 0" class="register-workspace-layout__dept-chips">
              <ElButton
                v-for="dept in commonDepartments"
                :key="dept.value"
                size="small"
                @click="quickSelectDepartment(dept.value)"
              >
                {{ dept.label }}
              </ElButton>
            </div>
          </div>
          <RegisterForm />

          <template v-if="$slots.slots">
            <div class="register-workspace-layout__section-split" />
            <div class="register-workspace-layout__slots-head">
              <SectionTitle class="register-workspace-layout__section-title" title="选择号源" />
              <div v-if="$slots['slots-toolbar']" class="register-workspace-layout__slots-meta">
                <slot name="slots-toolbar" />
              </div>
            </div>
            <div class="register-workspace-layout__slots">
              <slot name="slots" />
            </div>
          </template>

          <div v-if="$slots.actions" class="register-workspace-layout__slot-actions">
            <div class="register-workspace-layout__slot-fee">
              <span class="register-workspace-layout__slot-fee-label">挂号费用</span>
              <span class="register-workspace-layout__slot-fee-amount">
                ¥{{ registerFeeAmount.toFixed(2) }}
              </span>
            </div>
            <div class="register-workspace-layout__slot-actions-btns">
              <slot name="actions" />
            </div>
          </div>
        </aside>
      </div>
    </div>

    <div v-else class="space-y-2 overflow-y-auto px-2 py-1">
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
          <span class="shrink-0 text-base text-[var(--el-text-color-regular)]">挂号费用</span>
          <span class="text-xl font-semibold tabular-nums text-[var(--el-color-primary)]">
            ¥{{ formatMoneyDisplay(registerFeeAmount) }}
          </span>
        </div>
      </div>

      <ElTabs v-model="benefitSectionTab" class="benefit-section-tabs">
        <ElTabPane label="待遇信息" name="benefit">
          <div
            v-loading="benefitLoading"
            :element-loading-text="benefitLoadingText || '正在拉取人员与省市医保信息…'"
            class="benefit-tab-panel min-h-[140px] border-t bg-[var(--el-fill-color-lighter)] px-2 pb-2 pt-2"
          >
            <div class="benefit-tab-body space-y-2">
              <template v-if="benefitInfo.kind === 'STUDENT'">
                <div class="grid grid-cols-3 gap-x-2 gap-y-1 text-sm">
                  <div>
                    <div class="mb-0.5 text-[var(--el-text-color-secondary)]">姓名</div>
                    <div class="font-medium">
                      {{ displayBenefitDashLocal(benefitInfo.studentName) }}
                    </div>
                  </div>
                  <div>
                    <div class="mb-0.5 text-[var(--el-text-color-secondary)]">学号</div>
                    <div class="font-medium">
                      {{ displayBenefitDashLocal(benefitInfo.studentNo) }}
                    </div>
                  </div>
                  <div>
                    <div class="mb-0.5 text-[var(--el-text-color-secondary)]">院系</div>
                    <div class="font-medium">
                      {{ displayBenefitDashLocal(benefitInfo.college) }}
                    </div>
                  </div>
                  <div class="col-span-3">
                    <div class="mb-0.5 text-[var(--el-text-color-secondary)]">公费资格</div>
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
              </template>
              <div
                v-else-if="
                  isWorkInjuryUiEnabled() &&
                  (benefitInfo.kind === 'WORK_INJURY' || workInjurySelected)
                "
                class="space-y-2"
              >
                <div class="flex flex-wrap items-center justify-between gap-2">
                  <span class="text-sm text-[var(--el-text-color-secondary)]">工伤备案</span>
                  <WorkInjuryFilingDetailAction
                    ref="workInjuryFilingActionRef"
                    link
                    :encounter-id="workInjuryPreEncounterId"
                    :contract-id="workInjuryPreContractId"
                    :injure-affirm-id-hint="benefitInfo.workInjury?.injureAffirmId"
                  />
                </div>
                <WorkInjuryBenefitSummary
                  :error-message="benefitInfo.workInjury?.errorMessage"
                  :has-valid-filing="benefitInfo.workInjury?.hasValidFiling"
                  :injure-affirm-id="benefitInfo.workInjury?.injureAffirmId"
                  :loading-hint="benefitLoading ? '正在查询工伤备案…' : undefined"
                  :psn-name="benefitInfo.workInjury?.psnName || benefitInfo.psnName"
                  :psn-no="benefitInfo.workInjury?.psnNo || benefitInfo.psnNo"
                  :rows="benefitInfo.workInjury?.rows"
                  :status-label="benefitInfo.workInjury?.statusLabel"
                />
              </div>
              <template v-else>
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
                  :preview="benefitYbPreviewForDisplay"
                  :card-no="benefitInfo.cardNo"
                  :medium-type="benefitInfo.mediumType"
                  :contract-id="benefitRefreshContext.contractId"
                  :yb-card-info="benefitRefreshContext.ybCardInfo"
                  :yb-card-sn="benefitRefreshContext.ybCardSn"
                  :yb-certno="benefitRefreshContext.ybCertno"
                  :yb-medium-type="benefitRefreshContext.ybMediumType"
                  :scrollable-tables="false"
                  :suppress-area-alert="showContractMismatch"
                  :external-treatment-loading="treatmentLoading"
                  default-show-all-treatment
                />
              </template>
            </div>
          </div>
        </ElTabPane>
        <ElTabPane label="历史就诊" name="history">
          <div class="border-t bg-[var(--el-fill-color-lighter)] px-2 pb-2 pt-2">
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
.register-workspace-layout {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow: hidden;
}

.register-workspace-layout__patient-toolbar {
  flex-shrink: 0;
  width: 100%;
  min-width: 0;
}

/* 患者检索下拉与下方 Element 表单控件对齐 */
.register-workspace-layout__patient-toolbar :deep(.vxe-pulldown) {
  display: block !important;
  width: 100% !important;
  min-width: 0;
}

.register-workspace-layout__patient-toolbar :deep(.vxe-input) {
  width: 100% !important;
  min-width: 0 !important;
  height: var(--form-control-height);
  font-size: var(--el-font-size-base);
  line-height: calc(var(--form-control-height) - 2px);
  border-radius: var(--form-control-radius);
}

.register-workspace-layout__patient-toolbar :deep(.vxe-input--inner) {
  height: calc(var(--form-control-height) - 2px);
  line-height: calc(var(--form-control-height) - 2px);
}

.register-workspace-layout__main {
  display: flex;
  flex: 1 1 auto;
  gap: 8px;
  min-height: 0;
  overflow: hidden;
}

.register-workspace-layout__panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  min-height: 0;
  padding: 8px 10px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.register-workspace-layout__panel--left {
  flex: 0 0 46%;
}

.register-workspace-layout__panel--right {
  flex: 1 1 auto;
}

.register-workspace-layout__section-title {
  flex-shrink: 0;
  margin-bottom: 0;
  font-size: 14px;
}

.register-workspace-layout__section-split {
  flex-shrink: 0;
  margin: 2px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.register-workspace-layout__reg-head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.register-workspace-layout__dept-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: flex-end;
}

.register-workspace-layout__benefit {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

.register-workspace-layout__benefit--filled {
  flex: 1 1 auto;
  min-height: 0;
}

.register-workspace-layout__slots-head {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.register-workspace-layout__slots-meta {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  margin-left: auto;
}

.register-workspace-layout__slots {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  min-height: 0;
  overflow: visible;
}

.register-workspace-layout__benefit-hint {
  padding: 4px 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.register-workspace-layout__benefit-hint--warning {
  color: var(--el-color-warning);
}

.register-workspace-layout__benefit-alert {
  padding: 6px 10px;
  margin-bottom: 8px;
}

.register-benefit-section__field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 8px;
  align-items: center;
}

.register-benefit-section__field {
  display: flex;
  flex-direction: row;
  gap: 4px;
  align-items: center;
  min-width: 0;
  min-height: 22px;
}

.register-benefit-section__field--span-2 {
  grid-column: span 2;
}

.register-benefit-section__field-label {
  flex-shrink: 0;
  width: 64px;
  min-width: 64px;
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  text-align: right;
  white-space: nowrap;
}

.register-benefit-section__field-label::after {
  content: '：';
}

.register-benefit-section__field-value {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.register-workspace-layout__benefit :deep(.register-benefit-preview-panel--vertical) {
  gap: 8px;
}

.register-workspace-layout__benefit :deep(.register-benefit-preview-panel__field-grid) {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 8px;
}

.register-workspace-layout__benefit :deep(.register-benefit-preview-panel__field--span-3),
.register-workspace-layout__benefit :deep(.register-benefit-preview-panel__field--span-4) {
  grid-column: span 2;
}

.register-workspace-layout__benefit :deep(.register-benefit-preview-panel__field--span-2) {
  grid-column: span 2;
}

.register-workspace-layout__benefit :deep(.register-benefit-preview-panel__field-label) {
  width: 56px;
  min-width: 56px;
  font-size: 12px;
}

.register-workspace-layout__benefit :deep(.register-benefit-preview-panel__field-value) {
  font-size: 12px;
}

.register-workspace-layout__slot-actions {
  display: flex;
  flex-shrink: 0;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
  padding-top: 8px;
  margin-top: auto;
  border-top: 1px solid var(--el-border-color-lighter);
}

.register-workspace-layout__panel--right :deep(.register-reg-grid) {
  display: grid !important;
  grid-template-columns: repeat(4, minmax(0, 1fr)) !important;
  gap: 8px 12px;
  align-items: end;
  width: 100%;
}

.register-workspace-layout__panel--right :deep(.register-reg-grid > *) {
  min-width: 0;
}

.register-workspace-layout__panel--right :deep(.register-reg-grid .flex-col),
.register-workspace-layout__panel--right :deep(.flex-col) {
  align-items: flex-start;
  width: 100%;
}

.register-workspace-layout__panel--right :deep(.register-reg-grid .flex-auto),
.register-workspace-layout__panel--right :deep(.flex-auto) {
  flex: none;
  width: 100%;
  max-width: none;
}

.register-workspace-layout__panel--right :deep(.el-input),
.register-workspace-layout__panel--right :deep(.el-select),
.register-workspace-layout__panel--right :deep(.el-select .el-select__wrapper),
.register-workspace-layout__panel--right :deep(.el-date-editor) {
  width: 100% !important;
}

.register-workspace-layout__slots :deep(.register-daily-slot-panel) {
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  height: auto;
  min-height: 0;
}

.register-workspace-layout__slots :deep(.register-daily-slot-panel__body--horizontal) {
  flex: 0 0 auto;
  overflow: visible;
}

.register-workspace-layout__slots :deep(.register-daily-slot-panel__pick-layout) {
  gap: 6px;
}

.register-workspace-layout__slots :deep(.register-daily-slot-panel__slot-area) {
  flex: 0 0 auto;
  overflow: visible;
}

.register-workspace-layout__slots
  :deep(.register-daily-slot-panel__slot-area .register-daily-slot-panel__grid--horizontal) {
  display: flex !important;
  flex: 0 0 auto;
  flex-wrap: nowrap !important;
  gap: 6px;
  align-items: flex-start !important;
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
}

.register-workspace-layout__slots :deep(.register-daily-slot-panel__grid--horizontal) {
  display: flex !important;
  flex-wrap: nowrap !important;
  gap: 6px;
  align-items: flex-start !important;
  width: 100%;
}

.register-workspace-layout__slots
  :deep(.register-daily-slot-panel--horizontal .register-daily-slot-panel__card--slot) {
  flex: 1 1 0 !important;
  width: auto !important;
  min-width: 120px;
  max-width: none;
  height: auto !important;
  align-self: flex-start !important;
}

.register-workspace-layout__slots :deep(.register-slot-placeholder) {
  height: auto;
  min-height: 72px;
}

.register-workspace-layout__slot-fee {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: baseline;
}

.register-workspace-layout__slot-fee-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.register-workspace-layout__slot-fee-amount {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--el-color-primary);
  white-space: nowrap;
}

.register-workspace-layout__slot-actions-btns {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
}

.register-workspace-layout__slot-actions-btns :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
  white-space: nowrap;
}

.register-workspace-layout__slot-actions-btns :deep(.el-checkbox__label) {
  padding-left: 6px;
  font-size: 12px;
}

.register-workspace-layout__panel--left :deep(.register-patient-grid) {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px 16px;
}

.register-workspace-layout__panel--left :deep(.register-patient-item--address) {
  grid-column: 1 / -1;
}

.register-workspace-layout__panel--left :deep(.register-patient-grid .flex-col) {
  align-items: flex-start;
  width: 100%;
}

.register-workspace-layout__panel--left :deep(.register-patient-grid .flex-auto) {
  flex: none;
  width: 100%;
  max-width: none;
}

.register-workspace-layout__panel--left :deep(.el-input),
.register-workspace-layout__panel--left :deep(.el-select),
.register-workspace-layout__panel--left :deep(.el-select .el-select__wrapper),
.register-workspace-layout__panel--left :deep(.el-date-editor) {
  width: 100% !important;
}

.register-workspace-layout__panel--left :deep(.register-patient-grid .flex-col > .flex.leading-6),
.register-workspace-layout__panel--right :deep(.flex-col > .flex.leading-6) {
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
  color: var(--el-text-color-regular);
}

.register-workspace-layout__panel--left :deep(.register-patient-grid .pb-2),
.register-workspace-layout__panel--right :deep(.pb-2) {
  padding-bottom: 0;
}

.register-workspace-layout__panel--left :deep(.el-radio) {
  height: var(--form-control-height);
  margin-right: 12px;
}

.register-reg-section {
  display: flex;
  flex-direction: column;
}

.register-reg-section :deep(.register-reg-grid) {
  display: grid !important;
  grid-template-columns: repeat(4, 200px) !important;
  gap: 8px 16px;
  align-items: end;
}

.register-reg-section :deep(.register-reg-grid.flex-col) {
  flex-direction: unset !important;
}

@media (max-width: 1200px) {
  .register-workspace-layout__main {
    flex-direction: column;
  }

  .register-workspace-layout__panel--left {
    flex: 0 0 auto;
    max-height: 45%;
  }

  .register-reg-section :deep(.register-reg-grid) {
    grid-template-columns: repeat(3, 200px);
  }
}

@media (max-width: 992px) {
  .register-workspace-layout__panel--left :deep(.register-patient-grid) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .register-reg-section :deep(.register-reg-grid) {
    grid-template-columns: repeat(2, 200px);
  }
}

.reg-bill-form-area :deep(.el-form-item__content) {
  line-height: 28px;
}

.reg-bill-form-area :deep(.el-form-item) {
  margin-bottom: 6px;
}

.reg-bill-form-area :deep(.el-input__wrapper) {
  padding-top: 0;
  padding-bottom: 0;
}

.reg-bill-form-area :deep(.el-select .el-select__wrapper) {
  padding-top: 0;
  padding-bottom: 0;
}

.reg-bill-form-area :deep(.el-radio) {
  height: var(--form-control-height);
  margin-right: 14px;
}

.benefit-section-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.benefit-section-tabs :deep(.el-tabs__item) {
  height: 32px;
  font-size: 13px;
  line-height: 32px;
}

.reg-bill-form-area :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

/* 窄屏（<1680，含 1600）：压缩间距与字号，给历史列表腾空间 */
@media (max-width: 1679px) {
  .register-workspace-layout {
    gap: 4px;
  }

  .register-workspace-layout__main {
    flex: 0 0 auto;
    gap: 6px;
  }

  .register-workspace-layout__panel {
    gap: 4px;
    height: auto;
    padding: 4px 8px;
  }

  .register-workspace-layout__section-title {
    font-size: 13px;
    line-height: 18px;
  }

  .register-workspace-layout__section-title :deep(.section-title) {
    font-size: 13px;
    line-height: 18px;
  }

  .register-workspace-layout__section-split {
    margin: 1px 0;
  }

  .register-workspace-layout__benefit {
    flex: 0 0 auto;
  }

  .register-workspace-layout__benefit--filled {
    flex: 1 1 auto;
    min-height: 0;
    max-height: 220px;
  }

  .register-workspace-layout__benefit-hint {
    padding: 2px 0;
    line-height: 1.4;
  }

  .register-workspace-layout__slot-actions {
    padding-top: 4px;
    margin-top: 0;
  }

  .register-workspace-layout__panel--right :deep(.register-reg-grid) {
    gap: 4px 10px;
  }

  .register-workspace-layout__panel--left :deep(.register-patient-grid) {
    gap: 4px 12px;
  }

  .register-workspace-layout__panel--left :deep(.register-patient-grid .flex-col > .flex.leading-6),
  .register-workspace-layout__panel--right :deep(.flex-col > .flex.leading-6) {
    margin-bottom: 2px;
    font-size: 12px;
    line-height: 18px;
  }

  .reg-bill-form-area :deep(.el-form-item) {
    margin-bottom: 4px;
  }
}
</style>
