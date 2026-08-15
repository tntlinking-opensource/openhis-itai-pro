/**
 * 门诊医生站 · 中医 Tab · 与后端 Request/Response DTO 字段名一致（camelCase）。
 * 凡 Java Long 主键/外键：请求体与前端持有的值一律用 string。
 */

import type {
  DoctorStationAdviceCatalogRowDto,
  PrepickedProductKeyDto,
  TerminologyOptionDto,
} from './advice.types';

/** 行操作：暂存明细 */
export type TcmRxLineOp = 'CREATE' | 'DELETE' | 'UPDATE';

/** 签退/删除动作 */
export type TcmRxWithdrawAction = 'DELETE' | 'WITHDRAW';

/** 签退/删除范围 */
export type TcmRxWithdrawScope = 'LINE' | 'PRESCRIPTION';

/** 中医目录角色 */
export type TcmCatalogRole = 'DISEASE' | 'SYNDROME';

/** 住院长临医嘱类型 */
export type TcmTherapyType = 'LONG_TERM' | 'TEMPORARY';

/** listTcmDiagnosisGroups */
export interface ListTcmDiagnosisGroupsRequest {
  encounterId: string;
  patientId: string;
}

export interface TcmDiagnosisGroupRowDto {
  tcmSyndromeGroup?: null | string;
  diseaseConditionId?: null | string;
  diseaseDefinitionId?: null | string;
  diseaseCode?: null | string;
  diseaseName?: null | string;
  syndromeConditionId?: null | string;
  syndromeDefinitionId?: null | string;
  syndromeCode?: null | string;
  syndromeName?: null | string;
  primaryFlag?: null | number;
  rank?: null | number;
  medicalTypeCode?: null | string;
  diagnosisDesc?: null | string;
  /** 病名医保诊断编码；未对码时为空，列表展示「-」 */
  ybNo?: null | string;
}

export interface ListTcmDiagnosisGroupsResponse {
  groups?: null | TcmDiagnosisGroupRowDto[];
  totalCount?: null | number;
}

/** pageTcmConditionDefinitionCatalog */
export interface PageTcmConditionDefinitionRequest {
  catalogRole: TcmCatalogRole;
  keyword?: null | string;
  pageNo: number;
  pageSize: number;
}

export interface TcmDiagnosisCatalogRowDto {
  definitionId?: null | string;
  conditionCode?: null | string;
  conditionName?: null | string;
  sourceCode?: null | string;
  sourceText?: null | string;
  /** 医保诊断编码；未对码时为空，列表展示「-」 */
  ybNo?: null | string;
}

export interface PageTcmConditionDefinitionResponse {
  rows?: null | TcmDiagnosisCatalogRowDto[];
  total?: null | number;
}

/** saveTcmDiagnosisGroupsBatch */
export interface TcmDiagnosisGroupItemRequest {
  tcmSyndromeGroup?: null | string;
  diseaseDefinitionId?: null | string;
  diseaseConditionId?: null | string;
  syndromeDefinitionId?: null | string;
  syndromeConditionId?: null | string;
  primaryFlag: number;
  rank: number;
  medicalTypeCode?: null | string;
  diagnosisDesc?: null | string;
}

export interface SaveTcmDiagnosisGroupsBatchRequest {
  encounterId: string;
  patientId: string;
  groups: TcmDiagnosisGroupItemRequest[];
}

export interface SaveTcmDiagnosisGroupsBatchResponse {
  success?: boolean | null;
  savedGroupKeys?: null | string[];
}

/** deleteTcmDiagnosisGroup */
export interface DeleteTcmDiagnosisGroupRequest {
  encounterId: string;
  patientId: string;
  tcmSyndromeGroup: string;
}

export interface DeleteTcmDiagnosisGroupResponse {
  success?: boolean | null;
}

/** listTcmHerbalPrescriptions */
export interface ListTcmRxRequest {
  encounterId: string;
  patientId: string;
  /** 住院：LONG_TERM / TEMPORARY；空=全部 */
  therapyTypeCode?: null | string;
}

export interface TcmRxLineDto {
  medicationRequestId?: null | string;
  medicationDefinitionId?: null | string;
  medicationName?: null | string;
  dose?: null | number | string;
  doseUnitCode?: null | string;
  doseUnitName?: null | string;
  quantity?: null | number | string;
  unitCode?: null | string;
  unitName?: null | string;
  retailUnitPrice?: null | number | string;
  sortNumber?: null | number;
  /** 行级费用态码（ChargeItemStatus） */
  chargeStatusEnum?: null | string;
  chargeStatusText?: null | string;
  lineStatusEnum?: null | string;
  /** 特殊煎法术语 code（dosage_instruction） */
  dosageInstructionCode?: null | string;
  /** 特殊煎法展示名 */
  dosageInstructionText?: null | string;
  /** 行级费用性质/合同主键 */
  contractId?: null | string;
  /** 行级费用性质展示文案 */
  feeNatureText?: null | string;
}

export interface TcmRxHeaderDto {
  prescriptionGroupKey?: null | string;
  prescriptionNo?: null | string;
  /** 住院方头长临 */
  therapyEnum?: null | string;
  tcmSyndromeGroup?: null | string;
  /** 方头绑定就诊诊断有序 ids 串（多选回显） */
  encounterDiagnosisIds?: null | string;
  /**
   * 历史引用用病证组（IF-DOR-39 detail 附带；含病名/证候目录主键，供跨就诊落到本次）
   */
  reuseDiagnosisGroups?: null | TcmDiagnosisGroupRowDto[];
  chineseHerbsDoseQuantity?: null | number | string;
  sufferingFlag?: boolean | null;
  methodCode?: null | string;
  methodName?: null | string;
  rateCode?: null | string;
  rateName?: null | string;
  days?: null | number;
  decoctionMethodCode?: null | string;
  remark?: null | string;
  statusEnum?: null | string;
  displayStatusEnum?: null | string;
  displayStatusEnumText?: null | string;
  /** 方级费用态码（药材行 + 代煎 companion 归并） */
  chargeStatusEnum?: null | string;
  chargeStatusText?: null | string;
  /** 方头费用性质/合同主键（与西药行 contractId 同口径） */
  contractId?: null | string;
  /** 方头费用性质展示名 */
  feeNatureText?: null | string;
  lines?: null | TcmRxLineDto[];
}

export interface ListTcmRxResponse {
  prescriptions?: null | TcmRxHeaderDto[];
}

/** pageTcmHerbalMedicationCatalog */
export interface PageTcmMedCatalogRequest {
  encounterId: string;
  patientId: string;
  keyword?: null | string;
  pageNo: number;
  pageSize: number;
  prepickedProductKeys?: null | PrepickedProductKeyDto[];
}

/** IF-DOR-35 分页响应：与 IF-DOR-11 同构（PagedResult：content / totalCount） */
export interface PageTcmMedCatalogResponse {
  content?: DoctorStationAdviceCatalogRowDto[] | null;
  totalCount?: null | number;
  pageNo?: null | number;
  pageSize?: null | number;
}

/** saveTcmHerbalPrescriptionDraftBatch */
export interface TcmRxDraftLineRequest {
  lineOp: TcmRxLineOp;
  medicationRequestId?: null | string;
  medicationDefinitionId?: null | string;
  dose?: null | number | string;
  doseUnitCode?: null | string;
  sortNumber?: null | number;
  dispenseLocationId?: null | string;
  /** 特殊煎法术语 code（dosage_instruction） */
  dosageInstructionCode?: null | string;
  /** 行级费用性质/合同主键（优先于方头 contractId） */
  contractId?: null | string;
}

export interface TcmRxDraftHeaderRequest {
  prescriptionGroupKey: string;
  /** 住院必填：LONG_TERM / TEMPORARY */
  therapyEnum?: null | string;
  tcmSyndromeGroup: string;
  /** 方头绑定就诊诊断有序 ids 串（多选；空则后端按病证组回落） */
  encounterDiagnosisIds?: null | string;
  chineseHerbsDoseQuantity: number | string;
  sufferingFlag?: boolean | null;
  methodCode?: null | string;
  rateCode?: null | string;
  days?: null | number;
  decoctionMethodCode?: null | string;
  remark?: null | string;
  /** 方头默认费用性质/合同主键（行未传时回落） */
  contractId?: null | string;
  lines: TcmRxDraftLineRequest[];
}

export interface SaveTcmRxDraftBatchRequest {
  encounterId: string;
  patientId: string;
  prescriptions: TcmRxDraftHeaderRequest[];
}

export interface SaveTcmRxDraftBatchResponse {
  success?: boolean | null;
  newMedicationRequestIds?: null | string[];
  message?: null | string;
}

/** signTcmHerbalPrescriptionBatch */
export interface SignTcmRxTargetRequest {
  prescriptionGroupKey: string;
  medicationRequestId: string;
}

export interface SignTcmRxBatchRequest {
  encounterId: string;
  patientId: string;
  signTargets: SignTcmRxTargetRequest[];
}

export interface SignTcmRxBatchResponse {
  success?: boolean | null;
  prescriptionNoByGroupKey?: null | Record<string, string>;
}

/** withdrawOrDeleteTcmHerbalPrescription */
export interface WithdrawOrDeleteTcmRxRequest {
  encounterId: string;
  patientId: string;
  action: TcmRxWithdrawAction;
  scope: TcmRxWithdrawScope;
  medicationRequestIds: string[];
  prescriptionGroupKey?: null | string;
  prescriptionNo?: null | string;
}

export interface WithdrawOrDeleteTcmRxResponse {
  success?: boolean | null;
}

/** voidTcmDecoctionCharge：单独取消已签发方的代煎费 */
export interface VoidTcmDecoctionChargeRequest {
  encounterId: string;
  patientId: string;
  prescriptionGroupKey: string;
}

export interface VoidTcmDecoctionChargeResponse {
  success?: boolean | null;
}

/** supplementTcmDecoctionCharge：已签发方补充代煎费（药材已收费亦可） */
export interface SupplementTcmDecoctionChargeRequest {
  encounterId: string;
  patientId: string;
  prescriptionGroupKey: string;
}

export interface SupplementTcmDecoctionChargeResponse {
  success?: boolean | null;
}

/** listPatientTcmHerbalPrescriptionHistory */
export interface ListPatientTcmRxHistoryRequest {
  patientId: string;
  currentEncounterId: string;
  keyword?: null | string;
}

export interface TcmRxHistoryRowDto {
  prescriptionNo?: null | string;
  encounterId?: null | string;
  /** 开立时间（后端 req_opened_time） */
  reqOpenedTime?: null | string;
  /** 保留类型字段，UI 不展示 */
  reqAuthoredTime?: null | string;
  /** 开立人姓名 */
  authoredByName?: null | string;
  summaryText?: null | string;
  medicationNames?: null | string[];
  /** 历史方完整明细（侧栏点击引用；有则无需再调 listTcmHerbalPrescriptions） */
  detail?: null | TcmRxHeaderDto;
  /** 是否本次就诊（侧栏「本次」标识） */
  currentEncounterFlag?: boolean | null;
}

export interface ListPatientTcmRxHistoryResponse {
  historyRows?: null | TcmRxHistoryRowDto[];
}

/** initDoctorStationTcm */
export interface InitDoctorStationTcmRequest {
  encounterId: string;
  patientId: string;
}

export interface InitDoctorStationTcmResponse {
  tcmMethodCodeOptions?: null | TerminologyOptionDto[];
  medicationRateOptions?: null | TerminologyOptionDto[];
  /** 单味特殊煎法（dosage_instruction） */
  dosageInstructionOptions?: null | TerminologyOptionDto[];
}

/** stopLongTermTcmHerbalPrescription（住院） */
export interface StopLongTermTcmHerbalPrescriptionRequest {
  encounterId: string;
  patientId: string;
  prescriptionNos: string[];
  stopTime?: null | string;
}

export interface StopLongTermTcmHerbalPrescriptionResponse {
  success?: boolean | null;
  message?: null | string;
}

/** cancelStopLongTermTcmHerbalPrescription（住院） */
export interface CancelStopLongTermTcmHerbalPrescriptionRequest {
  encounterId: string;
  patientId: string;
  prescriptionNos: string[];
}

export interface CancelStopLongTermTcmHerbalPrescriptionResponse {
  success?: boolean | null;
  message?: null | string;
}
