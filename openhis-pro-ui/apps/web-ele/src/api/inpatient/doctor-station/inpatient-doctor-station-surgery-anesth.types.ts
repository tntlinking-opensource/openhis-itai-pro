import type { ApiOptionItem } from '#/types/api-response';

/** 手术申请 / 麻醉申请 */
export type SurgeryAnesthFormKind = 'ANESTHESIA' | 'SURGERY';

/** 加收检索侧别，与申请单种类同码 */
export type SurgeryAnesthSideCode = 'ANESTHESIA' | 'SURGERY';

/**
 * 申请行种类。
 * 开立：手术 PROCEDURE + ENTRUST（嘱托 basedOn 术式）；麻醉 ANESTHESIA / MEDICATION（平级）。
 * SURCHARGE 仅历史/护士划价回显，医生站开立禁止。
 */
export type SurgeryAnesthLineKind =
  | 'ANESTHESIA'
  | 'ENTRUST'
  | 'MEDICATION'
  | 'PROCEDURE'
  | 'SURCHARGE';

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
  /** 频次（与医嘱开立同源） */
  rateOptions?: ApiOptionItem[];
  /** 用法/给药途径 */
  methodOptions?: ApiOptionItem[];
  /**
   * 单次剂量单位（术语 dose_unit）。
   * 麻醉不按剂量单位换算总量，开立可按名称/码检索改选。
   */
  doseUnitOptions?: ApiOptionItem[];
  /** 麻醉医师（麻醉室等配置科室上激活医师出诊岗） */
  anesthetistOptions?: ApiOptionItem[];
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
  /** 术前嘱托正文 */
  entrustContent?: string;
  /** 挂靠术式 SR */
  basedOnProcedureServiceRequestId?: string;
  /** 挂靠术式 sortNumber（编辑回填） */
  basedOnProcedureSortNumber?: number;
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
  medicationDefinitionId?: string;
  medicationName?: string;
  medicationRequestId?: string;
  quantity?: number | string;
  unitCode?: string;
  /** 单位展示名（术语文案） */
  unitName?: string;
  rateCode?: string;
  methodCode?: string;
  dose?: number | string;
  doseUnitCode?: string;
  doseUnitName?: string;
  statusEnum?: string;
  sortNumber?: number;
}

export interface ClinicalProcV3SearchItemDto {
  id?: string;
  procedureCode?: string;
  name?: string;
  pyStr?: string;
  wbStr?: string;
  /** 医保手术码（可空） */
  ybNo?: string;
  statusEnum?: string;
  activityDefinitionId?: string;
  activityName?: string;
}

export interface SurchargeActivityDefinitionItemDto {
  activityDefinitionId?: string;
  medicationDefinitionId?: string;
  busNo?: string;
  name?: string;
  categoryCode?: string;
  unitCode?: string;
  /** 选品种类；EXTRA 已废弃 */
  itemKindCode?: 'MAIN' | 'MEDICATION';
}

export interface SurgeryApplyFormLineRequest {
  /** 开立仅 PROCEDURE / ENTRUST；SURCHARGE 禁止提交 */
  lineKindCode: SurgeryAnesthLineKind;
  /** 术式行必填（国临 PD） */
  procedureDefinitionId?: string;
  /** 术式行可空（后端 activity_id=0）；开立不加收 */
  activityDefinitionId?: string;
  /** 术前嘱托正文（ENTRUST 必填） */
  entrustContent?: string;
  /** 挂靠术式行的 sortNumber（ENTRUST 必填） */
  basedOnProcedureSortNumber?: number;
  quantity: number;
  unitCode: string;
  /** 频次；术式/嘱托可空（后端默认立即） */
  rateCode?: string;
  sortNumber?: number;
  mainProcedureFlag?: boolean;
  note?: string;
  /** 仅前端展示，不提交 */
  displayName?: string;
  /** 仅前端：规格/单位名 */
  displaySpec?: string;
  unitName?: string;
}

export interface AnesthesiaApplyFormLineRequest {
  /** 开立仅 ANESTHESIA / MEDICATION；SURCHARGE 禁止提交 */
  lineKindCode: 'ANESTHESIA' | 'MEDICATION' | 'SURCHARGE';
  activityDefinitionId?: string;
  medicationDefinitionId?: string;
  quantity: number;
  unitCode: string;
  /** 频次必填 */
  rateCode?: string;
  /** 用法（药品必填） */
  methodCode?: string;
  /** 单次剂量（药品必填） */
  dose?: number;
  /** 单次剂量单位（术语 dose_unit；药品必填；不换算总量） */
  doseUnitCode?: string;
  sortNumber?: number;
  note?: string;
  displayName?: string;
  /** 仅前端：规格 / 总量单位名 / 剂量单位名 / 单价 / 可选单位 */
  displaySpec?: string;
  unitName?: string;
  doseUnitName?: string;
  unitPrice?: number;
  unitOptions?: Array<{ value: string; label: string }>;
}
