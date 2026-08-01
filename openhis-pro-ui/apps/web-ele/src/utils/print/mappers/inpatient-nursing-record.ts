/** 护理记录明细行（与 ip-nursingRecord-print 模板 recordRows 列一致） */
export interface InpatientNursingRecordRow {
  recordDatetime: string;
  temperature: string;
  pulse: string;
  heartRate: string;
  respiratory: string;
  bloodPressure: string;
  consciousness: string;
  pupilLeft: string;
  pupilRight: string;
  intakeName: string;
  intakeAmount: string;
  outputName: string;
  outputAmount: string;
  outputColorCharacter: string;
  conditionMeasure: string;
  nurseSignature: string;
}

/** 护理记录单 PrintData（与 ip-nursingRecord-print 模板 field 一致） */
export interface InpatientNursingRecordPrintData {
  weekRangeLabel: string;
  documentTitle: string;
  patientName: string;
  genderEnumText: string;
  ageText: string;
  wardName: string;
  bedNo: string;
  inpatientNo: string;
  admitAtText: string;
  dischargeAtText: string;
  recordRows: InpatientNursingRecordRow[];
}

/** 护理记录 mapper 入参（字段均为展示串） */
export interface InpatientNursingRecordPrintSource {
  weekRangeLabel?: string;
  documentTitle?: string;
  patientName?: string;
  genderEnumText?: string;
  ageText?: string;
  wardName?: string;
  bedNo?: string;
  inpatientNo?: string;
  admitAtText?: string;
  dischargeAtText?: string;
  recordRows?: InpatientNursingRecordRow[];
}

function str(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

function mapRecordRow(row: InpatientNursingRecordRow): InpatientNursingRecordRow {
  return {
    recordDatetime: str(row.recordDatetime),
    temperature: str(row.temperature),
    pulse: str(row.pulse),
    heartRate: str(row.heartRate),
    respiratory: str(row.respiratory),
    bloodPressure: str(row.bloodPressure),
    consciousness: str(row.consciousness),
    pupilLeft: str(row.pupilLeft),
    pupilRight: str(row.pupilRight),
    intakeName: str(row.intakeName),
    intakeAmount: str(row.intakeAmount),
    outputName: str(row.outputName),
    outputAmount: str(row.outputAmount),
    outputColorCharacter: str(row.outputColorCharacter),
    conditionMeasure: str(row.conditionMeasure),
    nurseSignature: str(row.nurseSignature),
  };
}

/** 护理记录单 → PrintData */
export function buildInpatientNursingRecordPrintData(
  source: InpatientNursingRecordPrintSource,
): InpatientNursingRecordPrintData {
  return {
    weekRangeLabel: str(source.weekRangeLabel),
    documentTitle: str(source.documentTitle) || '护理记录单',
    patientName: str(source.patientName),
    genderEnumText: str(source.genderEnumText),
    ageText: str(source.ageText),
    wardName: str(source.wardName),
    bedNo: str(source.bedNo),
    inpatientNo: str(source.inpatientNo),
    admitAtText: str(source.admitAtText),
    dischargeAtText: str(source.dischargeAtText),
    recordRows: (source.recordRows ?? []).map((row) => mapRecordRow(row)),
  };
}
