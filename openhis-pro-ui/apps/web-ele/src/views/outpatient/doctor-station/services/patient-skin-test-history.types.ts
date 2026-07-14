/** 医生站患者卡片：单条历史皮试记录 */
export interface PatientSkinTestHistoryItem {
  key: string;
  encounterId: string;
  medicationRequestId?: string;
  medicationName: string;
  clinicalStatusCode?: string;
  clinicalStatusText: string;
  recordedAt?: string;
  positive: boolean;
}

/** 医生站患者卡片：皮试历史汇总 */
export interface PatientSkinTestHistorySummary {
  records: PatientSkinTestHistoryItem[];
  positiveRecords: PatientSkinTestHistoryItem[];
  positiveMedicationNames: string[];
  hasPositive: boolean;
}
