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
  accountBalanceDisplayMode?: string;
  accountBalanceSubText?: string;
  accountBalanceSummaryText?: string;
  availableBalance?: number | string;
  admissionTime?: string;
  ageText?: string;
  arrearsAmount?: number | string;
  attendingDoctorName?: string;
  bedId?: string;
  bedName?: string;
  /** 费用性质/医保类型展示（与预交金 contractDisplay 对齐，接口有则展示） */
  contractDisplay?: string;
  /** 险种类型码（310 职工 / 390 城乡居民等） */
  insutype?: string;
  diagnosisList?: string[];
  diagnosisSummary?: string;
  encounterBusNo?: string;
  encounterId: string;
  genderEnum?: string;
  genderEnumText?: string;
  houseName?: string;
  /** 身份证号（完整号；读模型未投影，由队列增补回填） */
  idCard?: string;
  /** 联系电话（读模型未投影，由队列增补回填） */
  phone?: string;
  lowBalanceWarning?: boolean;
  nursingLevelActivityId?: string;
  /** 护理级别展示名（IF-IP-DOR-01 · BR-IP-DOR-009） */
  nursingLevelName?: string;
  /** 护理级别医嘱主键 */
  nursingLevelServiceRequestId?: string;
  /** 护理级别医嘱状态 DRAFT/ACTIVE/VERIFIED/STOPPED */
  nursingLevelStatusEnum?: string;
  organizationId?: string;
  organizationName?: string;
  paidAmount?: number | string;
  patientId: string;
  patientName: string;
  pendingSettlementTotal?: number | string;
  pinyinCode?: string;
  prepayNetBalance?: number | string;
  primaryNurseName?: string;
  priorityEnum?: string;
  startTime?: string;
  /** 出院时间（未出院为空） */
  endTime?: string;
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
