import type { NursingRecordRow, NursingRecordViewRow } from './nursing-record.types';

/**
 * 判断护理记录是否尚未填写业务字段。
 * 不含日期/时点；也不含护士签名（选中行会自动带入登录人，不能据此当成已填）。
 */
const CONTENT_FIELD_KEYS: (keyof NursingRecordRow)[] = [
  'consciousness',
  'temperature',
  'heartRate',
  'pulse',
  'respiratory',
  'bloodPressure',
  'pupilLeft',
  'pupilRight',
  'oxygenSaturation',
  'intakeName',
  'intakeAmount',
  'intakeRoute',
  'outputName',
  'outputAmount',
  'outputColorCharacter',
  'skinStatus',
  'tubeCare',
  'conditionMeasure',
  'reservedCol1',
  'reservedCol2',
];

export type NursingRecordRowVisualState = 'dirty' | 'empty' | 'filled';

/** 判断一行是否仍为空白槽位（骨架行且各业务字段皆空）。 */
export function isNursingRecordRowEmpty(row: NursingRecordRow): boolean {
  return CONTENT_FIELD_KEYS.every((key) => !`${row[key] ?? ''}`.trim());
}

/**
 * 是否允许删除该行。
 * 额外行、已落库行、已填写的骨架槽可删；空白骨架槽保留，避免纸质单行数被抽掉。
 */
export function canDeleteNursingRecordRow(row: NursingRecordViewRow): boolean {
  if (row.rowType === 'extra') {
    return true;
  }
  if (`${row.vitalSignId ?? ''}`.trim()) {
    return true;
  }
  return !isNursingRecordRowEmpty(row);
}

/** 计算纸质单行视觉态：未保存优先，其次空槽与已填。 */
export function getNursingRecordRowVisualState(
  row: NursingRecordViewRow,
): NursingRecordRowVisualState {
  if (row.isDirty) {
    return 'dirty';
  }
  if (isNursingRecordRowEmpty(row)) {
    return 'empty';
  }
  return 'filled';
}
