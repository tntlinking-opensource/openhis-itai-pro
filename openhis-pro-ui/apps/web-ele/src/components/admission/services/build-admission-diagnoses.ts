import type { AdmissionDiagnosisSubmitDto } from '#/api/inpatient-registration';

import type { AdmissionDiagnosisFormState } from '../admission-diagnosis.types';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 校验入院诊断表单是否已选完整（西医单条 / 中医一病一症）。 */
export function validateAdmissionDiagnosisFormState(
  state: AdmissionDiagnosisFormState,
): string | null {
  if (state.readonlyDisplay?.trim()) {
    return null;
  }
  if (state.medicineType === 'western') {
    if (!preserveApiIdForRequest(state.westernDefinitionId)) {
      return '请选择入院诊断';
    }
    return null;
  }
  if (!preserveApiIdForRequest(state.tcmDiseaseDefinitionId)) {
    return '请选择中医疾病';
  }
  if (!preserveApiIdForRequest(state.tcmSyndromeDefinitionId)) {
    return '请选择中医证候';
  }
  return null;
}

/**
 * 组装入院诊断提交行。
 * 西医：单条主诊断；中医：一病一症两条，共用 {@code tcmFlag=true}，组号由后端分配。
 * 诊断描述仅当用户填写时提交，不回落目录名。
 */
export function buildAdmissionDiagnosesFromFormState(
  state: AdmissionDiagnosisFormState,
  options: {
    prefilledConditionId?: string;
    prefilledWesternDefinitionId?: string;
  } = {},
): AdmissionDiagnosisSubmitDto[] {
  if (state.readonlyDisplay?.trim()) {
    return [];
  }

  const userDescription = state.diagnosisDescription.trim();

  if (state.medicineType === 'tcm') {
    const diseaseDefinitionId = preserveApiIdForRequest(state.tcmDiseaseDefinitionId);
    const syndromeDefinitionId = preserveApiIdForRequest(state.tcmSyndromeDefinitionId);
    if (!diseaseDefinitionId || !syndromeDefinitionId) {
      return [];
    }
    const descriptionPayload = userDescription ? { description: userDescription } : {};
    return [
      {
        primaryFlag: true,
        conditionDefinitionId: diseaseDefinitionId,
        tcmFlag: true,
        ...descriptionPayload,
      },
      {
        primaryFlag: false,
        conditionDefinitionId: syndromeDefinitionId,
        tcmFlag: true,
        ...descriptionPayload,
      },
    ];
  }

  const conditionDefinitionId = preserveApiIdForRequest(state.westernDefinitionId);
  if (!conditionDefinitionId) {
    return [];
  }
  const prefilledDefId = preserveApiIdForRequest(options.prefilledWesternDefinitionId);
  const conditionId =
    prefilledDefId === conditionDefinitionId
      ? preserveApiIdForRequest(options.prefilledConditionId)
      : undefined;
  return [
    {
      primaryFlag: true,
      conditionDefinitionId,
      tcmFlag: false,
      ...(conditionId ? { conditionId } : {}),
      ...(userDescription ? { description: userDescription } : {}),
    },
  ];
}
