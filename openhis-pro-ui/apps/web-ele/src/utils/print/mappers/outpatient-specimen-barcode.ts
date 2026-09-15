/** 门诊采血贴 PrintData（与 op-specimenBarcode-print 模板 field 一致；50×30mm） */
export interface OutpatientSpecimenBarcodePrintData {
  patientName: string;
  genderEnumText: string;
  ageText: string;
  itemName: string;
  barcode: string;
}

/**
 * 采血贴 mapper 入参
 * P：患者信息 + 检验项目名 + 申请主键（条码，禁止 Number）
 */
export interface OutpatientSpecimenBarcodePrintSource {
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

/** 采血贴 → PrintData */
export function buildOutpatientSpecimenBarcodePrintData(
  source: OutpatientSpecimenBarcodePrintSource,
): OutpatientSpecimenBarcodePrintData {
  return {
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    itemName: str(source.itemName),
    barcode: str(source.barcode) || str(source.serviceRequestId),
  };
}
