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
  uploadRequest?: Record<string, unknown>;
  drugLines?: Array<{
    lineNo?: number;
    medListCodg?: string;
    drugName?: string;
    drugSpec?: string;
  }>;
  diagnosis?: {
    diagCode?: string;
    diagName?: string;
  };
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
  platformBody: Record<string, unknown>;
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
  platformBody: Record<string, unknown>;
}

export interface ElepGeneratePdfRequest {
  contractBusNo: string;
  prescriptionId: string;
  pdfInput?: Record<string, unknown>;
}
