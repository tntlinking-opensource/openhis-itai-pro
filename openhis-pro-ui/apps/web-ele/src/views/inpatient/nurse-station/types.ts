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
  /** 入院时间（读模型 startTime）。 */
  startTime?: string;
  /** 出院时间（读模型 endTime）。 */
  endTime?: string;
  name: string;
  gender: string;
  age: number;
  level: string;
  /** 费用性质（如医保、自费）。 */
  feeType: string;
  /** 主要诊断描述。 */
  diagnosis: string;
  /** 住院账户主键（读模型 accountId）。 */
  accountId?: string;
  /** 账户余额（读模型 accountBalance）。 */
  accountBalance?: number | string;
  accountBalanceDisplayMode?: string;
  accountBalanceSubText?: string;
  accountBalanceSummaryText?: string;
  availableBalance?: number | string;
  pendingSettlementTotal?: number | string;
  /** 病区位置 id（读模型 wardId）。 */
  regionId: string;
  /** 病房位置 id（读模型 house 解析）。 */
  wardId: string;
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
  /** 是否展示出院召回（读模型 recallEnabled；仅 DISCHARGED）。 */
  recallEnabled?: boolean;
  pendingAudit: number;
  pendingExecute: number;
  taskTag: string;
  riskText: string;
  selected: boolean;
}

export type PatientStatusFilter = 'all' | 'pending-audit' | 'pending-execute' | 'processed';

/** 患者列表范围筛选（就诊状态并集，与任务筛选独立） */
export type PatientQueueScopeFilter = 'in-hospital' | 'settled-discharged';

export type SelectionPolicy = 'multiple' | 'single';
