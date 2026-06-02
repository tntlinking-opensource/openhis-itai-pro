<script setup lang="ts">
import type { AdmissionDiagnosisSubmitDto } from '#/api/doctor-station/inpatient-admission.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { OutpatientInfoItemDto } from '#/api/doctor-station/encounter.types';
import type { OptionDto } from '#/api/inpatient-registration';

import { computed, reactive, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import dayjs from 'dayjs';

import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus';

import { wardBedInitApi } from '#/api/basic/ward-bed';
import {
  httpGetEncounterDiagnosis,
  httpGetConditionDefinitionMetadata,
} from '#/api/doctor-station/diagnosis.http';
import {
  httpCheckInpatientAdmissionExistsByAmbEncounter,
  httpRequestInpatientAdmission,
} from '#/api/doctor-station/inpatient-admission.http';
import { httpListWardsByDepartment } from '#/api/inpatient-registration';
import { useRequest } from '#/hooks/use-request';
import {
  ADMISSION_TYPE_OPTIONS,
  IN_WAY_OPTIONS,
  PATIENT_CONDITION_OPTIONS,
} from '#/views/inpatient/charge/register/inpatient-register.constants';
import {
  preserveApiIdForRequest,
  resolveLoginPractitionerIdForRequest,
} from '#/utils/preserve-api-id';

defineOptions({ name: 'DoctorStationInpatientAdmissionDialog' });

interface Props {
  modelValue: boolean;
  ambEncounterId?: string;
  patientId?: string;
  /** 当前门诊就诊行（用于患者信息展示） */
  patient?: OutpatientInfoItemDto | null;
}

const props = withDefaults(defineProps<Props>(), {
  ambEncounterId: '',
  patientId: '',
  patient: null,
});

const emit = defineEmits<{
  submitted: [];
  'update:modelValue': [value: boolean];
}>();

const userStore = useUserStore();

const organizationOptions = ref<OptionDto[]>([]);
const wardOptions = ref<Array<{ label: string; value: string }>>([]);
const diagnosisOptions = ref<OptionDto[]>([]);
/** 门诊主诊预填行（提交时可带 conditionId） */
const prefilledDiagnosisRow = ref<EncounterDiagnosisRowDto | null>(null);

const existingLine = reactive({
  exists: false,
  inpatientEncounterId: '' as string | undefined,
  statusEnum: '' as string | undefined,
});

const form = reactive({
  targetDepartmentId: '',
  wardId: '',
  conditionDefinitionId: '',
  diagnosisDescription: '',
  admitSourceCode: 'OUTPATIENT',
  inWayCode: '',
  priorityCode: '',
});

const checkRequest = useRequest(httpCheckInpatientAdmissionExistsByAmbEncounter, { manual: true });
const submitRequest = useRequest(httpRequestInpatientAdmission, { manual: true });

const dialogLoading = computed(() => checkRequest.loading.value || submitRequest.loading.value);

/** 已存在住院线时禁止再次申请 */
const submitBlocked = computed(() => existingLine.exists);

const existingStatusHint = computed(() => {
  if (!existingLine.exists) return '';
  const status = existingLine.statusEnum?.trim() || '—';
  if (status === 'PLANNED') {
    return '该就诊已提交住院申请（待登记），请至住院收费窗口办理登记。';
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
  form.conditionDefinitionId = '';
  form.diagnosisDescription = '';
  form.admitSourceCode = 'OUTPATIENT';
  form.inWayCode = '';
  form.priorityCode = '';
  prefilledDiagnosisRow.value = null;
  existingLine.exists = false;
  existingLine.inpatientEncounterId = undefined;
  existingLine.statusEnum = undefined;
  wardOptions.value = [];
}

/** 加载住院科室下拉 */
async function loadOrganizationOptions() {
  const init = await wardBedInitApi();
  organizationOptions.value = (init.organizationOptions ?? []).map((item) => ({
    label: item.label,
    value: String(item.value),
  }));
}

/** 加载入院诊断目录 */
async function loadDiagnosisOptions() {
  const res = await httpGetConditionDefinitionMetadata({ pageNo: 1, pageSize: 500 });
  diagnosisOptions.value = (res.records ?? [])
    .filter((row) => row.conditionDefinitionId && row.conditionName)
    .map((row) => ({
      label: String(row.conditionName),
      value: String(row.conditionDefinitionId),
    }));
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

/** 从门诊就诊诊断预填主诊断 */
async function prefillDiagnosisFromAmbEncounter() {
  const encounterId = preserveApiIdForRequest(props.ambEncounterId);
  const patientId = preserveApiIdForRequest(props.patientId);
  if (!encounterId || !patientId) return;
  const res = await httpGetEncounterDiagnosis({ encounterId, patientId });
  const rows = res.diagnosisList ?? res.content ?? [];
  if (!rows.length) return;
  const primary =
    rows.find((row) => row.mainDiagnosisFlag === 1) ??
    rows.find((row) => String(row.mainDiagnosisFlag) === '1') ??
    rows[0];
  if (!primary) return;
  prefilledDiagnosisRow.value = primary;
  const defId = preserveApiIdForRequest(primary.conditionDefinitionId);
  if (defId) {
    form.conditionDefinitionId = defId;
  }
  const desc = primary.diagnosisDesc?.trim() || primary.conditionDefinitionName?.trim();
  if (desc) {
    form.diagnosisDescription = desc;
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
    await Promise.all([
      loadOrganizationOptions(),
      loadDiagnosisOptions(),
      loadExistingAdmissionLine(),
    ]);
    if (!existingLine.exists) {
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

/** 解析入院诊断说明 */
function resolveDiagnosisDescription(conditionDefinitionId: string): string {
  const custom = form.diagnosisDescription.trim();
  if (custom) return custom;
  const hit = diagnosisOptions.value.find((item) => item.value === conditionDefinitionId);
  return hit?.label ?? '';
}

/** 组装提交的入院诊断列表 */
function buildAdmissionDiagnoses(): AdmissionDiagnosisSubmitDto[] {
  const conditionDefinitionId = preserveApiIdForRequest(form.conditionDefinitionId);
  if (!conditionDefinitionId) return [];
  const prefilled = prefilledDiagnosisRow.value;
  const prefilledDefId = preserveApiIdForRequest(prefilled?.conditionDefinitionId);
  const conditionId =
    prefilledDefId === conditionDefinitionId
      ? preserveApiIdForRequest(prefilled?.conditionId)
      : undefined;
  const description = resolveDiagnosisDescription(conditionDefinitionId);
  return [
    {
      primaryFlag: true,
      conditionDefinitionId,
      ...(conditionId ? { conditionId } : {}),
      ...(description ? { description } : {}),
    },
  ];
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
  const conditionDefinitionId = preserveApiIdForRequest(form.conditionDefinitionId);
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
  if (!conditionDefinitionId) {
    ElMessage.warning('请选择入院主诊断');
    return;
  }
  const admissionDiagnoses = buildAdmissionDiagnoses();
  if (!admissionDiagnoses.length) {
    ElMessage.warning('入院诊断不能为空');
    return;
  }

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
    @open="onDialogOpen"
  >
    <div v-loading="dialogLoading" class="flex flex-col gap-3">
      <div
        v-if="patientSummaryRows.length"
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
        <ElFormItem label="入院主诊断" required>
          <ElSelect
            v-model="form.conditionDefinitionId"
            filterable
            placeholder="请选择入院主诊断"
            class="w-full"
            :disabled="submitBlocked"
          >
            <ElOption
              v-for="item in diagnosisOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="诊断说明">
          <ElInput
            v-model="form.diagnosisDescription"
            placeholder="可选"
            :disabled="submitBlocked"
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
              v-for="item in ADMISSION_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="入院方式">
          <ElSelect
            v-model="form.inWayCode"
            clearable
            placeholder="请选择"
            class="w-full"
            :disabled="submitBlocked"
          >
            <ElOption
              v-for="item in IN_WAY_OPTIONS"
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
              v-for="item in PATIENT_CONDITION_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </ElForm>
    </div>

    <template #footer>
      <ElButton @click="closeDialog">取消</ElButton>
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
