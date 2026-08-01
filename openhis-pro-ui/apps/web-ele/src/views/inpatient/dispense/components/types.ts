/** 住院发药左侧队列行（由接口 DTO 映射） */
export interface InpatientDispenseQueuePatient {
  id: string;
  patientId?: string;
  patientName: string;
  genderText: string;
  ageLabel: string;
  hospitalizationNo: string;
  wardName: string;
  bedName: string;
  admissionTimeLabel: string;
  attendingDoctorName: string;
  statusText: string;
  /** 费用性质展示名（队列 contractDisplay；卡片底栏胶囊） */
  feeNatureLabel?: string;
}
