import type { ConditionDefinitionRowResponse } from '#/api/doctor-station/diagnosis.types';
import type { TcmCatalogRole, TcmDiagnosisCatalogRowDto } from '#/api/doctor-station/tcm.types';

/** 中医目录来源：疾病 / 证候（与术语码 tcm_dx / tcm_syndrome 对齐） */
export const TCM_CATALOG_SOURCE_DISEASE = 'tcm_dx';
export const TCM_CATALOG_SOURCE_SYNDROME = 'tcm_syndrome';

export type TcmCatalogKind = 'disease' | 'syndrome';

export interface TcmDiagnosisPick {
  conditionDefinitionId: string;
  conditionCode: string;
  conditionName: string;
}

/** 中医诊断列表标准回显：疾病（证候） */
export function formatTcmDiagnosisDisplayName(
  diseaseName?: null | string,
  syndromeName?: null | string,
): string {
  const disease = (diseaseName ?? '').trim();
  const syndrome = (syndromeName ?? '').trim();
  if (disease && syndrome) return `${disease}（${syndrome}）`;
  return disease || syndrome;
}

/** 检索入参：中医疾病/证候子类映射为后端 diagnosisKindCode */
export function resolveTcmDiagnosisKindCode(catalogKind: TcmCatalogKind): '5' | '6' {
  return catalogKind === 'syndrome' ? '6' : '5';
}

/** 检索入参：UI 疾病/证候子类映射为 IF-DOR-31 catalogRole */
export function resolveTcmCatalogRole(catalogKind: TcmCatalogKind): TcmCatalogRole {
  return catalogKind === 'syndrome' ? 'SYNDROME' : 'DISEASE';
}

/** 中医目录行 → 诊断定义检索行（供诊断弹窗表格复用；可带类型文案） */
export function mapTcmCatalogRowToConditionDefinitionRow(
  row: TcmDiagnosisCatalogRowDto,
  catalogKind?: TcmCatalogKind,
): ConditionDefinitionRowResponse {
  const kindText =
    catalogKind === 'syndrome' ? '证候' : catalogKind === 'disease' ? '疾病' : undefined;
  return {
    conditionDefinitionId: row.definitionId ?? undefined,
    conditionCode: row.conditionCode ?? undefined,
    conditionName: row.conditionName ?? undefined,
    medicineType: 'TCM',
    ybNo: row.ybNo ?? undefined,
    // 列表「类型」列：优先用入参角色，其次目录 sourceText
    sourceEnumText: kindText || String(row.sourceText ?? '').trim() || undefined,
  };
}

/** 由目录行 / 来源文案解析疾病或证候（合并列表点选用） */
export function resolveTcmCatalogKindFromRow(
  row: Pick<ConditionDefinitionRowResponse, 'sourceEnumText'> & {
    tcmCatalogKind?: TcmCatalogKind;
    sourceCode?: null | string;
  },
): TcmCatalogKind {
  if (row.tcmCatalogKind === 'disease' || row.tcmCatalogKind === 'syndrome') {
    return row.tcmCatalogKind;
  }
  const sourceCode = String(row.sourceCode ?? '').trim();
  if (sourceCode === TCM_CATALOG_SOURCE_SYNDROME) return 'syndrome';
  if (sourceCode === TCM_CATALOG_SOURCE_DISEASE) return 'disease';
  const text = String(row.sourceEnumText ?? '').trim();
  if (text.includes('证')) return 'syndrome';
  return 'disease';
}

/** 是否已有有效医保诊断编码（用于无对照标红）。 */
export function hasDiagnosisYbNo(ybNo?: null | string): boolean {
  return Boolean((ybNo ?? '').trim());
}

/** 诊断目录医保编码展示：有值原样，无值显示「未对码」 */
export function formatDiagnosisYbNoDisplay(ybNo?: null | string): string {
  const text = (ybNo ?? '').trim();
  if (text) return text;
  return '未对码';
}

/** 从已保存的合并名称中粗分解析疾病与证候（仅用于表单回显） */
export function parseTcmDiagnosisDisplayName(combined?: null | string): {
  diseaseName: string;
  syndromeName: string;
} {
  const text = (combined ?? '').trim();
  const match = text.match(/^(.+?)（(.+)）$/u);
  if (!match) {
    return { diseaseName: text, syndromeName: '' };
  }
  return { diseaseName: (match[1] ?? '').trim(), syndromeName: (match[2] ?? '').trim() };
}
