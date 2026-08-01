import type {
  AdmissionVitalSignSubmitDto,
  ParticipantSubmitDto,
} from './inpatient-nurse-station.types';

/** 床位列表面板：入科 / 出 Tab */
export type BedBoardPanel = 'admission' | 'discharge';

/** 床位列表查询范围（houseId 优先于 wardId） */
export interface ListBedBoardRequest {
  houseId?: string;
  panel: BedBoardPanel;
  wardId?: string;
}

/** 床位位置块 */
export interface BedBoardLocationBlockDto {
  bedId: string;
  bedName?: string;
  displayOrder?: number;
  houseId: string;
  houseName?: string;
  operationalEnum?: string;
}

/** 占床患者块（空床时为 null） */
export interface BedBoardOccupantDto {
  accountBalance?: number | string;
  admissionTime?: string;
  ageText?: string;
  attendingDoctorName?: string;
  diagnosisSummary?: string;
  encounterBusNo?: string;
  encounterId: string;
  genderEnum?: string;
  /** 性别展示文案（如「男性」） */
  genderEnumText?: string;
  lowBalanceWarning?: boolean;
  /** 护理级别展示名（如「二级护理」） */
  nursingLevelName?: string;
  patientId?: string;
  patientName?: string;
  primaryNurseName?: string;
  priorityEnum?: string;
  statusEnum?: string;
  statusEnumText?: string;
}

/** 床位列表单行 */
export interface BedBoardItemDto {
  location: BedBoardLocationBlockDto;
  occupant?: BedBoardOccupantDto | null;
}

/** 床位列表响应 */
export interface ListBedBoardResponse {
  items?: BedBoardItemDto[];
}

/** 分床 / 换床 / 互换写操作类型 */
export type BedOperationType = 'ASSIGN' | 'SWAP' | 'TRANSFER';

/** 分床换床互换提交 */
export interface ExecuteBedOperationRequest {
  admissionVitalSigns?: AdmissionVitalSignSubmitDto;
  bedLocationId?: string;
  encounterId: string;
  encounterIdB?: string;
  houseLocationId?: string;
  operationType: BedOperationType;
  participants?: ParticipantSubmitDto[];
  priorityEnum?: string;
}

/** 分床换床互换响应 */
export interface ExecuteBedOperationResponse {
  encounterId?: string;
  operationType?: string;
  succeeded?: boolean;
}

/** 办理出院 */
export interface ExecuteInpatientDischargeRequest {
  encounterId: string;
}

/** 办理出院响应 */
export interface ExecuteInpatientDischargeResponse {
  encounterId?: string;
  succeeded?: boolean;
}

/** 清床离院 */
export interface ClearDischargedBedRequest {
  encounterId: string;
}

/** 清床离院响应 */
export interface ClearDischargedBedResponse {
  encounterId?: string;
  succeeded?: boolean;
}

/** 清床前缺追溯码软提示查询 */
export interface ListMissingDispenseTraceTipsRequest {
  encounterId: string;
}

/** 缺追溯码发药提示行 */
export interface MissingDispenseTraceTipDto {
  dispenseId?: string;
  /** 发药归属日 yyyy-MM-dd */
  dispenseDate?: string;
  medicationName?: string;
  lotNumber?: string;
  /** 可直接展示文案，如「2026-07-18 阿莫西林胶囊（批号 A01）未分配追溯码」 */
  tipText?: string;
}

/** 清床前缺追溯码软提示响应 */
export interface ListMissingDispenseTraceTipsResponse {
  hasMissingTrace?: boolean;
  tips?: MissingDispenseTraceTipDto[];
}

/** 出院前待执行医嘱列表 */
export interface ListDischargePendingExecutionsRequest {
  encounterId: string;
}

/** 出院阻塞医嘱行 */
export interface DischargePendingExecutionItemDto {
  blockingReason?: string;
  displayName?: string;
  doseText?: string;
  rateCode?: string;
  reqOpenedTime?: string;
  /** 保留类型字段，UI 不展示 */
  reqAuthoredTime?: string;
  requestId: string;
  requestTable?: string;
  statusEnum?: string;
  therapyEnum?: string;
}

export interface ListDischargePendingExecutionsResponse {
  items?: DischargePendingExecutionItemDto[];
}

/** 出院前待发退药列表 */
export interface ListDischargePendingMedicationsRequest {
  encounterId: string;
}

/** 出院阻塞发药行 */
export interface DischargePendingMedicationItemDto {
  dispenseId: string;
  medicationId?: string;
  medicationName?: string;
  medicationRequestId?: string;
  performLocationId?: string;
  performLocationName?: string;
  plannedDispenseTime?: string;
  quantity?: number | string;
  statusEnum?: string;
  statusEnumText?: string;
  unitCode?: string;
}

export interface ListDischargePendingMedicationsResponse {
  items?: DischargePendingMedicationItemDto[];
}
