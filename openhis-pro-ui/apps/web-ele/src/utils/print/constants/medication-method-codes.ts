/** 给药途径 method_code（术语表 225；对齐后端 ter_concept） */
export const METHOD_CODE_ORAL = '1';
export const METHOD_CODE_INHALATION = '5';
export const METHOD_CODE_SUBCUTANEOUS = '401';
export const METHOD_CODE_INTRADERMAL = '402';
export const METHOD_CODE_IV_INJECTION = '404';
export const METHOD_CODE_IV_INFUSION = '405';

export const ORAL_METHOD_CODES = new Set<string>(['1', 'PO']);
export const INHALATION_METHOD_CODES = new Set<string>(['5']);
export const INJECTION_METHOD_CODES = new Set<string>([
  '401',
  '402',
  '403',
  '404',
  'IM',
  'IV',
  'SC',
]);
export const INFUSION_METHOD_CODES = new Set<string>(['405', 'IVD']);

export function normalizeMedicationMethodCode(code?: null | string): string {
  return String(code ?? '')
    .trim()
    .toUpperCase();
}
