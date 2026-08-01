/** 汇总领药单明细行（对齐 getMedicationSummarySlipDetail.lines） */
export interface InpatientDispenseSummaryDrugRow {
  seqNo: string;
  drugName: string;
  specText: string;
  unitPriceText: string;
  quantityUnitText: string;
}

/** 汇总领药单 PrintData（对齐后端汇总单头 + 合并明细） */
export interface InpatientDispenseSummaryPrintData {
  hospitalName: string;
  documentTitle: string;
  docNo: string;
  /** 药房名称 ← purposeLocationName */
  wardName: string;
  applicantName: string;
  dispenseAtText: string;
  generatedAtText: string;
  orderCountText: string;
  drugCountText: string;
  totalAmountText: string;
  drugRows: InpatientDispenseSummaryDrugRow[];
  /** 领药人 ← receiverName */
  dispenserName: string;
  receiverSignLabel: string;
  printedAtText: string;
}

/** 汇总领药单 mapper 入参 */
export interface InpatientDispenseSummaryPrintSource {
  hospitalName?: string;
  documentTitle?: string;
  docNo?: string;
  wardName?: string;
  applicantName?: string;
  dispenseAtText?: string;
  generatedAtText?: string;
  orderCountText?: string;
  drugCountText?: string;
  totalAmountText?: string;
  drugRows?: InpatientDispenseSummaryDrugRow[];
  dispenserName?: string;
  receiverSignLabel?: string;
  printedAtText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapDrugRow(row: InpatientDispenseSummaryDrugRow): InpatientDispenseSummaryDrugRow {
  return {
    seqNo: str(row.seqNo),
    drugName: str(row.drugName),
    specText: str(row.specText),
    unitPriceText: str(row.unitPriceText),
    quantityUnitText: str(row.quantityUnitText),
  };
}

/** 汇总领药单 → PrintData */
export function buildInpatientDispenseSummaryPrintData(
  source: InpatientDispenseSummaryPrintSource,
): InpatientDispenseSummaryPrintData {
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '汇总领药单',
    docNo: str(source.docNo),
    wardName: str(source.wardName),
    applicantName: str(source.applicantName),
    dispenseAtText: str(source.dispenseAtText),
    generatedAtText: str(source.generatedAtText),
    orderCountText: str(source.orderCountText),
    drugCountText: str(source.drugCountText),
    totalAmountText: str(source.totalAmountText),
    drugRows: (source.drugRows ?? []).map((row) => mapDrugRow(row)),
    dispenserName: str(source.dispenserName),
    receiverSignLabel: str(source.receiverSignLabel) || '签收：',
    printedAtText: str(source.printedAtText),
  };
}
