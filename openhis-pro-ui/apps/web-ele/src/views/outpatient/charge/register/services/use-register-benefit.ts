import type { PatientIdentitySnapshot } from './register-patient-identity.service';

import type {
  RegisterContractMatchDto,
  YbRegisterBenefitPreviewDto,
} from '#/api/outpatient-registration';

import { ref } from 'vue';

import { ElMessage } from 'element-plus';

import { ensureInsuranceSignIn } from '#/chs/utils/ensure-insurance-sign-in';

import {
  isInsuranceContractCategory,
  isPublicChargeContractCategory,
  isWorkInjuryContractCategory,
  loadRegisterBenefitPreview,
} from './register-benefit.service';
import { applyInsuranceSnapshotToRefs } from './register-patient-identity.service';

export function useRegisterBenefit() {
  const benefitSyncing = ref(false);
  const lastYbPreview = ref<null | YbRegisterBenefitPreviewDto>(null);
  const lastYbCardInfo = ref('');
  const lastYbCardSn = ref('');
  const lastYbCertno = ref('');
  const lastYbPsnName = ref('');
  const lastYbIssuingAreaCode = ref('');
  const lastYbMediumType = ref('03');
  const lastYbCredentialReadAt = ref<null | number>(null);
  const lastContractId = ref('');
  const lastStudentId = ref('');
  const lastContractMatch = ref<null | RegisterContractMatchDto>(null);

  type InsuranceBenefitSyncResult = null | {
    contractMatch?: RegisterContractMatchDto;
    preview: YbRegisterBenefitPreviewDto;
  };

  let insuranceBenefitInFlight: null | Promise<InsuranceBenefitSyncResult> = null;

  function resetBenefitState() {
    lastYbPreview.value = null;
    lastYbCardInfo.value = '';
    lastYbCardSn.value = '';
    lastYbCertno.value = '';
    lastYbPsnName.value = '';
    lastYbIssuingAreaCode.value = '';
    lastYbCredentialReadAt.value = null;
    lastContractId.value = '';
    lastStudentId.value = '';
    lastContractMatch.value = null;
  }

  /** 费用性质切换：保留读卡信息，仅清空预览缓存。 */
  function resetBenefitPreviewCache() {
    lastYbPreview.value = null;
    lastContractId.value = '';
    lastStudentId.value = '';
    lastContractMatch.value = null;
  }

  function fillInsurancePatientNameIfEmpty(name: string) {
    const normalized = name.trim();
    if (!lastYbPsnName.value.trim() && normalized) {
      lastYbPsnName.value = normalized;
    }
  }

  function applyInsuranceSnapshot(snapshot: PatientIdentitySnapshot) {
    applyInsuranceSnapshotToRefs(snapshot, {
      lastYbCardInfo,
      lastYbCardSn,
      lastYbCertno,
      lastYbPsnName,
      lastYbIssuingAreaCode,
      lastYbMediumType,
    });
    lastYbCredentialReadAt.value = Date.now();
  }

  async function syncInsuranceBenefit(params: {
    contractBusNo?: string;
    contractId: string;
    patientId?: string;
    /** 2001 医疗类别；门诊默认 11，住院登记传 21 */
    medType?: string;
  }): Promise<InsuranceBenefitSyncResult> {
    if (!lastYbCardInfo.value) {
      return null;
    }
    if (lastYbMediumType.value === '03' && !lastYbCardSn.value.trim()) {
      ElMessage.error('社保卡读卡缺少卡识别码，请重新读卡');
      return null;
    }
    if (insuranceBenefitInFlight) {
      return insuranceBenefitInFlight;
    }
    insuranceBenefitInFlight = (async () => {
      benefitSyncing.value = true;
      try {
        if (params.contractBusNo) {
          await ensureInsuranceSignIn(params.contractBusNo, { warnOnServerFallback: false });
        }
        const previewResult = await loadRegisterBenefitPreview({
          contractId: params.contractId,
          insuplcAdmdvs:
            lastYbMediumType.value === '03' ? lastYbIssuingAreaCode.value || undefined : undefined,
          medType: params.medType,
          runCumulativeQuery: true,
          runOpspFilingQuery: true,
          runTreatmentCheck: true,
          ybCardInfo: lastYbCardInfo.value,
          ybCardSn: lastYbCardSn.value || undefined,
          ybCertno: lastYbCertno.value || undefined,
          ybMediumType: lastYbMediumType.value,
          ybPsnName: lastYbPsnName.value || undefined,
        });
        const preview = previewResult.ybPreview;
        if (!preview?.psnNo) {
          ElMessage.warning('医保待遇拉取未返回人员编号，请检查读卡信息或费用性质');
          return null;
        }
        lastYbPreview.value = preview;
        lastContractId.value = params.contractId;
        lastContractMatch.value = previewResult.contractMatch ?? null;
        return {
          preview,
          contractMatch: lastContractMatch.value ?? undefined,
        };
      } catch {
        // 全局 request 拦截器已展示持久化友好提示，此处不再重复 ElMessage
        return null;
      } finally {
        benefitSyncing.value = false;
        insuranceBenefitInFlight = null;
      }
    })();
    return insuranceBenefitInFlight;
  }

  async function syncStudentBenefit(params: {
    contractId: string;
    patientId: string;
  }): Promise<null | {
    eligibility?: import('#/api/outpatient-registration').StudentEligibilityPreviewDto;
    record?: import('#/api/outpatient-registration').StudentRecordPreviewDto;
  }> {
    benefitSyncing.value = true;
    try {
      const preview = await loadRegisterBenefitPreview({
        contractId: params.contractId,
        patientId: params.patientId,
      });
      const records = preview.studentPreview?.studentRecords ?? [];
      if (records.length === 0) {
        return null;
      }
      const studentId = String(records[0]?.studentId ?? '');
      const withEligibility = await loadRegisterBenefitPreview({
        contractId: params.contractId,
        patientId: params.patientId,
        studentId,
      });
      lastContractId.value = params.contractId;
      lastStudentId.value = studentId;
      return {
        record: records[0],
        eligibility: withEligibility.studentEligibility,
      };
    } catch {
      return null;
    } finally {
      benefitSyncing.value = false;
    }
  }

  /** 切换险种后仅刷新 2001 待遇检查。 */
  async function refreshTreatmentCheck(params: {
    contractId: string;
    insuplcAdmdvs?: string;
    insutype: string;
    medType?: string;
    psnNo: string;
  }): Promise<null | YbRegisterBenefitPreviewDto> {
    const previewResult = await loadRegisterBenefitPreview({
      contractId: params.contractId,
      insuplcAdmdvs: params.insuplcAdmdvs,
      insutype: params.insutype,
      medType: params.medType,
      psnNo: params.psnNo,
      runOpspFilingQuery: false,
      runTreatmentCheck: true,
      ybCardInfo: lastYbCardInfo.value || undefined,
      ybMediumType: lastYbCardInfo.value ? lastYbMediumType.value : undefined,
    });
    const preview = previewResult.ybPreview;
    if (preview) {
      lastYbPreview.value = preview;
    }
    return preview ?? null;
  }

  async function loadStudentBenefit(params: {
    contractId: string;
    patientId: string;
    studentId?: string;
  }) {
    return loadRegisterBenefitPreview({
      contractId: params.contractId,
      patientId: params.patientId,
      studentId: params.studentId,
    });
  }

  function resolveBenefitRequirement(contractCategory?: string) {
    if (isWorkInjuryContractCategory(contractCategory)) {
      return 'WORK_INJURY' as const;
    }
    if (isInsuranceContractCategory(contractCategory)) {
      return 'INSURANCE' as const;
    }
    if (isPublicChargeContractCategory(contractCategory)) {
      return 'STUDENT' as const;
    }
    return null;
  }

  return {
    applyInsuranceSnapshot,
    benefitSyncing,
    lastContractId,
    lastContractMatch,
    lastStudentId,
    lastYbCardInfo,
    lastYbCardSn,
    lastYbCertno,
    lastYbPsnName,
    lastYbIssuingAreaCode,
    lastYbMediumType,
    lastYbCredentialReadAt,
    lastYbPreview,
    fillInsurancePatientNameIfEmpty,
    loadStudentBenefit,
    refreshTreatmentCheck,
    resetBenefitPreviewCache,
    resetBenefitState,
    resolveBenefitRequirement,
    syncInsuranceBenefit,
    syncStudentBenefit,
  };
}
