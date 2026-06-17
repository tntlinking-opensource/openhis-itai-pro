<script setup lang="ts">
import type { OptionDto, RegisterFormAreaExpose, RegisterPatientRecord } from '../services';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';

import { ElButton } from 'element-plus';

import { useVbenForm } from '#/adapter/form';
import SectionTitle from '#/components/layouts/section-title.vue';
import {
  collectFormFocusableElements,
  DEFAULT_FORM_KEYBOARD_SELECTORS,
  useFormKeyboardNavigation,
} from '#/hooks/use-form-keyboard-navigation';
import { calcAgeFromBirthDate, parseChineseIdCard18 } from '#/utils/id-card-cn';

import {
  listPractitioners,
  listBookableSlotHints,
  loadRegisterFormInit,
} from '../services/register.service';

defineOptions({
  name: 'RegisterFormArea',
});

/** 科室下拉：label 展示名，value 与主数据 id 一致 */
const departmentOptions = ref<OptionDto[]>([]);

/** IF-REG-05 拉取的可选医生 */
const doctorOptions = ref<OptionDto[]>([]);

/** 费用性质下拉 */
const contractOptions = ref<OptionDto[]>([]);

/** IF-REG-01 号源类型（OptionDto.value 即 serviceTypeId） */
const serviceTypeOptions = ref<OptionDto[]>([]);

/** 号源类型主键 → 挂号费销售价（元），来自初始化接口 */
const serviceTypeFeeById = ref<Record<string, number | string>>({});

/** 初始化接口下发的患者性别字典（Radio 选项） */
const genderOptions = ref<OptionDto[]>([]);

/** 预约带入后锁定科室/医生/号源类型 */
const bookingLocked = ref(false);

/** 当日可约时段摘要（无预约现场挂号提示） */
const slotHints = ref<string[]>([]);

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
  slotHints.value = [];
};

/**
 * 医师变更：收窄号源类型并刷新时段提示。
 */
async function handleDoctorChange(value: string | undefined) {
  if (bookingLocked.value) {
    return;
  }
  const values = await registerFormApi.getValues();
  const organizationId = String(values.departmentId ?? '').trim();
  await refreshServiceTypeOptions(organizationId || undefined, value || undefined);
  registerFormApi.setFieldValue('serviceTypeId', undefined);
  registerFeeAmount.value = 0;
  slotHints.value = [];
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
 * 身份证号输满 18 位时写入性别、出生日期、年龄（规则见 `#/utils/id-card-cn`）。
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
  await refreshDoctorOptions(value || undefined);
  await refreshServiceTypeOptions(value || undefined, undefined);
  slotHints.value = [];
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
  serviceTypeFeeById.value = { ...(init.serviceTypeFeeById ?? {}) };
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
 * 选齐科室/医师/号源后拉取可约时段摘要。
 */
async function refreshSlotHints() {
  if (bookingLocked.value) {
    slotHints.value = [];
    return;
  }
  const values = await registerFormApi.getValues();
  const organizationId = String(values.departmentId ?? '').trim();
  const practitionerId = String(values.doctorId ?? '').trim();
  const serviceTypeId = String(values.serviceTypeId ?? '').trim();
  if (!organizationId || !serviceTypeId) {
    slotHints.value = [];
    return;
  }
  try {
    const res = await listBookableSlotHints({
      healthcareServiceId: serviceTypeId,
      organizationId,
      practitionerId: practitionerId || undefined,
    });
    const hints = res.hints ?? [];
    slotHints.value = hints.slice(0, 6).map((item) => {
      const start = String(item.slotStart ?? '')
        .replace('T', ' ')
        .slice(0, 16);
      const end = String(item.slotEnd ?? '')
        .replace('T', ' ')
        .slice(11, 16);
      const remain =
        item.remainingCount == null || item.remainingCount === ''
          ? ''
          : ` 余${item.remainingCount}`;
      return `${start}${end ? `-${end}` : ''}${remain}`;
    });
    if (hints.length > 6) {
      slotHints.value.push(`…共 ${hints.length} 个时段可约`);
    }
  } catch {
    slotHints.value = [];
  }
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
  registerFormApi.setFieldValue(
    'doctorId',
    snapshot.practitionerId != null && String(snapshot.practitionerId) !== ''
      ? String(snapshot.practitionerId)
      : undefined,
  );
  registerFormApi.setFieldValue('serviceTypeId', String(snapshot.healthcareServiceId ?? ''));
  handleServiceTypeChange(String(snapshot.healthcareServiceId ?? ''));
  syncBookingFieldLockState();
}

/**
 * 解除预约锁定。
 */
function clearBookingLock() {
  bookingLocked.value = false;
  slotHints.value = [];
  syncBookingFieldLockState();
}

/**
 * 号源类型变更：按初始化下发的销售价映射刷新展示用挂号费。
 */
function handleServiceTypeChange(value: string | undefined) {
  if (!value) {
    registerFeeAmount.value = 0;
    slotHints.value = [];
    return;
  }
  const raw = serviceTypeFeeById.value[value];
  const fee = Number(raw);
  registerFeeAmount.value = Number.isFinite(fee) ? fee : 0;
  void refreshSlotHints();
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

/** 下方「就诊信息」只读展示区数据源（后续可接读卡/查询结果灌入） */
const patientInfo = ref({
  medicalRecordNo: '',
  cardNo: '',
  age: '',
  visitType: '',
  insuranceBalance: undefined as number | undefined,
  insuranceType: '',
  insuranceName: '',
  insuranceRegion: '',
  debtLimit: undefined as number | undefined,
  feeType: '',
  visitReason: '',
  priority: '',
  departmentName: '',
  registerTypeText: '',
  doctorName: '',
  specialDisease: '',
  registerFee: undefined as number | undefined,
  treatmentFee: undefined as number | undefined,
  totalAmount: undefined as number | undefined,
});

/** 生成空的就诊信息展示对象，用于清空 */
const emptyPatientInfo = () => ({
  medicalRecordNo: '',
  cardNo: '',
  age: '',
  visitType: '',
  insuranceBalance: undefined as number | undefined,
  insuranceType: '',
  insuranceName: '',
  insuranceRegion: '',
  debtLimit: undefined as number | undefined,
  feeType: '',
  visitReason: '',
  priority: '',
  departmentName: '',
  registerTypeText: '',
  doctorName: '',
  specialDisease: '',
  registerFee: undefined as number | undefined,
  treatmentFee: undefined as number | undefined,
  totalAmount: undefined as number | undefined,
});

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
  patientInfo.value = emptyPatientInfo();
  await refreshDoctorsAndResetFeeDisplay();
  syncBookingFieldLockState();
}

/**
 * 整页清空：表单与就诊信息展示区清空；医生下拉恢复为不按科室过滤的全量列表。
 */
function resetForClear() {
  bookingLocked.value = false;
  patientFormApi.resetForm();
  registerFormApi.resetForm();
  patientInfo.value = emptyPatientInfo();
  void refreshDoctorsAndResetFeeDisplay();
  syncBookingFieldLockState();
}

/**
 * 仅清空患者信息表单（患者下拉被清空时由父组件调用）。
 */
function clearPatientFields() {
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
  applyPatientRecord,
  clearBookingLock,
  clearPatientFields,
  focusFirstField,
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
    class="reg-bill-form-area flex flex-1 flex-col overflow-auto px-2 py-1"
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
        <div
          v-if="slotHints.length > 0"
          class="mt-1 rounded border border-dashed border-gray-200 px-2 py-1 text-xs text-gray-600"
        >
          当日可约：{{ slotHints.join(' · ') }}
        </div>
        <div class="ml-auto mt-2 flex w-1/2 items-baseline justify-end gap-2">
          <span class="shrink-0 text-base text-gray-500">挂号费用</span>
          <span class="text-xl font-semibold tabular-nums text-[var(--el-color-primary)]">
            ¥{{ registerFeeAmount.toFixed(2) }}
          </span>
        </div>
      </div>

      <SectionTitle class="mb-1" title="就诊信息" />
      <div class="border-t bg-[#f8fafc] px-2 pb-2 pt-2">
        <div class="grid grid-cols-3 gap-x-2 gap-y-1 text-sm">
          <div>
            <div class="mb-0.5 text-gray-500">病历号</div>
            <div class="font-medium">{{ patientInfo.medicalRecordNo || '-' }}</div>
          </div>
          <div>
            <div class="mb-0.5 text-gray-500">卡号</div>
            <div class="font-medium">{{ patientInfo.cardNo || '-' }}</div>
          </div>

          <div>
            <div class="mb-0.5 text-gray-500">年龄</div>
            <div class="font-medium">
              {{ patientInfo.age ? `${patientInfo.age}岁` : '-' }}
            </div>
          </div>
          <div>
            <div class="mb-0.5 text-gray-500">初复诊</div>
            <div class="font-medium">
              {{
                patientInfo.visitType === '1'
                  ? '初诊'
                  : patientInfo.visitType === '2'
                    ? '复诊'
                    : '-'
              }}
            </div>
          </div>

          <div>
            <div class="mb-0.5 text-gray-500">医保余额</div>
            <div class="font-medium text-orange-600">
              {{
                patientInfo.insuranceBalance !== undefined
                  ? `¥${patientInfo.insuranceBalance}`
                  : '-'
              }}
            </div>
          </div>

          <div>
            <div class="mb-0.5 text-gray-500">费用性质</div>
            <div class="font-medium">{{ patientInfo.feeType || '-' }}</div>
          </div>

          <div>
            <div class="mb-0.5 text-gray-500">就诊科室</div>
            <div class="font-medium">{{ patientInfo.departmentName || '-' }}</div>
          </div>
          <div>
            <div class="mb-0.5 text-gray-500">挂号类型</div>
            <div class="font-medium">{{ patientInfo.registerTypeText || '-' }}</div>
          </div>

          <div>
            <div class="mb-0.5 text-gray-500">医生</div>
            <div class="font-medium">{{ patientInfo.doctorName || '-' }}</div>
          </div>
        </div>
      </div>
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

.reg-bill-form-area :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}
</style>
