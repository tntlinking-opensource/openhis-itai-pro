<script setup lang="ts">
import type { TableColumn } from '@zxplat-core/table-layout-ui';

import type {
  AdmissionDepartmentBedOptionDto,
  AdmissionDiagnosisSubmitDto,
  LoadRegistrationContextResult,
  OptionDto,
  RegistrationListStatus,
  RegistrationQueueItemDto,
  WardOptionDto,
} from '#/api/inpatient-registration';
import type { PatientItemDto, RegisterContractMatchDto } from '#/api/outpatient-registration';
import type { YbRegisterBenefitPreviewDto } from '#/api/outpatient-registration';

import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';

import { Page } from '@zxplat/common-ui';
import { useUserStore } from '@zxplat/stores';

import dayjs from 'dayjs';
import {
  ElButton,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';
import { z } from 'zod';

import { useVbenForm } from '#/adapter/form';
import {
  birthDateFromAgeYears,
  calcAgeFromBirthDate,
  isAcceptableChineseIdCardInput,
} from '#/utils/format/id-card-cn';
import { getPatientApi } from '#/api/basic/patient';
import { httpGetConditionDefinitionMetadata } from '#/api/doctor-station/diagnosis.http';
import {
  httpCancelInpatientRegistration,
  httpCancelInsuranceInpatientAdmission,
  httpChangeAdmissionDepartment,
  httpListAdmissionDepartmentBeds,
  httpListAdmissionDepartmentWards,
  httpListWardsByDepartment,
  httpLoadInpatientRegistrationContext,
  httpLoadInpatientRegistrationPageInit,
  httpLoadWardBedCapacity,
  httpPageInpatientRegistrationQueue,
  httpSubmitInpatientRegistration,
  httpSwitchInpatientToInsurance,
  httpSwitchInpatientToSelfPay,
} from '#/api/inpatient-registration';
import { httpLoadAdmissionParticipantInit } from '#/api/inpatient/nurse-station/inpatient-nurse-station.http';
import { httpPagePatientsForSelect } from '#/api/outpatient-registration';
import AppDialog from '#/components/app-dialog.vue';
import SelectArea from '#/components/select-area.vue';
import {
  CONER_RLTS_OPTIONS,
  MRG_STAS_OPTIONS,
  NATY_OPTIONS,
  NTLY_OPTIONS,
  PRFS_OPTIONS,
} from '#/views/mrms/mrms.constants';
import AdmissionDiagnosisField from '#/components/admission/admission-diagnosis-field.vue';
import type { AdmissionDiagnosisFormState } from '#/components/admission/admission-diagnosis.types';
import { createEmptyAdmissionDiagnosisFormState } from '#/components/admission/admission-diagnosis.types';
import RegisterStyleTableLayout from '#/components/layouts/register-style-table-layout.vue';
import {
  buildAdmissionDiagnosesFromFormState,
  validateAdmissionDiagnosisFormState,
} from '#/components/admission/services/build-admission-diagnoses';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import {
  printAdmissionCertificate,
  resolveAdmissionCertificateDiagnosisText,
  resolveAdmissionCertificateGenderText,
} from '#/utils/print';
import { normalizeAdmCondTermCode } from '#/utils/terminology/resolve-adm-cond-code-label';
import {
  optionDtosToPayChannelOptions,
  type PayChannelOption,
} from '#/utils/payment/payment-channel-options';
import { formatInsutype } from '#/chs/constants/reconciliation';
import RegisterBenefitPreviewPanel from '#/views/outpatient/charge/register/components/register-benefit-preview-panel.vue';
import {
  enrichInsuranceBenefitPreview,
  isInsuranceContractCategory,
  CHARGE_TYPE_CATEGORY,
} from '#/views/outpatient/charge/register/services/register-benefit.service';
import {
  isSelfPayContractCategory,
  requiresInsuranceBenefitFlow,
} from '#/views/outpatient/charge/register/services/charge-type-category';
import { useRegisterBenefit } from '#/views/outpatient/charge/register/services/use-register-benefit';
import { buildFormRecordFromSnapshot } from '#/views/outpatient/charge/register/services/register-patient-identity.service';

import {
  useInpatientRegisterPatientIdentity,
  PATIENT_IDENTITY_SOURCE_LABEL,
} from './composables/use-inpatient-register-patient-identity';
import RegisterListToolbar from './components/register-list-toolbar.vue';
import {
  findInpatientBootstrapInsuranceContractId,
  findInpatientBootstrapInsuranceContractIdByInsuplc,
  getInpatientContractBusNo,
  getInpatientContractCategory,
  loadInpatientRegisterContractMeta,
  type InpatientRegisterContractMeta,
} from './services/inpatient-register-contract-meta';
import {
  DEFAULT_ADM_COND_CODE_GENERAL,
  DEFAULT_ADMIT_SOURCE_CODE_ROUTINE,
  DEFAULT_PAY_CHANNEL_CODE,
  ENCOUNTER_STATUS,
  GENDER_OPTIONS,
  INPATIENT_MED_TYPE,
  PAY_CHANNEL_OPTIONS,
} from './inpatient-register.constants';

defineOptions({
  name: 'ChargeIpRegister',
});

type RegisterDialogMode = 'register' | 'switchInsurance' | 'view';

const userStore = useUserStore();

const registerDialogVisible = ref(false);
/** 登记弹窗模式：登记 / 只读查看 / 自费转医保 */
const dialogMode = ref<RegisterDialogMode>('register');
/** 当前上下文是否允许转医保（查看弹窗底部按钮） */
const switchToInsuranceEnabled = ref(false);
/** 当前上下文是否允许医保转自费（查看弹窗底部按钮） */
const switchToSelfPayEnabled = ref(false);
/** 转自费提交中，避免重复点平台撤销 */
const selfPaySwitching = ref(false);
/** 登记弹窗 Tab：默认住院信息，避免待遇面板撑高弹窗 */
const registerDetailTab = ref<'benefit' | 'inpatient'>('inpatient');
const dialogReadOnly = ref(false);
const dialogSubmitting = ref(false);
const printingCertificate = ref(false);
const tableData = ref<RegistrationQueueItemDto[]>([]);
const loading = ref(false);
const total = ref(0);
const tableAreaRef = ref<HTMLElement | null>(null);
const tableAreaHeight = ref<number>();

let tableAreaResizeObserver: null | ResizeObserver = null;
const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  idCard: '',
  organizationId: '',
  wardId: '',
  admissionDateFrom: '',
  admissionDateTo: '',
  registrationListStatuses: [] as RegistrationListStatus[],
});

const organizationOptions = ref<OptionDto[]>([]);
const contractOptions = ref<OptionDto[]>([]);
const inWayCodeOptions = ref<OptionDto[]>([]);
const admitSourceCodeOptions = ref<OptionDto[]>([]);
const admCondCodeOptions = ref<OptionDto[]>([]);
const westernAdmissionDiagnosisOptions = ref<OptionDto[]>([]);
const admissionDiagnosisReadonly = ref(false);
/** 入院病区下拉（与入院科室同为 label/value 结构） */
const wardOptions = ref<OptionDto[]>([]);
/** 列表筛选病区下拉（随筛选科室联动，与登记弹窗病区 options 隔离） */
const filterWardOptions = ref<OptionDto[]>([]);
/** 主治医生下拉（科室下角色为医生的参与者） */
const attendingDoctorOptions = ref<OptionDto[]>([]);
const wardCapacityHint = ref('');
const contractMeta = ref<InpatientRegisterContractMeta>({
  contractCategoryById: {},
  contractInsuranceProfileById: {},
});
const benefitYbPreview = ref<YbRegisterBenefitPreviewDto | null>(null);
const {
  applyInsuranceSnapshot,
  benefitSyncing,
  treatmentSyncing,
  fillInsurancePatientNameIfEmpty,
  lastContractMatch,
  lastYbCardInfo,
  lastYbCardSn,
  lastYbCertno,
  lastYbIssuingAreaCode,
  lastYbMediumType,
  lastYbPreview,
  lastYbPsnName,
  resetBenefitPreviewCache,
  resetBenefitState,
  syncInsuranceBenefit,
} = useRegisterBenefit();
const genderSelectOptions = GENDER_OPTIONS.filter((item) => item.value !== 'UNKNOWN').map(
  (item) => ({
    label: item.label,
    value: item.value,
  }),
);

/** 患者下拉表格列（与门诊挂号一致） */
const patientSelectColumns = [
  { prop: 'patientBusNo', label: '病历号', width: 120 },
  { prop: 'name', label: '姓名', width: 100 },
  { prop: 'genderText', label: '性别', width: 72 },
  { prop: 'birthDate', label: '出生日期', width: 112 },
  { prop: 'age', label: '年龄', width: 64 },
  { prop: 'phoneMasked', label: '联系电话', width: 140 },
  { prop: 'idCardNoMasked', label: '身份证号', width: 190 },
];

const selectedPatientId = ref('');
const selectedPatientLabel = ref('');
const patientSelectError = ref('');
const patientRowById = ref<Record<string, PatientItemDto>>({});
const patientSelectRef = ref<null | { getKeywordDraft: () => string }>(null);

const dialogContext = reactive({
  encounterId: '' as string | undefined,
  patientId: '' as string | undefined,
});

/** 查看弹窗对应就诊状态，决定能否更正入院科室、是否必须选床 */
const viewedEncounterStatus = ref('');
/** 查看弹窗当前床，科室变更时用于「不换床」回显 */
const viewedBedId = ref('');
const departmentChangeVisible = ref(false);
const departmentChangeSubmitting = ref(false);
const departmentChangeWards = ref<WardOptionDto[]>([]);
const departmentChangeBeds = ref<AdmissionDepartmentBedOptionDto[]>([]);
const departmentChangeForm = reactive({
  organizationId: '',
  wardId: '',
  bedId: '',
});

/** 待入科、已入科的查看弹窗可以更正选错的入院科室 */
const departmentChangeEnabled = computed(
  () =>
    dialogReadOnly.value &&
    (viewedEncounterStatus.value === ENCOUNTER_STATUS.REGISTERED ||
      viewedEncounterStatus.value === ENCOUNTER_STATUS.IN_PROGRESS),
);

/** 已入科必须改到目标病区的床；待入科只改科室和病区 */
const departmentChangeNeedsBed = computed(
  () => viewedEncounterStatus.value === ENCOUNTER_STATUS.IN_PROGRESS,
);

const registerForm = reactive({
  patientName: '',
  genderEnum: 'MALE',
  phone: '',
  age: undefined as number | undefined,
  idCard: '',
  birthDate: '',
  /** 国籍（country_code，病案 ntly） */
  countryCode: '156',
  /** 民族（nationality_code，病案 naty） */
  nationalityCode: '01',
  /** 籍贯省市区 code（SelectArea，maxLevel=2） */
  naplCodes: [] as string[],
  /** 婚姻状况编码（患者主档） */
  maritalStatusEnum: '',
  /** 职业编码（患者主档） */
  prfsEnum: '',
  /** 现住址省市区 code（SelectArea） */
  regionCodes: [] as string[],
  /** 现住址详细地址 */
  address: '',
  /** 工作单位 */
  workCompany: '',
  /** 联系人姓名 */
  linkName: '',
  /** 联系人关系编码 */
  linkRelationCode: '' as string,
  /** 联系人电话 */
  linkTelcom: '',
  encounterBusNo: '',
  admissionCount: '',
  organizationId: '',
  wardId: '',
  assignedBedDisplay: '',
  outpatientDoctor: '',
  attendingDoctorId: '',
  admissionDiagnosis: createEmptyAdmissionDiagnosisFormState(),
  contractId: '',
  /** 医保险种类别码（310 职工 / 390 居民等；只读展示，读卡或上下文回填） */
  insutype: '',
  startTime: '',
  admitSourceCode: DEFAULT_ADMIT_SOURCE_CODE_ROUTINE,
  inWayCode: '',
  priorityEnum: DEFAULT_ADM_COND_CODE_GENERAL,
  /**
   * 预交金支付分配行（款员拆分渠道）。
   * 医生意向总额只用于打开弹窗时预填，界面不单独展示「总额」。
   */
  paymentDetailRows: [
    { payChannelCode: DEFAULT_PAY_CHANNEL_CODE, amount: 0 },
  ] as Array<{ amount: number; payChannelCode: string }>,
});

/** 病案口径下拉：国籍/民族/婚姻/职业/联系人关系 */
const ntlySelectOptions = NTLY_OPTIONS.map((opt) => ({
  label: opt.label,
  value: String(opt.value),
}));
const natySelectOptions = NATY_OPTIONS.map((opt) => ({
  label: opt.label,
  value: String(opt.value),
}));
const mrgStasSelectOptions = MRG_STAS_OPTIONS.map((opt) => ({
  label: opt.label,
  value: String(opt.value),
}));
const prfsSelectOptions = PRFS_OPTIONS.map((opt) => ({
  label: opt.label,
  value: String(opt.value),
}));
const conerRltsSelectOptions = CONER_RLTS_OPTIONS.map((opt) => ({
  label: opt.label,
  value: String(opt.value),
}));

/** 省市区字段 → SelectArea 用 code 数组 */
function buildRegionCodes(
  province?: null | string,
  city?: null | string,
  district?: null | string,
): string[] {
  const codes: string[] = [];
  if (province) codes.push(String(province));
  if (city) codes.push(String(city));
  if (district) codes.push(String(district));
  return codes;
}

/** 籍贯斜杠串 → SelectArea codes */
function parseNativePlaceCodes(nativePlace?: null | string): string[] {
  if (!nativePlace) return [];
  const trimmed = String(nativePlace).trim();
  if (!trimmed) return [];
  if (/^\d{6}(\/\d{6}){0,2}$/.test(trimmed)) {
    return trimmed.split('/').filter(Boolean);
  }
  return [];
}

/** SelectArea codes → 籍贯斜杠串（写入患者 nativePlace） */
function joinNativePlaceCodes(codes: string[]): string {
  return codes.map((c) => String(c).trim()).filter(Boolean).join('/');
}

/** 清空基础信息中的病案对齐人口学字段 */
function clearPatientDemographicsExtra() {
  registerForm.countryCode = '156';
  registerForm.nationalityCode = '01';
  registerForm.naplCodes = [];
  registerForm.maritalStatusEnum = '';
  registerForm.prfsEnum = '';
  registerForm.regionCodes = [];
  registerForm.address = '';
  registerForm.workCompany = '';
  registerForm.linkName = '';
  registerForm.linkRelationCode = '';
  registerForm.linkTelcom = '';
}

/**
 * 将患者主档人口学字段写入登记表单（与病案首页病人基本信息对齐）。
 *
 * @param source 含人口学扩展字段的对象
 */
function applyPatientDemographicsExtra(source: {
  address?: null | string;
  addressCity?: null | string;
  addressDistrict?: null | string;
  addressProvince?: null | string;
  countryCode?: null | string;
  linkName?: null | string;
  linkRelationCode?: null | number | string;
  linkTelcom?: null | string;
  maritalStatusEnum?: null | number | string;
  nationalityCode?: null | string;
  nativePlace?: null | string;
  prfsEnum?: null | number | string;
  workCompany?: null | string;
}) {
  registerForm.countryCode = source.countryCode ? String(source.countryCode) : '156';
  registerForm.nationalityCode = source.nationalityCode ? String(source.nationalityCode) : '01';
  registerForm.naplCodes = parseNativePlaceCodes(source.nativePlace);
  registerForm.maritalStatusEnum =
    source.maritalStatusEnum === null || source.maritalStatusEnum === undefined
      ? ''
      : String(source.maritalStatusEnum);
  registerForm.prfsEnum =
    source.prfsEnum === null || source.prfsEnum === undefined ? '' : String(source.prfsEnum);
  registerForm.regionCodes = buildRegionCodes(
    source.addressProvince,
    source.addressCity,
    source.addressDistrict,
  );
  registerForm.address = source.address ? String(source.address) : '';
  registerForm.workCompany = source.workCompany ? String(source.workCompany) : '';
  registerForm.linkName = source.linkName ? String(source.linkName) : '';
  // 医保 patn_rlts：01/02 等；主档 Integer 存 1/2，回填时补零以匹配下拉 value
  registerForm.linkRelationCode =
    source.linkRelationCode === null || source.linkRelationCode === undefined
      ? ''
      : String(source.linkRelationCode).padStart(2, '0');
  registerForm.linkTelcom = source.linkTelcom ? String(source.linkTelcom) : '';
}

const payChannelOptions = ref<PayChannelOption[]>([...PAY_CHANNEL_OPTIONS]);

/**
 * 按医生意向/改后金额预填一条支付行（默认现金）。
 *
 * @param amount 预填金额
 * @returns 单行明细
 */
function createSeedPaymentDetailRows(amount: number) {
  let rowAmount = 0;
  if (Number.isFinite(amount) && amount > 0) {
    rowAmount = amount;
  }
  return [
    {
      payChannelCode: DEFAULT_PAY_CHANNEL_CODE,
      amount: rowAmount,
    },
  ];
}

/** 新增一条支付渠道明细行。 */
function addPaymentDetailRow() {
  registerForm.paymentDetailRows.push({
    payChannelCode: DEFAULT_PAY_CHANNEL_CODE,
    amount: 0,
  });
}

/**
 * 删除一条支付渠道明细行（至少保留一行）。
 *
 * @param index 行下标
 */
function removePaymentDetailRow(index: number) {
  // 至少保留一行，避免提交时空明细
  if (registerForm.paymentDetailRows.length <= 1) {
    return;
  }
  registerForm.paymentDetailRows.splice(index, 1);
}

/**
 * 组装提交用支付明细；金额均为 0 时表示不收预交金。
 *
 * @returns 明细数组或 undefined
 */
function buildPaymentDetailsForSubmit() {
  const rows = registerForm.paymentDetailRows
    .filter((row) => row && Number(row.amount) > 0 && String(row.payChannelCode || '').trim())
    .map((row) => ({
      payChannelCode: String(row.payChannelCode).trim(),
      amount: Number(row.amount),
    }));
  if (rows.length === 0) {
    return undefined;
  }
  return rows;
}

/**
 * 由支付明细合计得到预交金总额（提交用；界面不单独展示）。
 *
 * @param details 支付明细
 * @returns 合计金额
 */
function sumPaymentDetailsAmount(
  details: Array<{ amount: number; payChannelCode: string }> | undefined,
): number {
  if (!details || details.length === 0) {
    return 0;
  }
  let sum = 0;
  for (const row of details) {
    sum += Number(row.amount);
  }
  return Number(sum.toFixed(2));
}

const isInsuranceContractSelected = computed(() => {
  const contractId = String(registerForm.contractId ?? '').trim();
  if (!contractId) {
    return false;
  }
  const category = getInpatientContractCategory(contractId, contractMeta.value);
  return requiresInsuranceBenefitFlow(category);
});

function clearInsuranceCardState() {
  benefitYbPreview.value = null;
  resetBenefitState();
}

/** 费用性质展示名 */
function resolveContractLabel(contractId: string): string {
  const id = String(contractId ?? '').trim();
  if (!id) return '';
  return contractOptions.value.find((item) => String(item.value) === id)?.label?.trim() || '';
}

/** 医保类别展示文案（职工基本医疗保险 / 城乡居民基本医疗保险 等） */
function resolveInsutypeDisplay(insutype?: string | null): string {
  const code = String(insutype ?? '').trim();
  if (!code) {
    return '';
  }
  const label = formatInsutype(code);
  if (!label || label === '—') {
    return '';
  }
  return label;
}

/**
 * 回写医保类别码并刷新只读展示。
 *
 * @param insutype 国家医保险种码（310/390 等）
 */
async function applyInsutypeToForm(insutype?: string | null) {
  registerForm.insutype = String(insutype ?? '').trim();
  await inpatientInfoFormApi.setFieldValue(
    'insutypeDisplay',
    resolveInsutypeDisplay(registerForm.insutype),
  );
}

/** 患者病情展示名 */
function resolvePriorityLabel(priorityCode: string): string {
  const code = String(priorityCode ?? '').trim();
  if (!code) return '';
  return admCondCodeOptions.value.find((item) => String(item.value) === code)?.label?.trim() || '';
}

/** 组装并打印住院证（取弹窗当前表单已有字段） */
async function handlePrintAdmissionCertificate() {
  if (!dialogReadOnly.value) {
    await syncModelFromDialogForms();
  }
  const patientName = registerForm.patientName.trim();
  if (!patientName) {
    ElMessage.warning('缺少患者姓名，无法打印住院证');
    return;
  }
  printingCertificate.value = true;
  try {
    const startTime = registerForm.startTime?.trim();
    const admissionTimeText = startTime
      ? dayjs(startTime).isValid()
        ? dayjs(startTime).format('YYYY-MM-DD HH:mm')
        : startTime
      : '';
    const ageText =
      registerForm.age === undefined || registerForm.age === null || Number.isNaN(registerForm.age)
        ? ''
        : String(registerForm.age);
    await printAdmissionCertificate({
      encounterId: dialogContext.encounterId,
      source: {
        departmentName: resolveOrganizationLabel(registerForm.organizationId) ?? '',
        inpatientNo: registerForm.encounterBusNo.trim(),
        patientName,
        genderText: resolveAdmissionCertificateGenderText(registerForm.genderEnum),
        ageText,
        phone: registerForm.phone.trim(),
        diagnosisText: resolveAdmissionCertificateDiagnosisText(
          registerForm.admissionDiagnosis,
          westernAdmissionDiagnosisOptions.value,
        ),
        conditionText: resolvePriorityLabel(registerForm.priorityEnum),
        admissionTimeText,
        // 住院证费用性质：与弹窗「费用性质」下拉一致（医生意向已预填到 contractId）
        paymentMethodText: resolveContractLabel(registerForm.contractId) || '',
        // 预交金：取弹窗支付行合计（表单无单独 advanceDepositAmount 字段）
        prepayAmount: sumPaymentDetailsAmount(registerForm.paymentDetailRows),
      },
    });
  } finally {
    printingCertificate.value = false;
  }
}

function resolveOrganizationLabel(organizationId: string): string | undefined {
  return organizationOptions.value.find((item) => String(item.value) === organizationId)?.label;
}

function getCurrentOrganizationId(): string {
  return String(registerForm.organizationId ?? '').trim();
}

async function applyPatientSnapshotToForm(
  snapshot: import('#/views/outpatient/charge/register/services/register-patient-identity.service').PatientIdentitySnapshot,
) {
  clearPatientSelectionOnly();
  const record = buildFormRecordFromSnapshot(snapshot);
  if (record.patientName) {
    registerForm.patientName = record.patientName;
  }
  registerForm.idCard = record.patientIdCard;
  registerForm.phone = record.patientPhone;
  registerForm.genderEnum = mapGenderFromPatient(record.patientGender);
  if (record.patientBirthday) {
    registerForm.birthDate = normalizeBirthDateForForm(record.patientBirthday);
    registerForm.age = resolveAgeFromBirthDate(registerForm.birthDate);
  } else if (registerForm.idCard) {
    syncBirthAgeFromIdCard(registerForm.idCard);
  }
  // 读卡快照不含婚姻/职业/现住址；先清空，匹配到档案后再由选人逻辑回填
  clearPatientDemographicsExtra();
  await syncDialogFormsFromModel();
}

async function maybeApplyRecommendedContract(
  preview: YbRegisterBenefitPreviewDto | null | undefined,
  match: RegisterContractMatchDto | null | undefined,
  autoApply: boolean,
) {
  if (!autoApply) {
    return;
  }
  const currentId = String(registerForm.contractId ?? '');
  const currentCategory = getInpatientContractCategory(currentId, contractMeta.value) ?? '';
  const resolvedId = String(preview?.resolvedContractId ?? '').trim();
  if (resolvedId && resolvedId !== currentId) {
    registerForm.contractId = resolvedId;
    await inpatientInfoFormApi.setFieldValue('contractId', registerForm.contractId);
    ElMessage.success(`已切换费用性质为：${match?.recommendedContractName ?? '省医保'}`);
    await syncBenefitPreview();
    return;
  }
  if (!match?.recommendedContractId) {
    return;
  }
  const shouldApply =
    match.recommendedContractId !== currentId &&
    (currentCategory === CHARGE_TYPE_CATEGORY.SELF_PAY ||
      !currentCategory ||
      match.matchStatus !== 'MATCH');
  if (!shouldApply) {
    return;
  }
  registerForm.contractId = match.recommendedContractId;
  await inpatientInfoFormApi.setFieldValue('contractId', registerForm.contractId);
  ElMessage.success(`已切换费用性质为：${match.recommendedContractName ?? '推荐项'}`);
  await syncBenefitPreview();
}

async function syncBenefitPreview(options?: { autoApplyRecommend?: boolean }) {
  benefitYbPreview.value = null;
  if (!lastYbCardInfo.value) {
    return;
  }
  let contractId = String(registerForm.contractId ?? '').trim();
  let category = getInpatientContractCategory(contractId, contractMeta.value);
  if (!isInsuranceContractCategory(category) && lastYbCardInfo.value) {
    const bootstrapId = lastYbIssuingAreaCode.value
      ? findInpatientBootstrapInsuranceContractIdByInsuplc(
          lastYbIssuingAreaCode.value,
          contractMeta.value,
        )
      : findInpatientBootstrapInsuranceContractId(contractMeta.value);
    if (bootstrapId) {
      contractId = bootstrapId;
      category = getInpatientContractCategory(contractId, contractMeta.value);
      registerForm.contractId = contractId;
      await inpatientInfoFormApi.setFieldValue('contractId', contractId);
    }
  }
  if (!contractId) {
    ElMessage.warning('请先选择医保类费用性质，以便拉取待遇信息');
    return;
  }
  if (!isInsuranceContractCategory(category)) {
    return;
  }
  const applyInpatientPreview = (preview: YbRegisterBenefitPreviewDto) => {
    const patientFallback = {
      patientBirthday: registerForm.birthDate,
      patientGender: registerForm.genderEnum,
      patientIdCard: registerForm.idCard,
      patientName: registerForm.patientName,
    };
    benefitYbPreview.value = enrichInsuranceBenefitPreview(preview, patientFallback);
    const insu = preview.insuinfoList?.[0];
    const nextInsutype = String(insu?.insutype ?? preview.insutype ?? '').trim();
    if (nextInsutype) {
      void applyInsutypeToForm(nextInsutype);
    }
  };
  const result = await syncInsuranceBenefit({
    contractBusNo: getInpatientContractBusNo(contractId, contractMeta.value),
    contractId,
    medType: INPATIENT_MED_TYPE,
    patientId: preserveApiIdForRequest(dialogContext.patientId),
    onTreatmentReady: (preview) => {
      applyInpatientPreview(preview);
    },
  });
  if (result?.preview) {
    applyInpatientPreview(result.preview);
    await maybeApplyRecommendedContract(
      result.preview,
      result.contractMatch ?? lastContractMatch.value,
      Boolean(options?.autoApplyRecommend),
    );
  }
}

async function bootstrapBenefitAfterCardRead() {
  await syncBenefitPreview({ autoApplyRecommend: true });
}

watch(
  () => registerForm.contractId,
  () => {
    resetBenefitPreviewCache();
    benefitYbPreview.value = null;
    if (lastYbCardInfo.value) {
      void syncBenefitPreview();
    }
  },
);

/** 从大陆居民身份证解析出生日期 */
function parseChineseIdBirth(idRaw: string): null | string {
  const s = String(idRaw || '').trim();
  if (s.length === 18) {
    return `${s.slice(6, 10)}-${s.slice(10, 12)}-${s.slice(12, 14)} 00:00:00`;
  }
  if (s.length === 15) {
    return `19${s.slice(6, 8)}-${s.slice(8, 10)}-${s.slice(10, 12)} 00:00:00`;
  }
  return null;
}

/** 证件号输入组件事件：解析身份证回填出生日期与年龄 */
const idCardFieldProps = {
  placeholder: '请输入居民身份证',
  maxlength: 18,
  onBlur: (e: FocusEvent) => {
    const el = e.target as HTMLInputElement | null;
    syncBirthAgeFromIdCard(el?.value ?? '');
  },
  onInput: (e: Event) => {
    const el = e.target as HTMLInputElement | null;
    const raw = String(el?.value ?? '').trim();
    if (raw.length === 15 || raw.length === 18) {
      syncBirthAgeFromIdCard(raw);
    }
  },
};

/** 无档登记时可选患者；有医生申请/只读/转医保时禁用下拉 */
const patientSelectDisabled = computed(
  () =>
    dialogReadOnly.value ||
    dialogMode.value === 'switchInsurance' ||
    Boolean(preserveApiIdForRequest(dialogContext.encounterId)),
);

/** 出生日期统一为登记表单 valueFormat */
function normalizeBirthDateForForm(raw?: string): string {
  if (!raw?.trim()) return '';
  const parsed = dayjs(raw.trim());
  if (!parsed.isValid()) return raw.trim();
  return parsed.format('YYYY-MM-DD HH:mm:ss');
}

/** 患者行性别 code → 登记表单 genderEnum */
function mapGenderFromPatient(genderCode?: string): string {
  const code = String(genderCode ?? '')
    .trim()
    .toUpperCase();
  if (code === 'MALE' || code === 'FEMALE') return code;
  return 'MALE';
}

/** 清空患者下拉选中态（不抹掉手工填写的姓名） */
function clearPatientSelectionOnly() {
  selectedPatientId.value = '';
  selectedPatientLabel.value = '';
  patientSelectError.value = '';
  patientRowById.value = {};
  dialogContext.patientId = undefined;
}

/** 清空患者下拉与基础信息中的患者姓名 */
function clearPatientSelectState() {
  clearPatientSelectionOnly();
  registerForm.patientName = '';
}

/** 按关键字分页拉取患者（pagePatientsForSelect） */
async function queryPatientOptions({
  keyword = '',
  pageNo = 1,
  pageSize = 20,
}: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
}) {
  const data = await httpPagePatientsForSelect({
    keyword: keyword.trim(),
    pageNo,
    pageSize,
  });
  const { content, totalCount } = parsePagedRowsFromApiData<PatientItemDto>(data);
  const next = { ...patientRowById.value };
  for (const row of content) {
    next[row.patientId] = row;
  }
  patientRowById.value = next;
  return {
    content,
    totalElements: totalCount,
  };
}

/** 未选中档案行时，将检索框内文字作为患者姓名（无 patientId 直登） */
function applyManualPatientNameFromSearch(draft: string) {
  const name = draft.trim();
  if (!name) {
    return;
  }
  patientSelectError.value = '';
  dialogContext.patientId = undefined;
  registerForm.patientName = name;
  selectedPatientLabel.value = name;
  void basicInfoFormApi.setFieldValue('patientName', name);
}

/** 检索框失焦：未选患者档案则按输入姓名登记 */
function handlePatientSearchBlur(draft: string) {
  if (patientSelectDisabled.value) return;
  if (preserveApiIdForRequest(selectedPatientId.value)) return;
  applyManualPatientNameFromSearch(draft);
}

/** 提交/校验前同步检索框文案（避免未失焦时 patientName 未写入） */
function syncPatientNameFromSearchDraft() {
  if (patientSelectDisabled.value) return;
  if (preserveApiIdForRequest(dialogContext.patientId)) return;
  if (preserveApiIdForRequest(selectedPatientId.value)) return;
  const draft = patientSelectRef.value?.getKeywordDraft?.() ?? '';
  if (draft) {
    applyManualPatientNameFromSearch(draft);
  }
}

/** 选中患者后回填基础信息 */
async function handlePatientSelect(
  value: string | string[],
  options?: { preserveInsuranceRead?: boolean },
) {
  if (options?.preserveInsuranceRead) {
    resetBenefitPreviewCache();
  } else {
    clearInsuranceCardState();
  }
  benefitYbPreview.value = null;
  patientSelectError.value = '';
  const patientId = Array.isArray(value) ? value[0] : value;
  if (!patientId) {
    const draft = (patientSelectRef.value?.getKeywordDraft?.() ?? '').trim();
    if (draft) {
      applyManualPatientNameFromSearch(draft);
      clearPatientSelectionOnly();
      clearPatientDemographicsExtra();
      return;
    }
    clearPatientSelectState();
    registerForm.genderEnum = 'MALE';
    registerForm.phone = '';
    registerForm.idCard = '';
    registerForm.birthDate = '';
    registerForm.age = undefined;
    clearPatientDemographicsExtra();
    await syncDialogFormsFromModel();
    return;
  }
  const row = patientRowById.value[patientId];
  if (!row) return;
  selectedPatientId.value = patientId;
  selectedPatientLabel.value = `${row.name}（${row.patientBusNo}）`;
  dialogContext.patientId = patientId;
  registerForm.patientName = row.name ?? '';
  registerForm.genderEnum = mapGenderFromPatient(row.genderCode);
  registerForm.phone = String(row.phoneMasked ?? '');
  registerForm.idCard = String(row.idCardNoMasked ?? '');
  if (row.birthDate) {
    registerForm.birthDate = normalizeBirthDateForForm(row.birthDate);
    const ageFromRow = row.age;
    registerForm.age =
      ageFromRow === undefined || ageFromRow === null
        ? resolveAgeFromBirthDate(registerForm.birthDate)
        : Number(ageFromRow);
  } else if (registerForm.idCard) {
    syncBirthAgeFromIdCard(registerForm.idCard);
  } else {
    registerForm.birthDate = '';
    registerForm.age = undefined;
  }
  // 检索列表无婚姻/职业/地址；拉患者详情补齐（失败时不阻断选人）
  try {
    const detail = await getPatientApi(patientId);
    if (detail) {
      if (detail.phone) {
        registerForm.phone = String(detail.phone);
      }
      if (detail.idCard) {
        registerForm.idCard = String(detail.idCard);
      }
      applyPatientDemographicsExtra(detail);
    } else {
      clearPatientDemographicsExtra();
    }
  } catch {
    clearPatientDemographicsExtra();
  }
  await syncDialogFormsFromModel();
  if (!options?.preserveInsuranceRead && lastYbCardInfo.value) {
    void syncBenefitPreview();
  }
}

const { cardReadAction, isCardReadDisabled, isCardReadLoading, readPatientIdentity } =
  useInpatientRegisterPatientIdentity({
    patientRowById,
    selectedPatientId,
    selectedPatientLabel,
    resolveOrganizationLabel,
    getOrganizationId: getCurrentOrganizationId,
    applyInsuranceSnapshot,
    applyPatientSnapshotToForm,
    fillPatientNameIfEmpty: fillInsurancePatientNameIfEmpty,
    resetBenefitState: clearInsuranceCardState,
    onPatientSelect: handlePatientSelect,
    onBootstrapBenefit: bootstrapBenefitAfterCardRead,
  });

const benefitPanelLoading = computed(() => benefitSyncing.value || cardReadAction.value !== null);

const benefitLoadingText = computed(() => {
  const action = cardReadAction.value;
  if (action) {
    return `正在读取${PATIENT_IDENTITY_SOURCE_LABEL[action]}…`;
  }
  if (benefitSyncing.value) {
    return '正在拉取人员与省市医保信息…';
  }
  return '';
});

const identityReadDisabled = computed(() => dialogReadOnly.value || benefitPanelLoading.value);

/** 同步患者下拉展示（有申请登记打开弹窗时） */
function syncPatientSelectDisplayFromContext(patient?: LoadRegistrationContextResult['patient']) {
  const patientId = preserveApiIdForRequest(patient?.patientId);
  if (!patientId) {
    clearPatientSelectState();
    return;
  }
  selectedPatientId.value = patientId;
  const name = patient?.name?.trim() ?? '';
  selectedPatientLabel.value = name || patientId;
  dialogContext.patientId = patientId;
  registerForm.patientName = name;
}

/** 证件号变更时同步年龄与出生日期（仍可手改） */
function syncBirthAgeFromIdCard(raw: string) {
  const trimmed = String(raw || '').trim();
  const birth = parseChineseIdBirth(trimmed);
  if (!birth) {
    if (!trimmed) {
      registerForm.birthDate = '';
      registerForm.age = undefined;
      void basicInfoFormApi.setFieldValue('birthDate', '');
      void basicInfoFormApi.setFieldValue('age', null);
    }
    return;
  }
  registerForm.birthDate = birth;
  registerForm.age = dayjs().diff(dayjs(birth), 'year');
  void basicInfoFormApi.setFieldValue('birthDate', birth);
  void basicInfoFormApi.setFieldValue('age', ageToFormValue(registerForm.age));
}

/**
 * 年龄变更：未填证件号时回写出生日期（年龄↔生日双向联动）。
 */
function handleAgeChange(val: null | number | undefined) {
  void (async () => {
    const values = await basicInfoFormApi.getValues<Record<string, unknown>>();
    if (String(values.idCard ?? registerForm.idCard ?? '').trim()) {
      return;
    }
    if (val === null || val === undefined || Number.isNaN(Number(val))) {
      return;
    }
    const age = Number(val);
    if (age < 0 || age > 150) {
      return;
    }
    const birth = `${birthDateFromAgeYears(age)} 00:00:00`;
    registerForm.age = age;
    registerForm.birthDate = birth;
    void basicInfoFormApi.setFieldValue('birthDate', birth);
  })();
}

/**
 * 出生日期变更：未填证件号时回写年龄（年龄↔生日双向联动）。
 */
function handleBirthDateChange(val: string) {
  void (async () => {
    const values = await basicInfoFormApi.getValues<Record<string, unknown>>();
    if (String(values.idCard ?? registerForm.idCard ?? '').trim()) {
      return;
    }
    const raw = String(val ?? '').trim();
    if (!raw) {
      return;
    }
    const ymd = raw.length >= 10 ? raw.slice(0, 10) : raw;
    const ageText = calcAgeFromBirthDate(ymd);
    if (!ageText) {
      return;
    }
    const age = Number(ageText);
    registerForm.birthDate = normalizeBirthDateForForm(raw);
    registerForm.age = age;
    void basicInfoFormApi.setFieldValue('age', ageToFormValue(age));
  })();
}

const tableColumns: TableColumn[] = [
  { type: 'index', label: '序号', width: 60, align: 'center', headerAlign: 'center' },
  {
    prop: 'encounterBusNo',
    label: '住院号',
    minWidth: 120,
    align: 'center',
    headerAlign: 'center',
  },
  { prop: 'patientName', label: '患者姓名', minWidth: 110, align: 'center', headerAlign: 'center' },
  {
    prop: 'contractDisplay',
    label: '费用性质',
    minWidth: 168,
    slot: 'contract',
    align: 'center',
    headerAlign: 'center',
    showOverflowTooltip: false,
  },
  {
    prop: 'insutype',
    label: '医保类别',
    minWidth: 120,
    align: 'center',
    headerAlign: 'center',
    formatter: (_row, _column, cellValue) => formatInsutype(String(cellValue ?? '').trim() || null),
  },
  {
    prop: 'statusEnumText',
    label: '登记状态',
    width: 100,
    slot: 'status',
    align: 'center',
    headerAlign: 'center',
  },
  {
    prop: 'organizationName',
    label: '住院科室',
    minWidth: 120,
    align: 'center',
    headerAlign: 'center',
  },
  { prop: 'wardName', label: '入院病区', width: 120, align: 'center', headerAlign: 'center' },
  { prop: 'bedName', label: '床位', width: 90, align: 'center', headerAlign: 'center' },
  { prop: 'startTime', label: '申请/入院时间', width: 170, align: 'center', headerAlign: 'center' },
  {
    prop: 'actions',
    label: '操作',
    width: 200,
    slot: 'actions',
    fixed: 'right',
    headerAlign: 'center',
    showOverflowTooltip: false,
  },
];

const dialogTitle = computed(() => {
  if (dialogMode.value === 'switchInsurance') {
    return '转医保';
  }
  if (dialogReadOnly.value) {
    return '查看';
  }
  return '登记';
});

const [BasicInfoForm, basicInfoFormApi] = useVbenForm(
  reactive({
    scrollToFirstError: true,
    commonConfig: {
      componentProps: {
        style: { width: '100%' },
      },
    },
    layout: 'horizontal',
    labelWidth: 88,
    wrapperClass: 'register-grid-form',
    schema: [
      {
        component: 'Input',
        fieldName: 'patientName',
        label: '患者',
        required: true,
      },
      {
        component: 'Input',
        fieldName: 'idCard',
        label: '证件号码',
        required: true,
        componentProps: idCardFieldProps,
        // 产品：填了则校 15/18 位格式；空串放行（无证建档 id_card 可空）
        rules: z.preprocess(
          (v) => (v === null || v === undefined ? '' : String(v)).trim(),
          z.string().refine((val) => isAcceptableChineseIdCardInput(val), {
            message: '请输入正确的身份证号格式（15位或18位）',
          }),
        ),
      },
      {
        component: 'DatePicker',
        fieldName: 'birthDate',
        label: '出生日期',
        required: true,
        componentProps: {
          type: 'date',
          valueFormat: 'YYYY-MM-DD HH:mm:ss',
          placeholder: '请选择出生日期',
          onChange: handleBirthDateChange,
        },
      },
      {
        component: 'InputNumber',
        fieldName: 'age',
        label: '年龄',
        required: true,
        componentProps: {
          min: 0,
          max: 150,
          controls: false,
          placeholder: '请输入年龄',
          onChange: handleAgeChange,
        },
      },
      {
        component: 'RadioGroup',
        fieldName: 'genderEnum',
        label: '性别',
        required: true,
        componentProps: {
          options: genderSelectOptions,
        },
      },
      {
        component: 'Input',
        fieldName: 'phone',
        label: '联系方式',
        required: true,
        componentProps: { placeholder: '请输入联系电话' },
      },
      {
        component: 'Select',
        fieldName: 'countryCode',
        label: '国籍',
        componentProps: {
          clearable: true,
          filterable: true,
          placeholder: '请选择国籍',
          options: ntlySelectOptions,
        },
      },
      {
        component: 'Select',
        fieldName: 'nationalityCode',
        label: '民族',
        componentProps: {
          clearable: true,
          filterable: true,
          placeholder: '请选择民族',
          options: natySelectOptions,
        },
      },
      {
        component: SelectArea,
        fieldName: 'naplCodes',
        label: '籍贯',
        modelPropName: 'modelValue',
        componentProps: {
          maxLevel: 2,
          placeholder: '请选择籍贯',
        },
      },
      {
        component: 'Select',
        fieldName: 'maritalStatusEnum',
        label: '婚姻',
        componentProps: {
          clearable: true,
          filterable: true,
          placeholder: '请选择婚姻状况',
          options: mrgStasSelectOptions,
        },
      },
      {
        component: 'Select',
        fieldName: 'prfsEnum',
        label: '职业',
        componentProps: {
          clearable: true,
          filterable: true,
          placeholder: '请选择职业',
          options: prfsSelectOptions,
        },
      },
      {
        component: SelectArea,
        fieldName: 'regionCodes',
        label: '现住址',
        modelPropName: 'modelValue',
        formItemClass: 'register-form-item--region',
        componentProps: {
          maxLevel: 3,
          placeholder: '请选择省市区',
        },
      },
      {
        component: 'Input',
        fieldName: 'address',
        label: '详细地址',
        formItemClass: 'register-form-item--address-detail',
        componentProps: { placeholder: '请输入现住址详细地址' },
      },
      {
        component: 'Input',
        fieldName: 'workCompany',
        label: '工作单位',
        formItemClass: 'register-form-item--work-company',
        componentProps: { placeholder: '请输入工作单位' },
      },
      {
        component: 'Input',
        fieldName: 'linkName',
        label: '联系人',
        componentProps: { placeholder: '请输入联系人姓名' },
      },
      {
        component: 'Select',
        fieldName: 'linkRelationCode',
        label: '关系',
        componentProps: {
          clearable: true,
          filterable: true,
          placeholder: '请选择关系',
          options: conerRltsSelectOptions,
        },
      },
      {
        component: 'Input',
        fieldName: 'linkTelcom',
        label: '联系电话',
        componentProps: { placeholder: '请输入联系人电话' },
      },
    ],
    showDefaultActions: false,
  }),
);

const [InpatientInfoForm, inpatientInfoFormApi] = useVbenForm(
  reactive({
    scrollToFirstError: true,
    commonConfig: {
      componentProps: {
        style: { width: '100%' },
      },
    },
    layout: 'horizontal',
    labelWidth: 88,
    wrapperClass: 'register-grid-form',
    schema: [
      {
        component: 'Input',
        fieldName: 'encounterBusNo',
        label: '住院号',
        componentProps: { disabled: true, placeholder: '-' },
      },
      {
        component: 'Input',
        fieldName: 'admissionCount',
        label: '住院次数',
        componentProps: { disabled: true, placeholder: '-' },
      },
      {
        component: 'Select',
        fieldName: 'organizationId',
        label: '入院科室',
        required: true,
        componentProps: {
          options: [],
          placeholder: '请选择入院科室',
          onChange: (value: string) => {
            void handleOrganizationChange(value);
          },
        },
      },
      {
        component: 'Select',
        fieldName: 'wardId',
        label: '入院病区',
        required: true,
        dependencies: {
          triggerFields: ['wardId'],
          trigger(values) {
            void nextTick(() => {
              void onWardIdFieldChange(String(values.wardId ?? ''));
            });
          },
        },
        componentProps: {
          options: [],
          clearable: true,
          filterable: true,
          placeholder: '请先选择入院科室',
        },
      },
      {
        component: 'Input',
        fieldName: 'admissionDiagnosisSlot',
        label: '入院诊断',
        required: true,
        formItemClass: 'register-form-item--admission-diagnosis',
      },
      {
        component: 'Input',
        fieldName: 'outpatientDoctor',
        label: '门诊医生',
        componentProps: { disabled: true, placeholder: '-' },
      },
      {
        component: 'Select',
        fieldName: 'attendingDoctorId',
        label: '主治医生',
        componentProps: {
          clearable: true,
          filterable: true,
          options: [],
          placeholder: '请先选择入院科室',
        },
      },
      {
        component: 'Select',
        fieldName: 'priorityEnum',
        label: '患者病情',
        componentProps: {
          clearable: true,
          placeholder: '请选择',
          options: [],
        },
      },
      {
        component: 'Select',
        fieldName: 'admitSourceCode',
        label: '入院类型',
        componentProps: {
          clearable: true,
          placeholder: '请选择',
          options: [],
        },
      },
      {
        component: 'Select',
        fieldName: 'inWayCode',
        label: '入院途径',
        componentProps: {
          clearable: true,
          placeholder: '请选择',
          options: [],
        },
      },
      {
        component: 'Select',
        fieldName: 'contractId',
        label: '费用性质',
        required: true,
        componentProps: { options: [], placeholder: '费用性质' },
      },
      {
        component: 'Input',
        fieldName: 'insutypeDisplay',
        label: '医保类别',
        componentProps: {
          disabled: true,
          placeholder: '读卡后自动带出（职工/居民）',
        },
      },
      {
        component: 'DatePicker',
        fieldName: 'startTime',
        label: '入院时间',
        required: true,
        componentProps: {
          type: 'datetime',
          valueFormat: 'YYYY-MM-DD HH:mm:ss',
          format: 'YYYY-MM-DD HH:mm:ss',
          placeholder: '请选择入院时间',
        },
      },
      {
        component: 'Input',
        fieldName: 'assignedBedDisplay',
        label: '入科床位',
        dependencies: {
          triggerFields: ['assignedBedDisplay'],
          if(values) {
            return Boolean(String(values.assignedBedDisplay ?? '').trim());
          },
        },
        componentProps: { disabled: true, placeholder: '-' },
      },
    ],
    showDefaultActions: false,
  }),
);

/** 解析当前登录操作人 ID。 */
function resolveOperatorId(): string | undefined {
  return preserveApiIdForRequest(userStore.userInfo?.id);
}

/** 将接口返回的费用性质选项写入页面级缓存。 */
function applyContractOptionsFromList(options?: OptionDto[]) {
  if (!options?.length) return;
  contractOptions.value = options.map((item) => ({
    label: item.label,
    value: String(item.value),
    disabled: item.disabled,
  }));
}

/** 将登记上下文中的入院方式/类型术语下拉写入页面级缓存。 */
function applyAdmissionTerminologyOptionsFromContext(context: LoadRegistrationContextResult) {
  if (context.inWayCodeOptions?.length) {
    inWayCodeOptions.value = context.inWayCodeOptions.map((item) => ({
      label: item.label,
      value: String(item.value),
      disabled: item.disabled,
    }));
  }
  if (context.admitSourceCodeOptions?.length) {
    admitSourceCodeOptions.value = context.admitSourceCodeOptions.map((item) => ({
      label: item.label,
      value: String(item.value),
      disabled: item.disabled,
    }));
  }
  if (context.admCondCodeOptions?.length) {
    admCondCodeOptions.value = context.admCondCodeOptions.map((item) => ({
      label: item.label,
      value: String(item.value),
      disabled: item.disabled,
    }));
  }
}

/** 费用性质未选时默认取第一项（仅无档登记弹窗）。 */
function applyDefaultContractIdIfEmpty() {
  if (registerForm.contractId) return;
  const first = contractOptions.value[0];
  if (first?.value) {
    registerForm.contractId = String(first.value);
  }
}

/**
 * 医生转住院意向（INSURANCE/SELF_PAY）直接预填「费用性质」下拉，不再单独展示「医生费用性质」。
 * 仅在尚无账户合同（contractId 为空）时生效，不覆盖已登记合同。
 */
function applyContractIdFromDoctorChargeIntent(chargeIntent?: string) {
  if (registerForm.contractId) {
    return;
  }
  const intent = String(chargeIntent ?? '')
    .trim()
    .toUpperCase();
  if (intent !== 'INSURANCE' && intent !== 'SELF_PAY') {
    return;
  }
  if (intent === 'SELF_PAY') {
    const selfPay = contractOptions.value.find((opt) =>
      isSelfPayContractCategory(
        getInpatientContractCategory(String(opt.value), contractMeta.value),
      ),
    );
    if (selfPay?.value) {
      registerForm.contractId = String(selfPay.value);
    }
    return;
  }
  // 医生意向「医保」默认市医保；无市医保再回落其它医保合同
  const municipal = contractOptions.value.find((opt) => {
    const cat = getInpatientContractCategory(String(opt.value), contractMeta.value);
    return (
      cat === CHARGE_TYPE_CATEGORY.MUNICIPAL_INSURANCE || cat === 'MUNICIPAL_INSURANCE'
    );
  });
  if (municipal?.value) {
    registerForm.contractId = String(municipal.value);
    return;
  }
  const insurance = contractOptions.value.find((opt) =>
    isInsuranceContractCategory(
      getInpatientContractCategory(String(opt.value), contractMeta.value),
    ),
  );
  if (insurance?.value) {
    registerForm.contractId = String(insurance.value);
  }
}

/** 当前科室费用性质下拉是否包含指定契约。 */
function isContractInCurrentOptions(contractId: string): boolean {
  const id = String(contractId ?? '').trim();
  if (!id) {
    return false;
  }
  return contractOptions.value.some((item) => String(item.value) === id);
}

/**
 * 科室切换后重载费用性质下拉：保留仍有效的已选/读卡匹配项，避免误回落到列表第一项（省医保）。
 */
function restoreContractIdAfterOrganizationReload(previousContractId: string) {
  const previous = String(previousContractId ?? '').trim();
  if (previous && isContractInCurrentOptions(previous)) {
    registerForm.contractId = previous;
    return;
  }
  const recommended = String(lastContractMatch.value?.recommendedContractId ?? '').trim();
  if (recommended && isContractInCurrentOptions(recommended)) {
    registerForm.contractId = recommended;
    return;
  }
  if (lastYbCardInfo.value) {
    const bootstrapId = lastYbIssuingAreaCode.value
      ? findInpatientBootstrapInsuranceContractIdByInsuplc(
          lastYbIssuingAreaCode.value,
          contractMeta.value,
        )
      : findInpatientBootstrapInsuranceContractId(contractMeta.value);
    if (bootstrapId && isContractInCurrentOptions(bootstrapId)) {
      registerForm.contractId = bootstrapId;
      return;
    }
  }
  registerForm.contractId = '';
  applyDefaultContractIdIfEmpty();
}

/** 组装登记提交的入院诊断行（西医单条 / 中医一病一症）。 */
function buildAdmissionDiagnosesForSubmit(): AdmissionDiagnosisSubmitDto[] | undefined {
  const rows = buildAdmissionDiagnosesFromFormState(registerForm.admissionDiagnosis);
  return rows.length > 0 ? rows : undefined;
}

/** 护士分床后拼接「病区 / 床位」展示文案；未分床返回空串以隐藏表单项。 */
function resolveAssignedBedDisplay(encounter?: LoadRegistrationContextResult['encounter']): string {
  const bedId = preserveApiIdForRequest(encounter?.bedId);
  const bedName = encounter?.bedName?.trim();
  if (!bedId && !bedName) {
    return '';
  }
  const wardName = encounter?.wardName?.trim();
  if (wardName && bedName) {
    return `${wardName} / ${bedName}`;
  }
  if (bedName) {
    return bedName;
  }
  if (wardName) {
    return wardName;
  }
  return '';
}

/** 重置住院信息扩展字段为默认值 */
function resetInpatientInfoExtendedFields() {
  registerForm.admissionCount = '';
  registerForm.assignedBedDisplay = '';
  registerForm.attendingDoctorId = '';
  registerForm.admissionDiagnosis = createEmptyAdmissionDiagnosisFormState();
  attendingDoctorOptions.value = [];
}

/** 根据出生日期推算年龄 */
function resolveAgeFromBirthDate(birthDate?: string) {
  if (!birthDate) return undefined;
  const parsed = dayjs(birthDate);
  if (!parsed.isValid()) return undefined;
  return dayjs().diff(parsed, 'year');
}

/** InputNumber 清空须传 null，undefined 不会回写控件 */
function ageToFormValue(age: number | undefined): number | null {
  if (age === undefined || age === null || Number.isNaN(Number(age))) {
    return null;
  }
  return Number(age);
}

/** 入院时间表单回显：保留完整时刻，缺省为当前时间 */
function normalizeStartTimeForForm(raw: string | undefined | null): string {
  const trimmed = String(raw ?? '').trim();
  if (!trimmed) {
    return dayjs().format('YYYY-MM-DD HH:mm:ss');
  }
  const parsed = dayjs(trimmed);
  return parsed.isValid()
    ? parsed.format('YYYY-MM-DD HH:mm:ss')
    : dayjs().format('YYYY-MM-DD HH:mm:ss');
}

/** 入院时间提交前补齐时分秒（仅日期时取当前时刻，避免落库 00:00:00） */
function normalizeStartTimeForSubmit(startTime: string) {
  const raw = startTime.trim();
  if (!raw) return '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    const now = dayjs();
    return dayjs(raw)
      .hour(now.hour())
      .minute(now.minute())
      .second(now.second())
      .format('YYYY-MM-DD HH:mm:ss');
  }
  const parsed = dayjs(raw);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : raw;
}

/** 出生日期提交前补齐时分秒（与后端 LocalDateTime 契约一致） */
function normalizeBirthDateForSubmit(birthDate?: string) {
  const raw = String(birthDate ?? '').trim();
  if (!raw) return '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    return `${raw} 00:00:00`;
  }
  return normalizeBirthDateForForm(raw);
}

/**
 * 提交前对齐出生日期与年龄：写侧只认 birthDate，年龄由读侧推算。
 * 仅填年龄时按周岁反推出生日期（与患者档案编辑弹窗一致）。
 */
async function alignBirthDateAndAgeBeforeSubmit() {
  const basicInfoValues = await basicInfoFormApi.getValues<Record<string, unknown>>();
  const idCard = String(basicInfoValues.idCard ?? registerForm.idCard ?? '').trim();
  const birthFromId = idCard ? parseChineseIdBirth(idCard) : null;
  let birthDate = normalizeBirthDateForForm(
    String(basicInfoValues.birthDate ?? registerForm.birthDate ?? ''),
  );
  const ageRaw = basicInfoValues.age;
  let age: number | undefined =
    ageRaw === null || ageRaw === undefined || ageRaw === '' ? registerForm.age : Number(ageRaw);

  if (birthFromId) {
    birthDate = normalizeBirthDateForForm(birthFromId);
    age = dayjs().diff(dayjs(birthFromId), 'year');
  } else if (birthDate) {
    age = resolveAgeFromBirthDate(birthDate) ?? age;
  } else if (age !== undefined && age !== null && !Number.isNaN(Number(age))) {
    birthDate = dayjs().subtract(Number(age), 'year').format('YYYY-MM-DD HH:mm:ss');
  }

  registerForm.idCard = idCard;
  registerForm.birthDate = birthDate;
  registerForm.age =
    age === undefined || age === null || Number.isNaN(Number(age)) ? undefined : Number(age);

  await basicInfoFormApi.setFieldValue('idCard', registerForm.idCard);
  await basicInfoFormApi.setFieldValue('birthDate', registerForm.birthDate);
  await basicInfoFormApi.setFieldValue('age', ageToFormValue(registerForm.age));
}

/** 将登记上下文写入弹窗表单。 */
async function applyContextToDialog(
  context: LoadRegistrationContextResult,
  mode: RegisterDialogMode = 'register',
) {
  dialogMode.value = mode;
  switchToInsuranceEnabled.value = Boolean(context.switchToInsuranceEnabled);
  switchToSelfPayEnabled.value = Boolean(context.switchToSelfPayEnabled);
  if (mode === 'view') {
    dialogReadOnly.value = true;
  } else if (mode === 'switchInsurance') {
    dialogReadOnly.value = false;
  } else {
    dialogReadOnly.value = Boolean(context.readOnly);
  }
  applyContractOptionsFromList(context.contractOptions);
  applyAdmissionTerminologyOptionsFromContext(context);
  if (context.organizationOptions?.length) {
    organizationOptions.value = context.organizationOptions.map((item) => ({
      label: item.label,
      value: String(item.value),
    }));
  }
  const patient = context.patient;
  const encounter = context.encounter;
  dialogContext.encounterId = preserveApiIdForRequest(encounter?.encounterId);
  dialogContext.patientId = preserveApiIdForRequest(patient?.patientId);
  viewedEncounterStatus.value = encounter?.statusEnum ?? '';
  viewedBedId.value = preserveApiIdForRequest(encounter?.bedId) ?? '';
  syncPatientSelectDisplayFromContext(patient);
  registerForm.genderEnum = patient?.genderEnum ?? 'MALE';
  registerForm.phone = patient?.phone ?? '';
  registerForm.idCard = patient?.idCard ?? '';
  registerForm.birthDate = normalizeBirthDateForForm(patient?.birthDate);
  registerForm.age = resolveAgeFromBirthDate(registerForm.birthDate);
  if (registerForm.idCard && !registerForm.birthDate) {
    syncBirthAgeFromIdCard(registerForm.idCard);
  }
  if (patient) {
    applyPatientDemographicsExtra(patient);
  } else {
    clearPatientDemographicsExtra();
  }
  registerForm.encounterBusNo = encounter?.encounterBusNo ?? '';
  registerForm.organizationId = preserveApiIdForRequest(encounter?.organizationId) ?? '';
  registerForm.wardId = preserveApiIdForRequest(encounter?.wardId) ?? '';
  // 已有就诊：回显优先合同；未开户时按医生意向预填费用性质，再回落第一项。
  registerForm.contractId = preserveApiIdForRequest(encounter?.contractId) ?? '';
  applyContractIdFromDoctorChargeIntent(encounter?.chargeIntent);
  applyDefaultContractIdIfEmpty();
  registerForm.insutype = String(encounter?.insutype ?? '').trim();
  registerForm.startTime = normalizeStartTimeForForm(encounter?.startTime);
  registerForm.admitSourceCode =
    encounter?.admitSourceCode?.trim() || DEFAULT_ADMIT_SOURCE_CODE_ROUTINE;
  registerForm.inWayCode = encounter?.inWayCode ?? '';
  registerForm.priorityEnum =
    normalizeAdmCondTermCode(encounter?.priorityEnum) || DEFAULT_ADM_COND_CODE_GENERAL;
  resetInpatientInfoExtendedFields();
  registerForm.outpatientDoctor = encounter?.outpatientDoctorName?.trim() ?? '';
  registerForm.attendingDoctorId = preserveApiIdForRequest(encounter?.attendingDoctorId) ?? '';
  registerForm.assignedBedDisplay = resolveAssignedBedDisplay(encounter);
  registerForm.admissionDiagnosis = {
    ...createEmptyAdmissionDiagnosisFormState(),
    readonlyDisplay:
      encounter?.primaryConditionDefinitionName?.trim() ||
      encounter?.primaryConditionDefinitionId?.trim() ||
      '',
  };
  // 已有住院线（医生申请/已登记）或弹窗只读查看时，入院诊断不可编辑
  admissionDiagnosisReadonly.value = dialogReadOnly.value || Boolean(encounter?.encounterId);
  registerForm.admissionCount = '-';
  // 医生意向总额只预填到支付行，款员按行拆分渠道，界面不单独展示总额
  const draftAdvance = Number(encounter?.advanceDepositAmount ?? 0);
  let seedAmount = 0;
  if (Number.isFinite(draftAdvance) && draftAdvance > 0) {
    seedAmount = draftAdvance;
  }
  registerForm.paymentDetailRows = createSeedPaymentDetailRows(seedAmount);
  clearInsuranceCardState();
  await refreshContractOptionsSchema();
  await refreshWesternAdmissionDiagnosisOptionsSchema();
  await refreshAdmissionTerminologyOptionsSchema();
  await refreshOrganizationOptionsSchema();
  await loadWardSelectOptions();
  await loadAttendingDoctorOptions();
  await refreshAttendingDoctorOptionsSchema();
  await syncDialogFormsFromModel();
  await applyWardIdEcho(registerForm.wardId);
  if (mode === 'switchInsurance') {
    clearInsuranceCardState();
    await setDialogFormsForSwitchInsurance();
    return;
  }
  await setDialogFormsDisabled(dialogReadOnly.value);
}

/** 重置无档登记空白弹窗。 */
async function resetDialogForNoRecord() {
  resetRegisterFormModel();
  applyDefaultContractIdIfEmpty();
  clearInsuranceCardState();
  await refreshContractOptionsSchema();
  await refreshWesternAdmissionDiagnosisOptionsSchema();
  await refreshAdmissionTerminologyOptionsSchema();
  await loadWardSelectOptions();
  await loadAttendingDoctorOptions();
  await refreshAttendingDoctorOptionsSchema();
  await syncDialogFormsFromModel();
  await setDialogFormsDisabled(false);
  admissionDiagnosisReadonly.value = false;
}

/** 清空登记弹窗 reactive model（不含 vben 控件）。 */
function resetRegisterFormModel() {
  dialogMode.value = 'register';
  switchToInsuranceEnabled.value = false;
  switchToSelfPayEnabled.value = false;
  dialogReadOnly.value = false;
  dialogContext.encounterId = undefined;
  dialogContext.patientId = undefined;
  clearPatientSelectState();
  registerForm.genderEnum = 'MALE';
  registerForm.phone = '';
  registerForm.idCard = '';
  registerForm.birthDate = '';
  registerForm.age = undefined;
  clearPatientDemographicsExtra();
  registerForm.encounterBusNo = '';
  registerForm.organizationId = '';
  registerForm.wardId = '';
  registerForm.contractId = '';
  registerForm.insutype = '';
  registerForm.startTime = dayjs().format('YYYY-MM-DD HH:mm:ss');
  registerForm.admitSourceCode = DEFAULT_ADMIT_SOURCE_CODE_ROUTINE;
  registerForm.inWayCode = '';
  registerForm.priorityEnum = DEFAULT_ADM_COND_CODE_GENERAL;
  registerForm.outpatientDoctor = '';
  resetInpatientInfoExtendedFields();
  registerForm.admissionCount = '-';
  registerForm.paymentDetailRows = createSeedPaymentDetailRows(0);
  wardCapacityHint.value = '';
}

/** 弹窗关闭后清空 model 与 vben 表单（避免 InputNumber 等控件残留）。 */
async function clearRegisterDialogOnClose() {
  resetRegisterFormModel();
  clearInsuranceCardState();
  registerDetailTab.value = 'inpatient';
  await basicInfoFormApi.resetForm();
  await inpatientInfoFormApi.resetForm();
}

/** 请求关闭登记弹窗（实际清空在 @closed 回调）。 */
function requestCloseRegisterDialog() {
  registerDialogVisible.value = false;
}

/** 登记弹窗完全关闭后清空表单。 */
async function handleRegisterDialogClosed() {
  await clearRegisterDialogOnClose();
}

/** 有医生申请时入院诊断只读（登记不重写主诊） */
async function refreshAdmissionDiagnosisFieldSchema() {
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'admissionDiagnosisSlot',
      rules: admissionDiagnosisReadonly.value ? null : 'selectRequired',
      formItemClass: admissionDiagnosisReadonly.value
        ? 'register-form-item--admission-diagnosis register-form-item--admission-diagnosis-readonly'
        : 'register-form-item--admission-diagnosis',
    },
  ]);
}

/** 打开弹窗时把当前 model 值写入 vben 表单。 */
async function syncDialogFormsFromModel() {
  await basicInfoFormApi.setValues({
    patientName: registerForm.patientName,
    genderEnum: registerForm.genderEnum,
    phone: registerForm.phone,
    age: ageToFormValue(registerForm.age),
    idCard: registerForm.idCard,
    birthDate: registerForm.birthDate,
    countryCode: registerForm.countryCode || undefined,
    nationalityCode: registerForm.nationalityCode || undefined,
    naplCodes: [...registerForm.naplCodes],
    maritalStatusEnum: registerForm.maritalStatusEnum || undefined,
    prfsEnum: registerForm.prfsEnum || undefined,
    regionCodes: [...registerForm.regionCodes],
    address: registerForm.address,
    workCompany: registerForm.workCompany,
    linkName: registerForm.linkName,
    linkRelationCode: registerForm.linkRelationCode || undefined,
    linkTelcom: registerForm.linkTelcom,
  });
  await inpatientInfoFormApi.setValues({
    encounterBusNo: registerForm.encounterBusNo || '-',
    admissionCount: registerForm.admissionCount || '-',
    organizationId: registerForm.organizationId,
    wardId: registerForm.wardId,
    assignedBedDisplay: registerForm.assignedBedDisplay,
    outpatientDoctor: registerForm.outpatientDoctor,
    attendingDoctorId: registerForm.attendingDoctorId,
    admissionDiagnosisSlot: ' ',
    contractId: registerForm.contractId,
    insutypeDisplay: resolveInsutypeDisplay(registerForm.insutype),
    startTime: registerForm.startTime,
    admitSourceCode: registerForm.admitSourceCode,
    inWayCode: registerForm.inWayCode,
    priorityEnum: registerForm.priorityEnum,
  });
}

/** 提交前读取 vben 表单值并回写当前 model。 */
async function syncModelFromDialogForms() {
  const basicInfoValues = await basicInfoFormApi.getValues<Record<string, unknown>>();
  const inpatientInfoValues = await inpatientInfoFormApi.getValues<Record<string, unknown>>();
  registerForm.patientName = String(basicInfoValues.patientName ?? registerForm.patientName);
  registerForm.genderEnum = String(basicInfoValues.genderEnum ?? 'MALE');
  registerForm.phone = String(basicInfoValues.phone ?? '');
  const ageValue = basicInfoValues.age;
  registerForm.age =
    ageValue === null || ageValue === undefined || ageValue === '' ? undefined : Number(ageValue);
  registerForm.idCard = String(basicInfoValues.idCard ?? '');
  registerForm.birthDate = String(basicInfoValues.birthDate ?? '');
  registerForm.countryCode = String(basicInfoValues.countryCode ?? '156');
  registerForm.nationalityCode = String(basicInfoValues.nationalityCode ?? '01');
  const naplRaw = basicInfoValues.naplCodes;
  registerForm.naplCodes = Array.isArray(naplRaw)
    ? naplRaw.map((code) => String(code)).filter(Boolean)
    : [];
  registerForm.maritalStatusEnum = String(basicInfoValues.maritalStatusEnum ?? '');
  registerForm.prfsEnum = String(basicInfoValues.prfsEnum ?? '');
  const regionRaw = basicInfoValues.regionCodes;
  registerForm.regionCodes = Array.isArray(regionRaw)
    ? regionRaw.map((code) => String(code)).filter(Boolean)
    : [];
  registerForm.address = String(basicInfoValues.address ?? '');
  registerForm.workCompany = String(basicInfoValues.workCompany ?? '');
  registerForm.linkName = String(basicInfoValues.linkName ?? '');
  registerForm.linkRelationCode = String(basicInfoValues.linkRelationCode ?? '');
  registerForm.linkTelcom = String(basicInfoValues.linkTelcom ?? '');
  registerForm.organizationId = String(inpatientInfoValues.organizationId ?? '');
  registerForm.wardId = String(inpatientInfoValues.wardId ?? '');
  registerForm.attendingDoctorId = String(inpatientInfoValues.attendingDoctorId ?? '');
  registerForm.contractId = String(inpatientInfoValues.contractId ?? '');
  registerForm.startTime = String(inpatientInfoValues.startTime ?? '');
  registerForm.admitSourceCode = String(inpatientInfoValues.admitSourceCode ?? '');
  registerForm.inWayCode = String(inpatientInfoValues.inWayCode ?? '');
  registerForm.priorityEnum = normalizeAdmCondTermCode(
    String(inpatientInfoValues.priorityEnum ?? ''),
  );
}

/** 页面级登记字典：费用性质（无就诊时拉取上下文）。 */
async function loadRegistrationFormBootstrap() {
  try {
    const context = await httpLoadInpatientRegistrationContext({});
    applyContractOptionsFromList(context.contractOptions);
  } catch {
    void 0;
  }
}

/** 加载西医入院诊断目录（诊断定义元数据分页，仅 diagnosisKindCode=1）。 */
async function loadWesternAdmissionDiagnosisOptions() {
  try {
    const res = await httpGetConditionDefinitionMetadata({
      diagnosisKindCode: '1',
      pageNo: 1,
      pageSize: 500,
    });
    westernAdmissionDiagnosisOptions.value = (res.content ?? [])
      .filter((row) => row.conditionDefinitionId && row.conditionName)
      .map((row) => ({
        label: String(row.conditionName),
        value: String(row.conditionDefinitionId),
      }));
    await refreshWesternAdmissionDiagnosisOptionsSchema();
  } catch {
    westernAdmissionDiagnosisOptions.value = [];
    await refreshWesternAdmissionDiagnosisOptionsSchema();
  }
}

/** 住院登记页初始化：列表、主数据字典、登记弹窗下拉。 */
async function loadRegistrationPageInit() {
  try {
    const init = await httpLoadInpatientRegistrationPageInit({});
    payChannelOptions.value = optionDtosToPayChannelOptions(init.paymentChannelOptions);
  } catch {
    payChannelOptions.value = [...PAY_CHANNEL_OPTIONS];
  }
  try {
    contractMeta.value = await loadInpatientRegisterContractMeta();
  } catch {
    contractMeta.value = { contractCategoryById: {}, contractInsuranceProfileById: {} };
  }
  await Promise.all([
    loadOrganizationOptions(),
    loadRegistrationFormBootstrap(),
    loadWesternAdmissionDiagnosisOptions(),
    handleQuery(),
  ]);
}

/** 按只读态切换弹窗表单禁用。 */
async function setDialogFormsDisabled(disabled: boolean) {
  const disabledProps = { disabled };
  await basicInfoFormApi.updateSchema([
    {
      fieldName: 'patientName',
      componentProps: disabledProps,
    },
    {
      fieldName: 'idCard',
      componentProps: disabled ? disabledProps : idCardFieldProps,
    },
    {
      fieldName: 'birthDate',
      componentProps: disabled
        ? { disabled: true, type: 'date', valueFormat: 'YYYY-MM-DD HH:mm:ss' }
        : {
            type: 'date',
            valueFormat: 'YYYY-MM-DD HH:mm:ss',
            placeholder: '请选择出生日期',
            onChange: handleBirthDateChange,
          },
    },
    {
      fieldName: 'age',
      componentProps: disabled
        ? { disabled: true, min: 0, max: 150, controls: false }
        : {
            min: 0,
            max: 150,
            controls: false,
            placeholder: '请输入年龄',
            onChange: handleAgeChange,
          },
    },
    {
      fieldName: 'genderEnum',
      componentProps: { ...disabledProps, options: genderSelectOptions },
    },
    {
      fieldName: 'phone',
      componentProps: disabledProps,
    },
    {
      fieldName: 'countryCode',
      componentProps: {
        ...disabledProps,
        clearable: true,
        filterable: true,
        placeholder: '请选择国籍',
        options: ntlySelectOptions,
      },
    },
    {
      fieldName: 'nationalityCode',
      componentProps: {
        ...disabledProps,
        clearable: true,
        filterable: true,
        placeholder: '请选择民族',
        options: natySelectOptions,
      },
    },
    {
      fieldName: 'naplCodes',
      componentProps: {
        ...disabledProps,
        maxLevel: 2,
        placeholder: '请选择籍贯',
      },
    },
    {
      fieldName: 'maritalStatusEnum',
      componentProps: {
        ...disabledProps,
        clearable: true,
        filterable: true,
        placeholder: '请选择婚姻状况',
        options: mrgStasSelectOptions,
      },
    },
    {
      fieldName: 'prfsEnum',
      componentProps: {
        ...disabledProps,
        clearable: true,
        filterable: true,
        placeholder: '请选择职业',
        options: prfsSelectOptions,
      },
    },
    {
      fieldName: 'regionCodes',
      componentProps: {
        ...disabledProps,
        maxLevel: 3,
        placeholder: '请选择省市区',
      },
    },
    {
      fieldName: 'address',
      componentProps: {
        ...disabledProps,
        placeholder: '请输入现住址详细地址',
      },
    },
    {
      fieldName: 'workCompany',
      componentProps: {
        ...disabledProps,
        placeholder: '请输入工作单位',
      },
    },
    {
      fieldName: 'linkName',
      componentProps: {
        ...disabledProps,
        placeholder: '请输入联系人姓名',
      },
    },
    {
      fieldName: 'linkRelationCode',
      componentProps: {
        ...disabledProps,
        clearable: true,
        filterable: true,
        placeholder: '请选择关系',
        options: conerRltsSelectOptions,
      },
    },
    {
      fieldName: 'linkTelcom',
      componentProps: {
        ...disabledProps,
        placeholder: '请输入联系人电话',
      },
    },
  ]);
  const inpatientEditableFields = [
    'organizationId',
    'wardId',
    'attendingDoctorId',
    'contractId',
    'startTime',
    'admitSourceCode',
    'inWayCode',
    'priorityEnum',
  ] as const;
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'organizationId',
      componentProps: { disabled, options: organizationOptions.value },
    },
    {
      fieldName: 'wardId',
      componentProps: { disabled, options: wardOptions.value },
    },
    {
      fieldName: 'attendingDoctorId',
      componentProps: {
        disabled,
        clearable: true,
        filterable: true,
        options: attendingDoctorOptions.value,
        placeholder: registerForm.organizationId ? '请选择主治医生' : '请先选择入院科室',
      },
    },
    ...inpatientEditableFields
      .filter(
        (fieldName) =>
          fieldName !== 'organizationId' &&
          fieldName !== 'wardId' &&
          fieldName !== 'attendingDoctorId',
      )
      .map((fieldName) => ({ fieldName, componentProps: disabledProps })),
  ]);
}

/** 转医保模式：仅费用性质与主治医生可编辑，其余只读。 */
async function setDialogFormsForSwitchInsurance() {
  await setDialogFormsDisabled(true);
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'contractId',
      componentProps: { disabled: false },
    },
    {
      fieldName: 'attendingDoctorId',
      componentProps: {
        disabled: false,
        clearable: true,
        filterable: true,
        options: attendingDoctorOptions.value,
        placeholder: registerForm.organizationId ? '请选择主治医生' : '请先选择入院科室',
      },
    },
  ]);
}

/**
 * 按入院科室/病区拉取主治医生下拉（角色=医生）。
 * 院点若在 init 响应带回 defaultAttendingDoctorId，且当前未选手治，则自动带出。
 */
async function loadAttendingDoctorOptions() {
  const organizationId = preserveApiIdForRequest(registerForm.organizationId);
  if (!organizationId) {
    attendingDoctorOptions.value = [];
    return;
  }
  const wardLocationId = preserveApiIdForRequest(registerForm.wardId);
  try {
    const result = await httpLoadAdmissionParticipantInit({
      organizationId,
      ...(wardLocationId ? { wardLocationId } : {}),
    });
    attendingDoctorOptions.value = (result.doctorOptions ?? [])
      .map((item) => ({
        label: item.label,
        value: String(item.value),
      }))
      .filter((item) => item.label && item.value);
    // 院点默认主治：仅在当前未选手治时带出，避免覆盖已选/回显
    const defaultAttendingId = preserveApiIdForRequest(result.defaultAttendingDoctorId);
    if (!registerForm.attendingDoctorId && defaultAttendingId) {
      const matched = attendingDoctorOptions.value.some(
        (item) => String(item.value) === defaultAttendingId,
      );
      if (matched) {
        registerForm.attendingDoctorId = defaultAttendingId;
        await inpatientInfoFormApi.setFieldValue('attendingDoctorId', defaultAttendingId);
      }
    }
  } catch {
    attendingDoctorOptions.value = [];
  }
}

/** 刷新主治医生下拉 schema。 */
async function refreshAttendingDoctorOptionsSchema() {
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'attendingDoctorId',
      componentProps: {
        clearable: true,
        filterable: true,
        options: attendingDoctorOptions.value,
        placeholder: registerForm.organizationId ? '请选择主治医生' : '请先选择入院科室',
      },
    },
  ]);
}

/** 刷新费用性质下拉 schema。 */
async function refreshContractOptionsSchema() {
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'contractId',
      componentProps: { options: contractOptions.value },
    },
  ]);
}

/** 刷新西医入院诊断下拉（供 AdmissionDiagnosisField 使用）。 */
async function refreshWesternAdmissionDiagnosisOptionsSchema() {
  await refreshAdmissionDiagnosisFieldSchema();
}

/** 刷新入院方式/入院类型/患者病情下拉 schema。 */
async function refreshAdmissionTerminologyOptionsSchema() {
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'priorityEnum',
      componentProps: {
        clearable: true,
        placeholder: '请选择',
        options: admCondCodeOptions.value,
      },
    },
    {
      fieldName: 'admitSourceCode',
      componentProps: {
        clearable: true,
        placeholder: '请选择',
        options: admitSourceCodeOptions.value,
      },
    },
    {
      fieldName: 'inWayCode',
      componentProps: {
        clearable: true,
        placeholder: '请选择',
        options: inWayCodeOptions.value,
      },
    },
  ]);
}

/** 刷新住院科室下拉 schema。 */
async function refreshOrganizationOptionsSchema() {
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'organizationId',
      componentProps: {
        options: organizationOptions.value,
        onChange: (value: string) => {
          void handleOrganizationChange(value);
        },
      },
    },
  ]);
}

/** 按科室重新拉取费用性质下拉。 */
async function reloadContractOptionsByOrganization(organizationId: string) {
  const orgId = preserveApiIdForRequest(organizationId);
  try {
    const context = await httpLoadInpatientRegistrationContext({
      organizationId: orgId,
    });
    applyContractOptionsFromList(context.contractOptions);
    await refreshContractOptionsSchema();
  } catch {
    void 0;
  }
}

/** 科室变更后清空病区并按科室加载病区列表；费用性质保留读卡/已选匹配项。 */
async function handleOrganizationChange(organizationId: string) {
  registerForm.organizationId = organizationId;
  registerForm.wardId = '';
  registerForm.attendingDoctorId = '';
  const previousContractId = String(registerForm.contractId ?? '');
  wardCapacityHint.value = '';
  await reloadContractOptionsByOrganization(organizationId);
  restoreContractIdAfterOrganizationReload(previousContractId);
  await loadWardSelectOptions();
  // 先清空表单主治，再拉下拉；院点默认主治在 loadAttendingDoctorOptions 内回填
  await inpatientInfoFormApi.setFieldValue('attendingDoctorId', '');
  await loadAttendingDoctorOptions();
  await refreshAttendingDoctorOptionsSchema();
  await applyWardIdEcho('');
  await inpatientInfoFormApi.setFieldValue('contractId', registerForm.contractId);
}

/** 病区字段变更：同步 model 并查询床位余量（勿在 componentProps.onChange 里处理，避免与 Select 双向绑定冲突）。 */
async function onWardIdFieldChange(selectedWardId: string) {
  const normalized = preserveApiIdForRequest(selectedWardId) ?? '';
  registerForm.wardId = normalized;
  wardCapacityHint.value = '';
  if (!normalized) {
    await loadAttendingDoctorOptions();
    await refreshAttendingDoctorOptionsSchema();
    return;
  }
  try {
    const capacity = await httpLoadWardBedCapacity({ wardId: normalized });
    const vacant = Number(capacity.vacantBedCount ?? 0);
    const total = Number(capacity.totalBedCount ?? 0);
    if (Number.isFinite(vacant) && Number.isFinite(total)) {
      wardCapacityHint.value = `空床 ${vacant} / 总床 ${total}`;
    }
  } catch {
    void 0;
  }
  await loadAttendingDoctorOptions();
  await refreshAttendingDoctorOptionsSchema();
}

/** 按当前入院科室拉取病区下拉选项（只更新 options，不赋值）。 */
async function loadWardSelectOptions() {
  const organizationId = preserveApiIdForRequest(registerForm.organizationId);
  if (organizationId) {
    try {
      const result = await httpListWardsByDepartment({ organizationId });
      wardOptions.value = (result.wards ?? [])
        .map((item) => {
          const value = preserveApiIdForRequest(item.wardId) ?? '';
          const label = (item.wardName ?? '').trim();
          if (!value || !label) {
            return null;
          }
          return { label, value };
        })
        .filter((item): item is OptionDto => item !== null);
    } catch {
      wardOptions.value = [];
    }
  } else {
    wardOptions.value = [];
  }
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'wardId',
      componentProps: {
        options: wardOptions.value,
        clearable: true,
        filterable: true,
        placeholder: organizationId ? '请选择入院病区' : '请先选择入院科室',
      },
    },
  ]);
  await nextTick();
}

/** 病区 options 就绪后再回显 wardId（避免首次打开弹窗时 Select 尚未挂载/无 options）。 */
async function applyWardIdEcho(wardIdRaw?: string) {
  const wardId = preserveApiIdForRequest(wardIdRaw) ?? '';
  registerForm.wardId = wardId;
  await nextTick();
  await nextTick();
  await inpatientInfoFormApi.setFieldValue('wardId', wardId);
  if (wardId) {
    await onWardIdFieldChange(wardId);
  } else {
    wardCapacityHint.value = '';
  }
}

/** 按筛选条件与分页刷新列表数据。 */
async function handleQuery() {
  loading.value = true;
  try {
    const statuses = queryParams.registrationListStatuses;
    const organizationId = preserveApiIdForRequest(queryParams.organizationId);
    const wardId = preserveApiIdForRequest(queryParams.wardId);
    const result = await httpPageInpatientRegistrationQueue({
      keyword: queryParams.keyword.trim() || undefined,
      idCard: queryParams.idCard.trim() || undefined,
      organizationId: organizationId || undefined,
      wardId: wardId || undefined,
      admissionDateFrom: queryParams.admissionDateFrom.trim() || undefined,
      admissionDateTo: queryParams.admissionDateTo.trim() || undefined,
      registrationListStatuses: statuses.length > 0 ? [...statuses] : undefined,
      // 单选兼容：仅选一个状态时同步传 registrationListStatus
      registrationListStatus: statuses.length === 1 ? statuses[0] : undefined,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
    });
    tableData.value = result.content ?? [];
    total.value = Number(result.totalCount ?? 0);
  } finally {
    loading.value = false;
  }
}

/** 列表筛选：按所选住院科室加载病区下拉。 */
async function loadFilterWardOptions(organizationIdRaw?: string) {
  const organizationId = preserveApiIdForRequest(organizationIdRaw) ?? '';
  if (!organizationId) {
    filterWardOptions.value = [];
    return;
  }
  try {
    const result = await httpListWardsByDepartment({ organizationId });
    filterWardOptions.value = (result.wards ?? [])
      .map((item) => {
        const value = preserveApiIdForRequest(item.wardId) ?? '';
        const label = (item.wardName ?? '').trim();
        if (!value || !label) {
          return null;
        }
        return { label, value };
      })
      .filter((item): item is OptionDto => item !== null);
  } catch {
    filterWardOptions.value = [];
  }
}

/** 列表筛选科室变更：清空病区并重载病区选项。 */
async function handleFilterOrganizationChange(organizationId: string) {
  queryParams.wardId = '';
  await loadFilterWardOptions(organizationId);
}

/** 加载住院科室与入院方式/类型术语下拉。 */
async function loadOrganizationOptions() {
  const context = await httpLoadInpatientRegistrationContext({});
  organizationOptions.value = (context.organizationOptions ?? []).map((item) => ({
    label: item.label,
    value: String(item.value),
  }));
  applyAdmissionTerminologyOptionsFromContext(context);
  await refreshAdmissionTerminologyOptionsSchema();
}

/** 打开登记弹窗并加载上下文。 */
async function handleOpenRegister(row: RegistrationQueueItemDto) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) {
    ElMessage.warning('缺少就诊标识，无法登记');
    return;
  }
  const context = await httpLoadInpatientRegistrationContext({
    encounterId,
    organizationId: preserveApiIdForRequest(row.organizationId),
  });
  registerDialogVisible.value = true;
  await nextTick();
  await nextTick();
  await applyContextToDialog(context, 'register');
}

/** 打开转医保弹窗（读卡补录 + 2401）。 */
async function handleOpenSwitchInsurance(row: RegistrationQueueItemDto) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) {
    ElMessage.warning('缺少就诊标识，无法转医保');
    return;
  }
  const context = await httpLoadInpatientRegistrationContext({
    encounterId,
    organizationId: preserveApiIdForRequest(row.organizationId),
  });
  if (!context.switchToInsuranceEnabled) {
    ElMessage.warning('当前就诊不允许转医保');
    return;
  }
  registerDialogVisible.value = true;
  await nextTick();
  await nextTick();
  await applyContextToDialog(context, 'switchInsurance');
  admissionDiagnosisReadonly.value = true;
}

/**
 * 打开更正入院科室弹窗。默认带出当前科室、病区和床，收费员可以不换。
 */
async function openDepartmentChangeDialog() {
  if (!departmentChangeEnabled.value) {
    return;
  }
  departmentChangeForm.organizationId = registerForm.organizationId;
  departmentChangeForm.wardId = '';
  departmentChangeForm.bedId = '';
  departmentChangeWards.value = [];
  departmentChangeBeds.value = [];
  departmentChangeVisible.value = true;
  await loadDepartmentChangeWards();
  const currentWardId = preserveApiIdForRequest(registerForm.wardId) ?? '';
  const wardStillListed = departmentChangeWards.value.some(
    (item) => preserveApiIdForRequest(item.wardId) === currentWardId,
  );
  if (currentWardId && wardStillListed) {
    departmentChangeForm.wardId = currentWardId;
    await loadDepartmentChangeBeds(currentWardId);
  }
}

/** 加载全部启用病区，不随入院科室收窄，以便改到其他科室的病区。 */
async function loadDepartmentChangeWards() {
  const result = await httpListAdmissionDepartmentWards();
  departmentChangeWards.value = result.wards ?? [];
}

/**
 * 按所选病区加载空床。已入科时包含当前床；病区未换则回显当前床。
 *
 * @param wardId 病区主键
 */
async function loadDepartmentChangeBeds(wardId: string) {
  const previousBedId = preserveApiIdForRequest(departmentChangeForm.bedId) ?? '';
  departmentChangeForm.bedId = '';
  if (!wardId || !departmentChangeNeedsBed.value) {
    departmentChangeBeds.value = [];
    return;
  }
  const encounterId = preserveApiIdForRequest(dialogContext.encounterId);
  const result = await httpListAdmissionDepartmentBeds({
    wardId,
    encounterId,
  });
  departmentChangeBeds.value = result.beds ?? [];
  const currentWardId = preserveApiIdForRequest(registerForm.wardId) ?? '';
  const currentBedId = preserveApiIdForRequest(viewedBedId.value) ?? '';
  let preferredBedId = previousBedId;
  if (!preferredBedId && wardId === currentWardId) {
    preferredBedId = currentBedId;
  }
  const bedStillListed = departmentChangeBeds.value.some(
    (item) => preserveApiIdForRequest(item.bedId) === preferredBedId,
  );
  if (preferredBedId && bedStillListed) {
    departmentChangeForm.bedId = preferredBedId;
  }
}

/** 病区下拉文案：带主挂靠科室，当前病区标明。 */
function formatDepartmentChangeWardLabel(ward: WardOptionDto): string {
  const wardName = ward.wardName?.trim() ?? '';
  const organizationName = ward.organizationName?.trim() ?? '';
  let label = wardName;
  if (organizationName && wardName) {
    label = `${organizationName} / ${wardName}`;
  } else if (organizationName) {
    label = organizationName;
  }
  const currentWardId = preserveApiIdForRequest(registerForm.wardId) ?? '';
  if (currentWardId && preserveApiIdForRequest(ward.wardId) === currentWardId) {
    return `${label}（当前病区）`;
  }
  return label;
}

/** 床位下拉文案：当前床标明，其余用病房/床名。 */
function formatDepartmentChangeBedLabel(bed: AdmissionDepartmentBedOptionDto): string {
  const houseName = bed.houseName?.trim() ?? '';
  const bedName = bed.bedName?.trim() ?? '';
  let label = bedName;
  if (houseName && bedName) {
    label = `${houseName} / ${bedName}`;
  } else if (houseName) {
    label = houseName;
  }
  if (bed.currentBed) {
    return `${label}（当前床）`;
  }
  return label;
}

/**
 * 更换入院科室。病区和床不跟着清空，可以只改科室。
 *
 * @param value 科室主键
 */
function onDepartmentChangeOrganization(value: number | string) {
  departmentChangeForm.organizationId = preserveApiIdForRequest(value) ?? '';
}

/**
 * 更换目标病区后重载可选床。
 *
 * @param value 病区主键
 */
function onDepartmentChangeWard(value: number | string) {
  const wardId = preserveApiIdForRequest(value) ?? '';
  departmentChangeForm.wardId = wardId;
  void loadDepartmentChangeBeds(wardId);
}

/** 提交更正入院科室。已医保入院时后端会发 2403，失败则院内也不改。 */
async function submitDepartmentChange() {
  const operatorId = resolveOperatorId();
  const encounterId = preserveApiIdForRequest(dialogContext.encounterId);
  const organizationId = preserveApiIdForRequest(departmentChangeForm.organizationId);
  const wardId = preserveApiIdForRequest(departmentChangeForm.wardId);
  const bedId = preserveApiIdForRequest(departmentChangeForm.bedId);
  if (!operatorId) {
    ElMessage.warning('无法获取当前操作人，请重新登录');
    return;
  }
  if (!encounterId || !organizationId || !wardId) {
    ElMessage.warning('请选择入院科室和病区');
    return;
  }
  if (departmentChangeNeedsBed.value && !bedId) {
    ElMessage.warning('已入科请选择目标病区的床位');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '确认更正入院科室？已在医保入院的，会同时向医保平台变更入院科室。',
      '科室变更',
      {
        cancelButtonText: '取消',
        confirmButtonText: '确认变更',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  departmentChangeSubmitting.value = true;
  try {
    const result = await httpChangeAdmissionDepartment({
      encounterId,
      operatorId,
      organizationId,
      wardId,
      bedId: departmentChangeNeedsBed.value ? bedId : undefined,
    });
    if (!result.success) {
      ElMessage.error('科室变更失败，请稍后重试');
      return;
    }
    if (result.insurancePlatformChanged) {
      ElMessage.success('入院科室已变更，并已通知医保');
    } else {
      ElMessage.success('入院科室已变更');
    }
    departmentChangeVisible.value = false;
    registerDialogVisible.value = false;
    await handleQuery();
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '科室变更失败，请稍后重试';
    if (message) {
      ElMessage.error(message);
    }
  } finally {
    departmentChangeSubmitting.value = false;
  }
}

/** 查看弹窗内进入转医保模式。 */
async function enterSwitchInsuranceFromView() {
  if (!switchToInsuranceEnabled.value) {
    return;
  }
  dialogMode.value = 'switchInsurance';
  dialogReadOnly.value = false;
  clearInsuranceCardState();
  admissionDiagnosisReadonly.value = true;
  await setDialogFormsForSwitchInsurance();
}

/** 打开查看弹窗（只读）。 */
async function handleOpenView(row: RegistrationQueueItemDto) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) {
    ElMessage.warning('缺少就诊标识，无法查看');
    return;
  }
  const context = await httpLoadInpatientRegistrationContext({
    encounterId,
    organizationId: preserveApiIdForRequest(row.organizationId),
  });
  registerDialogVisible.value = true;
  await nextTick();
  await nextTick();
  await applyContextToDialog(context, 'view');
  admissionDiagnosisReadonly.value = true;
}

/** 无档登记：打开空白弹窗。 */
async function handleOpenNoRecordRegister() {
  registerDialogVisible.value = true;
  await nextTick();
  await nextTick();
  await resetDialogForNoRecord();
}

async function resolveInsuranceFieldsForSubmit(contractId: string) {
  if (!lastYbCardInfo.value) {
    throw new Error('医保登记须先读身份证、医保卡或医保码');
  }
  if (lastYbMediumType.value === '03' && !lastYbCardSn.value.trim()) {
    throw new Error('社保卡读卡缺少卡识别码，请重新读卡');
  }
  let preview = lastYbPreview.value;
  if (!preview?.psnNo) {
    await syncBenefitPreview();
    preview = lastYbPreview.value;
  }
  if (!preview?.psnNo) {
    throw new Error('医保人员信息未确认，请重新读卡');
  }
  const insu = preview.insuinfoList?.[0];
  return {
    insuplcAdmdvs: insu?.insuplcAdmdvs ?? preview.insuplcAdmdvs,
    insutype: insu?.insutype ?? preview.insutype,
    medType: INPATIENT_MED_TYPE,
    psnNo: preview.psnNo,
    ybCardInfo: lastYbCardInfo.value,
    ybMediumType: lastYbMediumType.value,
  };
}

/** 校验登记弹窗两块表单，未通过时由 vben 在对应表单项下展示错误 */
async function validateRegisterDialogForms(): Promise<boolean> {
  const hasDoctorAdmission = Boolean(preserveApiIdForRequest(dialogContext.encounterId));
  if (hasDoctorAdmission) {
    patientSelectError.value = '';
    await basicInfoFormApi.setFieldValue('patientName', registerForm.patientName);
    const fieldNames = ['organizationId', 'wardId', 'contractId', 'startTime'] as const;
    for (const fieldName of fieldNames) {
      const fieldResult = await inpatientInfoFormApi.validateField(fieldName);
      if (!fieldResult.valid) {
        return false;
      }
    }
    return true;
  }
  syncPatientNameFromSearchDraft();
  await basicInfoFormApi.setFieldValue('patientName', registerForm.patientName);
  if (!registerForm.patientName.trim()) {
    patientSelectError.value = '请选择患者档案或直接输入患者姓名';
    return false;
  }
  patientSelectError.value = '';
  const basicResult = await basicInfoFormApi.validate();
  if (!basicResult.valid) {
    return false;
  }
  const inpatientResult = await inpatientInfoFormApi.validate();
  if (!inpatientResult.valid) {
    return false;
  }
  if (!hasDoctorAdmission) {
    const diagnosisError = validateAdmissionDiagnosisFormState(registerForm.admissionDiagnosis);
    if (diagnosisError) {
      ElMessage.warning(diagnosisError);
      return false;
    }
  }
  return true;
}

/** 提交住院登记。 */
async function handleSubmitRegister() {
  if (dialogReadOnly.value || dialogMode.value === 'switchInsurance') return;
  syncPatientNameFromSearchDraft();
  await alignBirthDateAndAgeBeforeSubmit();
  const formValid = await validateRegisterDialogForms();
  if (!formValid) {
    return;
  }
  await syncModelFromDialogForms();
  const operatorId = resolveOperatorId();
  if (!operatorId) {
    ElMessage.warning('无法获取当前操作人，请重新登录');
    return;
  }
  const organizationId = preserveApiIdForRequest(registerForm.organizationId);
  const wardId = preserveApiIdForRequest(registerForm.wardId);
  let contractId = preserveApiIdForRequest(registerForm.contractId);
  if (!contractId) {
    applyDefaultContractIdIfEmpty();
    contractId = preserveApiIdForRequest(registerForm.contractId);
    if (contractId) {
      await inpatientInfoFormApi.setFieldValue('contractId', registerForm.contractId);
    }
  }
  const normalizedStartTime = normalizeStartTimeForSubmit(registerForm.startTime);
  const hasDoctorAdmission = Boolean(preserveApiIdForRequest(dialogContext.encounterId));
  if (!organizationId || !wardId || !contractId || !normalizedStartTime) {
    ElMessage.warning('请完善入院科室、入院病区、费用性质和入院时间');
    return;
  }
  if (
    isInsuranceContractSelected.value &&
    !preserveApiIdForRequest(registerForm.attendingDoctorId)
  ) {
    ElMessage.warning('医保登记须选择主治医生');
    return;
  }
  dialogSubmitting.value = true;
  try {
    let insuranceFields: Record<string, string | undefined> = {};
    if (isInsuranceContractSelected.value) {
      try {
        insuranceFields = await resolveInsuranceFieldsForSubmit(contractId);
      } catch (error) {
        const message = error instanceof Error ? error.message : '医保读卡失败';
        ElMessage.error(message);
        return;
      }
    }
    // 总额由支付行合计得出；医生草稿仅作打开时的预填
    const paymentDetails = buildPaymentDetailsForSubmit();
    const advanceAmount = sumPaymentDetailsAmount(paymentDetails);
    const submitPatientId = preserveApiIdForRequest(dialogContext.patientId);
    const normalizedBirthDate = normalizeBirthDateForSubmit(registerForm.birthDate);
    const attendingDoctorId = preserveApiIdForRequest(registerForm.attendingDoctorId);
    const regionCodes = registerForm.regionCodes;
    const body = {
      encounterId: preserveApiIdForRequest(dialogContext.encounterId),
      patientId: submitPatientId,
      patientName: registerForm.patientName,
      genderEnum: registerForm.genderEnum,
      idCard: registerForm.idCard || undefined,
      birthDate: normalizedBirthDate || undefined,
      phone: registerForm.phone || undefined,
      // 空串也传：后端叠写主档；登记后 bootstrap 病案首页从主档自动取值
      countryCode: registerForm.countryCode ?? '',
      nationalityCode: registerForm.nationalityCode ?? '',
      nativePlace: joinNativePlaceCodes(registerForm.naplCodes),
      maritalStatusEnum: registerForm.maritalStatusEnum ?? '',
      prfsEnum: registerForm.prfsEnum ?? '',
      address: registerForm.address ?? '',
      addressProvince: regionCodes[0] ?? '',
      addressCity: regionCodes[1] ?? '',
      addressDistrict: regionCodes[2] ?? '',
      workCompany: registerForm.workCompany ?? '',
      linkName: registerForm.linkName ?? '',
      linkRelationCode: registerForm.linkRelationCode
        ? Number(registerForm.linkRelationCode)
        : undefined,
      linkTelcom: registerForm.linkTelcom ?? '',
      updatePatientFromIdCard: true,
      organizationId,
      wardId,
      contractId,
      attendingDoctorId: attendingDoctorId || undefined,
      startTime: normalizedStartTime,
      admitSourceCode: registerForm.admitSourceCode || undefined,
      inWayCode: registerForm.inWayCode || undefined,
      priorityEnum: registerForm.priorityEnum || undefined,
      admissionDiagnoses: hasDoctorAdmission ? undefined : buildAdmissionDiagnosesForSubmit(),
      operatorId,
      advanceDepositAmount: advanceAmount > 0 ? advanceAmount : undefined,
      paymentDetails,
      ...insuranceFields,
    };
    const result = await httpSubmitInpatientRegistration(body);
    let successMsg = `登记成功，住院号 ${result.encounterBusNo}`;
    if (result.ybMdtrtId) {
      successMsg += `（医保就诊ID：${result.ybMdtrtId}）`;
    }
    ElMessage.success(successMsg);
    registerDialogVisible.value = false;
    await handleQuery();
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '登记提交失败，请稍后重试';
    if (message) {
      ElMessage.error(message);
    }
  } finally {
    dialogSubmitting.value = false;
  }
}

/** 校验转医保弹窗：费用性质、主治医生须已选。 */
async function validateSwitchInsuranceForms(): Promise<boolean> {
  const contractResult = await inpatientInfoFormApi.validateField('contractId');
  if (!contractResult.valid) {
    return false;
  }
  const doctorResult = await inpatientInfoFormApi.validateField('attendingDoctorId');
  if (!doctorResult.valid) {
    return false;
  }
  if (!isInsuranceContractSelected.value) {
    ElMessage.warning('请选择省/市医保费用性质');
    return false;
  }
  return true;
}

/** 提交自费转医保（IF-IP-REG-07）。 */
async function handleSwitchToInsurance() {
  if (dialogMode.value !== 'switchInsurance') {
    return;
  }
  await syncModelFromDialogForms();
  const formValid = await validateSwitchInsuranceForms();
  if (!formValid) {
    return;
  }
  const operatorId = resolveOperatorId();
  if (!operatorId) {
    ElMessage.warning('无法获取当前操作人，请重新登录');
    return;
  }
  const encounterId = preserveApiIdForRequest(dialogContext.encounterId);
  const contractId = preserveApiIdForRequest(registerForm.contractId);
  const attendingDoctorId = preserveApiIdForRequest(registerForm.attendingDoctorId);
  if (!encounterId || !contractId || !attendingDoctorId) {
    ElMessage.warning('请完善费用性质与主治医生');
    return;
  }
  let insuranceFields: Record<string, string | undefined> = {};
  try {
    insuranceFields = await resolveInsuranceFieldsForSubmit(contractId);
  } catch (error) {
    const message = error instanceof Error ? error.message : '医保读卡失败';
    ElMessage.error(message.replace('医保登记', '转医保'));
    return;
  }
  try {
    await ElMessageBox.confirm(
      '确认将该住院自费就诊转为医保？未结费用将挂至医保账户，预交金仍保留在个人现金户。',
      '转医保',
      {
        cancelButtonText: '取消',
        confirmButtonText: '确认转医保',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  dialogSubmitting.value = true;
  try {
    const result = await httpSwitchInpatientToInsurance({
      encounterId,
      contractId,
      attendingDoctorId,
      operatorId,
      psnNo: insuranceFields.psnNo ?? '',
      insuplcAdmdvs: insuranceFields.insuplcAdmdvs,
      insutype: insuranceFields.insutype,
      medType: insuranceFields.medType,
      ybCardInfo: insuranceFields.ybCardInfo,
      ybMediumType: insuranceFields.ybMediumType,
    });
    if (!result.success) {
      ElMessage.error('转医保失败，请稍后重试');
      return;
    }
    let successMsg = '转医保成功';
    if (result.ybMdtrtId) {
      successMsg += `（医保就诊ID：${result.ybMdtrtId}）`;
    }
    if (result.switchedChargeItemCount != null && result.switchedChargeItemCount > 0) {
      successMsg += `，已切换 ${result.switchedChargeItemCount} 条未结费用`;
    }
    ElMessage.success(successMsg);
    registerDialogVisible.value = false;
    await handleQuery();
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '转医保失败，请稍后重试';
    if (message) {
      ElMessage.error(message);
    }
  } finally {
    dialogSubmitting.value = false;
  }
}

/** 医保入院整单改为自费：撤销平台入院，未结费用改挂个人现金户。 */
async function handleSwitchToSelfPay(encounterIdRaw: string | undefined) {
  const encounterId = preserveApiIdForRequest(encounterIdRaw);
  const operatorId = resolveOperatorId();
  // 没有住院就诊就无法撤销平台入院
  if (!encounterId) {
    ElMessage.warning('缺少就诊标识，无法转自费');
    return;
  }
  // 操作人写入切户审计
  if (!operatorId) {
    ElMessage.warning('无法获取当前操作人，请重新登录');
    return;
  }
  try {
    await ElMessageBox.confirm(
      '确认将该住院从医保入院改为自费？将撤销医保入院登记，未结费用改挂个人现金账户，已收预交金保留。已结算费用须先取消结算。',
      '转自费',
      {
        cancelButtonText: '取消',
        confirmButtonText: '确认转自费',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  selfPaySwitching.value = true;
  try {
    const result = await httpSwitchInpatientToSelfPay({
      encounterId,
      operatorId,
    });
    if (!result.success) {
      ElMessage.error('转自费失败，请稍后重试');
      return;
    }
    let successMsg = '已转为自费';
    if (result.switchedChargeItemCount != null && result.switchedChargeItemCount > 0) {
      successMsg += `，已切换 ${result.switchedChargeItemCount} 条未结费用`;
    }
    ElMessage.success(successMsg);
    registerDialogVisible.value = false;
    await handleQuery();
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '转自费失败，请稍后重试';
    if (message) {
      ElMessage.error(message);
    }
  } finally {
    selfPaySwitching.value = false;
  }
}

/** 取消住院登记。已医保入院时先撤销平台入院（2404），再取消本地登记。 */
async function handleCancelRegistration(row: RegistrationQueueItemDto) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) return;
  const operatorId = resolveOperatorId();
  if (!operatorId) {
    ElMessage.warning('无法获取当前操作人，请重新登录');
    return;
  }
  const platformAdmitted = Boolean(row.insurancePlatformRegistered);
  try {
    if (platformAdmitted) {
      await ElMessageBox.confirm(
        '该记录已在医保平台完成入院登记，确认后将先撤销医保入院（2404），再取消本地住院登记。',
        '取消住院登记',
        {
          confirmButtonText: '确认取消',
          cancelButtonText: '返回',
          type: 'warning',
        },
      );
      await httpCancelInsuranceInpatientAdmission({
        encounterId,
        operatorId,
        cancelReason: '取消登记',
      });
      ElMessage.success('已取消住院登记，并已撤销医保入院');
    } else {
      const { value } = await ElMessageBox.prompt('请输入取消原因（可选）', '取消住院登记', {
        confirmButtonText: '确认取消',
        cancelButtonText: '返回',
        inputPlaceholder: '取消原因',
      });
      await httpCancelInpatientRegistration({
        encounterId,
        operatorId,
        cancelReason: value?.trim() || undefined,
      });
      ElMessage.success('已取消住院登记');
    }
    await handleQuery();
  } catch {
    // 用户关闭弹窗时不提示
  }
}

/** 是否展示「取消」。已医保入院的待入科记录也走取消，确认后自动 2404。 */
function canShowCancel(row: RegistrationQueueItemDto) {
  return Boolean(row.cancelable);
}

/** 列表分页变更。 */
function handlePagination({ page, limit }: { limit: number; page: number }) {
  queryParams.pageNo = page;
  queryParams.pageSize = limit;
  void handleQuery();
}

/** 顶部筛选查询：回到第一页。 */
function handleSearch() {
  queryParams.pageNo = 1;
  void handleQuery();
}

/**
 * 费用性质列的合同名（市医保/省医保/自费）。
 * 无合同时回落医生费用性质意向（医保/自费）；再无则险种标签或破折号。
 *
 * @param row 登记队列行
 */
function feeNatureContractLabel(row: RegistrationQueueItemDto): string {
  const contract = String(row.contractDisplay ?? '').trim();
  if (contract) {
    return contract;
  }
  // 待登记：尚无账户合同时展示医生费用性质意向（医保/自费）
  const chargeIntentText = String(row.chargeIntentText ?? '').trim();
  if (chargeIntentText) {
    return chargeIntentText;
  }
  if (feeNatureInsutypeLabel(row)) {
    return '';
  }
  return '—';
}

/**
 * 险种展示文案：310 职工医保，390 居民医保，340 离休。
 * 其它码或未读卡不展示，列表仍只显示合同名。
 *
 * @param row 登记队列行
 */
function feeNatureInsutypeLabel(row: RegistrationQueueItemDto): string {
  const code = String(row.insutype ?? '').trim();
  if (code === '310') {
    return '职工医保';
  }
  if (code === '390') {
    return '居民医保';
  }
  if (code === '340') {
    return '离休';
  }
  return '';
}

/**
 * 险种标签颜色：职工蓝、居民绿、离休灰。
 *
 * @param row 登记队列行
 */
function feeNatureInsutypeTagType(
  row: RegistrationQueueItemDto,
): 'info' | 'primary' | 'success' {
  const code = String(row.insutype ?? '').trim();
  if (code === '310') {
    return 'primary';
  }
  if (code === '390') {
    return 'success';
  }
  return 'info';
}

/** 行状态标签类型。 */
function getStatusTagType(row: RegistrationQueueItemDto) {
  if (row.statusEnum === ENCOUNTER_STATUS.PLANNED) return 'warning';
  if (row.bedId) return 'success';
  return 'primary';
}

/** 是否展示「登记」按钮。 */
function canRegister(row: RegistrationQueueItemDto) {
  return row.statusEnum === ENCOUNTER_STATUS.PLANNED;
}

/** 是否展示「查看」：非待登记均可查阅（含在院/待结算/已结算/已出院等）。 */
function canView(row: RegistrationQueueItemDto) {
  return row.statusEnum !== ENCOUNTER_STATUS.PLANNED;
}

onMounted(() => {
  if (typeof ResizeObserver !== 'undefined' && tableAreaRef.value) {
    tableAreaResizeObserver = new ResizeObserver(() => {
      const height = tableAreaRef.value?.clientHeight ?? 0;
      tableAreaHeight.value = height > 0 ? height : undefined;
    });
    tableAreaResizeObserver.observe(tableAreaRef.value);
  }
  void loadRegistrationPageInit();
});

onBeforeUnmount(() => {
  tableAreaResizeObserver?.disconnect();
  tableAreaResizeObserver = null;
});
</script>

<template>
  <Page auto-content-height content-class="!p-2 !pb-0 flex min-h-0 flex-col overflow-hidden">
    <div
      ref="tableAreaRef"
      class="ip-register-list flex h-full min-h-0 flex-1 flex-col overflow-hidden"
    >
      <RegisterStyleTableLayout
        class="ip-register-list__table h-full min-h-0 flex-1 !p-0"
        :table-data="tableData"
        :loading="loading"
        :total="total"
        :query-params="queryParams"
        :table-columns="tableColumns"
        :table-height="tableAreaHeight"
        :show-pagination="true"
        :show-top-query="false"
        @pagination="handlePagination"
      >
        <template #toolbar-actions>
          <RegisterListToolbar
            v-model:keyword="queryParams.keyword"
            v-model:id-card="queryParams.idCard"
            v-model:organization-id="queryParams.organizationId"
            v-model:ward-id="queryParams.wardId"
            v-model:admission-date-from="queryParams.admissionDateFrom"
            v-model:admission-date-to="queryParams.admissionDateTo"
            v-model:registration-list-statuses="queryParams.registrationListStatuses"
            :organization-options="organizationOptions"
            :ward-options="filterWardOptions"
            @organization-change="handleFilterOrganizationChange"
            @search="handleSearch"
            @no-record="handleOpenNoRecordRegister"
          />
        </template>
        <template #status="{ row }">
          <ElTag :type="getStatusTagType(row)" effect="light">{{ row.statusEnumText }}</ElTag>
        </template>
        <template #contract="{ row }">
          <span class="ip-register-fee">
            <span v-if="feeNatureContractLabel(row)">{{ feeNatureContractLabel(row) }}</span>
            <ElTag
              v-if="feeNatureInsutypeLabel(row)"
              size="small"
              effect="plain"
              :type="feeNatureInsutypeTagType(row)"
            >
              {{ feeNatureInsutypeLabel(row) }}
            </ElTag>
          </span>
        </template>
        <template #actions="{ row }">
          <div class="flex items-center justify-center gap-2">
            <ElButton v-if="canRegister(row)" link type="primary" @click="handleOpenRegister(row)">
              登记
            </ElButton>
            <ElButton v-if="canView(row)" link type="primary" @click="handleOpenView(row)">
              查看
            </ElButton>
            <ElButton
              v-if="row.switchToInsuranceEnabled"
              link
              type="primary"
              @click="handleOpenSwitchInsurance(row)"
            >
              转医保
            </ElButton>
            <ElButton
              v-if="row.switchToSelfPayEnabled"
              link
              type="primary"
              :loading="selfPaySwitching"
              @click="handleSwitchToSelfPay(row.encounterId)"
            >
              转自费
            </ElButton>
            <ElButton
              v-if="canShowCancel(row)"
              link
              type="danger"
              @click="handleCancelRegistration(row)"
            >
              取消
            </ElButton>
          </div>
        </template>
      </RegisterStyleTableLayout>
    </div>

    <AppDialog
      v-model="registerDialogVisible"
      :title="dialogTitle"
      width="1200px"
      top="3vh"
      @closed="handleRegisterDialogClosed"
    >
      <div class="register-dialog-body">
        <section class="form-section">
          <header class="form-section__title form-section__title--with-actions">
            <span>基础信息</span>
            <div v-if="!dialogReadOnly" class="form-section__actions">
              <ElButton
                size="small"
                :loading="isCardReadLoading('yb-card')"
                :disabled="identityReadDisabled || isCardReadDisabled('yb-card')"
                @click="readPatientIdentity('yb-card')"
              >
                医保卡
              </ElButton>
              <ElButton
                size="small"
                :loading="isCardReadLoading('id')"
                :disabled="identityReadDisabled || isCardReadDisabled('id')"
                @click="readPatientIdentity('id')"
              >
                身份证
              </ElButton>
              <ElButton
                size="small"
                :loading="isCardReadLoading('yb-ec')"
                :disabled="identityReadDisabled || isCardReadDisabled('yb-ec')"
                @click="readPatientIdentity('yb-ec')"
              >
                医保码
              </ElButton>
              <ElTag v-if="lastYbPreview?.psnNo" type="success" effect="plain" size="small">
                已读卡 {{ lastYbPreview.psnNo }}
              </ElTag>
            </div>
          </header>
          <BasicInfoForm>
            <template #patientName>
              <SelectTable
                v-if="!patientSelectDisabled"
                ref="patientSelectRef"
                placeholder="搜索患者（姓名/病历号/证件号）"
                :columns="patientSelectColumns"
                :remote-method="queryPatientOptions"
                value-key="patientId"
                label-key="name"
                :selected-label="selectedPatientLabel"
                v-model="selectedPatientId"
                @change="handlePatientSelect"
                @blur="handlePatientSearchBlur"
              />
              <span v-else class="register-basic-patient-readonly">{{
                registerForm.patientName || '—'
              }}</span>
              <span v-if="patientSelectError" class="register-patient-select-error">{{
                patientSelectError
              }}</span>
            </template>
          </BasicInfoForm>
        </section>

        <section class="form-section form-section--tabs">
          <ElTabs v-model="registerDetailTab" class="register-detail-tabs">
            <ElTabPane label="住院信息" name="inpatient">
              <div class="register-tab-panel">
                <div v-if="wardCapacityHint" class="register-tab-panel__hint">
                  {{ wardCapacityHint }}
                </div>
                <InpatientInfoForm>
                  <template #admissionDiagnosisSlot>
                    <AdmissionDiagnosisField
                      v-model="registerForm.admissionDiagnosis"
                      :western-options="westernAdmissionDiagnosisOptions"
                      :readonly="admissionDiagnosisReadonly || dialogReadOnly"
                      :disabled="dialogReadOnly"
                    />
                  </template>
                </InpatientInfoForm>
              </div>
            </ElTabPane>
            <ElTabPane label="待遇信息" name="benefit">
              <div
                v-loading="benefitPanelLoading"
                :element-loading-text="benefitLoadingText || '正在拉取人员与省市医保信息…'"
                class="register-tab-panel register-tab-panel--benefit"
              >
                <div
                  v-if="!benefitPanelLoading && (!benefitYbPreview || !isInsuranceContractSelected)"
                  class="register-benefit-empty"
                >
                  选择医保费用性质并读卡后，在此查看待遇检查、慢特病备案与费用累计
                </div>
                <RegisterBenefitPreviewPanel
                  v-else-if="benefitYbPreview && isInsuranceContractSelected"
                  :preview="benefitYbPreview"
                  :contract-id="registerForm.contractId"
                  :med-type="INPATIENT_MED_TYPE"
                  :card-no="lastYbCardInfo"
                  :yb-card-info="lastYbCardInfo"
                  :yb-card-sn="lastYbCardSn"
                  :yb-certno="lastYbCertno"
                  :yb-medium-type="lastYbMediumType"
                  :medium-type="lastYbMediumType"
                  :scrollable-tables="false"
                  :external-treatment-loading="treatmentSyncing"
                />
              </div>
            </ElTabPane>
          </ElTabs>
        </section>

        <footer v-if="dialogMode === 'switchInsurance'" class="register-footer">
          <div class="flex w-full justify-end gap-2">
            <ElButton
              :loading="printingCertificate"
              :disabled="!registerForm.patientName.trim()"
              @click="handlePrintAdmissionCertificate"
            >
              打印住院证
            </ElButton>
            <ElButton @click="requestCloseRegisterDialog">取消</ElButton>
            <ElButton
              type="primary"
              :loading="dialogSubmitting || benefitPanelLoading"
              @click="handleSwitchToInsurance"
            >
              确认转医保
            </ElButton>
          </div>
        </footer>
        <footer v-else-if="!dialogReadOnly" class="register-footer">
          <div class="register-footer__left">
            <div class="register-footer__payment">
              <span class="register-footer__label">预交金</span>
              <div class="register-footer__pay-rows">
                <div
                  v-for="(row, index) in registerForm.paymentDetailRows"
                  :key="`pay-row-${index}`"
                  class="register-footer__pay-row"
                >
                  <ElSelect v-model="row.payChannelCode" class="register-footer__channel" filterable>
                    <ElOption
                      v-for="item in payChannelOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </ElSelect>
                  <ElInputNumber
                    v-model="row.amount"
                    :precision="2"
                    :step="100"
                    :min="0"
                    controls-position="right"
                  />
                  <ElButton
                    link
                    type="danger"
                    :disabled="registerForm.paymentDetailRows.length <= 1"
                    @click="removePaymentDetailRow(index)"
                  >
                    删除
                  </ElButton>
                </div>
                <ElButton link type="primary" @click="addPaymentDetailRow">添加支付方式</ElButton>
              </div>
            </div>
          </div>
          <div class="flex gap-2">
            <ElButton
              :loading="printingCertificate"
              :disabled="!registerForm.patientName.trim()"
              @click="handlePrintAdmissionCertificate"
            >
              打印住院证
            </ElButton>
            <ElButton @click="requestCloseRegisterDialog">取消</ElButton>
            <ElButton
              type="primary"
              :loading="dialogSubmitting || benefitPanelLoading"
              @click="handleSubmitRegister"
            >
              登记
            </ElButton>
          </div>
        </footer>
        <footer v-else class="register-footer register-footer--readonly">
          <ElButton
            :loading="printingCertificate"
            :disabled="!registerForm.patientName.trim()"
            @click="handlePrintAdmissionCertificate"
          >
            打印住院证
          </ElButton>
          <ElButton
            v-if="switchToInsuranceEnabled"
            type="primary"
            @click="enterSwitchInsuranceFromView"
          >
            转医保
          </ElButton>
          <ElButton
            v-if="switchToSelfPayEnabled"
            type="primary"
            :loading="selfPaySwitching"
            @click="handleSwitchToSelfPay(dialogContext.encounterId)"
          >
            转自费
          </ElButton>
          <ElButton v-if="departmentChangeEnabled" type="primary" @click="openDepartmentChangeDialog">
            科室变更
          </ElButton>
          <ElButton @click="requestCloseRegisterDialog">关闭</ElButton>
        </footer>
      </div>
    </AppDialog>
    <AppDialog
      v-model="departmentChangeVisible"
      title="科室变更"
      width="520px"
      :close-on-click-modal="false"
    >
      <div class="department-change-form">
        <label class="department-change-form__row">
          <span>入院科室</span>
          <ElSelect
            :model-value="departmentChangeForm.organizationId"
            filterable
            placeholder="请选择住院科室"
            @update:model-value="onDepartmentChangeOrganization"
          >
            <ElOption
              v-for="item in organizationOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </label>
        <label class="department-change-form__row">
          <span>入院病区</span>
          <ElSelect
            :model-value="departmentChangeForm.wardId"
            filterable
            placeholder="请选择病区"
            @update:model-value="onDepartmentChangeWard"
          >
            <ElOption
              v-for="item in departmentChangeWards"
              :key="item.wardId"
              :label="formatDepartmentChangeWardLabel(item)"
              :value="item.wardId"
            />
          </ElSelect>
        </label>
        <label v-if="departmentChangeNeedsBed" class="department-change-form__row">
          <span>床位</span>
          <ElSelect v-model="departmentChangeForm.bedId" filterable placeholder="请选择床位">
            <ElOption
              v-for="item in departmentChangeBeds"
              :key="item.bedId"
              :label="formatDepartmentChangeBedLabel(item)"
              :value="item.bedId"
            />
          </ElSelect>
        </label>
      </div>
      <template #footer>
        <ElButton @click="departmentChangeVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="departmentChangeSubmitting" @click="submitDepartmentChange">
          确认变更
        </ElButton>
      </template>
    </AppDialog>
  </Page>
</template>

<style scoped>
.ip-register-fee {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: wrap;
  line-height: 1.4;
}

.register-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-section {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #fff;
}

.form-section__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: #f5f7fa;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 36px;
  padding: 6px 12px;
}

:deep(.register-grid-form .vxe-input) {
  width: 100%;
}

.department-change-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.department-change-form__row {
  display: grid;
  grid-template-columns: 72px 1fr;
  align-items: center;
  gap: 8px;
}

.register-basic-patient-readonly {
  display: inline-block;
  min-height: 32px;
  color: var(--el-text-color-primary);
  font-size: var(--el-font-size-base);
  line-height: 32px;
}

.register-patient-select-error {
  color: var(--el-color-danger);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.2;
}

.form-section__hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 400;
}

:deep(.register-grid-form) {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px 12px;
  padding: 12px;
}

:deep(.register-grid-form .el-form-item) {
  margin-bottom: 0;
  align-items: center;
}

:deep(.register-grid-form .el-form-item__label) {
  line-height: 32px;
  padding-right: 8px;
}

:deep(.register-grid-form .el-select),
:deep(.register-grid-form .el-date-editor),
:deep(.register-grid-form .el-input-number) {
  width: 100%;
}

:deep(.register-form-item--region) {
  grid-column: span 2;
}

:deep(.register-form-item--address-detail) {
  grid-column: span 2;
}

:deep(.register-form-item--work-company) {
  grid-column: span 2;
}

:deep(.register-form-item--admission-diagnosis) {
  grid-column: 1 / -1;
  align-items: flex-start;
}

:deep(.register-form-item--admission-diagnosis .el-form-item__label) {
  line-height: 32px;
  padding-top: 0;
}

:deep(.register-form-item--admission-diagnosis .el-form-item__content) {
  width: 100%;
  max-width: none;
}

:deep(.register-form-item--admission-diagnosis-readonly) {
  grid-column: 1 / -1;
  align-items: center;
}

@media (max-width: 960px) {
  :deep(.register-grid-form) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  :deep(.register-form-item--region),
  :deep(.register-form-item--address-detail),
  :deep(.register-form-item--work-company),
  :deep(.register-form-item--admission-diagnosis),
  :deep(.register-form-item--admission-diagnosis-readonly) {
    grid-column: 1 / -1;
  }
}

.register-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 6px;
}

.register-footer--readonly {
  justify-content: flex-end;
}

.register-footer__payment {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.register-footer__pay-rows {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.register-footer__pay-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.register-footer__channel {
  width: 140px;
}

.form-section__title--with-actions {
  justify-content: space-between;
}

.form-section__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-left: auto;
  font-weight: 400;
}

.form-section--tabs {
  overflow: hidden;
}

.register-detail-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 12px;
  background: #f5f7fa;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.register-detail-tabs :deep(.el-tabs__item) {
  height: 36px;
  line-height: 36px;
  font-size: 14px;
  font-weight: 600;
}

.register-detail-tabs :deep(.el-tabs__content) {
  padding: 0;
}

.register-tab-panel {
  padding: 12px;
}

.register-tab-panel--benefit {
  min-height: 160px;
}

.register-tab-panel__hint {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.register-benefit-empty {
  padding: 32px 12px;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  text-align: center;
}

.register-benefit-loading {
  padding: 8px 12px 0;
  color: var(--el-color-primary);
  font-size: 13px;
}

.register-benefit-panel-wrap {
  padding: 0 12px 12px;
}

.register-footer__left {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.register-footer__insurance {
  display: flex;
  align-items: center;
  gap: 8px;
}

.register-footer__label {
  color: var(--el-text-color-primary);
  font-weight: 500;
}
</style>
