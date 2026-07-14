import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import { PRINT_SCENARIO, type PrintScenario } from './constants/print-scenario';
import { isTcmPrescriptionSlipGroup } from './mappers/outpatient-tcm-prescription';

/** 按 slip 组判定门诊处方打印场景（中药/西药分流） */
export function resolveOutpatientPrescriptionPrintScenario(
  rows: EncounterMedicationDispenseSlipRowDto[],
): PrintScenario {
  if (isTcmPrescriptionSlipGroup(rows)) {
    return PRINT_SCENARIO.outpatientTcmRx;
  }
  return PRINT_SCENARIO.outpatientRx;
}
