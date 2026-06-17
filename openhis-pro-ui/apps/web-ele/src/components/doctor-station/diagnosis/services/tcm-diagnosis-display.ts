import type { ConditionDefinitionRowResponse } from '#/api/doctor-station/diagnosis.types';
import type { TcmCatalogRole, TcmDiagnosisCatalogRowDto } from '#/api/doctor-station/tcm.types';

/** 中医目录来源：疾病 / 证候（与术语码 tcm_dx / tcm_syndrome 对齐） */
export const TCM_CATALOG_SOURCE_DISEASE = 'tcm_dx';
export const TCM_CATALOG_SOURCE_SYNDROME = 'tcm_syndrome';

/** @deprecated 使用 {@link TCM_CATALOG_SOURCE_DISEASE} */
export const TCM_CONDITION_SOURCE_DISEASE = 5;
/** @deprecated 使用 {@link TCM_CATALOG_SOURCE_SYNDROME} */
export const TCM_CONDITION_SOURCE_SYNDROME = 6;

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

/** 中医目录行 → 诊断定义检索行（供诊断弹窗表格复用） */
export function mapTcmCatalogRowToConditionDefinitionRow(
  row: TcmDiagnosisCatalogRowDto,
): ConditionDefinitionRowResponse {
  return {
    conditionDefinitionId: row.definitionId ?? undefined,
    conditionCode: row.conditionCode ?? undefined,
    conditionName: row.conditionName ?? undefined,
    medicineType: 'TCM',
  };
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
