import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

/** 处方打印表格行（字段与模板 medicationLines 列一致） */
export interface OutpatientPrescriptionMedicationLine {
  medicationDisplayLeft: string;
  medicationDisplayRight: string;
  medicationLineType?: 'usage';
}

/** 门诊处方 PrintData（字段与 pageEncounterMedicationDispenseSlips 回补字段 + 模板 field 一致） */
export interface OutpatientPrescriptionPrintData {
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
  medicationLines: OutpatientPrescriptionMedicationLine[];
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

/** 将同一处方号下的 slip 行转为 medicationLines */
function buildMedicationLines(
  rows: EncounterMedicationDispenseSlipRowDto[],
): OutpatientPrescriptionMedicationLine[] {
  return rows.flatMap((row) => {
    const lines: OutpatientPrescriptionMedicationLine[] = [
      {
        medicationDisplayLeft: str(row.medicationDisplayLeft),
        medicationDisplayRight: str(row.medicationDisplayRight),
      },
    ];
    const usage = str(row.medicationUsageDisplayText);
    if (usage) {
      lines.push({
        medicationDisplayLeft: usage,
        medicationDisplayRight: '',
        medicationLineType: 'usage',
      });
    }
    return lines;
  });
}

/** 门诊处方：同处方号 slip 行列表 → PrintData */
export function buildOutpatientPrescriptionPrintData(
  rows: EncounterMedicationDispenseSlipRowDto[],
): OutpatientPrescriptionPrintData {
  const head = rows[0];
  if (!head) {
    return {
      prescriptionNo: '',
      hospitalName: '',
      prescriptionTypeLine1: '',
      prescriptionTypeLine2: '',
      prescriptionDocumentTitle: '处方笺',
      patientName: '',
      ageText: '',
      genderEnumText: '',
      orderingDepartmentName: '',
      feeNatureText: '',
      prescriptionDateText: '',
      encounterBusNo: '',
      patientPhone: '',
      conditionSummaryText: '',
      rpHeading: 'Rp',
      medicationLines: [],
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
    prescriptionDocumentTitle: str(head.prescriptionDocumentTitle) || '处方笺',
    patientName: str(head.patientName),
    ageText: str(head.ageText),
    genderEnumText: str(head.genderEnumText),
    orderingDepartmentName: str(head.orderingDepartmentName),
    feeNatureText: str(head.feeNatureText),
    prescriptionDateText: str(head.prescriptionDateText),
    encounterBusNo: str(head.encounterBusNo),
    patientPhone: str(head.patientPhone),
    conditionSummaryText: str(head.conditionSummaryText),
    rpHeading: 'Rp',
    medicationLines: buildMedicationLines(rows),
    orderingPractitionerName: str(head.orderingPractitionerName),
    pricingPractitionerName: str(head.pricingPractitionerName),
    prescriptionTotalPriceText: str(head.prescriptionTotalPriceText),
    dispensingPractitionerName: str(head.dispensingPractitionerName),
    preparerPractitionerName: str(head.preparerPractitionerName),
  };
}

/** 按处方号聚合 slip 行（保持原顺序） */
export function groupSlipRowsByPrescriptionNo(
  rows: EncounterMedicationDispenseSlipRowDto[],
): EncounterMedicationDispenseSlipRowDto[][] {
  const groups = new Map<string, EncounterMedicationDispenseSlipRowDto[]>();
  const order: string[] = [];
  for (const row of rows) {
    const key = str(row.prescriptionNo) || `dispense:${row.dispenseId}`;
    if (!groups.has(key)) {
      groups.set(key, []);
      order.push(key);
    }
    groups.get(key)?.push(row);
  }
  return order.map((key) => groups.get(key) ?? []).filter((g) => g.length > 0);
}
