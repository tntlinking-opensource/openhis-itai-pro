/** 住院腕带 PrintData（与 ip-wristband-print 模板 field 一致） */
export interface InpatientWristbandPrintData {
  patientName: string;
  genderEnumText: string;
  ageText: string;
  departmentName: string;
  bedNo: string;
  inpatientNo: string;
  allergyText: string;
  diagnosisText: string;
}

/** 住院腕带 mapper 入参 */
export interface InpatientWristbandPrintSource {
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  departmentName?: string;
  bedNo?: string;
  inpatientNo?: string;
  allergyText?: string;
  diagnosisText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 住院腕带 → PrintData */
export function buildInpatientWristbandPrintData(
  source: InpatientWristbandPrintSource,
): InpatientWristbandPrintData {
  return {
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    departmentName: str(source.departmentName),
    bedNo: str(source.bedNo),
    inpatientNo: str(source.inpatientNo),
    allergyText: str(source.allergyText) || '无',
    diagnosisText: str(source.diagnosisText),
  };
}
