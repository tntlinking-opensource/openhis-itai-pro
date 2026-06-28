import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

/** 处置单表格行（与 op-treatment-print 模板 treatmentLines 列一致） */
export interface OutpatientTreatmentSheetLine {
  itemName: string;
  unitPriceText: string;
  quantityText: string;
  executeCountText: string;
}

/** 门诊处置单 PrintData（与 op-treatment-print 模板 field 一致） */
export interface OutpatientTreatmentSheetPrintData {
  hospitalName: string;
  patientName: string;
  ageText: string;
  genderEnumText: string;
  orderingDepartmentName: string;
  feeNatureText: string;
  orderDateText: string;
  encounterBusNo: string;
  orderingPractitionerName: string;
  treatmentLines: OutpatientTreatmentSheetLine[];
}

/** 处置单 mapper 入参（P：护士站当前就诊 + 处置单读模型） */
export interface OutpatientTreatmentSheetPrintSource {
  hospitalName?: string;
  patientName?: string;
  ageText?: string;
  genderEnumText?: string;
  orderingDepartmentName?: string;
  feeNatureText?: string;
  orderDateText?: string;
  encounterBusNo?: string;
  orderingPractitionerName?: string;
  serviceLines?: NurseStationTreatmentLineDto[];
  deviceLines?: NurseStationTreatmentLineDto[];
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function formatUnitPriceText(row: NurseStationTreatmentLineDto): string {
  const unitPrice = row.unitPrice;
  if (unitPrice === null || unitPrice === undefined || String(unitPrice).trim() === '') {
    return '';
  }
  const n = typeof unitPrice === 'number' ? unitPrice : Number(String(unitPrice).trim());
  if (!Number.isFinite(n)) {
    return '';
  }
  return `${n} 元`;
}

function formatQuantityText(row: NurseStationTreatmentLineDto): string {
  const qtyRaw = row.quantity;
  if (qtyRaw === null || qtyRaw === undefined || String(qtyRaw).trim() === '') {
    return '';
  }
  const qty = typeof qtyRaw === 'number' ? qtyRaw : Number(String(qtyRaw).trim());
  const qtyText = Number.isFinite(qty) ? String(qty) : String(qtyRaw).trim();
  const unit = str(row.unitName) || str(row.unitCode);
  return unit ? `${qtyText} ${unit}` : qtyText;
}

function resolveExecuteCountText(row: NurseStationTreatmentLineDto): string {
  const planned = row.plannedPerformCount;
  if (planned !== null && planned !== undefined) {
    return String(planned);
  }
  const medExec = row.medicationExecuteNum;
  if (medExec !== null && medExec !== undefined) {
    return String(medExec);
  }
  const perform = row.performCount;
  if (perform !== null && perform !== undefined) {
    return String(perform);
  }
  const qtyRaw = row.quantity;
  if (qtyRaw !== null && qtyRaw !== undefined && String(qtyRaw).trim() !== '') {
    return String(qtyRaw).trim();
  }
  return '';
}

function mapTreatmentLine(row: NurseStationTreatmentLineDto): OutpatientTreatmentSheetLine {
  return {
    itemName: str(row.itemName),
    unitPriceText: formatUnitPriceText(row),
    quantityText: formatQuantityText(row),
    executeCountText: resolveExecuteCountText(row),
  };
}

/** 护士站处置单读模型 → PrintData（诊疗 + 耗材行） */
export function buildOutpatientTreatmentSheetPrintData(
  source: OutpatientTreatmentSheetPrintSource,
): OutpatientTreatmentSheetPrintData {
  const lines = [...(source.serviceLines ?? []), ...(source.deviceLines ?? [])].map((line) =>
    mapTreatmentLine(line),
  );
  return {
    hospitalName: str(source.hospitalName),
    patientName: str(source.patientName),
    ageText: str(source.ageText),
    genderEnumText: str(source.genderEnumText),
    orderingDepartmentName: str(source.orderingDepartmentName),
    feeNatureText: str(source.feeNatureText),
    orderDateText: str(source.orderDateText),
    encounterBusNo: str(source.encounterBusNo),
    orderingPractitionerName: str(source.orderingPractitionerName),
    treatmentLines: lines,
  };
}
