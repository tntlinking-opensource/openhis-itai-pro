import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';
import type { TcmDiagnosisGroupRowDto } from '#/api/doctor-station/tcm.types';

import { pickMainTcmDiagnosisDisplayName } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import {
  buildTcmHerbGridRowsFromSlipRows,
  buildTcmPrescriptionUsageTextFromSlipHead,
  type TcmHerbGridDisplayRow,
} from '#/utils/tcm-prescription-display';

/** 中药处方饮片表格行（与模板 tcmHerbRows 列一致） */
export type OutpatientTcmPrescriptionHerbGridRow = TcmHerbGridDisplayRow;

/** 门诊中药处方 PrintData（字段与模板 field 一致） */
export interface OutpatientTcmPrescriptionPrintData {
  prescriptionNo: string;
  hospitalName: string;
  prescriptionTypeLine1: string;
  prescriptionTypeLine2: string;
  prescriptionDocumentTitle: string;
  patientName: string;
  ageText: string;
  genderEnumText: string;
  orderingDepartmentName: string;
  feeNatureText: string;
  prescriptionDateText: string;
  encounterBusNo: string;
  patientPhone: string;
  conditionSummaryText: string;
  rpHeading: string;
  tcmHerbRows: OutpatientTcmPrescriptionHerbGridRow[];
  tcmUsageText: string;
  orderingPractitionerName: string;
  pricingPractitionerName: string;
  prescriptionTotalPriceText: string;
  dispensingPractitionerName: string;
  preparerPractitionerName: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 中药处方打印源：slip 行 + 就诊诊断（诊断展示由前端合并，与诊断页一致） */
export interface OutpatientTcmPrescriptionPrintSource {
  slipRows: EncounterMedicationDispenseSlipRowDto[];
  encounterDiagnosisRows?: EncounterDiagnosisRowDto[];
  tcmDiagnosisGroups?: TcmDiagnosisGroupRowDto[];
}

function resolveSlipRows(
  source: EncounterMedicationDispenseSlipRowDto[] | OutpatientTcmPrescriptionPrintSource,
): EncounterMedicationDispenseSlipRowDto[] {
  if (Array.isArray(source)) return source;
  return source.slipRows;
}

function resolveConditionSummaryText(
  source: EncounterMedicationDispenseSlipRowDto[] | OutpatientTcmPrescriptionPrintSource,
): string {
  if (Array.isArray(source)) return '';
  const rows = source.encounterDiagnosisRows ?? [];
  const tcmGroups = source.tcmDiagnosisGroups ?? [];
  return pickMainTcmDiagnosisDisplayName(rows, tcmGroups);
}

function buildTcmHerbRows(rows: EncounterMedicationDispenseSlipRowDto[]): TcmHerbGridDisplayRow[] {
  return buildTcmHerbGridRowsFromSlipRows(rows);
}

function buildTcmUsageText(head: EncounterMedicationDispenseSlipRowDto): string {
  return buildTcmPrescriptionUsageTextFromSlipHead(head);
}

/** 门诊中药处方：slip 行（+ 可选就诊诊断）→ PrintData */
export function buildOutpatientTcmPrescriptionPrintData(
  source: EncounterMedicationDispenseSlipRowDto[] | OutpatientTcmPrescriptionPrintSource,
): OutpatientTcmPrescriptionPrintData {
  const rows = resolveSlipRows(source);
  const head = rows[0];
  if (!head) {
    return {
      prescriptionNo: '',
      hospitalName: '',
      prescriptionTypeLine1: '',
      prescriptionTypeLine2: '',
      prescriptionDocumentTitle: '中药处方笺',
      patientName: '',
      ageText: '',
      genderEnumText: '',
      orderingDepartmentName: '',
      feeNatureText: '',
      prescriptionDateText: '',
      encounterBusNo: '',
      patientPhone: '',
      conditionSummaryText: '',
      rpHeading: 'Rp :',
      tcmHerbRows: [],
      tcmUsageText: '',
      orderingPractitionerName: '',
      pricingPractitionerName: '',
      prescriptionTotalPriceText: '',
      dispensingPractitionerName: '',
      preparerPractitionerName: '',
    };
  }
  return {
    prescriptionNo: str(head.prescriptionNo),
    hospitalName: str(head.hospitalName),
    prescriptionTypeLine1: str(head.prescriptionTypeLine1),
    prescriptionTypeLine2: str(head.prescriptionTypeLine2),
    prescriptionDocumentTitle: str(head.prescriptionDocumentTitle) || '中药处方笺',
    patientName: str(head.patientName),
    ageText: str(head.ageText),
    genderEnumText: str(head.genderEnumText),
    orderingDepartmentName: str(head.orderingDepartmentName),
    feeNatureText: str(head.feeNatureText),
    prescriptionDateText: str(head.prescriptionDateText),
    encounterBusNo: str(head.encounterBusNo),
    patientPhone: str(head.patientPhone),
    conditionSummaryText: resolveConditionSummaryText(source),
    rpHeading: 'Rp :',
    tcmHerbRows: buildTcmHerbRows(rows),
    tcmUsageText: buildTcmUsageText(head),
    orderingPractitionerName: str(head.orderingPractitionerName),
    pricingPractitionerName: str(head.pricingPractitionerName),
    prescriptionTotalPriceText: str(head.prescriptionTotalPriceText),
    dispensingPractitionerName: str(head.dispensingPractitionerName),
    preparerPractitionerName: str(head.preparerPractitionerName),
  };
}

/** 判定 slip 组是否中药处方（仅认 tcmFlag） */
export function isTcmPrescriptionSlipGroup(rows: EncounterMedicationDispenseSlipRowDto[]): boolean {
  return rows.some((row) => row.tcmFlag === true);
}
