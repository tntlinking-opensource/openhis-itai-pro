/** 长期/临时医嘱明细行（与 ip-tempOrder-print / ip-longOrder-print 模板 orderRows 列一致） */
export interface InpatientTempOrderRow {
  /** 开嘱时间：MM-DD HH:mm（单行） */
  startAtText: string;
  /** 类型：药品/诊疗/护理/嘱托等 */
  orderKindText: string;
  /** 医嘱内容：药品规格一行，用法/频次/用量折行；嘱托仅展示名 */
  orderContent: string;
  orderDoctorName: string;
  /** 开嘱侧校对人（纸面列头「执行护士」，取值仍为校对人） */
  startCheckerName: string;
  /** 执行时间：MM-DD HH:mm */
  executeAtText: string;
  stopTimeText: string;
  stopDoctorName: string;
  /** 停嘱侧校对护士（stop_performer_check_id 解析名） */
  stopCheckerName: string;
}

/** 长期/临时医嘱 PrintData（与模板 field 一致） */
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

/** mapper 入参（字段均为展示串） */
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
    startAtText: str(row.startAtText),
    orderKindText: str(row.orderKindText),
    orderContent: str(row.orderContent),
    orderDoctorName: str(row.orderDoctorName),
    startCheckerName: str(row.startCheckerName),
    executeAtText: str(row.executeAtText),
    stopTimeText: str(row.stopTimeText),
    stopDoctorName: str(row.stopDoctorName),
    stopCheckerName: str(row.stopCheckerName),
  };
}

/** 长期/临时医嘱 → PrintData */
export function buildInpatientTempOrderPrintData(
  source: InpatientTempOrderPrintSource,
): InpatientTempOrderPrintData {
  return {
    hospitalName: str(source.hospitalName),
    documentTitle: str(source.documentTitle) || '临时医嘱',
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
