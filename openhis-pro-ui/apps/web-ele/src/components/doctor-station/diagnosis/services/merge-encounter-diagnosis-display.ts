import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import {
  formatTcmDiagnosisDisplayName,
  TCM_CATALOG_SOURCE_DISEASE,
  TCM_CATALOG_SOURCE_SYNDROME,
} from './tcm-diagnosis-display';

/** 是否中医诊断行 */
function isTcmDiagnosisRow(row: EncounterDiagnosisRowDto): boolean {
  const kind = String(row.medicineType ?? row.diagnosisKind ?? '')
    .trim()
    .toUpperCase();
  return kind === 'TCM';
}

/** 是否中医病名目录来源 */
function isTcmDiseaseSource(sourceEnum?: null | string): boolean {
  return String(sourceEnum ?? '').trim() === TCM_CATALOG_SOURCE_DISEASE;
}

/** 是否中医证候目录来源 */
function isTcmSyndromeSource(sourceEnum?: null | string): boolean {
  return String(sourceEnum ?? '').trim() === TCM_CATALOG_SOURCE_SYNDROME;
}

/** 将同组病名/证候两行合并为列表展示行 */
function mergeTcmGroupRows(
  groupKey: string,
  members: EncounterDiagnosisRowDto[],
): EncounterDiagnosisRowDto | null {
  if (members.length === 0) return null;
  if (members.length === 1) {
    return { ...members[0], tcmSyndromeGroup: groupKey };
  }

  let diseaseRow: EncounterDiagnosisRowDto | undefined;
  let syndromeRow: EncounterDiagnosisRowDto | undefined;
  for (const row of members) {
    if (isTcmDiseaseSource(row.sourceEnum)) {
      diseaseRow = row;
      continue;
    }
    if (isTcmSyndromeSource(row.sourceEnum)) {
      syndromeRow = row;
    }
  }
  if (!diseaseRow) {
    diseaseRow = members[0];
  }
  if (!syndromeRow) {
    syndromeRow = members.find((row) => row !== diseaseRow);
  }

  const diseaseName = diseaseRow?.conditionDefinitionName ?? '';
  const syndromeName = syndromeRow?.conditionDefinitionName ?? '';
  const displayName =
    formatTcmDiagnosisDisplayName(diseaseName, syndromeName) || diseaseName || syndromeName;

  return {
    ...diseaseRow,
    conditionDefinitionName: displayName,
    conditionCode: diseaseRow?.conditionCode ?? syndromeRow?.conditionCode,
    conditionDefinitionId: diseaseRow?.conditionDefinitionId,
    conditionId: diseaseRow?.conditionId,
    encounterDiagnosisId: diseaseRow?.encounterDiagnosisId,
    tcmSyndromeGroup: groupKey,
    medicineType: 'TCM',
    diagnosisKind: 'TCM',
    diagnosisKindText: diseaseRow?.diagnosisKindText ?? '中医',
    mainDiagnosisFlag: diseaseRow?.mainDiagnosisFlag ?? syndromeRow?.mainDiagnosisFlag,
    diagnosisSortNo: diseaseRow?.diagnosisSortNo ?? syndromeRow?.diagnosisSortNo,
    medicalTypeCode: diseaseRow?.medicalTypeCode ?? syndromeRow?.medicalTypeCode,
    diagnosisDesc: diseaseRow?.diagnosisDesc ?? syndromeRow?.diagnosisDesc,
    syndromeConditionId: syndromeRow?.conditionId,
    syndromeDefinitionId: syndromeRow?.conditionDefinitionId,
  };
}

/**
 * 就诊诊断列表展示合并：西医原样；中医按 {@code tcmSyndromeGroup} 合并为一行。
 */
export function mergeEncounterDiagnosisListForDisplay(
  rows: EncounterDiagnosisRowDto[],
): EncounterDiagnosisRowDto[] {
  const western: EncounterDiagnosisRowDto[] = [];
  const tcmByGroup = new Map<string, EncounterDiagnosisRowDto[]>();
  const tcmOrphans: EncounterDiagnosisRowDto[] = [];

  for (const row of rows) {
    if (!isTcmDiagnosisRow(row)) {
      western.push(row);
      continue;
    }
    const groupKey = String(row.tcmSyndromeGroup ?? '').trim();
    if (!groupKey) {
      tcmOrphans.push(row);
      continue;
    }
    const bucket = tcmByGroup.get(groupKey) ?? [];
    bucket.push(row);
    tcmByGroup.set(groupKey, bucket);
  }

  const mergedTcm: EncounterDiagnosisRowDto[] = [];
  for (const [groupKey, members] of tcmByGroup) {
    const merged = mergeTcmGroupRows(groupKey, members);
    if (merged) mergedTcm.push(merged);
  }
  mergedTcm.push(...tcmOrphans);

  return [...western, ...mergedTcm].sort(
    (a, b) => (a.diagnosisSortNo ?? 0) - (b.diagnosisSortNo ?? 0),
  );
}
