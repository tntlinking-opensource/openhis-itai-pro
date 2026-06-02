import type { PageApiResponse, PageParams } from '#/types/api-response';

/** 住院医生站在院患者队列分页请求（IF-IP-DOR-01） */
export interface PageInpatientDoctorStationPatientQueueRequest extends PageParams {
  keyword?: string;
}

/**
 * 住院医生站在院患者队列行（IF-IP-DOR-01 · DoctorStationPatientQueueItemDto）。
 * 响应主键在页面层映射为 string 再参与业务。
 */
export interface InpatientDoctorStationPatientDto {
  accountBalance?: number | string;
  admissionTime?: string;
  ageText?: string;
  arrearsAmount?: number | string;
  attendingDoctorName?: string;
  bedId?: string;
  bedName?: string;
  /** 费用性质/医保类型展示（与预交金 contractDisplay 对齐，接口有则展示） */
  contractDisplay?: string;
  diagnosisList?: string[];
  diagnosisSummary?: string;
  encounterBusNo?: string;
  encounterId: string;
  genderEnum?: string;
  genderEnumText?: string;
  houseName?: string;
  lowBalanceWarning?: boolean;
  organizationId?: string;
  organizationName?: string;
  paidAmount?: number | string;
  patientId: string;
  patientName: string;
  pinyinCode?: string;
  primaryNurseName?: string;
  priorityEnum?: string;
  startTime?: string;
  statusEnum?: string;
  statusEnumText?: string;
  totalAmount?: number | string;
  wardId?: string;
  wardName?: string;
  wubiCode?: string;
}

/** 住院医生站在院患者队列分页响应 */
export type PageInpatientDoctorStationPatientQueueResponse =
  PageApiResponse<InpatientDoctorStationPatientDto>;
