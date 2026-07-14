import type {
  BedOperationType,
  ExecuteBedOperationRequest,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import type { PatientItem } from '../types';

import { httpExecuteBedOperation } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';
import { httpGetPatientDetail } from '#/api/inpatient/nurse-station/inpatient-nurse-station.http';

import type { AdmissionBedOperationIntent } from './admission-bed-operation';

import {
  buildParticipantSubmitRows,
  mapParticipantsToFormIds,
} from './map-patient-detail-participants';

/** 就诊状态：待入科（首次分床） */
export const ENCOUNTER_STATUS_REGISTERED = 'REGISTERED';

/** 就诊状态：已入科（换床 / 互换） */
export const ENCOUNTER_STATUS_IN_PROGRESS = 'IN_PROGRESS';

export type BedDropPayload =
  | {
      bedLocationId: string;
      houseLocationId: string;
      type: 'assign';
      encounterId: string;
    }
  | {
      bedLocationId: string;
      houseLocationId: string;
      type: 'transfer';
      encounterId: string;
    }
  | { encounterId: string; encounterIdB: string; type: 'swap' };

/** 根据拖放语义解析写操作类型（与 SDD IF-ATD-02 三分支对齐） */
export function resolveBedOperationType(
  dragged: PatientItem,
  targetOccupied: boolean,
): BedOperationType | null {
  if (targetOccupied) {
    if (!dragged.bedLocationId) {
      return null;
    }
    return 'SWAP';
  }
  if (!dragged.bedLocationId) {
    return 'ASSIGN';
  }
  return 'TRANSFER';
}

/** 分床前拉取详情并组装参与者（ASSIGN 建议必填） */
async function loadAssignParticipants(encounterId: string) {
  const res = await httpGetPatientDetail({ encounterId });
  const detail = res.detail;
  if (!detail) {
    return undefined;
  }
  const ids = mapParticipantsToFormIds(detail.participants);
  const rows = buildParticipantSubmitRows(ids);
  return rows.length > 0 ? rows : undefined;
}

/** 拖放载荷 → executeBedOperation 请求体 */
export async function buildExecuteBedOperationRequest(
  payload: BedDropPayload,
  dragged: PatientItem,
  targetOccupied: boolean,
): Promise<ExecuteBedOperationRequest | null> {
  const operationType = resolveBedOperationType(dragged, targetOccupied);
  if (!operationType) {
    return null;
  }
  if (payload.type === 'swap') {
    return {
      encounterId: payload.encounterId,
      encounterIdB: payload.encounterIdB,
      operationType: 'SWAP',
    };
  }
  const base: ExecuteBedOperationRequest = {
    encounterId: payload.encounterId,
    operationType,
    houseLocationId: payload.houseLocationId,
    bedLocationId: payload.bedLocationId,
  };
  if (operationType === 'ASSIGN') {
    const participants = await loadAssignParticipants(payload.encounterId);
    if (!participants?.length) {
      return null;
    }
    base.participants = participants;
    if (dragged.priorityEnum) {
      base.priorityEnum = dragged.priorityEnum;
    }
  }
  return base;
}

/** 入科意图 → 拖放载荷（换床/互换直提交用） */
export function bedDropPayloadFromIntent(
  intent: AdmissionBedOperationIntent,
  encounterId: string,
): BedDropPayload {
  if (intent.type === 'swap') {
    return {
      encounterId,
      encounterIdB: intent.encounterIdB ?? '',
      type: 'swap',
    };
  }
  return {
    encounterId,
    houseLocationId: intent.houseLocationId,
    bedLocationId: intent.bedLocationId,
    type: intent.type,
  };
}

/** 换床/互换：不经过入科弹窗，直接提交写床 */
export async function submitDirectBedOperation(
  patient: PatientItem,
  intent: AdmissionBedOperationIntent,
): Promise<string | null> {
  if (intent.type === 'assign') {
    return null;
  }
  const payload = bedDropPayloadFromIntent(intent, patient.id);
  const targetOccupied = intent.type === 'swap';
  const body = await buildExecuteBedOperationRequest(payload, patient, targetOccupied);
  if (!body) {
    return null;
  }
  await httpExecuteBedOperation(body);
  return bedOperationSuccessMessage(body.operationType, patient.name, intent.targetBedName);
}

/** 写操作成功后的提示文案 */
export function bedOperationSuccessMessage(
  operationType: BedOperationType,
  patientName: string,
  bedName: string,
): string {
  if (operationType === 'ASSIGN') {
    return `已将 ${patientName} 分至 ${bedName}`;
  }
  if (operationType === 'TRANSFER') {
    return `已将 ${patientName} 换至 ${bedName}`;
  }
  return `换床成功：${patientName} 已与对手互换床位`;
}
