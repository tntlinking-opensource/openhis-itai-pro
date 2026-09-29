import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import { isTcmDiagnosisRow } from '#/components/doctor-station/diagnosis/services/merge-encounter-diagnosis-display';

/** 诊断带入行（弹窗勾选后的展示/插入单元） */
export interface DiagnosisImportItem {
  /** 稳定键，用于勾选 */
  key: string;
  /** 西医 / 中医 */
  kind: 'TCM' | 'WESTERN';
  /** 列表展示主文案 */
  title: string;
  /** 编码（西医） */
  code?: string;
  /** 是否主诊 */
  mainDiagnosis?: boolean;
  /** 住院诊断类型文案 */
  iptDiseaseTypeText?: string;
  /** 说明 */
  diagnosisDesc?: string;
  /** 排序号 */
  sortNo: number;
  /** 插入用正文行（不含序号） */
  lineText: string;
}

function trimText(v: unknown): string {
  if (v === null || v === undefined) return '';
  return String(v).trim();
}

function diagnosisRowKey(row: EncounterDiagnosisRowDto, index: number): string {
  return (
    trimText(row.encounterDiagnosisId) ||
    trimText(row.conditionId) ||
    trimText(row.tcmSyndromeGroup) ||
    trimText(row.conditionDefinitionId) ||
    `dx-${index}`
  );
}

/**
 * 术语目录名常带「(可选词：同义词)」。弹窗列表可保留；插入正文按 BR-IMPORT-DX-01 去掉。
 */
function stripOptionalWordAlias(name: string): string {
  return name
    .replace(/[（(]可选词[：:][^）)]*[）)]/g, '')
    .replace(/[ \t]{2,}/g, ' ')
    .trim();
}

/** 中医展示名「病（证）」→「病 — 证」；已是「病 — 证」则原样 */
function formatTcmImportName(displayName: string): string {
  const name = displayName.trim();
  if (!name) return '';
  if (name.includes(' — ')) return name;
  const m = name.match(/^(.+?)（(.+)）$/);
  if (m?.[1] && m[2]) {
    return `${m[1].trim()} — ${m[2].trim()}`;
  }
  return name;
}

function buildWesternLineText(row: EncounterDiagnosisRowDto): string {
  const name = stripOptionalWordAlias(trimText(row.conditionDefinitionName)) || '未命名诊断';
  const code = trimText(row.conditionCode);
  const typeText = trimText(row.iptDiseaseTypeText);
  const desc = trimText(row.diagnosisDesc);
  const main = Number(row.mainDiagnosisFlag) === 1;
  let line = name;
  if (code) line += `（${code}）`;
  if (typeText) line += `[${typeText}]`;
  if (main) line += '（主诊）';
  if (desc) line += `（${desc}）`;
  return line;
}

function buildTcmLineText(row: EncounterDiagnosisRowDto): string {
  const name = formatTcmImportName(
    stripOptionalWordAlias(trimText(row.conditionDefinitionName)) || '未命名诊断',
  );
  const typeText = trimText(row.iptDiseaseTypeText);
  const desc = trimText(row.diagnosisDesc);
  const main = Number(row.mainDiagnosisFlag) === 1;
  let line = name;
  if (typeText) line += `[${typeText}]`;
  if (main) line += '（主诊）';
  if (desc) line += `（${desc}）`;
  return line;
}

/** 将就诊诊断展示行转为带入勾选项（调用方应已做中医合并） */
export function buildDiagnosisImportItems(rows: EncounterDiagnosisRowDto[]): DiagnosisImportItem[] {
  const items: DiagnosisImportItem[] = rows.map((row, index) => {
    const tcm = isTcmDiagnosisRow(row);
    return {
      key: diagnosisRowKey(row, index),
      kind: tcm ? 'TCM' : 'WESTERN',
      title: trimText(row.conditionDefinitionName) || '未命名诊断',
      code: trimText(row.conditionCode) || undefined,
      mainDiagnosis: Number(row.mainDiagnosisFlag) === 1,
      iptDiseaseTypeText: trimText(row.iptDiseaseTypeText) || undefined,
      diagnosisDesc: trimText(row.diagnosisDesc) || undefined,
      sortNo: Number(row.diagnosisSortNo ?? 0) || 0,
      lineText: tcm ? buildTcmLineText(row) : buildWesternLineText(row),
    };
  });

  return items.toSorted((a, b) => {
    if (a.mainDiagnosis !== b.mainDiagnosis) {
      return a.mainDiagnosis ? -1 : 1;
    }
    if (a.sortNo !== b.sortNo) return a.sortNo - b.sortNo;
    return a.key.localeCompare(b.key);
  });
}

/**
 * BR-IMPORT-DX-01：勾选诊断 → 多行纯文本。
 * 西医/中医分组标题始终输出（某侧无行时仅保留标题，便于空诊断栏默认带出）。
 */
export function formatDiagnosisImportText(items: DiagnosisImportItem[]): string {
  const western = items.filter((i) => i.kind === 'WESTERN');
  const tcm = items.filter((i) => i.kind === 'TCM');
  const blocks: string[] = [];

  const westernLines = western.map((item, i) => `${i + 1}. ${item.lineText}`);
  blocks.push(['西医诊断：', ...westernLines].join('\n'));
  const tcmLines = tcm.map((item, i) => `${i + 1}. ${item.lineText}`);
  blocks.push(['中医诊断：', ...tcmLines].join('\n'));

  return blocks.join('\n');
}
