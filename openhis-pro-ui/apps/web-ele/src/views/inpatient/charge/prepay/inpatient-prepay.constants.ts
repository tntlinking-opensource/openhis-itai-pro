/** 可办理预交金收退的就诊状态（EncounterStatus）；队列可见范围含 COMPLETED 待清床，由后端 Q-IPI-004 控制 */
export const PREPAY_ALLOWED_ENCOUNTER_STATUSES = [
  'REGISTERED',
  'IN_PROGRESS',
  'ON_HOLD',
  'PENDING',
] as const;

export {
  DEFAULT_PAY_CHANNEL_CODE,
  PAY_CHANNEL_OPTIONS,
} from '../register/inpatient-register.constants';
