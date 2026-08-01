/** 住院检查单 PrintData（与 ip-examApply-print 模板 field 一致） */
export interface InpatientExamApplyItemRow {
  seqNo: string;
  itemName: string;
  quantityText: string;
  remarkText: string;
}

export interface InpatientExamApplyPrintData {
  hospitalName: string;
  documentTitle: string;
  inpatientNo: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  departmentName: string;
  bedNo: string;
  diagnosisText: string;
  examPurposeText: string;
  itemRows: InpatientExamApplyItemRow[];
  performOrgName: string;
  orderingPractitionerName: string;
  orderDateText: string;
}

/** 住院检查单 mapper 入参（P：医嘱执行行 + 患者卡片；C：医院名） */
export interface InpatientExamApplyPrintSource {
  hospitalName?: string;
  inpatientNo?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  departmentName?: string;
  bedNo?: string;
  diagnosisText?: string;
  examPurposeText?: string;
  itemRows?: InpatientExamApplyItemRow[];
  performOrgName?: string;
  orderingPractitionerName?: string;
  orderDateText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 住院检查单 → PrintData */
export function buildInpatientExamApplyPrintData(
  source: InpatientExamApplyPrintSource,
): InpatientExamApplyPrintData {
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: '住院检查单',
    inpatientNo: str(source.inpatientNo),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    departmentName: str(source.departmentName),
    bedNo: str(source.bedNo),
    diagnosisText: str(source.diagnosisText),
    examPurposeText: str(source.examPurposeText),
    itemRows: (source.itemRows ?? []).map((row, index) => ({
      seqNo: str(row.seqNo) || String(index + 1),
      itemName: str(row.itemName),
      quantityText: str(row.quantityText),
      remarkText: str(row.remarkText),
    })),
    performOrgName: str(source.performOrgName),
    orderingPractitionerName: str(source.orderingPractitionerName),
    orderDateText: str(source.orderDateText),
  };
}
