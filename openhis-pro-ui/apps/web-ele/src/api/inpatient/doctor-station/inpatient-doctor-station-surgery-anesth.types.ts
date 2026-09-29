import type { ApiOptionItem } from '#/types/api-response';

/** 手术申请 / 麻醉申请 */
export type SurgeryAnesthFormKind = 'ANESTHESIA' | 'SURGERY';

/** 加收检索侧别，与申请单种类同码 */
export type SurgeryAnesthSideCode = 'ANESTHESIA' | 'SURGERY';

/** 申请行种类 */
export type SurgeryAnesthLineKind = 'ANESTHESIA' | 'PROCEDURE' | 'SURCHARGE';

/** 单头聚合状态 */
export type SurgeryAnesthHeaderStatus = 'ACTIVE' | 'DRAFT' | 'MIXED';

export interface PlannedPerformerDto {
  roleCode?: string;
  practitionerId?: string;
  practitionerName?: string;
}

export interface PlannedSurgeryFieldsDto {
  procedureTime?: string;
  mainProcedureFlag?: boolean;
  procedureLevelCode?: string;
  incisionTypeCode?: string;
  anesthesiaMethodCode?: string;
  note?: string;
  performers?: PlannedPerformerDto[];
}

export interface InitSurgeryAnesthResponse {
  procedureLevelOptions?: ApiOptionItem[];
  incisionTypeOptions?: ApiOptionItem[];
  anesthesiaMethodOptions?: ApiOptionItem[];
  yesNoOptions?: ApiOptionItem[];
}

export interface SurgeryApplyFormHeaderDto {
  prescriptionNo?: string;
  statusEnum?: string;
  plannedProcedureTime?: string;
  mainProcedureName?: string;
  lineCount?: number;
  nestedAnesthesiaPrescriptionNo?: string;
  requesterName?: string;
}

export interface SurgeryApplyFormLineDto {
  serviceRequestId?: string;
  lineKindCode?: string;
  procedureDefinitionId?: string;
  procedureCode?: string;
  procedureName?: string;
  activityDefinitionId?: string;
  activityName?: string;
  quantity?: number | string;
  unitCode?: string;
  statusEnum?: string;
  sortNumber?: number;
  mainProcedureFlag?: boolean;
}

export interface AnesthesiaApplyFormHeaderDto {
  prescriptionNo?: string;
  statusEnum?: string;
  linkedSurgeryPrescriptionNo?: string;
  anesthesiaMethodCode?: string;
  lineCount?: number;
  requesterName?: string;
}

export interface AnesthesiaApplyFormLineDto {
  serviceRequestId?: string;
  lineKindCode?: string;
  activityDefinitionId?: string;
  activityName?: string;
  quantity?: number | string;
  unitCode?: string;
  statusEnum?: string;
  sortNumber?: number;
}

export interface ClinicalProcV3SearchItemDto {
  id?: string;
  procedureCode?: string;
  name?: string;
  pyStr?: string;
  wbStr?: string;
  statusEnum?: string;
  activityDefinitionId?: string;
  activityName?: string;
}

export interface SurchargeActivityDefinitionItemDto {
  activityDefinitionId?: string;
  busNo?: string;
  name?: string;
  categoryCode?: string;
  unitCode?: string;
}

export interface SurgeryApplyFormLineRequest {
  lineKindCode: SurgeryAnesthLineKind;
  procedureDefinitionId?: string;
  activityDefinitionId: string;
  quantity: number;
  unitCode: string;
  sortNumber?: number;
  mainProcedureFlag?: boolean;
  note?: string;
  /** 仅前端展示，不提交 */
  displayName?: string;
}

export interface AnesthesiaApplyFormLineRequest {
  lineKindCode: 'ANESTHESIA' | 'SURCHARGE';
  activityDefinitionId: string;
  quantity: number;
  unitCode: string;
  sortNumber?: number;
  note?: string;
  displayName?: string;
}
