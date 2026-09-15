/** 住院采血贴 PrintData（与 ip-specimenBarcode-print 模板 field 一致；30×50mm，勿复用门诊模板） */
export interface InpatientSpecimenBarcodePrintData {
  bedNo: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  itemName: string;
  barcode: string;
}

/**
 * 住院采血贴 mapper 入参
 * P：床号 + 患者信息 + 检验项目名 + 申请主键（条码，禁止 Number）
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
  return {
    bedNo: str(source.bedNo),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    itemName: str(source.itemName),
    barcode: str(source.barcode) || str(source.serviceRequestId),
  };
}
