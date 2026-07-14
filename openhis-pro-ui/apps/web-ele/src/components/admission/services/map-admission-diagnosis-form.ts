import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import type { AdmissionDiagnosisFormState } from '../admission-diagnosis.types';
import { createEmptyAdmissionDiagnosisFormState } from '../admission-diagnosis.types';

import {
  isTcmDiagnosisRow,
  mergeEncounterDiagnosisListForDisplay,
} from '#/components/doctor-station/diagnosis/services/merge-encounter-diagnosis-display';
import {
  TCM_CATALOG_SOURCE_DISEASE,
  TCM_CATALOG_SOURCE_SYNDROME,
} from '#/components/doctor-station/diagnosis/services/tcm-diagnosis-display';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 是否中医病名目录来源 */
function isTcmDiseaseSource(sourceEnum?: null | string): boolean {
  return String(sourceEnum ?? '').trim() === TCM_CATALOG_SOURCE_DISEASE;
}

/** 是否中医证候目录来源 */
function isTcmSyndromeSource(sourceEnum?: null | string): boolean {
  return String(sourceEnum ?? '').trim() === TCM_CATALOG_SOURCE_SYNDROME;
}

/** 从就诊诊断行解析用户填写的诊断描述（不含目录名回退）。 */
export function resolveUserDiagnosisDescription(row: EncounterDiagnosisRowDto): string {
  return row.diagnosisDesc?.trim() ?? '';
}

/**
 * 门诊/住院就诊诊断行 → 入院诊断表单（预填或编辑）。
 * 中医按 {@code tcmSyndromeGroup} 合并后再映射一病一症；诊断描述仅回显用户填写项。
 */
export function mapEncounterDiagnosisToAdmissionFormState(
  primary: EncounterDiagnosisRowDto,
  allRows: EncounterDiagnosisRowDto[],
): AdmissionDiagnosisFormState {
  const base = createEmptyAdmissionDiagnosisFormState();
  base.diagnosisDescription = resolveUserDiagnosisDescription(primary);
  base.conditionId = preserveApiIdForRequest(primary.conditionId) || undefined;

  if (isTcmDiagnosisRow(primary)) {
    const groupKey = String(primary.tcmSyndromeGroup ?? '').trim();
    const members = groupKey
      ? allRows.filter((row) => String(row.tcmSyndromeGroup ?? '').trim() === groupKey)
      : [primary];

    let diseaseRow = members.find((row) => isTcmDiseaseSource(row.sourceEnum));
    let syndromeRow = members.find((row) => isTcmSyndromeSource(row.sourceEnum));

    if (!diseaseRow && primary.syndromeDefinitionId) {
      diseaseRow = primary;
      syndromeRow =
        members.find(
          (row) =>
            preserveApiIdForRequest(row.conditionDefinitionId) ===
            preserveApiIdForRequest(primary.syndromeDefinitionId),
        ) ?? undefined;
    }
    if (!syndromeRow && primary.syndromeDefinitionId) {
      syndromeRow = members.find(
        (row) =>
          preserveApiIdForRequest(row.conditionDefinitionId) ===
          preserveApiIdForRequest(primary.syndromeDefinitionId),
      );
    }

    diseaseRow = diseaseRow ?? primary;

    return {
      ...base,
      medicineType: 'tcm',
      tcmDiseaseDefinitionId:
        preserveApiIdForRequest(
          diseaseRow?.conditionDefinitionId ?? primary.conditionDefinitionId,
        ) ?? '',
      tcmSyndromeDefinitionId:
        preserveApiIdForRequest(
          syndromeRow?.conditionDefinitionId ?? primary.syndromeDefinitionId,
        ) ?? '',
      tcmDiseaseName: diseaseRow?.conditionDefinitionName?.trim() ?? '',
      tcmSyndromeName: syndromeRow?.conditionDefinitionName?.trim() ?? '',
    };
  }

  const defId = preserveApiIdForRequest(primary.conditionDefinitionId);
  return {
    ...base,
    medicineType: 'western',
    westernDefinitionId: defId ?? '',
  };
}

/** 合并中医病证组后取主诊断行，供办理住院/登记预填。 */
export function pickPrimaryEncounterDiagnosisForAdmission(
  rows: EncounterDiagnosisRowDto[],
): EncounterDiagnosisRowDto | null {
  if (rows.length === 0) return null;
  const merged = mergeEncounterDiagnosisListForDisplay(rows);
  return (
    merged.find((row) => row.mainDiagnosisFlag === 1) ??
    merged.find((row) => String(row.mainDiagnosisFlag) === '1') ??
    merged[0] ??
    null
  );
}
