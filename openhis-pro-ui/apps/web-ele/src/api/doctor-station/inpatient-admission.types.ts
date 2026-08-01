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
}

export interface RequestInpatientAdmissionResponse {
  inpatientEncounterId?: string;
  inpatientEncounterBusNo?: string;
  success: boolean;
}
