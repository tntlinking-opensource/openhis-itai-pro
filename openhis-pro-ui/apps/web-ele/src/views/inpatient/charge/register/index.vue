<script setup lang="ts">
import type { TableColumn } from '@vben-core/table-layout-ui';

import type {
  AdmissionDiagnosisSubmitDto,
  LoadRegistrationContextResult,
  OptionDto,
  RegistrationQueueItemDto,
} from '#/api/inpatient-registration';
import type { PatientItemDto } from '#/api/outpatient-registration';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { TableLayout } from '@vben-core/table-layout-ui';

import dayjs from 'dayjs';
import {
  ElButton,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElRadio,
  ElRadioGroup,
  ElTag,
} from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import { wardBedInitApi } from '#/api/basic/ward-bed';
import { httpGetConditionDefinitionMetadata } from '#/api/doctor-station/diagnosis.http';
import {
  httpCancelInpatientRegistration,
  httpListWardsByDepartment,
  httpLoadInpatientRegistrationContext,
  httpLoadWardBedCapacity,
  httpPageInpatientRegistrationQueue,
  httpSubmitInpatientRegistration,
} from '#/api/inpatient-registration';
import { httpPagePatientsForSelect } from '#/api/outpatient-registration';
import AppDialog from '#/components/app-dialog.vue';
import SelectTable from '#/components/select-table.vue';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

import RegisterListToolbar from './components/register-list-toolbar.vue';
import {
  ADMISSION_TYPE_OPTIONS,
  DEFAULT_DIAGNOSIS_CATEGORY,
  DEFAULT_PAY_CHANNEL_CODE,
  DIAGNOSIS_CATEGORY_OPTIONS,
  ENCOUNTER_STATUS,
  GENDER_OPTIONS,
  IN_WAY_OPTIONS,
  PATIENT_CONDITION_OPTIONS,
  PAY_CHANNEL_OPTIONS,
  REGISTRATION_LIST_STATUS,
} from './inpatient-register.constants';

defineOptions({
  name: 'ChargeIpRegister',
});

const userStore = useUserStore();

const registerDialogVisible = ref(false);
const dialogReadOnly = ref(false);
const dialogSubmitting = ref(false);
const tableData = ref<RegistrationQueueItemDto[]>([]);
const loading = ref(false);
const total = ref(0);
const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  idCard: '',
  registrationListStatus: REGISTRATION_LIST_STATUS.PLANNED,
});

const organizationOptions = ref<OptionDto[]>([]);
const contractOptions = ref<OptionDto[]>([]);
const admissionDiagnosisOptions = ref<OptionDto[]>([]);
/** 入院病区下拉（与入院科室同为 label/value 结构） */
const wardOptions = ref<OptionDto[]>([]);
const wardCapacityHint = ref('');
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

const registerForm = reactive({
  patientName: '',
  genderEnum: 'MALE',
  phone: '',
  age: undefined as number | undefined,
  idCard: '',
  birthDate: '',
  encounterBusNo: '',
  admissionCount: '',
  organizationId: '',
  wardId: '',
  bedCountDisplay: '',
  outpatientDoctor: '',
  diagnosisCategoryCode: DEFAULT_DIAGNOSIS_CATEGORY,
  conditionDefinitionId: '',
  diagnosisDescription: '',
  contractId: '',
  startTime: '',
  admitSourceCode: '',
  inWayCode: '',
  priorityEnum: '',
  advanceDepositAmount: 0,
  payChannelCode: DEFAULT_PAY_CHANNEL_CODE,
});

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

/** 无档登记时可选患者；有医生申请/只读时禁用下拉 */
const patientSelectDisabled = computed(
  () => dialogReadOnly.value || Boolean(preserveApiIdForRequest(dialogContext.encounterId)),
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

/** 清空患者下拉与基础信息中的患者字段 */
function clearPatientSelectState() {
  selectedPatientId.value = '';
  selectedPatientLabel.value = '';
  patientSelectError.value = '';
  patientRowById.value = {};
  dialogContext.patientId = undefined;
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
async function handlePatientSelect(value: string | string[]) {
  patientSelectError.value = '';
  const patientId = Array.isArray(value) ? value[0] : value;
  if (!patientId) {
    clearPatientSelectState();
    registerForm.genderEnum = 'MALE';
    registerForm.phone = '';
    registerForm.idCard = '';
    registerForm.birthDate = '';
    registerForm.age = undefined;
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
  await syncDialogFormsFromModel();
}

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
      void basicInfoFormApi.setFieldValue('age', undefined);
    }
    return;
  }
  registerForm.birthDate = birth;
  registerForm.age = dayjs().diff(dayjs(birth), 'year');
  void basicInfoFormApi.setFieldValue('birthDate', birth);
  void basicInfoFormApi.setFieldValue('age', registerForm.age);
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

const dialogTitle = computed(() => (dialogReadOnly.value ? '查看' : '登记'));

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
        fieldName: 'bedCountDisplay',
        label: '床位数',
        componentProps: { disabled: true, placeholder: '-' },
      },
      { component: 'Input', fieldName: 'outpatientDoctor', label: '门诊医生' },
      {
        component: 'Select',
        fieldName: 'diagnosisCategoryCode',
        label: '诊断类别',
        componentProps: {
          options: DIAGNOSIS_CATEGORY_OPTIONS.map((item) => ({
            label: item.label,
            value: item.value,
          })),
        },
      },
      {
        component: 'Select',
        fieldName: 'conditionDefinitionId',
        label: '入院诊断',
        required: true,
        componentProps: {
          filterable: true,
          options: [],
          placeholder: '请选择入院诊断',
        },
      },
      { component: 'Input', fieldName: 'diagnosisDescription', label: '诊断描述' },
      {
        component: 'Select',
        fieldName: 'priorityEnum',
        label: '患者病情',
        componentProps: {
          clearable: true,
          placeholder: '请选择',
          options: PATIENT_CONDITION_OPTIONS.map((item) => ({
            label: item.label,
            value: item.value,
          })),
        },
      },
      {
        component: 'Select',
        fieldName: 'admitSourceCode',
        label: '入院类型',
        componentProps: {
          clearable: true,
          placeholder: '请选择',
          options: ADMISSION_TYPE_OPTIONS.map((item) => ({ label: item.label, value: item.value })),
        },
      },
      {
        component: 'Select',
        fieldName: 'inWayCode',
        label: '入院方式',
        componentProps: {
          clearable: true,
          placeholder: '请选择',
          options: IN_WAY_OPTIONS.map((item) => ({ label: item.label, value: item.value })),
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
        component: 'DatePicker',
        fieldName: 'startTime',
        label: '入院日期',
        required: true,
        componentProps: {
          type: 'date',
          valueFormat: 'YYYY-MM-DD',
          placeholder: '请选择日期',
        },
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

/** 费用性质未选时默认取第一项（仅无档登记弹窗）。 */
function applyDefaultContractIdIfEmpty() {
  if (registerForm.contractId) return;
  const first = contractOptions.value[0];
  if (first?.value) {
    registerForm.contractId = String(first.value);
  }
}

/** 解析入院诊断说明：优先自定义描述，否则用目录项名称。 */
function resolveAdmissionDiagnosisDescription(
  conditionDefinitionId: string,
  customDescription: string,
): string {
  const custom = customDescription.trim();
  if (custom) return custom;
  const hit = admissionDiagnosisOptions.value.find(
    (item) => String(item.value) === conditionDefinitionId,
  );
  return hit?.label ?? '';
}

/** 组装登记提交的入院诊断行（须含 conditionDefinitionId 或 conditionId）。 */
function buildAdmissionDiagnosesForSubmit(): AdmissionDiagnosisSubmitDto[] | undefined {
  const conditionDefinitionId = preserveApiIdForRequest(registerForm.conditionDefinitionId);
  if (!conditionDefinitionId) return undefined;
  const description = resolveAdmissionDiagnosisDescription(
    conditionDefinitionId,
    registerForm.diagnosisDescription,
  );
  return [
    {
      primaryFlag: true,
      conditionDefinitionId,
      ...(description ? { description } : {}),
    },
  ];
}

/** 重置住院信息扩展字段为默认值 */
function resetInpatientInfoExtendedFields() {
  registerForm.admissionCount = '';
  registerForm.bedCountDisplay = '';
  registerForm.outpatientDoctor = '';
  registerForm.diagnosisCategoryCode = DEFAULT_DIAGNOSIS_CATEGORY;
  registerForm.conditionDefinitionId = '';
  registerForm.diagnosisDescription = '';
}

/** 根据出生日期推算年龄 */
function resolveAgeFromBirthDate(birthDate?: string) {
  if (!birthDate) return undefined;
  const parsed = dayjs(birthDate);
  if (!parsed.isValid()) return undefined;
  return dayjs().diff(parsed, 'year');
}

/** 入院日期提交前补齐时分秒 */
function normalizeStartTimeForSubmit(startTime: string) {
  const raw = startTime.trim();
  if (!raw) return '';
  if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
    return `${raw} 00:00:00`;
  }
  return raw;
}

/** 将登记上下文写入弹窗表单。 */
async function applyContextToDialog(context: LoadRegistrationContextResult) {
  dialogReadOnly.value = Boolean(context.readOnly);
  applyContractOptionsFromList(context.contractOptions);
  const patient = context.patient;
  const encounter = context.encounter;
  dialogContext.encounterId = preserveApiIdForRequest(encounter?.encounterId);
  dialogContext.patientId = preserveApiIdForRequest(patient?.patientId);
  syncPatientSelectDisplayFromContext(patient);
  registerForm.genderEnum = patient?.genderEnum ?? 'MALE';
  registerForm.phone = patient?.phone ?? '';
  registerForm.idCard = patient?.idCard ?? '';
  registerForm.birthDate = normalizeBirthDateForForm(patient?.birthDate);
  registerForm.age = resolveAgeFromBirthDate(registerForm.birthDate);
  if (registerForm.idCard && !registerForm.birthDate) {
    syncBirthAgeFromIdCard(registerForm.idCard);
  }
  registerForm.encounterBusNo = encounter?.encounterBusNo ?? '';
  registerForm.organizationId = preserveApiIdForRequest(encounter?.organizationId) ?? '';
  registerForm.wardId = preserveApiIdForRequest(encounter?.wardId) ?? '';
  registerForm.contractId = '';
  applyDefaultContractIdIfEmpty();
  registerForm.startTime = encounter?.startTime?.slice(0, 10) ?? dayjs().format('YYYY-MM-DD');
  registerForm.admitSourceCode = encounter?.admitSourceCode ?? '';
  registerForm.inWayCode = encounter?.inWayCode ?? '';
  registerForm.priorityEnum = encounter?.priorityEnum ?? '';
  resetInpatientInfoExtendedFields();
  registerForm.conditionDefinitionId =
    preserveApiIdForRequest(encounter?.primaryConditionDefinitionId) ?? '';
  registerForm.diagnosisDescription = encounter?.primaryConditionDefinitionName?.trim() ?? '';
  registerForm.admissionCount = '-';
  registerForm.bedCountDisplay = '-';
  registerForm.advanceDepositAmount = 0;
  registerForm.payChannelCode = DEFAULT_PAY_CHANNEL_CODE;
  await refreshContractOptionsSchema();
  await refreshAdmissionDiagnosisOptionsSchema();
  await refreshOrganizationOptionsSchema();
  await loadWardSelectOptions();
  await syncDialogFormsFromModel();
  await applyWardIdEcho(registerForm.wardId);
  await setDialogFormsDisabled(dialogReadOnly.value);
  await setAdmissionDiagnosisFieldsReadonly(
    Boolean(encounter?.encounterId) && !dialogReadOnly.value,
  );
}

/** 重置无档登记空白弹窗。 */
async function resetDialogForNoRecord() {
  dialogReadOnly.value = false;
  dialogContext.encounterId = undefined;
  dialogContext.patientId = undefined;
  clearPatientSelectState();
  registerForm.genderEnum = 'MALE';
  registerForm.phone = '';
  registerForm.idCard = '';
  registerForm.birthDate = '';
  registerForm.age = undefined;
  registerForm.encounterBusNo = '';
  registerForm.organizationId = '';
  registerForm.wardId = '';
  registerForm.contractId = '';
  applyDefaultContractIdIfEmpty();
  registerForm.startTime = dayjs().format('YYYY-MM-DD');
  registerForm.admitSourceCode = '';
  registerForm.inWayCode = '';
  registerForm.priorityEnum = '';
  resetInpatientInfoExtendedFields();
  registerForm.admissionCount = '-';
  registerForm.bedCountDisplay = '-';
  registerForm.advanceDepositAmount = 0;
  registerForm.payChannelCode = DEFAULT_PAY_CHANNEL_CODE;
  wardCapacityHint.value = '';
  await refreshContractOptionsSchema();
  await refreshAdmissionDiagnosisOptionsSchema();
  await loadWardSelectOptions();
  await syncDialogFormsFromModel();
  await setDialogFormsDisabled(false);
  await setAdmissionDiagnosisFieldsReadonly(false);
}

/** 有医生申请时入院诊断只读（登记不重写主诊） */
async function setAdmissionDiagnosisFieldsReadonly(readonly: boolean) {
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'conditionDefinitionId',
      rules: readonly ? null : 'selectRequired',
      componentProps: {
        disabled: readonly,
        filterable: true,
        options: admissionDiagnosisOptions.value,
        placeholder: readonly ? '由医生申请带入' : '请选择入院诊断',
      },
    },
    {
      fieldName: 'diagnosisDescription',
      componentProps: {
        disabled: readonly,
        placeholder: readonly ? '由医生申请带入' : undefined,
      },
    },
  ]);
}

/** 打开弹窗时把当前 model 值写入 vben 表单。 */
async function syncDialogFormsFromModel() {
  await basicInfoFormApi.setValues({
    patientName: registerForm.patientName,
    genderEnum: registerForm.genderEnum,
    phone: registerForm.phone,
    age: registerForm.age,
    idCard: registerForm.idCard,
    birthDate: registerForm.birthDate,
  });
  await inpatientInfoFormApi.setValues({
    encounterBusNo: registerForm.encounterBusNo || '-',
    admissionCount: registerForm.admissionCount || '-',
    organizationId: registerForm.organizationId,
    wardId: registerForm.wardId,
    bedCountDisplay: registerForm.bedCountDisplay || '-',
    outpatientDoctor: registerForm.outpatientDoctor,
    diagnosisCategoryCode: registerForm.diagnosisCategoryCode,
    conditionDefinitionId: registerForm.conditionDefinitionId,
    diagnosisDescription: registerForm.diagnosisDescription,
    contractId: registerForm.contractId,
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
  registerForm.organizationId = String(inpatientInfoValues.organizationId ?? '');
  registerForm.wardId = String(inpatientInfoValues.wardId ?? '');
  registerForm.outpatientDoctor = String(inpatientInfoValues.outpatientDoctor ?? '');
  registerForm.diagnosisCategoryCode = String(
    inpatientInfoValues.diagnosisCategoryCode ?? DEFAULT_DIAGNOSIS_CATEGORY,
  );
  registerForm.conditionDefinitionId = String(inpatientInfoValues.conditionDefinitionId ?? '');
  registerForm.diagnosisDescription = String(inpatientInfoValues.diagnosisDescription ?? '');
  registerForm.contractId = String(inpatientInfoValues.contractId ?? '');
  registerForm.startTime = String(inpatientInfoValues.startTime ?? '');
  registerForm.admitSourceCode = String(inpatientInfoValues.admitSourceCode ?? '');
  registerForm.inWayCode = String(inpatientInfoValues.inWayCode ?? '');
  registerForm.priorityEnum = String(inpatientInfoValues.priorityEnum ?? '');
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

/** 加载入院诊断目录（诊断定义元数据分页）。 */
async function loadAdmissionDiagnosisOptions() {
  try {
    const res = await httpGetConditionDefinitionMetadata({
      pageNo: 1,
      pageSize: 500,
    });
    admissionDiagnosisOptions.value = (res.records ?? [])
      .filter((row) => row.conditionDefinitionId && row.conditionName)
      .map((row) => ({
        label: String(row.conditionName),
        value: String(row.conditionDefinitionId),
      }));
    await refreshAdmissionDiagnosisOptionsSchema();
  } catch {
    admissionDiagnosisOptions.value = [];
    await refreshAdmissionDiagnosisOptionsSchema();
  }
}

/** 住院登记页初始化：列表、主数据字典、登记弹窗下拉。 */
async function loadRegistrationPageInit() {
  await Promise.all([
    loadOrganizationOptions(),
    loadRegistrationFormBootstrap(),
    loadAdmissionDiagnosisOptions(),
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
        : { type: 'date', valueFormat: 'YYYY-MM-DD HH:mm:ss', placeholder: '请选择出生日期' },
    },
    {
      fieldName: 'age',
      componentProps: disabled
        ? { disabled: true, min: 0, max: 150, controls: false }
        : { min: 0, max: 150, controls: false, placeholder: '请输入年龄' },
    },
    {
      fieldName: 'genderEnum',
      componentProps: { ...disabledProps, options: genderSelectOptions },
    },
    {
      fieldName: 'phone',
      componentProps: disabledProps,
    },
  ]);
  const inpatientEditableFields = [
    'organizationId',
    'wardId',
    'outpatientDoctor',
    'diagnosisCategoryCode',
    'conditionDefinitionId',
    'diagnosisDescription',
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
    ...inpatientEditableFields
      .filter((fieldName) => fieldName !== 'organizationId' && fieldName !== 'wardId')
      .map((fieldName) => ({ fieldName, componentProps: disabledProps })),
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

/** 刷新入院诊断目录下拉 schema。 */
async function refreshAdmissionDiagnosisOptionsSchema() {
  await inpatientInfoFormApi.updateSchema([
    {
      fieldName: 'conditionDefinitionId',
      componentProps: {
        filterable: true,
        options: admissionDiagnosisOptions.value,
        placeholder: '请选择入院诊断',
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

/** 科室变更后清空病区并按科室加载病区列表。 */
async function handleOrganizationChange(organizationId: string) {
  registerForm.organizationId = organizationId;
  registerForm.wardId = '';
  registerForm.contractId = '';
  wardCapacityHint.value = '';
  registerForm.bedCountDisplay = '-';
  await reloadContractOptionsByOrganization(organizationId);
  applyDefaultContractIdIfEmpty();
  await loadWardSelectOptions();
  await applyWardIdEcho('');
  await inpatientInfoFormApi.setFieldValue('contractId', registerForm.contractId);
}

/** 病区字段变更：同步 model 并查询床位余量（勿在 componentProps.onChange 里处理，避免与 Select 双向绑定冲突）。 */
async function onWardIdFieldChange(selectedWardId: string) {
  const normalized = preserveApiIdForRequest(selectedWardId) ?? '';
  registerForm.wardId = normalized;
  wardCapacityHint.value = '';
  registerForm.bedCountDisplay = '-';
  await inpatientInfoFormApi.setFieldValue('bedCountDisplay', '-');
  if (!normalized) {
    return;
  }
  try {
    const capacity = await httpLoadWardBedCapacity({ wardId: normalized });
    const vacant = Number(capacity.vacantBedCount ?? 0);
    const total = Number(capacity.totalBedCount ?? 0);
    if (Number.isFinite(vacant) && Number.isFinite(total)) {
      wardCapacityHint.value = `空床 ${vacant} / 总床 ${total}`;
      registerForm.bedCountDisplay = String(vacant);
      await inpatientInfoFormApi.setFieldValue('bedCountDisplay', registerForm.bedCountDisplay);
    }
  } catch {
    void 0;
  }
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
    registerForm.bedCountDisplay = '-';
    await inpatientInfoFormApi.setFieldValue('bedCountDisplay', '-');
  }
}

/** 按筛选条件与分页刷新列表数据。 */
async function handleQuery() {
  loading.value = true;
  try {
    const result = await httpPageInpatientRegistrationQueue({
      keyword: queryParams.keyword.trim() || undefined,
      idCard: queryParams.idCard.trim() || undefined,
      registrationListStatus: queryParams.registrationListStatus || undefined,
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
    });
    tableData.value = result.content ?? [];
    total.value = Number(result.totalCount ?? 0);
  } finally {
    loading.value = false;
  }
}

/** 加载住院科室下拉（病区主数据初始化，后端登记服务未提供科室列表）。 */
async function loadOrganizationOptions() {
  const init = await wardBedInitApi();
  organizationOptions.value = (init.organizationOptions ?? []).map((item) => ({
    label: item.label,
    value: String(item.value),
  }));
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
  await applyContextToDialog(context);
}

/** 打开查看弹窗（只读）。 */
async function handleOpenView(row: RegistrationQueueItemDto) {
  await handleOpenRegister(row);
}

/** 无档登记：打开空白弹窗。 */
async function handleOpenNoRecordRegister() {
  registerDialogVisible.value = true;
  await nextTick();
  await nextTick();
  await resetDialogForNoRecord();
}

/** 关闭登记弹窗。 */
function handleCloseRegisterDialog() {
  registerDialogVisible.value = false;
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
  return inpatientResult.valid;
}

/** 提交住院登记。 */
async function handleSubmitRegister() {
  if (dialogReadOnly.value) return;
  syncBirthAgeFromIdCard(registerForm.idCard);
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
    ElMessage.warning('请完善入院科室、入院病区、费用性质和入院日期');
    return;
  }
  dialogSubmitting.value = true;
  try {
    const advanceAmount = Number(registerForm.advanceDepositAmount ?? 0);
    const body = {
      encounterId: dialogContext.encounterId,
      patientId: dialogContext.patientId,
      patientName: registerForm.patientName,
      genderEnum: registerForm.genderEnum,
      idCard: registerForm.idCard || undefined,
      birthDate: registerForm.birthDate || undefined,
      phone: registerForm.phone || undefined,
      organizationId,
      wardId,
      contractId,
      startTime: normalizedStartTime,
      admitSourceCode: registerForm.admitSourceCode || undefined,
      inWayCode: registerForm.inWayCode || undefined,
      priorityEnum: registerForm.priorityEnum || undefined,
      admissionDiagnoses: hasDoctorAdmission ? undefined : buildAdmissionDiagnosesForSubmit(),
      operatorId,
      advanceDepositAmount: advanceAmount > 0 ? advanceAmount : undefined,
      paymentDetails:
        advanceAmount > 0
          ? [
              {
                payChannelCode: registerForm.payChannelCode,
                amount: advanceAmount,
              },
            ]
          : undefined,
    };
    const result = await httpSubmitInpatientRegistration(body);
    ElMessage.success(`登记成功，住院号 ${result.encounterBusNo}`);
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

/** 取消住院登记。 */
async function handleCancelRegistration(row: RegistrationQueueItemDto) {
  const encounterId = preserveApiIdForRequest(row.encounterId);
  if (!encounterId) return;
  const operatorId = resolveOperatorId();
  if (!operatorId) {
    ElMessage.warning('无法获取当前操作人，请重新登录');
    return;
  }
  try {
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
    await handleQuery();
  } catch {
    // 用户关闭弹窗时不提示
  }
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

/** 是否展示「查看」按钮。 */
function canView(row: RegistrationQueueItemDto) {
  return row.statusEnum === ENCOUNTER_STATUS.IN_PROGRESS;
}

onMounted(() => {
  void loadRegistrationPageInit();
});
</script>

<template>
  <Page>
    <TableLayout
      :table-data="tableData"
      :loading="loading"
      :total="total"
      :query-params="queryParams"
      :table-columns="tableColumns"
      :show-pagination="true"
      :show-top-query="false"
      border
      stripe
      @pagination="handlePagination"
    >
      <template #toolbar-actions>
        <RegisterListToolbar
          v-model:keyword="queryParams.keyword"
          v-model:id-card="queryParams.idCard"
          v-model:registration-list-status="queryParams.registrationListStatus"
          @search="handleSearch"
          @no-record="handleOpenNoRecordRegister"
        />
      </template>
      <template #status="{ row }">
        <ElTag :type="getStatusTagType(row)" effect="light">{{ row.statusEnumText }}</ElTag>
      </template>
      <template #actions="{ row }">
        <div class="flex items-center justify-center gap-2">
          <ElButton v-if="canRegister(row)" link type="primary" @click="handleOpenRegister(row)">
            登记
          </ElButton>
          <ElButton v-if="canView(row)" link type="primary" @click="handleOpenView(row)">
            查看
          </ElButton>
          <ElButton v-if="row.cancelable" link type="danger" @click="handleCancelRegistration(row)">
            取消
          </ElButton>
        </div>
      </template>
    </TableLayout>

    <AppDialog
      v-model="registerDialogVisible"
      :title="dialogTitle"
      width="1200px"
      top="3vh"
      @closed="handleCloseRegisterDialog"
    >
      <div class="register-dialog-body">
        <section class="form-section">
          <header class="form-section__title">基础信息</header>
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

        <section class="form-section">
          <header class="form-section__title">
            住院信息
            <span v-if="wardCapacityHint" class="form-section__hint">{{ wardCapacityHint }}</span>
          </header>
          <InpatientInfoForm />
        </section>

        <footer v-if="!dialogReadOnly" class="register-footer">
          <div class="register-footer__payment">
            <span class="register-footer__label">预交金</span>
            <ElInputNumber
              v-model="registerForm.advanceDepositAmount"
              :precision="2"
              :step="100"
              :min="0"
              controls-position="right"
            />
            <ElRadioGroup v-model="registerForm.payChannelCode">
              <ElRadio v-for="item in PAY_CHANNEL_OPTIONS" :key="item.value" :label="item.value">
                {{ item.label }}
              </ElRadio>
            </ElRadioGroup>
          </div>
          <div class="flex gap-2">
            <ElButton @click="handleCloseRegisterDialog">取消</ElButton>
            <ElButton type="primary" :loading="dialogSubmitting" @click="handleSubmitRegister">
              登记
            </ElButton>
          </div>
        </footer>
        <footer v-else class="register-footer register-footer--readonly">
          <ElButton @click="handleCloseRegisterDialog">关闭</ElButton>
        </footer>
      </div>
    </AppDialog>
  </Page>
</template>

<style scoped>
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
  gap: 8px 10px;
  padding: 12px;
}

:deep(.register-grid-form .el-form-item) {
  margin-bottom: 0;
}

:deep(.register-grid-form .el-select),
:deep(.register-grid-form .el-date-editor),
:deep(.register-grid-form .el-input-number) {
  width: 100%;
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
  align-items: center;
  gap: 10px;
}

.register-footer__label {
  color: var(--el-text-color-primary);
  font-weight: 500;
}
</style>
