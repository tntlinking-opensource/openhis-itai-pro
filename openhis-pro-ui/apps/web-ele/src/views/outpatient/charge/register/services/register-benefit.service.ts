import type {
  LoadRegisterBenefitPreviewParams,
  LoadRegisterBenefitPreviewResult,
  StudentEligibilityPreviewDto,
  StudentRecordPreviewDto,
  YbRegisterBenefitPreviewDto,
} from '#/api/outpatient-registration';

import { httpLoadRegisterBenefitPreview } from '#/api/outpatient-registration';

import type { RegisterBenefitDisplayPayload } from './register.types';

export {
  CHARGE_TYPE_CATEGORY,
  INSURANCE_CONTRACT_CATEGORIES,
  INSURANCE_CONTRACT_CATEGORY_PRIORITY,
  isCrossProvinceInsuplc,
  isInsuranceContractCategory,
  isNonSelfPayContractCategory,
  isPublicChargeContractCategory,
  isSelfPayContractCategory,
  isWorkInjuryContractCategory,
  JILIN_INSUPLC_PROVINCE_PREFIX,
  PUBLIC_CHARGE_CONTRACT_CATEGORIES,
  requiresInsuranceBenefitFlow,
} from './charge-type-category';

export async function loadRegisterBenefitPreview(
  body: LoadRegisterBenefitPreviewParams,
): Promise<LoadRegisterBenefitPreviewResult> {
  return httpLoadRegisterBenefitPreview(body);
}

export interface InsuranceBenefitPreviewPatientFallback {
  patientBirthday?: string;
  patientGender?: string;
  patientIdCard?: string;
  patientName?: string;
}

/** 1101 缓存/确认摘要缺字段时，用患者表单与根级险种信息补全展示 */
export function enrichInsuranceBenefitPreview(
  preview: YbRegisterBenefitPreviewDto,
  fallback?: InsuranceBenefitPreviewPatientFallback,
): YbRegisterBenefitPreviewDto {
  const psnName = (preview.psnName ?? fallback?.patientName ?? '').trim() || undefined;
  const certno = (preview.certno ?? fallback?.patientIdCard ?? '').trim() || undefined;
  const gend =
    (preview.gend ?? normalizeYbGenderCode(fallback?.patientGender) ?? '').trim() || undefined;
  const brdy = (preview.brdy ?? fallback?.patientBirthday ?? '').trim() || undefined;

  let insuinfoList = preview.insuinfoList ?? [];
  if (insuinfoList.length === 0 && (preview.insutype || preview.insuplcAdmdvs)) {
    insuinfoList = [
      {
        balc: preview.balc,
        insuplcAdmdvs: preview.insuplcAdmdvs,
        insutype: preview.insutype,
      },
    ];
  }

  return {
    ...preview,
    brdy,
    certno,
    gend,
    insuinfoList,
    psnName,
  };
}

function normalizeYbGenderCode(gender?: string): string | undefined {
  const text = String(gender ?? '')
    .trim()
    .toUpperCase();
  if (!text) {
    return undefined;
  }
  if (text === 'MALE' || text === '1') {
    return '1';
  }
  if (text === 'FEMALE' || text === '2') {
    return '2';
  }
  return text;
}

export interface InsuranceBenefitDisplayExtras {
  fallbackCertno?: string;
  fallbackPsnName?: string;
  mediumType?: string;
}

/** 1101 预览 → 表单区「待遇信息」展示 */
export function toInsuranceBenefitDisplay(
  preview: YbRegisterBenefitPreviewDto,
  cardNo?: string,
  insuIndex = 0,
  extras?: InsuranceBenefitDisplayExtras,
): RegisterBenefitDisplayPayload {
  const list = preview.insuinfoList ?? [];
  const idx = list.length > 0 ? Math.min(Math.max(insuIndex, 0), list.length - 1) : -1;
  const insu = idx >= 0 ? list[idx] : undefined;
  const certno = (preview.certno ?? extras?.fallbackCertno ?? '').trim() || undefined;
  const psnName = (preview.psnName ?? extras?.fallbackPsnName ?? '').trim() || undefined;
  return {
    kind: 'INSURANCE',
    cardNo,
    certno,
    mediumType: extras?.mediumType,
    psnNo: preview.psnNo,
    psnName,
    insuplcAdmdvs: insu?.insuplcAdmdvs ?? preview.insuplcAdmdvs,
    insutype: insu?.insutype ?? preview.insutype,
    balc: insu?.balc ?? preview.balc,
    psnType: insu?.psnType,
    psnInsuStas: insu?.psnInsuStas,
    areaHint: preview.areaHint?.hintMessage,
    mdtrtareaAdmdvs: preview.areaHint?.mdtrtareaAdmdvs,
    specialDisease: preview.opspFilingList?.[0]?.opspDiseName,
    treatmentBlocked: preview.treatmentBlocked,
  };
}

/** 学籍预览 → 表单区「待遇信息」展示 */
export function toStudentBenefitDisplay(
  record: StudentRecordPreviewDto,
  eligibility?: StudentEligibilityPreviewDto | null,
): RegisterBenefitDisplayPayload {
  return {
    kind: 'STUDENT',
    studentName: record.name,
    studentNo: record.studentNo,
    college: record.college,
    eligible: eligibility?.eligible,
    eligibilityMessage: eligibility?.reasonMessage,
  };
}
