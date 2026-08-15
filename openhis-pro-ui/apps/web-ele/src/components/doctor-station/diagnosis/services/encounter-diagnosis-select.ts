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

/** 合并展示行 → 医嘱诊断候选项（须有就诊诊断主键，供多选绑定） */
export function mapEncounterRowToDiagnosisItem(
  row: EncounterDiagnosisRowDto,
): DiagnosisItem | null {
  const name = (row.conditionDefinitionName ?? '').trim();
  const encounterDiagnosisId = String(row.encounterDiagnosisId ?? '').trim();
  if (!name || !encounterDiagnosisId) return null;
  const code = (row.conditionCode ?? '').trim();
  const mtRaw = String(row.medicineType ?? row.diagnosisKind ?? 'WESTERN').toUpperCase();
  const medicineType = mtRaw === 'TCM' ? 'tcm' : 'western';
  const conditionDefinitionId = (row.conditionDefinitionId ?? '').trim() || undefined;
  const mainDiagnosisFlag = Number(row.mainDiagnosisFlag) === 1 ? 1 : 0;
  const medicalTypeCode = String(row.medicalTypeCode ?? '').trim() || undefined;
  const tcmSyndromeGroup = String(row.tcmSyndromeGroup ?? '').trim() || undefined;
  return {
    id: encounterDiagnosisId,
    code,
    name,
    medicineType,
    encounterDiagnosisId,
    mainDiagnosisFlag,
    ...(conditionDefinitionId ? { conditionDefinitionId } : {}),
    ...(medicalTypeCode ? { medicalTypeCode } : {}),
    ...(tcmSyndromeGroup ? { tcmSyndromeGroup } : {}),
  };
}

/** 解析有序 ids 串 / 数组为去重 string[]（保序；Long 安全） */
export function parseEncounterDiagnosisIds(raw: string | string[] | null | undefined): string[] {
  if (raw == null) return [];
  const parts = Array.isArray(raw) ? raw : String(raw).split(',');
  const seen = new Set<string>();
  const out: string[] = [];
  for (const part of parts) {
    const id = String(part ?? '').trim();
    if (!id || seen.has(id)) continue;
    seen.add(id);
    out.push(id);
  }
  return out;
}

/** 仍存在于当前就诊诊断中的绑定 ids（保序）。 */
export function filterValidEncounterDiagnosisIds(
  selectedIds: string[] | undefined,
  encounterRows: EncounterDiagnosisRowDto[],
): string[] {
  const validIdSet = new Set(
    resolveEncounterDiagnosisDisplayRows(encounterRows)
      .map((row) => String(row.encounterDiagnosisId ?? '').trim())
      .filter(Boolean),
  );
  return parseEncounterDiagnosisIds(selectedIds).filter((id) => validIdSet.has(id));
}

/** 就诊诊断恰好一条时可默认绑定的 id。 */
export function pickSoleEncounterDiagnosisIds(encounterRows: EncounterDiagnosisRowDto[]): string[] {
  const named = resolveEncounterDiagnosisDisplayRows(encounterRows).filter(
    (row) =>
      (row.conditionDefinitionName ?? '').trim() && String(row.encounterDiagnosisId ?? '').trim(),
  );
  if (named.length !== 1) return [];
  const id = String(named[0]?.encounterDiagnosisId ?? '').trim();
  return id ? [id] : [];
}

/**
 * 开立默认诊断：已选保留 → 会话上次（仍有效）→ 唯一诊断 → 空（多诊断不默认主诊）。
 */
export function resolveSessionDefaultEncounterDiagnosisIds(
  currentIds: string[] | undefined,
  encounterRows: EncounterDiagnosisRowDto[],
  lastSelectedIds?: string[] | undefined,
): string[] {
  const existing = filterValidEncounterDiagnosisIds(currentIds, encounterRows);
  if (existing.length > 0) return existing;
  const lastValid = filterValidEncounterDiagnosisIds(lastSelectedIds, encounterRows);
  if (lastValid.length > 0) return lastValid;
  return pickSoleEncounterDiagnosisIds(encounterRows);
}

/** 主诊优先：选中 id 中主诊排前，其余保持相对顺序 */
export function orderEncounterDiagnosisIdsPrimaryFirst(
  selectedIds: string[],
  encounterRows: EncounterDiagnosisRowDto[],
): string[] {
  const unique = parseEncounterDiagnosisIds(selectedIds);
  if (unique.length <= 1) return unique;
  const display = resolveEncounterDiagnosisDisplayRows(encounterRows);
  const mainIdSet = new Set(
    display
      .filter((row) => Number(row.mainDiagnosisFlag) === 1)
      .map((row) => String(row.encounterDiagnosisId ?? '').trim())
      .filter(Boolean),
  );
  const mains = unique.filter((id) => mainIdSet.has(id));
  const others = unique.filter((id) => !mainIdSet.has(id));
  mains.sort((a, b) => {
    const ra = display.find((row) => String(row.encounterDiagnosisId ?? '').trim() === a);
    const rb = display.find((row) => String(row.encounterDiagnosisId ?? '').trim() === b);
    return Number(ra?.diagnosisSortNo ?? 0) - Number(rb?.diagnosisSortNo ?? 0);
  });
  return [...mains, ...others];
}

/** 默认勾选：主诊 id（无主诊则取排序首条） */
export function pickDefaultEncounterDiagnosisIds(
  encounterRows: EncounterDiagnosisRowDto[],
): string[] {
  const display = resolveEncounterDiagnosisDisplayRows(encounterRows);
  const named = display.filter(
    (row) =>
      (row.conditionDefinitionName ?? '').trim() && String(row.encounterDiagnosisId ?? '').trim(),
  );
  if (named.length === 0) return [];
  const mains = named.filter((row) => Number(row.mainDiagnosisFlag) === 1);
  const pool = mains.length > 0 ? mains : named;
  pool.sort((a, b) => Number(a.diagnosisSortNo ?? 0) - Number(b.diagnosisSortNo ?? 0));
  const id = String(pool[0]?.encounterDiagnosisId ?? '').trim();
  return id ? [id] : [];
}

/** 按有序 ids 拼诊断展示名（顿号） */
export function formatEncounterDiagnosisDisplayNames(
  selectedIds: string[],
  encounterRows: EncounterDiagnosisRowDto[],
): string {
  const display = resolveEncounterDiagnosisDisplayRows(encounterRows);
  const names = selectedIds
    .map((id) => {
      const hit = display.find((row) => String(row.encounterDiagnosisId ?? '').trim() === id);
      return String(hit?.conditionDefinitionName ?? '').trim();
    })
    .filter(Boolean);
  return names.join('、');
}

/** ElSelect 多选 #tag：已选项拼成顿号纯文本（不用 Tag 胶囊） */
export function formatDiagnosisSelectTagText(
  data: Array<{ currentLabel?: null | number | string }> | null | undefined,
): string {
  if (!Array.isArray(data) || data.length === 0) return '';
  return data
    .map((d) => String(d.currentLabel ?? '').trim())
    .filter(Boolean)
    .join('、');
}

/** 所选 ids → 行上绑定字段（主诊优先 + 展示名） */
export function buildEncounterDiagnosisBindingPatch(
  selectedIds: string[],
  encounterRows: EncounterDiagnosisRowDto[],
): { diagnosis: string; encounterDiagnosisIds: string[] } {
  const ordered = orderEncounterDiagnosisIdsPrimaryFirst(selectedIds, encounterRows);
  return {
    encounterDiagnosisIds: ordered,
    diagnosis: formatEncounterDiagnosisDisplayNames(ordered, encounterRows),
  };
}

/** 有序 ids → 逗号分隔串（主诊第一）；空则 undefined */
export function joinEncounterDiagnosisIds(
  selectedIds: string[],
  encounterRows: EncounterDiagnosisRowDto[],
): string | undefined {
  const ordered = orderEncounterDiagnosisIdsPrimaryFirst(selectedIds, encounterRows);
  if (ordered.length === 0) return undefined;
  return ordered.join(',');
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
