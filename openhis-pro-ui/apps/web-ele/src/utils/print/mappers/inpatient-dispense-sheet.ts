/** 摆药单明细行（与 ip-dispenseSheet-print 模板 drugRows 列一致） */
export interface InpatientDispenseSheetDrugRow {
  patientText: string;
  drugName: string;
  specText: string;
  quantityUnitText: string;
  doseText: string;
  usageText: string;
  freqText: string;
}

/** 摆药单 PrintData（与 ip-dispenseSheet-print 模板 field 一致） */
export interface InpatientDispenseSheetPrintData {
  hospitalName: string;
  documentTitle: string;
  periodText: string;
  preparedByName: string;
  wardName: string;
  drugRows: InpatientDispenseSheetDrugRow[];
  printedAtText: string;
}

/** 摆药单 mapper 入参（后续对接摆药批次接口；字段均为展示串） */
export interface InpatientDispenseSheetPrintSource {
  hospitalName?: string;
  documentTitle?: string;
  periodText?: string;
  preparedByName?: string;
  wardName?: string;
  drugRows?: InpatientDispenseSheetDrugRow[];
  printedAtText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapDrugRow(row: InpatientDispenseSheetDrugRow): InpatientDispenseSheetDrugRow {
  return {
    patientText: str(row.patientText),
    drugName: str(row.drugName),
    specText: str(row.specText),
    quantityUnitText: str(row.quantityUnitText),
    doseText: str(row.doseText),
    usageText: str(row.usageText),
    freqText: str(row.freqText),
  };
}

/** 摆药单 → PrintData */
export function buildInpatientDispenseSheetPrintData(
  source: InpatientDispenseSheetPrintSource,
): InpatientDispenseSheetPrintData {
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '摆药单',
    periodText: str(source.periodText),
    preparedByName: str(source.preparedByName),
    wardName: str(source.wardName),
    drugRows: (source.drugRows ?? []).map((row) => mapDrugRow(row)),
    printedAtText: str(source.printedAtText),
  };
}
