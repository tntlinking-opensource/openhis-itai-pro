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
 * P：床号 + 患者信息 + 检验项目名 + 条码（与 LIS applyNo=SR.busNo 一致，禁止 Number）+ 优先级
 */
export interface InpatientSpecimenBarcodePrintSource {
  bedNo?: null | string;
  patientName?: null | string;
  genderEnumText?: null | string;
  ageText?: null | string;
  itemName?: null | string;
  /** 检验申请主键；仅作条码回落，禁止 Number */
  serviceRequestId?: null | string;
  /** 显式条码优先（LIS applyNo / SR.busNo）；未传时回落 serviceRequestId */
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

/**
 * 合管项目名截断：按顿号整项装入，超出宽度的项不印（贴面窄，禁止全量堆叠）。
 * maxChars 约对齐 50×30 模板项目行可视宽度。
 */
export function truncateSpecimenBarcodeItemName(
  raw: null | string | undefined,
  maxChars = 20,
): string {
  const text = str(raw);
  if (!text) {
    return '';
  }
  if (text.length <= maxChars) {
    return text;
  }
  const parts = text
    .split('、')
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
  if (parts.length <= 1) {
    return text.slice(0, maxChars);
  }
  let out = '';
  for (const part of parts) {
    const next = out ? `${out}、${part}` : part;
    if (next.length > maxChars) {
      break;
    }
    out = next;
  }
  // 首项本身超长时仍截断，避免贴面撑破
  return out || parts[0]!.slice(0, maxChars);
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
    itemName: truncateSpecimenBarcodeItemName(source.itemName),
    barcode: str(source.barcode) || str(source.serviceRequestId),
    urgencyText,
  };
}
