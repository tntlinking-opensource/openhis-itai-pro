import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { TcmDiagnosisGroupRowDto } from '#/api/doctor-station/tcm.types';

import {
  formatTcmDiagnosisDisplayName,
  TCM_CATALOG_SOURCE_DISEASE,
  TCM_CATALOG_SOURCE_SYNDROME,
} from './tcm-diagnosis-display';

/** 是否中医诊断行 */
export function isTcmDiagnosisRow(row: EncounterDiagnosisRowDto): boolean {
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

  return sortEncounterDiagnosisDisplayRows([...western, ...mergedTcm]);
}

/** 按排序号稳定排序展示行 */
export function sortEncounterDiagnosisDisplayRows(
  rows: EncounterDiagnosisRowDto[],
): EncounterDiagnosisRowDto[] {
  return [...rows].sort((a, b) => (a.diagnosisSortNo ?? 0) - (b.diagnosisSortNo ?? 0));
}

/** 中医病证组 → 列表合并展示行（与门诊快照合并口径一致） */
export function mapTcmDiagnosisGroupToDisplayRow(
  group: TcmDiagnosisGroupRowDto,
): EncounterDiagnosisRowDto {
  const displayName = formatTcmDiagnosisDisplayName(group.diseaseName, group.syndromeName);
  return {
    conditionId: group.diseaseConditionId ?? undefined,
    conditionDefinitionId: group.diseaseDefinitionId ?? undefined,
    conditionCode: group.diseaseCode ?? undefined,
    conditionDefinitionName: displayName || group.diagnosisDesc || undefined,
    diagnosisKind: 'TCM',
    diagnosisKindText: '中医',
    medicineType: 'TCM',
    tcmSyndromeGroup: group.tcmSyndromeGroup ?? undefined,
    syndromeConditionId: group.syndromeConditionId ?? undefined,
    syndromeDefinitionId: group.syndromeDefinitionId ?? undefined,
    mainDiagnosisFlag: group.primaryFlag ?? undefined,
    diagnosisSortNo: group.rank ?? undefined,
    medicalTypeCode: group.medicalTypeCode ?? undefined,
    diagnosisDesc: group.diagnosisDesc ?? undefined,
  };
}

/** 用就诊诊断事实行补齐住院诊断类型等就诊级字段 */
function enrichTcmDisplayRowFromEncounterMembers(
  displayRow: EncounterDiagnosisRowDto,
  encounterTcmRows: EncounterDiagnosisRowDto[],
  group: TcmDiagnosisGroupRowDto,
): EncounterDiagnosisRowDto {
  const groupKey = String(group.tcmSyndromeGroup ?? '').trim();
  const diseaseConditionId = String(group.diseaseConditionId ?? '').trim();
  const syndromeConditionId = String(group.syndromeConditionId ?? '').trim();
  const members = encounterTcmRows.filter((row) => {
    const rowGroupKey = String(row.tcmSyndromeGroup ?? '').trim();
    if (groupKey && rowGroupKey && rowGroupKey === groupKey) {
      return true;
    }
    const conditionId = String(row.conditionId ?? '').trim();
    return (
      conditionId && (conditionId === diseaseConditionId || conditionId === syndromeConditionId)
    );
  });
  if (members.length === 0) {
    return displayRow;
  }
  const diseaseMember =
    members.find((row) => isTcmDiseaseSource(row.sourceEnum)) ??
    members.find(
      (row) =>
        String(row.conditionId ?? '').trim() === diseaseConditionId ||
        String(row.conditionDefinitionId ?? '').trim() ===
          String(group.diseaseDefinitionId ?? '').trim(),
    ) ??
    members[0];
  const syndromeMember =
    members.find((row) => isTcmSyndromeSource(row.sourceEnum)) ??
    members.find(
      (row) =>
        String(row.conditionId ?? '').trim() === syndromeConditionId ||
        String(row.conditionDefinitionId ?? '').trim() ===
          String(group.syndromeDefinitionId ?? '').trim(),
    );
  return {
    ...displayRow,
    encounterDiagnosisId: diseaseMember?.encounterDiagnosisId ?? displayRow.encounterDiagnosisId,
    iptDiseaseTypeCode:
      diseaseMember?.iptDiseaseTypeCode ??
      syndromeMember?.iptDiseaseTypeCode ??
      displayRow.iptDiseaseTypeCode,
    iptDiseaseTypeText:
      diseaseMember?.iptDiseaseTypeText ??
      syndromeMember?.iptDiseaseTypeText ??
      displayRow.iptDiseaseTypeText,
  };
}

/**
 * 组装诊断 Tab 展示列表：西医走就诊快照；中医优先病证组接口（住院快照缺组号时与门诊展示对齐）。
 */
export function buildEncounterDiagnosisDisplayList(
  encounterRows: EncounterDiagnosisRowDto[],
  tcmGroups: TcmDiagnosisGroupRowDto[] = [],
): EncounterDiagnosisRowDto[] {
  const westernRows = encounterRows.filter((row) => !isTcmDiagnosisRow(row));
  const encounterTcmRows = encounterRows.filter((row) => isTcmDiagnosisRow(row));

  if (tcmGroups.length > 0) {
    const tcmDisplayRows = tcmGroups.map((group) =>
      enrichTcmDisplayRowFromEncounterMembers(
        mapTcmDiagnosisGroupToDisplayRow(group),
        encounterTcmRows,
        group,
      ),
    );
    return sortEncounterDiagnosisDisplayRows([...westernRows, ...tcmDisplayRows]);
  }

  return mergeEncounterDiagnosisListForDisplay(encounterRows);
}
