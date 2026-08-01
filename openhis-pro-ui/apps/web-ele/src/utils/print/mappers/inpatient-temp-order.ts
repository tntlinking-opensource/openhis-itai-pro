/** 临嘱执行记录明细行（与 ip-tempOrder-print 模板 orderRows 列一致） */
export interface InpatientTempOrderRow {
  startDateText: string;
  startTimeText: string;
  orderContent: string;
  orderDoctorName: string;
  executeNurseName: string;
  executeAtText: string;
}

/** 临嘱执行记录 PrintData（与 ip-tempOrder-print 模板 field 一致） */
export interface InpatientTempOrderPrintData {
  hospitalName: string;
  documentTitle: string;
  inpatientNo: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  departmentName: string;
  wardName: string;
  roomNo: string;
  bedNo: string;
  orderRows: InpatientTempOrderRow[];
  doctorSignLabel: string;
  nurseSignLabel: string;
  printedAtText: string;
}

/** 临嘱 mapper 入参（后续对接医嘱执行接口；字段均为展示串） */
export interface InpatientTempOrderPrintSource {
  hospitalName?: string;
  documentTitle?: string;
  inpatientNo?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  departmentName?: string;
  wardName?: string;
  roomNo?: string;
  bedNo?: string;
  orderRows?: InpatientTempOrderRow[];
  doctorSignLabel?: string;
  nurseSignLabel?: string;
  printedAtText?: string;
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapOrderRow(row: InpatientTempOrderRow): InpatientTempOrderRow {
  return {
    startDateText: str(row.startDateText),
    startTimeText: str(row.startTimeText),
    orderContent: str(row.orderContent),
    orderDoctorName: str(row.orderDoctorName),
    executeNurseName: str(row.executeNurseName),
    executeAtText: str(row.executeAtText),
  };
}

/** 临嘱执行记录 → PrintData */
export function buildInpatientTempOrderPrintData(
  source: InpatientTempOrderPrintSource,
): InpatientTempOrderPrintData {
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '临嘱执行记录',
    inpatientNo: str(source.inpatientNo),
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    departmentName: str(source.departmentName),
    wardName: str(source.wardName),
    roomNo: str(source.roomNo),
    bedNo: str(source.bedNo),
    orderRows: (source.orderRows ?? []).map((row) => mapOrderRow(row)),
    doctorSignLabel: str(source.doctorSignLabel) || '医生：',
    nurseSignLabel: str(source.nurseSignLabel) || '护士：',
    printedAtText: str(source.printedAtText),
  };
}
