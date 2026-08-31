import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import { fetchEncounterTcmDiagnosisSnapshot } from '#/components/doctor-station/diagnosis/services/fetch-encounter-tcm-diagnosis-text';

import { PRINT_SCENARIO } from './constants/print-scenario';
import { buildOutpatientTcmPrescriptionPrintData } from './mappers/outpatient-tcm-prescription';
import { print, type PrintResult } from './runtime';

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 门诊中药处方打印：前端拉就诊诊断并组装 PrintData（与诊断页展示口径一致） */
export async function printOutpatientTcmPrescription(options: {
  slipRows: EncounterMedicationDispenseSlipRowDto[];
  encounterId: string;
  patientId: string;
  prescriptionNo?: string;
}): Promise<PrintResult> {
  const encounterId = str(options.encounterId);
  const patientId = str(options.patientId);
  const diagnosisSnapshot = await fetchEncounterTcmDiagnosisSnapshot(encounterId, patientId);
  const printData = buildOutpatientTcmPrescriptionPrintData({
    slipRows: options.slipRows,
    encounterDiagnosisRows: diagnosisSnapshot.encounterDiagnosisRows,
    tcmDiagnosisGroups: diagnosisSnapshot.tcmDiagnosisGroups,
  });
  const prescriptionNo = str(options.prescriptionNo);
  return print({
    scenarioCode: PRINT_SCENARIO.outpatientTcmRx,
    printData,
    bizRef: {
      scenarioCode: PRINT_SCENARIO.outpatientTcmRx,
      encounterId: encounterId || undefined,
      prescriptionNo: prescriptionNo || undefined,
    },
  });
}
