import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { TcmDiagnosisGroupRowDto } from '#/api/doctor-station/tcm.types';

import type { DiagnosisItem } from './diagnosis.types';

import {
  buildEncounterDiagnosisDisplayList,
  isTcmDiagnosisRow,
  mergeEncounterDiagnosisListForDisplay,
} from './merge-encounter-diagnosis-display';

/** 就诊诊断原始行 → 医嘱/申请单下拉用合并展示行 */
export function resolveEncounterDiagnosisDisplayRows(
  rows: EncounterDiagnosisRowDto[],
): EncounterDiagnosisRowDto[] {
  return mergeEncounterDiagnosisListForDisplay(rows);
}

/** 合并展示行 → 医嘱诊断候选项 */
export function mapEncounterRowToDiagnosisItem(
  row: EncounterDiagnosisRowDto,
): DiagnosisItem | null {
  const name = (row.conditionDefinitionName ?? '').trim();
  if (!name) return null;
  const groupKey = (row.tcmSyndromeGroup ?? '').trim();
  const id =
    (groupKey ? `tcm-group-${groupKey}` : '') ||
    (row.encounterDiagnosisId ?? row.conditionId ?? row.conditionDefinitionId ?? '').trim() ||
    name;
  const code = (row.conditionCode ?? '').trim();
  const mtRaw = String(row.medicineType ?? row.diagnosisKind ?? 'WESTERN').toUpperCase();
  const medicineType = mtRaw === 'TCM' ? 'tcm' : 'western';
  const conditionDefinitionId = (row.conditionDefinitionId ?? '').trim() || undefined;
  return {
    id: id || name,
    code,
    name,
    medicineType,
    ...(conditionDefinitionId ? { conditionDefinitionId } : {}),
  };
}

/** 从合并后的就诊诊断中取主诊断展示名 */
export function pickMainDiagnosisDisplayName(rows: EncounterDiagnosisRowDto[]): string {
  return pickMainDiagnosisFromDisplayRows(resolveEncounterDiagnosisDisplayRows(rows));
}

/**
 * 中药处方等场景：仅取中医主诊断（病证合并展示），不受西医主诊断干扰。
 */
export function pickMainTcmDiagnosisDisplayName(
  rows: EncounterDiagnosisRowDto[],
  tcmGroups: TcmDiagnosisGroupRowDto[] = [],
): string {
  const displayRows = buildEncounterDiagnosisDisplayList(rows, tcmGroups);
  const tcmNamed = displayRows.filter(
    (row) => isTcmDiagnosisRow(row) && (row.conditionDefinitionName ?? '').trim(),
  );
  return pickMainDiagnosisFromDisplayRows(tcmNamed);
}

function pickMainDiagnosisFromDisplayRows(named: EncounterDiagnosisRowDto[]): string {
  if (named.length === 0) return '';
  const mains = named.filter((r) => Number(r.mainDiagnosisFlag) === 1);
  const pool = mains.length > 0 ? mains : named;
  pool.sort((a, b) => Number(a.diagnosisSortNo ?? 0) - Number(b.diagnosisSortNo ?? 0));
  return String(pool[0]?.conditionDefinitionName ?? '').trim();
}

/** 按展示名匹配就诊诊断关联 id（基于合并行） */
export function resolveEncounterDiagnosisIdFromEncounterRows(
  diagnosisName: string,
  encounterRows: EncounterDiagnosisRowDto[],
): string | undefined {
  const name = diagnosisName.trim();
  if (!name || encounterRows.length === 0) return undefined;
  const hit = resolveEncounterDiagnosisDisplayRows(encounterRows).find(
    (r) => (r.conditionDefinitionName ?? '').trim() === name,
  );
  const eid = String(hit?.encounterDiagnosisId ?? '').trim();
  return eid || undefined;
}

/** 按展示名匹配诊断定义 id（基于合并行） */
export function resolveConditionDefinitionIdFromEncounterRows(
  diagnosisName: string,
  encounterRows: EncounterDiagnosisRowDto[],
): string | undefined {
  const name = diagnosisName.trim();
  if (!name || encounterRows.length === 0) return undefined;
  const hit = resolveEncounterDiagnosisDisplayRows(encounterRows).find(
    (r) => (r.conditionDefinitionName ?? '').trim() === name,
  );
  const did = String(hit?.conditionDefinitionId ?? '').trim();
  return did || undefined;
}
