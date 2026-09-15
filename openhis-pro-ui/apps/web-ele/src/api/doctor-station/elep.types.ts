/** 电子处方本地状态 */
export type ElepLocalStatus =
  | 'DRAFT'
  | 'FAILED'
  | 'PRE_CHECKED'
  | 'REVOKED'
  | 'SIGNED'
  | 'UPLOADED';

export interface ElepEncounterPrescriptionItemDto {
  prescriptionId?: string;
  hospRxno?: string;
  hiRxno?: string;
  prescriptionNo?: string;
  localStatus?: ElepLocalStatus | string;
  mainDiagName?: string;
  drugSummary?: string;
  prscTime?: string;
  prscDrName?: string;
}

export interface ElepPatientHistoryItemDto {
  prescriptionId?: string;
  hiRxno?: string;
  hospRxno?: string;
  localStatus?: string;
  prscDrName?: string;
  prscDeptName?: string;
  mainDiagName?: string;
  drugSummary?: string;
  prscTime?: string;
  canContinuationRx?: boolean;
}

export interface ElepPatientHistoryResultDto {
  items?: ElepPatientHistoryItemDto[];
  total?: number;
  page?: number;
  pageSize?: number;
}

export interface ElepHistoryDetailDto {
  prescriptionId?: string;
  hiRxno?: string;
  hospRxno?: string;
  initRxno?: string;
  localStatus?: ElepLocalStatus | string;
  rxStasCodg?: string;
  rxUsedStasCodg?: string;
  reptFlag?: string;
  maxReptCnt?: number;
  reptdCnt?: number;
  valiEndTime?: string;
  prscTime?: string;
  prscDrName?: string;
  mainDiagName?: string;
  drugSummary?: string;
  pdfFilePath?: string;
  canContinuationRx?: boolean;
  medType?: string;
  diseCodg?: string;
  diseName?: string;
  valiDays?: string;
  /** 审方药师医保码；非空表示已审方 */
  pharCode?: string;
  pharName?: string;
  mdtrtId?: string;
  uploadRequest?: Record<string, unknown>;
  drugLines?: ElepDrugFormLine[];
  diagnosis?: ElepDiagnosisForm;
}

/** 本页诊断（医保 1307） */
export interface ElepDiagnosisForm {
  diagCode?: string;
  diagName?: string;
}

/** 本页药品行（医保 1301 + 用法） */
export interface ElepDrugFormLine {
  lineNo?: number;
  medListCodg?: string;
  drugGenname?: string;
  drugName?: string;
  drugSpec?: string;
  drugDosform?: string;
  drugCnt?: string;
  drugDosunt?: string;
  medcWayCodg?: string;
  medcWayDscr?: string;
  medcDays?: string;
  sinDoscnt?: string;
  sinDosunt?: string;
  usedFrquCodg?: string;
  usedFrquName?: string;
  /** 用药开始时间 yyyy-MM-dd HH:mm:ss */
  medcBegntime?: string;
  /** 用药结束时间 yyyy-MM-dd HH:mm:ss */
  medcEndtime?: string;
  /** 生产厂家 */
  prdrName?: string;
  /** 药品商品名 */
  drugProdname?: string;
}

export interface ElepPrescriptionDraftDto {
  prescriptionId?: string;
  hospRxno?: string;
  initRxno?: string;
  localStatus?: string;
}

export interface ElepActionResultDto {
  success?: boolean;
  message?: string;
  prescriptionId?: string;
  hospRxno?: string;
  hiRxno?: string;
  rxTraceCode?: string;
  pdfFilePath?: string;
  signDigest?: string;
  rxStasCodg?: string;
  pharCode?: string;
  pharName?: string;
}

/** 就诊只读上下文（自费/无挂号号时 allowed=false） */
export interface ElepEncounterContextDto {
  allowed?: boolean;
  blockReason?: string;
  encounterId?: string;
  patientId?: string;
  encounterBusNo?: string;
  patientName?: string;
  genderEnum?: string;
  gend?: string;
  patnAge?: string;
  idCard?: string;
  deptName?: string;
  deptYbNo?: string;
  doctorName?: string;
  doctorYbNo?: string;
  mdtrtId?: string;
  psnNo?: string;
  iptOtpNo?: string;
  mdtrtTime?: string;
  fixmedinsCode?: string;
  fixmedinsName?: string;
  /** 挂号契约 bus_no；电子处方不区分省市工伤，由后端回填 */
  contractBusNo?: string;
  /** 挂号契约主键 */
  contractId?: number | string;
}

export interface ListElepEncounterPrescriptionsRequest {
  encounterId: string;
}

export interface ListElepPatientHistoryRequest {
  patientId: string;
  currentEncounterId?: string;
  contractBusNo?: string;
  page?: number;
  pageSize?: number;
}

export interface ElepPreCheckRequest {
  contractBusNo: string;
  contractId?: string;
  encounterId: string;
  patientId: string;
  prescriptionId: string;
  pdfInput?: Record<string, unknown>;
}

export interface ElepSignRequest {
  contractBusNo: string;
  prescriptionId: string;
  platformBody?: Record<string, unknown>;
}

export interface ElepUploadFileRequest {
  contractBusNo: string;
  prescriptionId: string;
  platformBody?: Record<string, unknown>;
}

export interface ElepRevokeRequest {
  contractBusNo: string;
  prescriptionId: string;
  platformBody?: Record<string, unknown>;
}

export interface ElepCloneRequest {
  sourcePrescriptionId: string;
  targetEncounterId: string;
  patientId: string;
  contractBusNo: string;
  contractId?: string;
  continuationRx?: boolean;
  copyDiagnosis?: boolean;
  newHospRxno?: string;
}

export interface ElepPlatformQueryRequest {
  contractBusNo: string;
  prescriptionId: string;
  contractId?: string;
  hiRxno?: string;
  refreshLocal?: boolean;
}

export interface ElepSaveDraftRequest {
  contractBusNo: string;
  contractId?: string;
  encounterId: string;
  patientId: string;
  prescriptionId?: string;
  diseCodg?: string;
  diseName?: string;
  rxTypeCode?: string;
  valiDays?: string;
  diagnosis?: ElepDiagnosisForm;
  drugLines?: ElepDrugFormLine[];
}

export interface ElepGeneratePdfRequest {
  contractBusNo: string;
  prescriptionId: string;
  pdfInput?: Record<string, unknown>;
}

export interface ElepEncounterContextRequest {
  encounterId: string;
  patientId: string;
  contractBusNo?: string;
}

export interface ElepReviewRequest {
  contractBusNo?: string;
  prescriptionId: string;
}
