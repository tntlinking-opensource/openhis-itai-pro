/**
 * 慢病相关医保医疗类别码（与处方方头「慢病处方」、诊断「慢病」标识一致）。
 * 14=门诊慢特病 / 140104=门诊慢病 / 140201=门诊特病
 */
const CHRONIC_MEDICAL_TYPE_CODES = new Set(['14', '140104', '140201']);

/** 普通门诊（YbMedType code=11）；保存缺省与空码回落一致 */
export const GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE = '11';

export interface OutpatientDiagnosisMedicalTypeOption {
  label: string;
  value: string;
}

/** 门诊诊断类型下拉兜底（后端 medicalTypeOptions 缺失时） */
export const FALLBACK_OUTPATIENT_DIAGNOSIS_MEDICAL_TYPE_OPTIONS: OutpatientDiagnosisMedicalTypeOption[] =
  [
    { value: GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE, label: '普通门诊' },
    { value: '13', label: '急诊' },
    { value: '14', label: '门诊慢特病' },
    { value: '140104', label: '门诊慢病' },
    { value: '140201', label: '门诊特病' },
    { value: '16', label: '中医特色门诊' },
    { value: '15', label: '特药' },
    { value: '51', label: '生育门诊' },
    { value: '81', label: '意外伤害门诊' },
  ];

/** 备案导入允许的二级医疗类别 */
const OPSP_IMPORT_MEDICAL_TYPE_CODES = new Set(['140104', '140201']);

/** 医疗类别码是否为慢病/慢特病/特病 */
export function isChronicMedicalTypeCode(code: null | number | string | undefined): boolean {
  const normalized = String(code ?? '').trim();
  return Boolean(normalized) && CHRONIC_MEDICAL_TYPE_CODES.has(normalized);
}

/**
 * 诊断角标：门诊慢病「慢」、门诊特病「特」、一级慢特病「慢特」；其它为空。
 */
export function resolveChronicMedicalTypeTag(code: null | number | string | undefined): string {
  const med = String(code ?? '').trim();
  if (med === '140201') {
    return '特';
  }
  if (med === '140104') {
    return '慢';
  }
  if (med === '14') {
    return '慢特';
  }
  return '';
}

/**
 * 慢特病备案导入医疗类别：特病 140201 原样；其余（含空）默认门诊慢病 140104。
 * 与后端 5301 主查 + 5302A 打标缺省规则对齐。
 */
export function resolveOpspMedicalTypeCode(code?: null | string): string {
  const med = String(code ?? '').trim();
  if (med === '140201') {
    return '140201';
  }
  if (OPSP_IMPORT_MEDICAL_TYPE_CODES.has(med)) {
    return med;
  }
  return '140104';
}
