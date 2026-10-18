<script setup lang="ts">
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { OutpatientInfoItemDto } from '#/api/doctor-station/encounter.types';
import type { OptionDto } from '#/api/inpatient-registration';

import { computed, reactive, ref, watch } from 'vue';

import { useUserStore } from '@zxplat/stores';

import dayjs from 'dayjs';
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import { httpGetEncounterDiagnosis } from '#/api/doctor-station/diagnosis.http';
import {
  httpCheckInpatientAdmissionExistsByAmbEncounter,
  httpRequestInpatientAdmission,
  httpWithdrawInpatientAdmission,
} from '#/api/doctor-station/inpatient-admission.http';
import {
  httpListWardsByDepartment,
  httpLoadInpatientRegistrationContext,
  type RegistrationEncounterDto,
} from '#/api/inpatient-registration';
import AdmissionDiagnosisField from '#/components/admission/admission-diagnosis-field.vue';
import { createEmptyAdmissionDiagnosisFormState } from '#/components/admission/admission-diagnosis.types';
import {
  buildAdmissionDiagnosesFromFormState,
  validateAdmissionDiagnosisFormState,
} from '#/components/admission/services/build-admission-diagnoses';
import {
  mapEncounterDiagnosisToAdmissionFormState,
  pickPrimaryEncounterDiagnosisForAdmission,
} from '#/components/admission/services/map-admission-diagnosis-form';
import { useRequest } from '#/hooks/use-request';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/format/preserve-api-id';
import {
  printAdmissionCertificate,
  resolveAdmissionCertificateDiagnosisText,
  resolveAdmissionCertificateGenderText,
} from '#/utils/print';
import {
  DEFAULT_ADM_COND_CODE_GENERAL,
  DEFAULT_ADMIT_SOURCE_CODE_ROUTINE,
  DEFAULT_IN_WAY_CODE_OUTPATIENT_TRANSFER,
} from '#/views/inpatient/charge/register/inpatient-register.constants';
import { normalizeTerminologyOptions } from '#/utils/terminology/resolve-terminology-option-label';
import { normalizeAdmCondTermCode } from '#/utils/terminology/resolve-adm-cond-code-label';

defineOptions({ name: 'DoctorStationInpatientAdmissionDialog' });

const props = withDefaults(defineProps<Props>(), {
  ambEncounterId: '',
  patientId: '',
  patient: null,
});

const emit = defineEmits<{
  submitted: [];
  'update:modelValue': [value: boolean];
}>();

interface Props {
  modelValue: boolean;
  ambEncounterId?: string;
  patientId?: string;
  /** 当前门诊就诊行（用于患者信息展示） */
  patient?: null | OutpatientInfoItemDto;
}

const userStore = useUserStore();

const organizationOptions = ref<OptionDto[]>([]);
const wardOptions = ref<Array<{ label: string; value: string }>>([]);
const inWayCodeOptions = ref<OptionDto[]>([]);
const admitSourceCodeOptions = ref<OptionDto[]>([]);
const admCondCodeOptions = ref<OptionDto[]>([]);
const westernDiagnosisOptions = ref<OptionDto[]>([]);
/** 门诊主诊预填行（仅带目录 ID 比对；住院线须新建诊断，不提交门诊 encounterDiagnosisId） */
const prefilledDiagnosisRow = ref<EncounterDiagnosisRowDto | null>(null);

const existingLine = reactive({
  exists: false,
  inpatientEncounterId: '' as string | undefined,
  inpatientEncounterBusNo: '' as string | undefined,
  statusEnum: '' as string | undefined,
});

const printingCertificate = ref(false);

const form = reactive({
  targetDepartmentId: '',
  wardId: '',
  admitSourceCode: DEFAULT_ADMIT_SOURCE_CODE_ROUTINE,
  inWayCode: DEFAULT_IN_WAY_CODE_OUTPATIENT_TRANSFER,
  priorityCode: DEFAULT_ADM_COND_CODE_GENERAL,
  /**
   * 费用性质意向：INSURANCE=医保 / SELF_PAY=自费。
   * 仅提示款台；真实费用性质由登记处选择合同。
   */
  chargeIntent: '' as '' | 'INSURANCE' | 'SELF_PAY',
  /** 意向预交金总额；医生只填总额，支付方式由登记处填写 */
  advanceDepositAmount: undefined as number | undefined,
  admissionDiagnosis: createEmptyAdmissionDiagnosisFormState(),
});

const checkRequest = useRequest(httpCheckInpatientAdmissionExistsByAmbEncounter, { manual: true });
const submitRequest = useRequest(httpRequestInpatientAdmission, { manual: true });
const withdrawRequest = useRequest(httpWithdrawInpatientAdmission, { manual: true });

const dialogLoading = computed(
  () =>
    checkRequest.loading.value ||
    submitRequest.loading.value ||
    withdrawRequest.loading.value,
);

/** 已存在住院线时禁止再次申请 */
const submitBlocked = computed(() => existingLine.exists);

/** 仅待登记（PLANNED）允许医生撤回，撤回后表单可再编辑提交 */
const canWithdraw = computed(
  () => existingLine.exists && existingLine.statusEnum?.trim() === 'PLANNED',
);

const existingStatusHint = computed(() => {
  if (!existingLine.exists) return '';
  const status = existingLine.statusEnum?.trim() || '—';
  if (status === 'PLANNED') {
    return '该就诊已提交住院申请（待登记）。可撤回后修改再提交，或至住院收费窗口办理登记。';
  }
  return `该就诊已有住院记录（状态：${status}），不可重复申请。`;
});

/** 展示用：空值占位 */
function displayText(value: null | string | undefined): string {
  const s = value === null || value === undefined ? '' : String(value).trim();
  return s || '—';
}

/** 患者摘要行（姓名、性别年龄、电话等） */
const patientSummaryRows = computed(() => {
  const p = props.patient;
  if (!p) return [];
  const genderAge = [displayText(p.genderEnumText), displayText(p.ageText)]
    .filter((item) => item !== '—')
    .join(' / ');
  return [
    { label: '姓名', value: displayText(p.patientName) },
    { label: '性别/年龄', value: genderAge || '—' },
    { label: '联系电话', value: displayText(p.phone) },
    { label: '证件号码', value: displayText(p.idCard) },
    { label: '门诊号', value: displayText(p.encounterBusNo) },
    { label: '就诊科室', value: displayText(p.organizationName) },
    { label: '号别', value: displayText(p.serviceTypeName) },
    { label: '就诊状态', value: displayText(p.statusEnumText) },
    {
      label: '接诊时间',
      value: formatEncounterStartTime(p.startTime),
    },
    { label: '接诊医师', value: displayText(p.doctorName) },
  ];
});

/** 格式化接诊开始时间 */
function formatEncounterStartTime(raw: null | string | undefined): string {
  if (!raw?.trim()) return '—';
  const parsed = dayjs(raw);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : raw.trim();
}

/**
 * 解析提交用意向预交金总额：仅正数有效，未填或 ≤0 不传。
 *
 * @param raw 表单金额
 * @returns 正数金额，或 undefined（不落草稿）
 */
function resolveAdvanceDepositAmountForSubmit(
  raw: number | undefined,
): number | undefined {
  // 未填或非正数：不传字段，后端不落意向草稿
  if (raw == null || !Number.isFinite(raw) || raw <= 0) {
    return undefined;
  }
  return raw;
}

/** 关闭弹窗 */
function closeDialog() {
  emit('update:modelValue', false);
}

function onDialogModelValue(value: unknown) {
  emit('update:modelValue', Boolean(value));
}

/** 重置表单与防重状态 */
function resetFormState() {
  form.targetDepartmentId = '';
  form.wardId = '';
  form.admitSourceCode = DEFAULT_ADMIT_SOURCE_CODE_ROUTINE;
  form.inWayCode = DEFAULT_IN_WAY_CODE_OUTPATIENT_TRANSFER;
  form.priorityCode = DEFAULT_ADM_COND_CODE_GENERAL;
  form.chargeIntent = '';
  form.advanceDepositAmount = undefined;
  form.admissionDiagnosis = createEmptyAdmissionDiagnosisFormState();
  prefilledDiagnosisRow.value = null;
  existingLine.exists = false;
  existingLine.inpatientEncounterId = undefined;
  existingLine.inpatientEncounterBusNo = undefined;
  existingLine.statusEnum = undefined;
  wardOptions.value = [];
  westernDiagnosisOptions.value = [];
}

/** 入院科室展示名 */
function resolveDepartmentLabel(organizationId: string): string {
  const id = organizationId.trim();
  if (!id) return '';
  return organizationOptions.value.find((item) => item.value === id)?.label?.trim() || '';
}

/** 病情优先级展示名 */
function resolvePriorityLabel(priorityCode: string): string {
  const code = priorityCode.trim();
  if (!code) return '';
  return admCondCodeOptions.value.find((item) => String(item.value) === code)?.label?.trim() || '';
}

/** 费用性质意向展示名（医保/自费） */
function resolveChargeIntentLabel(chargeIntent: string): string {
  const code = chargeIntent.trim();
  if (code === 'INSURANCE') return '医保';
  if (code === 'SELF_PAY') return '自费';
  return '';
}

/** 组装并打印住院证 */
async function handlePrintAdmissionCertificate() {
  const patient = props.patient;
  const patientName = patient?.patientName?.trim() || '';
  if (!patientName) {
    ElMessage.warning('缺少患者姓名，无法打印住院证');
    return;
  }
  printingCertificate.value = true;
  try {
    const ageText = patient?.ageText?.trim() || '';
    // 住院证费用性质：表单「医保/自费」；未选时回落门诊合同展示
    const paymentMethodText =
      resolveChargeIntentLabel(form.chargeIntent) || patient?.contractDisplay?.trim() || '';
    if (!paymentMethodText) {
      ElMessage.warning('请先选择费用性质（医保/自费）再打印');
      return;
    }
    await printAdmissionCertificate({
      encounterId: existingLine.inpatientEncounterId,
      source: {
        departmentName: resolveDepartmentLabel(form.targetDepartmentId),
        inpatientNo: existingLine.inpatientEncounterBusNo?.trim() || '',
        patientName,
        genderText: resolveAdmissionCertificateGenderText(
          patient?.genderEnumText || patient?.genderEnum,
        ),
        ageText,
        address: patient?.address?.trim() || '',
        phone: patient?.phone?.trim() || '',
        diagnosisText: resolveAdmissionCertificateDiagnosisText(
          form.admissionDiagnosis,
          westernDiagnosisOptions.value,
        ),
        conditionText: resolvePriorityLabel(form.priorityCode),
        paymentMethodText,
        // 预交金：与弹窗「预交金」同值（可选；未填则模板金额为空）
        prepayAmount: form.advanceDepositAmount,
      },
    });
  } finally {
    printingCertificate.value = false;
  }
}

/** 加载住院科室与入院方式/类型术语下拉 */
async function loadOrganizationOptions() {
  const context = await httpLoadInpatientRegistrationContext({});
  organizationOptions.value = (context.organizationOptions ?? []).map((item) => ({
    label: item.label,
    value: String(item.value),
  }));
  inWayCodeOptions.value = (context.inWayCodeOptions ?? []).map((item) => ({
    label: item.label,
    value: String(item.value),
  }));
  admitSourceCodeOptions.value = (context.admitSourceCodeOptions ?? []).map((item) => ({
    label: item.label,
    value: String(item.value),
  }));
  admCondCodeOptions.value = normalizeTerminologyOptions(context.admCondCodeOptions);
}

/** 按入院科室刷新入院病区 */
async function loadWardOptions(organizationId: string) {
  const orgId = preserveApiIdForRequest(organizationId);
  if (!orgId) {
    wardOptions.value = [];
    return;
  }
  const result = await httpListWardsByDepartment({ organizationId: orgId });
  wardOptions.value = (result.wards ?? [])
    .map((item) => {
      const value = preserveApiIdForRequest(item.wardId);
      const label = (item.wardName ?? '').trim();
      if (!value || !label) {
        return null;
      }
      return { label, value };
    })
    .filter((item): item is { label: string; value: string } => item !== null);
}

/** 补全科室下拉项（上下文中的科室可能不在 init 列表） */
function ensureOrganizationOption(organizationId: string, organizationName: string) {
  const value = organizationId.trim();
  const label = organizationName.trim();
  if (!value || !label) return;
  if (organizationOptions.value.some((item) => item.value === value)) return;
  organizationOptions.value = [{ label, value }, ...organizationOptions.value];
}

/** 补全病区下拉项 */
function ensureWardOption(wardId: string, wardName: string) {
  const value = wardId.trim();
  const label = wardName.trim();
  if (!value || !label) return;
  if (wardOptions.value.some((item) => item.value === value)) return;
  wardOptions.value = [{ label, value }, ...wardOptions.value];
}

/** 将住院登记上下文中的就诊信息写入表单（已申请只读回显） */
async function applyExistingInpatientEncounterToForm(encounter: RegistrationEncounterDto) {
  const orgId = preserveApiIdForRequest(encounter.organizationId);
  const orgName = encounter.organizationName?.trim() ?? '';
  if (orgId) {
    ensureOrganizationOption(orgId, orgName);
    form.targetDepartmentId = orgId;
    await loadWardOptions(orgId);
  }
  const wardId = preserveApiIdForRequest(encounter.wardId);
  const wardName = encounter.wardName?.trim() ?? '';
  if (wardId) {
    ensureWardOption(wardId, wardName);
    form.wardId = wardId;
  }
  form.admitSourceCode = encounter.admitSourceCode?.trim() || DEFAULT_ADMIT_SOURCE_CODE_ROUTINE;
  form.inWayCode = encounter.inWayCode?.trim() || DEFAULT_IN_WAY_CODE_OUTPATIENT_TRANSFER;
  form.priorityCode =
    normalizeAdmCondTermCode(encounter.priorityEnum) || DEFAULT_ADM_COND_CODE_GENERAL;
  // 办理意向回显（登记上下文 encounter.chargeIntent）
  const intent = String(encounter.chargeIntent ?? '').trim();
  if (intent === 'INSURANCE' || intent === 'SELF_PAY') {
    form.chargeIntent = intent;
  }
  // 意向预交金 DRAFT 总额回显（登记上下文 encounter.advanceDepositAmount）
  const draftAdvance = Number(encounter.advanceDepositAmount ?? NaN);
  form.advanceDepositAmount =
    Number.isFinite(draftAdvance) && draftAdvance > 0 ? draftAdvance : undefined;
  form.admissionDiagnosis = {
    ...createEmptyAdmissionDiagnosisFormState(),
    readonlyDisplay:
      encounter.primaryConditionDefinitionName?.trim() ||
      encounter.primaryConditionDefinitionId?.trim() ||
      '',
  };
  const busNo = encounter.encounterBusNo?.trim();
  if (busNo) {
    existingLine.inpatientEncounterBusNo = busNo;
  }
}

/** 已提交住院申请：从住院线登记上下文回显科室/病区/诊断等 */
async function loadExistingAdmissionContext() {
  const encounterId = preserveApiIdForRequest(existingLine.inpatientEncounterId);
  if (!encounterId) return;
  const context = await httpLoadInpatientRegistrationContext({ encounterId });
  if (!context.encounter) return;
  await applyExistingInpatientEncounterToForm(context.encounter);
}

/** 门诊主诊预填后，补全西医下拉项（目录分页可能未含该 ID） */
function ensureWesternDiagnosisOption(definitionId: string, label: string) {
  const value = definitionId.trim();
  const name = label.trim();
  if (!value || !name) return;
  if (westernDiagnosisOptions.value.some((item) => String(item.value) === value)) return;
  westernDiagnosisOptions.value = [{ label: name, value }, ...westernDiagnosisOptions.value];
}

/** 从门诊就诊诊断预填主诊断（目录预填；不复用门诊诊断行主键） */
async function prefillDiagnosisFromAmbEncounter() {
  const encounterId = preserveApiIdForRequest(props.ambEncounterId);
  const patientId = preserveApiIdForRequest(props.patientId);
  if (!encounterId || !patientId) return;
  const res = await httpGetEncounterDiagnosis({ encounterId, patientId });
  const rows = res.diagnosisList ?? res.content ?? [];
  const primary = pickPrimaryEncounterDiagnosisForAdmission(rows);
  if (!primary) return;
  prefilledDiagnosisRow.value = primary;
  form.admissionDiagnosis = mapEncounterDiagnosisToAdmissionFormState(primary, rows);
  // 住院申请 INSERT 新 IMP 线，入院诊断须在住院就诊新建，禁止携带门诊行主键
  form.admissionDiagnosis.encounterDiagnosisId = undefined;
  if (form.admissionDiagnosis.medicineType === 'western') {
    ensureWesternDiagnosisOption(
      form.admissionDiagnosis.westernDefinitionId,
      primary.conditionDefinitionName ?? '',
    );
  }
}

/** 查询当前门诊是否已有住院线 */
async function loadExistingAdmissionLine() {
  const ambEncounterId = preserveApiIdForRequest(props.ambEncounterId);
  if (!ambEncounterId) return;
  const res = await checkRequest.run({ ambEncounterId });
  existingLine.exists = Boolean(res.exists);
  existingLine.inpatientEncounterId = preserveApiIdForRequest(res.inpatientEncounterId);
  existingLine.statusEnum = res.statusEnum?.trim() || undefined;
}

/** 弹窗打开：拉主数据、防重、预填诊断 */
async function onDialogOpen() {
  resetFormState();
  try {
    await Promise.all([loadOrganizationOptions(), loadExistingAdmissionLine()]);
    if (existingLine.exists) {
      await loadExistingAdmissionContext();
    } else {
      await prefillDiagnosisFromAmbEncounter();
    }
  } catch {
    void 0;
  }
}

/** 科室变更：清空病区并重新加载 */
async function handleDepartmentChange(organizationId: string) {
  form.targetDepartmentId = organizationId;
  form.wardId = '';
  await loadWardOptions(organizationId);
}

/** 组装提交的入院诊断列表（仅目录定义；不传门诊就诊诊断行主键） */
function buildAdmissionDiagnoses() {
  const prefilled = prefilledDiagnosisRow.value;
  return buildAdmissionDiagnosesFromFormState(form.admissionDiagnosis, {
    prefilledWesternDefinitionId: preserveApiIdForRequest(prefilled?.conditionDefinitionId),
  });
}

/**
 * 撤回待登记住院申请：取消住院线后解除只读，保留表单内容供直接编辑再提交。
 */
async function handleWithdraw() {
  if (!canWithdraw.value) {
    ElMessage.warning('仅待登记的住院申请可撤回');
    return;
  }
  const ambEncounterId = preserveApiIdForRequest(props.ambEncounterId);
  const operatorId = preserveApiIdForRequest(userStore.userInfo?.id);
  if (!ambEncounterId) {
    ElMessage.warning('缺少门诊就诊标识');
    return;
  }
  if (!operatorId) {
    ElMessage.warning('无法获取当前操作人，请重新登录');
    return;
  }
  const res = await withdrawRequest.run({ ambEncounterId, operatorId });
  // 撤回失败：保持只读，避免误提交
  if (!res.success) {
    ElMessage.error('撤回住院申请失败');
    return;
  }
  // 解除防重锁：款台待登记队列随 CANCELLED 不可见；表单科室/病区/意向等保留供编辑
  existingLine.exists = false;
  existingLine.inpatientEncounterId = undefined;
  existingLine.inpatientEncounterBusNo = undefined;
  existingLine.statusEnum = undefined;
  // 只读回显诊断无目录 ID，撤回后从门诊主诊重新预填，便于直接改后提交
  try {
    await prefillDiagnosisFromAmbEncounter();
  } catch {
    form.admissionDiagnosis = createEmptyAdmissionDiagnosisFormState();
  }
  ElMessage.success('已撤回住院申请，可修改后重新提交');
  emit('submitted');
}

/** 提交住院申请 */
async function handleSubmit() {
  if (submitBlocked.value) {
    ElMessage.warning(existingStatusHint.value || '当前就诊不可重复办理住院');
    return;
  }
  const ambEncounterId = preserveApiIdForRequest(props.ambEncounterId);
  const targetDepartmentId = preserveApiIdForRequest(form.targetDepartmentId);
  const requestDoctorId = resolveLoginPractitionerIdForRequest(userStore.userInfo);
  const operatorId = preserveApiIdForRequest(userStore.userInfo?.id);
  if (!ambEncounterId) {
    ElMessage.warning('缺少门诊就诊标识');
    return;
  }
  if (!targetDepartmentId) {
    ElMessage.warning('请选择入院科室');
    return;
  }
  if (!requestDoctorId) {
    ElMessage.warning('无法获取当前医师信息，请重新登录');
    return;
  }
  if (!operatorId) {
    ElMessage.warning('无法获取当前操作人，请重新登录');
    return;
  }
  const diagnosisError = validateAdmissionDiagnosisFormState(form.admissionDiagnosis);
  if (diagnosisError) {
    ElMessage.warning(diagnosisError);
    return;
  }
  const admissionDiagnoses = buildAdmissionDiagnoses();
  if (admissionDiagnoses.length === 0) {
    ElMessage.warning('入院诊断不能为空');
    return;
  }
  // 费用性质意向：必选，仅提示款台医保/自费
  if (!form.chargeIntent) {
    ElMessage.warning('请选择费用性质（医保/自费）');
    return;
  }
  // 意向预交金：仅提交正数总额；支付方式由住院登记处填写
  const advanceDepositAmount = resolveAdvanceDepositAmountForSubmit(form.advanceDepositAmount);

  const patientId = preserveApiIdForRequest(props.patientId);
  const wardId = preserveApiIdForRequest(form.wardId);
  const res = await submitRequest.run({
    ambEncounterId,
    ...(patientId ? { patientId } : {}),
    targetDepartmentId,
    ...(wardId ? { wardId } : {}),
    requestDoctorId,
    operatorId,
    admissionDiagnoses,
    admitSourceCode: form.admitSourceCode || undefined,
    inWayCode: form.inWayCode || undefined,
    priorityCode: form.priorityCode || undefined,
    chargeIntent: form.chargeIntent,
    ...(advanceDepositAmount != null ? { advanceDepositAmount } : {}),
  });
  if (res.success) {
    const busNo = res.inpatientEncounterBusNo?.trim();
    ElMessage.success(busNo ? `住院申请已提交，住院号 ${busNo}` : '住院申请已提交');
    emit('submitted');
    closeDialog();
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      void onDialogOpen();
    }
  },
);
</script>

<template>
  <ElDialog
    :model-value="modelValue"
    width="640px"
    title="办理住院"
    append-to-body
    destroy-on-close
    @update:model-value="onDialogModelValue"
  >
    <div v-loading="dialogLoading" class="flex flex-col gap-3">
      <div
        v-if="patientSummaryRows.length > 0"
        class="patient-summary rounded border border-[var(--el-border-color-lighter)] bg-[var(--el-fill-color-light)] px-3 py-2"
      >
        <div class="mb-2 text-sm font-medium text-[var(--el-text-color-primary)]">患者信息</div>
        <div class="patient-summary-grid text-sm text-[var(--el-text-color-regular)]">
          <div
            v-for="item in patientSummaryRows"
            :key="item.label"
            class="patient-summary-item flex gap-1"
          >
            <span class="shrink-0 text-[var(--el-text-color-secondary)]">{{ item.label }}：</span>
            <span class="min-w-0 break-all text-[var(--el-text-color-primary)]">{{
              item.value
            }}</span>
          </div>
        </div>
      </div>
      <div v-else class="text-sm text-[var(--el-text-color-placeholder)]">未选择患者</div>

      <ElAlert
        v-if="existingLine.exists"
        type="warning"
        :closable="false"
        show-icon
        :title="existingStatusHint"
      />

      <ElForm label-width="100px" class="admission-form">
        <ElFormItem label="入院科室" required>
          <ElSelect
            v-model="form.targetDepartmentId"
            filterable
            clearable
            placeholder="请选择入院科室"
            class="w-full"
            :disabled="submitBlocked"
            @change="handleDepartmentChange"
          >
            <ElOption
              v-for="item in organizationOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="入院病区">
          <ElSelect
            v-model="form.wardId"
            filterable
            clearable
            placeholder="请先选择入院科室"
            class="w-full"
            :disabled="submitBlocked || !form.targetDepartmentId"
          >
            <ElOption
              v-for="item in wardOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="入院主诊断" required class="admission-form__diagnosis-item">
          <AdmissionDiagnosisField
            v-model="form.admissionDiagnosis"
            :western-options="westernDiagnosisOptions"
            :disabled="submitBlocked"
            :readonly="submitBlocked"
          />
        </ElFormItem>
        <ElFormItem label="入院类型">
          <ElSelect
            v-model="form.admitSourceCode"
            clearable
            placeholder="请选择"
            class="w-full"
            :disabled="submitBlocked"
          >
            <ElOption
              v-for="item in admitSourceCodeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="入院途径">
          <ElSelect
            v-model="form.inWayCode"
            clearable
            placeholder="请选择"
            class="w-full"
            :disabled="submitBlocked"
          >
            <ElOption
              v-for="item in inWayCodeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="病情优先级">
          <ElSelect
            v-model="form.priorityCode"
            clearable
            placeholder="请选择"
            class="w-full"
            :disabled="submitBlocked"
          >
            <ElOption
              v-for="item in admCondCodeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="费用性质" required>
          <ElRadioGroup v-model="form.chargeIntent" :disabled="submitBlocked">
            <ElRadio value="INSURANCE">医保</ElRadio>
            <ElRadio value="SELF_PAY">自费</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="预交金">
          <ElInputNumber
            v-model="form.advanceDepositAmount"
            :precision="2"
            :step="100"
            :min="0"
            controls-position="right"
            class="w-full"
            :disabled="submitBlocked"
            placeholder="可选，仅填总额"
          />
          <div class="mt-1 text-xs text-[var(--el-text-color-secondary)]">
            仅填总额；支付方式由住院收费窗口登记时确认
          </div>
        </ElFormItem>
      </ElForm>
    </div>

    <template #footer>
      <ElButton
        :loading="printingCertificate"
        :disabled="dialogLoading || !patient?.patientName"
        @click="handlePrintAdmissionCertificate"
      >
        打印住院证
      </ElButton>
      <ElButton @click="closeDialog">取消</ElButton>
      <ElButton
        v-if="canWithdraw"
        type="warning"
        :loading="withdrawRequest.loading.value"
        :disabled="dialogLoading"
        @click="handleWithdraw"
      >
        撤回申请
      </ElButton>
      <ElButton
        type="primary"
        :loading="dialogLoading"
        :disabled="submitBlocked"
        @click="handleSubmit"
      >
        提交申请
      </ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.admission-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.admission-form__diagnosis-item :deep(.el-form-item__content) {
  line-height: normal;
}

.patient-summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
}

@media (max-width: 520px) {
  .patient-summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
