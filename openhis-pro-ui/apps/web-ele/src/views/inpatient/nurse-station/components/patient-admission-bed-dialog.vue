<script setup lang="ts">
import type {
  AdmissionVitalSignSubmitDto,
  OptionDto,
  PatientDetailDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';

import type { PatientItem } from '../types';

import { computed, reactive, ref, watch } from 'vue';

import dayjs from 'dayjs';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
} from 'element-plus';

import {
  httpGetPatientDetail,
  httpLoadAdmissionParticipantInit,
  httpSavePatientInfo,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station.http';
import { httpExecuteBedOperation } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';
import AppDialog from '#/components/app-dialog.vue';
import { useRequest } from '#/hooks/use-request';
import { isStaleCrossRoleError } from '#/utils/http/stale-cross-role-error';
import { PATIENT_CONDITION_OPTIONS } from '#/views/inpatient/charge/register/inpatient-register.constants';

import type { AdmissionBedOperationIntent } from '../services/admission-bed-operation';
import {
  admissionBedConfirmLabel,
  admissionBedDialogTitle,
  buildExecuteBedOperationFromIntent,
} from '../services/admission-bed-operation';
import { bedOperationSuccessMessage } from '../services/execute-bed-operation';
import {
  buildParticipantSubmitRows,
  mapParticipantsToFormIds,
  mergeParticipantsIntoOptionLists,
} from '../services/map-patient-detail-participants';

defineOptions({
  name: 'InpatientNurseStationPatientAdmissionBedDialog',
});

interface Props {
  /** 列表行摘要（详情加载前展示） */
  patient: PatientItem | null;
  /** 拖放/选床后的待确认写操作；无则仅维护患者信息 */
  bedOperation: AdmissionBedOperationIntent | null;
  regionNameById: Record<string, string>;
  wardNameById: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), {
  patient: null,
  bedOperation: null,
  regionNameById: () => ({}),
  wardNameById: () => ({}),
});

const visible = defineModel<boolean>({ default: false });

const emit = defineEmits<{
  saved: [];
}>();

const detail = ref<PatientDetailDto | null>(null);
const doctorOptions = ref<OptionDto[]>([]);
const primaryNurseOptions = ref<OptionDto[]>([]);

const vitalForm = reactive({
  heightCm: '',
  weightKg: '',
  temperature: '',
  heartRate: '',
  pulse: '',
  bloodPressureHigh: '',
  bloodPressureLow: '',
});

const admissionForm = reactive({
  inpatientDoctorId: '',
  attendingDoctorId: '',
  chiefDoctorId: '',
  primaryNurseId: '',
  priorityEnum: '',
  admissionTimeDisplay: '',
});

const detailRequest = useRequest(httpGetPatientDetail, { manual: true });
const saveRequest = useRequest(httpSavePatientInfo, { manual: true });
const bedOperationRequest = useRequest(httpExecuteBedOperation, {
  manual: true,
  refreshOnStale: () => {
    emit('saved');
  },
});

const dialogTitle = computed(() => admissionBedDialogTitle(props.bedOperation));
const confirmButtonLabel = computed(() => admissionBedConfirmLabel(props.bedOperation));

const dialogLoading = computed(
  () =>
    detailRequest.loading.value || saveRequest.loading.value || bedOperationRequest.loading.value,
);

const encounterId = computed(() => props.patient?.id ?? '');

/** 患者姓名 */
const patientName = computed(
  () => (detail.value?.patientName ?? props.patient?.name ?? '—').trim() || '—',
);

/** 头像占位字（姓名首字） */
const patientAvatarText = computed(() => {
  const name = patientName.value;
  if (!name || name === '—') {
    return '?';
  }
  return name.slice(0, 1);
});

/** 性别展示 */
const genderDisplay = computed(
  () => (detail.value?.genderEnumText ?? props.patient?.gender ?? '—').trim() || '—',
);

/** 年龄展示 */
const ageDisplay = computed(() => {
  const ageText = (detail.value?.ageText ?? '').trim();
  if (ageText) {
    return ageText.includes('岁') ? ageText : `${ageText}岁`;
  }
  const age = props.patient?.age;
  return age != null && age > 0 ? `${age}岁` : '—';
});

/** 联系电话 */
const phoneDisplay = computed(() => (detail.value?.patientPhone ?? '').trim() || '—');

/** 住院号 */
const inpatientNoDisplay = computed(() => (detail.value?.encounterBusNo ?? '').trim() || '—');

/** 住院诊断 */
const diagnosisDisplay = computed(() => {
  const list = detail.value?.diagnosisList;
  if (list?.length) {
    return list.join('；');
  }
  return (detail.value?.diagnosisSummary ?? props.patient?.diagnosis ?? '—').trim() || '—';
});

/** 费用性质 */
const feeTypeLabel = computed(
  () => (detail.value?.organizationName ?? props.patient?.feeType ?? '—').trim() || '—',
);

/** 护理等级 */
const nursingLevelLabel = computed(() => (props.patient?.level ?? '').trim() || '');

/** 是否已分床 */
const hasAssignedBed = computed(() => {
  const bed = (detail.value?.bedName ?? props.patient?.bedNo ?? '').trim();
  return Boolean(bed);
});

/** 床位标签文案 */
const bedBadgeText = computed(() => {
  const bed = (detail.value?.bedName ?? props.patient?.bedNo ?? '').trim();
  if (!bed) {
    return '未分床';
  }
  const ward = (
    detail.value?.wardName ??
    props.regionNameById[props.patient?.regionId ?? ''] ??
    ''
  ).trim();
  return ward ? `${ward} · ${bed}` : bed;
});

/** 所在位置：病区 | 病房 | 床位 */
const locationDisplay = computed(() => {
  const op = props.bedOperation;
  if (op) {
    return `${op.targetRegionName} | ${op.targetHouseName} | ${op.targetBedName}`;
  }
  const ward =
    (detail.value?.wardName ?? props.regionNameById[props.patient?.regionId ?? ''] ?? '').trim() ||
    '—';
  const house =
    (detail.value?.houseName ?? props.wardNameById[props.patient?.wardId ?? ''] ?? '').trim() ||
    '—';
  const bed = (detail.value?.bedName ?? props.patient?.bedNo ?? '').trim() || '未分床';
  return `${ward} | ${house} | ${bed}`;
});

/** 互换时展示对手信息 */
const swapPartnerHint = computed(() => {
  const op = props.bedOperation;
  if (op?.type !== 'swap' || !op.swapPartnerName) {
    return '';
  }
  return `将与「${op.swapPartnerName}」（床位 ${op.targetBedName}）互换`;
});

/** 患者信息区展示字段 */
const patientInfoFields = computed(() => [
  { label: '联系电话', value: phoneDisplay.value },
  { label: '住院号', value: inpatientNoDisplay.value },
  { label: '住院诊断', value: diagnosisDisplay.value, span: 2 },
  { label: '所在位置', value: locationDisplay.value, span: 2 },
]);

/** 入院科室（病区）只读 */
const admissionDepartment = computed(() => {
  const op = props.bedOperation;
  if (op) {
    return op.targetRegionName;
  }
  return (
    (detail.value?.wardName ?? props.regionNameById[props.patient?.regionId ?? ''] ?? '—').trim() ||
    '—'
  );
});

/** 入院病区只读 */
const admissionWard = computed(() => {
  const op = props.bedOperation;
  if (op) {
    return op.targetHouseName;
  }
  return (
    (detail.value?.houseName ?? props.wardNameById[props.patient?.wardId ?? ''] ?? '—').trim() ||
    '—'
  );
});

/** 入住床位只读 */
const assignedBed = computed(() => {
  const op = props.bedOperation;
  if (op) {
    return op.targetBedName;
  }
  return (detail.value?.bedName ?? props.patient?.bedNo ?? '未分床').trim() || '未分床';
});

const priorityOptions = PATIENT_CONDITION_OPTIONS.map((item) => ({
  label: item.label,
  value: item.value,
}));

/** 重置体征与入院表单 */
function resetForms() {
  vitalForm.heightCm = '';
  vitalForm.weightKg = '';
  vitalForm.temperature = '';
  vitalForm.heartRate = '';
  vitalForm.pulse = '';
  vitalForm.bloodPressureHigh = '';
  vitalForm.bloodPressureLow = '';
  admissionForm.inpatientDoctorId = '';
  admissionForm.attendingDoctorId = '';
  admissionForm.chiefDoctorId = '';
  admissionForm.primaryNurseId = '';
  admissionForm.priorityEnum = '';
  admissionForm.admissionTimeDisplay = '';
}

/** 体征表单 → 提交块；全空时返回 undefined */
function buildAdmissionVitalSignsFromForm(): AdmissionVitalSignSubmitDto | undefined {
  const dto: AdmissionVitalSignSubmitDto = {
    heightCm: vitalForm.heightCm.trim() || undefined,
    weightKg: vitalForm.weightKg.trim() || undefined,
    temperature: vitalForm.temperature.trim() || undefined,
    heartRate: vitalForm.heartRate.trim() || undefined,
    pulse: vitalForm.pulse.trim() || undefined,
    bloodPressureHigh: vitalForm.bloodPressureHigh.trim() || undefined,
    bloodPressureLow: vitalForm.bloodPressureLow.trim() || undefined,
  };
  const hasAny = Object.values(dto).some((value) => Boolean(value));
  return hasAny ? dto : undefined;
}

/** 详情回填块 → 体征表单 */
function applyAdmissionVitalSignsToForm(vitals?: AdmissionVitalSignSubmitDto) {
  vitalForm.heightCm = (vitals?.heightCm ?? '').trim();
  vitalForm.weightKg = (vitals?.weightKg ?? '').trim();
  vitalForm.temperature = (vitals?.temperature ?? '').trim();
  vitalForm.heartRate = (vitals?.heartRate ?? '').trim();
  vitalForm.pulse = (vitals?.pulse ?? '').trim();
  vitalForm.bloodPressureHigh = (vitals?.bloodPressureHigh ?? '').trim();
  vitalForm.bloodPressureLow = (vitals?.bloodPressureLow ?? '').trim();
}

/** 关闭弹窗 */
function closeDialog() {
  visible.value = false;
}

/** 解析当前表单适用的入院病区位置 id */
function resolveWardLocationId(): string {
  const op = props.bedOperation;
  if (op?.targetWardLocationId) {
    return op.targetWardLocationId.trim();
  }
  const fromDetail = (detail.value?.wardId ?? '').trim();
  if (fromDetail) {
    return fromDetail;
  }
  return (props.patient?.regionId ?? '').trim();
}

/** 加载住院医师与责任护士下拉 */
async function loadParticipantOptions() {
  const orgId = detail.value?.organizationId;
  const wardLocationId = resolveWardLocationId();
  const res = await httpLoadAdmissionParticipantInit({
    ...(orgId ? { organizationId: String(orgId) } : {}),
    ...(wardLocationId ? { wardLocationId } : {}),
  });
  const merged = mergeParticipantsIntoOptionLists(
    res.doctorOptions ?? [],
    res.primaryNurseOptions ?? [],
    detail.value?.participants,
  );
  doctorOptions.value = merged.doctorOptions;
  primaryNurseOptions.value = merged.primaryNurseOptions;
}

/** 打开时拉取患者详情并回填表单 */
async function loadDetailAndForm() {
  const id = encounterId.value;
  if (!id) {
    return;
  }
  resetForms();
  detail.value = null;
  const res = await detailRequest.run({ encounterId: id });
  const row = res.detail;
  if (!row) {
    ElMessage.warning('未获取到患者详情');
    return;
  }
  detail.value = row;
  const participantIds = mapParticipantsToFormIds(row.participants);
  admissionForm.inpatientDoctorId = participantIds.inpatientDoctorId;
  admissionForm.attendingDoctorId = participantIds.attendingDoctorId;
  admissionForm.chiefDoctorId = participantIds.chiefDoctorId;
  admissionForm.primaryNurseId = participantIds.primaryNurseId;
  admissionForm.priorityEnum = (row.priorityEnum ?? '').trim();
  applyAdmissionVitalSignsToForm(row.admissionVitalSigns);
  const admissionRaw = row.admissionTime ?? row.startTime;
  if (admissionRaw) {
    const parsed = dayjs(admissionRaw);
    admissionForm.admissionTimeDisplay = parsed.isValid()
      ? parsed.format('YYYY-MM-DD HH:mm:ss')
      : String(admissionRaw);
  } else {
    admissionForm.admissionTimeDisplay = dayjs().format('YYYY-MM-DD HH:mm:ss');
  }
  await loadParticipantOptions();
}

/** 校验入院信息必填项 */
function validateAdmissionForm(): boolean {
  if (!admissionForm.inpatientDoctorId) {
    ElMessage.warning('请选择住院医生');
    return false;
  }
  if (!admissionForm.primaryNurseId) {
    ElMessage.warning('请选择责任护士');
    return false;
  }
  return true;
}

/** 确认：保存医护信息后按需提交分床/换床/互换 */
async function handleAdmit() {
  const id = encounterId.value;
  if (!id) {
    return;
  }
  if (!validateAdmissionForm()) {
    return;
  }
  const participants = buildParticipantSubmitRows({
    inpatientDoctorId: admissionForm.inpatientDoctorId,
    attendingDoctorId: admissionForm.attendingDoctorId,
    chiefDoctorId: admissionForm.chiefDoctorId,
    primaryNurseId: admissionForm.primaryNurseId,
  });
  const op = props.bedOperation;
  const vitalPayload = buildAdmissionVitalSignsFromForm();
  const assignWithBed = op?.type === 'assign';
  try {
    await saveRequest.run({
      encounterId: id,
      priorityEnum: admissionForm.priorityEnum || undefined,
      participants: participants.length > 0 ? participants : undefined,
      admissionVitalSigns: assignWithBed ? undefined : vitalPayload,
    });
    if (op) {
      const execBody = buildExecuteBedOperationFromIntent(
        op,
        id,
        participants,
        admissionForm.priorityEnum || undefined,
        assignWithBed ? vitalPayload : undefined,
      );
      await bedOperationRequest.run(execBody);
      ElMessage.success(
        bedOperationSuccessMessage(execBody.operationType, patientName.value, op.targetBedName),
      );
    } else {
      ElMessage.success('患者信息已保存');
    }
    emit('saved');
    closeDialog();
  } catch (error) {
    if (isStaleCrossRoleError(error)) {
      emit('saved');
      closeDialog();
    }
  }
}

watch(visible, (open) => {
  if (open) {
    void loadDetailAndForm();
  } else {
    detail.value = null;
    resetForms();
  }
});
</script>

<template>
  <AppDialog
    v-model="visible"
    :title="dialogTitle"
    width="920px"
    destroy-on-close
    :close-on-click-modal="false"
    class="patient-admission-bed-dialog"
  >
    <div v-loading="dialogLoading" class="admission-dialog">
      <section class="patient-info-card">
        <div class="patient-info-card__accent" aria-hidden="true"></div>
        <div class="patient-info-card__body">
          <div class="patient-info-card__hero">
            <div class="patient-info-card__avatar">{{ patientAvatarText }}</div>
            <div class="patient-info-card__identity">
              <div class="patient-info-card__name-row">
                <span class="patient-info-card__name">{{ patientName }}</span>
                <span class="patient-info-card__demographics">
                  {{ genderDisplay }} · {{ ageDisplay }}
                </span>
              </div>
              <div class="patient-info-card__tags">
                <ElTag size="small" :type="hasAssignedBed ? 'primary' : 'warning'" effect="light">
                  {{ bedBadgeText }}
                </ElTag>
                <ElTag size="small" type="info" effect="plain">{{ feeTypeLabel }}</ElTag>
                <ElTag v-if="nursingLevelLabel" size="small" type="success" effect="plain">
                  {{ nursingLevelLabel }}
                </ElTag>
              </div>
            </div>
          </div>
          <div v-if="swapPartnerHint" class="patient-info-card__swap-hint">
            {{ swapPartnerHint }}
          </div>
          <div class="patient-info-card__grid">
            <div
              v-for="item in patientInfoFields"
              :key="item.label"
              class="patient-info-card__field"
              :class="{ 'patient-info-card__field--span2': item.span === 2 }"
            >
              <span class="patient-info-card__label">{{ item.label }}</span>
              <span class="patient-info-card__value" :title="item.value">{{ item.value }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="admission-dialog__section">
        <div class="admission-dialog__section-title">入院体征</div>
        <ElForm label-width="72px" class="admission-dialog__form-grid">
          <div class="admission-dialog__row">
            <ElFormItem label="身高">
              <ElInput v-model="vitalForm.heightCm" placeholder="请输入">
                <template #suffix>cm</template>
              </ElInput>
            </ElFormItem>
            <ElFormItem label="体重">
              <ElInput v-model="vitalForm.weightKg" placeholder="请输入">
                <template #suffix>kg</template>
              </ElInput>
            </ElFormItem>
            <ElFormItem label="体温">
              <ElInput v-model="vitalForm.temperature" placeholder="请输入">
                <template #suffix>°C</template>
              </ElInput>
            </ElFormItem>
          </div>
          <div class="admission-dialog__row">
            <ElFormItem label="心率">
              <ElInput v-model="vitalForm.heartRate" placeholder="请输入">
                <template #suffix>BPM</template>
              </ElInput>
            </ElFormItem>
            <ElFormItem label="脉搏">
              <ElInput v-model="vitalForm.pulse" placeholder="请输入">
                <template #suffix>P</template>
              </ElInput>
            </ElFormItem>
            <ElFormItem label="血压">
              <div class="admission-dialog__bp">
                <ElInput v-model="vitalForm.bloodPressureHigh" placeholder="请输入" />
                <span class="admission-dialog__bp-sep">~</span>
                <ElInput v-model="vitalForm.bloodPressureLow" placeholder="请输入" />
                <span class="admission-dialog__bp-unit">mmHg</span>
              </div>
            </ElFormItem>
          </div>
        </ElForm>
      </section>

      <section class="admission-dialog__section">
        <div class="admission-dialog__section-title">入院信息</div>
        <ElForm label-width="88px" class="admission-dialog__form-grid">
          <div class="admission-dialog__row">
            <ElFormItem label="入院科室">
              <ElInput :model-value="admissionDepartment" disabled />
            </ElFormItem>
            <ElFormItem label="入院病区">
              <ElInput :model-value="admissionWard" disabled />
            </ElFormItem>
            <ElFormItem label="入住床位">
              <ElInput :model-value="assignedBed" disabled />
            </ElFormItem>
          </div>
          <div class="admission-dialog__row">
            <ElFormItem label="住院医生" required>
              <ElSelect
                v-model="admissionForm.inpatientDoctorId"
                filterable
                clearable
                placeholder="请选择住院医生"
                class="w-full"
              >
                <ElOption
                  v-for="opt in doctorOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="主治医生">
              <ElSelect
                v-model="admissionForm.attendingDoctorId"
                filterable
                clearable
                placeholder="请选择主治医生"
                class="w-full"
              >
                <ElOption
                  v-for="opt in doctorOptions"
                  :key="`att-${opt.value}`"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="主任医生">
              <ElSelect
                v-model="admissionForm.chiefDoctorId"
                filterable
                clearable
                placeholder="请选择主任医生"
                class="w-full"
              >
                <ElOption
                  v-for="opt in doctorOptions"
                  :key="`chief-${opt.value}`"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </ElFormItem>
          </div>
          <div class="admission-dialog__row">
            <ElFormItem label="入科时间">
              <ElInput v-model="admissionForm.admissionTimeDisplay" disabled />
            </ElFormItem>
            <ElFormItem label="责任护士" required>
              <ElSelect
                v-model="admissionForm.primaryNurseId"
                filterable
                clearable
                placeholder="请选择责任护士"
                class="w-full"
              >
                <ElOption
                  v-for="opt in primaryNurseOptions"
                  :key="`nurse-${opt.value}`"
                  :label="opt.label"
                  :value="String(opt.value)"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="患者病情">
              <ElSelect
                v-model="admissionForm.priorityEnum"
                clearable
                placeholder="请选择患者病情"
                class="w-full"
              >
                <ElOption
                  v-for="opt in priorityOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </ElSelect>
            </ElFormItem>
          </div>
        </ElForm>
      </section>
    </div>

    <template #footer>
      <div class="admission-dialog__footer">
        <div class="admission-dialog__footer-actions">
          <ElButton @click="closeDialog">取消</ElButton>
          <ElButton type="primary" :loading="dialogLoading" @click="handleAdmit">
            {{ confirmButtonLabel }}
          </ElButton>
        </div>
      </div>
    </template>
  </AppDialog>
</template>

<style scoped>
.patient-info-card {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  display: flex;
  margin-bottom: 16px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgb(15 23 42 / 6%);
}
.patient-info-card__accent {
  background: var(--el-color-primary);
  flex-shrink: 0;
  width: 4px;
}
.patient-info-card__body {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
}
.patient-info-card__hero {
  align-items: center;
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.patient-info-card__avatar {
  align-items: center;
  background: linear-gradient(145deg, var(--el-color-primary-light-3), var(--el-color-primary));
  border-radius: 50%;
  color: #fff;
  display: flex;
  flex-shrink: 0;
  font-size: 18px;
  font-weight: 600;
  height: 44px;
  justify-content: center;
  width: 44px;
}
.patient-info-card__identity {
  flex: 1;
  min-width: 0;
}
.patient-info-card__name-row {
  align-items: baseline;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}
.patient-info-card__name {
  color: var(--el-text-color-primary);
  font-size: 18px;
  font-weight: 600;
  line-height: 1.2;
}
.patient-info-card__demographics {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.patient-info-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.patient-info-card__swap-hint {
  background: var(--el-color-warning-light-9);
  border-radius: 6px;
  color: var(--el-color-warning-dark-2);
  font-size: 13px;
  line-height: 1.45;
  margin-bottom: 10px;
  padding: 8px 10px;
}
.patient-info-card__grid {
  border-top: 1px dashed var(--el-border-color-lighter);
  display: grid;
  gap: 10px 20px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding-top: 12px;
}
.patient-info-card__field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.patient-info-card__field--span2 {
  grid-column: span 2;
}
.patient-info-card__label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.2;
}
.patient-info-card__value {
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.patient-info-card__field--span2 .patient-info-card__value {
  white-space: normal;
}
.admission-dialog__section + .admission-dialog__section {
  margin-top: 8px;
}
.admission-dialog__section-title {
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  margin-bottom: 12px;
  padding-left: 8px;
}
.admission-dialog__row {
  display: grid;
  gap: 0 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.admission-dialog__bp {
  align-items: center;
  display: flex;
  gap: 6px;
  width: 100%;
}
.admission-dialog__bp-sep {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}
.admission-dialog__bp-unit {
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
  font-size: 12px;
}
.admission-dialog__footer {
  align-items: center;
  display: flex;
  justify-content: space-between;
  width: 100%;
}
.admission-dialog__footer-actions {
  display: flex;
  gap: 8px;
}
:deep(.patient-admission-bed-dialog .el-dialog__body) {
  padding-top: 8px;
}
</style>
