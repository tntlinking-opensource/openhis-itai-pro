/** 可办理预交金收退的就诊状态；队列另可读 COMPLETED/DISCHARGED/CANCELLED，写仍仅下列四态 */
export const PREPAY_ALLOWED_ENCOUNTER_STATUSES = [
  'REGISTERED',
  'IN_PROGRESS',
  'ON_HOLD',
  'PENDING',
] as const;

/** 预交金队列在院范围（与后端 InpatientPrepayStayScope 一致） */
export const PREPAY_STAY_SCOPE = {
  ALL: 'ALL',
  IN_HOSPITAL: 'IN_HOSPITAL',
  /** COMPLETED/DISCHARGED/CANCELLED；后端另要求存在预交金流水才入队 */
  NOT_IN_HOSPITAL: 'NOT_IN_HOSPITAL',
} as const;

export type PrepayStayScope = (typeof PREPAY_STAY_SCOPE)[keyof typeof PREPAY_STAY_SCOPE];

/** 左侧在院范围筛选项 */
export const PREPAY_STAY_SCOPE_OPTIONS = [
  { label: '在院', value: PREPAY_STAY_SCOPE.IN_HOSPITAL },
  { label: '不在院', value: PREPAY_STAY_SCOPE.NOT_IN_HOSPITAL },
  { label: '全部', value: PREPAY_STAY_SCOPE.ALL },
] as const;

export {
  DEFAULT_PAY_CHANNEL_CODE,
  PAY_CHANNEL_OPTIONS,
} from '../register/inpatient-register.constants';
