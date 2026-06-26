<script setup lang="ts">
import type {
  ChargeItemPreviewDto,
  PatientItemDto,
  PreRegisterSettlementResult,
  RegisterConfirmDetail,
  RegisterFormAreaExpose,
  RegisterSlotSelectionPayload,
} from '../services';

import type { RegisterContractMatchDto } from '#/api/outpatient-registration';

import { computed, ref } from 'vue';

import { ElButton, ElCard, ElCheckbox, ElMessage } from 'element-plus';

import SelectTable from '#/components/select-table.vue';

import { useRegisterPatientIdentity } from '../composables/use-register-patient-identity';
import {
  CHARGE_TYPE_CATEGORY,
  enrichInsuranceBenefitPreview,
  isInsuranceContractCategory,
  toInsuranceBenefitDisplay,
  toStudentBenefitDisplay,
} from '../services/register-benefit.service';
import { mergeRefreshIntoPreSettlement } from '../services/register-insurance-refresh.utils';
import { PATIENT_IDENTITY_SOURCE_LABEL } from '../services/register-patient-identity.service';
import {
  pageRegisterPatientsForSelect,
  preRegisterSettlement,
  refreshRegisterFeeInsurancePreSettle,
} from '../services/register.service';
import { useRegisterBenefit } from '../services/use-register-benefit';
import RegisterConfirmDialog from './register-confirm-dialog.vue';
import RegisterFormArea from './register-form-area.vue';

defineOptions({
  name: 'RegisterLeftPanel',
});

const emit = defineEmits<{
  /** 左侧挂号科室变更：同步右侧当日号源查询 */
  departmentChange: [organizationId: string];
  doctorChange: [practitionerId: string];
  /** 取消核对弹窗：刷新右侧待挂号列表 */
  registerCancelled: [];
  /** 挂号费结算成功：由页面刷新右侧就诊记录 */
  registerSuccess: [];
  serviceTypeChange: [serviceTypeId: string];
}>();

const registerFormAreaRef = ref<null | RegisterFormAreaExpose>(null);
const confirmDialogRef = ref<InstanceType<typeof RegisterConfirmDialog> | null>(null);

const {
  benefitSyncing,
  lastContractMatch,
  lastYbPreview,
  lastYbCardInfo,
  lastYbCardSn,
  lastYbCertno,
  lastYbPsnName,
  lastYbIssuingAreaCode,
  lastYbMediumType,
  lastYbCredentialReadAt,
  resetBenefitPreviewCache,
  resetBenefitState,
  resolveBenefitRequirement,
  applyInsuranceSnapshot,
  fillInsurancePatientNameIfEmpty,
  syncInsuranceBenefit,
  syncStudentBenefit,
} = useRegisterBenefit();

function buildInsuranceBenefitDisplay(
  preview: import('#/api/outpatient-registration').YbRegisterBenefitPreviewDto,
  patientFallback?: {
    patientBirthday?: string;
    patientGender?: string;
    patientIdCard?: string;
    patientName?: string;
  },
) {
  const enriched = enrichInsuranceBenefitPreview(preview, patientFallback);
  return toInsuranceBenefitDisplay(enriched, lastYbCardInfo.value, 0, {
    fallbackCertno: lastYbCertno.value || patientFallback?.patientIdCard,
    fallbackPsnName: patientFallback?.patientName,
    mediumType: lastYbMediumType.value,
  });
}

async function buildPatientBenefitFallback() {
  const area = registerFormAreaRef.value;
  if (!area) {
    return undefined;
  }
  const values = await area.getSubmitValues();
  return {
    patientBirthday: String(values.patientBirthday ?? '').trim() || undefined,
    patientGender: String(values.patientGender ?? '').trim() || undefined,
    patientIdCard: String(values.patientIdCard ?? '').trim() || undefined,
    patientName: String(values.patientName ?? '').trim() || undefined,
  };
}

function buildBenefitRefreshContext(contractId?: string) {
  return {
    contractId: contractId?.trim() || undefined,
    ybCardInfo: lastYbCardInfo.value || undefined,
    ybCardSn: lastYbCardSn.value || undefined,
    ybCertno: lastYbCertno.value || undefined,
    ybMediumType: lastYbMediumType.value || undefined,
    ybPsnName: lastYbPsnName.value || undefined,
  };
}

const selectedPatientId = ref('');
const selectedPatientLabel = ref('');
const selectedSlotId = ref('');
const useYbAcctPay = ref(true);
const currentContractCategory = ref('');
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

const isInsuranceRegister = computed(() =>
  isInsuranceContractCategory(currentContractCategory.value),
);

function handleContractCategoryChange(category: string) {
  resetBenefitPreviewCache();
  registerFormAreaRef.value?.clearBenefitPreview();
  currentContractCategory.value = category;
  void syncBenefitPreview();
}

async function syncBenefitPreview(options?: { autoApplyRecommend?: boolean }) {
  const area = registerFormAreaRef.value;
  if (!area) {
    return;
  }
  await refreshContractCategoryFromForm();
  const values = await area.getSubmitValues();
  let contractId = String(values.contractId ?? '').trim();
  let requirement = resolveBenefitRequirement(currentContractCategory.value);
  if (requirement !== 'INSURANCE' && lastYbCardInfo.value) {
    const bootstrapId = lastYbIssuingAreaCode.value
      ? area.findBootstrapInsuranceContractIdByInsuplc(lastYbIssuingAreaCode.value)
      : area.findBootstrapInsuranceContractId();
    if (bootstrapId) {
      contractId = bootstrapId;
      requirement = 'INSURANCE';
    }
  }
  if (!contractId) {
    if (lastYbCardInfo.value) {
      ElMessage.warning('请先选择医保类费用性质，以便拉取待遇信息');
    }
    return;
  }
  if (requirement === 'INSURANCE') {
    const result = await syncInsuranceBenefit({
      contractBusNo: area.getContractBusNo(contractId),
      contractId,
      patientId: selectedPatientId.value || undefined,
    });
    if (result?.preview) {
      const patientFallback = await buildPatientBenefitFallback();
      const enrichedPreview = enrichInsuranceBenefitPreview(result.preview, patientFallback);
      area.applyBenefitPreview(
        buildInsuranceBenefitDisplay(enrichedPreview, patientFallback),
        enrichedPreview,
        buildBenefitRefreshContext(contractId),
      );
      area.applyContractMatch(result.contractMatch ?? lastContractMatch.value);
      if (options?.autoApplyRecommend) {
        await maybeApplyRecommendedContract(result.contractMatch, true);
      }
    }
    return;
  }
  if (requirement === 'STUDENT' && selectedPatientId.value) {
    const student = await syncStudentBenefit({
      contractId,
      patientId: selectedPatientId.value,
    });
    if (student?.record) {
      area.applyBenefitPreview(toStudentBenefitDisplay(student.record, student.eligibility));
    }
  }
}

async function maybeApplyRecommendedContract(
  match: null | RegisterContractMatchDto | undefined,
  autoApply: boolean,
) {
  const area = registerFormAreaRef.value;
  if (!area || !match?.recommendedContractId) {
    return;
  }
  const values = await area.getSubmitValues();
  const currentId = String(values.contractId ?? '');
  const currentCategory = area.getContractCategory(values.contractId) ?? '';
  const shouldApply =
    autoApply &&
    match.recommendedContractId !== currentId &&
    (currentCategory === CHARGE_TYPE_CATEGORY.SELF_PAY ||
      !currentCategory ||
      match.matchStatus !== 'MATCH');
  if (!shouldApply) {
    return;
  }
  await area.applyContractId(match.recommendedContractId);
  ElMessage.success(`已切换费用性质为：${match.recommendedContractName ?? '推荐项'}`);
  await syncBenefitPreview();
}

async function bootstrapBenefitAfterCardRead() {
  await syncBenefitPreview({ autoApplyRecommend: true });
}

async function refreshContractCategoryFromForm() {
  const area = registerFormAreaRef.value;
  if (!area) {
    currentContractCategory.value = '';
    return;
  }
  const values = await area.getSubmitValues();
  currentContractCategory.value = area.getContractCategory(values.contractId) ?? '';
}

/**
 * 按关键字与分页拉取患者列表，供头部患者下拉表格展示。
 */
const queryPatientOptions = async ({
  pageNo = 1,
  pageSize = 20,
  keyword = '',
}: {
  keyword?: string;
  pageNo?: number;
  pageSize?: number;
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
 * 读卡自动匹配时 preserveInsuranceRead 保留刚读入的医保凭证，避免待遇拉取失败。
 */
const handlePatientSelect = async (
  value: string | string[],
  options?: { preserveInsuranceRead?: boolean },
) => {
  if (options?.preserveInsuranceRead) {
    resetBenefitPreviewCache();
  } else {
    resetBenefitState();
  }
  registerFormAreaRef.value?.clearBenefitPreview();
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
  // 读卡匹配患者时由 bootstrapBenefitAfterCardRead 统一拉待遇，避免并发 1101 重复写 yb_person_cache
  if (!options?.preserveInsuranceRead) {
    void syncBenefitPreview();
  }
};

const { cardReadAction, isCardReadDisabled, isCardReadLoading, readPatientIdentity } =
  useRegisterPatientIdentity({
    registerFormAreaRef,
    patientRowById,
    selectedPatientId,
    selectedPatientLabel,
    applyInsuranceSnapshot,
    fillPatientNameIfEmpty: fillInsurancePatientNameIfEmpty,
    resetBenefitState,
    onPatientSelect: handlePatientSelect,
    onBootstrapBenefit: bootstrapBenefitAfterCardRead,
  });

const benefitPanelLoading = computed(() => benefitSyncing.value || cardReadAction.value !== null);

const benefitLoadingText = computed(() => {
  const action = cardReadAction.value;
  if (action) {
    return `正在读取${PATIENT_IDENTITY_SOURCE_LABEL[action]}并拉取待遇信息…`;
  }
  if (benefitSyncing.value) {
    return '正在拉取待遇信息…';
  }
  return '';
});

/**
 * 一键清空：挂号表单、患者下拉及患者行缓存。
 */
const handleClear = () => {
  resetBenefitState();
  registerFormAreaRef.value?.resetForClear();
  selectedSlotId.value = '';
  selectedPatientId.value = '';
  selectedPatientLabel.value = '';
  patientRowById.value = {};
  currentContractCategory.value = '';
};

/**
 * 号源日历点选：预填挂号三要素。
 */
async function applySlotSelection(payload: RegisterSlotSelectionPayload) {
  selectedSlotId.value = String(payload.slotId ?? '');
  await registerFormAreaRef.value?.applySlotSelection(payload);
  ElMessage.success('已带入挂号信息');
}

defineExpose({
  applySlotSelection,
});

/**
 * 正式结算成功后：重置表单与患者选择，并通知外层刷新就诊记录。
 */
async function onRegisterPaidSuccess() {
  resetBenefitState();
  await registerFormAreaRef.value?.resetForSubmitSuccess();
  selectedSlotId.value = '';
  selectedPatientId.value = '';
  selectedPatientLabel.value = '';
  currentContractCategory.value = '';
  emit('registerSuccess');
}

function onRegisterConfirmCancelled() {
  emit('registerCancelled');
}

async function rescanEcAndRefreshPreSettlement(
  pre: PreRegisterSettlementResult,
): Promise<null | PreRegisterSettlementResult> {
  if (!pre.paymentDraftId) {
    return null;
  }
  await readPatientIdentity('yb-ec');
  if (!lastYbCardInfo.value || !lastYbPreview.value?.psnNo) {
    ElMessage.warning('重新扫医保码未获取有效凭证，请重试');
    return null;
  }
  const refresh = await refreshRegisterFeeInsurancePreSettle({
    paymentDraftId: pre.paymentDraftId,
    ybUseAcctPay: useYbAcctPay.value,
    ybMediumType: lastYbMediumType.value || undefined,
    ybCardInfo: lastYbCardInfo.value || undefined,
    psnNo: lastYbPreview.value.psnNo || undefined,
    insuplcAdmdvs:
      lastYbPreview.value.insuplcAdmdvs ||
      lastContractMatch.value?.patientInsuplcAdmdvs ||
      undefined,
    insutype: lastYbPreview.value.insutype || undefined,
    medType: '11',
  });
  return mergeRefreshIntoPreSettlement(pre, refresh);
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
  await refreshContractCategoryFromForm();

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
    const preRegisterBody = {
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
      slotId: selectedSlotId.value || undefined,
      ...(isInsuranceRegister.value
        ? {
            ybRegisterFee: true,
            ybUseAcctPay: useYbAcctPay.value,
            psnNo: lastYbPreview.value?.psnNo || undefined,
            insuplcAdmdvs:
              lastYbPreview.value?.insuplcAdmdvs ||
              lastContractMatch.value?.patientInsuplcAdmdvs ||
              undefined,
            insutype: lastYbPreview.value?.insutype || undefined,
            ybMediumType: lastYbMediumType.value || undefined,
            ybCardInfo: lastYbCardInfo.value || undefined,
            medType: '11',
          }
        : {}),
    };
    const pre: PreRegisterSettlementResult = await preRegisterSettlement(preRegisterBody);
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

    if (isInsuranceRegister.value && useYbAcctPay.value && pre.insurancePreSettled) {
      const acctPay = Number(pre.ybAcctPay ?? 0);
      const balc =
        lastYbPreview.value?.balc === null || lastYbPreview.value?.balc === undefined
          ? null
          : Number(lastYbPreview.value.balc);
      if (acctPay > 0 && balc !== null && Number.isFinite(balc) && acctPay > balc) {
        ElMessage.warning('医保个人账户余额不足，请取消使用个账后重新确认挂号');
        return;
      }
    }

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
    confirmDialogRef.value?.open(nextDetail, pre, {
      insuranceMediumType: lastYbMediumType.value || undefined,
      ybCredentialReadAt: lastYbCredentialReadAt.value,
      rescanAndRefresh: (currentPre) => rescanEcAndRefreshPreSettlement(currentPre),
    });
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
      header-class="reg-bill-left-card-header shrink-0"
      body-class="!flex !min-h-0 !flex-1 !flex-col !overflow-hidden !px-2 !py-1"
      footer-class="reg-bill-left-card-footer shrink-0"
    >
      <template #header>
        <div class="flex flex-col gap-2">
          <div class="flex flex-wrap items-center gap-2">
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
            <ElButton type="primary" @click="handleClear"> 清空 </ElButton>
            <ElButton
              :loading="isCardReadLoading('yb-card')"
              :disabled="isCardReadDisabled('yb-card')"
              @click="readPatientIdentity('yb-card')"
            >
              医保卡
            </ElButton>
            <ElButton
              :loading="isCardReadLoading('id')"
              :disabled="isCardReadDisabled('id')"
              @click="readPatientIdentity('id')"
            >
              身份证
            </ElButton>
            <ElButton
              :loading="isCardReadLoading('yb-ec')"
              :disabled="isCardReadDisabled('yb-ec')"
              @click="readPatientIdentity('yb-ec')"
            >
              医保码
            </ElButton>
          </div>
        </div>
      </template>

      <template #footer>
        <div class="mt-1 flex items-center gap-3">
          <ElCheckbox
            v-if="isInsuranceRegister"
            v-model="useYbAcctPay"
            class="shrink-0 whitespace-nowrap"
          >
            使用个账
          </ElCheckbox>
          <ElButton
            type="primary"
            size="default"
            class="min-w-0 flex-1"
            :loading="loading"
            @click="handleSubmit"
          >
            确认挂号
          </ElButton>
        </div>
      </template>

      <RegisterFormArea
        ref="registerFormAreaRef"
        :active-patient-id="selectedPatientId"
        :benefit-loading="benefitPanelLoading"
        :benefit-loading-text="benefitLoadingText"
        @contract-change="handleContractCategoryChange"
        @department-change="(organizationId) => emit('departmentChange', organizationId)"
        @doctor-change="(practitionerId) => emit('doctorChange', practitionerId)"
        @service-type-change="(serviceTypeId) => emit('serviceTypeChange', serviceTypeId)"
      />
    </ElCard>

    <RegisterConfirmDialog
      ref="confirmDialogRef"
      @success="onRegisterPaidSuccess"
      @cancel="onRegisterConfirmCancelled"
    />
  </div>
</template>

<style scoped>
.reg-bill-left-card-header {
  padding-top: 6px !important;
  padding-bottom: 6px !important;
}

.reg-bill-left-card-footer {
  padding-top: 8px !important;
  padding-bottom: 8px !important;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
