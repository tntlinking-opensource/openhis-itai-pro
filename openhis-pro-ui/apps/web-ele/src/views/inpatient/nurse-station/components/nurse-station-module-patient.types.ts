/** 护士站右侧模块（医嘱校对/执行等）统一患者入参。 */
export interface NurseStationModulePatient {
  id: string;
  patientId?: string;
  /** 住院号（就诊 encounterBusNo，非 encounterId）。 */
  encounterBusNo?: string;
  /** 入院时间（YYYY-MM-DD HH:mm:ss）。 */
  admissionAt?: string;
  /** 入科时间（YYYY-MM-DD HH:mm:ss；读模型 startTime）。 */
  inWardAt?: string;
  /** 出院时间（YYYY-MM-DD HH:mm:ss）。 */
  dischargeAt?: string;
  bedNo: string;
  name: string;
  gender: string;
  age: number;
  nurseLevel: string;
  /** 费用性质/医保类型展示（读模型 contractDisplay）。 */
  feeType?: string;
  /** 险种（310 职工 / 390 居民），完整预结算据此走 2303。 */
  insutype?: string;
  /** 主治医生姓名（读模型 attendingDoctorName）。 */
  attendingDoctorName?: string;
  /** 责任护士姓名（读模型 primaryNurseName）。 */
  primaryNurseName?: string;
  diagnosis?: string;
  /** 入院科室名称（读模型 organizationName）。 */
  organizationName?: string;
  /** 入院/当前住院科室主键（读模型 organizationId；划价取药模板键）。 */
  organizationId?: string;
  /** 病区名称（读模型 wardName / regionId 反查）。 */
  regionName: string;
  /**
   * 病房名称（读模型 houseName / wardId 反查）。
   * 历史字段名 wardName，语义为病房而非病区。
   */
  wardName: string;
  pendingAudit: number;
  pendingExecute: number;
  accountId?: string;
  accountBalance?: number | string;
  accountBalanceDisplayMode?: string;
  accountBalanceSummaryText?: string;
  accountBalanceSubText?: string;
  availableBalance?: number | string;
  pendingSettlementTotal?: number | string;
  lowBalanceWarning?: boolean;
  riskTags: string[];
  /** 就诊状态 code（PENDING=待结算）。 */
  statusEnum?: string;
}

import {
  formatInpatientAccountBalancePrimaryText,
  formatInpatientAccountBalanceSecondaryText,
  resolveInpatientAccountBalanceLabel,
} from '#/utils/inpatient/account-balance-display';

export {
  formatInpatientAccountBalancePrimaryText,
  formatInpatientAccountBalanceSecondaryText,
  resolveInpatientAccountBalanceLabel,
};

/** 账户余额展示：对接读模型文案；无文案时回退金额。 */
export function formatNurseStationAccountBalance(
  row?: Pick<
    NurseStationModulePatient,
    'accountBalance' | 'accountBalanceSummaryText' | 'availableBalance'
  >,
): string {
  return formatInpatientAccountBalancePrimaryText(row);
}

/** 展示余额是否非零（与后端 InpatientDisplayAccountBalanceAssembler 2 位小数口径对齐）。 */
export function isNurseStationDisplayBalanceNonZero(value?: number | string): boolean {
  if (value == null || value === '') {
    return false;
  }
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return false;
  }
  return Math.abs(num) >= 0.005;
}

/** 是否尚未分床（与左侧患者卡片一致）。 */
export function isNurseStationBedUnassigned(bedNo: string | undefined): boolean {
  return !bedNo?.trim();
}

export type NurseStationPatientStatusTone =
  | 'completed'
  | 'default'
  | 'discharged'
  | 'in-progress'
  | 'on-hold'
  | 'pending'
  | 'registered';

export type NurseStationPatientBalanceTone = 'danger' | 'normal' | 'primary' | 'success';

/** 就诊状态 code → 患者卡片色调 class 后缀 */
export function resolveNurseStationPatientStatusTone(code?: string): NurseStationPatientStatusTone {
  const c = (code ?? '').trim();
  if (c === 'REGISTERED') return 'registered';
  if (c === 'IN_PROGRESS') return 'in-progress';
  if (c === 'ON_HOLD') return 'on-hold';
  if (c === 'PENDING') return 'pending';
  if (c === 'COMPLETED') return 'completed';
  if (c === 'DISCHARGED') return 'discharged';
  return 'default';
}

/** 账户余额 → 患者卡片色调（负值/低余额预警用警示色） */
export function resolveNurseStationPatientBalanceTone(
  value?: number | string,
  lowBalanceWarning?: boolean,
): NurseStationPatientBalanceTone {
  if (lowBalanceWarning) {
    return 'danger';
  }
  if (value == null || value === '') {
    return 'normal';
  }
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return 'normal';
  }
  if (num < 0) {
    return 'danger';
  }
  if (Math.abs(num) < 0.005) {
    return 'success';
  }
  return 'primary';
}
