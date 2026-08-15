/**
 * 纸质处方方头分类（prescriptionTypeLine1/2）。
 * 与发药 slip 的 med_type（encounterMedicationTypeCode）对齐：慢病 →「慢病处方」。
 */

import { isChronicMedicalTypeCode } from '#/utils/chronic-medical-type';

function trimText(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/**
 * 方头第一行：慢病医疗类别覆盖默认「普通处方」；急诊/儿科/麻精等特殊分类保留后端文案。
 */
export function resolvePrescriptionPaperTypeLine1(options: {
  encounterMedicationTypeCode?: null | string;
  prescriptionTypeLine1?: null | string;
}): string {
  const line1 = trimText(options.prescriptionTypeLine1);
  const medTypeCode = trimText(options.encounterMedicationTypeCode);
  if (isChronicMedicalTypeCode(medTypeCode) && (!line1 || line1 === '普通处方')) {
    return '慢病处方';
  }
  return line1;
}

/** 方头第二行：缺省「当日有效」 */
export function resolvePrescriptionPaperTypeLine2(prescriptionTypeLine2?: null | string): string {
  return trimText(prescriptionTypeLine2) || '当日有效';
}
