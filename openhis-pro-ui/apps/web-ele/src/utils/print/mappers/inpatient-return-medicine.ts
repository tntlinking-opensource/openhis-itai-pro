/** 住院退药单明细行（与 ip-returnMedicine-print 模板 drugRows 列一致） */
export interface InpatientReturnMedicineDrugRow {
  seqText: string;
  drugName: string;
  lotNumber: string;
  quantityText: string;
  unitText: string;
  doctorName: string;
  statusText: string;
}

/**
 * 住院退药单 PrintData。
 * 一名患者一张：表头是该患者，drugRows 只放这名患者的退药明细。
 */
export interface InpatientReturnMedicinePrintData {
  hospitalName: string;
  documentTitle: string;
  patientName: string;
  genderAgeText: string;
  hospitalizationNo: string;
  wardName: string;
  bedName: string;
  feeNatureText: string;
  statusText: string;
  printedAtText: string;
  drugRows: InpatientReturnMedicineDrugRow[];
}

/** 住院退药单 mapper 入参 */
export interface InpatientReturnMedicinePrintSource {
  hospitalName?: string;
  documentTitle?: string;
  patientName?: string;
  genderAgeText?: string;
  hospitalizationNo?: string;
  wardName?: string;
  bedName?: string;
  feeNatureText?: string;
  statusText?: string;
  printedAtText?: string;
  drugRows?: InpatientReturnMedicineDrugRow[];
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapDrugRow(
  row: InpatientReturnMedicineDrugRow,
  index: number,
): InpatientReturnMedicineDrugRow {
  return {
    seqText: str(row.seqText) || String(index + 1),
    drugName: str(row.drugName),
    lotNumber: str(row.lotNumber),
    quantityText: str(row.quantityText),
    unitText: str(row.unitText),
    doctorName: str(row.doctorName),
    statusText: str(row.statusText),
  };
}

/** 住院退药单 → PrintData（一患一单） */
export function buildInpatientReturnMedicinePrintData(
  source: InpatientReturnMedicinePrintSource,
): InpatientReturnMedicinePrintData {
  const drugRows = (source.drugRows ?? []).map((row, index) => mapDrugRow(row, index));
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '住院退药单',
    patientName: str(source.patientName),
    genderAgeText: str(source.genderAgeText),
    hospitalizationNo: str(source.hospitalizationNo),
    wardName: str(source.wardName),
    bedName: str(source.bedName),
    feeNatureText: str(source.feeNatureText),
    statusText: str(source.statusText),
    printedAtText: str(source.printedAtText),
    drugRows,
  };
}
