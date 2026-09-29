/** 住院采血贴 PrintData（与 ip-specimenBarcode-print 模板 field 一致；30×50mm，勿复用门诊模板） */
export interface InpatientSpecimenBarcodePrintData {
  bedNo: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  itemName: string;
  barcode: string;
  /** 加急字样「急」；非急为空串 */
  urgencyText: string;
}

/**
 * 住院采血贴 mapper 入参
 * P：床号 + 患者信息 + 检验项目名 + 申请主键（条码，禁止 Number）+ 优先级
 */
export interface InpatientSpecimenBarcodePrintSource {
  bedNo?: null | string;
  patientName?: null | string;
  genderEnumText?: null | string;
  ageText?: null | string;
  itemName?: null | string;
  /** 检验项目主键，直接作为条码内容 */
  serviceRequestId?: null | string;
  /** 显式条码优先；未传时由 serviceRequestId 生成 */
  barcode?: null | string;
  /** 医嘱优先级 code（EM=急）；也可直接传展示文案 urgencyText */
  priorityEnum?: null | string;
  urgencyText?: null | string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 住院采血贴 → PrintData */
export function buildInpatientSpecimenBarcodePrintData(
  source: InpatientSpecimenBarcodePrintSource,
): InpatientSpecimenBarcodePrintData {
  const urgencyFromSource = str(source.urgencyText);
  const urgencyText = urgencyFromSource || (str(source.priorityEnum) === 'EM' ? '急' : '');
  return {
    bedNo: str(source.bedNo),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    itemName: str(source.itemName),
    barcode: str(source.barcode) || str(source.serviceRequestId),
    urgencyText,
  };
}
