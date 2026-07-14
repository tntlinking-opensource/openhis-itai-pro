/**
 * 收费工作站患者卡片数据（门诊 / 住院收费列表共用）。
 */
export type BillingPatientChargeStatusCode = 'PLANNED' | 'REFUNDING' | 'SETTLED';

export interface BillingPatientCardPatient {
  id: string;
  patientName: string;
  gender: string;
  /** 周岁等数值年龄（住院等场景） */
  age?: number;
  /** 门诊读模型等返回的年龄展示文案（优先于 age） */
  ageText?: string;
  medicalRecordNo?: string;
  insuranceType?: string;
  /** 不传则不展示卡片收费状态 */
  chargeStatus?: '已结算' | '待收费' | '待退费';
  /** 收费状态 code（逻辑判断用） */
  chargeStatusCode?: BillingPatientChargeStatusCode;
  /** 门诊：挂号科室；住院：科室或病区 */
  visitDepartment: string;
  /** 门诊：挂号时间；住院：入院时间 */
  visitTime: string;
  visitType?: 'inpatient' | 'outpatient';
  /** 门诊接诊医生；住院可为主管医生 */
  doctorName?: string;
  /** 住院号（住院收费时展示） */
  inpatientNo?: string;
  /** 账户余额主文案（住院队列卡片） */
  accountBalanceSummary?: string;
  accountBalanceSub?: string;
  lowBalanceWarning?: boolean;
}
