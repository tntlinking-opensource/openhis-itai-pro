/** 护理记录明细行（与 ip-nursingRecord-print 模板 recordRows 列一致） */
export interface InpatientNursingRecordRow {
  recordDatetime: string;
  temperature: string;
  pulse: string;
  heartRate: string;
  /** 红旗合列：脉搏或心率 */
  pulseHeartRate: string;
  respiratory: string;
  bloodPressure: string;
  systolic: string;
  diastolic: string;
  consciousness: string;
  pupilLeft: string;
  pupilRight: string;
  oxygenSaturation: string;
  oxygenRate: string;
  intakeName: string;
  intakeAmount: string;
  intakeRoute: string;
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

function splitBloodPressure(bloodPressure?: string): { diastolic: string; systolic: string } {
  const raw = str(bloodPressure);
  if (!raw) {
    return { diastolic: '', systolic: '' };
  }
  const slashIndex = raw.indexOf('/');
  if (slashIndex < 0) {
    return { diastolic: '', systolic: raw };
  }
  return {
    systolic: raw.slice(0, slashIndex).trim(),
    diastolic: raw.slice(slashIndex + 1).trim(),
  };
}

function mapRecordRow(row: InpatientNursingRecordRow): InpatientNursingRecordRow {
  const bloodPressure = str(row.bloodPressure);
  const split = splitBloodPressure(bloodPressure);
  return {
    recordDatetime: str(row.recordDatetime),
    temperature: str(row.temperature),
    pulse: str(row.pulse),
    heartRate: str(row.heartRate),
    pulseHeartRate: str(row.pulseHeartRate) || str(row.pulse) || str(row.heartRate),
    respiratory: str(row.respiratory),
    bloodPressure,
    systolic: str(row.systolic) || split.systolic,
    diastolic: str(row.diastolic) || split.diastolic,
    consciousness: str(row.consciousness),
    pupilLeft: str(row.pupilLeft),
    pupilRight: str(row.pupilRight),
    oxygenSaturation: str(row.oxygenSaturation),
    oxygenRate: str(row.oxygenRate),
    intakeName: str(row.intakeName),
    intakeAmount: str(row.intakeAmount),
    intakeRoute: str(row.intakeRoute),
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
