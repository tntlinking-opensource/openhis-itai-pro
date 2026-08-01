/** 住院费用清单明细行（与 ip-feeList-print 模板 feeRows 列一致） */
export interface InpatientFeeListFeeRow {
  /** item | category | subtotal */
  rowType: 'category' | 'item' | 'subtotal';
  /** 分类行显示名 */
  categoryName?: string;
  seqNo: string;
  itemName: string;
  specText: string;
  /** 数量+单位，如「2瓶」 */
  quantityUnitText: string;
  insuranceLevelText: string;
  unitPriceText: string;
  amountText: string;
}

/** 住院费用清单 PrintData（与 ip-feeList-print 模板 field 一致） */
export interface InpatientFeeListPrintData {
  hospitalName: string;
  documentTitle: string;
  inpatientNo: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  wardName: string;
  roomNo: string;
  bedNo: string;
  departmentName: string;
  admitAtText: string;
  dischargeAtText: string;
  inpatientDaysText: string;
  admissionDiagnosis: string;
  prepaidAmountText: string;
  totalAmountText: string;
  balanceAmountText: string;
  feeRows: InpatientFeeListFeeRow[];
  printedAtText: string;
}

/** 清单 mapper 入参（后续对接费用明细接口时填充；字段均为展示串） */
export interface InpatientFeeListPrintSource {
  hospitalName?: string;
  documentTitle?: string;
  inpatientNo?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  wardName?: string;
  roomNo?: string;
  bedNo?: string;
  departmentName?: string;
  admitAtText?: string;
  dischargeAtText?: string;
  inpatientDaysText?: string;
  admissionDiagnosis?: string;
  prepaidAmountText?: string;
  totalAmountText?: string;
  balanceAmountText?: string;
  feeRows?: InpatientFeeListFeeRow[];
  printedAtText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function stripCategoryDecorators(name: string): string {
  return name
    .replace(/^【/, '')
    .replace(/】$/, '')
    .replace(/^◆\s*/, '')
    .replace(/合计金额$/, '')
    .replace(/合计$/, '')
    .trim();
}

function mapFeeRow(row: InpatientFeeListFeeRow): InpatientFeeListFeeRow {
  const rowType = row.rowType;
  const categoryName = str(row.categoryName);
  if (rowType === 'category') {
    const title = categoryName || stripCategoryDecorators(str(row.itemName) || str(row.seqNo));
    const label = title ? `【${title}】` : '';
    return {
      rowType,
      categoryName: title,
      // 合并单元格从序号列起渲染，标题必须写在 seqNo
      seqNo: label,
      itemName: label,
      specText: '',
      quantityUnitText: '',
      insuranceLevelText: '',
      unitPriceText: '',
      amountText: '',
    };
  }
  if (rowType === 'subtotal') {
    const title = categoryName || stripCategoryDecorators(str(row.itemName) || str(row.seqNo));
    const label = title ? `${title}合计金额` : '合计金额';
    return {
      rowType,
      categoryName: title,
      seqNo: label,
      itemName: label,
      specText: '',
      quantityUnitText: '',
      insuranceLevelText: '',
      unitPriceText: '',
      amountText: str(row.amountText),
    };
  }
  return {
    rowType: 'item',
    categoryName: '',
    seqNo: str(row.seqNo),
    itemName: str(row.itemName),
    specText: str(row.specText),
    quantityUnitText: str(row.quantityUnitText),
    insuranceLevelText: str(row.insuranceLevelText),
    unitPriceText: str(row.unitPriceText),
    amountText: str(row.amountText),
  };
}

/** 费用明细 → PrintData（版式字段已由模板处理，此处仅做空值归一） */
export function buildInpatientFeeListPrintData(
  source: InpatientFeeListPrintSource,
): InpatientFeeListPrintData {
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '住院费用清单',
    inpatientNo: str(source.inpatientNo),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    wardName: str(source.wardName),
    roomNo: str(source.roomNo),
    bedNo: str(source.bedNo),
    departmentName: str(source.departmentName),
    admitAtText: str(source.admitAtText),
    dischargeAtText: str(source.dischargeAtText),
    inpatientDaysText: str(source.inpatientDaysText),
    admissionDiagnosis: str(source.admissionDiagnosis),
    prepaidAmountText: str(source.prepaidAmountText),
    totalAmountText: str(source.totalAmountText),
    balanceAmountText: str(source.balanceAmountText),
    feeRows: (source.feeRows ?? []).map((row) => mapFeeRow(row)),
    printedAtText: str(source.printedAtText),
  };
}
