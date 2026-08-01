<script setup lang="ts">
import type {
  ChargeItemPreviewDto,
  PatientItemDto,
  PreRegisterSettlementResult,
  RegisterConfirmDetail,
  RegisterFormAreaExpose,
  RegisterSlotSelectionPayload,
} from '../services';

import type { BookingEntityId } from '#/api/booking/booking-id';
import type { RegisterContractMatchDto } from '#/api/outpatient-registration';

import { computed, nextTick, ref } from 'vue';

import { useLocalStorage } from '@vueuse/core';
import { Refresh } from '@element-plus/icons-vue';
import { todayLocalDateIso } from '#/api/booking/booking-constants';
import { toBookingId } from '#/api/booking/booking-id';
import { ElButton, ElCheckbox, ElMessage } from 'element-plus';
import { useRegisterBookingMode } from '../composables/use-register-booking-mode';
import { useRegisterPatientIdentity } from '../composables/use-register-patient-identity';
import {
  CHARGE_TYPE_CATEGORY,
  enrichInsuranceBenefitPreview,
  isInsuranceContractCategory,
  isWorkInjuryContractCategory,
  toInsuranceBenefitDisplay,
  toStudentBenefitDisplay,
} from '../services/register-benefit.service';
import { mergeRefreshIntoPreSettlement } from '../services/register-insurance-refresh.utils';
import { runWorkInjuryRegister9102 } from '#/chs/work-injury/use-work-injury-register';
import { httpWorkInjuryLoadEncounterContext } from '#/chs/work-injury/work-injury.http';
import { PATIENT_IDENTITY_SOURCE_LABEL } from '../services/register-patient-identity.service';
import {
  pageRegisterPatientsForSelect,
  preRegisterSettlement,
  refreshRegisterFeeInsurancePreSettle,
} from '../services/register.service';
import { useRegisterBenefit } from '../services/use-register-benefit';
import DailySlotPanel from './daily-slot-panel.vue';
import RegisterConfirmDialog from './register-confirm-dialog.vue';
import RegisterFormArea from './register-form-area.vue';
import RegisterTopToolbar from './register-top-toolbar.vue';

defineOptions({
  name: 'RegisterLeftPanel',
});

const emit = defineEmits<{
  departmentChange: [organizationId: string];
  doctorChange: [practitionerId: string];
  registerCancelled: [];
  registerSuccess: [];
  serviceTypeChange: [serviceTypeId: string];
}>();

const { bookingEnabled } = useRegisterBookingMode();
const showSlotPanel = computed(() => bookingEnabled.value);

const SLOT_WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

const slotTodayDateLabel = computed(() => {
  const date = todayLocalDateIso();
  const parsed = new Date(`${date}T12:00:00`);
  const weekday = Number.isNaN(parsed.getTime()) ? '' : SLOT_WEEKDAY_LABELS[parsed.getDay()];
  return weekday ? `${date} ${weekday}` : date;
});

function handleSlotPanelRefresh() {
  void slotPanelRef.value?.refresh();
}
const registerFormAreaRef = ref<null | RegisterFormAreaExpose>(null);
const confirmDialogRef = ref<InstanceType<typeof RegisterConfirmDialog> | null>(null);
const slotPanelRef = ref<null | { refresh: () => Promise<void> }>(null);

const {
  benefitSyncing,
  treatmentSyncing,
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

/** 已选患者档案 ID（提交/待遇/历史用，与搜索框解耦） */
const selectedPatientId = ref('');
/** 患者搜索下拉的 v-model；选中回填后立即清空，避免还要手动清 */
const patientSearchId = ref('');
const selectedPatientLabel = ref('');
const selectedSlotId = ref('');
/** 省/市医保下是否用医保支付挂号费；浏览器 localStorage 记住上次选择。勾上后预结算固定允许个账（能统筹则统筹，否则扣个账）。 */
const useYbRegisterFee = useLocalStorage('outpatient-register:use-yb-register-fee', false);
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

const patientRowById = ref<Record<string, PatientItemDto>>({});
const loading = ref(false);

const slotOrganizationId = ref<BookingEntityId | undefined>();
const slotPractitionerId = ref<BookingEntityId | undefined>();
const slotHealthcareServiceId = ref<BookingEntityId | undefined>();

const isInsuranceRegister = computed(() =>
  isInsuranceContractCategory(currentContractCategory.value),
);

const isWorkInjuryRegister = computed(() =>
  isWorkInjuryContractCategory(currentContractCategory.value),
);

/** 工伤预结算后保留 encounter/contract，供待遇区与确认框 2301 入口。 */
const workInjuryPreEncounterId = ref<string | number | undefined>(undefined);
const workInjuryPreContractId = ref<string | number | undefined>(undefined);

function clearWorkInjuryPreContext() {
  workInjuryPreEncounterId.value = undefined;
  workInjuryPreContractId.value = undefined;
}

function handleContractCategoryChange(category: string) {
  resetBenefitPreviewCache();
  registerFormAreaRef.value?.clearBenefitPreview();
  clearWorkInjuryPreContext();
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
  // 仅空费用性质时用读卡结果引导省/市医保 1101；勿覆盖已选的工伤/公费
  if (!requirement && lastYbCardInfo.value) {
    const bootstrapId = lastYbIssuingAreaCode.value
      ? area.findBootstrapInsuranceContractIdByInsuplc(lastYbIssuingAreaCode.value)
      : area.findBootstrapInsuranceContractId();
    if (bootstrapId) {
      contractId = bootstrapId;
      requirement = 'INSURANCE';
      if (!String(values.contractId ?? '').trim()) {
        await area.applyContractId(bootstrapId, { emitChange: false });
        currentContractCategory.value = area.getContractCategory(bootstrapId) ?? '';
      }
    }
  }
  if (!contractId) {
    if (lastYbCardInfo.value) {
      ElMessage.warning('请先选择医保类费用性质，以便拉取待遇信息');
    }
    return;
  }
  if (requirement === 'INSURANCE') {
    const applyInsurancePreview = async (
      preview: import('#/api/outpatient-registration').YbRegisterBenefitPreviewDto,
      contractMatch?: import('#/api/outpatient-registration').RegisterContractMatchDto | null,
    ) => {
      const patientFallback = await buildPatientBenefitFallback();
      const enrichedPreview = enrichInsuranceBenefitPreview(preview, patientFallback);
      area.applyBenefitPreview(
        buildInsuranceBenefitDisplay(enrichedPreview, patientFallback),
        enrichedPreview,
        buildBenefitRefreshContext(contractId),
      );
      if (contractMatch !== undefined) {
        area.applyContractMatch(contractMatch ?? lastContractMatch.value);
      }
    };
    const result = await syncInsuranceBenefit({
      contractBusNo: area.getContractBusNo(contractId),
      contractId,
      patientId: selectedPatientId.value || undefined,
      onTreatmentReady: async (preview) => {
        await applyInsurancePreview(preview);
      },
    });
    if (result?.preview) {
      await applyInsurancePreview(result.preview, result.contractMatch ?? lastContractMatch.value);
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
    return;
  }
  if (requirement === 'WORK_INJURY') {
    area.applyBenefitPreview({
      kind: 'WORK_INJURY',
      eligibilityMessage: '工伤保险：挂号费须自费',
    });
    // 选工伤后后台预查（不阻塞 UI）；失败静默，详情弹窗可再查
    void (async () => {
      try {
        const { probeWorkInjuryFilingViaWin } = await import(
          '#/chs/work-injury/probe-work-injury-filing'
        );
        const { isWorkInjuryPluginOnline } = await import('#/chs/work-injury/work-injury-plugin');
        if (!(await isWorkInjuryPluginOnline())) {
          return;
        }
        const result = await probeWorkInjuryFilingViaWin();
        const summary = result.hasValidFiling
          ? `工伤保险：已查到 ${result.rows.length} 条审批，挂号费须自费`
          : `工伤保险：已识别人员 ${result.psnNo}，暂无审批，挂号费须自费`;
        area.applyBenefitPreview({
          kind: 'WORK_INJURY',
          eligibilityMessage: summary,
        });
      } catch {
        // 预查失败不打断选费用性质
      }
    })();
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

/** 按关键字与分页拉取患者列表，供顶栏患者下拉表格展示 */
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

/** 处理患者下拉变更：清空选择时同步表单；选中时回填患者信息并清空搜索框 */
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
    patientSearchId.value = '';
    selectedPatientLabel.value = '';
    area?.clearPatientFields();
    return;
  }
  selectedPatientId.value = patientId;
  const selectedPatient = patientRowById.value[patientId];
  if (!selectedPatient) return;
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
  // 等下拉先关闭，再清空搜索框，避免同 tick 清空触发点击穿透重开
  await nextTick();
  patientSearchId.value = '';
  selectedPatientLabel.value = '';
  await area?.focusFirstField();
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

/** 读卡前清空搜索框，避免残留上次检索文案 */
async function handleReadIdentity(source: 'id' | 'yb-card' | 'yb-ec') {
  patientSearchId.value = '';
  selectedPatientLabel.value = '';
  await readPatientIdentity(source);
}

const benefitPanelLoading = computed(() => benefitSyncing.value || cardReadAction.value !== null);

const cardReadDisabledMap = computed(() => ({
  'yb-card': isCardReadDisabled('yb-card'),
  id: isCardReadDisabled('id'),
  'yb-ec': isCardReadDisabled('yb-ec'),
}));

const cardReadLoadingMap = computed(() => ({
  'yb-card': isCardReadLoading('yb-card'),
  id: isCardReadLoading('id'),
  'yb-ec': isCardReadLoading('yb-ec'),
}));

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

/** 一键清空：挂号表单、患者下拉及患者行缓存 */
const handleClear = () => {
  resetBenefitState();
  clearWorkInjuryPreContext();
  registerFormAreaRef.value?.resetForClear();
  selectedSlotId.value = '';
  selectedPatientId.value = '';
  patientSearchId.value = '';
  selectedPatientLabel.value = '';
  patientRowById.value = {};
  currentContractCategory.value = '';
};

/** 科室变更：同步号源查询并通知外层 */
function handleDepartmentChange(organizationId: string) {
  slotOrganizationId.value = toBookingId(organizationId);
  slotPractitionerId.value = undefined;
  slotHealthcareServiceId.value = undefined;
  emit('departmentChange', organizationId);
}

/** 医师变更：同步号源筛选 */
function handleDoctorChange(practitionerId: string) {
  slotPractitionerId.value = toBookingId(practitionerId);
  emit('doctorChange', practitionerId);
}

/** 号源类型变更：同步号源筛选 */
function handleServiceTypeChange(serviceTypeId: string) {
  slotHealthcareServiceId.value = toBookingId(serviceTypeId);
  emit('serviceTypeChange', serviceTypeId);
}

/** 号源点选：预填挂号三要素 */
async function applySlotSelection(payload: RegisterSlotSelectionPayload) {
  selectedSlotId.value = String(payload.slotId ?? '');
  await registerFormAreaRef.value?.applySlotSelection(payload);
  ElMessage.success('已带入挂号信息');
}

defineExpose({
  applySlotSelection,
});

async function onRegisterPaidSuccess() {
  resetBenefitState();
  clearWorkInjuryPreContext();
  await registerFormAreaRef.value?.resetForSubmitSuccess();
  selectedSlotId.value = '';
  selectedPatientId.value = '';
  patientSearchId.value = '';
  selectedPatientLabel.value = '';
  currentContractCategory.value = '';
  await slotPanelRef.value?.refresh();
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
    ybUseAcctPay: true,
    ybMediumType: lastYbMediumType.value || undefined,
    ybCardInfo: lastYbCardInfo.value || undefined,
    psnNo: lastYbPreview.value.psnNo || undefined,
    insuplcAdmdvs:
      lastYbPreview.value.insuplcAdmdvs ||
      lastContractMatch.value?.patientInsuplcAdmdvs ||
      undefined,
    insutype: lastYbPreview.value.insutype || undefined,
    // 挂号费刷新预结算同样固定 med_type=11（普通门诊）
    medType: '11',
  });
  return mergeRefreshIntoPreSettlement(pre, refresh);
}

/** 提交挂号：预结算成功后打开核对弹窗 */
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
  // 未选医生时不传 practitionerId（挂到科室）；禁止传空串导致后端 Long 反序列化失败
  const doctorRaw = String(values.doctorId ?? '').trim();
  const practitionerId = doctorRaw !== '' ? doctorRaw : undefined;

  // 医保合同预结算会走平台 2201，与是否勾选「医保支付挂号费」无关，须先读卡拿到 psnNo
  if (isInsuranceRegister.value && !lastYbPreview.value?.psnNo) {
    ElMessage.warning('医保挂号须先读卡获取人员编号');
    return;
  }

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
            // 医保建档（2201）始终需要读卡字段；挂号费是否走医保由 ybRegisterFee 控制
            psnNo: lastYbPreview.value?.psnNo || undefined,
            insuplcAdmdvs:
              lastYbPreview.value?.insuplcAdmdvs ||
              lastContractMatch.value?.patientInsuplcAdmdvs ||
              undefined,
            insutype: lastYbPreview.value?.insutype || undefined,
            ybMediumType: lastYbMediumType.value || undefined,
            ybCardInfo: lastYbCardInfo.value || undefined,
            ybRegisterFee: useYbRegisterFee.value,
            ...(useYbRegisterFee.value
              ? {
                  ybUseAcctPay: true,
                  // 挂号费医保：med_type=11（普通门诊）；2201 仅在预结算 map miss 时触发
                  medType: '11',
                }
              : {}),
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

    if (isWorkInjuryRegister.value && pre.encounterId) {
      workInjuryPreEncounterId.value = pre.encounterId;
      workInjuryPreContractId.value = contractId;
      await runWorkInjuryRegister9102({
        encounterId: pre.encounterId,
        contractId,
      });
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
    confirmDialogRef.value?.open(nextDetail, pre, {
      insuranceMediumType: lastYbMediumType.value || undefined,
      ybCredentialReadAt: lastYbCredentialReadAt.value,
      rescanAndRefresh: (currentPre) => rescanEcAndRefreshPreSettlement(currentPre),
      workInjuryRegister: isWorkInjuryRegister.value,
      workInjuryContractId: isWorkInjuryRegister.value ? contractId : undefined,
    });

    // 确认框打开后：无有效 qualification 则弹出审批引导（绑定或 2301）
    if (isWorkInjuryRegister.value && pre.encounterId) {
      try {
        const wiCtx = await httpWorkInjuryLoadEncounterContext(pre.encounterId);
        if (!wiCtx?.qualificationValid) {
          await nextTick();
          area.openWorkInjuryApprovalGuide();
        }
      } catch {
        await nextTick();
        area.openWorkInjuryApprovalGuide();
      }
    }
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <RegisterFormArea
    ref="registerFormAreaRef"
    class="h-full min-h-0"
    layout="split"
    :active-patient-id="selectedPatientId"
    :benefit-loading="benefitPanelLoading"
    :benefit-loading-text="benefitLoadingText"
    :treatment-loading="treatmentSyncing"
    :work-injury-selected="isWorkInjuryRegister"
    :work-injury-pre-encounter-id="workInjuryPreEncounterId"
    :work-injury-pre-contract-id="workInjuryPreContractId"
    @contract-change="handleContractCategoryChange"
    @department-change="handleDepartmentChange"
    @doctor-change="handleDoctorChange"
    @service-type-change="handleServiceTypeChange"
  >
    <template #patient-toolbar>
      <RegisterTopToolbar
        v-model:selected-patient-id="patientSearchId"
        :selected-patient-label="selectedPatientLabel"
        :patient-select-columns="patientSelectColumns"
        :query-patient-options="queryPatientOptions"
        :card-read-disabled="cardReadDisabledMap"
        :card-read-loading="cardReadLoadingMap"
        variant="patient"
        @patient-change="(value) => handlePatientSelect(value)"
        @clear="handleClear"
        @read-identity="handleReadIdentity"
      />
    </template>
    <template #slots-toolbar>
      <div class="register-slot-toolbar">
        <span class="register-slot-toolbar__date">{{ slotTodayDateLabel }}</span>
        <ElButton
          v-if="showSlotPanel"
          class="register-slot-toolbar__refresh ml-auto"
          :icon="Refresh"
          circle
          text
          title="刷新今日号源"
          @click="handleSlotPanelRefresh"
        />
      </div>
    </template>
    <template #slots>
      <DailySlotPanel
        v-if="showSlotPanel"
        ref="slotPanelRef"
        variant="horizontal"
        hide-toolbar
        :show-filters="false"
        :organization-id="slotOrganizationId"
        :practitioner-id="slotPractitionerId"
        :healthcare-service-id="slotHealthcareServiceId"
        @slot-selected="applySlotSelection"
      />
      <div v-else class="register-slot-placeholder">
        <span class="register-slot-placeholder__text"
          >当前未启用号源功能，请在上方选择科室与医生</span
        >
      </div>
    </template>
    <template #actions>
      <ElCheckbox
        v-if="isInsuranceRegister"
        v-model="useYbRegisterFee"
        class="shrink-0 whitespace-nowrap"
      >
        医保支付挂号费
      </ElCheckbox>
      <ElButton
        type="primary"
        class="register-workspace__confirm-btn"
        :loading="loading"
        @click="handleSubmit"
      >
        确认挂号
      </ElButton>
    </template>
  </RegisterFormArea>

  <RegisterConfirmDialog
    ref="confirmDialogRef"
    @success="onRegisterPaidSuccess"
    @cancel="onRegisterConfirmCancelled"
  />
</template>

<style scoped>
.register-workspace__confirm-btn {
  min-width: 120px;
  height: 36px;
  padding: 0 24px;
  font-size: 14px;
}

.register-slot-toolbar {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 100%;
  min-height: 36px;
}

.register-slot-toolbar__date {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.register-slot-toolbar__refresh.el-button.is-circle {
  width: 24px;
  height: 24px;
  padding: 0;
}

.register-slot-placeholder {
  display: flex;
  flex: 1 1 auto;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  padding: 24px 16px;
  background: #fff;
  border: 1px dashed #dce3eb;
  border-radius: 8px;
}

.register-slot-placeholder__text {
  max-width: 240px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
