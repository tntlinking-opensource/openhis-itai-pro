/** 入院诊断提交行（与后端 AdmissionDiagnosisSubmitDto 对齐） */
export interface AdmissionDiagnosisSubmitDto {
  conditionDefinitionId?: string;
  /** 已有就诊诊断行主键；更新/引用时携带 */
  encounterDiagnosisId?: string;
  description?: string;
  primaryFlag: boolean;
  /** 是否中医诊断；中医一病一症须两条均为 true */
  tcmFlag?: boolean;
  /** 中医病证组号（同组病+证共用；可空由后端分配） */
  tcmSyndromeGroup?: string;
}

/** 查询是否已存在住院申请 */
export interface CheckInpatientAdmissionExistsRequest {
  ambEncounterId: string;
}

export interface CheckInpatientAdmissionExistsResponse {
  exists: boolean;
  inpatientEncounterId?: string;
  statusEnum?: string;
}

/** 发起住院申请 */
export interface RequestInpatientAdmissionRequest {
  ambEncounterId: string;
  patientId?: string;
  targetDepartmentId: string;
  /** 意向病区 */
  wardId?: string;
  requestDoctorId: string;
  admitSourceCode?: string;
  inWayCode?: string;
  priorityCode?: string;
  admissionDiagnoses: AdmissionDiagnosisSubmitDto[];
  operatorId: string;
  /**
   * 入院办理意向：INSURANCE=医保办 / SELF_PAY=自费办。
   * 仅提示款台，非真实费用性质；登记合同仍以收费员选择为准。
   */
  chargeIntent?: 'INSURANCE' | 'SELF_PAY' | string;
  /**
   * 意向预交金总额（医生只填总额，不填支付方式）。
   * >0 时后端落 HOSPITAL_DEPOSIT DRAFT；登记确认时由收费员补渠道。
   */
  advanceDepositAmount?: number | string;
}

export interface RequestInpatientAdmissionResponse {
  inpatientEncounterId?: string;
  inpatientEncounterBusNo?: string;
  success: boolean;
}

/** 撤回待登记住院申请（直接软删，不迁取消态） */
export interface WithdrawInpatientAdmissionRequest {
  ambEncounterId: string;
  operatorId: string;
}

export interface WithdrawInpatientAdmissionResponse {
  inpatientEncounterId?: string;
  /** 撤回前状态码（通常 PLANNED） */
  statusEnum?: string;
  success: boolean;
}
