import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';
import { resolveAdviceQuantityUnitLabel } from '#/utils/doctor-station/advice-quantity-unit-display';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import {
  expandActivityDefinitionChildrenForPrint,
  isLabServiceCategoryForPrint,
} from '#/utils/print/expand-activity-definition-children-for-print';

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

function toFiniteNumber(value: null | number | string | undefined): null | number {
  if (value === null || value === undefined || String(value).trim() === '') {
    return null;
  }
  const n = typeof value === 'number' ? value : Number(String(value).trim());
  if (!Number.isFinite(n)) {
    return null;
  }
  return n;
}

/** 单价：优先账单 unitPrice；缺省时用总价÷数量回推（与护士站中区总价同源） */
function formatUnitPriceText(row: NurseStationTreatmentLineDto): string {
  let unitPrice = toFiniteNumber(row.unitPrice);
  if (unitPrice === null) {
    const total = toFiniteNumber(row.displayTotalPrice);
    const qty = toFiniteNumber(row.quantity);
    if (total !== null && qty !== null && qty !== 0) {
      unitPrice = total / qty;
    }
  }
  if (unitPrice === null) {
    return '';
  }
  const text = formatMoneyDisplay(unitPrice);
  return text ? `${text} 元` : '';
}

function formatQuantityText(row: NurseStationTreatmentLineDto): string {
  const qty = toFiniteNumber(row.quantity);
  if (
    qty === null &&
    (row.quantity === null || row.quantity === undefined || String(row.quantity).trim() === '')
  ) {
    return '';
  }
  const qtyText = qty === null ? String(row.quantity).trim() : String(qty);
  // 与医生站一致：unitName 优先；EA 遗留码统一展示为「次」
  const unit = resolveAdviceQuantityUnitLabel(row.unitName, row.unitCode);
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
  const serviceLines = expandActivityDefinitionChildrenForPrint(
    source.serviceLines ?? [],
    (line) => line.packageChildren,
    (line, child) => {
      const childName = str(child.childrenName);
      return {
        ...line,
        itemName: childName || str(line.itemName),
      };
    },
    (line) => isLabServiceCategoryForPrint(line.serviceCategoryCode),
  );
  const lines = [...serviceLines, ...(source.deviceLines ?? [])].map((line) =>
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
