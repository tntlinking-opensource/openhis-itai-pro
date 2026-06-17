import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

/** 术语「药品大类」中麻精一类相关 code（含 legacy 数值 id） */
const NARCOTIC_CATEGORY_CODES = new Set(['7', '297', '2832', '2834']);

const NARCOTIC_CATEGORY_TEXT_PATTERN = /麻[,、]?精一|麻醉药品|精神药品第一类|第一类精神|精一处方/;

export function isNarcoticSlipRow(
  row: Pick<
    EncounterMedicationDispenseSlipRowDto,
    'medicationCategoryCode' | 'medicationCategoryText'
  >,
): boolean {
  const code = row.medicationCategoryCode?.trim();
  if (code && NARCOTIC_CATEGORY_CODES.has(code)) {
    return true;
  }
  const text = row.medicationCategoryText?.trim() ?? '';
  if (!text) {
    return false;
  }
  return NARCOTIC_CATEGORY_TEXT_PATTERN.test(text) || text.includes('麻、精一');
}
