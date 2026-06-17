export interface NursingRecordPatient {
  id: string;
  bedNo: string;
  name: string;
  gender?: string;
  age: number;
  nurseLevel: string;
  feeType?: string;
  diagnosis?: string;
  wardName: string;
  riskTags: string[];
}

export interface NursingRecordRow {
  id: string;
  recordDate: string;
  recordTime: string;
  consciousness: string;
  temperature: string;
  heartRate: string;
  pulse: string;
  respiratory: string;
  bloodPressure: string;
  pupilLeft: string;
  pupilRight: string;
  oxygenSaturation: string;
  oxygenRate: string;
  oxygenMode: string;
  oxygenFlow: string;
  intakeName: string;
  intakeAmount: string;
  intakeRoute: string;
  outputName: string;
  outputAmount: string;
  outputColorCharacter: string;
  reservedCol1: string;
  reservedCol2: string;
  skinStatus: string;
  tubeCare: string;
  conditionMeasure: string;
  nurseSignature: string;
}

export type NursingRecordRowType = 'extra' | 'skeleton';

export interface NursingRecordPaperHeader {
  admitAt?: string;
  age?: number | string;
  bedNo?: string;
  dischargeAt?: string;
  gender?: string;
  inpatientNo?: string;
  name?: string;
  wardName?: string;
}

export interface NursingRecordViewRow extends NursingRecordRow {
  isDirty?: boolean;
  isEditing?: boolean;
  isNew?: boolean;
  rowType?: NursingRecordRowType;
}
