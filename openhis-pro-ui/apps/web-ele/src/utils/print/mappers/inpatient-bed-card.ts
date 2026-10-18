/** 住院床头卡 PrintData（与 ip-bedCard-print 模板 field 一致） */
export interface InpatientBedCardPrintData {
  patientName: string;
  ageText: string;
  genderEnumText: string;
  wardName: string;
  roomName: string;
  bedNo: string;
  inpatientNo: string;
  diagnosisText: string;
  /** 入院日期 YYYY-MM-DD（模板 field=admissionDateText） */
  admissionDateText: string;
  admissionTimeText: string;
  nursingLevelText: string;
  attendingDoctorName: string;
  /** 医保类别（模板 field=insuranceCategoryText，来自合同/费用性质） */
  insuranceCategoryText: string;
  primaryNurseName: string;
}

/** 住院床头卡 mapper 入参 */
export interface InpatientBedCardPrintSource {
  patientName?: string;
  ageText?: string;
  genderEnumText?: string;
  wardName?: string;
  roomName?: string;
  bedNo?: string;
  inpatientNo?: string;
  diagnosisText?: string;
  admissionDateText?: string;
  admissionTimeText?: string;
  nursingLevelText?: string;
  attendingDoctorName?: string;
  insuranceCategoryText?: string;
  primaryNurseName?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  const text = String(value).trim();
  // 列表占位「—」不当作真实文案打印
  if (text === '—' || text === '-') {
    return '';
  }
  return text;
}

/** 入院日期：优先 admissionDateText，否则从日期时间取 YYYY-MM-DD */
function admissionDateOf(source: InpatientBedCardPrintSource): string {
  const explicit = str(source.admissionDateText);
  if (explicit) {
    return explicit.length >= 10 ? explicit.slice(0, 10) : explicit;
  }
  const timeText = str(source.admissionTimeText);
  if (timeText.length >= 10 && timeText[4] === '-' && timeText[7] === '-') {
    return timeText.slice(0, 10);
  }
  return timeText;
}

/** 住院床头卡 → PrintData */
export function buildInpatientBedCardPrintData(
  source: InpatientBedCardPrintSource,
): InpatientBedCardPrintData {
  return {
    patientName: str(source.patientName),
    ageText: str(source.ageText),
    genderEnumText: str(source.genderEnumText),
    wardName: str(source.wardName),
    roomName: str(source.roomName),
    bedNo: str(source.bedNo),
    inpatientNo: str(source.inpatientNo),
    diagnosisText: str(source.diagnosisText),
    admissionDateText: admissionDateOf(source),
    admissionTimeText: str(source.admissionTimeText),
    nursingLevelText: str(source.nursingLevelText),
    attendingDoctorName: str(source.attendingDoctorName),
    insuranceCategoryText: str(source.insuranceCategoryText),
    primaryNurseName: str(source.primaryNurseName),
  };
}
