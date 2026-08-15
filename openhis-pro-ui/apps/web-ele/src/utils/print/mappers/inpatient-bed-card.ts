/** 住院床头卡 PrintData（与 ip-bedCard-print 模板 field 一致） */
export interface InpatientBedCardPrintData {
  patientName: string;
  genderEnumText: string;
  inpatientNo: string;
  ageText: string;
  admissionDateText: string;
  attendingDoctorName: string;
  insuranceCategoryText: string;
}

/** 住院床头卡 mapper 入参 */
export interface InpatientBedCardPrintSource {
  patientName?: string;
  genderEnumText?: string;
  inpatientNo?: string;
  ageText?: string;
  admissionDateText?: string;
  attendingDoctorName?: string;
  insuranceCategoryText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  const text = String(value).trim();
  // 列表占位「—」不当作真实医保/医生文案打印
  if (text === '—' || text === '-') {
    return '';
  }
  return text;
}

/** 住院床头卡 → PrintData */
export function buildInpatientBedCardPrintData(
  source: InpatientBedCardPrintSource,
): InpatientBedCardPrintData {
  return {
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    inpatientNo: str(source.inpatientNo),
    ageText: str(source.ageText),
    admissionDateText: str(source.admissionDateText),
    attendingDoctorName: str(source.attendingDoctorName),
    insuranceCategoryText: str(source.insuranceCategoryText),
  };
}
