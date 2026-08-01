import type { PrescriptionSheetData } from '#/views/outpatient/dispense/components/prescription-sheet.types';

import type {
  OutpatientPrescriptionMedicationLine,
  OutpatientPrescriptionPrintData,
} from './outpatient-prescription';
import type { OutpatientTcmPrescriptionPrintData } from './outpatient-tcm-prescription';

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

function displayOrEmpty(value: null | number | string | undefined): string {
  const text = str(value);
  if (!text || text === '—') return '';
  return text;
}

/** 医生站笺面西药行 → 打印 medicationLines（药名规格 | 数量；用法用量缩进行） */
function buildMedicationLinesFromSheet(
  sheet: PrescriptionSheetData,
): OutpatientPrescriptionMedicationLine[] {
  return (sheet.lines ?? []).flatMap((line) => {
    const name = displayOrEmpty(line.itemName);
    const spec = displayOrEmpty(line.spec);
    const left = [name, spec].filter(Boolean).join(' ');
    const right = displayOrEmpty(line.quantity);
    const lines: OutpatientPrescriptionMedicationLine[] = [
      {
        medicationDisplayLeft: left,
        medicationDisplayRight: right,
      },
    ];
    const usage = displayOrEmpty(line.usage);
    if (usage) {
      const usageText = usage.startsWith('用法用量') ? usage : `用法用量：${usage}`;
      lines.push({
        medicationDisplayLeft: usageText,
        medicationDisplayRight: '',
        medicationLineType: 'usage',
      });
    }
    return lines;
  });
}

/** 笺面公共头尾字段 */
function mapSheetHeaderFields(sheet: PrescriptionSheetData) {
  return {
    prescriptionNo: displayOrEmpty(sheet.prescriptionNo),
    hospitalName: displayOrEmpty(sheet.hospitalName),
    prescriptionTypeLine1: '',
    prescriptionTypeLine2: '',
    patientName: displayOrEmpty(sheet.patientName),
    ageText: displayOrEmpty(sheet.patientAgeLabel),
    genderEnumText: displayOrEmpty(sheet.patientGenderLabel),
    orderingDepartmentName: displayOrEmpty(sheet.deptName),
    feeNatureText: displayOrEmpty(sheet.feeNatureLabel) || '自费',
    prescriptionDateText: displayOrEmpty(sheet.prescriptionDate),
    encounterBusNo: displayOrEmpty(sheet.outpatientNoLabel),
    patientPhone: displayOrEmpty(sheet.patientPhone),
    conditionSummaryText: displayOrEmpty(sheet.diagnosis),
    orderingPractitionerName: displayOrEmpty(sheet.footerDoctorName || sheet.prescribingDoctorName),
    pricingPractitionerName: displayOrEmpty(sheet.footerPricingClerkName),
    prescriptionTotalPriceText: displayOrEmpty(sheet.totalAmountLabel),
    dispensingPractitionerName: displayOrEmpty(sheet.footerDeliverName),
    preparerPractitionerName: displayOrEmpty(sheet.footerDispensingName),
  };
}

/** 医生站处方笺 → 西药 PrintData（与 op-rx-print field 一致） */
export function buildWesternPrintDataFromDoctorSheet(
  sheet: PrescriptionSheetData,
): OutpatientPrescriptionPrintData {
  return {
    ...mapSheetHeaderFields(sheet),
    prescriptionDocumentTitle: '处方笺',
    rpHeading: 'Rp',
    medicationLines: buildMedicationLinesFromSheet(sheet),
  };
}

/**
 * 医生站处方笺 → 中药 PrintData（与 op-tcm-rx-print field 一致）。
 * @param conditionSummaryText 可选覆盖方头诊断（优先于笺面 diagnosis）
 */
export function buildTcmPrintDataFromDoctorSheet(
  sheet: PrescriptionSheetData,
  conditionSummaryText?: string,
): OutpatientTcmPrescriptionPrintData {
  const header = mapSheetHeaderFields(sheet);
  const diagnosisOverride = displayOrEmpty(conditionSummaryText);
  return {
    ...header,
    conditionSummaryText: diagnosisOverride || header.conditionSummaryText,
    prescriptionDocumentTitle: '中药处方笺',
    rpHeading: 'Rp :',
    tcmHerbRows: sheet.tcmHerbGridRows ?? [],
    tcmUsageText: displayOrEmpty(sheet.tcmUsageText),
  };
}

/** 是否中药处方笺 */
export function isDoctorSheetHerbal(sheet: PrescriptionSheetData): boolean {
  return sheet.rxType === 'herbal';
}
