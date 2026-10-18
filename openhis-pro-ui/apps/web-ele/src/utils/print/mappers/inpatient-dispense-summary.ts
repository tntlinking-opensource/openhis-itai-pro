/** 汇总领药单明细行（对齐 getMedicationSummarySlipDetail.lines） */
export interface InpatientDispenseSummaryDrugRow {
  seqNo: string;
  drugName: string;
  specText: string;
  /** 账单单价文案 */
  unitPriceText: string;
  /** 账单行总价文案 */
  amountText: string;
  quantityUnitText: string;
}

/** 汇总领药单 PrintData（对齐后端汇总单头 + 合并明细） */
export interface InpatientDispenseSummaryPrintData {
  hospitalName: string;
  documentTitle: string;
  docNo: string;
  /** 药房名称 ← purposeLocationName */
  wardName: string;
  /**
   * 申领科室（模板常见 field）。
   * 优先病区所属科室，空则回落申领人登录科室。
   */
  applicantDepartmentName: string;
  /**
   * 申领科室别名 field（部分院点模板绑 requestingDepartmentName）。
   * 与 applicantDepartmentName 同值，避免模板 field 不一致打空。
   */
  requestingDepartmentName: string;
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
  applicantDepartmentName?: string;
  /** 申领人登录科室；病区所属科室为空时回落 */
  requestingDepartmentName?: string;
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
    amountText: str(row.amountText),
    quantityUnitText: str(row.quantityUnitText),
  };
}

/** 申领科室展示：病区所属科室优先，否则申领人登录科室 */
function resolveApplicantDepartmentName(source: InpatientDispenseSummaryPrintSource): string {
  return str(source.applicantDepartmentName) || str(source.requestingDepartmentName);
}

/** 汇总领药单 → PrintData（明细序以后端拼音排序为准，仅透传） */
export function buildInpatientDispenseSummaryPrintData(
  source: InpatientDispenseSummaryPrintSource,
): InpatientDispenseSummaryPrintData {
  const applicantDepartmentName = resolveApplicantDepartmentName(source);
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '汇总领药单',
    docNo: str(source.docNo),
    wardName: str(source.wardName),
    applicantDepartmentName,
    requestingDepartmentName: applicantDepartmentName,
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
