import type { ExecuteBedOperationRequest } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';
import type {
  AdmissionVitalSignSubmitDto,
  ParticipantSubmitDto,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station.types';

import type { PatientItem } from '../types';

/** 拖放/选床后待确认的床位写操作（入科弹窗消费） */
export interface AdmissionBedOperationIntent {
  type: 'assign' | 'swap' | 'transfer';
  houseLocationId: string;
  bedLocationId: string;
  targetBedName: string;
  targetHouseName: string;
  targetRegionName: string;
  /** 目标入院病区位置 id */
  targetWardLocationId: string;
  /** 互换对手就诊 id */
  encounterIdB?: string;
  swapPartnerName?: string;
}

/** 弹窗确认后组装 executeBedOperation 请求体 */
export function buildExecuteBedOperationFromIntent(
  intent: AdmissionBedOperationIntent,
  encounterId: string,
  participants: ParticipantSubmitDto[],
  priorityEnum?: string,
  admissionVitalSigns?: AdmissionVitalSignSubmitDto,
): ExecuteBedOperationRequest {
  if (intent.type === 'swap') {
    return {
      encounterId,
      encounterIdB: intent.encounterIdB ?? '',
      operationType: 'SWAP',
    };
  }
  const body: ExecuteBedOperationRequest = {
    encounterId,
    operationType: intent.type === 'assign' ? 'ASSIGN' : 'TRANSFER',
    houseLocationId: intent.houseLocationId,
    bedLocationId: intent.bedLocationId,
  };
  if (intent.type === 'assign') {
    if (participants.length > 0) {
      body.participants = participants;
    }
    if (priorityEnum) {
      body.priorityEnum = priorityEnum;
    }
    if (admissionVitalSigns) {
      body.admissionVitalSigns = admissionVitalSigns;
    }
  }
  return body;
}

/** 根据拖放结果生成入科弹窗意图 */
export function buildAdmissionBedOperationIntent(
  dragged: PatientItem,
  houseId: string,
  bedId: string,
  bedName: string,
  houseName: string,
  regionName: string,
  wardLocationId: string,
  targetOccupied: boolean,
  occupantEncounterId?: string,
  occupantName?: string,
): AdmissionBedOperationIntent | null {
  if (targetOccupied) {
    if (!dragged.bedLocationId || !occupantEncounterId) {
      return null;
    }
    return {
      type: 'swap',
      houseLocationId: houseId,
      bedLocationId: bedId,
      targetBedName: bedName,
      targetHouseName: houseName,
      targetRegionName: regionName,
      targetWardLocationId: wardLocationId,
      encounterIdB: occupantEncounterId,
      swapPartnerName: occupantName,
    };
  }
  return {
    type: dragged.bedLocationId ? 'transfer' : 'assign',
    houseLocationId: houseId,
    bedLocationId: bedId,
    targetBedName: bedName,
    targetHouseName: houseName,
    targetRegionName: regionName,
    targetWardLocationId: wardLocationId,
  };
}

/** 弹窗标题 */
export function admissionBedDialogTitle(intent: AdmissionBedOperationIntent | null): string {
  if (!intent) {
    return '入科选床';
  }
  if (intent.type === 'swap') {
    return '换床 · 床位互换';
  }
  if (intent.type === 'transfer') {
    return '换床 · 换至空床';
  }
  return '入科选床 · 分床';
}

/** 主按钮文案 */
export function admissionBedConfirmLabel(intent: AdmissionBedOperationIntent | null): string {
  if (!intent) {
    return '入科';
  }
  if (intent.type === 'swap') {
    return '确认互换';
  }
  if (intent.type === 'transfer') {
    return '确认换床';
  }
  return '确认分床';
}
