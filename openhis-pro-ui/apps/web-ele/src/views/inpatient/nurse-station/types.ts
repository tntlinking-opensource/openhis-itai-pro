export interface RegionItem {
  id: string;
  name: string;
}

export interface WardItem {
  id: string;
  regionId: string;
  name: string;
}

export interface PatientItem {
  id: string;
  /** 患者主键（读模型 patientId，与 encounterId 不同）。 */
  patientId?: string;
  /** 住院号（就诊 busNo）。 */
  encounterBusNo?: string;
  /** 入科时间（读模型 startTime）。 */
  startTime?: string;
  /** 入院时间（读模型 admissionTime；缺省时与 startTime 同源）。 */
  admissionTime?: string;
  /** 出院时间（读模型 endTime = 办理出院写入的计划出院时间；非清床/结算）。 */
  endTime?: string;
  name: string;
  gender: string;
  age: number;
  /** 年龄原文案（读模型 ageText，大屏优先展示）。 */
  ageText?: string;
  /** 护理级别展示名（读模型 nursingLevelName；未开立时为空）。 */
  level: string;
  /** 病情 code（读模型 priorityEnum；分床 ASSIGN 写路径用）。 */
  priorityEnum?: string;
  /** 费用性质/医保具体类型（读模型 contractDisplay）。 */
  feeType: string;
  /** 身份证号（读模型 idCardNo，完整号）。 */
  idCardNo?: string;
  /** 医保人员编号（读模型 insuranceNo ← 平台 psn_no）。 */
  insuranceNo?: string;
  /** 住址（读模型 address）。 */
  address?: string;
  /** 联系电话。 */
  patientPhone?: string;
  /** 病人院内编码（病人 ID）。 */
  patientBusNo?: string;
  /** 入院途径展示。 */
  inWayCodeText?: string;
  /** 入院状态/病情展示。 */
  priorityEnumText?: string;
  /** 入院科室名称。 */
  organizationName?: string;
  /** 入院/当前住院科室主键（取药模板键）。 */
  organizationId?: string;
  /** 险种类别 code。 */
  insutype?: string;
  /** 医疗类别展示（如 21-普通住院）。 */
  medTypeDisplay?: string;
  /** 欠费金额。 */
  arrearsAmount?: number | string;
  /** 诊断列表（读模型 diagnosisList）。 */
  diagnosisList?: string[];
  /** 主要诊断描述。 */
  diagnosis: string;
  /** 主管医生姓名（读模型 attendingDoctorName）。 */
  attendingDoctorName?: string;
  /** 主管护士姓名（读模型 primaryNurseName）。 */
  primaryNurseName?: string;
  /** 住院账户主键（读模型 accountId）。 */
  accountId?: string;
  /** 账户余额（读模型 accountBalance）。 */
  accountBalance?: number | string;
  accountBalanceDisplayMode?: string;
  accountBalanceSubText?: string;
  accountBalanceSummaryText?: string;
  availableBalance?: number | string;
  pendingSettlementTotal?: number | string;
  /** 预缴/已缴金额（读模型 paidAmount）。 */
  paidAmount?: number | string;
  /** 病区位置 id（读模型 wardId）。 */
  regionId: string;
  /** 病区名称（读模型 wardName；优先于 id 反查）。 */
  wardName?: string;
  /** 病房位置 id（读模型 house 解析）。 */
  wardId: string;
  /** 病房名称（读模型 houseName；优先于 id 反查）。 */
  houseName?: string;
  /** 床位展示名（读模型 bedName）。 */
  bedNo: string;
  /** 床位位置 id（写操作 bedLocationId）。 */
  bedLocationId: string;
  /** 就诊状态 code（REGISTERED / IN_PROGRESS 等）。 */
  statusEnum: string;
  /** 就诊状态展示文案（读模型/床位图有则返）。 */
  statusEnumText?: string;
  /** 低余额预警（读模型 lowBalanceWarning）。 */
  lowBalanceWarning?: boolean;
  pendingAudit: number;
  pendingExecute: number;
  taskTag: string;
  riskText: string;
  selected: boolean;
}

export type PatientStatusFilter = 'all' | 'pending-audit' | 'pending-execute' | 'processed';

/** 入科状态筛选：全部 / 待入科（REGISTERED） */
export type PatientAdmissionFilter = 'all' | 'pending-admission';

export type SelectionPolicy = 'multiple' | 'single';
