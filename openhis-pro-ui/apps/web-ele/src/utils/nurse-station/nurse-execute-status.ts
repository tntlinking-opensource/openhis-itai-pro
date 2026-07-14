import type { TagProps } from 'element-plus';

import type { NurseStationTreatmentLineDto } from '#/api/outpatient-nurse-station/outpatient-nurse-station.types';

type NurseStatusTagType = NonNullable<TagProps['type']>;

/** 护士执行表行展示态 code（由 displayStatusEnum / chargeStatusEnum 归一） */
export type NurseExecuteDisplayStatusCode =
  | 'CHARGED'
  | 'COMPLETED'
  | 'EXECUTING'
  | 'ISSUED'
  | 'PENDING_EXECUTE'
  | 'PENDING_ISSUE'
  | 'REFUNDED'
  | 'REFUNDING';

const NURSE_EXECUTE_STATUS_TAG: Record<NurseExecuteDisplayStatusCode, NurseStatusTagType> = {
  PENDING_ISSUE: 'warning',
  ISSUED: 'success',
  CHARGED: 'info',
  REFUNDED: 'danger',
  REFUNDING: 'warning',
  COMPLETED: 'success',
  EXECUTING: 'warning',
  PENDING_EXECUTE: 'primary',
};

function serviceLineDone(row: NurseStationTreatmentLineDto): boolean {
  const planned = row.plannedPerformCount;
  if (planned === null || planned === undefined || planned <= 0) {
    return false;
  }
  const performed = row.performCount ?? 0;
  const cancelled = row.cancelCount ?? 0;
  return performed - cancelled >= planned;
}

function medicationLineDone(row: NurseStationTreatmentLineDto): boolean {
  const plan = row.medicationExecuteNum;
  if (plan === null || plan === undefined || plan <= 0) {
    return false;
  }
  const performed = row.performCount ?? 0;
  const cancelled = row.cancelCount ?? 0;
  return performed - cancelled >= plan;
}

function isServiceRequestTable(requestTable: string): boolean {
  const u = requestTable.trim().toUpperCase();
  return u === 'SERVICE' || u.endsWith('SERVICE_REQUEST');
}

function isMedicationRequestTable(requestTable: string): boolean {
  const u = requestTable.trim().toUpperCase();
  return u === 'MEDICATION' || u.includes('MEDICATION_REQUEST');
}

function lineDone(row: NurseStationTreatmentLineDto): boolean {
  const table = String(row.requestTable ?? '');
  if (isServiceRequestTable(table)) {
    return serviceLineDone(row);
  }
  if (isMedicationRequestTable(table)) {
    return medicationLineDone(row);
  }
  return false;
}

/** 自处置单行解析展示态 code（逻辑判断用，展示文案另取 enumText） */
export function resolveNurseExecuteDisplayStatusCode(
  row: NurseStationTreatmentLineDto,
): NurseExecuteDisplayStatusCode {
  const displayCode = String(row.displayStatusEnum ?? '')
    .trim()
    .toUpperCase();
  if (displayCode === 'PENDING_ISSUE' || displayCode === 'DRAFT') {
    return 'PENDING_ISSUE';
  }
  if (displayCode === 'ISSUED' || displayCode === 'ACTIVE') {
    return 'ISSUED';
  }
  if (displayCode === 'EXECUTING' || displayCode === 'IN_PROGRESS') {
    return 'EXECUTING';
  }
  if (displayCode === 'COMPLETED' || displayCode === 'DONE') {
    return 'COMPLETED';
  }

  const chargeCode = String(row.chargeStatusEnum ?? '')
    .trim()
    .toUpperCase();
  if (chargeCode === 'REFUNDING') {
    return 'REFUNDING';
  }
  if (chargeCode === 'REFUNDED') {
    return 'REFUNDED';
  }
  if (chargeCode === 'SETTLED' || chargeCode === 'CHARGED' || chargeCode === 'PLANNED') {
    return chargeCode === 'PLANNED' ? 'PENDING_EXECUTE' : 'CHARGED';
  }

  if (lineDone(row)) {
    return 'COMPLETED';
  }
  return 'PENDING_EXECUTE';
}

/** 展示态 code → 列表 Tag 类型 */
export function resolveNurseExecuteStatusTagType(
  statusCode: NurseExecuteDisplayStatusCode,
): NurseStatusTagType {
  return NURSE_EXECUTE_STATUS_TAG[statusCode] ?? 'info';
}

export function isNurseExecuteRowCompleted(statusCode: NurseExecuteDisplayStatusCode): boolean {
  return statusCode === 'COMPLETED';
}

/** 耗材发放展示态 code */
export type SupplyDispenseStatusCode = 'COMPLETED' | 'PART_COMPLETED' | 'PENDING' | 'PREPARED';

const SUPPLY_DISPENSE_PENDING_CODES = new Set(['DRAFT', 'IN_PROGRESS', 'PREPARATION', 'PENDING']);

const SUPPLY_DISPENSE_DONE_HINTS = [
  'COMPLETED',
  'DISPENSED',
  'DONE',
  'CLOSED',
  'FINISHED',
  'PREPARED',
];

function isDispenseDoneCode(code: string): boolean {
  const u = code.trim().toUpperCase();
  if (!u) {
    return false;
  }
  return SUPPLY_DISPENSE_DONE_HINTS.some((hint) => u.includes(hint));
}

/** 自发放状态枚举解析展示态 code */
export function resolveSupplyDispenseStatusCode(
  row: Pick<NurseStationTreatmentLineDto, 'dispenseStatusEnum'>,
): SupplyDispenseStatusCode {
  const code = String(row.dispenseStatusEnum ?? '')
    .trim()
    .toUpperCase();
  if (code === 'PREPARED') {
    return 'PREPARED';
  }
  if (code === 'COMPLETED') {
    return 'COMPLETED';
  }
  if (code === 'PART_COMPLETED') {
    return 'PART_COMPLETED';
  }
  if (SUPPLY_DISPENSE_PENDING_CODES.has(code)) {
    return 'PENDING';
  }
  if (isDispenseDoneCode(code)) {
    return 'COMPLETED';
  }
  return 'PENDING';
}

export function canCancelSupplyDispense(statusCode: SupplyDispenseStatusCode): boolean {
  return statusCode === 'PREPARED' || statusCode === 'COMPLETED' || statusCode === 'PART_COMPLETED';
}
