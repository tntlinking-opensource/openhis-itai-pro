<script setup lang="ts">
import type {
  ChargeItemPreviewDto,
  PatientItemDto,
  PreRegisterSettlementResult,
  RegisterConfirmDetail,
  RegisterFormAreaExpose,
} from '../services';

import { ref } from 'vue';

import { ElButton, ElCard, ElMessage } from 'element-plus';

import SelectTable from '#/components/select-table.vue';

import { pageRegisterPatientsForSelect, preRegisterSettlement } from '../services/register.service';
import RegisterConfirmDialog from './register-confirm-dialog.vue';
import RegisterFormArea from './register-form-area.vue';

defineOptions({
  name: 'RegisterLeftPanel',
});

const emit = defineEmits<{
  /** 挂号费结算成功：由页面刷新右侧就诊记录 */
  registerSuccess: [];
}>();

const registerFormAreaRef = ref<null | RegisterFormAreaExpose>(null);
const confirmDialogRef = ref<InstanceType<typeof RegisterConfirmDialog> | null>(null);

const selectedPatientId = ref('');
const selectedPatientLabel = ref('');
const patientSelectColumns = [
  { prop: 'patientBusNo', label: '病历号', width: 120 },
  { prop: 'name', label: '姓名', width: 100 },
  { prop: 'genderText', label: '性别', width: 72 },
  { prop: 'birthDate', label: '出生日期', width: 112 },
  { prop: 'age', label: '年龄', width: 64 },
  { prop: 'phoneMasked', label: '联系电话', width: 140 },
  { prop: 'idCardNoMasked', label: '身份证号', width: 190 },
];

/** 患者 id → 下拉行；仅选 id 时从这里取姓名等回填表单 */
const patientRowById = ref<Record<string, PatientItemDto>>({});

const loading = ref(false);

/**
 * 按关键字与分页拉取患者列表，供头部患者下拉表格展示。
 */
const queryPatientOptions = async ({
  pageNo = 1,
  pageSize = 20,
  keyword = '',
}: {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
}) => {
  const { content, totalElements } = await pageRegisterPatientsForSelect({
    pageNo,
    pageSize,
    keyword,
  });
  const next = { ...patientRowById.value };
  for (const row of content) {
    next[row.patientId] = row;
  }
  patientRowById.value = next;
  return {
    content,
    totalElements,
  };
};

/**
 * 处理患者下拉变更：清空选择时同步表单；选中时回填患者信息并聚焦到表单。
 */
const handlePatientSelect = async (value: string | string[]) => {
  const patientId = Array.isArray(value) ? value[0] : value;
  const area = registerFormAreaRef.value;
  if (!patientId) {
    selectedPatientId.value = '';
    selectedPatientLabel.value = '';
    area?.clearPatientFields();
    return;
  }
  selectedPatientId.value = patientId;
  const selectedPatient = patientRowById.value[patientId];
  if (!selectedPatient) return;
  selectedPatientLabel.value = `${selectedPatient.name}（${selectedPatient.patientBusNo}）`;
  const p = selectedPatient;
  const patientAge =
    p.age === undefined || p.age === null || String(p.age).trim() === '' ? '' : String(p.age);
  area?.applyPatientRecord({
    patientAge,
    patientBirthday: String(p.birthDate ?? ''),
    patientGender: String(p.genderCode ?? ''),
    patientIdCard: String(p.idCardNoMasked ?? ''),
    patientName: p.name,
    patientPhone: String(p.phoneMasked ?? ''),
  });
  await area?.focusFirstField();
};

/**
 * 一键清空：挂号表单、患者下拉及患者行缓存。
 */
const handleClear = () => {
  registerFormAreaRef.value?.resetForClear();
  selectedPatientId.value = '';
  selectedPatientLabel.value = '';
  patientRowById.value = {};
};

/**
 * 正式结算成功后：重置表单与患者选择，并通知外层刷新就诊记录。
 */
async function onRegisterPaidSuccess() {
  await registerFormAreaRef.value?.resetForSubmitSuccess();
  selectedPatientId.value = '';
  selectedPatientLabel.value = '';
  emit('registerSuccess');
}

/**
 * 提交挂号：预结算成功后打开核对弹窗，用户在弹窗内确认后再正式结算。
 */
const handleSubmit = async () => {
  const area = registerFormAreaRef.value;
  if (!area) {
    return;
  }
  const valid = await area.validateForSubmit();
  if (!valid) {
    ElMessage.warning('请填写完整的挂号信息');
    return;
  }

  const values = await area.getSubmitValues();
  const deptLabel = area.getDepartmentLabel(values.departmentId) ?? '';
  const doctorLabel = area.getDoctorLabel(values.doctorId) ?? '';
  const contractLabel = area.getContractLabel(values.contractId) ?? '';
  const serviceTypeLabel = area.getServiceTypeLabel(values.serviceTypeId) ?? '';
  const submitPatientId = selectedPatientId.value || undefined;
  const patientName = String(values.patientName ?? '');
  const patientGender = String(values.patientGender ?? '');
  const patientIdCard = String(values.patientIdCard ?? '');
  const patientPhone = String(values.patientPhone ?? '');
  const patientBirthday = String(values.patientBirthday ?? '');
  const contractId = String(values.contractId ?? '');
  const organizationId = String(values.departmentId ?? '');
  const serviceTypeId = String(values.serviceTypeId ?? '');
  const practitionerId = String(values.doctorId ?? '');

  loading.value = true;
  try {
    const pre: PreRegisterSettlementResult = await preRegisterSettlement({
      birthDate: patientBirthday,
      contractId,
      gender: patientGender,
      idCardNo: patientIdCard,
      name: patientName,
      organizationId,
      patientId: submitPatientId,
      phone: patientPhone,
      practitionerId,
      serviceTypeId,
    });
    const preRaw = pre as unknown;
    const chargeItems: ChargeItemPreviewDto[] = Array.isArray(preRaw)
      ? preRaw
      : (pre.chargeItems ?? []);
    if (chargeItems.length === 0) {
      ElMessage.error('预结算未返回费用明细，请联系管理员检查接口返回体');
      return;
    }

    let amountFromItems = 0;
    for (const item of chargeItems) {
      const amount = Number(item.amount);
      amountFromItems += Number.isFinite(amount) ? amount : 0;
    }
    const totalNumRaw = Array.isArray(preRaw) ? amountFromItems : Number(pre.totalAmount);
    const totalNum = Number.isFinite(totalNumRaw) ? totalNumRaw : amountFromItems;
    const medicalRecordNo =
      submitPatientId && patientRowById.value[submitPatientId]
        ? patientRowById.value[submitPatientId].patientBusNo
        : '';

    const nextDetail: RegisterConfirmDetail = {
      contractLabel,
      departmentLabel: deptLabel,
      doctorLabel,
      medicalRecordNo,
      patientAge: String(values.medicalInsuranceNo ?? ''),
      patientBirthday,
      patientGender,
      patientIdCard,
      patientName,
      patientPhone,
      serviceTypeLabel,
      totalAmount: totalNum,
    };
    confirmDialogRef.value?.open(nextDetail, pre);
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="flex h-full min-h-0 w-[37%] min-w-[460px] flex-col">
    <ElCard
      shadow="never"
      class="flex h-full min-h-0 flex-col overflow-hidden rounded-lg"
      header-class="reg-bill-left-card-header"
      body-class="!px-2 !py-1"
    >
      <template #header>
        <div class="flex items-center gap-2">
          <SelectTable
            v-model="selectedPatientId"
            style="width: 260px"
            placeholder="搜索患者（姓名/病历号/证件号）"
            :columns="patientSelectColumns"
            :remote-method="queryPatientOptions"
            value-key="patientId"
            label-key="name"
            :selected-label="selectedPatientLabel"
            @change="handlePatientSelect"
          />
          <div class="flex-1"></div>
          <ElButton type="primary" @click="handleClear"> 清空 </ElButton>
        </div>
      </template>

      <template #footer>
        <div class="mt-1 flex gap-2">
          <ElButton
            type="primary"
            size="default"
            class="flex-1"
            :loading="loading"
            @click="handleSubmit"
          >
            确认挂号
          </ElButton>
        </div>
      </template>

      <RegisterFormArea ref="registerFormAreaRef" />
    </ElCard>

    <RegisterConfirmDialog ref="confirmDialogRef" @success="onRegisterPaidSuccess" />
  </div>
</template>

<style scoped>
.reg-bill-left-card-header {
  padding-top: 6px !important;
  padding-bottom: 6px !important;
}
</style>
