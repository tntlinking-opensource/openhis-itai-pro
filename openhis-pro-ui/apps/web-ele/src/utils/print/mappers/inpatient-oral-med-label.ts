/** 住院口服药贴 PrintData（与 ip-oralMedLabel-print 模板 field 一致） */
export interface InpatientOralMedLabelPrintData {
  bedNo: string;
  patientName: string;
  ageText: string;
  drugName: string;
  specText: string;
  doseText: string;
}

/** 住院口服药贴 mapper 入参 */
export interface InpatientOralMedLabelPrintSource {
  bedNo?: string;
  patientName?: string;
  ageText?: string;
  drugName?: string;
  specText?: string;
  doseText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 住院口服药贴 → PrintData */
export function buildInpatientOralMedLabelPrintData(
  source: InpatientOralMedLabelPrintSource,
): InpatientOralMedLabelPrintData {
  return {
    bedNo: str(source.bedNo),
    patientName: str(source.patientName),
    ageText: str(source.ageText),
    drugName: str(source.drugName),
    specText: str(source.specText),
    doseText: str(source.doseText),
  };
}
