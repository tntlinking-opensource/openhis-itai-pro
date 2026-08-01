/** 登记列表状态筛选（与后端 InpatientRegistrationConstants 一致） */
export const REGISTRATION_LIST_STATUS = {
  PLANNED: 'PLANNED',
  WAIT_ADMISSION: 'WAIT_ADMISSION',
  ADMITTED: 'ADMITTED',
  PRE_ADMISSION: 'PRE_ADMISSION',
  ON_HOLD: 'ON_HOLD',
  PENDING: 'PENDING',
  COMPLETED: 'COMPLETED',
  DISCHARGED: 'DISCHARGED',
  CANCELLED: 'CANCELLED',
  ENTERED_IN_ERROR: 'ENTERED_IN_ERROR',
} as const;

export type RegistrationListStatus =
  (typeof REGISTRATION_LIST_STATUS)[keyof typeof REGISTRATION_LIST_STATUS];

/** 登记状态筛选项（对齐住院 IMP 状态展示文案） */
export const REGISTRATION_LIST_STATUS_OPTIONS = [
  { label: '待登记', value: REGISTRATION_LIST_STATUS.PLANNED },
  { label: '待入科', value: REGISTRATION_LIST_STATUS.WAIT_ADMISSION },
  { label: '已入科', value: REGISTRATION_LIST_STATUS.ADMITTED },
  { label: '预入院', value: REGISTRATION_LIST_STATUS.PRE_ADMISSION },
  { label: '暂离', value: REGISTRATION_LIST_STATUS.ON_HOLD },
  { label: '待结算', value: REGISTRATION_LIST_STATUS.PENDING },
  { label: '已结算', value: REGISTRATION_LIST_STATUS.COMPLETED },
  { label: '已出院', value: REGISTRATION_LIST_STATUS.DISCHARGED },
  { label: '取消登记', value: REGISTRATION_LIST_STATUS.CANCELLED },
  { label: '错误录入', value: REGISTRATION_LIST_STATUS.ENTERED_IN_ERROR },
] as const;

/** 就诊状态 code（EncounterStatus） */
export const ENCOUNTER_STATUS = {
  PLANNED: 'PLANNED',
  REGISTERED: 'REGISTERED',
  IN_PROGRESS: 'IN_PROGRESS',
  CANCELLED: 'CANCELLED',
} as const;

/** 行政性别 code（AdministrativeGender） */
export const GENDER_OPTIONS = [
  { label: '男性', value: 'MALE' },
  { label: '女性', value: 'FEMALE' },
  { label: '未知', value: 'UNKNOWN' },
] as const;

export {
  FALLBACK_DEFAULT_PAY_CHANNEL as DEFAULT_PAY_CHANNEL_CODE,
  FALLBACK_PAY_CHANNEL_OPTIONS as PAY_CHANNEL_OPTIONS,
} from '#/utils/payment/payment-channel-options';

/** 民族（联调前静态选项） */
export const ETHNICITY_OPTIONS = [
  { label: '汉族', value: '汉族' },
  { label: '蒙古族', value: '蒙古族' },
  { label: '回族', value: '回族' },
  { label: '藏族', value: '藏族' },
  { label: '维吾尔族', value: '维吾尔族' },
  { label: '苗族', value: '苗族' },
  { label: '其他', value: '其他' },
] as const;

/** 住院医保医疗类别：普通住院（2401 默认 med_type=21）。 */
export const INPATIENT_MED_TYPE = '21';

/** 诊断类别（联调前静态选项，页面已不展示） */
export const DIAGNOSIS_CATEGORY_OPTIONS = [
  { label: '普通住院', value: 'GENERAL' },
  { label: '日间手术', value: 'DAY_SURGERY' },
  { label: '日间病房', value: 'DAY_WARD' },
] as const;

export const DEFAULT_DIAGNOSIS_CATEGORY = 'GENERAL';

/** 门诊转住院默认入院方式码（与后端 in_way_code 术语 code=1 对齐） */
export const DEFAULT_IN_WAY_CODE_OUTPATIENT_TRANSFER = '1';

/** 默认入院类型码（与后端 admit_source_code 术语 code=6「常规」对齐） */
export const DEFAULT_ADMIT_SOURCE_CODE_ROUTINE = '6';

/** 默认病情优先级码（与后端 adm_cond_code 术语 code=3「一般」对齐） */
export const DEFAULT_ADM_COND_CODE_GENERAL = '3';
