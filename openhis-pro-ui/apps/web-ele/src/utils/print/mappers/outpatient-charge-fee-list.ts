/** 门诊患者费用清单明细行（与 op-feeList-print 模板 feeListRows 列一致） */
export interface OutpatientChargeFeeListRow {
  encounterBusNo: string;
  patientName: string;
  departmentName: string;
  orderingDoctorName: string;
  itemName: string;
  unitPriceText: string;
  quantityText: string;
  amountText: string;
  insuranceLevelText: string;
  visitTypeText: string;
  itemTypeText: string;
  timeText: string;
}

/** 门诊患者费用清单 PrintData（与 op-feeList-print 模板 field 一致） */
export interface OutpatientChargeFeeListPrintData {
  organizationLabel: string;
  documentTitle: string;
  hospitalName: string;
  feeListRows: OutpatientChargeFeeListRow[];
  feeListTotalAmountText: string;
}

/** printChargeReceipt.slip 中费用清单相关字段 */
export interface ChargeFeeListPrintSource {
  hospitalName?: string;
  organizationLabel?: string;
  documentTitle?: string;
  feeListRows?: OutpatientChargeFeeListRow[];
  feeListTotalAmountText?: string;
  encounterId?: null | number | string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapRow(row: OutpatientChargeFeeListRow): OutpatientChargeFeeListRow {
  return {
    encounterBusNo: str(row.encounterBusNo),
    patientName: str(row.patientName),
    departmentName: str(row.departmentName),
    orderingDoctorName: str(row.orderingDoctorName),
    itemName: str(row.itemName),
    unitPriceText: str(row.unitPriceText),
    quantityText: str(row.quantityText),
    amountText: str(row.amountText),
    insuranceLevelText: str(row.insuranceLevelText),
    visitTypeText: str(row.visitTypeText),
    itemTypeText: str(row.itemTypeText),
    timeText: str(row.timeText),
  };
}

/** 后端收费 slip → 门诊患者费用清单 PrintData */
export function buildOutpatientChargeFeeListPrintData(
  source: ChargeFeeListPrintSource,
): OutpatientChargeFeeListPrintData {
  const hospitalName = str(source.hospitalName);
  const organizationLabel =
    str(source.organizationLabel) || (hospitalName ? `机构：${hospitalName}` : '');
  return {
    organizationLabel,
    documentTitle: str(source.documentTitle) || '门诊患者费用清单',
    hospitalName,
    feeListRows: (source.feeListRows ?? []).map((row) => mapRow(row)),
    feeListTotalAmountText: str(source.feeListTotalAmountText),
  };
}
