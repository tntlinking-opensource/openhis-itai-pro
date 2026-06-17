/** 左侧住院退药患者队列行 */
export interface InpatientReturnMedicineQueuePatient {
  id: string;
  patientName: string;
  genderEnumText: string;
  ageLabel: string;
  hospitalizationNo: string;
  wardName: string;
  bedName: string;
  refundEnum: string;
  refundEnumText: string;
}

/** 退药明细表格行 */
export interface InpatientReturnMedicineDetailRow {
  id: string;
  dispenseId: string;
  requestId: string;
  itemName: string;
  lotNumber: string;
  quantity: string;
  unitText: string;
  doctorName: string;
  refundEnum: string;
  refundEnumText: string;
  traceNo: string;
  selectable: boolean;
}
